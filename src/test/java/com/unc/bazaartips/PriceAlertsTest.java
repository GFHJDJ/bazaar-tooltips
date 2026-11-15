package com.unc.bazaartips;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PriceAlertsTest {
	private final Map<String, BazaarPrices.Price> prices = new HashMap<>();

	@BeforeEach
	void reset() {
		PriceAlerts.resetForTests();
		prices.clear();
	}

	@Test
	void firesOnceWhenThePriceCrossesThenAgainAfterItComesBack() {
		PriceAlerts.Alert a = new PriceAlerts.Alert("JADE", true, true, 29_000);   // insta-buy at or below 29k
		assertTrue(PriceAlerts.add(a));
		assertFalse(PriceAlerts.add(a));                                            // no duplicates
		prices.put("JADE", TestPrices.busy(31_000, 29_000));
		assertTrue(PriceAlerts.check(prices::get).isEmpty());
		prices.put("JADE", TestPrices.busy(28_900, 27_000));
		List<PriceAlerts.Hit> hits = PriceAlerts.check(prices::get);
		assertEquals(1, hits.size());
		assertEquals(28_900, hits.get(0).price());
		assertTrue(PriceAlerts.check(prices::get).isEmpty());                       // still there: quiet
		prices.put("JADE", TestPrices.busy(30_000, 28_000));
		assertTrue(PriceAlerts.check(prices::get).isEmpty());                       // back above: re-armed
		prices.put("JADE", TestPrices.busy(29_000, 27_000));
		assertEquals(1, PriceAlerts.check(prices::get).size());                     // "at" counts
	}

	@Test
	void sellSideAndAbove() {
		PriceAlerts.add(new PriceAlerts.Alert("JADE", false, false, 30_000));       // insta-sell at or above 30k
		prices.put("JADE", TestPrices.busy(31_000, 29_500));
		assertTrue(PriceAlerts.check(prices::get).isEmpty());
		prices.put("JADE", TestPrices.busy(31_000, 30_100));
		assertEquals(1, PriceAlerts.check(prices::get).size());
		assertTrue(PriceAlerts.check(Map.<String, BazaarPrices.Price>of()::get).isEmpty());   // no price: nothing changes
	}

	@Test
	void savedAndLoaded(@TempDir Path dir) {
		Path f = dir.resolve("alerts.json");
		PriceAlerts.load(f);
		PriceAlerts.add(new PriceAlerts.Alert("JADE", true, true, 29_000));
		PriceAlerts.add(new PriceAlerts.Alert("RUBY", false, false, 1_000_000));
		PriceAlerts.remove(new PriceAlerts.Alert("RUBY", false, false, 1_000_000));
		PriceAlerts.resetForTests();
		PriceAlerts.load(f);
		assertEquals(List.of(new PriceAlerts.Alert("JADE", true, true, 29_000)), PriceAlerts.all());
		assertEquals(1, PriceAlerts.forItem("JADE").size());
	}

	@Test
	void typedNamesFindTheItem() {
		List<String> ids = List.of("FINE_JADE_GEM", "FLAWED_JADE_GEM", "DIAMOND", "ENCHANTED_DIAMOND");
		assertEquals("DIAMOND", PriceAlerts.find("diamond", ids));                  // exact name beats a longer one containing it
		assertEquals("ENCHANTED_DIAMOND", PriceAlerts.find("enchanted dia", ids));
		assertNull(PriceAlerts.find("x", ids));
		assertNull(PriceAlerts.find("nothing like it", ids));
	}
}
