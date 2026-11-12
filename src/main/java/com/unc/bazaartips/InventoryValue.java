package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * What your inventory is worth and the best place to sell each item: insta-sell on the Bazaar (walking the buy orders,
 * after tax), a sell offer (more, but you wait), an NPC (no tax), or, for things not on the Bazaar, the AH lowest BIN.
 * No Minecraft code, so it can be tested on its own.
 */
public final class InventoryValue {
	private InventoryValue() {}

	/** One stack as read off the inventory: its SkyBlock ID, Bazaar product (null if none), and how many. */
	public record Stack(String skyblockId, String bazaarId, String name, int count) {}

	/**
	 * One item, all its stacks together. Values are for the whole count; NaN = can't be sold that way.
	 * best = "npc", "bazaar", "ah" or "" (no price): the most you get right now (sell offers wait, so they never count as best).
	 */
	public record Row(String skyblockId, String bazaarId, String name, long count, double instaSell, double sellOffer, double npc, double ah,
			String best, long weekVol) {
		/** What it's worth right now, sold the best way (NaN if it can't be priced). */
		public double bestValue() {
			return switch (best) {
				case "npc" -> npc;
				case "bazaar" -> instaSell;
				case "ah" -> ah;
				default -> Double.NaN;
			};
		}

		/** How much more a sell offer pays than the best way now (0 if it doesn't). */
		public double offerExtra() {
			double now = bestValue();
			return !Double.isNaN(sellOffer) && !Double.isNaN(now) && sellOffer > now ? sellOffer - now : 0;
		}

		public boolean priced() {
			return !best.isEmpty();
		}
	}

	/** The totals for the intro lines. */
	public record Totals(double now, double withOffers, double npcBeatsBazaar, int npcCount, int unpriced, double ahValue) {}

	/** Adds up stacks of the same item, then prices each one. */
	public static List<Row> rows(List<Stack> stacks, Function<String, BazaarPrices.Price> prices, Function<String, Double> npcPrice,
			Function<String, Double> ahBin, double tax) {
		Map<String, Stack> merged = new LinkedHashMap<>();
		for (Stack s : stacks) {
			if (s.skyblockId() == null || s.skyblockId().isEmpty() || s.count() <= 0) continue;
			String key = s.bazaarId() != null ? s.bazaarId() : s.skyblockId();
			merged.merge(key, s, (a, b) -> new Stack(a.skyblockId(), a.bazaarId(), a.name(), a.count() + b.count()));
		}
		List<Row> out = new ArrayList<>();
		for (Stack s : merged.values()) {
			long n = s.count();
			BazaarPrices.Price p = s.bazaarId() == null ? null : prices.apply(s.bazaarId());
			double insta = Double.NaN, offer = Double.NaN;
			long week = -1;
			if (p != null) {
				week = Math.min(p.buyMovingWeek(), p.sellMovingWeek());
				if (!p.buyOrders().isEmpty()) {
					CraftEngine.BookWalk w = CraftEngine.walkBook(p.buyOrders(), n);
					// past the top 30 buy orders Hypixel sends, count the rest at the lowest one shown
					double total = w.total() + w.shortBy() * p.buyOrders().get(p.buyOrders().size() - 1).price();
					insta = total * (1 - tax);
				}
				if (!p.sellOffers().isEmpty()) offer = n * CraftEngine.offerSellAt(p) * (1 - tax);
			}
			Double npcEach = npcPrice.apply(s.skyblockId());
			double npc = npcEach != null && npcEach > 0 ? npcEach * n : Double.NaN;
			double ah = Double.NaN;
			if (p == null) {
				Double b = ahBin.apply(s.skyblockId());
				if (b != null && b > 0) ah = b * n;
			}
			String best = "";
			double bestV = -1;
			if (!Double.isNaN(insta) && insta > bestV) { best = "bazaar"; bestV = insta; }
			if (!Double.isNaN(npc) && npc > bestV) { best = "npc"; bestV = npc; }
			if (!Double.isNaN(ah) && ah > bestV) { best = "ah"; }
			String name = p != null ? SiteData.prettyName(s.bazaarId()) : s.name();
			out.add(new Row(s.skyblockId(), s.bazaarId(), name, n, insta, offer, npc, ah, best, week));
		}
		return out;
	}

	public static Totals totals(List<Row> rows) {
		double now = 0, offers = 0, npcGain = 0, ah = 0;
		int npcCount = 0, unpriced = 0;
		for (Row r : rows) {
			if (!r.priced()) { unpriced++; continue; }
			now += r.bestValue();
			offers += r.bestValue() + r.offerExtra();
			if (r.best().equals("npc") && !Double.isNaN(r.instaSell())) {
				npcCount++;
				npcGain += r.npc() - r.instaSell();
			}
			if (r.best().equals("ah")) ah += r.ah();
		}
		return new Totals(now, offers, npcGain, npcCount, unpriced, ah);
	}

	/** "value" (most first), "count", "offer" (most extra from a sell offer), "name". Unpriced items go last. */
	public static List<Row> sorted(List<Row> rows, String sort, String search) {
		String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		List<Row> out = new ArrayList<>();
		for (Row r : rows) if (q.isEmpty() || r.name().toLowerCase(Locale.ROOT).contains(q) || r.skyblockId().toLowerCase(Locale.ROOT).contains(q)) out.add(r);
		java.util.Comparator<Row> c = switch (sort) {
			case "count" -> (a, b) -> Long.compare(b.count(), a.count());
			case "offer" -> (a, b) -> Double.compare(b.offerExtra(), a.offerExtra());
			case "name" -> java.util.Comparator.comparing(r -> r.name().toLowerCase(Locale.ROOT));
			default -> (a, b) -> Double.compare(nz(b.bestValue()), nz(a.bestValue()));
		};
		out.sort(java.util.Comparator.<Row>comparingInt(r -> r.priced() ? 0 : 1).thenComparing(c));
		return out;
	}

	private static double nz(double v) {
		return Double.isNaN(v) ? -1 : v;
	}
}
