package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The website's Auction house tab (scanAHArb, scanAHCrafts, the lookup), on the AH lowest-BIN list.
 * Two fixes over the site: that list also carries Bazaar prices for most Bazaar items, so a "lowest BIN" equal to the
 * Bazaar's own price isn't treated as an auction; and AH crafts must use at least one ingredient that isn't on the
 * Bazaar (everything else is the Crafts tab). No Minecraft code, so it can be tested on its own.
 */
public final class AhEngine {
	private AhEngine() {}

	/** One line of the tab. kind = "arb", "craft" or "lookup". Money is per item (arb, lookup) or per craft (craft). */
	public record Row(String kind, String id, String name, double bin, double cost, double sellOffer, double instaSell,
			long weekVol, int output, List<Part> parts, Risk risk) {
		/** Profit selling the way you chose (sell offer or insta-sell). Lookup: the AH lowest BIN minus the craft cost. */
		public double profit(boolean bySellOffer) {
			if (kind.equals("lookup")) return Double.isNaN(cost) ? Double.NaN : bin - cost;
			return (bySellOffer ? sellOffer : instaSell) - cost;
		}

		/** Profit as a % of what it costs. */
		public double pct(boolean bySellOffer) {
			double p = profit(bySellOffer);
			return cost > 0 && !Double.isNaN(p) ? p / cost * 100 : 0;
		}

		public boolean usesAh() {
			for (Part p : parts) if (p.ah()) return true;
			return false;
		}
	}

	/** An ingredient: how many, the price each, and whether that price is an AH lowest BIN (false = Bazaar insta-buy). */
	public record Part(String id, String name, int qty, double unit, boolean ah) {}

	/**
	 * Whether the list's "lowest BIN" for a Bazaar item is really a Bazaar price: within 3% of either side of the book or
	 * Hypixel's averages. The list carries (slightly stale) Bazaar quotes for most Bazaar items; real AH listings that
	 * close to the Bazaar price wouldn't be worth the trip anyway.
	 */
	static boolean isBazaarPrice(double bin, BazaarPrices.Price p) {
		for (double v : new double[] {p.instaBuy(), p.instaSell(), p.quickBuy(), p.quickSell()}) {
			if (!Double.isNaN(v) && v > 0 && Math.abs(v - bin) <= Math.max(0.05, v * 0.03)) return true;
		}
		return false;
	}

	/** A real AH listing for this item, or NaN (none, or the list only has the Bazaar's price). */
	static double ahOnly(String id, Map<String, Double> bins, Function<String, BazaarPrices.Price> prices) {
		Double b = bins.get(id);
		if (b == null || !(b > 0)) return Double.NaN;
		BazaarPrices.Price p = prices.apply(id);
		return p != null && isBazaarPrice(b, p) ? Double.NaN : b;
	}

	/** scanAHArb: Bazaar items listed on the AH for less than they sell for on the Bazaar (after tax). Either way of selling must profit. */
	public static List<Row> arb(Iterable<String> bazaarIds, Map<String, Double> bins, Function<String, BazaarPrices.Price> prices, double tax) {
		List<Row> out = new ArrayList<>();
		for (String id : bazaarIds) {
			BazaarPrices.Price p = prices.apply(id);
			if (p == null || !(p.quickBuy() > 0) || !(p.quickSell() > 0)) continue;
			double bin = ahOnly(id, bins, prices);
			if (Double.isNaN(bin)) continue;
			double so = p.quickBuy() * (1 - tax), is = p.quickSell() * (1 - tax);
			if (so - bin <= 0 && is - bin <= 0) continue;
			out.add(new Row("arb", id, SiteData.prettyName(id), bin, bin, so, is, Math.min(p.buyMovingWeek(), p.sellMovingWeek()), 1, List.of(), Risk.of(p)));
		}
		return out;
	}

	/**
	 * scanAHCrafts: recipes needing at least one item that's only on the AH (priced at its lowest BIN; everything else at the
	 * Bazaar insta-buy price), whose result sells on the Bazaar for more than the materials cost.
	 */
	public static List<Row> crafts(Iterable<Recipes.Recipe> recipes, Map<String, Double> bins, Function<String, BazaarPrices.Price> prices,
			double tax, Settings.Reqs reqs) {
		List<Row> out = new ArrayList<>();
		for (Recipes.Recipe rec : recipes) {
			if (rec.ingredients().isEmpty()) continue;
			BazaarPrices.Price prod = prices.apply(rec.id());
			if (prod == null || !(prod.quickBuy() > 0)) continue;              // must be sellable on the Bazaar
			if (reqs == Settings.Reqs.NONE && !rec.req().isEmpty()) continue;
			if (reqs == Settings.Reqs.UNLOCKED && !Recipes.reqMet(rec.req())) continue;
			Cost c = cost(rec, bins, prices);
			if (c == null || !(c.total() > 0) || c.parts().stream().noneMatch(Part::ah)) continue;
			int n = Math.max(1, rec.output());
			double so = prod.quickBuy() * (1 - tax) * n, is = prod.quickSell() * (1 - tax) * n;
			if (so - c.total() <= 0 && is - c.total() <= 0) continue;
			out.add(new Row("craft", rec.id(), rec.name(), Double.NaN, c.total(), so, is, Math.min(prod.buyMovingWeek(), prod.sellMovingWeek()), n,
					c.parts(), Risk.of(prod)));
		}
		return out;
	}

	private record Cost(double total, List<Part> parts) {}

	/** Materials for one craft: Bazaar insta-buy (averaged price) when the item's on the Bazaar, else its AH lowest BIN; null if one has neither. */
	private static Cost cost(Recipes.Recipe rec, Map<String, Double> bins, Function<String, BazaarPrices.Price> prices) {
		double total = 0;
		List<Part> parts = new ArrayList<>();
		for (Recipes.Ing ing : rec.ingredients()) {
			BazaarPrices.Price p = prices.apply(ing.id());
			double unit;
			boolean ah = false;
			if (p != null && p.quickBuy() > 0) {
				unit = p.quickBuy();
			} else {
				Double b = bins.get(ing.id());
				if (b == null || !(b > 0)) return null;
				unit = b;
				ah = true;
			}
			total += unit * ing.qty();
			parts.add(new Part(ing.id(), Recipes.name(ing.id()), ing.qty(), unit, ah));
		}
		return new Cost(total, List.copyOf(parts));
	}

	/**
	 * The lookup: every AH item whose name or ID contains the search (at least 2 letters), its lowest BIN and, for craftable
	 * ones, the craft cost and craft -> AH profit (before AH fees). Best matches first, at most `limit`.
	 */
	public static List<Row> lookup(String search, Map<String, Double> bins, Function<String, BazaarPrices.Price> prices, int limit) {
		String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		if (q.length() < 2) return List.of();
		List<Row> out = new ArrayList<>();
		for (var e : bins.entrySet()) {
			if (!(e.getValue() > 0)) continue;
			String id = e.getKey(), name = ahName(id);
			if (!name.toLowerCase(Locale.ROOT).contains(q) && !id.toLowerCase(Locale.ROOT).contains(q)) continue;
			Recipes.Recipe rec = Recipes.get(id);
			Cost c = rec == null ? null : cost(rec, bins, prices);
			double cost = c == null ? Double.NaN : c.total() / Math.max(1, rec.output());
			BazaarPrices.Price p = prices.apply(id);
			out.add(new Row("lookup", id, name, e.getValue(), cost, Double.NaN, Double.NaN,
					p == null ? -1 : Math.min(p.buyMovingWeek(), p.sellMovingWeek()), 1, c == null ? List.of() : c.parts(), p == null ? Risk.NONE : Risk.of(p)));
		}
		// names that start with the search first, then shorter names (closer matches), then A-Z
		out.sort(Comparator.<Row>comparingInt(r -> r.name().toLowerCase(Locale.ROOT).startsWith(q) ? 0 : 1)
				.thenComparingInt(r -> r.name().length()).thenComparing(Row::name));
		return out.size() > limit ? List.copyOf(out.subList(0, limit)) : out;
	}

	/** A readable name for a price-list key: pets ("PET-ARMADILLO-EPIC") and books ("ENCHANTED_BOOK-SHARPNESS-6") included. */
	public static String ahName(String id) {
		java.util.regex.Matcher pet = java.util.regex.Pattern.compile("^PET-(.+?)-(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC)(-100)?$").matcher(id);
		if (pet.matches()) {
			String r = pet.group(2).charAt(0) + pet.group(2).substring(1).toLowerCase(Locale.ROOT);
			return Recipes.fallbackName(pet.group(1)) + " pet (" + r + (pet.group(3) != null ? ", level 100" : "") + ")";
		}
		java.util.regex.Matcher book = java.util.regex.Pattern.compile("^ENCHANTED_BOOK-(.+)-(\\d+)$").matcher(id);
		if (book.matches()) return Recipes.fallbackName(book.group(1)) + " " + SiteData.roman(Integer.parseInt(book.group(2))) + " (book)";
		return SiteData.prettyName(id);
	}

	/** Sorts: "profit" (or "pct") for the chosen way of selling, "bin" (cheapest first), "vol", "name". */
	public static List<Row> sorted(List<Row> rows, String sort, boolean bySellOffer, String search) {
		String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		List<Row> out = new ArrayList<>();
		for (Row r : rows) if (q.isEmpty() || r.kind().equals("lookup") || r.name().toLowerCase(Locale.ROOT).contains(q) || r.id().toLowerCase(Locale.ROOT).contains(q)) out.add(r);
		Comparator<Row> c = switch (sort) {
			case "pct" -> (a, b) -> Double.compare(b.pct(bySellOffer), a.pct(bySellOffer));
			case "bin" -> Comparator.comparingDouble(r -> Double.isNaN(r.bin()) ? r.cost() : r.bin());
			case "vol" -> (a, b) -> Long.compare(b.weekVol(), a.weekVol());
			case "name" -> Comparator.comparing(r -> r.name().toLowerCase(Locale.ROOT));
			default -> (a, b) -> Double.compare(nz(b.profit(bySellOffer)), nz(a.profit(bySellOffer)));
		};
		if (!(rows.isEmpty() || rows.get(0).kind().equals("lookup")) || !sort.equals("profit")) out.sort(c);
		return out;
	}

	private static double nz(double v) {
		return Double.isNaN(v) ? Double.NEGATIVE_INFINITY : v;
	}
}
