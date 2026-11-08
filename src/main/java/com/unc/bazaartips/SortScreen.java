package com.unc.bazaartips;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Pick how a list is sorted: the website's sort options for that tab. */
public class SortScreen extends BaseScreen {
	private final String heading;
	private final List<CraftEngine.SortOption> options;
	private final Supplier<String> current;
	private final Consumer<String> choose;
	private int buttonsTop;
	private int buttonWidth;
	private int explainLines;

	/** The Craft tabs' 12 sort options. */
	public SortScreen(Screen parent) {
		this(parent, "Sort crafts by", CraftEngine.SORTS, () -> Settings.sort, k -> Settings.sort = k);
	}

	public SortScreen(Screen parent, String heading, List<CraftEngine.SortOption> options, Supplier<String> current, Consumer<String> choose) {
		super(heading, parent);
		this.heading = heading;
		this.options = options;
		this.current = current;
		this.choose = choose;
	}

	@Override
	protected void init() {
		layoutPanel();
		buttonsTop = 26;
		int gap = 4;
		buttonWidth = (tableWidth - gap) / 2;
		int rows = (options.size() + 1) / 2;
		int rowGap = rowGap(rows);
		for (int i = 0; i < options.size(); i++) {
			CraftEngine.SortOption s = options.get(i);
			String mark = s.key().equals(current.get()) ? "> " : "";
			String label = font.width(mark + s.label()) <= buttonWidth - 8 ? s.label() : s.shortLabel(); // short name when the full one won't fit
			button(mark + label, left + (i % 2) * (buttonWidth + gap), buttonsTop + (i / 2) * rowGap, buttonWidth, b -> {
				choose.accept(s.key());
				Settings.save();
				onClose();
			});
		}
		button("< Back", left, 2, 50, b -> onClose());
		explainLines = height < 240 ? 2 : 3;
	}

	/** Spacing between option rows: as roomy as the window allows (20-24px), leaving room for the explanation strip. */
	private int rowGap(int rows) {
		int lines = height < 240 ? 2 : 3;
		int room = height - buttonsTop - 10 - lines * (font.lineHeight + 1);
		return Math.max(20, Math.min(24, room / rows));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		explain.clear();
		int rowGap = rowGap((options.size() + 1) / 2);
		for (int i = 0; i < options.size(); i++) {
			int x = left + (i % 2) * (buttonWidth + 4), y = buttonsTop + (i / 2) * rowGap;
			explain.add(x, y, x + buttonWidth, y + 20, options.get(i).label() + ": " + options.get(i).explanation());
		}
		drawPanel(graphics);
		graphics.centeredText(font, heading, width / 2, 8, Colors.GOLD);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		drawExplainStrip(graphics, mouseX, mouseY, height - 6 - explainLines * (font.lineHeight + 1), explainLines,
				"Hover an option to see what it does. '>' marks the current one.");
	}
}
