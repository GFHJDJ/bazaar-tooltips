package com.unc.bazaartips;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderTrackerTest {
	private final Map<String, BazaarPrices.Price> prices = new HashMap<>();

	@BeforeEach
	void reset() {
		OrderTracker.resetForTests();
		prices.clear();
	}

	private static OrderTracker.Order buy(String name, String id, long amount, double price) {
		return new OrderTracker.Order(OrderTracker.Type.BUY, name, id, amount, 0, false, price, 0, 1);
	}

	private static OrderTracker.Order sell(String name, String id, long amount, double price) {
		return new OrderTracker.Order(OrderTracker.Type.SELL, name, id, amount, 0, false, price, 0, 1);
	}

	// ---- reading the Manage Orders screen ----

	@Test
	void readsAnOrderOffTheScreen() {
		OrderTracker.Order o = OrderTracker.parse("§a§lBUY §aEnchanted Diamond", List.of(
				"§8Worth 80k coins", "", "§7Order amount: §a64§7x", "§7Filled: §632§7/64 §a§l50%", "",
				"§7Price per unit: §61,250.5 coins", "", "§7By: §b[MVP§c+§b] Unc", "§eYou have §a32 §eitems to claim!"), "Unc", 5);
		assertNotNull(o);
		assertEquals(OrderTracker.Type.BUY, o.type());
		assertEquals("Enchanted Diamond", o.name());
		assertEquals(64, o.amount());
		assertEquals(32, o.filled());
		assertFalse(o.full());
		assertEquals(1250.5, o.price());
		assertEquals(32, o.claimable());
	}

	@Test
	void aFullyFilledOrderIsFull() {
		OrderTracker.Order o = OrderTracker.parse("SELL Diamond", List.of("Offer amount: 1,280x", "Filled: 1.2k/1.2k 100%!",
				"Price per unit: 9.8 coins"), "Unc", 5);
		assertTrue(o.full());
		assertEquals(1280, o.filled());
	}

	@Test
	void aCoopMembersOrderIsLeftOut() {
		List<String> lore = List.of("Order amount: 64x", "Price per unit: 10.0 coins", "By: [VIP] SomeoneElse");
		assertNull(OrderTracker.parse("BUY Diamond", lore, "Unc", 5));
		assertNotNull(OrderTracker.parse("BUY Diamond", lore, "someoneelse", 5));
	}

	@Test
	void thingsThatArentOrdersAreIgnored() {
		assertNull(OrderTracker.parse("Go Back", List.of("To Bazaar"), "Unc", 5));
		assertNull(OrderTracker.parse("BUY Diamond", List.of("no numbers here"), "Unc", 5));
		assertTrue(OrderTracker.isOrdersTitle("§8Your Bazaar Orders"));
		assertFalse(OrderTracker.isOrdersTitle("Bazaar ➜ Mining"));
	}

	// ---- checking against the order book ----

	@Test
	void topTiedOutbidAndFilled() {
		prices.put("DIAMOND", new BazaarPrices.Price(12, 10, 1000, 1000,
				List.of(new BazaarPrices.Level(12, 100, 2)), List.of(new BazaarPrices.Level(10, 100, 1))));
		assertEquals(OrderTracker.Status.TOP, OrderTracker.check(buy("Diamond", "DIAMOND", 64, 10), prices::get).status());
		assertEquals(OrderTracker.Status.OUTBID, OrderTracker.check(buy("Diamond", "DIAMOND", 64, 9.9), prices::get).status());
		assertEquals(OrderTracker.Status.TIED, OrderTracker.check(sell("Diamond", "DIAMOND", 64, 12), prices::get).status());
		assertEquals(OrderTracker.Status.OUTBID, OrderTracker.check(sell("Diamond", "DIAMOND", 64, 12.1), prices::get).status());
		assertEquals(OrderTracker.Status.UNKNOWN, OrderTracker.check(buy("Mystery", null, 64, 10), prices::get).status());
		OrderTracker.Order full = new OrderTracker.Order(OrderTracker.Type.BUY, "Diamond", "DIAMOND", 64, 64, true, 1, 64, 1);
		assertEquals(OrderTracker.Status.FILLED, OrderTracker.check(full, prices::get).status());
	}

	@Test
	void anOutbidOnlyAlertsOnceUntilItsBackOnTop() {
		OrderTracker.replace(List.of(buy("Diamond", "DIAMOND", 64, 10)));
		prices.put("DIAMOND", TestPrices.busy(12, 10));
		assertTrue(OrderTracker.newlyOutbid(prices::get).isEmpty());
		prices.put("DIAMOND", TestPrices.busy(12, 10.5));
		assertEquals(1, OrderTracker.newlyOutbid(prices::get).size());
		assertTrue(OrderTracker.newlyOutbid(prices::get).isEmpty());       // still outbid: no second message
		prices.put("DIAMOND", TestPrices.busy(12, 10));
		assertTrue(OrderTracker.newlyOutbid(prices::get).isEmpty());
		prices.put("DIAMOND", TestPrices.busy(12, 11));
		assertEquals(1, OrderTracker.newlyOutbid(prices::get).size());     // outbid again: a new message
	}

	@Test
	void twoIdenticalOrdersEachGetTheirOwnAlert() {
		OrderTracker.replace(List.of(buy("Diamond", "DIAMOND", 64, 10), buy("Diamond", "DIAMOND", 64, 10)));
		prices.put("DIAMOND", TestPrices.busy(12, 10.5));
		assertEquals(2, OrderTracker.newlyOutbid(prices::get).size());
		assertTrue(OrderTracker.newlyOutbid(prices::get).isEmpty());
	}

	// ---- Hypixel's chat messages ----

	@Test
	void aFilledMessageMarksTheOrderFilledInsteadOfOutbid() {
		OrderTracker.replace(List.of(buy("Enchanted Diamond", "ENCHANTED_DIAMOND", 1280, 10), sell("Enchanted Diamond", "ENCHANTED_DIAMOND", 1280, 12)));
		prices.put("ENCHANTED_DIAMOND", TestPrices.busy(12, 10.5));           // the book has moved past the buy order
		long v = OrderTracker.version();
		assertTrue(OrderTracker.onChat("§6[Bazaar] §7Your §aBuy Order §7for §a1,280§7x §fEnchanted Diamond §7was filled!"));
		assertTrue(OrderTracker.version() > v);
		List<OrderTracker.Check> checks = OrderTracker.checkAll(prices::get);
		assertEquals(OrderTracker.Status.FILLED, checks.get(0).status());
		assertEquals(OrderTracker.Type.SELL, checks.get(1).order().type());
		assertNotEquals(OrderTracker.Status.FILLED, checks.get(1).status());   // the sell offer is untouched
		assertTrue(OrderTracker.newlyOutbid(prices::get).isEmpty());           // and no false "outbid" message
	}

	@Test
	void claimingAFilledOrderRemovesIt() {
		OrderTracker.replace(List.of(buy("Diamond", "DIAMOND", 64, 10), sell("Coal", "COAL", 640, 5)));
		assertFalse(OrderTracker.onChat("[Bazaar] Claimed 64x Diamond worth 640 coins bought for 10.0 each!"));   // not filled yet
		assertTrue(OrderTracker.onChat("[Bazaar] Your Buy Order for 64x Diamond was filled!"));
		assertTrue(OrderTracker.onChat("[Bazaar] Claimed 64x Diamond worth 640 coins bought for 10.0 each!"));
		assertEquals(1, OrderTracker.orders().size());
		assertTrue(OrderTracker.onChat("[Bazaar] Your Sell Offer for 640x Coal was filled!"));
		assertTrue(OrderTracker.onChat("[Bazaar] Claimed 3,160 coins from selling 640x Coal at 5.0 each!"));
		assertTrue(OrderTracker.orders().isEmpty());
	}

	@Test
	void otherChatChangesNothing() {
		OrderTracker.replace(List.of(buy("Diamond", "DIAMOND", 64, 10)));
		long v = OrderTracker.version();
		assertFalse(OrderTracker.onChat("[Bazaar] Your Buy Order for 64x Emerald was filled!"));    // not one of yours
		assertFalse(OrderTracker.onChat("[Bazaar] Your Buy Order for 32x Diamond was filled!"));    // different size
		assertFalse(OrderTracker.onChat("Unc: [Bazaar] Your Buy Order for 64x Diamond was filled!")); // a player typing it
		assertFalse(OrderTracker.onChat("Welcome to Hypixel SkyBlock!"));
		assertEquals(v, OrderTracker.version());
		assertFalse(OrderTracker.orders().get(0).full());
	}
}
