package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The website's Flipper tab: buy order 0.1 above the top bid, sell offer 0.1 below the lowest ask, keep the gap. */
public class FlipperScreen extends TableScreen<Flipper.Listed> {
	public FlipperScreen() {
		super("Flipper", "flips");
	}

	private static boolean sized() {
		return Settings.coinsBudget() > 0 || Settings.freeSlots() > 0;
	}

	@Override
	protected List<Control> controls() {
		return List.of(
				sortControl("Sort flips by", Flipper.SORTS, () -> Settings.flipSort, k -> Settings.flipSort = k),
				new Control(() -> "Risky: " + (Settings.hideRisky ? "Hidden" : "Shown"),
						() -> "Hide risky (" + onOff(Settings.hideRisky) + "): hides items whose order book looks manipulated "
								+ "(an extreme spread with only a few orders). Risky ones that show are orange/red with '!'.",
						() -> Settings.hideRisky = !Settings.hideRisky),
				new Control(() -> "Low vol: " + (Settings.hideLowVolume ? "Hidden" : "Shown"),
						() -> "Hide low volume (" + onOff(Settings.hideLowVolume) + "): hides items with under 10k trades in the last 7 days "
								+ "(the lower of insta-bought and insta-sold). Shared with the Crafts tab.",
						() -> Settings.hideLowVolume = !Settings.hideLowVolume),
				taxControl());
	}

	@Override
	protected List<Column<Flipper.Listed>> columns() {
		List<Column<Flipper.Listed>> cols = new ArrayList<>(List.of(
				new Column<>("Score", MoneyReport.SCORE_EXPLAIN, l -> MoneyReport.scoreText(l.score()), l -> MoneyReport.scoreColor(l.score())),
				new Column<>("Each", "Each: profit per item after tax, buying 0.1 above the top buy order and selling 0.1 below the lowest sell offer.",
						l -> Format.signedShortCoins(l.flip().each()), l -> Colors.profit(l.flip().each())),
				new Column<>("Margin", "Margin: profit each as a % of the buy order price.",
						l -> String.format(Locale.US, "%.1f%%", l.flip().pct()), l -> Colors.GRAY),
				new Column<>("Per hr", "Profit / hr: profit each x trades per hour on the slower side, if you caught every trade. Real results are lower: others flip too.",
						l -> Format.signedShortCoins(l.flip().perHour()), l -> Colors.profit(l.flip().perHour())),
				new Column<>("Trades/hr", "Trades / hr: how many items trade per hour on the slower side (buy orders filling vs sell offers filling).",
						l -> Format.shortCoins(l.flip().flow()), l -> l.flip().flow() >= 20 ? Colors.GRAY : Colors.RED)));
		if (sized()) {
			cols.add(new Column<>("Best", "Best: profit from the best amount for your coins and free slots (at most about an hour of trading). Hover a row for the amount.",
					l -> l.qty().n() > 0 ? Format.signedShortCoins(l.qty().n() * l.flip().each()) : "none",
					l -> l.qty().n() > 0 ? Colors.GREEN : Colors.DARK_GRAY));
		}
		return cols;
	}

	@Override
	protected String stamp(String search) {
		return MoneyData.flipListStamp(search);
	}

	@Override
	protected List<Flipper.Listed> build(String search) {
		CoflData.ensureMovement();
		return MoneyData.flipList(search);
	}

	@Override
	protected void onVisible(List<Flipper.Listed> visible) {
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < Math.min(12, visible.size()); i++) ids.add(visible.get(i).flip().id());
		CoflData.queueHistory(ids, false);
	}

	@Override
	protected String name(Flipper.Listed l) {
		return (l.flip().risk().level() != Risk.Level.NONE ? "! " : "") + l.flip().name();
	}

	@Override
	protected int nameColor(Flipper.Listed l) {
		Risk.Level r = l.flip().risk().level();
		return r == Risk.Level.HIGH ? Colors.RED : r == Risk.Level.CAUTION ? Colors.ORANGE : Colors.WHITE;
	}

	@Override
	protected String tooltip(Flipper.Listed l) {
		Flipper.Flip f = l.flip();
		if (!f.risk().reasons().isEmpty()) return "! " + f.risk().reasons().get(0);
		String s = "Buy order " + Format.price(f.buyAt()) + " -> sell offer " + Format.price(f.sellAt());
		if (sized()) {
			s += l.qty().n() > 0 ? " · best: flip " + Format.count(l.qty().n()) + " -> " + Format.signedCoins(l.qty().n() * f.each())
					: l.qty().why().equals("slots") ? " · doesn't fit in your slots" : " · can't afford one";
		}
		return s;
	}

	@Override
	protected void open(Flipper.Listed l) {
		minecraft.gui.setScreen(new FlipperDetailScreen(this, l.flip().id()));
	}

	@Override
	protected String itemExplanation() {
		return "The item to flip, ranked by the Sort button. Orange/red with '!' = the market looks suspicious. Click a row for "
				+ "its score breakdown, a plan, how fast it fills and the live order book.";
	}

	@Override
	protected String noun() {
		return "flips";
	}

	@Override
	protected String hint() {
		return "Score uses price history & 24h moves provided by SkyCofl (* = not loaded yet). Click a row for details.";
	}

	@Override
	protected String emptyText() {
		return "No profitable flips match" + (Settings.hideLowVolume || Settings.hideRisky ? " (try showing low volume or risky)" : "");
	}

	@Override
	protected String sortKeyFor(String header) {
		return switch (header) {
			case "Score" -> "score";
			case "Each" -> "each";
			case "Margin" -> "pct";
			case "Per hr" -> "perHour";
			case "Trades/hr" -> "flow";
			case "Best" -> "batch";
			default -> null;
		};
	}

	@Override
	protected String currentSort() {
		return Settings.flipSort;
	}

	@Override
	protected void applySort(String key) {
		Settings.flipSort = key;
	}
}
