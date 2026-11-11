package com.unc.bazaartips;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AmountsTest {
	/** 10 coins profit each up to `peak` items, then each extra one loses 5; costs 100 each, 64 per slot. */
	private static Amounts.Sizer sizer(long peak, Long hardCap) {
		return new Amounts.Sizer() {
			@Override public double profitAt(long n) { return n <= peak ? n * 10.0 : peak * 10.0 - (n - peak) * 5.0; }
			@Override public Long maxAfford(double coins) { return (long) Math.floor(coins / 100); }
			@Override public Long maxFit(int slots) { return slots * 64L; }
			@Override public long slotsUsed(long n) { return (n + 63) / 64; }
			@Override public Long hardCap() { return hardCap; }
			@Override public String hardWhy() { return "the market"; }
			@Override public List<Long> breaks() { return List.of(peak); }
		};
	}

	@Test
	void theTightestLimitWins() {
		Amounts.Sizer s = sizer(1_000_000, 500L);
		assertNull(Amounts.best(s, 0, 0));                         // no coins or slots: nothing to size
		assertEquals(new Amounts.Best(30, 300, "coins"), Amounts.best(s, 3000, 0));
		assertEquals(new Amounts.Best(128, 1280, "slots"), Amounts.best(s, 1_000_000, 2));
		assertEquals(new Amounts.Best(500, 5000, "cap"), Amounts.best(s, 1_000_000, 100));
		assertEquals(new Amounts.Best(0, 0, "coins"), Amounts.best(s, 50, 0));   // can't afford one
	}

	@Test
	void fewerIsBetterWhenMoreStopsPaying() {
		assertEquals(new Amounts.Best(20, 200, "price"), Amounts.best(sizer(20, null), 10_000, 0));
	}

	@Test
	void whereThePriceStepsUp() {
		List<BazaarPrices.Level> book = List.of(new BazaarPrices.Level(10, 100), new BazaarPrices.Level(11, 60));
		assertEquals(List.of(100L, 160L), Amounts.cumBreaks(book, 1));
		assertEquals(List.of(11L, 17L), Amounts.cumBreaks(book, 9));
	}

	@Test
	void timesReadNaturally() {
		assertEquals("instant", Amounts.timeLabel(0));
		assertEquals("under a minute", Amounts.timeLabel(0.01));
		assertEquals("30 min", Amounts.timeLabel(0.5));
		assertEquals("1.5 hr", Amounts.timeLabel(1.5));
		assertEquals("3 days", Amounts.timeLabel(72));
		assertEquals("very slow", Amounts.timeLabel(Double.POSITIVE_INFINITY));
		assertEquals("45s", Amounts.duration(45));
		assertEquals("8 hr", Amounts.duration(28800));
		assertEquals("2 days", Amounts.duration(172800));
	}
}
