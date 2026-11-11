package com.unc.bazaartips;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CraftEngineTest {
	private static final double TAX = 0.0125;
	private static final String RECIPES = """
			{"recipes":{
			  "ENCHANTED_DIAMOND":{"name":"Enchanted Diamond","out":1,"in":[["DIAMOND",160]]},
			  "ENCHANTED_DIAMOND_BLOCK":{"name":"Enchanted Diamond Block","out":1,"in":[["ENCHANTED_DIAMOND",160]]},
			  "DIAMOND_BLOCK":{"name":"Block of Diamond","out":9,"in":[["DIAMOND",81],["COAL",2]]},
			  "MYSTERY":{"name":"Mystery","out":1,"in":[["NOT_ON_BAZAAR",1]]}},
			 "names":{"DIAMOND":"Diamond","COAL":"Coal"}}""";

	private final Map<String, BazaarPrices.Price> prices = new HashMap<>();
	private CraftEngine engine;

	@BeforeEach
	void setUp() {
		Recipes.load(RECIPES);
		prices.clear();
		prices.put("DIAMOND", TestPrices.busy(10, 9));
		prices.put("COAL", TestPrices.busy(4, 3));
		prices.put("ENCHANTED_DIAMOND", TestPrices.busy(1700, 1650));
		prices.put("ENCHANTED_DIAMOND_BLOCK", TestPrices.busy(280_000, 270_000));
		prices.put("DIAMOND_BLOCK", TestPrices.busy(100, 95));
		prices.put("MYSTERY", TestPrices.busy(100, 95));
		engine = new CraftEngine(prices::get, TAX, 1);
	}

	@Test
	void pricesToPlaceOrdersAt() {
		BazaarPrices.Price p = TestPrices.busy(10, 9);
		assertEquals(10, CraftEngine.instaBuyAt(p));
		assertEquals(9, CraftEngine.instaSellAt(p));
		assertEquals(9.1, CraftEngine.orderBuyAt(p), 1e-9);
		assertEquals(9.9, CraftEngine.offerSellAt(p), 1e-9);
	}

	@Test
	void instaBuyCraftCostAndProfit() {
		CraftEngine.Craft c = engine.craft("ENCHANTED_DIAMOND", false, false);
		assertNotNull(c);
		assertEquals(1600, c.cost(), 1e-9);                                   // 160 x 10
		assertEquals(1699.9 * (1 - TAX) - 1600, c.profitSellOffer(), 1e-6);   // sell offer 0.1 under 1,700
		assertEquals(1650 * (1 - TAX) - 1600, c.profitInstaSell(), 1e-6);
		assertEquals(1600 / (1 - TAX), c.breakEven(), 1e-6);
		assertEquals(c.profitSellOffer() / 1600 * 100, c.marginSellOffer(), 1e-9);
		assertFalse(c.usesChain());
	}

	@Test
	void buyOrderCraftIsCheaper() {
		CraftEngine.Craft c = engine.craft("ENCHANTED_DIAMOND", true, false);
		assertEquals(160 * 9.1, c.cost(), 1e-9);                              // 0.1 over the top buy order
		assertTrue(c.profitSellOffer() > engine.craft("ENCHANTED_DIAMOND", false, false).profitSellOffer());
	}

	@Test
	void aCraftThatMakesNineIsPricedPerCraft() {
		CraftEngine.Craft c = engine.craft("DIAMOND_BLOCK", false, false);
		assertEquals(9, c.output());
		assertEquals(81 * 10 + 2 * 4, c.cost(), 1e-9);
		assertEquals(9 * 99.9 * (1 - TAX) - 818, c.profitSellOffer(), 1e-6);
		assertEquals(c.profitSellOffer() / 9 * 64, c.perSlotSellOffer(), 1e-6);
	}

	@Test
	void theMostYouCanPayForAnIngredientLeavesExactlyNoProfit() {
		CraftEngine.Craft c = engine.craft("DIAMOND_BLOCK", false, false);
		CraftEngine.Ingredient diamond = c.ingredients().get(0);
		double restOfCost = c.cost() - diamond.qty() * diamond.unit();
		assertEquals(0, 9 * 99.9 * (1 - TAX) - restOfCost - diamond.qty() * diamond.maxSO(), 1e-6);
		assertEquals(0, 9 * 95 * (1 - TAX) - restOfCost - diamond.qty() * diamond.maxIS(), 1e-6);
	}

	@Test
	void chainsCraftAnIngredientWhenThatsCheaperThanBuyingIt() {
		CraftEngine.Craft plain = engine.craft("ENCHANTED_DIAMOND_BLOCK", false, false);
		assertEquals(160 * 1700.0, plain.cost(), 1e-6);
		assertFalse(plain.usesChain());
		CraftEngine.Craft chained = engine.craft("ENCHANTED_DIAMOND_BLOCK", false, true);
		assertEquals(160 * 1600.0, chained.cost(), 1e-6);                     // crafting each Enchanted Diamond costs 1,600
		assertTrue(chained.usesChain());
		assertTrue(chained.ingredients().get(0).viaCraft());
		assertEquals(Map.of("DIAMOND", 160.0 * 160), CraftEngine.leaf(chained, 1));   // what you actually buy
		assertEquals(400, CraftEngine.slotsForCrafts(chained, 1));
	}

	@Test
	void chainsBuyWhenBuyingIsCheaper() {
		prices.put("ENCHANTED_DIAMOND", TestPrices.busy(1500, 1450));
		engine = new CraftEngine(prices::get, TAX, 2);
		CraftEngine.Craft c = engine.craft("ENCHANTED_DIAMOND_BLOCK", false, true);
		assertEquals(160 * 1500.0, c.cost(), 1e-6);
		assertFalse(c.usesChain());
	}

	@Test
	void aCraftWithAnIngredientThatHasNoPriceIsLeftOut() {
		assertNull(engine.craft("MYSTERY", false, false));
		assertNull(engine.craft("MYSTERY", false, true));
		assertEquals(3, engine.crafts(false, false).size());
		prices.remove("ENCHANTED_DIAMOND");                                   // a product that isn't on the bazaar
		assertNull(new CraftEngine(prices::get, TAX, 3).craft("ENCHANTED_DIAMOND", false, false));
	}

	@Test
	void walkingTheOrderBook() {
		List<BazaarPrices.Level> book = List.of(new BazaarPrices.Level(10, 100), new BazaarPrices.Level(12, 50));
		CraftEngine.BookWalk w = CraftEngine.walkBook(book, 120);
		assertEquals(100 * 10 + 20 * 12, w.total(), 1e-9);
		assertEquals(120, w.filled(), 1e-9);
		assertEquals(0, w.shortBy(), 1e-9);
		w = CraftEngine.walkBook(book, 200);
		assertEquals(150, w.filled(), 1e-9);
		assertEquals(50, w.shortBy(), 1e-9);
	}

	@Test
	void instaFlipDepthStopsWhenTheBookStopsPaying() {
		// diamonds: 320 at 10, then 100. enchanted diamond buyers: 5 at 1,650.
		prices.put("DIAMOND", new BazaarPrices.Price(10, 9, 500_000, 500_000,
				List.of(new BazaarPrices.Level(10, 320, 5), new BazaarPrices.Level(100, 10_000, 5)), List.of(new BazaarPrices.Level(9, 1000, 5))));
		prices.put("ENCHANTED_DIAMOND", new BazaarPrices.Price(1700, 1650, 500_000, 500_000,
				List.of(new BazaarPrices.Level(1700, 100, 5)), List.of(new BazaarPrices.Level(1650, 5, 5))));
		engine = new CraftEngine(prices::get, TAX, 4);
		CraftEngine.Craft c = engine.craft("ENCHANTED_DIAMOND", false, false);
		CraftEngine.Depth d = engine.depth(c);
		assertEquals(2, d.crafts());                                          // the third craft would need diamonds at 100
		assertEquals(2 * (1650 * (1 - TAX) - 1600), d.profit(), 1e-6);
	}

	@Test
	void theBestAmountStaysWithinYourCoinsAndSlots() {
		CraftEngine.Craft c = engine.craft("ENCHANTED_DIAMOND", false, false);
		assertNull(engine.best(c, null, 0, 0));                               // no coins or slots set: nothing to size
		CraftEngine.Best b = engine.best(c, CraftEngine.Sell.OFFER, 5000, 0);
		assertNotNull(b);
		assertEquals(3, b.n());                                               // 3 x 1,600 fits in 5,000; a 4th doesn't
		assertEquals("coins", b.why());
		assertEquals(3 * c.profitSellOffer(), b.profit(), 1e-6);
		assertTrue(b.sizing().costAt(b.n()) <= 5000);
		CraftEngine.Best s = engine.best(c, CraftEngine.Sell.OFFER, 1_000_000, 4);
		assertEquals(1, s.n());                                               // 160 diamonds take 3 slots; two crafts (320) take 5
		assertEquals("slots", s.why());
		assertEquals(2, engine.best(c, CraftEngine.Sell.OFFER, 1_000_000, 5).n());
		assertNull(engine.best(c, CraftEngine.Sell.OFFER, 100, 0));           // can't afford even one
	}
}
