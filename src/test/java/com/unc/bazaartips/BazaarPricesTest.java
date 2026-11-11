package com.unc.bazaartips;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BazaarPricesTest {
	private static final String JSON = """
			{"success":true,"products":{"DIAMOND":{
			  "buy_summary":[{"amount":100,"pricePerUnit":10.5,"orders":2},{"amount":50,"pricePerUnit":11.0,"orders":1}],
			  "sell_summary":[{"amount":200,"pricePerUnit":9.5,"orders":3}],
			  "quick_status":{"buyPrice":10.8,"sellPrice":9.4,"buyMovingWeek":70000,"sellMovingWeek":50000,
			                  "buyOrders":12,"sellOrders":9,"buyVolume":1500,"sellVolume":2500}}}}""";

	@Test
	void hypixelsBackwardsNamesAreReadTheRightWayRound() {
		Map<String, BazaarPrices.Price> m = BazaarPrices.parse(JSON);
		BazaarPrices.Price p = m.get("DIAMOND");
		assertNotNull(p);
		assertEquals(10.5, p.instaBuy());            // buy_summary = sell offers = what you pay
		assertEquals(9.5, p.instaSell());            // sell_summary = buy orders = what you get
		assertEquals(2, p.sellOffers().size());
		assertEquals(3, p.buyOrders().get(0).orders());
		assertEquals(70000, p.buyMovingWeek());
		assertEquals(50000, p.sellMovingWeek());
		assertEquals(10.8, p.quickBuy());
		assertEquals(12, p.sellOfferCount());
		assertEquals(9, p.buyOrderCount());
	}

	@Test
	void anEmptySideIsNaNNotZero() {
		BazaarPrices.Price p = BazaarPrices.parse("""
				{"success":true,"products":{"X":{"buy_summary":[],"sell_summary":[],"quick_status":{}}}}""").get("X");
		assertTrue(Double.isNaN(p.instaBuy()));
		assertTrue(Double.isNaN(p.instaSell()));
	}

	@Test
	void aFailedResponseGivesNothing() {
		assertTrue(BazaarPrices.parse("{\"success\":false}").isEmpty());
		assertTrue(BazaarPrices.parse("{\"success\":true}").isEmpty());
	}
}
