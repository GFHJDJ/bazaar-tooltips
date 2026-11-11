package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.List;

/** The website's Craft tabs as one in-game list (the Crafts tab). Click a row for the full card. */
public class FlipsScreen extends TableScreen<CraftEngine.Craft> {
	private CraftEngine engine = CraftEngine.current();

	public FlipsScreen() {
		super("Top Craft Flips", "crafts");
	}

	private static boolean sized() {
		return Settings.coinsBudget() > 0 || Settings.freeSlots() > 0;
	}

	@Override
	protected List<Control> controls() {
		return List.of(
				new Control(() -> "Buy: " + Settings.buy.label,
						() -> "Buy: how ingredients are bought. Insta-buy = now, at the cheapest sell offer. "
								+ "Buy order = 0.1 above the top buy order: cheaper, but you wait for it to fill. (The site's two Craft tabs.)",
						d -> Settings.buy = Settings.buy == Settings.Buy.INSTA ? Settings.Buy.ORDER : Settings.Buy.INSTA),
				new Control(() -> "Sort: " + CraftEngine.sortOption(Settings.sort).shortLabel(),
						() -> "Sort: " + CraftEngine.sortOption(Settings.sort).label() + ". " + CraftEngine.sortOption(Settings.sort).explanation()
								+ " Click to choose another.",
						() -> minecraft.gui.setScreen(new SortScreen(this))),
				new Control(FlipsScreen::filtersLabel,
						() -> "Filters & tax: low volume, craft chains, Personal Compactor, requirements and your unlocks, and bazaar tax ("
								+ Settings.taxLabel() + ").",
						() -> minecraft.gui.setScreen(new FiltersScreen(this))));
	}

	private static String filtersLabel() {
		int on = 0;
		if (Settings.hideLowVolume) on++;
		if (Settings.chains != Settings.Chains.EXCLUDE) on++;
		if (Settings.compactor != Settings.Compactor.ANY) on++;
		if (Settings.reqs != Settings.Reqs.ALL) on++;
		return "Filters" + (on > 0 ? " (" + on + " on)" : "") + " & tax";
	}

	private CraftEngine.Best bestOf(CraftEngine.Craft c) {
		return engine.best(c, CraftEngine.sellPick(Settings.sort), Settings.coinsBudget(), Settings.freeSlots());
	}

	@Override
	protected List<Column<CraftEngine.Craft>> columns() {
		List<Column<CraftEngine.Craft>> cols = new ArrayList<>(List.of(
				new Column<>("Cost", "Cost: what the ingredients for 1 craft cost, " + (Settings.buy == Settings.Buy.ORDER
						? "bought with buy orders 0.1 above the top buy order." : "insta-bought (the cheapest sell offers)."),
						c -> Format.shortCoins(c.cost()), c -> Colors.YELLOW),
				new Column<>("Sell offer", "Sell offer: profit per craft if you list the result 0.1 below the lowest sell offer, after tax.",
						c -> Format.signedShortCoins(c.profitSellOffer()), c -> Colors.profit(c.profitSellOffer())),
				new Column<>("Insta-sell", "Insta-sell: profit per craft if you sell the result into the top buy order, after tax.",
						c -> Format.signedShortCoins(c.profitInstaSell()), c -> Colors.profit(c.profitInstaSell())),
				new Column<>("Offer %", "Offer %: the sell-offer profit as a % of what the ingredients cost (the site's sell-order margin).",
						c -> String.format(java.util.Locale.US, "%.1f%%", c.marginSellOffer()), c -> Colors.profit(c.marginSellOffer())),
				new Column<>("Insta %", "Insta %: the insta-sell profit as a % of what the ingredients cost (the site's insta-sell margin).",
						c -> String.format(java.util.Locale.US, "%.1f%%", c.marginInstaSell()), c -> Colors.profit(c.marginInstaSell())),
				new Column<>("Vol/wk", "Vol/wk: how many of the result traded in the last 7 days (the lower of insta-bought and insta-sold). Under 10k is low volume.",
						c -> Format.amount(c.volume()), c -> c.volume() >= CraftEngine.LOW_VOLUME ? Colors.GRAY : Colors.RED)));
		if (sized()) {
			CraftEngine.Sell pick = CraftEngine.sellPick(Settings.sort);
			cols.add(new Column<>("Best", "Best: the most you can make with your coins and free slots ("
					+ (pick == CraftEngine.Sell.OFFER ? "selling by sell offer" : pick == CraftEngine.Sell.INSTA ? "insta-selling" : "whichever way of selling is better")
					+ "), walking the live order book. Hover a row for the amount.",
					c -> { CraftEngine.Best b = bestOf(c); return b == null ? "none" : Format.signedShortCoins(b.profit()); },
					c -> bestOf(c) != null ? Colors.GREEN : Colors.DARK_GRAY));
		}
		return cols;
	}

	@Override
	protected String stamp(String search) {
		return BazaarPrices.lastFetchMillis() + "|" + Settings.tax() + "|" + Settings.buy + "|" + Settings.chains + "|" + Settings.compactor + "|"
				+ Settings.reqs + "|" + Settings.hideLowVolume + "|" + Settings.sort + "|" + Settings.coins + "|" + Settings.slots + "|"
				+ Settings.unlocks.hashCode() + "|" + Format.rounded() + "|" + search;
	}

	@Override
	protected List<CraftEngine.Craft> build(String search) {
		engine = CraftEngine.current();
		return engine.list(search, Settings.buy, Settings.chains, Settings.compactor, Settings.reqs,
				Settings.hideLowVolume, Settings.sort, Settings.coinsBudget(), Settings.freeSlots());
	}

	@Override
	protected String name(CraftEngine.Craft c) {
		return (c.warnings().isEmpty() ? "" : "! ") + c.name() + (c.output() > 1 ? " x" + c.output() : "");
	}

	@Override
	protected int nameColor(CraftEngine.Craft c) {
		return c.warnings().isEmpty() ? Colors.WHITE : c.risk() == Risk.Level.HIGH ? Colors.RED : Colors.ORANGE;
	}

	@Override
	protected String tooltip(CraftEngine.Craft c) {
		if (!c.warnings().isEmpty()) return "! " + c.warnings().get(0);
		if (sized()) {
			// the shopping list for your coins & slots, right in the hover box
			CraftEngine.ShopList sl = engine.shopList(c, Settings.sort, Settings.coinsBudget(), Settings.freeSlots());
			if (sl == null) return "No profitable amount for your coins & slots right now";
			StringBuilder sb = new StringBuilder("Best: craft " + Format.count(sl.crafts()) + " -> " + Format.signedCoins(sl.profit())
					+ (sl.sell() == CraftEngine.Sell.OFFER ? " (sell offer)" : " (insta-sell)"));
			int shown = 0;
			for (CraftEngine.ShopLine l : sl.lines()) {
				if (shown++ == 7) {
					sb.append("\n...and more in the details");
					break;
				}
				sb.append("\n").append(l.what()).append(": ").append(Double.isNaN(l.coins()) ? "no price" : Format.shortCoins(l.coins()));
			}
			return sb.toString();
		}
		StringBuilder sb = new StringBuilder();
		for (CraftEngine.Ingredient i : c.ingredients()) {
			if (!sb.isEmpty()) sb.append(", ");
			sb.append(i.qty()).append("x ").append(i.name());
		}
		return sb.toString();
	}

	@Override
	protected void open(CraftEngine.Craft c) {
		minecraft.gui.setScreen(new FlipDetailScreen(this, c.key()));
	}

	@Override
	protected String itemExplanation() {
		return "The item you craft, ranked by the Sort button. Orange/red with '!' = the market looks suspicious (possible manipulation). "
				+ "Click a row for every number on it.";
	}

	@Override
	protected String noun() {
		return "crafts";
	}

	@Override
	protected String hint() {
		return "Hover anything to see what it means. Click a row for every number on it.";
	}

	@Override
	protected String emptyText() {
		return Settings.reqs == Settings.Reqs.UNLOCKED ? "No craftable items match (showing only what you've unlocked)"
				: "No craftable items match";
	}

	@Override
	protected String sortKeyFor(String header) {
		return switch (header) {
			case "Cost" -> "cost";
			case "Sell offer" -> "profitSellOrder";
			case "Insta-sell" -> "profitInstaSell";
			case "Offer %" -> "marginSellOrder";
			case "Insta %" -> "marginInstaSell";
			case "Best" -> Settings.sort.startsWith("best") ? Settings.sort : "best";
			default -> null;
		};
	}

	@Override
	protected String currentSort() {
		return Settings.sort;
	}

	@Override
	protected void applySort(String key) {
		Settings.sort = key;
	}
}
