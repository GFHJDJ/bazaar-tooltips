package com.unc.bazaartips;

import java.util.List;

/** Shortcuts for making prices in tests. */
final class TestPrices {
	private TestPrices() {}

	/** A healthy, busy market: lowest sell offer = buy, top buy order = sell, 99 orders a side. */
	static BazaarPrices.Price busy(double buy, double sell) {
		return new BazaarPrices.Price(buy, sell, 500_000, 500_000,
				List.of(new BazaarPrices.Level(buy, 1000, 5)), List.of(new BazaarPrices.Level(sell, 1000, 5)));
	}

	/** Full control: top of book, averaged prices, open order counts. */
	static BazaarPrices.Price of(double buy, double sell, double quickBuy, double quickSell, int sellOffers, int buyOrders) {
		return new BazaarPrices.Price(buy, sell, 500_000, 500_000,
				List.of(new BazaarPrices.Level(buy, 1000, 1), new BazaarPrices.Level(buy + 1, 1000, 1)),
				List.of(new BazaarPrices.Level(sell, 1000, 1), new BazaarPrices.Level(sell - 1, 1000, 1)),
				quickBuy, quickSell, sellOffers, buyOrders, 2000, 2000);
	}
}
