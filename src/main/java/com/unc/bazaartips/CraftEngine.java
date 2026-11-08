package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The website's Craft tabs, ported function by function (names in comments match the site's script).
 * One engine is built per set of prices + tax; everything it works out is cached until those change.
 * No Minecraft code, so it can be tested on its own.
 */
public final class CraftEngine {
	public static final long LOW_VOLUME = 10_000;
	/** Sell the result with a sell offer, or insta-sell it. */
	public enum Sell { OFFER, INSTA }

	/** A link in an ingredient's chain: bought on the bazaar, or crafted from its own parts when that's cheaper. */
	public record Part(String id, String name, int qty, double unit, boolean viaCraft, List<Part> parts) {}

	/**
	 * One ingredient of a craft. unit = price each (bought, or crafted when viaCraft).
	 * maxSO / maxIS = the most you could pay for one and still not lose coins selling by sell offer / insta-sell.
	 */
	public record Ingredient(String id, String name, int qty, double unit, boolean risky, boolean viaCraft,
			List<Part> parts, double maxSO, double maxIS) {
		Part asPart() {
			return new Part(id, name, qty, unit, viaCraft, parts);
		}
	}

	/** One craft, as on a site card. Money is per craft unless the name says Each / per slot. */
	public record Craft(String key, String name, int output, double cost, double profitSellOffer, double profitInstaSell,
			double sellEach, double instaEach, double breakEven, double marginSellOffer, double marginInstaSell,
			double perSlotSellOffer, double perSlotInstaSell, List<Ingredient> ingredients, List<String> warnings,
			Risk.Level risk, String req, String chainReq, long volume, boolean usesChain, boolean compactor,
			boolean byOrder, Set<String> flipped) {
		/** How this item is bought in this craft: the list's Buy setting, unless you switched it for this item. */
		public boolean orderFor(String id) {
			return byOrder != flipped.contains(id);
		}

		/** True when some items are bought one way and some the other. */
		public boolean mixed() {
			return !flipped.isEmpty();
		}

		public boolean instaWins() {
			return profitInstaSell > profitSellOffer;
		}

		/** Its requirement plus any requirement from parts you'd craft yourself (the site's fullReq). */
		public String fullReq() {
			if (req.isEmpty()) return chainReq;
			return chainReq.isEmpty() ? req : req + Recipes.REQ_SEP + chainReq;
		}
	}

	// ---------------------------------------------------------------- engine per prices + tax

	private static volatile CraftEngine current;

	/** The engine for the latest prices and your tax setting. */
	public static CraftEngine current() {
		CraftEngine e = current;
		long version = BazaarPrices.lastFetchMillis();
		double tax = Settings.tax();
		if (e == null || e.version != version || e.tax != tax) {
			e = new CraftEngine(BazaarPrices::get, tax, version);
			current = e;
		}
		return e;
	}

	private final Function<String, BazaarPrices.Price> prices;
	private final double tax;
	private final long version;
	private final Map<String, List<Craft>> lists = new ConcurrentHashMap<>();
	private final Map<String, Sizing> walks = new ConcurrentHashMap<>();
	private final Map<String, Risk> risks = new ConcurrentHashMap<>();

	public CraftEngine(Function<String, BazaarPrices.Price> prices, double tax, long version) {
		this.prices = prices;
		this.tax = tax;
		this.version = version;
	}

	public double tax() {
		return tax;
	}

	public BazaarPrices.Price price(String id) {
		return prices.apply(id);
	}

	public Risk risk(String id) {
		BazaarPrices.Price p = prices.apply(id);
		if (p == null) return Risk.NONE;
		return risks.computeIfAbsent(id, k -> Risk.of(p));
	}

	// ---------------------------------------------------------------- prices (top of the live order book)

	public static double round1(double x) {
		return Math.round(x * 10) / 10.0;
	}

	/** Cheapest sell offer (instaBuyAt). */
	public static double instaBuyAt(BazaarPrices.Price p) {
		return !p.sellOffers().isEmpty() ? p.sellOffers().get(0).price() : p.quickBuy();
	}

	/** Highest buy order (instaSellAt). */
	public static double instaSellAt(BazaarPrices.Price p) {
		return !p.buyOrders().isEmpty() ? p.buyOrders().get(0).price() : p.quickSell();
	}

	/** Where you'd place a buy order: 0.1 above the top buy order (orderBuyAt). */
	public static double orderBuyAt(BazaarPrices.Price p) {
		double top = !p.buyOrders().isEmpty() ? p.buyOrders().get(0).price() : p.quickSell();
		return round1(top + 0.1);
	}

	/** Where you'd list a sell offer: 0.1 below the lowest sell offer (offerSellAt). */
	public static double offerSellAt(BazaarPrices.Price p) {
		double top = !p.sellOffers().isEmpty() ? p.sellOffers().get(0).price() : p.quickBuy();
		return Math.max(0.1, round1(top - 0.1));
	}

	// ---------------------------------------------------------------- building the craft lists (buildCrafts)

	/**
	 * All crafts for one Craft tab. byOrder = the buy-order tab. chains = price ingredients as the
	 * cheaper of buying or crafting them (the site's chain data); false = always buy (plain).
	 */
	public List<Craft> crafts(boolean byOrder, boolean chains) {
		return lists.computeIfAbsent(byOrder + "|" + chains, k -> build(byOrder, !chains));
	}

	public Craft craft(String key, boolean byOrder, boolean chains) {
		for (Craft c : crafts(byOrder, chains)) if (c.key().equals(key)) return c;
		return null;
	}

	/**
	 * One craft where the items in `flipped` are bought the other way (insta-buy instead of buy order, or the
	 * reverse) from the list's Buy setting. Empty = the same as the list.
	 */
	public Craft craft(String key, boolean byOrder, boolean chains, Set<String> flipped) {
		if (flipped.isEmpty()) return craft(key, byOrder, chains);
		Recipes.Recipe rec = Recipes.get(key);
		return rec == null ? null : buildOne(rec, byOrder, Set.copyOf(flipped), !chains);
	}

	private record Unit(double unit, boolean viaCraft, List<Part> parts) {}

	/** Cheapest way to get one: buy it, or craft it from its own ingredients (recursively), whichever is cheaper. */
	private Unit cheapestUnit(String id, java.util.function.Predicate<String> useBuyOrders, Set<String> visiting) {
		BazaarPrices.Price p = prices.apply(id);
		Double bazaarPrice = (p != null && p.quickBuy() > 0) ? (useBuyOrders.test(id) ? orderBuyAt(p) : instaBuyAt(p)) : null;
		Recipes.Recipe rec = Recipes.get(id);
		Unit craft = null;
		if (rec != null && rec.output() > 0 && !visiting.contains(id) && visiting.size() < 8) { // depth + cycle guard
			visiting.add(id);
			double sum = 0;
			boolean ok = true;
			List<Part> parts = new ArrayList<>();
			for (Recipes.Ing sub : rec.ingredients()) {
				Unit r = cheapestUnit(sub.id(), useBuyOrders, visiting);
				if (r == null) {
					ok = false;
					break;
				}
				sum += sub.qty() * r.unit();
				parts.add(new Part(sub.id(), Recipes.name(sub.id()), sub.qty(), r.unit(), r.viaCraft(), r.parts()));
			}
			visiting.remove(id);
			if (ok) craft = new Unit(sum / rec.output(), true, List.copyOf(parts));
		}
		if (bazaarPrice == null && craft == null) return null;
		if (bazaarPrice == null) return craft;
		if (craft == null || bazaarPrice <= craft.unit()) return new Unit(bazaarPrice, false, null);
		return craft;
	}

	private List<Craft> build(boolean useBuyOrders, boolean plain) {
		List<Craft> out = new ArrayList<>();
		for (Recipes.Recipe rec : Recipes.all()) {
			Craft c = buildOne(rec, useBuyOrders, Set.of(), plain);
			if (c != null) out.add(c);
		}
		return List.copyOf(out);
	}

	private Craft buildOne(Recipes.Recipe rec, boolean byOrder, Set<String> flipped, boolean plain) {
		java.util.function.Predicate<String> useBuyOrders = id -> byOrder != flipped.contains(id);
		{
			BazaarPrices.Price prod = prices.apply(rec.id());
			if (prod == null) return null;
			double cost = 0;
			boolean ok = true;
			List<Ingredient> ings = new ArrayList<>();
			List<String> warnings = new ArrayList<>();
			for (Recipes.Ing ing : rec.ingredients()) {
				BazaarPrices.Price p = prices.apply(ing.id());
				double unit;
				boolean viaCraft = false;
				List<Part> parts = null;
				if (plain) {
					if (p == null) {
						ok = false;
						break;
					}
					unit = useBuyOrders.test(ing.id()) ? orderBuyAt(p) : instaBuyAt(p);
				} else {
					Set<String> visiting = new HashSet<>();
					visiting.add(rec.id());
					Unit r = cheapestUnit(ing.id(), useBuyOrders, visiting);
					if (r == null) {
						ok = false;
						break;
					}
					unit = r.unit();
					viaCraft = r.viaCraft();
					parts = r.parts();
				}
				cost += ing.qty() * unit;
				boolean risky = p != null && risk(ing.id()).flagged();
				ings.add(new Ingredient(ing.id(), Recipes.name(ing.id()), ing.qty(), unit, risky, viaCraft, parts, 0, 0));
				if (p != null && risk(ing.id()).level() == Risk.Level.HIGH) {
					warnings.add("Ingredient " + Recipes.name(ing.id()) + " looks manipulated: its price is distorting this craft's cost");
				}
			}
			if (!ok) return null;
			boolean usesChain = ings.stream().anyMatch(Ingredient::viaCraft);

			// requirements of any step you'd craft yourself in the chain (not already on this recipe)
			Set<String> chainTok = new LinkedHashSet<>();
			collectChainReqs(ings.stream().map(Ingredient::asPart).toList(), chainTok);
			Set<String> own = new HashSet<>(List.of(rec.req().split(java.util.regex.Pattern.quote(Recipes.REQ_SEP))));
			String chainReq = String.join(Recipes.REQ_SEP, chainTok.stream().filter(t -> !own.contains(t)).toList());

			Risk prodRisk = risk(rec.id());
			if (prodRisk.flagged()) warnings.add(0, "This item: " + prodRisk.reasons().get(0).toLowerCase(Locale.ROOT));

			double soAt = offerSellAt(prod), isAt = instaSellAt(prod);
			double pSO = rec.output() * soAt * (1 - tax) - cost;
			double pIS = rec.output() * isAt * (1 - tax) - cost;
			List<Ingredient> withMax = new ArrayList<>();
			for (Ingredient i : ings) {
				double rest = cost - i.qty() * i.unit();
				withMax.add(new Ingredient(i.id(), i.name(), i.qty(), i.unit(), i.risky(), i.viaCraft(), i.parts(),
						(rec.output() * soAt * (1 - tax) - rest) / i.qty(), (rec.output() * isAt * (1 - tax) - rest) / i.qty()));
			}
			long vol = Math.min(prod.buyMovingWeek(), prod.sellMovingWeek());
			return new Craft(rec.id(), rec.name(), rec.output(), cost, pSO, pIS, soAt, isAt,
					rec.output() > 0 ? cost / (rec.output() * (1 - tax)) : 0,
					cost > 0 ? pSO / cost * 100 : 0, cost > 0 ? pIS / cost * 100 : 0,
					pSO / rec.output() * 64, pIS / rec.output() * 64,
					List.copyOf(withMax), List.copyOf(warnings), prodRisk.level(), rec.req(), chainReq, vol, usesChain,
					Recipes.compactorMakes(rec), byOrder, flipped);
		}
	}

	private static void collectChainReqs(List<Part> list, Set<String> into) {
		if (list == null) return;
		for (Part p : list) {
			if (!p.viaCraft()) continue;
			Recipes.Recipe r = Recipes.get(p.id());
			if (r != null && !r.req().isEmpty()) into.addAll(List.of(r.req().split(java.util.regex.Pattern.quote(Recipes.REQ_SEP))));
			collectChainReqs(p.parts(), into);
		}
	}

	// ---------------------------------------------------------------- the list: filters and sorting (renderCrafts)

	/** A sort: the site's label, a short one for the Sort button, and what it does. */
	public record SortOption(String key, String label, String shortLabel, String explanation) {}

	public static final List<SortOption> SORTS = List.of(
			new SortOption("profitSellOrder", "Sell-order profit", "Sell-offer profit", "Profit per craft if you sell with a sell offer, highest first."),
			new SortOption("profitInstaSell", "Insta-sell profit", "Insta-sell profit", "Profit per craft if you insta-sell, highest first."),
			new SortOption("marginSellOrder", "Sell-order margin %", "Sell-offer margin", "Profit as a % of the craft cost, selling with a sell offer."),
			new SortOption("marginInstaSell", "Insta-sell margin %", "Insta-sell margin", "Profit as a % of the craft cost, insta-selling."),
			new SortOption("profitPerSlotSO", "Profit / slot · sell order", "Per slot (offer)", "Profit for a full inventory slot (64) of the result, selling with a sell offer."),
			new SortOption("profitPerSlotIS", "Profit / slot · insta-sell", "Per slot (insta)", "Profit for a full inventory slot (64) of the result, insta-selling."),
			new SortOption("best", "Best for your coins & slots", "Best for me", "The most you can make with your coins and free slots, whichever way of selling is better. Set your coins and/or slots first."),
			new SortOption("bestSO", "Best for your coins & slots · sell order", "Best (offer)", "The most you can make with your coins and free slots, selling with sell offers."),
			new SortOption("bestIS", "Best for your coins & slots · insta-sell", "Best (insta)", "The most you can make with your coins and free slots, insta-selling."),
			new SortOption("cost", "Craft cost", "Craft cost", "Most expensive crafts first."),
			new SortOption("req", "Requirement (A–Z)", "Requirement", "Grouped by what they need unlocked, lowest tier first; crafts with no requirement last."),
			new SortOption("name", "Name", "Name", "Alphabetical."));

	public static SortOption sortOption(String key) {
		for (SortOption s : SORTS) if (s.key().equals(key)) return s;
		return SORTS.get(0);
	}

	/** Which way of selling a "best" sort is locked to (craftSellPick), or null for "whichever is better". */
	public static Sell sellPick(String sortKey) {
		return sortKey.equals("bestSO") ? Sell.OFFER : sortKey.equals("bestIS") ? Sell.INSTA : null;
	}

	/** The list as the site's renderCrafts builds it: search, filters, then the chosen sort. */
	public List<Craft> list(String search, Settings.Buy buy, Settings.Chains chains, Settings.Compactor compactor,
			Settings.Reqs reqs, boolean hideLowVolume, String sortKey, double coins, int slots) {
		String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		List<Craft> items = new ArrayList<>();
		for (Craft c : crafts(buy == Settings.Buy.ORDER, chains != Settings.Chains.EXCLUDE)) {
			if (!c.name().toLowerCase(Locale.ROOT).contains(q)) continue;
			if (hideLowVolume && c.volume() < LOW_VOLUME) continue;
			if (chains == Settings.Chains.ONLY && !c.usesChain()) continue;
			if (compactor == Settings.Compactor.ONLY && !c.compactor()) continue;
			if (compactor == Settings.Compactor.HIDE && c.compactor()) continue;
			if (!reqPasses(c, reqs)) continue;
			items.add(c);
		}
		Comparator<Craft> order;
		switch (sortKey) {
			case "best", "bestSO", "bestIS" -> {
				Sell only = sellPick(sortKey);
				Map<String, Double> bestP = new HashMap<>();
				for (Craft c : items) {
					Best b = best(c, only, coins, slots);
					bestP.put(c.key(), b != null ? b.profit() : -1e18 - c.cost());
				}
				order = (a, b) -> Double.compare(bestP.get(b.key()), bestP.get(a.key()));
			}
			case "name" -> order = Comparator.comparing(Craft::name);
			case "req" -> order = Comparator.comparing(CraftEngine::reqSortKey)
					.thenComparing((a, b) -> Double.compare(b.profitSellOffer(), a.profitSellOffer()));
			case "profitInstaSell" -> order = (a, b) -> Double.compare(b.profitInstaSell(), a.profitInstaSell());
			case "marginSellOrder" -> order = (a, b) -> Double.compare(b.marginSellOffer(), a.marginSellOffer());
			case "marginInstaSell" -> order = (a, b) -> Double.compare(b.marginInstaSell(), a.marginInstaSell());
			case "profitPerSlotSO" -> order = (a, b) -> Double.compare(b.perSlotSellOffer(), a.perSlotSellOffer());
			case "profitPerSlotIS" -> order = (a, b) -> Double.compare(b.perSlotInstaSell(), a.perSlotInstaSell());
			case "cost" -> order = (a, b) -> Double.compare(b.cost(), a.cost());
			default -> order = (a, b) -> Double.compare(b.profitSellOffer(), a.profitSellOffer());
		}
		items.sort(order);
		return items;
	}

	public static boolean reqPasses(Craft c, Settings.Reqs reqs) {
		if (reqs == Settings.Reqs.ALL) return true;
		String all = c.fullReq();
		if (reqs == Settings.Reqs.NONE) return all.isEmpty();
		return Recipes.reqMet(all);
	}

	/** Group by requirement name, lowest tier first; no requirement goes last (reqSortKey). */
	static String reqSortKey(Craft c) {
		List<Recipes.Req> r = Recipes.parseReq(c.fullReq());
		if (r.isEmpty()) return "￿";
		return r.get(0).name().toLowerCase(Locale.ROOT) + "\u0000" + String.format("%03d", r.get(0).level());
	}

	// ---------------------------------------------------------------- materials (leafTotals, slots)

	/** What you actually buy: chained ingredients broken down to the bottom of their chain (leafTotals). */
	public static void leafTotals(Part item, double totalQty, Map<String, Double> acc) {
		Recipes.Recipe rec = item.viaCraft() ? Recipes.get(item.id()) : null;
		if (rec != null && item.parts() != null) {
			double crafts = totalQty / rec.output();
			for (Part p : item.parts()) leafTotals(p, p.qty() * crafts, acc);
		} else {
			acc.merge(item.id(), totalQty, Double::sum);
		}
	}

	public static Map<String, Double> leaf(Craft c, double crafts) {
		Map<String, Double> acc = new LinkedHashMap<>();
		for (Ingredient i : c.ingredients()) leafTotals(i.asPart(), i.qty() * crafts, acc);
		return acc;
	}

	/** Slots the materials for n crafts take (64 per slot), like the site's slotsForCrafts. */
	public static int slotsForCrafts(Craft c, long n) {
		int s = 0;
		for (double q : leaf(c, n).values()) s += (int) Math.ceil(q / 64);
		return s;
	}

	public static int maxCraftsForSlots(Craft c, int slots) {
		if (slots <= 0) return 0;
		if (slotsForCrafts(c, 1) > slots) return 0;
		long hi = 1;
		while (slotsForCrafts(c, hi * 2) <= slots) hi *= 2;
		long lo = hi, top = hi * 2;
		while (lo < top) {
			long mid = (lo + top + 1) / 2;
			if (slotsForCrafts(c, mid) <= slots) lo = mid;
			else top = mid - 1;
		}
		return (int) Math.min(lo, Integer.MAX_VALUE);
	}

	// ---------------------------------------------------------------- order-book walks

	static double bookDepth(List<BazaarPrices.Level> levels) {
		double t = 0;
		for (BazaarPrices.Level l : levels) t += l.amount();
		return t;
	}

	/** Buy or sell n through the levels: coins for what's there, how many filled, and how many were missing (walkBook). */
	public record BookWalk(double total, double filled, double shortBy) {}

	public static BookWalk walkBook(List<BazaarPrices.Level> levels, double n) {
		double left = n, total = 0;
		for (BazaarPrices.Level l : levels) {
			if (left <= 0) break;
			double t = Math.min(left, l.amount());
			total += t * l.price();
			left -= t;
		}
		return new BookWalk(total, n - left, left);
	}

	/** A side of the order book that gets used up as you trade into it. */
	private static final class Book {
		final double[] price, amount;

		Book(List<BazaarPrices.Level> levels) {
			price = new double[levels.size()];
			amount = new double[levels.size()];
			for (int i = 0; i < levels.size(); i++) {
				price[i] = levels.get(i).price();
				amount[i] = levels.get(i).amount();
			}
		}

		/** Coins for the next n (used up), or NaN if the book runs out. */
		double take(double n) {
			double left = n, t = 0;
			for (int i = 0; i < price.length; i++) {
				if (left <= 0) break;
				double k = Math.min(left, amount[i]);
				t += k * price[i];
				amount[i] -= k;
				left -= k;
			}
			return left > 0 ? Double.NaN : t;
		}
	}

	private static double perHr(long week) {
		return week / 168.0;
	}

	/** With orders, one go is capped at about an hour of what the market trades, so it can actually fill. */
	static double hourCapBuy(BazaarPrices.Price it, double per, boolean byOrder) {
		return byOrder ? Math.max(1, Math.floor(perHr(it.sellMovingWeek()) / per)) : Double.POSITIVE_INFINITY;
	}

	static double hourCapSell(BazaarPrices.Price it, double per, Sell sell) {
		return sell == Sell.OFFER ? Math.max(1, Math.floor(perHr(it.buyMovingWeek()) / per)) : Double.POSITIVE_INFINITY;
	}

	/**
	 * Profit and cost after 1, 2, 3... crafts, walking the live order book one craft at a time
	 * (craftWalk + craftSizing). Doesn't depend on your coins, so it's cached until prices change.
	 */
	public final class Sizing {
		final Craft craft;
		final double[] cost, prof;
		public final int maxN;
		final double hourCap;

		Sizing(Craft craft, double[] cost, double[] prof, double hourCap) {
			this.craft = craft;
			this.cost = cost;
			this.prof = prof;
			this.maxN = prof.length - 1;
			this.hourCap = hourCap;
		}

		public double costAt(long n) {
			return cost[(int) Math.min(n, maxN)];
		}

		public double profitAt(long n) {
			return prof[(int) Math.min(n, maxN)];
		}

		/** Most crafts your coins cover (past the walk's end, the walk's own cap stops you, not coins). */
		long maxAfford(double coins) {
			int n = 0;
			while (n < maxN && cost[n + 1] <= coins) n++;
			return n < maxN ? n : 1_000_000_000L;
		}

		long maxFit(int slots) {
			return maxCraftsForSlots(craft, slots);
		}

		public int slotsUsed(long n) {
			return slotsForCrafts(craft, n);
		}

		List<Long> breaks() {
			List<Long> b = new ArrayList<>();
			for (long i = 1; i <= Math.min(maxN, 60); i++) b.add(i);
			b.add((long) maxN);
			return b;
		}

		/** What stops the walk: the order book, or the hour-of-trading cap. */
		public String hardWhy() {
			return maxN < hourCap ? "the order book: more stops paying" : "about an hour of trading";
		}
	}

	public Sizing sizing(Craft c, Sell sell) {
		String key = c.key() + "|" + c.byOrder() + "|" + sell + "|" + c.usesChain() + "|" + new java.util.TreeSet<>(c.flipped());
		Sizing cached = walks.get(key);
		if (cached != null) return cached.maxN < 1 ? null : cached;
		Sizing s = computeSizing(c, sell);
		walks.put(key, s == null ? new Sizing(c, new double[] {0}, new double[] {0}, 0) : s);
		return s;
	}

	private Sizing computeSizing(Craft c, Sell sell) {
		BazaarPrices.Price prod = prices.apply(c.key());
		if (prod == null) return null;
		Map<String, Double> leaf0 = leaf(c, 1);
		double cap = hourCapSell(prod, c.output(), sell);
		for (var e : leaf0.entrySet()) {
			BazaarPrices.Price it = prices.apply(e.getKey());
			if (it != null && c.orderFor(e.getKey())) cap = Math.min(cap, hourCapBuy(it, e.getValue(), true));
		}
		int walkCap = (int) Math.min(cap, 5000);

		// craftWalk
		List<String> ids = new ArrayList<>(leaf0.keySet());
		BazaarPrices.Price[] mats = new BazaarPrices.Price[ids.size()];
		Book[] asks = new Book[ids.size()];
		for (int i = 0; i < ids.size(); i++) {
			mats[i] = prices.apply(ids.get(i));
			if (mats[i] == null) return null;
			asks[i] = new Book(mats[i].sellOffers());
		}
		Book bids = new Book(prod.buyOrders());
		List<Double> cost = new ArrayList<>(List.of(0.0)), prof = new ArrayList<>(List.of(0.0));
		walk:
		for (int n = 1; n <= walkCap; n++) {
			double cst = 0;
			for (int i = 0; i < ids.size(); i++) {
				double q = leaf0.get(ids.get(i));
				double t = c.orderFor(ids.get(i)) ? q * orderBuyAt(mats[i]) : asks[i].take(q);
				if (Double.isNaN(t)) break walk;
				cst += t;
			}
			double got = sell == Sell.OFFER ? c.output() * offerSellAt(prod) : bids.take(c.output());
			if (Double.isNaN(got)) break;
			double marg = got * (1 - tax) - cst;
			if (marg <= 0) break;
			cost.add(cost.get(n - 1) + cst);
			prof.add(prof.get(n - 1) + marg);
		}
		if (prof.size() < 2) return null;
		return new Sizing(c, cost.stream().mapToDouble(Double::doubleValue).toArray(),
				prof.stream().mapToDouble(Double::doubleValue).toArray(), cap);
	}

	// ---------------------------------------------------------------- best amount for your coins & slots

	/** The best amount: how many crafts, the total profit, and what stopped it ("coins", "slots", "price" or "cap"). */
	public record Best(long n, double profit, String why, Sell sell, Sizing sizing) {}

	private record Cap(long n, String why) {}

	private static Cap capFor(Sizing z, double coins, int slots) {
		if (coins <= 0 && slots <= 0) return null;
		double cap = Double.POSITIVE_INFINITY;
		String why = "";
		if (coins > 0) {
			long a = z.maxAfford(coins);
			if (a < cap) { cap = a; why = "coins"; }
		}
		if (slots > 0) {
			long f = z.maxFit(slots);
			if (f < cap) { cap = f; why = "slots"; }
		}
		if (z.maxN < cap) { cap = z.maxN; why = "cap"; }
		return cap == Double.POSITIVE_INFINITY ? null : new Cap((long) cap, why);
	}

	/** bestQty: try "as many as you can" and every price break below it, keep whichever earns most. */
	private static Best bestQty(Sizing z, Sell sell, double coins, int slots) {
		Cap c = capFor(z, coins, slots);
		if (c == null) return null;
		long maxN = c.n();
		if (maxN < 1) return new Best(0, 0, c.why(), sell, z);
		Set<Long> cands = new LinkedHashSet<>();
		cands.add(maxN);
		for (long b : z.breaks()) if (b >= 1 && b <= maxN) cands.add(b);
		long bestN = 0;
		double bestP = Double.NEGATIVE_INFINITY;
		for (long n : cands) {
			double pr = z.profitAt(n);
			if (pr > bestP) { bestP = pr; bestN = n; }
		}
		return new Best(bestN, bestP, bestN == maxN ? c.why() : "price", sell, z);
	}

	/** craftBest: the best amount for your coins & slots; only = one way of selling, or null to try both. */
	public Best best(Craft c, Sell only, double coins, int slots) {
		if (coins <= 0 && slots <= 0) return null;
		Best best = null;
		for (Sell s : only != null ? List.of(only) : List.of(Sell.OFFER, Sell.INSTA)) {
			Sizing z = sizing(c, s);
			if (z == null) continue;
			Best b = bestQty(z, s, coins, slots);
			if (b != null && b.n() > 0 && b.profit() > 0 && (best == null || b.profit() > best.profit())) best = b;
		}
		return best;
	}

	/** Why a craft can't be done even once at the prices in the order book (craftBlocker), or null. */
	public String blocker(Craft c, Sell sell) {
		{
			for (var e : leaf(c, 1).entrySet()) {
				BazaarPrices.Price it = prices.apply(e.getKey());
				if (it == null || c.orderFor(e.getKey())) continue;
				double have = bookDepth(it.sellOffers());
				if (have < e.getValue()) {
					return "only " + (it.sellOffers().size() >= 30 ? "the top 30 offers hold " : "") + Format.count(have) + " "
							+ Recipes.name(e.getKey()) + " for sale; one craft needs " + Format.count(e.getValue()) + " (try buy orders)";
				}
			}
		}
		BazaarPrices.Price prod = prices.apply(c.key());
		if (sell == Sell.INSTA && prod != null && bookDepth(prod.buyOrders()) < c.output()) {
			return "buyers want only " + Format.count(bookDepth(prod.buyOrders())) + " right now; one craft makes " + c.output();
		}
		return null;
	}

	/** Which way of selling the shopping list and highlights use: the sort's choice, else the more profitable one. */
	public Sell pick(Craft c, String sortKey, double coins, int slots) {
		Sell forced = sellPick(sortKey);
		if (forced != null) return forced;
		Best so = best(c, Sell.OFFER, coins, slots), is = best(c, Sell.INSTA, coins, slots);
		return so != null && (is == null || so.profit() >= is.profit()) ? Sell.OFFER : Sell.INSTA;
	}

	// ---------------------------------------------------------------- shopping list (craftShopList)

	/** One line of the shopping list: what to do, the coins, how (prices), and the prices in "how" as numbers. */
	public record ShopLine(String what, double coins, boolean selling, String how, List<Double> howPrices) {}

	public record ShopList(long crafts, Sell sell, List<ShopLine> lines, double profit, boolean usesOrders) {}

	public ShopList shopList(Craft c, String sortKey, double coins, int slots) {
		if (coins <= 0 && slots <= 0) return null;
		Sell pick = pick(c, sortKey, coins, slots);
		Best cb = best(c, pick, coins, slots);
		if (cb == null) return null;
		long n = cb.n();
		List<ShopLine> lines = new ArrayList<>();
		double spend = 0;
		for (var e : leaf(c, n).entrySet()) {
			long q = Math.round(e.getValue());
			BazaarPrices.Price it = prices.apply(e.getKey());
			String nm = Recipes.name(e.getKey());
			if (it == null) {
				lines.add(new ShopLine("Buy " + Format.count(q) + "x " + nm, Double.NaN, false, "no bazaar price", List.of()));
				continue;
			}
			if (c.orderFor(e.getKey())) {
				double at = orderBuyAt(it);
				spend += q * at;
				lines.add(new ShopLine("Buy " + Format.count(q) + "x " + nm, q * at, false,
						"buy order at " + Format.price(at) + " each (0.1 above the top buy order)", List.of(at)));
			} else {
				BookWalk w = walkBook(it.sellOffers(), q);
				spend += w.total();
				double cum = 0, last = Double.NaN;
				for (BazaarPrices.Level l : it.sellOffers()) {
					cum += l.amount();
					last = l.price();
					if (cum >= q) break;
				}
				double first = it.sellOffers().isEmpty() ? Double.NaN : it.sellOffers().get(0).price();
				boolean range = !Double.isNaN(first) && last > first;
				lines.add(new ShopLine("Buy " + Format.count(q) + "x " + nm, w.total(), false,
						"insta-buy, avg " + Format.price(w.total() / q) + " each"
								+ (range ? " (from " + Format.price(first) + " up to " + Format.price(last) + ")" : ""),
						range ? List.of(w.total() / q, first, last) : List.of(w.total() / q)));
			}
		}
		BazaarPrices.Price prod = prices.apply(c.key());
		long out = n * c.output();
		double get = 0;
		String how = "";
		List<Double> howPrices = List.of();
		if (prod != null) {
			if (pick == Sell.OFFER) {
				double at = offerSellAt(prod);
				get = out * at * (1 - tax);
				how = "sell offer at " + Format.price(at) + " each (0.1 below the lowest sell offer), after " + taxLabel() + " tax";
				howPrices = List.of(at);
			} else {
				BookWalk w = walkBook(prod.buyOrders(), out);
				double total = w.total();
				String extra = "";
				if (w.shortBy() > 0) {
					double lastBid = prod.buyOrders().isEmpty() ? prod.quickSell() : prod.buyOrders().get(prod.buyOrders().size() - 1).price();
					total += w.shortBy() * lastBid;
					extra = "; only " + Format.count(w.filled()) + " wanted in the top " + prod.buyOrders().size()
							+ " buy orders, the rest is estimated at the last price";
				}
				get = total * (1 - tax);
				how = "insta-sell, avg " + Format.price(total / out) + " each before tax" + extra + ", after " + taxLabel() + " tax";
				howPrices = List.of(total / out);
			}
		}
		lines.add(new ShopLine("Sell " + Format.count(out) + "x " + c.name(), get, true, how, howPrices));
		boolean anyOrder = false;
		for (String id : leaf(c, 1).keySet()) anyOrder |= c.orderFor(id);
		return new ShopList(n, pick, List.copyOf(lines), get - spend, anyOrder || pick == Sell.OFFER);
	}

	String taxLabel() {
		String s = String.format(Locale.US, "%.3f", tax * 100).replaceAll("0+$", "").replaceAll("\\.$", "");
		return s + "%";
	}

	// ---------------------------------------------------------------- order-book depth (depthAnalysis)

	public record Depth(long crafts, double profit) {}

	/** How many crafts you can insta-buy the ingredients for and insta-sell before profit runs out. */
	public Depth depth(Craft c) {
		BazaarPrices.Price prod = prices.apply(c.key());
		if (prod == null) return null;
		Book bids = new Book(prod.buyOrders());
		List<Book> asks = new ArrayList<>();
		List<Integer> qty = new ArrayList<>();
		for (Ingredient i : c.ingredients()) {
			BazaarPrices.Price it = prices.apply(i.id());
			if (it == null || it.sellOffers().isEmpty()) return null;
			asks.add(new Book(it.sellOffers()));
			qty.add(i.qty());
		}
		if (prod.buyOrders().isEmpty()) return null;
		long crafts = 0;
		double profit = 0;
		int guard = 0;
		while (guard++ < 200_000) {
			// price the next craft without using anything up yet
			double rev = peek(bids, c.output());
			if (Double.isNaN(rev)) break;
			double cost = 0;
			boolean enough = true;
			for (int k = 0; k < asks.size(); k++) {
				double t = peek(asks.get(k), qty.get(k));
				if (Double.isNaN(t)) { enough = false; break; }
				cost += t;
			}
			if (!enough) break;
			double marg = rev * (1 - tax) - cost;
			if (marg <= 0) break;
			bids.take(c.output());
			for (int k = 0; k < asks.size(); k++) asks.get(k).take(qty.get(k));
			crafts++;
			profit += marg;
		}
		return new Depth(crafts, profit);
	}

	/**
	 * Buy-order depth: how many crafts you could make buying every ingredient with buy orders (0.1 above the top
	 * buy order; that price stays the same however many you buy) and insta-selling the results into today's buy
	 * orders, before profit runs out. hours = how long the ingredient buy orders take to fill at the rate people
	 * insta-sell them (the slowest ingredient decides; infinite if it never trades).
	 */
	public record OrderDepth(long crafts, double profit, double costEach, double hours, String slowest) {}

	public OrderDepth orderDepth(Craft c) {
		BazaarPrices.Price prod = prices.apply(c.key());
		if (prod == null || prod.buyOrders().isEmpty()) return null;
		Map<String, Double> leaf0 = leaf(c, 1);
		double cost = 0;
		for (var e : leaf0.entrySet()) {
			BazaarPrices.Price it = prices.apply(e.getKey());
			if (it == null || it.buyOrders().isEmpty()) return null;
			cost += e.getValue() * orderBuyAt(it);
		}
		Book bids = new Book(prod.buyOrders());
		long crafts = 0;
		double profit = 0;
		while (crafts < 200_000) {
			double rev = peek(bids, c.output());
			if (Double.isNaN(rev)) break;
			double marg = rev * (1 - tax) - cost;
			if (marg <= 0) break;
			bids.take(c.output());
			crafts++;
			profit += marg;
		}
		double hours = 0;
		String slowest = "";
		for (var e : leaf0.entrySet()) {
			double perHour = prices.apply(e.getKey()).sellMovingWeek() / 168.0;
			double h = perHour > 0 ? e.getValue() * crafts / perHour : Double.POSITIVE_INFINITY;
			if (crafts > 0 && h > hours) { hours = h; slowest = Recipes.name(e.getKey()); }
		}
		return new OrderDepth(crafts, profit, cost, hours, slowest);
	}

	private static double peek(Book b, double n) {
		double left = n, t = 0;
		for (int i = 0; i < b.price.length; i++) {
			if (left <= 0) break;
			double k = Math.min(left, b.amount[i]);
			t += k * b.price[i];
			left -= k;
		}
		return left > 0 ? Double.NaN : t;
	}
}
