package com.unc.bazaartips;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoflDataTest {
	@Test
	void median() {
		assertEquals(3, CoflData.median(List.of(5.0, 1.0, 3.0)));
		assertEquals(2.5, CoflData.median(List.of(4.0, 1.0, 2.0, 3.0)));
	}

	@Test
	void historyNeedsFivePoints() {
		assertNull(CoflData.summarize(JsonParser.parseString("""
				[{"timestamp":"2026-01-01T00:00:00Z","buy":10,"sell":8}]"""), 0));
		assertNull(CoflData.summarize(JsonParser.parseString("{}"), 0));
	}

	@Test
	void historySummary() {
		long now = CoflData.parseTime("2026-06-10T00:00:00Z");
		CoflData.Hist h = CoflData.summarize(JsonParser.parseString("""
				[{"timestamp":"2025-01-01T00:00:00","buy":110,"sell":90},
				 {"timestamp":"2026-06-01T00:00:00Z","buy":22,"sell":18},
				 {"timestamp":"2026-06-02T00:00:00Z","buy":33,"sell":27},
				 {"timestamp":"2026-06-03T00:00:00Z","buy":44,"sell":36},
				 {"Timestamp":"2026-06-04T00:00:00Z","Buy":55,"Sell":45},
				 {"timestamp":"2026-06-05T00:00:00Z","buy":0,"sell":45}]"""), now);
		assertEquals("ok", h.status());
		assertEquals(100, h.allHi());
		assertEquals(20, h.allLo());
		assertEquals(35, h.med30());                 // the last 30 days: 20, 30, 40, 50
		assertEquals(50, h.hi30());
		assertEquals(20, h.lo30());
		assertEquals(CoflData.parseTime("2025-01-01T00:00:00Z"), h.since());
	}

	@Test
	void movement() {
		var m = CoflData.movement(JsonParser.parseString("""
				[{"itemId":"A","previousPrice":100,"currentPrice":110}]"""), null);
		assertEquals(10, m.get("A").b(), 1e-9);
		assertTrue(Double.isNaN(m.get("A").s()));
		assertNull(CoflData.movement(null, null));
	}
}
