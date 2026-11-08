package com.unc.bazaartips;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * "Hover anything to see what it means": screens register a box + explanation for each
 * label and number as they draw, then show the hovered one's explanation in a strip at the bottom.
 */
final class Explain {
	private record Box(int x1, int y1, int x2, int y2, String text) {}

	private final List<Box> boxes = new ArrayList<>();

	/** Call at the start of each frame. */
	void clear() {
		boxes.clear();
	}

	void add(int x1, int y1, int x2, int y2, String text) {
		boxes.add(new Box(x1, y1, x2, y2, text));
	}

	/** The explanation under the mouse, or null. */
	String at(int mouseX, int mouseY) {
		for (Box b : boxes) {
			if (mouseX >= b.x1 && mouseX < b.x2 && mouseY >= b.y1 && mouseY < b.y2) return b.text;
		}
		return null;
	}

	/** Splits text into lines no wider than maxWidth. */
	static List<String> wrap(Font font, String text, int maxWidth) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String tryLine = line.isEmpty() ? word : line + " " + word;
			if (font.width(tryLine) <= maxWidth || line.isEmpty()) {
				line.setLength(0);
				line.append(tryLine);
			} else {
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
		}
		if (!line.isEmpty()) lines.add(line.toString());
		return lines;
	}

	/** Draws up to maxLines of wrapped text, centered, starting at y. */
	static void draw(GuiGraphicsExtractor graphics, Font font, String text, int centerX, int y, int maxWidth,
			int maxLines, int color) {
		List<String> lines = wrap(font, text, maxWidth);
		for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
			String s = lines.get(i);
			graphics.text(font, s, centerX - font.width(s) / 2, y + i * (font.lineHeight + 1), color);
		}
	}
}
