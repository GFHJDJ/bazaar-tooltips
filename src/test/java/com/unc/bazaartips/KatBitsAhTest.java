package com.unc.bazaartips;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KatBitsAhTest {
	private static final double TAX = 0.0125;
	private static final String SITE = """
			{"forge":[],"shops":[],
			 "npcs":{"KAT_NPC":["Kat",10,73,-53,"Hub",1],"ELIZABETH_NPC":["Elizabeth",-6,80,17,"Hub",1],"FARM_MERCHANT":["Farm Merchant","Hub"]},
			 "npcAvail":{},"stack":{},"siteNames":{"SEAL;2":"Seal","SEAL;3":"Seal","FROG;4":"Frog","FROG;5":"Frog"},
			 "cur":[{"npc":"FARM_MERCHANT","id":"WHEAT","q":1,"cur":"copper","amt":10,"c":0,"in":[]}],
			 "kat":[{"from":"SEAL;2","to":"SEAL;3","c":100000,"t":86400,"in":[["DIAMOND",10]]},
			        {"from":"FROG;4","to":"FROG;5","c":5000000,"t":3600,"in":[]}],
			 "bits":[{"id":"KISMET_FEATHER","q":1,"bits":1350,"in":[],"c":0,"npc":"ELIZABETH_NPC"},
			         {"id":"GOD_POTION_2","q":1,"bits":1500,"in":[],"c":0,"npc":"ELIZABETH_NPC"}]}""";
	private static final String RECIPES = """
			{"recipes":{
			  "AH_THING":{"name":"AH Thing","out":1,"in":[["AH_ONLY",1],["DIAMOND",2]]},
			  "PLAIN_THING":{"name":"Plain Thing","out":1,"in":[["DIAMOND",2]]},
			  "LOCKED_THING":{"name":"Locked Thing","out":1,"in":[["AH_ONLY",1]],"req":"Diamond IX"},
			  "NO_PRICE_THING":{"name":"No Price Thing","out":1,"in":[["NOWHERE",1]]}},
			 "names":{"DIAMOND":"Diamond","AH_ONLY":"Rare Relic"}}""";

	private final Map<String, BazaarPrices.Price> prices = new HashMap<>();
	private final Map<String, Double> bins = new HashMap<>();

	@BeforeEach
	void setUp() {
		SiteData.load(SITE);
		Recipes.load(RECIPES);
		prices.clear();
		bins.clear();
		prices.put("DIAMOND", TestPrices.busy(10, 9));
		prices.put("WHEAT", TestPrices.busy(5, 4));
		prices.put("KISMET_FEATHER", TestPrices.busy(1_700_000, 1_600_000));
		prices.put("AH_THING", TestPrices.busy(200, 150));
		prices.put("PLAIN_THING", TestPrices.busy(200, 150));
		prices.put("LOCKED_THING", TestPrices.busy(200, 150));
		prices.put("NO_PRICE_THING", TestPrices.busy(200, 150));
		bins.put("PET-SEAL-RARE", 1_000_000.0);
		bins.put("PET-SEAL-EPIC", 3_000_000.0);
		bins.put("PET-FROG-LEGENDARY", 40_000_000.0);          // no Mythic Frog listed: that upgrade can't be priced
		bins.put("GOD_POTION_2", 2_000_000.0);
		bins.put("AH_ONLY", 50.0);
		AhPrices.set(bins);
	}

	private MoneyEngine engine(boolean buyOrder, boolean bazaarOnly) {
		return new MoneyEngine(new MoneyEngine.Ctx(prices::get, id -> null, AhPrices::get, TAX, buyOrder, false, bazaarOnly, "all", 0, 0, 0));
	}

	// ---- pets in the AH price list ----

	@Test
	void petsAreLookedUpByRarityName() {
		assertEquals("PET-SEAL-EPIC", AhPrices.petKey("SEAL;3"));
		assertEquals("PET-ARMADILLO-COMMON", AhPrices.petKey("ARMADILLO;0"));
		assertNull(AhPrices.petKey("DIAMOND"));
		assertNull(AhPrices.petKey("SEAL;7"));
		assertEquals(3_000_000.0, AhPrices.get("SEAL;3"));
		assertEquals(50.0, AhPrices.get("AH_ONLY"));
		assertNull(AhPrices.get("FROG;5"));
	}

	// ---- Kat ----

	@Test
	void katProfitIsTheUpgradedPetMinusPetCoinsAndMaterials() {
		List<MoneyEngine.Row> rows = engine(false, true).kat();
		assertEquals(1, rows.size());                                  // the Frog has no Mythic listing
		MoneyEngine.Row r = rows.get(0);
		double cost = 1_000_000 + 100_000 + 10 * 10;
		assertEquals(cost, r.costEach, 1e-6);
		assertEquals(3_000_000, r.value, 1e-6);
		assertEquals(3_000_000 - cost, r.profit, 1e-6);
		assertEquals((3_000_000 - cost) / 24, r.perHour, 1e-6);        // one day at Kat
		assertEquals((3_000_000 - cost) / cost * 100, r.pct, 1e-9);
		assertEquals("Seal (Rare) -> Epic", r.title);
		assertEquals("SEAL;2", r.parts.get(0).id());                   // the pet you buy is the first cost line
		assertEquals("AH", r.parts.get(0).src());
		assertTrue(r.meta.get(0).startsWith("Kat · Hub"));
		assertEquals(3_000_000 - (1_000_000 + 100_000 + 10 * 9), engine(true, true).kat().get(0).profit, 1e-6);   // diamonds by buy order
	}

	// ---- Bits ----

	@Test
	void bitsAreRankedByCoinsPerBit() {
		MoneyEngine e = engine(false, true);
		List<MoneyEngine.Row> rows = e.currencies("bits");
		assertEquals(1, rows.size());                                  // the God Potion only sells on the AH
		assertEquals(1, e.viaAH);
		MoneyEngine.Row k = rows.get(0);
		assertEquals("KISMET_FEATHER", k.itemId);
		assertEquals(1_600_000 * (1 - TAX), k.value, 1e-6);            // insta-sold, after tax
		assertEquals(1_600_000 * (1 - TAX) / 1350, k.per, 1e-6);
		assertEquals(1350, k.amt);
		assertTrue(k.meta.get(0).startsWith("Elizabeth"));
		List<MoneyEngine.Row> withAh = MoneyEngine.sort(engine(false, false).currencies("bits"), "per", "");
		assertEquals(2, withAh.size());
		assertEquals("GOD_POTION_2", withAh.get(0).itemId);
		assertEquals(2_000_000.0 / 1500, withAh.get(0).per, 1e-6);
	}

	@Test
	void bitsStayOffTheOtherCurrencies() {
		List<MoneyEngine.Row> copper = engine(false, true).currencies("copper");
		assertEquals(1, copper.size());
		assertEquals("WHEAT", copper.get(0).itemId);
	}

	// ---- AH -> Bazaar ----

	@Test
	void aBazaarQuoteInTheAhListIsntAnAuction() {
		BazaarPrices.Price p = TestPrices.busy(100, 90);
		assertTrue(AhEngine.isBazaarPrice(99, p));                     // within 3% of the sell offers
		assertTrue(AhEngine.isBazaarPrice(90.5, p));
		assertFalse(AhEngine.isBazaarPrice(70, p));
		assertFalse(AhEngine.isBazaarPrice(95, p));                    // in the gap but more than 3% from both sides
	}

	@Test
	void ahListingsBelowTheBazaarPrice() {
		prices.put("A", TestPrices.busy(100, 90));
		prices.put("B", TestPrices.busy(100, 90));
		prices.put("C", TestPrices.busy(100, 90));
		Map<String, Double> b = Map.of("A", 99.5, "B", 70.0);
		List<AhEngine.Row> rows = AhEngine.arb(List.of("A", "B", "C"), b, prices::get, TAX);
		assertEquals(1, rows.size());
		AhEngine.Row r = rows.get(0);
		assertEquals("B", r.id());
		assertEquals(100 * (1 - TAX) - 70, r.profit(true), 1e-9);
		assertEquals(90 * (1 - TAX) - 70, r.profit(false), 1e-9);
		assertEquals((90 * (1 - TAX) - 70) / 70 * 100, r.pct(false), 1e-9);
		assertTrue(AhEngine.arb(List.of("B"), Map.of("B", 101.0), prices::get, TAX).isEmpty());   // dearer than the Bazaar
	}

	// ---- AH crafts ----

	@Test
	void ahCraftsNeedAnAhOnlyIngredient() {
		List<AhEngine.Row> rows = AhEngine.crafts(Recipes.all(), bins, prices::get, TAX, Settings.Reqs.ALL);
		assertEquals(List.of("AH_THING", "LOCKED_THING"), rows.stream().map(AhEngine.Row::id).sorted().toList());
		AhEngine.Row r = rows.stream().filter(x -> x.id().equals("AH_THING")).findFirst().orElseThrow();
		assertEquals(50 + 2 * 10, r.cost(), 1e-9);
		assertEquals(200 * (1 - TAX) - 70, r.profit(true), 1e-9);
		assertEquals(150 * (1 - TAX) - 70, r.profit(false), 1e-9);
		assertTrue(r.parts().get(0).ah());
		assertFalse(r.parts().get(1).ah());
		assertEquals(List.of("AH_THING"), AhEngine.crafts(Recipes.all(), bins, prices::get, TAX, Settings.Reqs.NONE).stream().map(AhEngine.Row::id).toList());
	}

	// ---- lookup ----

	@Test
	void lookupFindsAnyAhItemByName() {
		assertTrue(AhEngine.lookup("s", bins, prices::get, 50).isEmpty());   // needs 2+ letters
		List<AhEngine.Row> seals = AhEngine.lookup("seal", bins, prices::get, 50);
		assertEquals(List.of("Seal pet (Epic)", "Seal pet (Rare)"), seals.stream().map(AhEngine.Row::name).sorted().toList());
		bins.put("AH_THING", 300.0);
		AhEngine.Row thing = AhEngine.lookup("ah thing", bins, prices::get, 50).get(0);
		assertEquals(300, thing.bin());
		assertEquals(70, thing.cost(), 1e-9);
		assertEquals(230, thing.profit(true), 1e-9);
		assertTrue(Double.isNaN(AhEngine.lookup("seal", bins, prices::get, 50).get(0).cost()));   // not craftable
		assertEquals("Sharpness VI (book)", AhEngine.ahName("ENCHANTED_BOOK-SHARPNESS-6"));
		assertEquals("Armadillo pet (Mythic, level 100)", AhEngine.ahName("PET-ARMADILLO-MYTHIC-100"));
	}
}
