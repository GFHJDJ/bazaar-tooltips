package com.unc.bazaartips;

import java.util.List;
import java.util.Locale;

/** The website's Prices tab: every bazaar item's prices, spread, margin and volume. Click one for its order book. */
public class PricesScreen extends TableScreen<MoneyData.PriceItem> {
	public PricesScreen() {
		super("Prices", "prices");
	}

	@Override
	protected List<Control> controls() {
		return List.of(
				sortControl("Sort prices by", MoneyData.PRICE_SORTS, () -> Settings.pricesSort, k -> Settings.pricesSort = k),
				new Control(() -> "Low vol: " + (Settings.hideLowVolume ? "Hidden" : "Shown"),
						() -> "Hide low volume (" + onOff(Settings.hideLowVolume) + "): hides items with under 10k trades in the last 7 days "
								+ "(the lower of insta-bought and insta-sold). Shared with the Crafts and Flipper tabs.",
						d -> Settings.hideLowVolume = !Settings.hideLowVolume));
	}

	@Override
	protected List<Column<MoneyData.PriceItem>> columns() {
		return List.of(
				new Column<>("Buy", "Buy: Hypixel's averaged price to insta-buy one (what you pay right now).",
						i -> Format.shortCoins(i.p().quickBuy()), i -> Colors.YELLOW),
				new Column<>("Sell", "Sell: Hypixel's averaged price you get insta-selling one (before tax).",
						i -> Format.shortCoins(i.p().quickSell()), i -> Colors.YELLOW),
				new Column<>("Margin", "Margin: insta-buy minus insta-sell (the spread): roughly what a flipper makes per item before tax.",
						i -> Format.signedShortCoins(i.margin()), i -> Colors.profit(i.margin())),
				new Column<>("Margin %", "Margin %: the spread as a % of the insta-sell price.",
						i -> String.format(Locale.US, "%.1f%%", i.marginPct()), i -> Colors.profit(i.margin())),
				new Column<>("Vol/wk", "Vol/wk: how many traded in the last 7 days (the lower of insta-bought and insta-sold). Under 10k is low volume.",
						i -> Format.amount(i.weekVol()), i -> i.weekVol() >= CraftEngine.LOW_VOLUME ? Colors.GRAY : Colors.RED));
	}

	@Override
	protected String stamp(String search) {
		return BazaarPrices.lastFetchMillis() + "|" + Settings.pricesSort + "|" + Settings.hideLowVolume + "|" + Format.rounded() + "|" + search;
	}

	@Override
	protected List<MoneyData.PriceItem> build(String search) {
		return MoneyData.prices(search);
	}

	@Override
	protected String name(MoneyData.PriceItem i) {
		return (i.risk().level() != Risk.Level.NONE ? "! " : "") + i.name();
	}

	@Override
	protected int nameColor(MoneyData.PriceItem i) {
		return i.risk().level() == Risk.Level.HIGH ? Colors.RED : i.risk().level() == Risk.Level.CAUTION ? Colors.ORANGE : Colors.WHITE;
	}

	@Override
	protected String tooltip(MoneyData.PriceItem i) {
		return i.id() + " · " + Format.amount(i.weekVol()) + "/wk" + (i.risk().reasons().isEmpty() ? "" : " · ! " + i.risk().reasons().get(0));
	}

	@Override
	protected void open(MoneyData.PriceItem i) {
		minecraft.gui.setScreen(new PriceDetailScreen(this, i.id()));
	}

	@Override
	protected String itemExplanation() {
		return "Every bazaar item, ranked by the Sort button. Orange/red with '!' = the market looks suspicious. Click one for its full stats and live order book.";
	}

	@Override
	protected String noun() {
		return "items";
	}

	@Override
	protected String hint() {
		return "Hover anything to see what it means. Click an item for its order book.";
	}

	@Override
	protected String emptyText() {
		return "Nothing matches that.";
	}

	@Override
	protected String sortKeyFor(String header) {
		return switch (header) {
			case "Buy" -> "buy";
			case "Sell" -> "sell";
			case "Margin" -> "margin";
			case "Margin %" -> "marginPct";
			case "Vol/wk" -> "weekVol";
			default -> null;
		};
	}

	@Override
	protected String currentSort() {
		return Settings.pricesSort;
	}

	@Override
	protected void applySort(String key) {
		Settings.pricesSort = key;
	}
}
