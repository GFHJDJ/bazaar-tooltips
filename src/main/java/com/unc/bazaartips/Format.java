package com.unc.bazaartips;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Number formatting and bazaar ID helpers. No Minecraft code, so it can be tested on its own. */
public final class Format {
	private Format() {}

	/**
	 * Rounded (default): short numbers like 162.8k and 2.13M.
	 * Exact: every digit, like 162,844.8 and 2,134,000. Switched with the "Rounded" button on the screens.
	 */
	private static volatile boolean rounded = true;

	public static boolean rounded() {
		return rounded;
	}

	public static void setRounded(boolean on) {
		rounded = on;
	}

	/** Every digit, grouped, with at most one decimal: 2,134,000.5 */
	private static String exact(double value) {
		return pattern("#,##0.#").format(value);
	}

	/** Bazaar ID for an enchanted book, e.g. ("ultimate_wise", 5) -> "ENCHANTMENT_ULTIMATE_WISE_5". */
	public static String enchantmentId(String name, int level) {
		return "ENCHANTMENT_" + name.toUpperCase(Locale.ROOT) + "_" + level;
	}

	/** Coins: 33,269.8 under a million, then 2.13M / 1.05B (all digits when not rounded). NaN (no orders) shows as "none". */
	public static String coins(double value) {
		if (Double.isNaN(value)) return "none";
		if (!rounded) return exact(value);
		double abs = Math.abs(value);
		if (abs >= 1e9) return twoDecimals(value / 1e9) + "B";
		if (abs >= 1e6) return twoDecimals(value / 1e6) + "M";
		return pattern("#,##0.#").format(value);
	}

	/** Profit with a sign: +45.2, -1,200, +2.13M. */
	public static String signedCoins(double value) {
		if (Double.isNaN(value)) return "none";
		return (value >= 0 ? "+" : "-") + coins(Math.abs(value));
	}

	/** Short coins for tight table columns (never more than 4 digits): 9,999.9, 162.8k, 2.13M, 76.2M, 762M, 1.05B. */
	public static String shortCoins(double value) {
		if (Double.isNaN(value)) return "none";
		if (!rounded) return exact(value);
		double abs = Math.abs(value);
		if (abs >= 1e9) return threeDigits(value / 1e9) + "B";
		if (abs >= 1e6) return threeDigits(value / 1e6) + "M";
		if (abs >= 1e4) return oneDecimal(value / 1e3) + "k";
		return pattern("#,##0.#").format(value);
	}

	/** 7.62, 76.2, 762 */
	private static String threeDigits(double v) {
		double abs = Math.abs(v);
		return pattern(abs >= 100 ? "0" : abs >= 10 ? "0.#" : "0.##").format(v);
	}

	/** shortCoins with a sign: +162.8k, -45.2. */
	public static String signedShortCoins(double value) {
		if (Double.isNaN(value)) return "none";
		return (value >= 0 ? "+" : "-") + shortCoins(Math.abs(value));
	}

	/** Item counts: 980, 12.4k, 1.2M (all digits when not rounded: 1,234,567). */
	public static String amount(long value) {
		if (!rounded) return pattern("#,##0").format(value);
		if (value >= 1_000_000_000L) return oneDecimal(value / 1e9) + "B";
		if (value >= 1_000_000L) return oneDecimal(value / 1e6) + "M";
		if (value >= 1_000L) return oneDecimal(value / 1e3) + "k";
		return Long.toString(value);
	}

	/** A price for one item: like coins() when rounded, else always one decimal (215,907.7), like the site's fmtPrice. */
	public static String price(double value) {
		if (Double.isNaN(value) || Double.isInfinite(value)) return "none";
		return rounded ? coins(value) : pattern("#,##0.0").format(value);
	}

	/** A count of items or crafts: 1,234 (rounded to whole). */
	public static String count(double value) {
		if (Double.isNaN(value)) return "none";
		return pattern("#,##0").format(value);
	}

	/** "45s", "3m", "2h". */
	public static String age(long millis) {
		long s = millis / 1000;
		if (s < 60) return s + "s";
		if (s < 3600) return (s / 60) + "m";
		return (s / 3600) + "h";
	}

	private static String twoDecimals(double v) {
		return pattern("0.##").format(v);
	}

	private static String oneDecimal(double v) {
		return pattern("0.#").format(v);
	}

	// DecimalFormat isn't thread-safe, so each thread keeps its own copies.
	private static final ThreadLocal<java.util.Map<String, DecimalFormat>> FORMATS =
			ThreadLocal.withInitial(java.util.HashMap::new);

	private static DecimalFormat pattern(String p) {
		return FORMATS.get().computeIfAbsent(p, k -> {
			DecimalFormat f = new DecimalFormat(k, DecimalFormatSymbols.getInstance(Locale.US));
			f.setRoundingMode(java.math.RoundingMode.HALF_UP); // 0.05 -> 0.1, like the website (Java's default would give 0.0)
			return f;
		});
	}
}
