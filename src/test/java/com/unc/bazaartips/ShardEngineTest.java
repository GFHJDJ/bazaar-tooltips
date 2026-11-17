package com.unc.bazaartips;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShardEngineTest {
	private static final double TAX = 0.0125;
	private static final String DATA = """
			{"shards":{
			  "C1":["SHARD_GROVE","Grove","common",2,"Elemental Family"],
			  "C2":["SHARD_MIST","Mist","common",2,"Elemental Family"],
			  "U1":["SHARD_BIRRIES","Birries","uncommon",5,""],
			  "R1":["SHARD_LADYBUG","Ladybug","rare",5,"Bug Family"],
			  "R2":["SHARD_NOT_LISTED","Nowhere","rare",5,""]},
			 "recipes":{
			  "R1":{"1":"C1 U1,C2 U1","2":"C1 C2"},
			  "U1":{"1":"R2 C1"}}}""";

	private final Map<String, BazaarPrices.Price> prices = new HashMap<>();

	@BeforeEach
	void setUp() {
		ShardEngine.load(DATA);
		prices.clear();
		prices.put("SHARD_GROVE", TestPrices.busy(100, 90));
		prices.put("SHARD_MIST", TestPrices.busy(300, 280));
		prices.put("SHARD_BIRRIES", TestPrices.busy(50, 40));
		prices.put("SHARD_LADYBUG", TestPrices.busy(2_000, 1_800));
	}

	@Test
	void theCheapestPairPerShardMadeWins() {
		List<ShardEngine.Fusion> f = ShardEngine.fusions(prices::get, TAX, false, false);
		assertEquals(1, f.size());                                  // Birries' recipe needs an unlisted shard
		ShardEngine.Fusion lady = f.get(0);
		// Grove+Birries: 2x100 + 5x50 = 450 for 1; Mist+Birries: 600 + 250 = 850; Grove+Mist: 200 + 600 = 800 for 2 (400 each)
		assertEquals("Grove", lady.best().a().name());
		assertEquals("Mist", lady.best().b().name());
		assertEquals(2, lady.best().out());
		assertEquals(800, lady.best().cost(), 1e-9);
		assertEquals(1_800 * (1 - TAX), lady.sellEach(), 1e-9);         // insta-sold
		assertEquals(2 * 1_800 * (1 - TAX) - 800, lady.profit(), 1e-9);
		assertEquals(2, lady.others().size());
		assertEquals(450, lady.others().get(0).cost(), 1e-9);           // next cheapest per shard made
	}

	@Test
	void buyOrdersAndSellOffersChangeThePrices() {
		ShardEngine.Fusion lady = ShardEngine.fusions(prices::get, TAX, true, true).get(0);
		assertEquals(2 * 90.1 + 2 * 280.1, lady.best().cost(), 1e-9);    // 0.1 over the top buy orders
		assertEquals(1_999.9 * (1 - TAX), lady.sellEach(), 1e-9);       // 0.1 under the lowest sell offer
	}

	@Test
	void sortingAndSearch() {
		prices.put("SHARD_NOT_LISTED", TestPrices.busy(10, 9));
		List<ShardEngine.Fusion> f = ShardEngine.fusions(prices::get, TAX, false, false);
		assertEquals(2, f.size());
		assertEquals("Ladybug", ShardEngine.sorted(f, "profit", "").get(0).result().name());
		assertEquals("Birries", ShardEngine.sorted(f, "cost", "").get(0).result().name());   // 5x10 + 2x100
		assertEquals(List.of("Ladybug"), ShardEngine.sorted(f, "profit", "bug family").stream().map(x -> x.result().name()).toList());
		assertEquals("5x Nowhere + 2x Grove", ShardEngine.recipeText(ShardEngine.sorted(f, "cost", "").get(0).best()));
	}

	@Test
	void theBundledDataLoads() {
		ShardEngine.load();
		assertTrue(ShardEngine.shardCount() > 300);
	}
}
