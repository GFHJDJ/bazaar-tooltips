package com.unc.bazaartips;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FlipAlertsTest {
	private static final CoflData.Move MOVE = new CoflData.Move(0, 0);
	private static final CoflData.Hist HIST = new CoflData.Hist(1, "ok", 200, 50, 1, 105, 120, 90);
	private final Map<String, BazaarPrices.Price> prices = new HashMap<>();

	@BeforeEach
	void reset() {
		FlipAlerts.reset();
		prices.clear();
	}

	private FlipAlerts.Result check(long now, boolean history) {
		List<Flipper.Flip> flips = Flipper.compute(prices.keySet(), prices::get, 0.0125);
		return FlipAlerts.check(flips, 1, id -> history ? MOVE : null, id -> history ? HIST : null, now);
	}

	@Test
	void theFirstCheckOnlyCounts() {
		prices.put("A", TestPrices.of(110, 100, 110, 100, 50, 50));
		FlipAlerts.Result r = check(1000, true);
		assertEquals(1, r.firstCount());
		assertTrue(r.alerts().isEmpty());
	}

	@Test
	void aNewFlipAlertsOnceThenStaysQuietForAnHour() {
		prices.put("A", TestPrices.of(110, 100, 110, 100, 50, 50));
		check(1000, true);
		prices.put("B", TestPrices.of(220, 200, 220, 200, 50, 50));
		FlipAlerts.Result r = check(2000, true);
		assertEquals(-1, r.firstCount());
		assertEquals(1, r.alerts().size());
		assertEquals("B", r.alerts().get(0).flip().id());
		assertTrue(check(3000, true).alerts().isEmpty());                  // still there: no repeat
		prices.remove("B");
		check(4000, true);
		prices.put("B", TestPrices.of(220, 200, 220, 200, 50, 50));
		assertTrue(check(5000, true).alerts().isEmpty());                  // back within the hour: quiet
		prices.remove("B");
		check(6000, true);
		prices.put("B", TestPrices.of(220, 200, 220, 200, 50, 50));
		assertEquals(1, check(6000 + FlipAlerts.QUIET_MILLIS + 1, true).alerts().size());
	}

	@Test
	void aFlipWaitsForItsPriceHistoryBeforeAlerting() {
		prices.put("A", TestPrices.of(110, 100, 110, 100, 50, 50));
		check(1000, true);
		prices.put("B", TestPrices.of(220, 200, 220, 200, 50, 50));
		FlipAlerts.Result r = check(2000, false);
		assertTrue(r.alerts().isEmpty());
		assertTrue(r.needHistory().contains("B"));
		assertEquals(1, check(3000, true).alerts().size());
	}

	@Test
	void riskyAndLowVolumeFlipsNeverAlert() {
		prices.put("A", TestPrices.of(110, 100, 110, 100, 50, 50));
		check(1000, true);
		prices.put("RISKY", TestPrices.of(220, 200, 800, 200, 2, 50));
		prices.put("QUIET", new BazaarPrices.Price(220, 200, 100, 100,
				List.of(new BazaarPrices.Level(220, 10, 5)), List.of(new BazaarPrices.Level(200, 10, 5))));
		assertTrue(check(2000, true).alerts().isEmpty());
	}

	@Test
	void atMostThreeMessagesPerCheck() {
		prices.put("A", TestPrices.of(110, 100, 110, 100, 50, 50));
		check(1000, true);
		for (int i = 0; i < 6; i++) prices.put("N" + i, TestPrices.of(220 + i, 200, 220 + i, 200, 50, 50));
		assertEquals(FlipAlerts.MAX_PER_CHECK, check(2000, true).alerts().size());
	}
}
