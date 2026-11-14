package com.unc.bazaartips;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EarningsTest {
	private static final long HOUR = 3_600_000L;

	@BeforeEach
	void reset() {
		Earnings.resetForTests(1000);
	}

	private static Earnings.Trade buy(long at, String name, long qty, double coins) {
		return new Earnings.Trade(at, Earnings.Kind.BUY, name, qty, coins, false);
	}

	private static Earnings.Trade sell(long at, String name, long qty, double coins) {
		return new Earnings.Trade(at, Earnings.Kind.SELL, name, qty, coins, false);
	}

	// ---- reading chat ----

	@Test
	void readsAllFourKindsOfBazaarMessage() {
		Earnings.Trade t = Earnings.parse("§6[Bazaar] §r§7Bought §r§a1,280§r§7x §r§fEnchanted Diamond §r§7for §r§6204,800 coins§r§7!", 5);
		assertEquals(new Earnings.Trade(5, Earnings.Kind.BUY, "Enchanted Diamond", 1280, 204800, true), t);
		t = Earnings.parse("[Bazaar] Sold 64x Diamond for 601.6 coins!", 5);
		assertEquals(new Earnings.Trade(5, Earnings.Kind.SELL, "Diamond", 64, 601.6, true), t);
		t = Earnings.parse("[Bazaar] Claimed 64x Diamond worth 640 coins bought for 10.0 each!", 5);
		assertEquals(new Earnings.Trade(5, Earnings.Kind.BUY, "Diamond", 64, 640, false), t);
		t = Earnings.parse("[Bazaar] Claimed 3,160 coins from selling 640x Coal at 5.0 each!", 5);
		assertEquals(new Earnings.Trade(5, Earnings.Kind.SELL, "Coal", 640, 3160, false), t);
	}

	@Test
	void otherChatIsNotATrade() {
		assertNull(Earnings.parse("Unc: [Bazaar] Bought 64x Diamond for 640 coins!", 5));       // a player typing it
		assertNull(Earnings.parse("[Bazaar] Your Buy Order for 64x Diamond was filled!", 5));
		assertNull(Earnings.parse("[Bazaar] Buy Order Setup! 64x Diamond for 640 coins.", 5));
		assertNull(Earnings.parse("[Bazaar] Cancelled! Refunded 640 coins from cancelling Buy Order!", 5));
		assertNull(Earnings.parse("[Auction] Unc bought Diamond Sword for 500 coins", 5));
		assertFalse(Earnings.onChat("Welcome to Hypixel SkyBlock!", 5));
		assertTrue(Earnings.trades().isEmpty());
	}

	// ---- the sums ----

	@Test
	void profitIsWhatYouSoldForMinusWhatThoseCost() {
		Earnings.Summary s = Earnings.summarize(List.of(buy(10, "Diamond", 100, 1000), sell(20, "Diamond", 60, 900)), 0);
		assertEquals(1000, s.spent());
		assertEquals(900, s.earned());
		assertEquals(-100, s.net());
		assertEquals(300, s.profit(), 1e-9);           // 900 - 60 x 10
		assertEquals(0, s.otherSales(), 1e-9);
		assertEquals(40, s.holdingItems());
		assertEquals(400, s.holdingCost(), 1e-9);
		Earnings.Row r = s.rows().get(0);
		assertEquals(60, r.matched());
		assertEquals(100, r.bought());
		assertEquals(60, r.sold());
	}

	@Test
	void costIsTheAverageOfWhatYouPaid() {
		Earnings.Summary s = Earnings.summarize(List.of(buy(1, "Coal", 100, 100), buy(2, "Coal", 100, 300), sell(3, "Coal", 100, 500)), 0);
		assertEquals(300, s.profit(), 1e-9);           // 500 - 100 x 2 average
		assertEquals(200, s.holdingCost(), 1e-9);
	}

	@Test
	void sellingWhatYouNeverBoughtHereIsOtherSalesNotProfit() {
		Earnings.Summary s = Earnings.summarize(List.of(buy(1, "Wheat", 10, 100), sell(2, "Wheat", 40, 800), sell(3, "Melon", 5, 50)), 0);
		assertEquals(100, s.profit(), 1e-9);           // 10 of the 40 were bought here: 200 - 100
		assertEquals(650, s.otherSales(), 1e-9);       // the other 30 wheat (600) and the melons (50)
		assertEquals(850, s.earned(), 1e-9);
		assertEquals(0, s.holdingItems());
		assertEquals(s.earned(), s.profit() + s.otherSales() + 100, 1e-9);   // every coin earned is accounted for
	}

	private static final java.util.Map<String, String> IDS = java.util.Map.of("precursor apparatus", "PRECURSOR_APPARATUS",
			"control switch", "CONTROL_SWITCH", "ftx 3070", "FTX_3070", "synthetic heart", "SYNTHETIC_HEART", "melon", "MELON");
	private static final Recipes.Recipe PRECURSOR = new Recipes.Recipe("PRECURSOR_APPARATUS", "Precursor Apparatus", 1, List.of(
			new Recipes.Ing("CONTROL_SWITCH", 1), new Recipes.Ing("FTX_3070", 1), new Recipes.Ing("SYNTHETIC_HEART", 2)), "");

	private static Earnings.Summary crafted(List<Earnings.Trade> trades) {
		return Earnings.summarize(trades, 0, n -> IDS.get(n.toLowerCase(java.util.Locale.ROOT)), id -> id.equals("PRECURSOR_APPARATUS") ? PRECURSOR : null);
	}

	private static Earnings.Row row(Earnings.Summary s, String name) {
		return s.rows().stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
	}

	@Test
	void sellingWhatYouCraftedFromBoughtIngredientsCountsTheirCost() {
		Earnings.Summary s = crafted(List.of(
				buy(1, "Control Switch", 80, 15_000_000), buy(2, "FTX 3070", 80, 8_000_000), buy(3, "Synthetic Heart", 160, 40_000_000),
				sell(10, "Precursor Apparatus", 80, 70_000_000)));
		Earnings.Row p = row(s, "Precursor Apparatus");
		assertEquals(80, p.crafted());
		assertEquals(63_000_000, p.craftCost(), 1e-6);
		assertEquals(7_000_000, p.profit(), 1e-6);
		assertTrue(p.hasProfit());
		assertEquals(0, p.otherSales(), 1e-6);
		assertEquals(0, row(s, "Control Switch").holding());              // used up by the craft
		assertEquals(160, row(s, "Synthetic Heart").usedInCrafts());
		assertEquals(0, s.holdingItems());
		assertEquals(7_000_000, s.profit(), 1e-6);
	}

	@Test
	void onlyAsManyCraftsAsEveryIngredientCovers() {
		Earnings.Summary s = crafted(List.of(
				buy(1, "Control Switch", 80, 8_000_000), buy(2, "FTX 3070", 30, 3_000_000), buy(3, "Synthetic Heart", 160, 16_000_000),
				sell(10, "Precursor Apparatus", 80, 80_000_000)));
		Earnings.Row p = row(s, "Precursor Apparatus");
		assertEquals(30, p.crafted());                                    // only 30 FTX 3070s were bought
		assertEquals(30 * 100_000 + 30 * 100_000 + 60 * 100_000, p.craftCost(), 1e-6);
		assertEquals(30_000_000 - p.craftCost(), p.profit(), 1e-6);
		assertEquals(50_000_000, p.otherSales(), 1e-6);                   // the other 50 have no tracked cost
		assertEquals(50, row(s, "Control Switch").holding());
	}

	@Test
	void noCraftWhenAnIngredientWasntBought() {
		Earnings.Summary s = crafted(List.of(buy(1, "Control Switch", 80, 8_000_000), buy(2, "FTX 3070", 80, 8_000_000),
				sell(10, "Precursor Apparatus", 80, 80_000_000)));
		Earnings.Row p = row(s, "Precursor Apparatus");
		assertEquals(0, p.crafted());
		assertFalse(p.hasProfit());
		assertEquals(80, row(s, "Control Switch").holding());
	}

	@Test
	void losingTradesShowALoss() {
		assertEquals(-200, Earnings.summarize(List.of(buy(1, "Gold", 10, 1000), sell(2, "Gold", 10, 800)), 0).profit(), 1e-9);
	}

	@Test
	void anEarlierBuyStillSetsTheCostOfALaterSale() {
		List<Earnings.Trade> all = List.of(buy(10, "Diamond", 100, 1000), sell(500, "Diamond", 100, 1500), buy(600, "Coal", 5, 50));
		Earnings.Summary s = Earnings.summarize(all, 400);   // the period starts after the buy
		assertEquals(500, s.profit(), 1e-9);
		assertEquals(50, s.spent());                   // only the coal was bought in the period
		assertEquals(1500, s.earned());
		assertEquals(2, s.trades());
		assertEquals(500, s.firstAt());
		assertEquals(2, s.rows().size());
		Earnings.Summary before = Earnings.summarize(all, 700);
		assertEquals(0, before.trades());
		assertTrue(before.rows().isEmpty());
	}

	@Test
	void namesMatchWhateverTheCapitals() {
		Earnings.Summary s = Earnings.summarize(List.of(buy(1, "Enchanted Diamond", 1, 100), sell(2, "ENCHANTED  diamond", 1, 150)), 0);
		assertEquals(1, s.rows().size());
		assertEquals(50, s.profit(), 1e-9);
	}

	@Test
	void perHourCountsFromTheFirstTrade() {
		Earnings.Summary s = Earnings.summarize(List.of(buy(1000, "Diamond", 10, 100), sell(2000, "Diamond", 10, 700)), 0);
		assertEquals(300, s.perHour(1000 + 2 * HOUR), 1e-6);
		assertTrue(Double.isNaN(s.perHour(1500)));
		assertTrue(Double.isNaN(Earnings.summarize(List.of(), 0).perHour(5000)));
	}

	@Test
	void periodsAndSorting() {
		long now = 20L * 86_400_000L;
		assertEquals(1000, Earnings.periodStart("session", now, 1000));
		assertEquals(0, Earnings.periodStart("all", now, 1000));
		assertEquals(now - 7L * 86_400_000L, Earnings.periodStart("week", now, 1000));
		long today = Earnings.periodStart("today", now, 1000);
		assertTrue(today <= now && now - today < 86_400_000L);
		assertEquals("Today", Earnings.periodLabel("today"));
		Earnings.Summary s = Earnings.summarize(List.of(buy(1, "A", 1, 10), sell(2, "A", 1, 20), buy(3, "B", 1, 10), sell(4, "B", 1, 90)), 0);
		assertEquals("B", Earnings.sorted(s.rows(), "profit").get(0).name());
		assertEquals("A", Earnings.sorted(s.rows(), "name").get(0).name());
	}

	// ---- chat -> list -> file ----

	@Test
	void tradesAreWrittenDownAndSurviveARestart(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("earnings.jsonl");
		Earnings.load(f);
		long v = Earnings.version();
		assertTrue(Earnings.onChat("[Bazaar] Bought 64x Diamond for 640 coins!", 100));
		assertTrue(Earnings.onChat("[Bazaar] Sold 64x Diamond for 700 coins!", 200));
		assertTrue(Earnings.version() > v);
		Files.writeString(f, "[123,\"B\",\"half a li", StandardOpenOption.APPEND);   // the game closed mid-write
		Earnings.load(f);
		assertEquals(2, Earnings.trades().size());
		assertEquals(60, Earnings.summarize(Earnings.trades(), 0).profit(), 1e-9);
		assertEquals(0, Earnings.summary("session", System.currentTimeMillis()).trades());   // a new session starts empty
		Earnings.clear();
		assertTrue(Earnings.trades().isEmpty());
		assertFalse(Files.exists(f));
	}
}
