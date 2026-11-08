package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The website's NPC flips and Forge tabs (renderNpc, renderForge and their helpers), ported function by
 * function. Unlike the Craft tabs, these use Hypixel's averaged prices (quick_status) like the site does,
 * and walk the order book for insta-buys / insta-sells of bigger amounts.
 * No Minecraft code, so it can be tested on its own.
 */
public final class MoneyEngine {
	/** Everything a list depends on. buyOrder = buy materials with buy orders; sellOffer = sell with sell offers. */
	public record Ctx(Function<String, BazaarPrices.Price> prices, Function<String, Double> npcSell, Function<String, Double> ahBin,
			double tax, boolean buyOrder, boolean sellOffer, boolean bazaarOnly, String npcAvail, int forgeHotm,
			double coins, int slots, java.util.Set<String> flipped) {
		/** The list's settings, every item bought the same way. */
		public Ctx(Function<String, BazaarPrices.Price> prices, Function<String, Double> npcSell, Function<String, Double> ahBin,
				double tax, boolean buyOrder, boolean sellOffer, boolean bazaarOnly, String npcAvail, int forgeHotm, double coins, int slots) {
			this(prices, npcSell, ahBin, tax, buyOrder, sellOffer, bazaarOnly, npcAvail, forgeHotm, coins, slots, java.util.Set.of());
		}

		/** How this item is bought: the Buy setting, unless you switched it for this item on its details screen. */
		public boolean orderFor(String id) {
			return buyOrder != flipped.contains(id);
		}
	}

	/** One line of a cost table: qty x item at a price, from the Bazaar or the AH. */
	public record Part(String id, String label, double qty, double unit, double total, String src) {}

	/** One line of the "how many?" calculator: what, coins, colour hint (selling = green), how. */
	public record CalcLine(String what, String coins, boolean selling, String how) {}

	/** One row of the NPC or Forge list (a site card), with what's needed for its details. */
	public static final class Row implements Amounts.Sizer {
		public String key, itemId, name, search, title, flag, req = "";
		public double profit, pct, perHour, costEach, value, vol, week;
		/** Currencies: coins per one of the currency, and how many it costs. */
		public double per;
		public long amt;
		public long time;
		public List<String> meta = new ArrayList<>();
		public List<String[]> stats = new ArrayList<>();
		public List<Part> parts = List.of();
		public String partsLabel = "", note = "";
		public String qtyLabel = "Buy", qtyUnit = "items";
		public long defQty = 1;
		public long[] presets = {64, 640, 2240};
		public BazaarPrices.Price market;     // shown as market stats + order book in details (NPC bazaar view)
		/** The best amount for your coins & slots (null without coins/slots or when it can't be sized). */
		public Amounts.Best best;
		/** What sorts by "Best for your coins & slots" use: the best profit, or the plain profit if it can't be sized. */
		public double coins;
		boolean sized;
		Function<Long, Double> profitAt;
		Function<Double, Long> maxAfford;
		Function<Integer, Long> maxFit;
		Function<Long, Long> slotsUsed;
		java.util.function.Supplier<List<Long>> breaks = List::of;
		Long hardCap;
		String hardWhy = "";
		Function<Long, Calc> calc;

		@Override public double profitAt(long n) { return profitAt.apply(n); }
		@Override public Long maxAfford(double c) { return maxAfford == null ? null : maxAfford.apply(c); }
		@Override public Long maxFit(int s) { return maxFit == null ? null : maxFit.apply(s); }
		@Override public long slotsUsed(long n) { return slotsUsed == null ? 0 : slotsUsed.apply(n); }
		@Override public Long hardCap() { return hardCap; }
		@Override public String hardWhy() { return hardWhy; }
		@Override public List<Long> breaks() { return breaks.get(); }

		/** Can it be sized for coins/slots (has an order book to walk)? */
		public boolean sizable() { return sized; }

		/** The calculator for n (null when there isn't one). */
		public Calc calc(long n) { return calc == null ? null : calc.apply(n); }
	}

	/** The calculator's result for an amount: its lines and the profit. */
	public record Calc(List<CalcLine> lines, double profit, String footnote) {}

	private final Ctx x;

	public MoneyEngine(Ctx x) {
		this.x = x;
	}

	// ---------------------------------------------------------------- prices (buyUnit, sellUnit, costOf)

	private record Unit(double u, String src) {}

	private Unit buyUnit(String id) {
		String mk = SiteData.mktId(id, x.prices());
		BazaarPrices.Price p = x.prices().apply(SiteData.bzId(mk));
		if (p != null && p.quickBuy() > 0 && p.quickSell() > 0) return new Unit(x.orderFor(id) ? p.quickSell() : p.quickBuy(), "Bazaar");
		Double l = x.ahBin().apply(mk);
		return l != null && l > 0 ? new Unit(l, "AH") : null;
	}

	private Unit sellUnit(String id) {
		String mk = SiteData.mktId(id, x.prices());
		BazaarPrices.Price p = x.prices().apply(SiteData.bzId(mk));
		if (p != null && p.quickBuy() > 0 && p.quickSell() > 0) return new Unit((x.sellOffer() ? p.quickBuy() : p.quickSell()) * (1 - x.tax()), "Bazaar");
		Double l = x.ahBin().apply(mk);
		return l != null && l > 0 ? new Unit(l, "AH") : null;
	}

	private record Cost(double total, List<Part> parts) {}

	/** Cost of materials + coins; null if anything has no price. */
	private Cost costOf(List<SiteData.Mat> list, double coins) {
		double total = coins;
		List<Part> parts = new ArrayList<>();
		if (coins != 0) parts.add(new Part(null, "Coins", coins, 1, coins, ""));
		for (SiteData.Mat m : list) {
			Unit b = buyUnit(m.id());
			if (b == null) return null;
			total += b.u() * m.qty();
			parts.add(new Part(m.id(), SiteData.itemName(m.id()), m.qty(), b.u(), b.u() * m.qty(), b.src()));
		}
		return new Cost(total, List.copyOf(parts));
	}

	// ---------------------------------------------------------------- walking the bazaar (bzBuyCost, bzSellGet)

	public record Trade(double total, String note) {}

	private static double lastPrice(List<BazaarPrices.Level> lv, double fallback) {
		return lv.isEmpty() ? fallback : lv.get(lv.size() - 1).price();
	}

	/** Coins to buy n on the bazaar (buy order at the quick price, or insta-buy walking the sell offers). */
	Trade bzBuyCost(BazaarPrices.Price it, double n, boolean order) {
		if (order) return new Trade(n * it.quickSell(), "buy order at " + Format.price(it.quickSell()) + " each, fills over time");
		CraftEngine.BookWalk w = CraftEngine.walkBook(it.sellOffers(), n);
		double total = w.total();
		if (w.shortBy() > 0) total += w.shortBy() * lastPrice(it.sellOffers(), it.quickBuy());
		return new Trade(total, "insta-buy, avg " + Format.price(total / n) + " each"
				+ (w.shortBy() > 0 ? "; only " + Format.count(w.filled()) + " visible in the top " + it.sellOffers().size()
						+ " sell offers, the rest is estimated at the last price" : ""));
	}

	/** Coins you get selling n on the bazaar, after tax. */
	Trade bzSellGet(BazaarPrices.Price it, double n) {
		if (x.sellOffer()) return new Trade(n * it.quickBuy() * (1 - x.tax()), "sell offer at " + Format.price(it.quickBuy()) + " each, fills over time");
		CraftEngine.BookWalk w = CraftEngine.walkBook(it.buyOrders(), n);
		double total = w.total();
		if (w.shortBy() > 0) total += w.shortBy() * lastPrice(it.buyOrders(), it.quickSell());
		return new Trade(total * (1 - x.tax()), "insta-sell, avg " + Format.price(total / n) + " each before tax"
				+ (w.shortBy() > 0 ? "; only " + Format.count(w.filled()) + " wanted in the top " + it.buyOrders().size()
						+ " buy orders, the rest is estimated at the last price" : ""));
	}

	private static double perHr(long week) {
		return week / 168.0;
	}

	private static double bookDepth(List<BazaarPrices.Level> lv) {
		double t = 0;
		for (BazaarPrices.Level l : lv) t += l.amount();
		return t;
	}

	/** [cap, why] for buying `per` at a time (capBuy). */
	private Object[] capBuy(BazaarPrices.Price it, double per, boolean order) {
		return order ? new Object[] {Math.max(1, Math.floor(perHr(it.sellMovingWeek()) / per)), "about an hour of buy orders filling"}
				: new Object[] {Math.floor(bookDepth(it.sellOffers()) / per), "what's for sale in the order book now"};
	}

	private Object[] capSell(BazaarPrices.Price it, double per) {
		return x.sellOffer() ? new Object[] {Math.max(1, Math.floor(perHr(it.buyMovingWeek()) / per)), "about an hour of sell offers filling"}
				: new Object[] {Math.floor(bookDepth(it.buyOrders()) / per), "what buyers want in the order book now"};
	}

	/** capMin: the tightest of several caps. */
	private static void capMin(Row r, Object[]... caps) {
		double best = Double.POSITIVE_INFINITY;
		String why = "";
		for (Object[] c : caps) {
			if (c != null && (double) c[0] < best) { best = (double) c[0]; why = (String) c[1]; }
		}
		if (best != Double.POSITIVE_INFINITY) { r.hardCap = (long) best; r.hardWhy = why; }
	}

	private static long affordSearch(Function<Long, Double> costAt, double b) {
		long lo = 0, hi = 10_000_000;
		while (lo < hi) {
			long mid = (lo + hi + 1) / 2;
			if (costAt.apply(mid) <= b) lo = mid;
			else hi = mid - 1;
		}
		return lo;
	}

	public String taxLabel() {
		return String.format(Locale.US, "%.3f", x.tax() * 100).replaceAll("0+$", "").replaceAll("\\.$", "") + "%";
	}

	// ---------------------------------------------------------------- Forge (renderForge + forgeSizing)

	/** Forge slots for your HotM tier (forgeSlots): 2 when not set, none below tier 2, up to 7. */
	public static int forgeSlots(int hotm) {
		return hotm == 0 ? 2 : hotm < 2 ? 0 : Math.min(hotm, 7);
	}

	public List<Row> forge() {
		List<Row> rows = new ArrayList<>();
		for (SiteData.Forge r : SiteData.forge()) {
			if (x.forgeHotm() != 0 && r.hotm() > x.forgeHotm()) continue;
			Cost cost = costOf(r.in(), r.coins());
			Unit s = sellUnit(r.id());
			if (cost == null || s == null || (x.bazaarOnly() && s.src().equals("AH"))) continue;
			Row row = new Row();
			double value = s.u() * r.n(), profit = value - cost.total(), hrs = r.t() / 3600.0;
			row.key = r.id();
			row.itemId = r.id();
			row.name = SiteData.itemName(r.id());
			row.search = (row.name + " " + r.id()).toLowerCase(Locale.ROOT);
			row.costEach = cost.total();
			row.value = value;
			row.profit = profit;
			row.perHour = profit / Math.max(hrs, 1.0 / 120);
			row.pct = cost.total() > 0 ? profit / cost.total() * 100 : 0;
			row.time = r.t();
			row.req = r.req();
			row.title = row.name + (r.n() > 1 ? " x" + r.n() : "");
			row.flag = Amounts.duration(r.t());
			row.meta.add(s.src().equals("AH") ? "sells on AH" : "sells on Bazaar");
			row.stats.add(new String[] {"Cost", Format.coins(cost.total())});
			row.stats.add(new String[] {"Sells for", Format.coins(value)});
			row.stats.add(new String[] {"Profit", Format.signedCoins(profit)});
			row.stats.add(new String[] {"Profit / hr", Format.signedCoins(row.perHour)});
			row.parts = cost.parts();
			row.partsLabel = "Materials";
			row.note = "Takes " + Amounts.duration(r.t()) + " in one forge slot"
					+ (r.t() <= 60 ? " (NEU lists this as near-instant; Hypixel shortened many forge times, but double-check in game)" : "")
					+ ". Sells for " + Format.price(s.u()) + " each on the " + (s.src().equals("AH") ? "AH (lowest BIN, before AH fees)" : "Bazaar")
					+ ". Quick Forge and other time perks aren't counted.";
			String mid = SiteData.marketId(r.id(), x.prices());
			BazaarPrices.Price sellIt = x.prices().apply(mid);
			if (s.src().equals("Bazaar")) forgeSizing(row, r, cost.total(), sellIt);
			rows.add(row);
		}
		addCoinsInfo(rows);
		return rows;
	}

	private void forgeSizing(Row row, SiteData.Forge rec, double costEach, BazaarPrices.Price sellIt) {
		int fs = forgeSlots(x.forgeHotm());
		if (!(fs > 0) || sellIt == null || !(costEach > 0)) return;
		int out = rec.n();
		row.sized = true;
		row.profitAt = n -> bzSellGet(sellIt, n * out).total() - n * costEach;
		row.maxAfford = c -> (long) Math.floor(c / costEach);
		row.maxFit = s -> {
			long n = 0;
			while (n < 10000 && forgeSlotsUsed(rec, n + 1) <= s) n++;
			return n;
		};
		row.slotsUsed = n -> forgeSlotsUsed(rec, n);
		row.breaks = () -> x.sellOffer() ? List.of() : Amounts.cumBreaks(sellIt.buyOrders(), out);
		capMin(row, new Object[] {(double) fs, "your " + fs + " forge slot" + (fs == 1 ? "" : "s")}, capSell(sellIt, out));
		row.defQty = 1;
		row.qtyLabel = "Forge";
		row.qtyUnit = "at once";
		row.presets = fs == 1 ? new long[] {1} : new long[] {1, fs};
		row.calc = n -> {
			Trade g = bzSellGet(sellIt, n * out);
			List<CalcLine> lines = List.of(
					new CalcLine("Materials for " + Format.count(n) + " forge" + (n == 1 ? "" : "s"), Format.coins(n * costEach), false,
							n + " of your " + fs + " forge slots" + (n > fs ? "; more than you have, so it takes " + ceilDiv(n, fs) + " rounds" : "")),
					new CalcLine("Wait", Amounts.duration(rec.t() * ceilDiv(n, fs)), false,
							n > fs ? ceilDiv(n, fs) + " rounds" : "all slots run at the same time"),
					new CalcLine("Sell " + Format.count(n * out) + " on the Bazaar", Format.coins(g.total()), true, g.note() + ", after " + taxLabel() + " tax"));
			return new Calc(lines, g.total() - n * costEach, "");
		};
	}

	private static long forgeSlotsUsed(SiteData.Forge rec, long n) {
		long t = 0;
		for (SiteData.Mat m : rec.in()) t += (long) Math.ceil((double) m.qty() * n / SiteData.stackOf(m.id(), false));
		return t;
	}

	private static long ceilDiv(long a, long b) {
		return (a + b - 1) / b;
	}

	// ---------------------------------------------------------------- NPC flips: Bazaar -> NPC

	public List<Row> npcFromBazaar(Iterable<String> productIds) {
		List<Row> rows = new ArrayList<>();
		for (String id : productIds) {
			BazaarPrices.Price it = x.prices().apply(id);
			Double npc = x.npcSell().apply(id);
			if (it == null || npc == null || !(npc > 0) || !(it.quickBuy() > 0)) continue;
			boolean order = x.orderFor(id);
			double cost = order ? it.quickSell() : it.quickBuy();
			if (!(cost > 0)) continue;
			double profit = npc - cost;
			if (profit <= 0) continue;
			Row row = new Row();
			row.key = id;
			row.itemId = id;
			row.name = SiteData.prettyName(id);
			row.search = (row.name + " " + id).toLowerCase(Locale.ROOT);
			row.profit = profit;
			row.pct = profit / cost * 100;
			row.costEach = cost;
			row.vol = it.itemsInSellOffers();
			row.week = it.buyMovingWeek();
			row.market = it;
			row.title = row.name;
			row.flag = "Bazaar -> NPC";
			row.meta.add(Format.amount(it.itemsInSellOffers()) + " for sale now");
			row.meta.add(Format.amount(it.buyMovingWeek()) + " insta-bought / week");
			row.stats.add(new String[] {"Buy for", Format.price(cost)});
			row.stats.add(new String[] {"NPC pays", Format.price(npc)});
			row.stats.add(new String[] {"Profit each", Format.signedCoins(profit)});
			row.stats.add(new String[] {"Return", pctStr(profit / cost * 100)});
			row.note = (order ? "Place a buy order at about " + Format.price(cost) + " and wait for it to fill"
					: "Insta-buy at " + Format.price(cost) + " (the price rises as you eat through the cheapest sell offers)")
					+ ", then sell to any NPC for " + Format.price(npc) + " each. Selling to NPCs has no tax. Hypixel may limit how much "
					+ "you can sell to NPCs, and these gaps tend to close fast.";
			row.defQty = 64;
			row.sized = true;
			int st = SiteData.stackOf(id, false);
			capMin(row, capBuy(it, 1, order));
			row.profitAt = n -> n * npc - bzBuyCost(it, n, order).total();
			row.maxAfford = b -> affordSearch(n -> bzBuyCost(it, n, order).total(), b);
			row.maxFit = sl -> (long) sl * st;
			row.slotsUsed = n -> ceilDiv(n, st);
			row.breaks = () -> order ? List.of() : Amounts.cumBreaks(it.sellOffers(), 1);
			row.calc = n -> {
				Trade b = bzBuyCost(it, n, order);
				double get = n * npc;
				return new Calc(List.of(
						new CalcLine("Buy " + Format.count(n) + " on the Bazaar", Format.coins(b.total()), false, b.note()),
						new CalcLine("Sell " + Format.count(n) + " to an NPC", Format.coins(get), true, Format.price(npc) + " each, no tax"),
						invLine(ceilDiv(n, st), st)), get - b.total(), "");
			};
			rows.add(row);
		}
		addCoinsInfo(rows);
		return rows;
	}

	// ---------------------------------------------------------------- NPC flips: NPC shop -> Bazaar / AH

	/** npcAllowed: "always" hides event, mayor and situational NPCs; "nomayor" allows situational; "all" allows everything. */
	public static boolean npcAllowed(String npc, String setting) {
		String[] a = SiteData.npcAvail(npc);
		if (a == null || setting.equals("all")) return true;
		if (setting.equals("nomayor")) return a[0].equals("situational");
		return false;
	}

	public int hiddenShops;

	public List<Row> npcShops() {
		List<Row> rows = new ArrayList<>();
		hiddenShops = 0;
		for (SiteData.Shop r : SiteData.shops()) {
			if (!npcAllowed(r.npc(), x.npcAvail())) { hiddenShops++; continue; }
			Cost cost = costOf(r.in(), r.coins());
			Unit s = sellUnit(r.id());
			if (cost == null || s == null || !(cost.total() > 0) || (x.bazaarOnly() && s.src().equals("AH"))) continue;
			double value = s.u() * r.q(), profit = value - cost.total();
			if (profit <= 0) continue;
			Row row = new Row();
			BazaarPrices.Price sellIt = x.prices().apply(SiteData.marketId(r.id(), x.prices()));
			boolean bz = s.src().equals("Bazaar") && sellIt != null;
			row.key = "s" + r.index();
			row.itemId = r.id();
			row.name = SiteData.itemName(r.id());
			row.search = (row.name + " " + r.id() + " " + SiteData.npcName(r.npc())).toLowerCase(Locale.ROOT);
			row.profit = profit;
			row.pct = profit / cost.total() * 100;
			row.costEach = cost.total();
			row.value = value;
			row.title = row.name + (r.q() > 1 ? " x" + r.q() : "");
			row.flag = "NPC -> " + (s.src().equals("AH") ? "AH" : "Bazaar");
			String where = SiteData.npcWhere(r.npc());
			row.meta.add(SiteData.npcName(r.npc()) + (where.isEmpty() ? " (location not listed)" : " · " + where));
			String[] av = SiteData.npcAvail(r.npc());
			if (av != null) row.meta.add((av[0].equals("event") ? "Seasonal" : av[0].equals("mayor") ? "Mayor" : "Situational") + ": " + av[1]);
			row.stats.add(new String[] {"NPC cost", Format.coins(cost.total())});
			row.stats.add(new String[] {"Sells for", Format.coins(value)});
			row.stats.add(new String[] {"Profit", Format.signedCoins(profit)});
			row.stats.add(new String[] {"Return", pctStr(profit / cost.total() * 100)});
			row.parts = cost.parts();
			row.partsLabel = "Cost at " + SiteData.npcName(r.npc());
			row.note = "Buy from " + SiteData.npcName(r.npc()) + (where.isEmpty() ? "" : " (" + where + ")") + ", then sell "
					+ (s.src().equals("AH") ? "on the AH (lowest BIN " + Format.price(s.u()) + ", before AH fees)"
							: "on the Bazaar for " + Format.price(s.u()) + " each after tax")
					+ ". Many NPCs limit how many you can buy per day, and some need you to unlock the NPC first.";
			int st = SiteData.stackOf(r.id(), !s.src().equals("Bazaar"));
			row.sized = true;
			if (bz) capMin(row, capSell(sellIt, r.q()));
			row.defQty = r.q() > 1 ? Math.max(1, 64 / r.q()) : 64;
			row.qtyUnit = r.q() > 1 ? "times (x" + r.q() + " each)" : "items";
			double costEach = cost.total(), su = s.u();
			row.profitAt = n -> {
				double items = (double) n * r.q();
				double get = bz ? bzSellGet(sellIt, items).total() : items * su;
				return get - n * costEach;
			};
			row.maxAfford = b -> Math.min(10_000_000L, (long) Math.floor(b / costEach));
			row.maxFit = sl -> (long) Math.floor((double) sl * st / r.q());
			row.slotsUsed = n -> (long) Math.ceil((double) n * r.q() / st);
			row.breaks = () -> bz && !x.sellOffer() ? Amounts.cumBreaks(sellIt.buyOrders(), r.q()) : List.of();
			row.calc = n -> {
				long items = n * r.q();
				double pay = n * costEach, get;
				String note;
				if (bz) {
					Trade g = bzSellGet(sellIt, items);
					get = g.total();
					note = g.note() + ", after " + taxLabel() + " tax";
				} else {
					get = items * su;
					note = "AH lowest BIN " + Format.price(su) + " each, before AH fees; listings may take a while to sell";
				}
				String paid = (r.coins() != 0 ? Format.coins(r.coins()) + " coins" : "") + (r.in().isEmpty() ? "" : (r.coins() != 0 ? " + " : "") + "materials");
				return new Calc(List.of(
						new CalcLine("Buy " + (r.q() > 1 ? Format.count(n) + "x (" + Format.count(items) + " items)" : Format.count(items)) + " from "
								+ SiteData.npcName(r.npc()), Format.coins(pay), false, paid + " each time"),
						new CalcLine("Sell " + Format.count(items) + " on the " + (s.src().equals("Bazaar") ? "Bazaar" : "AH"), Format.coins(get), true, note),
						invLine((long) Math.ceil((double) items / st), st)), get - pay,
						items > 640 ? "Heads up: many NPCs cap how many you can buy per day (the wiki lists 640 a day for some shops), "
								+ "so this may take more than one day." : "");
			};
			rows.add(row);
		}
		addCoinsInfo(rows);
		return rows;
	}

	private CalcLine invLine(long used, int stack) {
		int sl = x.slots();
		return new CalcLine("Inventory space", used + " slot" + (used == 1 ? "" : "s"), false,
				(stack == 1 ? "doesn't stack" : "stacks of " + stack)
						+ (sl > 0 ? (used > sl ? "; more than your " + sl + " free slots, so it takes " + ceilDiv(used, sl) + " trips"
								: " · you have " + sl + " free") : ""));
	}

	static String pctStr(double p) {
		return (p >= 0 ? "+" : "-") + String.format(Locale.US, "%.1f", Math.abs(p)) + "%";
	}

	/** addCoinsInfo: each sizable row's best amount for your coins & slots; coins = that profit (or the plain profit). */
	private void addCoinsInfo(List<Row> rows) {
		for (Row r : rows) {
			r.best = r.sized ? Amounts.best(r, x.coins(), x.slots()) : null;
			r.coins = r.best != null ? r.best.profit() : r.profit;
		}
	}

	/** The amount the calculator starts at: the best amount, else the usual default. */
	public long startQty(Row r) {
		return r.best != null && r.best.n() > 0 ? r.best.n() : r.defQty;
	}

	/** The most your coins, slots and the market allow (the "Max" button), or null without coins/slots. */
	public Long maxQty(Row r) {
		if (!r.sized || (x.coins() <= 0 && x.slots() <= 0)) return null;
		double cap = Double.POSITIVE_INFINITY;
		if (x.coins() > 0) { Long a = r.maxAfford(x.coins()); if (a != null) cap = Math.min(cap, a); }
		if (x.slots() > 0) { Long f = r.maxFit(x.slots()); if (f != null) cap = Math.min(cap, f); }
		if (r.hardCap() != null) cap = Math.min(cap, r.hardCap());
		return cap == Double.POSITIVE_INFINITY ? null : (long) cap;
	}

	// ---------------------------------------------------------------- Books (renderBooks + bookSizing)

	private static final java.util.regex.Pattern BOOK_ID = java.util.regex.Pattern.compile("^ENCHANTMENT_(.+)_(\\d+)$");

	/** enchTableMax: the highest level the enchanting table gives (0 = not a table enchant). */
	static int enchTableMax(String key) {
		com.google.gson.JsonObject m = SiteData.extra("enchMax").getAsJsonObject(), map = SiteData.extra("enchMap").getAsJsonObject();
		String up = key.toUpperCase(Locale.ROOT);
		for (String k : new String[] {key, up, map.has(key) ? map.get(key).getAsString() : null, map.has(up) ? map.get(up).getAsString() : null}) {
			if (k != null && m.has(k)) return m.get(k).getAsInt();
		}
		return 0;
	}

	/** Two level-N books make one level N+1 in an anvil (up to the enchanting table's max level). */
	public List<Row> books(Iterable<String> productIds) {
		java.util.Map<String, java.util.TreeMap<Integer, String>> lv = new java.util.LinkedHashMap<>();
		for (String id : productIds) {
			java.util.regex.Matcher m = BOOK_ID.matcher(id);
			if (m.matches() && x.prices().apply(id) != null) lv.computeIfAbsent(m.group(1), k -> new java.util.TreeMap<>()).put(Integer.parseInt(m.group(2)), id);
		}
		List<Row> rows = new ArrayList<>();
		for (var e : lv.entrySet()) {
			String E = e.getKey();
			if (E.startsWith("ULTIMATE_")) continue;       // different combining rules; not verified
			int max = enchTableMax(E.toLowerCase(Locale.ROOT));
			if (max == 0) continue;
			for (int n : e.getValue().keySet()) {
				String aId = e.getValue().get(n), bId = e.getValue().get(n + 1);
				if (bId == null || n + 1 > max) continue;
				BazaarPrices.Price a = x.prices().apply(aId), b = x.prices().apply(bId);
				boolean order = x.orderFor(aId);
				double cost = 2 * (order ? a.quickSell() : a.quickBuy()), value = (x.sellOffer() ? b.quickBuy() : b.quickSell()) * (1 - x.tax());
				if (!(cost > 0) || !(value > 0)) continue;
				double profit = value - cost;
				String nm = Recipes.fallbackName(E);
				Row row = new Row();
				row.key = bId;
				row.itemId = bId;
				row.name = nm;
				row.search = (nm + " " + bId).toLowerCase(Locale.ROOT);
				row.profit = profit;
				row.pct = profit / cost * 100;
				row.costEach = cost;
				row.value = value;
				row.vol = Math.min(a.buyMovingWeek(), b.sellMovingWeek());
				row.market = b;
				String rn = SiteData.roman(n), rn1 = SiteData.roman(n + 1);
				row.title = nm + " " + rn + " + " + rn + " -> " + rn1;
				row.flag = "Anvil";
				row.meta.add(Format.amount(a.buyMovingWeek()) + " " + rn + " bought / week");
				row.meta.add(Format.amount(b.sellMovingWeek()) + " " + rn1 + " sold / week");
				row.stats.add(new String[] {"2x " + rn + " cost", Format.coins(cost)});
				row.stats.add(new String[] {rn1 + " sells for", Format.coins(value)});
				row.stats.add(new String[] {"Profit", Format.signedCoins(profit)});
				row.stats.add(new String[] {"Return", pctStr(profit / cost * 100)});
				row.note = "Buy two " + nm + " " + rn + " books, combine them in an anvil (costs some XP levels), sell the " + nm + " " + rn1
						+ ". Only combinations up to the enchanting table's max level (" + SiteData.roman(max) + " here) are shown, since those are "
						+ "sure to work; Ultimate enchants and higher levels follow different rules and are left out.";
				// bookSizing
				row.sized = true;
				row.profitAt = k -> bzSellGet(b, k).total() - bzBuyCost(a, 2 * k, order).total();
				row.maxAfford = c -> affordSearch(k -> bzBuyCost(a, 2 * k, order).total(), c);
				row.maxFit = sl -> (long) Math.floor(sl / 2.0);
				row.slotsUsed = k -> 2 * k;
				row.breaks = () -> {
					List<Long> br = new ArrayList<>(order ? List.of() : Amounts.cumBreaks(a.sellOffers(), 2));
					if (!x.sellOffer()) br.addAll(Amounts.cumBreaks(b.buyOrders(), 1));
					return br;
				};
				capMin(row, capBuy(a, 2, order), capSell(b, 1));
				row.defQty = 1;
				row.qtyLabel = "Combine";
				row.qtyUnit = "times";
				row.presets = new long[] {1, 8, 32};
				row.calc = k -> {
					Trade c = bzBuyCost(a, 2 * k, order), g = bzSellGet(b, k);
					return new Calc(List.of(
							new CalcLine("Buy " + Format.count(2 * k) + " books", Format.coins(c.total()), false, c.note()),
							new CalcLine("Combine " + Format.count(k) + "x in an anvil", "XP", false, "costs XP levels, not coins"),
							new CalcLine("Sell " + Format.count(k), Format.coins(g.total()), true, g.note() + ", after " + taxLabel() + " tax"),
							new CalcLine("Inventory space", Format.count(2 * k) + " slots", false, "books don't stack")), g.total() - c.total(), "");
				};
				rows.add(row);
			}
		}
		addCoinsInfo(rows);
		return rows;
	}

	// ---------------------------------------------------------------- Currencies (renderCur)

	/** Currency key -> {label, one, many}, as on the site. */
	public static final java.util.LinkedHashMap<String, String[]> CURRENCIES = new java.util.LinkedHashMap<>();
	static {
		String[][] c = {{"copper", "Copper", "copper", "copper"}, {"gold", "Gold medals", "gold medal", "gold medals"},
				{"silver", "Silver medals", "silver medal", "silver medals"}, {"bronze", "Bronze medals", "bronze medal", "bronze medals"},
				{"pelt", "Pelts", "pelt", "pelts"}, {"northstar", "North Stars", "North Star", "North Stars"}, {"pest", "Pests", "pest", "pests"},
				{"rusty", "Rusty Coins", "Rusty Coin", "Rusty Coins"}, {"bingo", "Bingo Points", "Bingo Point", "Bingo Points"},
				{"kernel", "Kernels", "kernel", "kernels"}, {"carnival", "Carnival Tokens", "Carnival Token", "Carnival Tokens"},
				{"gem", "SkyBlock Gems (bought with real money)", "Gem", "Gems"}};
		for (String[] r : c) CURRENCIES.put(r[0], new String[] {r[1], r[2], r[3]});
	}

	public int unsellable, viaAH;

	/** What to buy with a currency, ranked by coins per one of it. */
	public List<Row> currencies(String type) {
		String[] names = CURRENCIES.getOrDefault(type, CURRENCIES.get("copper"));
		String one = names[1], many = names[2];
		List<Row> rows = new ArrayList<>();
		unsellable = 0;
		viaAH = 0;
		com.google.gson.JsonArray all = SiteData.extra("cur").getAsJsonArray();
		for (int i = 0; i < all.size(); i++) {
			com.google.gson.JsonObject r = all.get(i).getAsJsonObject();
			if (!r.get("cur").getAsString().equals(type)) continue;
			String id = r.get("id").getAsString(), npc = r.get("npc").getAsString();
			int q = r.get("q").getAsInt();
			long amt = r.get("amt").getAsLong();
			List<SiteData.Mat> in = new ArrayList<>();
			if (r.has("in")) for (var m : r.getAsJsonArray("in")) in.add(new SiteData.Mat(m.getAsJsonArray().get(0).getAsString(), m.getAsJsonArray().get(1).getAsInt()));
			Unit s = sellUnit(id);
			Cost extra = costOf(in, r.has("c") ? r.get("c").getAsDouble() : 0);
			if (s == null || extra == null) { unsellable++; continue; }
			if (x.bazaarOnly() && s.src().equals("AH")) { viaAH++; continue; }
			double value = s.u() * q - extra.total(), per = value / amt;
			Row row = new Row();
			row.key = "c" + i;
			row.itemId = id;
			row.name = SiteData.itemName(id);
			row.search = (row.name + " " + id + " " + SiteData.npcName(npc)).toLowerCase(Locale.ROOT);
			row.per = per;
			row.value = value;
			row.amt = amt;
			row.profit = value;
			row.title = row.name + (q > 1 ? " x" + q : "");
			String cost = Format.count(amt) + " " + (amt == 1 ? one : many);
			row.flag = cost;
			String where = SiteData.npcWhere(npc);
			row.meta.add(SiteData.npcName(npc) + (where.isEmpty() ? " (location not listed)" : " · " + where));
			String[] av = SiteData.npcAvail(npc);
			if (av != null) row.meta.add((av[0].equals("event") ? "Seasonal" : av[0].equals("mayor") ? "Mayor" : "Situational") + ": " + av[1]);
			row.meta.add("sells on " + (s.src().equals("AH") ? "AH" : "Bazaar"));
			if (extra.total() > 0) row.meta.add("plus " + Format.coins(extra.total()) + " in coins/items");
			row.stats.add(new String[] {"Costs", cost});
			row.stats.add(new String[] {"Sells for", Format.coins(s.u() * q)});
			row.stats.add(new String[] {"Coins / " + one, Format.price(per)});
			row.stats.add(new String[] {"Net value", Format.signedCoins(value)});
			row.parts = extra.parts();
			row.partsLabel = "Extra cost besides " + many;
			row.note = "Buy from " + SiteData.npcName(npc) + (where.isEmpty() ? "" : " (" + where + ")") + " for " + cost
					+ (extra.total() > 0 ? " plus " + Format.coins(extra.total()) : "") + ", then sell "
					+ (s.src().equals("AH") ? "on the AH (lowest BIN " + Format.coins(s.u()) + ", before AH fees; it may take a while to sell)"
							: "on the Bazaar for " + Format.price(s.u()) + " each after tax")
					+ ". That works out to " + Format.price(per) + " coins per " + one + ".";
			rows.add(row);
		}
		return rows;
	}

	// ---------------------------------------------------------------- sorting (moneyList)

	public static List<Row> sort(List<Row> rows, String key, String search) {
		String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		List<Row> list = new ArrayList<>();
		for (Row r : rows) if (q.isEmpty() || r.search.contains(q)) list.add(r);
		Comparator<Row> c = switch (key) {
			case "profit" -> (a, b) -> Double.compare(b.profit, a.profit);
			case "perHour" -> (a, b) -> Double.compare(b.perHour, a.perHour);
			case "pct" -> (a, b) -> Double.compare(b.pct, a.pct);
			case "vol" -> (a, b) -> Double.compare(b.vol, a.vol);
			case "time" -> (a, b) -> Long.compare(a.time, b.time);
			case "name" -> Comparator.comparing(r -> r.name);
			case "per" -> (a, b) -> Double.compare(b.per, a.per);
			case "value" -> (a, b) -> Double.compare(b.value, a.value);
			case "amt" -> (a, b) -> Long.compare(a.amt, b.amt);
			default -> (a, b) -> Double.compare(b.coins, a.coins); // coins, coinsSO, coinsIS
		};
		list.sort(c);
		return list;
	}
}
