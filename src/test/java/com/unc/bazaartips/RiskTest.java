package com.unc.bazaartips;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiskTest {
	@Test
	void aBusyTightMarketIsFine() {
		assertEquals(Risk.Level.NONE, Risk.of(TestPrices.of(100, 98, 100, 98, 50, 50)).level());
	}

	@Test
	void aWideSpreadIsACaution() {
		Risk r = Risk.of(TestPrices.of(160, 100, 160, 100, 50, 50));
		assertEquals(Risk.Level.CAUTION, r.level());
		assertTrue(r.reasons().get(0).startsWith("Wide spread"));
	}

	@Test
	void anExtremeSpreadIsHigh() {
		assertEquals(Risk.Level.HIGH, Risk.of(TestPrices.of(300, 100, 300, 100, 50, 50)).level());
	}

	@Test
	void aThinBookIsACautionAndHighWithAWideSpread() {
		assertEquals(Risk.Level.CAUTION, Risk.of(TestPrices.of(100, 98, 100, 98, 3, 50)).level());
		assertEquals(Risk.Level.HIGH, Risk.of(TestPrices.of(160, 100, 160, 100, 3, 50)).level());
	}
}
