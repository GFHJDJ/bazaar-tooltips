package com.unc.bazaartips;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProductSummaryTest {
	private static String value(List<ProductSummary.Line> lines, String label) {
		for (ProductSummary.Line l : lines) if (l.text().equals(label)) return l.value();
		return null;
	}

	/** The panel in /bz must show the same insta-buy / insta-sell as the item tooltip: the top of the order book. */
	@Test
	void thePanelShowsTheTopOfTheBookLikeTheTooltip() {
		OrderTracker.resetForTests();
		BazaarPrices.Price p = TestPrices.of(1300, 1250, 1422.7, 1101.3, 50, 50);   // averaged prices differ from the top
		BazaarPrices.setForTests(Map.of("DIAMOND", p));
		List<ProductSummary.Line> lines = ProductSummary.build("DIAMOND");
		assertEquals(Format.price(p.instaBuy()), value(lines, "Insta-buy"));
		assertEquals(Format.price(p.instaSell()), value(lines, "Insta-sell"));
		assertEquals(Format.price(1250.1), value(lines, "Buy order at"));
		assertEquals(Format.price(1299.9), value(lines, "Sell offer at"));
	}

	@Test
	void yourOrdersOnTheItemAreListed() {
		BazaarPrices.setForTests(Map.of("DIAMOND", TestPrices.busy(12, 10.5)));
		OrderTracker.resetForTests();
		OrderTracker.replace(List.of(new OrderTracker.Order(OrderTracker.Type.BUY, "Diamond", "DIAMOND", 64, 0, false, 10, 0, 1)));
		List<ProductSummary.Line> lines = ProductSummary.build("DIAMOND");
		assertTrue(lines.stream().anyMatch(l -> l.text().equals("Your orders")));
		assertTrue(lines.stream().anyMatch(l -> l.value().startsWith("Outbid")));
		OrderTracker.resetForTests();
	}
}
