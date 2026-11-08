package com.unc.bazaartips;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** What every screen in the mod shares: the dark panel, the Rounded toggle, and "hover anything to see what it means". */
abstract class BaseScreen extends Screen {
	static final int ROUNDED_BUTTON_WIDTH = 74;
	static final String ROUNDED_EXPLANATION = "Rounded: On shows short numbers like 162.8k and 2.13M. "
			+ "Off shows every digit, like 162,844.8. Applies to these screens and item tooltips.";

	protected final Screen parent;
	protected final Explain explain = new Explain();
	protected int left;
	protected int tableWidth;

	protected BaseScreen(String title, Screen parent) {
		super(Component.literal(title));
		this.parent = parent;
	}

	/** Works out the panel's size; call first in init(). */
	protected void layoutPanel() {
		tableWidth = Math.min(width - 20, 480);
		left = (width - tableWidth) / 2;
	}

	// ---- the tabs along the top of the list screens (the website's tabs) ----

	private static final String[][] TABS = {
			{"crafts", "Crafts", "Crafts: buy the ingredients on the bazaar, craft, sell the result (the site's two Craft tabs)."},
			{"flips", "Flipper", "Flipper: buy with a buy order, sell with a sell offer, keep the gap (the site's Flipper tab)."},
			{"npc", "NPC", "NPC flips: buy on the Bazaar and sell to an NPC, or buy from an NPC shop and sell on the Bazaar or AH."},
			{"forge", "Forge", "Forge: buy the materials, forge it in the Dwarven Mines, sell the result."},
			{"prices", "Prices", "Prices: every bazaar item's insta-buy and insta-sell price, spread, margin and volume, with its order book."},
			{"books", "Books", "Books: combine two level-N enchanted books into one level N+1 in an anvil and sell it."},
			{"minions", "Minions", "Minions: what each minion costs to build, tier by tier, by insta-buy or buy order."},
			{"profit", "Profit/hr", "Profit / hr: coins per hour from each minion's drops, with fuel, upgrades and hoppers."},
			{"essence", "Essence", "Essence: buy the cheapest AH listing, salvage it (or sacrifice it), sell the essence."},
			{"cur", "Currency", "Currencies: what to buy with copper, medals, pelts, North Stars and more, ranked by coins per one."},
			{"orders", "Orders", "Your bazaar orders (read when you open Manage Orders) and whether each is still on top. Chat alerts when one is outbid."}};

	/** The list screen for a tab. */
	static Screen screenFor(String tab) {
		return switch (tab) {
			case "flips" -> new FlipperScreen();
			case "npc" -> new MoneyScreen("npc");
			case "forge" -> new MoneyScreen("forge");
			case "books" -> new MoneyScreen("books");
			case "cur" -> new MoneyScreen("cur");
			case "prices" -> new PricesScreen();
			case "minions" -> new MinionScreen();
			case "profit" -> new ProfitScreen();
			case "essence" -> new EssenceScreen();
			case "orders" -> new OrdersScreen();
			default -> new FlipsScreen();
		};
	}

	private String currentTab;
	/** Each tab button's x, y and width (the tabs wrap onto more rows when they don't fit). */
	private int[][] tabPos = new int[0][];
	private int tabsBottom = 16;

	/** Lays the tabs out in rows: the first row stops before the Rounded button, the others use the full width. */
	private void layoutTabs() {
		tabPos = new int[TABS.length][];
		int x = left, y = 2, right = left + tableWidth - ROUNDED_BUTTON_WIDTH - 4 - GEAR_WIDTH - 2;
		for (int i = 0; i < TABS.length; i++) {
			int w = Math.max(36, font.width(TABS[i][1]) + 12);
			if (x + w > right && x > left) {
				x = left;
				y += 16;
				right = left + tableWidth;
			}
			tabPos[i] = new int[] {x, y, w};
			x += w + 2;
		}
		tabsBottom = y + 14;
	}

	/** The gear (settings) button sits just left of the Rounded button. */
	static final int GEAR_WIDTH = 18;
	static final String GEAR = "\u2699";

	private int gearX() {
		return left + tableWidth - ROUNDED_BUTTON_WIDTH - 2 - GEAR_WIDTH;
	}

	/** Where the tabs end (the page starts a little below). */
	protected int tabsBottom() {
		return tabsBottom;
	}

	/** Adds the tab buttons at the top (call in init()). The current tab is greyed out and underlined. */
	protected void addTabs(String current) {
		currentTab = current;
		layoutTabs();
		for (int i = 0; i < TABS.length; i++) {
			String key = TABS[i][0];
			Button b = addRenderableWidget(Button.builder(Component.literal(TABS[i][1]), btn -> {
				if (key.equals(currentTab)) return;
				Settings.tab = key;
				Settings.save();
				minecraft.gui.setScreen(screenFor(key));
			}).pos(tabPos[i][0], tabPos[i][1]).size(tabPos[i][2], 14).build());
			if (key.equals(current)) b.active = false;
		}
		addRenderableWidget(Button.builder(Component.literal(GEAR), btn -> minecraft.gui.setScreen(new SettingsScreen(this)))
				.pos(gearX(), 2).size(GEAR_WIDTH, 14).build());
	}

	/** The tabs' explanations and the underline under the current one; call each frame after explain.clear(). */
	protected void drawTabs(GuiGraphicsExtractor graphics) {
		for (int i = 0; i < TABS.length; i++) {
			int x = tabPos[i][0], y = tabPos[i][1], w = tabPos[i][2];
			explain.add(x, y, x + w, y + 14, TABS[i][2] + (TABS[i][0].equals(currentTab) ? " (You're here.)" : " Click to switch."));
			if (TABS[i][0].equals(currentTab)) graphics.fill(x, y + 14, x + w, y + 15, Colors.GOLD);
		}
		explain.add(gearX(), 2, gearX() + GEAR_WIDTH, 16, "Settings: the key that opens this screen, and more.");
	}

	protected void addRoundedToggle(int y, Runnable afterChange) {
		int x = left + tableWidth - ROUNDED_BUTTON_WIDTH;
		addRenderableWidget(Button.builder(Component.literal(roundedLabel()), b -> {
			Format.setRounded(!Format.rounded());
			Settings.save();
			b.setMessage(Component.literal(roundedLabel()));
			afterChange.run();
		}).pos(x, y).size(ROUNDED_BUTTON_WIDTH, 14).build());
	}

	/** Registers the Rounded button's explanation; call each frame after explain.clear(). */
	protected void explainRounded(int y) {
		explain.add(left + tableWidth - ROUNDED_BUTTON_WIDTH, y, left + tableWidth, y + 14, ROUNDED_EXPLANATION);
	}

	private static String roundedLabel() {
		return "Rounded: " + (Format.rounded() ? "On" : "Off");
	}

	/** A button whose label is cut to fit (with "...") so it never spills over its neighbours. */
	protected Button button(String label, int x, int y, int w, Button.OnPress onPress) {
		return addRenderableWidget(Button.builder(Component.literal(fit(label, w - 8)), onPress).pos(x, y).size(w, 20).build());
	}

	/** Sets a button's label, cut to fit its width. */
	protected void relabel(Button b, String label, int w) {
		b.setMessage(Component.literal(fit(label, w - 8)));
	}

	protected void drawPanel(GuiGraphicsExtractor graphics) {
		graphics.fill(left - 6, 2, left + tableWidth + 6, height - 2, Colors.PANEL);
	}

	/** The bottom strip: the hovered thing's explanation, or a hint. */
	protected void drawExplainStrip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y, int lines, String hint) {
		String hovered = explain.at(mouseX, mouseY);
		Explain.draw(graphics, font, hovered != null ? hovered : hint, width / 2, y, tableWidth, lines,
				hovered != null ? Colors.AQUA : Colors.DARK_GRAY);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	protected void rightText(GuiGraphicsExtractor graphics, String text, int right, int y, int color) {
		graphics.text(font, text, right - font.width(text), y, color);
	}

	/** Cuts text to fit a width, adding "..." when it had to cut. */
	protected String fit(String text, int maxWidth) {
		if (maxWidth <= 0) return "";
		if (font.width(text) <= maxWidth) return text;
		String s = text;
		while (!s.isEmpty() && font.width(s + "...") > maxWidth) s = s.substring(0, s.length() - 1);
		return s + "...";
	}
}
