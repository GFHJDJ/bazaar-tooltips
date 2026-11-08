package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The website's "best amount" maths (capFor + bestQty), shared by the Flipper, NPC and Forge tabs:
 * the most profitable amount that your coins, your free slots and the market allow.
 * No Minecraft code, so it can be tested on its own.
 */
public final class Amounts {
	private Amounts() {}

	/** What an amount does: profit at n, and the limits. Any limit can be null (not limited by it). */
	public interface Sizer {
		double profitAt(long n);

		/** Most you can afford with these coins, or null if coins don't limit it. */
		Long maxAfford(double coins);

		/** Most that fits in these free slots, or null. */
		Long maxFit(int slots);

		/** Slots n takes. */
		long slotsUsed(long n);

		/** A hard limit from the market or your forge slots (null = none), and why. */
		Long hardCap();

		String hardWhy();

		/** Amounts where the price per item changes (worth trying as the best amount). */
		default List<Long> breaks() {
			return List.of();
		}
	}

	/** The best amount, its profit, and what stopped it: "coins", "slots", "cap" or "price". */
	public record Best(long n, double profit, String why) {}

	private record Cap(long n, String why) {}

	private static Cap capFor(Sizer r, double coins, int slots) {
		if (coins <= 0 && slots <= 0) return null;
		double cap = Double.POSITIVE_INFINITY;
		String why = "";
		if (coins > 0) {
			Long a = r.maxAfford(coins);
			if (a != null && a < cap) { cap = a; why = "coins"; }
		}
		if (slots > 0) {
			Long f = r.maxFit(slots);
			if (f != null && f < cap) { cap = f; why = "slots"; }
		}
		Long hard = r.hardCap();
		if (hard != null && hard < cap) { cap = hard; why = "cap"; }
		return cap == Double.POSITIVE_INFINITY ? null : new Cap((long) cap, why);
	}

	/** bestQty: try the most you can do and every price break below it; keep whichever earns most. Null without coins/slots. */
	public static Best best(Sizer r, double coins, int slots) {
		Cap c = capFor(r, coins, slots);
		if (c == null) return null;
		long maxN = c.n();
		if (maxN < 1) return new Best(0, 0, c.why());
		Set<Long> cands = new LinkedHashSet<>();
		cands.add(maxN);
		for (long b : r.breaks()) if (b >= 1 && b <= maxN) cands.add(b);
		long bestN = 0;
		double bestP = Double.NEGATIVE_INFINITY;
		for (long n : cands) {
			double pr = r.profitAt(n);
			if (pr > bestP) { bestP = pr; bestN = n; }
		}
		return new Best(bestN, bestP, bestN == maxN ? c.why() : "price");
	}

	/** Cumulative order-book amounts / div: where the price per item steps up (cumBreaks). */
	public static List<Long> cumBreaks(List<BazaarPrices.Level> levels, int div) {
		List<Long> out = new ArrayList<>();
		long c = 0;
		for (BazaarPrices.Level l : levels) {
			c += l.amount();
			out.add((long) Math.floor((double) c / Math.max(1, div)));
		}
		return out;
	}

	/** "1.5 hrs", "3 days" (the site's fmtHours), or null for zero / unknown. */
	public static String hours(double h) {
		if (Double.isNaN(h) || Double.isInfinite(h) || h <= 0) return null;
		if (h < 48) return trim1(h) + " hr" + (h >= 2 ? "s" : "");
		double d = h / 24;
		if (d < 60) return trim1(d) + " day" + (d >= 2 ? "s" : "");
		return trim1(d / 30.44) + " months";
	}

	/** "instant", "under a minute", "12 min", "1.5 hrs", "very slow" (the site's timeLbl). */
	public static String timeLabel(double h) {
		if (!(h > 0)) return "instant";
		if (Double.isInfinite(h)) return "very slow";
		if (h < 1.0 / 60) return "under a minute";
		if (h < 1) return Math.max(1, Math.round(h * 60)) + " min";
		String s = hours(h);
		return s != null ? s : trim1(h) + " hrs";
	}

	/** "45s", "12 min", "1.5 hr", "2 days" (the site's fmtDur, from seconds). */
	public static String duration(long s) {
		if (s < 60) return s + "s";
		if (s < 3600) return Math.round(s / 60.0) + " min";
		if (s < 86400) return trim1(s / 3600.0) + " hr";
		String d = trim1(s / 86400.0);
		return d + (d.equals("1") ? " day" : " days");
	}

	static String trim1(double v) {
		double r = Math.round(v * 10) / 10.0;
		return r == Math.floor(r) ? Long.toString((long) r) : Double.toString(r);
	}
}
