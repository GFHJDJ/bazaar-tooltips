package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Market-manipulation warning, same rules as the website's riskOf():
 * - spread: insta-buy at 1.5x+ the insta-sell price is a caution, 3x+ is high
 * - thin book: 3 or fewer open orders on either side
 * - one order controls 60%+ of a side (when that side has 6 or fewer orders)
 * - a wide spread on a thin book is high risk
 * It flags suspicious patterns, not proven manipulation: a quiet market looks the same.
 */
public record Risk(Level level, List<String> reasons) {
	public enum Level { NONE, CAUTION, HIGH }

	public static final Risk NONE = new Risk(Level.NONE, List.of());

	public boolean flagged() {
		return level != Level.NONE;
	}

	public static Risk of(BazaarPrices.Price p) {
		List<String> reasons = new ArrayList<>();
		Level level = Level.NONE;
		double ratio = p.quickSell() > 0 ? p.quickBuy() / p.quickSell() : Double.POSITIVE_INFINITY;
		String times = String.format(Locale.US, "%.1f", ratio);
		if (ratio >= 3) {
			reasons.add("Extreme spread: insta-buy is " + times + "x the insta-sell price");
			level = Level.HIGH;
		} else if (ratio >= 1.5) {
			reasons.add("Wide spread: insta-buy is " + times + "x the insta-sell price");
			level = Level.CAUTION;
		}
		boolean thin = p.buyOrderCount() <= 3 || p.sellOfferCount() <= 3;
		if (thin) {
			reasons.add("Thin order book: only a handful of open orders");
			if (level == Level.NONE) level = Level.CAUTION;
		}
		double conc = Math.max(concentration(p.sellOffers()), concentration(p.buyOrders()));
		if (conc >= 0.6 && (p.buyOrderCount() <= 6 || p.sellOfferCount() <= 6)) {
			reasons.add("A single order controls most of the book");
			if (level != Level.HIGH) level = Level.CAUTION;
		}
		if (ratio >= 1.5 && thin) level = Level.HIGH;
		return new Risk(level, List.copyOf(reasons));
	}

	/** Biggest single level's share of everything on that side (the site's concentration()). */
	private static double concentration(List<BazaarPrices.Level> levels) {
		double max = 0, sum = 0;
		for (BazaarPrices.Level l : levels) {
			sum += l.amount();
			max = Math.max(max, l.amount());
		}
		return sum > 0 ? max / sum : 0;
	}
}
