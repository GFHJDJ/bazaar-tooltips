package com.unc.bazaartips;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InventoryValueTest {
	private static final double TAX = 0.0125;
	private final Map<String, BazaarPrices.Price> prices = new HashMap<>();
	private final Map<String, Double> npc = new HashMap<>();
	private final Map<String, Double> ah = new HashMap<>();

	private List<InventoryValue.Row> rows(InventoryValue.Stack... stacks) {
		return InventoryValue.rows(List.of(stacks), prices::get, npc::get, ah::get, TAX);
	}

	private static InventoryValue.Stack bz(String id, int n) {
		return new InventoryValue.Stack(id, id, id, n);
	}

	@Test
	void stacksOfTheSameItemAreAddedUp() {
		prices.put("DIAMOND", TestPrices.busy(10, 9));
		List<InventoryValue.Row> r = rows(bz("DIAMOND", 64), bz("DIAMOND", 10));
		assertEquals(1, r.size());
		assertEquals(74, r.get(0).count());
	}

	@Test
	void insta_sellingWalksTheBuyOrdersAfterTax() {
		prices.put("GEODE", new BazaarPrices.Price(7_500_000, 7_000_000, 30, 27,
				List.of(new BazaarPrices.Level(7_500_000, 5, 2)),
				List.of(new BazaarPrices.Level(7_000_000, 2, 2), new BazaarPrices.Level(6_200_000, 5, 5))));
		InventoryValue.Row r = rows(bz("GEODE", 4)).get(0);
		assertEquals((2 * 7_000_000 + 2 * 6_200_000) * (1 - TAX), r.instaSell(), 1e-6);
		assertEquals(4 * 7_499_999.9 * (1 - TAX), r.sellOffer(), 1e-3);    // 0.1 under the lowest sell offer
		assertEquals("bazaar", r.best());
		assertEquals(r.sellOffer() - r.instaSell(), r.offerExtra(), 1e-6);
		assertEquals(27, r.weekVol());
	}

	@Test
	void anNpcThatPaysMoreWins() {
		prices.put("WHEAT", TestPrices.busy(5, 4));
		npc.put("WHEAT", 6.0);
		InventoryValue.Row r = rows(bz("WHEAT", 100)).get(0);
		assertEquals("npc", r.best());
		assertEquals(600, r.bestValue(), 1e-9);
		InventoryValue.Totals t = InventoryValue.totals(List.of(r));
		assertEquals(1, t.npcCount());
		assertEquals(600 - 100 * 4 * (1 - TAX), t.npcBeatsBazaar(), 1e-9);
	}

	@Test
	void notOnTheBazaarUsesTheAhAndTheRestHasNoPrice() {
		ah.put("HYPERION", 500_000_000.0);
		List<InventoryValue.Row> r = rows(new InventoryValue.Stack("HYPERION", null, "Hyperion", 1), new InventoryValue.Stack("PET", null, "[Lvl 1] Bee", 1));
		InventoryValue.Row hype = r.get(0), pet = r.get(1);
		assertEquals("ah", hype.best());
		assertEquals(500_000_000, hype.bestValue(), 1e-6);
		assertTrue(Double.isNaN(hype.instaSell()));
		assertFalse(pet.priced());
		InventoryValue.Totals t = InventoryValue.totals(r);
		assertEquals(500_000_000, t.now(), 1e-6);
		assertEquals(500_000_000, t.ahValue(), 1e-6);
		assertEquals(1, t.unpriced());
	}

	@Test
	void totalsAndSorting() {
		prices.put("DIAMOND", TestPrices.busy(10, 9));
		prices.put("COAL", TestPrices.busy(4, 3));
		List<InventoryValue.Row> r = rows(bz("COAL", 64), new InventoryValue.Stack("PET", null, "Bee", 1), bz("DIAMOND", 64));
		InventoryValue.Totals t = InventoryValue.totals(r);
		assertEquals(64 * 9 * (1 - TAX) + 64 * 3 * (1 - TAX), t.now(), 1e-9);
		assertEquals(64 * 9.9 * (1 - TAX) + 64 * 3.9 * (1 - TAX), t.withOffers(), 1e-9);
		List<InventoryValue.Row> byValue = InventoryValue.sorted(r, "value", "");
		assertEquals(List.of("DIAMOND", "COAL", "PET"), byValue.stream().map(InventoryValue.Row::skyblockId).toList());   // no price goes last
		assertEquals(List.of("DIAMOND"), InventoryValue.sorted(r, "value", "diam").stream().map(InventoryValue.Row::skyblockId).toList());
	}
}
