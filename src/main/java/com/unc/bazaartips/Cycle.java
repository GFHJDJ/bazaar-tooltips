package com.unc.bazaartips;

import java.util.List;

/** Stepping through a button's options: dir = +1 for the next one (left click), -1 for the one before (right click). */
final class Cycle {
	private Cycle() {}

	/** The option dir steps away from current, wrapping round. Not in the list: the first (forward) or last (back). */
	static <T> T step(List<T> options, T current, int dir) {
		int n = options.size(), i = options.indexOf(current);
		if (i < 0) return options.get(dir >= 0 ? 0 : n - 1);
		return options.get(Math.floorMod(i + dir, n));
	}

	static <E extends Enum<E>> E step(E current, int dir) {
		E[] all = current.getDeclaringClass().getEnumConstants();
		return all[Math.floorMod(current.ordinal() + dir, all.length)];
	}

	/** A whole number from min to max, wrapping round. */
	static int step(int current, int min, int max, int dir) {
		return min + Math.floorMod(current - min + dir, max - min + 1);
	}

	/** The next or previous of a list of numbers (e.g. the tax presets); not in the list: the first or last. */
	static double step(double[] options, double current, int dir) {
		for (int i = 0; i < options.length; i++) if (options[i] == current) return options[Math.floorMod(i + dir, options.length)];
		return options[dir >= 0 ? 0 : options.length - 1];
	}
}
