package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The website's Coin advisor (planRows, planSteps): every flip, NPC flip, NPC shop, forge, book combine and craft sized
 * to your coins and free slots, then ranked. Bazaar sales only. No Minecraft code, so it can be tested on its own.
 */
public final class Advisor {
	private Advisor() {}

	/** Kind key -> what it's called (PLAN_SRC). */
	public static final Map<String, String> SRC = new LinkedHashMap<>();
	private static final Map<String, String[]> UNIT = new LinkedHashMap<>();
	static {
		String[][] s = {{"flip", "Bazaar flip", "item", "items"}, {"npc", "Bazaar -> NPC", "item", "items"}, {"shop", "NPC shop -> Bazaar", "purchase", "purchases"},
				{"forge", "Forge", "forge", "forges"}, {"books", "Book combining", "combine", "combines"}, {"craft", "Craft", "craft", "crafts"}};
		for (String[] r : s) {
			SRC.put(r[0], r[1]);
			UNIT.put(r[0], new String[] {r[2], r[3]});
		}
	}

	public static String unit(String src, long n) {
		return UNIT.get(src)[n == 1 ? 0 : 1];
	}

	/** One way to make money, sized to your coins and slots. */
	public static final class Option implements Amounts.Sizer {
		public String src, key, itemId, name, title, search;
		/** What it was built from (one of these is set): open its own tab's details from it. */
		public MoneyEngine.Row row;
		public Flipper.Flip flip;
		public CraftEngine.Craft craft;
		public CraftEngine.Sizing sizing;

		Amounts.Sizer sizer;
		Function<Long, Double> costAt, hrsAt;

		/** The best amount and what it adds up to. */
		public Amounts.Best best;
		public double coins, cost, pct, hrs, perHour;
		public String limit = "";

		@Override public double profitAt(long n) { return sizer.profitAt(n); }
		@Override public Long maxAfford(double c) { return sizer.maxAfford(c); }
		@Override public Long maxFit(int s) { return sizer.maxFit(s); }
		@Override public long slotsUsed(long n) { return sizer.slotsUsed(n); }
		@Override public Long hardCap() { return sizer.hardCap(); }
		@Override public String hardWhy() { return sizer.hardWhy(); }
		@Override public List<Long> breaks() { return sizer.breaks(); }

		public double costAt(long n) {
			return costAt.apply(n);
		}

		public double hrsAt(long n) {
			return hrsAt == null ? 0 : hrsAt.apply(n);
		}

		public String kind() {
			return SRC.get(src);
		}
	}

	/**
	 * Everything the advisor depends on. money = the NPC/forge/books settings (with Bazaar only on); flips = every flip;
	 * npcLoaded = NPC sell prices are there; show = "all" or one kind; exSrc / exItems = what you've left out.
	 */
	public record Ctx(MoneyEngine.Ctx money, Iterable<String> ids, List<Flipper.Flip> flips, boolean npcLoaded, CraftEngine crafts,
			Settings.Reqs reqs, Settings.Compactor compactor, String show, Set<String> exSrc, Set<String> exItems) {}

	/** planRows: every option that makes money at its best amount for your coins and slots. Empty without coins or slots. */
	public static List<Option> options(Ctx c) {
		MoneyEngine.Ctx m = c.money();
		double coins = m.coins();
		int slots = m.slots();
		List<Option> rows = new ArrayList<>();
		// Bazaar flips: buy order -> sell offer, about an hour of the slower side at most
		for (Flipper.Flip f : c.flips()) {
			if (f.risk().level() == Risk.Level.HIGH || f.weekVol() < CraftEngine.LOW_VOLUME) continue;
			Option o = new Option();
			o.src = "flip";
			o.key = "flip:" + f.id();
			o.itemId = f.id();
			o.name = f.name();
			o.title = f.name();
			o.flip = f;
			o.sizer = Flipper.sizing(f);
			o.costAt = n -> n * f.buyAt();
			o.hrsAt = n -> n / Math.max(f.buyFill(), 1e-9) + n / Math.max(f.sellFill(), 1e-9);
			rows.add(o);
		}
		MoneyEngine e = new MoneyEngine(m);
		// Bazaar -> NPC
		if (c.npcLoaded()) {
			for (MoneyEngine.Row r : e.npcFromBazaar(c.ids())) rows.add(fromRow("npc", "npc:" + r.key, r.itemId, r.title, r));
		}
		// NPC shop -> Bazaar
		for (MoneyEngine.Row r : e.npcShops()) {
			if (r.sellIt == null) continue;
			rows.add(fromRow("shop", "shop:" + r.key, r.itemId, r.title, r));
		}
		// Forge: one item per forge slot at a time
		for (MoneyEngine.Row r : e.forge()) {
			if (!r.sizable()) continue;
			rows.add(fromRow("forge", "forge:" + r.key, r.itemId, r.title, r));
		}
		// Book combining (books don't stack: 1 per slot)
		for (MoneyEngine.Row r : e.books(c.ids())) {
			Option o = fromRow("books", "books:" + r.key, r.key, r.title, r);
			o.name = r.title;
			rows.add(o);
		}
		// Crafts: walked through the order book, capped at about an hour of trading
		if (c.crafts() != null) {
			boolean sellOffer = m.sellOffer();
			List<CraftEngine.Craft> list = new ArrayList<>();
			for (CraftEngine.Craft k : c.crafts().crafts(m.buyOrder(), true)) {
				if (k.risk() == Risk.Level.HIGH || k.volume() < CraftEngine.LOW_VOLUME || !(k.cost() > 0)) continue;
				if (!CraftEngine.reqPasses(k, c.reqs())) continue;
				if (c.compactor() == Settings.Compactor.ONLY && !k.compactor()) continue;
				if (c.compactor() == Settings.Compactor.HIDE && k.compactor()) continue;
				if (!((sellOffer ? k.profitSellOffer() : k.profitInstaSell()) > 0)) continue;
				list.add(k);
			}
			list.sort((a, b) -> sellOffer ? Double.compare(b.profitSellOffer(), a.profitSellOffer()) : Double.compare(b.profitInstaSell(), a.profitInstaSell()));
			CraftEngine.Sell sell = sellOffer ? CraftEngine.Sell.OFFER : CraftEngine.Sell.INSTA;
			for (CraftEngine.Craft k : list.subList(0, Math.min(80, list.size()))) {
				CraftEngine.Sizing z = c.crafts().sizing(k, sell);
				BazaarPrices.Price prod = m.prices().apply(k.key());
				if (z == null || prod == null) continue;
				Option o = new Option();
				o.src = "craft";
				o.key = "craft:" + k.key();
				o.itemId = k.key();
				o.name = k.name();
				o.title = k.name();
				o.search = (k.name() + " " + k.key()).toLowerCase(Locale.ROOT);
				o.craft = k;
				o.sizing = z;
				o.sizer = new Amounts.Sizer() {
					@Override public double profitAt(long n) { return z.profitAt(n); }
					@Override public Long maxAfford(double co) { return z.maxAfford(co); }
					@Override public Long maxFit(int s) { return z.maxFit(s); }
					@Override public long slotsUsed(long n) { return z.slotsUsed(n); }
					@Override public Long hardCap() { return (long) z.maxN; }
					@Override public String hardWhy() { return z.hardWhy(); }
					@Override public List<Long> breaks() { return z.breaks(); }
				};
				o.costAt = z::costAt;
				Map<String, Double> leaf = CraftEngine.leaf(k, 1);
				o.hrsAt = n -> {
					double h = e.sellHrs(prod, (double) n * k.output());
					if (m.buyOrder()) {
						for (var l : leaf.entrySet()) {
							BazaarPrices.Price it = m.prices().apply(l.getKey());
							if (it != null) h = Math.max(h, MoneyEngine.buyHrs(it, l.getValue() * n, true) + e.sellHrs(prod, (double) n * k.output()));
						}
					}
					return h;
				};
				rows.add(o);
			}
		}
		// size every option to your coins / slots / caps and keep the ones that make money
		List<Option> out = new ArrayList<>();
		for (Option r : rows) {
			if (r.search == null) r.search = r.name.toLowerCase(Locale.ROOT);
			r.search += " " + SRC.get(r.src).toLowerCase(Locale.ROOT);
			if (!c.show().equals("all") && !r.src.equals(c.show())) continue;
			if (c.exSrc().contains(r.src) || c.exItems().contains(r.itemId)) continue;
			Amounts.Best bq = Amounts.best(r, coins, slots);
			if (bq == null || bq.n() < 1 || !(bq.profit() > 0)) continue;
			double cost = r.costAt(bq.n()), hrs = r.hrsAt(bq.n());
			r.best = bq;
			r.coins = bq.profit();
			r.pct = cost > 0 ? bq.profit() / cost * 100 : 0;
			r.hrs = hrs;
			r.cost = cost;
			r.perHour = hrs > 0 && Double.isFinite(hrs) ? bq.profit() / hrs : bq.profit() * 12;    // instant things: count as ~5 minutes
			r.limit = switch (bq.why()) {
				case "slots" -> "fills your " + Settings.freeSlots() + " free inventory slots";
				case "coins" -> "all your coins";
				case "price" -> "more would cost more than it earns";
				default -> r.hardWhy() == null ? "" : r.hardWhy();
			};
			out.add(r);
		}
		return out;
	}

	private static Option fromRow(String src, String key, String itemId, String title, MoneyEngine.Row r) {
		Option o = new Option();
		o.src = src;
		o.key = key;
		o.itemId = itemId;
		o.name = r.name;
		o.title = title;
		o.search = r.search;
		o.row = r;
		o.sizer = r;
		o.costAt = r.costAt;
		o.hrsAt = r.hrsAt;
		return o;
	}

	// ---------------------------------------------------------------- the suggested split (planSteps)

	public record Step(Option option, long n, double cost, double profit) {}

	public record Steps(List<Step> steps, double left) {
		public double profit() {
			double t = 0;
			for (Step s : steps) t += s.profit();
			return t;
		}
	}

	/** The biggest profit first, then the next best with the coins that are left (up to 3, one per item). */
	public static Steps steps(List<Option> rows, double coins, int slots) {
		double left = coins;
		List<Step> steps = new ArrayList<>();
		Set<String> used = new HashSet<>();
		List<Option> sorted = new ArrayList<>(rows);
		sorted.sort((a, b) -> Double.compare(b.coins, a.coins));
		for (Option r : sorted) {
			if (steps.size() >= 3 || left < 1) break;
			if (used.contains(r.itemId)) continue;
			Amounts.Best bq = Amounts.best(r, Math.floor(left), slots);
			if (bq == null || bq.n() < 1 || !(bq.profit() > 0)) continue;
			double cost = r.costAt(bq.n());
			if (cost > left) continue;
			steps.add(new Step(r, bq.n(), cost, bq.profit()));
			left -= cost;
			used.add(r.itemId);
		}
		return new Steps(steps, left);
	}

	// ---------------------------------------------------------------- sorting (moneyList)

	public static final List<CraftEngine.SortOption> SORTS = List.of(
			new CraftEngine.SortOption("coins", "Most profit for your coins", "Most profit",
					"The most profit each idea makes at its best amount for your coins and free slots."),
			new CraftEngine.SortOption("coinsSO", "Best for your coins & slots · sell offer", "Best · sell offer",
					"Most profit for your coins, selling with sell offers (also switches Sell to sell offers)."),
			new CraftEngine.SortOption("coinsIS", "Best for your coins & slots · insta-sell", "Best · insta-sell",
					"Most profit for your coins, insta-selling (also switches Sell to insta-sell)."),
			new CraftEngine.SortOption("perHour", "Profit / hr", "Profit / hr", "Profit divided by how long the best amount takes (instant things count as about 5 minutes)."),
			new CraftEngine.SortOption("pct", "Return %", "Return %", "Profit as a % of the coins it uses."),
			new CraftEngine.SortOption("time", "Quickest", "Quickest", "Shortest time first (instant ones at the top)."),
			new CraftEngine.SortOption("name", "Name", "Name", "Alphabetical."));

	public static List<Option> sorted(List<Option> rows, String search, String sortKey) {
		String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		List<Option> list = new ArrayList<>();
		for (Option r : rows) if (q.isEmpty() || r.search.contains(q)) list.add(r);
		Comparator<Option> f = switch (sortKey) {
			case "perHour" -> (a, b) -> Double.compare(b.perHour, a.perHour);
			case "pct" -> (a, b) -> Double.compare(b.pct, a.pct);
			case "time" -> (a, b) -> Double.compare(a.hrs, b.hrs);
			case "name" -> Comparator.comparing(o -> o.name);
			default -> (a, b) -> Double.compare(b.coins, a.coins);
		};
		list.sort(f);
		return list;
	}
}
