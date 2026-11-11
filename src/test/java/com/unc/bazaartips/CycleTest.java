package com.unc.bazaartips;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CycleTest {
	@Test
	void forwardAndBackWrapRound() {
		List<String> l = List.of("a", "b", "c");
		assertEquals("b", Cycle.step(l, "a", 1));
		assertEquals("a", Cycle.step(l, "c", 1));
		assertEquals("c", Cycle.step(l, "a", -1));
		assertEquals("b", Cycle.step(l, "c", -1));
		assertEquals("a", Cycle.step(l, "zzz", 1));       // unknown: forward starts at the first
		assertEquals("c", Cycle.step(l, "zzz", -1));      // and back at the last
	}

	@Test
	void backUndoesForward() {
		List<String> l = List.of("a", "b", "c", "d");
		for (String s : l) assertEquals(s, Cycle.step(l, Cycle.step(l, s, 1), -1));
		for (Settings.Compactor c : Settings.Compactor.values()) assertEquals(c, Cycle.step(Cycle.step(c, 1), -1));
		for (int h = 0; h <= 10; h++) assertEquals(h, Cycle.step(Cycle.step(h, 0, 10, 1), 0, 10, -1));
	}

	@Test
	void numbers() {
		assertEquals(0, Cycle.step(10, 0, 10, 1));          // HotM: 10 -> any
		assertEquals(10, Cycle.step(0, 0, 10, -1));         // any -> 10
		assertEquals(1, Cycle.step(12, 1, 12, 1));          // minion tier XII -> I
		assertEquals(12, Cycle.step(1, 1, 12, -1));
		assertEquals(Settings.Compactor.HIDE, Cycle.step(Settings.Compactor.ANY, -1));
	}

	@Test
	void taxPresets() {
		double[] p = {1.25, 1.125, 1.0};
		assertEquals(1.125, Cycle.step(p, 1.25, 1));
		assertEquals(1.0, Cycle.step(p, 1.25, -1));
		assertEquals(1.25, Cycle.step(p, 3.0, 1));          // a custom tax goes to the first preset
		assertEquals(1.0, Cycle.step(p, 3.0, -1));
	}
}
