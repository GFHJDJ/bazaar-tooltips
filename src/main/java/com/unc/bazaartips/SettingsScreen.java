package com.unc.bazaartips;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The gear: the mod's settings, in sections. One row per setting: a label on the left, a button (or a number box)
 * on the right. Scrolls when it doesn't fit.
 */
public class SettingsScreen extends BaseScreen {
	/** A row: a section heading (value == null), a button, or a number box (onType != null). */
	private record Item(String label, Supplier<String> value, String explanation, Runnable action, Runnable back, Consumer<String> onType) {
		static Item heading(String label, String explanation) {
			return new Item(label, null, explanation, null, null, null);
		}

		/** A button that does one thing (opens a screen): right click does nothing. */
		static Item button(String label, Supplier<String> value, String explanation, Runnable action) {
			return new Item(label, value, explanation, action, null, null);
		}

		/** A button that steps through options: step(+1) on a left click, step(-1) on a right click. */
		static Item cycle(String label, Supplier<String> value, String explanation, java.util.function.IntConsumer step) {
			return new Item(label, value, explanation, () -> step.accept(1), () -> step.accept(-1), null);
		}

		static Item box(String label, Supplier<String> value, String explanation, Consumer<String> onType) {
			return new Item(label, value, explanation, null, null, onType);
		}

		boolean isHeading() {
			return value == null;
		}
	}

	private final List<Item> items = new ArrayList<>();
	private final List<Object> widgets = new ArrayList<>();     // Button, EditBox or null (heading) per item
	private int top, rowGap, buttonW, explainLines, scroll, bottom;

	public SettingsScreen(Screen parent) {
		super("Settings", parent);
		items.add(Item.heading("General", "How the bazaar screen opens and which tabs it shows."));
		items.add(Item.button("Open-screen key", SettingsScreen::keyName,
				"The key that opens the bazaar screen. Click to open Minecraft's Key Binds and change it: it's under Bazaar Tooltips "
						+ "(also in Options > Controls > Key Binds).",
				() -> minecraft.gui.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this, minecraft.options))));
		items.add(Item.cycle("Key opens", () -> Settings.startTab.equals("last") ? "Last tab used" : tabLabel(Settings.startTab),
				"Which tab the key opens: the one you used last, or always the same one. Click to step through the tabs (right click goes back).",
				d -> {
					List<String> keys = new ArrayList<>(List.of("last"));
					for (String[] t : tabs()) keys.add(t[0]);
					Settings.startTab = Cycle.step(keys, Settings.startTab, d);
				}));
		items.add(Item.button("Tabs shown", () -> (tabs().length - hiddenCount()) + " of " + tabs().length,
				"Hide tabs you don't use so the rest fit on one row. Click to pick.",
				() -> minecraft.gui.setScreen(new TabsPickScreen(this))));
		items.add(Item.heading("Item tooltips", "The lines added when you hover a SkyBlock item anywhere."));
		items.add(Item.cycle("Price lines", () -> onOff(Settings.tooltips),
				"Bazaar insta-buy / insta-sell, 7-day volume and what an NPC pays, added to item tooltips. Off = no extra lines at all.",
				d -> Settings.tooltips = !Settings.tooltips));
		items.add(Item.cycle("Craft lines", () -> onOff(Settings.tooltipCrafts),
				"The craft cost and profit lines under the prices (only for items with a recipe). Off keeps tooltips short.",
				d -> Settings.tooltipCrafts = !Settings.tooltipCrafts));
		items.add(Item.heading("Bazaar", "Things shown while you're in /bz."));
		items.add(Item.cycle("Panel in the bazaar", () -> onOff(Settings.bazaarOverlay),
				"When you open an item in /bz, a panel beside it shows its prices, the flip and its score, crafting it, selling to an NPC, "
						+ "and your orders on it. (It needs room beside the chest: a smaller GUI scale helps.)",
				d -> Settings.bazaarOverlay = !Settings.bazaarOverlay));
		items.add(Item.heading("Your orders", "Read from Manage Orders (Your Bazaar Orders) whenever you open it."));
		items.add(Item.cycle("Orders box on screen", () -> onOff(Settings.orderHud),
				"A small box on your screen with your bazaar orders and whether each is still on top (red = outbid).",
				d -> Settings.orderHud = !Settings.orderHud));
		items.add(Item.cycle("Orders box position", SettingsScreen::cornerName,
				"Where the orders box sits on your screen. Click for the next spot (right click goes back).",
				d -> {
					List<String> keys = new ArrayList<>();
					for (String[] c : Overlays.CORNERS) keys.add(c[0]);
					Settings.orderHudCorner = Cycle.step(keys, Settings.orderHudCorner, d);
				}));
		items.add(Item.cycle("Outbid chat alerts", () -> onOff(Settings.orderAlerts),
				"A chat message when one of your bazaar orders is outbid or undercut (also on the Orders tab).",
				d -> Settings.orderAlerts = !Settings.orderAlerts));
		items.add(Item.heading("Flip alerts", "Chat messages when a flip reaches your alert score."));
		items.add(Item.cycle("Flip chat alerts", () -> onOff(Settings.flipAlerts),
				"A chat message (with a /bz link) when a flip reaches your alert score. The first check only says how many there are; after "
						+ "that you hear about new ones, at most 3 per price update and each item at most once an hour. Risky and low-volume "
						+ "flips never alert.",
				d -> Settings.flipAlerts = !Settings.flipAlerts));
		items.add(Item.box("Alert at score", () -> Integer.toString(Settings.flipAlertScore),
				"The flip score (0-100, the Flipper tab's Score) a flip needs to alert. 75+ is Great, 60+ Good. Higher = fewer, better alerts.",
				v -> {
					try {
						int n = Integer.parseInt(v.trim());
						if (n >= 1 && n <= 100) Settings.flipAlertScore = n;
					} catch (NumberFormatException ignored) {
						// keep the last good number while typing
					}
				}));
	}

	private static int hiddenCount() {
		int n = 0;
		for (String[] t : tabs()) if (Settings.hiddenTabs.contains(t[0])) n++;
		return n;
	}

	private static String onOff(boolean v) {
		return v ? "On" : "Off";
	}

	private static String cornerName() {
		for (String[] c : Overlays.CORNERS) if (c[0].equals(Settings.orderHudCorner)) return c[1];
		return "Top left";
	}

	static String keyName() {
		KeyMapping k = BazaarTooltipsClient.openKey;
		return k == null ? "B" : k.getTranslatedKeyMessage().getString();
	}

	private int rowY(int i) {
		int y = top;
		for (int k = 0; k < i; k++) y += items.get(k).isHeading() ? 16 : rowGap;
		if (items.get(i).isHeading() && i > 0) y += 4;
		return y - scroll;
	}

	private int contentHeight() {
		return rowY(items.size() - 1) + scroll + rowGap - top;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight() - (bottom - top));
	}

	@Override
	protected void init() {
		layoutPanel();
		button("< Back", left, 2, 50, b -> onClose());
		explainLines = height < 240 ? 2 : 3;
		top = 28;
		rowGap = 22;
		bottom = height - 8 - explainLines * (font.lineHeight + 1);
		buttonW = Math.min(140, tableWidth / 2);
		scroll = Math.min(scroll, maxScroll());
		widgets.clear();
		for (Item it : items) {
			if (it.isHeading()) {
				widgets.add(null);
			} else if (it.onType() != null) {
				EditBox box = addRenderableWidget(new EditBox(font, left + tableWidth - buttonW, 0, buttonW, 16, Component.literal(it.label())));
				box.setValue(it.value().get());
				box.setResponder(v -> { it.onType().accept(v); Settings.save(); });
				widgets.add(box);
			} else {
				Button btn = button(it.value().get(), left + tableWidth - buttonW, 0, buttonW, b -> {
					it.action().run();
					Settings.save();
					if (minecraft.gui.screen() == this) relabel(b, it.value().get(), buttonW);
				});
				if (it.back() != null) onRightClick(btn, () -> {
					it.back().run();
					Settings.save();
					relabel(btn, it.value().get(), buttonW);
				});
				widgets.add(btn);
			}
		}
		place();
	}

	/** Puts each button/box at its scrolled row; hides the ones outside the visible area. */
	private void place() {
		for (int i = 0; i < items.size(); i++) {
			Object w = widgets.get(i);
			int y = rowY(i);
			boolean vis = y >= top && y + 20 <= bottom;
			if (w instanceof Button b) {
				b.setY(y);
				b.visible = vis;
			} else if (w instanceof EditBox e) {
				e.setY(y + 2);
				e.visible = vis;
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll + (scrollY > 0 ? -rowGap : scrollY < 0 ? rowGap : 0)));
		place();
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		place();
		explain.clear();
		explain.add(left, 2, left + 50, 22, "Back to the list.");
		drawPanel(graphics);
		graphics.centeredText(font, GEAR + " Settings", width / 2, 8, Colors.GOLD);
		for (int i = 0; i < items.size(); i++) {
			Item it = items.get(i);
			int y = rowY(i);
			if (y < top || y + (it.isHeading() ? 12 : 20) > bottom) continue;
			if (it.isHeading()) {
				graphics.text(font, it.label(), left + 2, y + 2, Colors.GOLD);
				graphics.fill(left, y + 12, left + tableWidth, y + 13, Colors.DIVIDER);
				explain.add(left, y, left + tableWidth, y + 13, it.label() + ": " + it.explanation());
			} else {
				graphics.text(font, fit(it.label(), tableWidth - buttonW - 14), left + 8, y + 6, Colors.WHITE);
				explain.add(left, y, left + tableWidth, y + 20, it.label() + ": " + it.explanation());
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		drawExplainStrip(graphics, mouseX, mouseY, height - 6 - explainLines * (font.lineHeight + 1), explainLines,
				maxScroll() > 0 ? "Hover a setting to see what it does. Scroll for more." : "Hover a setting to see what it does.");
	}
}
