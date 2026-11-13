package com.unc.bazaartips;

/** Screen colors. ARGB: the leading FF means fully visible (text with alpha 00 is invisible). */
public final class Colors {
	private Colors() {}

	public static final int PANEL = 0xC0000000;
	/** A nearly solid box, for panels drawn over busy things (the potion effects beside your inventory). */
	public static final int PANEL_SOLID = 0xF0101010;
	public static final int ROW_STRIPE = 0x14FFFFFF;
	public static final int ROW_HOVER = 0x40FFFFFF;
	public static final int DIVIDER = 0x40FFFFFF;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GRAY = 0xFFAAAAAA;
	public static final int DARK_GRAY = 0xFF777777;
	public static final int GOLD = 0xFFFFAA00;
	public static final int YELLOW = 0xFFFFFF55;
	public static final int GREEN = 0xFF55FF55;
	public static final int RED = 0xFFFF5555;
	public static final int ORANGE = 0xFFFF9944;
	public static final int AQUA = 0xFF55FFFF;

	public static int profit(double v) {
		return Double.isNaN(v) ? GRAY : v >= 0 ? GREEN : RED;
	}
}
