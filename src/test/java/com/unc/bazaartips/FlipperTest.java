package com.unc.bazaartips;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FlipperTest {
	private static final double TAX = 0.0125;

	@Test
	void profitIsSellOfferAfterTaxMinusBuyOrder() {
		Map<String, BazaarPrices.Price> m = Map.of("DIAMOND", TestPrices.busy(120, 100));
		List<Flipper.Flip> flips = Flipper.compute(List.of("DIAMOND"), m::get, TAX);
		assertEquals(1, flips.size());
		Flipper.Flip f = flips.get(0);
		assertEquals(100.1, f.buyAt(), 1e-9);         // 0.1 over the top buy order
		assertEquals(119.9, f.sellAt(), 1e-9);        // 0.1 under the lowest sell offer
		assertEquals(119.9 * (1 - TAX) - 100.1, f.each(), 1e-9);
		assertEquals(f.each() / 100.1 * 100, f.pct(), 1e-9);
		assertEquals(500_000 / 168.0, f.flow(), 1e-9);
		assertEquals(f.each() * f.flow(), f.perHour(), 1e-6);
	}

	@Test
	void noFlipWhenTaxEatsTheGap() {
		Map<String, BazaarPrices.Price> m = Map.of("A", TestPrices.busy(100.5, 100), "B", TestPrices.busy(100.2, 100));
		assertTrue(Flipper.compute(List.of("A", "B"), m::get, TAX).isEmpty());
	}

	@Test
	void itemsWithAnEmptySideOrNoPriceAreSkipped() {
		Map<String, BazaarPrices.Price> m = Map.of("A", new BazaarPrices.Price(Double.NaN, 100, 1000, 1000));
		assertTrue(Flipper.compute(List.of("A", "MISSING"), m::get, TAX).isEmpty());
	}

	@Test
	void scoreStaysBetween0And100AndRiskLowersIt() {
		Map<String, BazaarPrices.Price> m = Map.of(
				"SAFE", TestPrices.of(110, 100, 110, 100, 50, 50),
				"RISKY", TestPrices.of(110, 100, 400, 100, 2, 50));   // averaged price says extreme spread, thin book
		List<Flipper.Flip> flips = Flipper.compute(List.of("SAFE", "RISKY"), m::get, TAX);
		assertEquals(2, flips.size());
		int safe = -1, risky = -1;
		for (Flipper.Flip f : flips) {
			int s = Flipper.score(f, null, null).score();
			assertTrue(s >= 0 && s <= 100);
			if (f.id().equals("SAFE")) safe = s; else risky = s;
		}
		assertTrue(risky < safe, "a risky flip must score lower (" + risky + " vs " + safe + ")");
	}
}
