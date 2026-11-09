package com.unc.bazaartips;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The website's Flipper tab (computeFlips, flipScore, flipSizing, flipQty, renderFlips): buy with a buy order
 * 0.1 above the top buy order, sell with a sell offer 0.1 below the lowest sell offer.
 * No Minecraft code, so it can be tested on its own.
 */
public final class Flipper {
	public static final double TICK = 0.1;

	/**
	 * One flip. each = profit per item after tax; buyFill / sellFill = how fast buy orders / sell offers fill
	 * (items per hour); flow = the slower of the two; perHour = each x flow.
	 */
	public record Flip(String id, String name, BazaarPrices.Price it, double buyAt, double sellAt, double each, double pct,
			double buyFill, double sellFill, double flow, double perHour, long weekVol, Risk risk) {}

	/** One part of the flip score: points out of `of` (of = 0 for penalties), and why. */
	public record Part(String label, int pts, int of, String why) {}

	public record Score(int score, String grade, List<Part> parts, boolean partial, String histState) {}

	/** computeFlips: every bazaar item where a buy order -> sell offer flip makes money after tax. */
	public static List<Flip> compute(Iterable<String> productIds, Function<String, BazaarPrices.Price> prices, double tax) {
		List<Flip> out = new ArrayList<>();
		for (String id : productIds) {
			BazaarPrices.Price it = prices.apply(id);
			if (it == null || it.buyOrders().isEmpty() || it.sellOffers().isEmpty()) continue;
			double buyAt = CraftEngine.round1(it.buyOrders().get(0).price() + TICK);
			double sellAt = CraftEngine.round1(it.sellOffers().get(0).price() - TICK);
			if (sellAt <= buyAt) continue;
			double each = sellAt * (1 - tax) - buyAt;
			if (!(each > 0)) continue;
			double buyFill = it.sellMovingWeek() / 168.0, sellFill = it.buyMovingWeek() / 168.0;
			double flow = Math.min(buyFill, sellFill);
			out.add(new Flip(id, SiteData.prettyName(id), it, buyAt, sellAt, each, each / buyAt * 100, buyFill, sellFill, flow,
					each * flow, Math.min(it.buyMovingWeek(), it.sellMovingWeek()), Risk.of(it)));
		}
		return out;
	}

	// ---------------------------------------------------------------- the flip score

	public static Score score(Flip f, CoflData.Move mv, CoflData.Hist h) {
		List<Part> parts = new ArrayList<>();
		int[] sum = {0}, max = {0};
		java.util.function.Consumer<Part> add = p -> { parts.add(p); sum[0] += p.pts(); max[0] += p.of(); };

		double m = f.pct();
		add.accept(new Part("Margin after tax", m < 1 ? 0 : m < 2 ? 6 : m < 5 ? 17 : m < 15 ? 25 : m < 40 ? 15 : 6, 25,
				pct1(m) + "%" + (m < 2 ? ": thin; a small price move wipes it out" : m >= 40 ? ": suspiciously wide"
						: m >= 15 ? ": wide, often a thin market" : ": healthy")));

		double vol = Math.min(25, Math.max(0, Math.log10(f.flow() + 1) / Math.log10(2000) * 25));
		add.accept(new Part("Trading volume", (int) Math.round(vol), 25, "~" + Format.coins(f.flow()) + " trades/hr on the slower side"
				+ (f.flow() < 20 ? ": slow to fill" : f.flow() >= 500 ? ": fills fast" : "")));

		double bal = Math.min(f.buyFill(), f.sellFill()) / Math.max(Math.max(f.buyFill(), f.sellFill()), 1e-9);
		add.accept(new Part("Both sides fill evenly", (int) Math.round(bal * 10), 10, "buy orders fill ~" + Format.coins(f.buyFill())
				+ "/hr, sell offers ~" + Format.coins(f.sellFill()) + "/hr" + (bal < 0.35 ? ": lopsided" : "")));

		int open = f.it().buyOrderCount() + f.it().sellOfferCount();
		double comp = open / Math.max(f.flow(), 1);
		add.accept(new Part("Competition", comp <= 0.05 ? 10 : comp <= 0.2 ? 7 : comp <= 1 ? 4 : 0, 10, Format.count(open)
				+ " open orders for ~" + Format.coins(f.flow()) + " trades/hr" + (comp > 1 ? ": crowded" : comp <= 0.05 ? ": plenty to go around" : "")));

		if (mv != null) {
			double b = Double.isNaN(mv.b()) ? 0 : mv.b(), s = Double.isNaN(mv.s()) ? 0 : mv.s();
			double chg = Math.max(Math.abs(b), Math.abs(s));
			String dir = (b != 0 ? b : s) >= 0 ? "up" : "down";
			add.accept(new Part("Price today", chg < 3 ? 10 : chg < 8 ? 5 : chg < 20 ? -5 : -12, 10,
					(chg < 0.05 ? "flat" : dir + " " + pct1(chg) + "%") + " in the last 24h"
							+ (chg >= 8 ? ": moving fast, prices may shift before you fill" : chg < 3 ? ": stable" : "")));
		}

		double cur = (f.buyAt() + f.sellAt()) / 2;
		if (h != null && h.status().equals("ok")) {
			double dev = (cur / h.med30() - 1) * 100;
			add.accept(new Part("Vs. its usual price", dev > 25 ? -15 : dev > 10 ? -6 : dev >= -10 ? 12 : 4, 12,
					(Math.abs(dev) < 1 ? "right at" : String.format(Locale.US, "%.0f", Math.abs(dev)) + "% " + (dev > 0 ? "above" : "below"))
							+ " its 30-day typical price (" + Format.coins(h.med30()) + ")"
							+ (dev > 25 ? ": likely a spike; could crash while you hold" : dev < -10 ? ": cheap now; may recover, or keep falling" : "")));
			double span = h.allHi() - h.allLo(), pos = span > 0 ? (cur - h.allLo()) / span : 0.5;
			int yr = Instant.ofEpochMilli(h.since()).atZone(ZoneOffset.UTC).getYear();
			add.accept(new Part("All-time range", pos >= 0.9 ? -8 : pos <= 0.1 ? 8 : pos <= 0.35 ? 5 : 0, 8,
					(pos > 1 ? "above its previous all-time high" : pos < 0 ? "below its previous all-time low" : pos >= 0.9 ? "near its all-time high"
							: pos <= 0.1 ? "near its all-time low" : Math.round(pos * 100) + "% of the way from its low to its high")
							+ " since " + yr + " (" + Format.coins(h.allLo()) + " - " + Format.coins(h.allHi()) + ")"));
		}

		if (f.risk().level() == Risk.Level.HIGH) add.accept(new Part("Risk flags", -35, 0, String.join("; ", f.risk().reasons())));
		else if (f.risk().level() == Risk.Level.CAUTION) add.accept(new Part("Risk flags", -8, 0, String.join("; ", f.risk().reasons())));

		int score = (int) Math.max(0, Math.min(100, Math.round((double) sum[0] / max[0] * 100)));
		String grade = score >= 75 ? "Great" : score >= 60 ? "Good" : score >= 45 ? "OK" : score >= 30 ? "Weak" : "Avoid";
		String hs = h == null ? null : h.status();
		return new Score(score, grade, List.copyOf(parts), mv == null || !"ok".equals(hs), hs);
	}

	private static String pct1(double v) {
		return String.format(Locale.US, "%.1f", v);
	}

	// ---------------------------------------------------------------- best amount (flipSizing, flipQty)

	public static Amounts.Sizer sizing(Flip f) {
		int st = SiteData.stackOf(f.id(), false);
		return new Amounts.Sizer() {
			@Override public double profitAt(long n) { return n * f.each(); }
			@Override public Long maxAfford(double coins) { return (long) Math.floor(coins / f.buyAt()); }
			@Override public Long maxFit(int slots) { return (long) slots * st; }
			@Override public long slotsUsed(long n) { return (n + st - 1) / st; }
			@Override public Long hardCap() { return (long) Math.max(1, Math.floor(f.flow())); }
			@Override public String hardWhy() { return "about an hour of trading"; }
		};
	}

	/** How many to flip and why ("coins", "slots", "price" or "cap"): the best amount for your coins & slots, else about an hour of trading. */
	public record Qty(long n, String why) {}

	public static Qty qty(Flip f, double coins, int slots) {
		Amounts.Best b = Amounts.best(sizing(f), coins, slots);
		if (b == null) return new Qty(Math.max(1, (long) Math.floor(f.flow())), "cap");
		return new Qty(b.n(), b.why());
	}

	// ---------------------------------------------------------------- the list (renderFlips)

	public static final List<CraftEngine.SortOption> SORTS = List.of(
			new CraftEngine.SortOption("score", "Flip score", "Flip score", "The 0-100 score: margin, volume, both sides filling evenly, competition, today's price move, price history and risk."),
			new CraftEngine.SortOption("perHour", "Profit / hr", "Profit / hr", "Profit each x trades per hour on the slower side: the most per hour if you caught every trade."),
			new CraftEngine.SortOption("batch", "Best for your coins & slots", "Best for me", "Profit from the best amount for your coins and free slots (at most about an hour of trading)."),
			new CraftEngine.SortOption("each", "Profit each", "Profit each", "Profit per item after tax."),
			new CraftEngine.SortOption("pct", "Margin %", "Margin %", "Profit each as a % of the buy order price."),
			new CraftEngine.SortOption("flow", "Trades / hr", "Trades / hr", "How many trade per hour on the slower side (buy orders filling vs sell offers filling)."),
			new CraftEngine.SortOption("name", "Name", "Name", "Alphabetical."));

	public static CraftEngine.SortOption sortOption(String key) {
		for (CraftEngine.SortOption s : SORTS) if (s.key().equals(key)) return s;
		return SORTS.get(0);
	}

	public record Listed(Flip flip, Score score, Qty qty) {}

	public static List<Listed> list(List<Flip> flips, String search, boolean hideLowVolume, boolean hideRisky, String sortKey,
			double coins, int slots, Function<String, CoflData.Move> moves, Function<String, CoflData.Hist> hists) {
		return list(flips, search, hideLowVolume, hideRisky, 0, sortKey, coins, slots, moves, hists);
	}

	/** minScore: flips scoring under it are left out (0 = keep all). */
	public static List<Listed> list(List<Flip> flips, String search, boolean hideLowVolume, boolean hideRisky, int minScore, String sortKey,
			double coins, int slots, Function<String, CoflData.Move> moves, Function<String, CoflData.Hist> hists) {
		String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		List<Listed> out = new ArrayList<>();
		for (Flip f : flips) {
			if (!(f.name().toLowerCase(Locale.ROOT).contains(q) || f.id().toLowerCase(Locale.ROOT).contains(q))) continue;
			if (hideLowVolume && f.weekVol() < CraftEngine.LOW_VOLUME) continue;
			if (hideRisky && f.risk().level() == Risk.Level.HIGH) continue;
			Score sc = score(f, moves.apply(f.id()), hists.apply(f.id()));
			if (sc.score() < minScore) continue;
			out.add(new Listed(f, sc, qty(f, coins, slots)));
		}
		Comparator<Listed> c = switch (sortKey) {
			case "name" -> Comparator.comparing(l -> l.flip().name());
			case "perHour" -> (a, b) -> Double.compare(b.flip().perHour(), a.flip().perHour());
			case "batch" -> (a, b) -> Double.compare(b.qty().n() * b.flip().each(), a.qty().n() * a.flip().each());
			case "each" -> (a, b) -> Double.compare(b.flip().each(), a.flip().each());
			case "pct" -> (a, b) -> Double.compare(b.flip().pct(), a.flip().pct());
			case "flow" -> (a, b) -> Double.compare(b.flip().flow(), a.flip().flow());
			default -> (a, b) -> Integer.compare(b.score().score(), a.score().score());
		};
		out.sort(c);
		return out;
	}
}
