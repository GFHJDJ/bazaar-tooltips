package com.unc.bazaartips;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdvisorTest {
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
		prices.put("ENCHANTED_DIAMOND", TestPrices.busy(1700, 1650));
		prices.put("ENCHANTED_DIAMOND_BLOCK", TestPrices.busy(280_000, 270_000));
		prices.put("REFINED_DIAMOND", TestPrices.busy(700_000, 650_000));
		prices.put("SCAM", TestPrices.of(300, 100, 900, 100, 2, 50));         // extreme spread on a thin book
		npc.put("DIAMOND", 12.0);
	}

	private List<Advisor.Option> options(double coins, int slots, String show, Set<String> exSrc, Set<String> exItems) {
		MoneyEngine.Ctx m = new MoneyEngine.Ctx(prices::get, npc::get, id -> null, TAX, false, false, true, "all", 5, coins, slots);
		List<Flipper.Flip> flips = Flipper.compute(prices.keySet(), prices::get, TAX);
		return Advisor.options(new Advisor.Ctx(m, new ArrayList<>(prices.keySet()), flips, true, new CraftEngine(prices::get, TAX, 1),
				Settings.Reqs.ALL, Settings.Compactor.ANY, show, exSrc, exItems));
	}

	private static Set<String> kinds(List<Advisor.Option> list) {
		Set<String> out = new HashSet<>();
		for (Advisor.Option o : list) out.add(o.src);
		return out;
	}

	@Test
	void everyIdeaMakesMoneyAndFitsYourCoins() {
		double coins = 2_000_000;
		List<Advisor.Option> list = options(coins, 0, "all", Set.of(), Set.of());
		assertTrue(kinds(list).containsAll(Set.of("flip", "npc", "shop", "forge", "craft")), "kinds found: " + kinds(list));
		for (Advisor.Option o : list) {
			assertTrue(o.best.n() >= 1, o.key);
			assertTrue(o.coins > 0, o.key + " must make money");
			assertTrue(o.cost <= coins + 1e-6, o.key + " costs " + o.cost);
			assertEquals(o.coins, o.profitAt(o.best.n()), 1e-6, o.key);
			assertEquals(o.coins / o.cost * 100, o.pct, 1e-6, o.key);
		}
	}

	@Test
	void theNumbersMatchEachTabsOwnMath() {
		List<Advisor.Option> list = options(2_000_000, 0, "all", Set.of(), Set.of());
		Advisor.Option forge = null, npcFlip = null;
		for (Advisor.Option o : list) {
			if (o.key.equals("forge:REFINED_DIAMOND")) forge = o;
			if (o.key.equals("npc:DIAMOND")) npcFlip = o;
		}
		assertNotNull(forge);
		assertEquals(3, forge.best.n());                                      // 2M covers three at 560k
		assertEquals(3 * (650_000 * (1 - TAX) - 560_000), forge.coins, 1e-6);
		assertEquals(3 * 560_000, forge.cost, 1e-6);
		assertNotNull(npcFlip);
		assertEquals(2.0 * npcFlip.best.n(), npcFlip.coins, 1e-6);            // NPC pays 12, costs 10
	}

	@Test
	void nothingWithoutCoinsOrSlotsAndRiskyFlipsAreLeftOut() {
		assertTrue(options(0, 0, "all", Set.of(), Set.of()).isEmpty());
		for (Advisor.Option o : options(2_000_000, 0, "all", Set.of(), Set.of())) assertNotEquals("flip:SCAM", o.key);
	}

	@Test
	void showAndLeaveOut() {
		assertEquals(Set.of("forge"), kinds(options(2_000_000, 0, "forge", Set.of(), Set.of())));
		assertFalse(kinds(options(2_000_000, 0, "all", Set.of("forge", "flip"), Set.of())).contains("forge"));
		for (Advisor.Option o : options(2_000_000, 0, "all", Set.of(), Set.of("REFINED_DIAMOND"))) assertNotEquals("REFINED_DIAMOND", o.itemId);
	}

	@Test
	void freeSlotsLimitTheAmount() {
		for (Advisor.Option o : options(2_000_000, 3, "all", Set.of(), Set.of())) {
			assertTrue(o.slotsUsed(o.best.n()) <= 3, o.key + " uses " + o.slotsUsed(o.best.n()) + " slots");
		}
	}

	@Test
	void theSuggestedSplitNeverSpendsMoreThanYouHave() {
		double coins = 2_000_000;
		List<Advisor.Option> list = options(coins, 0, "all", Set.of(), Set.of());
		Advisor.Steps steps = Advisor.steps(list, coins, 0);
		assertFalse(steps.steps().isEmpty());
		assertTrue(steps.steps().size() <= 3);
		double spent = 0, best = 0;
		Set<String> items = new HashSet<>();
		for (Advisor.Option o : list) best = Math.max(best, o.coins);
		for (Advisor.Step s : steps.steps()) {
			spent += s.cost();
			assertTrue(s.profit() > 0);
			assertTrue(items.add(s.option().itemId), "the same item twice: " + s.option().itemId);
		}
		assertTrue(spent <= coins + 1e-6);
		assertEquals(coins - spent, steps.left(), 1e-6);
		assertEquals(best, steps.steps().get(0).profit(), 1e-6);              // the most profitable idea goes first
	}
}
