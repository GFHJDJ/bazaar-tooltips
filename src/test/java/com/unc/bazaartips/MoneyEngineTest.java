package com.unc.bazaartips;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MoneyEngineTest {
	private static final double TAX = 0.0125;
	private final Map<String, BazaarPrices.Price> prices = new HashMap<>();
	private final Map<String, Double> npc = new HashMap<>();

	@BeforeEach
	void setUp() {
		TestData.load();
		prices.clear();
		npc.clear();
		prices.put("DIAMOND", TestPrices.busy(10, 9));
		prices.put("WHEAT", TestPrices.busy(5, 4));
		prices.put("ENCHANTED_DIAMOND_BLOCK", TestPrices.busy(280_000, 270_000));
		prices.put("REFINED_DIAMOND", TestPrices.busy(700_000, 650_000));
		prices.put("TOP_TIER_THING", TestPrices.busy(500, 400));
		npc.put("DIAMOND", 12.0);
		npc.put("WHEAT", 2.0);
	}

	private MoneyEngine engine(boolean buyOrder, boolean sellOffer, int hotm, double coins, int slots) {
		return new MoneyEngine(new MoneyEngine.Ctx(prices::get, npc::get, id -> null, TAX, buyOrder, sellOffer, true, "all", hotm, coins, slots));
	}

	private static MoneyEngine.Row row(List<MoneyEngine.Row> rows, String key) {
		for (MoneyEngine.Row r : rows) if (r.key.equals(key)) return r;
		return null;
	}

	// ---- Bazaar -> NPC ----

	@Test
	void bazaarToNpcProfitIsNpcPriceMinusWhatYouPay() {
		List<MoneyEngine.Row> rows = engine(false, false, 0, 0, 0).npcFromBazaar(prices.keySet());
		MoneyEngine.Row d = row(rows, "DIAMOND");
		assertNotNull(d);
		assertEquals(2, d.profit, 1e-9);               // NPC pays 12, insta-buy costs 10 (no tax selling to NPCs)
		assertEquals(20, d.pct, 1e-9);
		assertNull(row(rows, "WHEAT"));                // NPC pays less than it costs: not listed
		assertEquals(1, rows.size());                  // items no NPC buys aren't listed either
	}

	@Test
	void buyOrdersMakeBazaarToNpcCheaper() {
		MoneyEngine.Row d = row(engine(true, false, 0, 0, 0).npcFromBazaar(prices.keySet()), "DIAMOND");
		assertEquals(3, d.profit, 1e-9);               // a buy order at 9 instead of insta-buying at 10
	}

	// ---- NPC shop -> Bazaar ----

	@Test
	void npcShopProfitIsBazaarSaleAfterTaxMinusShopPrice() {
		List<MoneyEngine.Row> rows = engine(false, false, 0, 0, 0).npcShops();
		assertEquals(1, rows.size());
		MoneyEngine.Row w = rows.get(0);
		assertEquals("WHEAT", w.itemId);
		assertEquals(7, w.costEach, 1e-9);                             // 3 wheat for 7 coins
		assertEquals(3 * 4 * (1 - TAX), w.value, 1e-9);                // insta-sold at 4 each, after tax
		assertEquals(3 * 4 * (1 - TAX) - 7, w.profit, 1e-9);
		MoneyEngine.Row offer = engine(false, true, 0, 0, 0).npcShops().get(0);
		assertEquals(3 * 5 * (1 - TAX) - 7, offer.profit, 1e-9);       // a sell offer at 5 pays more
	}

	@Test
	void aShopThatLosesMoneyIsntListed() {
		prices.put("WHEAT", TestPrices.busy(2, 1));
		assertTrue(engine(false, false, 0, 0, 0).npcShops().isEmpty());
	}

	// ---- Forge ----

	@Test
	void forgeProfitAndProfitPerHour() {
		MoneyEngine.Row r = row(engine(false, false, 0, 0, 0).forge(), "REFINED_DIAMOND");
		assertNotNull(r);
		assertEquals(560_000, r.costEach, 1e-6);                       // 2 blocks insta-bought at 280k
		assertEquals(650_000 * (1 - TAX), r.value, 1e-6);
		assertEquals(650_000 * (1 - TAX) - 560_000, r.profit, 1e-6);
		assertEquals(r.profit / 8, r.perHour, 1e-6);                   // 8 hours in the forge
		assertEquals(r.profit / 560_000 * 100, r.pct, 1e-9);
	}

	@Test
	void yourHotmTierHidesWhatYouCantForgeAndSetsYourSlots() {
		assertNotNull(row(engine(false, false, 0, 0, 0).forge(), "TOP_TIER_THING"));
		assertNull(row(engine(false, false, 2, 0, 0).forge(), "TOP_TIER_THING"));
		assertNotNull(row(engine(false, false, 2, 0, 0).forge(), "REFINED_DIAMOND"));
		assertEquals(2, MoneyEngine.forgeSlots(0));
		assertEquals(0, MoneyEngine.forgeSlots(1));
		assertEquals(5, MoneyEngine.forgeSlots(5));
		assertEquals(7, MoneyEngine.forgeSlots(10));
	}

	@Test
	void theBestForgeAmountFitsYourCoinsAndForgeSlots() {
		MoneyEngine.Row r = row(engine(false, false, 5, 1_200_000, 0).forge(), "REFINED_DIAMOND");
		assertNotNull(r.best);
		assertEquals(2, r.best.n());                                   // 1.2M covers two at 560k each
		assertEquals("coins", r.best.why());
		assertEquals(2 * (650_000 * (1 - TAX) - 560_000), r.best.profit(), 1e-6);
		MoneyEngine.Row rich = row(engine(false, false, 5, 1_000_000_000, 0).forge(), "REFINED_DIAMOND");
		assertEquals(5, rich.best.n());                                // plenty of coins: your 5 forge slots stop you
		assertEquals("cap", rich.best.why());
	}

	// ---- buying and selling bigger amounts ----

	@Test
	void instaBuyingALotWalksUpTheOrderBook() {
		BazaarPrices.Price p = new BazaarPrices.Price(10, 9, 500_000, 500_000,
				List.of(new BazaarPrices.Level(10, 100, 5), new BazaarPrices.Level(12, 100, 5)),
				List.of(new BazaarPrices.Level(9, 100, 5), new BazaarPrices.Level(7, 100, 5)));
		MoneyEngine e = engine(false, false, 0, 0, 0);
		assertEquals(100 * 10 + 50 * 12, e.bzBuyCost(p, 150, false).total(), 1e-9);
		assertEquals((100 * 9 + 50 * 7) * (1 - TAX), e.bzSellGet(p, 150).total(), 1e-9);
		assertEquals(150 * 9.0, e.bzBuyCost(p, 150, true).total(), 1e-9);             // a buy order: one price for all
		assertEquals(150 * 10 * (1 - TAX), engine(false, true, 0, 0, 0).bzSellGet(p, 150).total(), 1e-9);
		// more than the book shows: the rest is estimated at the last price
		assertEquals(100 * 10 + 100 * 12 + 50 * 12, e.bzBuyCost(p, 250, false).total(), 1e-9);
	}
}
