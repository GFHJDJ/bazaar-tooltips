package com.unc.bazaartips;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** The gear: the mod's settings. One row per setting: a label on the left, a button on the right. */
public class SettingsScreen extends BaseScreen {
	/** A setting: its name, what its button says now, what it means, and what a click does. */
	private record Item(String label, java.util.function.Supplier<String> value, String explanation, Runnable action) {}

	private final List<Item> items = new ArrayList<>();
	private final List<Button> buttons = new ArrayList<>();
	private int top, rowGap, buttonW, explainLines;

	public SettingsScreen(Screen parent) {
		super("Settings", parent);
		items.add(new Item("Open-screen key", () -> keyName(),
				"The key that opens the bazaar screen. Click to open Minecraft's Key Binds and change it: it's under Bazaar Tooltips "
						+ "(also in Options > Controls > Key Binds).",
				() -> minecraft.gui.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this, minecraft.options))));
	}

	static String keyName() {
		KeyMapping k = BazaarTooltipsClient.openKey;
		return k == null ? "B" : k.getTranslatedKeyMessage().getString();
	}

	@Override
	protected void init() {
		layoutPanel();
		button("< Back", left, 2, 50, b -> onClose());
		explainLines = height < 240 ? 2 : 3;
		top = 30;
		rowGap = 24;
		buttonW = Math.min(140, tableWidth / 2);
		buttons.clear();
		for (Item it : items) {
			int y = top + buttons.size() * rowGap;
			buttons.add(button(it.value().get(), left + tableWidth - buttonW, y, buttonW, b -> {
				it.action().run();
				Settings.save();
				if (minecraft.gui.screen() == this) relabel(b, it.value().get(), buttonW);
			}));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		explain.clear();
		explain.add(left, 2, left + 50, 22, "Back to the list.");
		drawPanel(graphics);
		graphics.centeredText(font, GEAR + " Settings", width / 2, 8, Colors.GOLD);
		for (int i = 0; i < items.size(); i++) {
			int y = top + i * rowGap;
			graphics.text(font, fit(items.get(i).label(), tableWidth - buttonW - 8), left + 2, y + 6, Colors.WHITE);
			explain.add(left, y, left + tableWidth, y + 20, items.get(i).label() + ": " + items.get(i).explanation());
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		drawExplainStrip(graphics, mouseX, mouseY, height - 6 - explainLines * (font.lineHeight + 1), explainLines,
				"Hover a setting to see what it does. More settings coming.");
	}
}
