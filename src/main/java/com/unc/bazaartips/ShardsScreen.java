package com.unc.bazaartips;

import java.util.List;

/** The Shards tab: fuse two shards into another and sell it on the Bazaar, the cheapest way to make each one, best first. */
public class ShardsScreen extends TableScreen<ShardEngine.Fusion> {
	public ShardsScreen() {
		super("Shards", "shards");
	}

	private static String sellLabel() {
		return Settings.moneySellOffer ? "sell offer" : "insta-sell";
	}

	@Override
	protected List<Control> controls() {
		return List.of(
				new Control(() -> "Buy: " + (Settings.moneyBuyOrder ? "Buy order" : "Insta-buy"),
						() -> "Buy: how you get the two shards you fuse (shared with the NPC, Forge and Books tabs). Insta-buy = now, at the lowest sell "
								+ "offer. Buy order = 0.1 above the top buy order: cheaper, but you wait.",
						d -> Settings.moneyBuyOrder = !Settings.moneyBuyOrder),
				new Control(() -> "Sell: " + (Settings.moneySellOffer ? "Sell offer" : "Insta-sell"),
						() -> "Sell: how the result sells on the Bazaar, after tax. Insta-sell = now, into the top buy order. Sell offer = 0.1 under the "
								+ "lowest sell offer: more, but you wait.",
						d -> { Settings.moneySellOffer = !Settings.moneySellOffer; MoneyData.syncSortFromSell(); }),
				new Control(() -> "Risky: " + (Settings.hideRisky ? "Hidden" : "Shown"),
						() -> "Hide risky (" + onOff(Settings.hideRisky) + "): hides shards whose Bazaar order book looks manipulated (an extreme spread with "
								+ "only a few orders). Shared with the Flipper and AH tabs.",
						d -> Settings.hideRisky = !Settings.hideRisky),
				new Control(() -> "Profitable: " + onOff(Settings.shardProfitOnly),
						() -> "Profitable only (" + onOff(Settings.shardProfitOnly) + "): hides fusions that lose coins right now.",
						d -> Settings.shardProfitOnly = !Settings.shardProfitOnly),
				taxControl());
	}

	@Override
	protected List<Column<ShardEngine.Fusion>> columns() {
		return List.of(
				new Column<>("Cost", "Cost: the two shards one fusion uses, bought the way the Buy button says (the cheapest pair that makes this shard).",
						f -> Format.shortCoins(f.best().cost()), f -> Colors.YELLOW),
				new Column<>("Sells for", "Sells for: what one fusion's result sells for on the Bazaar, the way the Sell button says, after tax.",
						f -> Format.shortCoins(f.value()), f -> Colors.YELLOW),
				new Column<>("Profit", "Profit: sells for minus cost, per fusion.", f -> Format.signedShortCoins(f.profit()), f -> Colors.profit(f.profit())),
				new Column<>("Return", "Return: profit as a % of the cost.", f -> MoneyEngine.pctStr(f.pct()), f -> Colors.GRAY),
				new Column<>("Vol/wk", "Vol/wk: how many of the result traded on the Bazaar in the last 7 days (the lower of bought and sold). Low = slow to sell.",
						f -> Format.amount(f.weekVol()), f -> f.weekVol() >= 1000 ? Colors.GRAY : Colors.RED));
	}

	@Override
	protected String stamp(String search) {
		return BazaarPrices.lastFetchMillis() + "|" + Settings.tax() + "|" + Settings.moneyBuyOrder + "|" + Settings.moneySellOffer + "|" + Settings.hideRisky
				+ "|" + Settings.shardProfitOnly + "|" + Settings.shardSort + "|" + Format.rounded() + "|" + search;
	}

	@Override
	protected List<ShardEngine.Fusion> build(String search) {
		List<ShardEngine.Fusion> all = ShardEngine.fusions(BazaarPrices::get, Settings.tax(), Settings.moneyBuyOrder, Settings.moneySellOffer);
		all.removeIf(f -> (Settings.hideRisky && f.risk().level() == Risk.Level.HIGH) || (Settings.shardProfitOnly && !(f.profit() > 0)));
		return ShardEngine.sorted(all, Settings.shardSort, search);
	}

	@Override
	protected String name(ShardEngine.Fusion f) {
		return (f.risk().flagged() ? "! " : "") + f.result().name() + (f.best().out() > 1 ? " x" + f.best().out() : "") + "  <- "
				+ ShardEngine.recipeText(f.best());
	}

	@Override
	protected int nameColor(ShardEngine.Fusion f) {
		if (f.risk().level() == Risk.Level.HIGH) return Colors.RED;
		return switch (f.result().rarity()) {
			case "uncommon" -> Colors.GREEN;
			case "rare" -> 0xFF5555FF;
			case "epic" -> 0xFFAA00AA;
			case "legendary" -> Colors.GOLD;
			default -> Colors.WHITE;
		};
	}

	@Override
	protected String tooltip(ShardEngine.Fusion f) {
		ShardEngine.Way w = f.best();
		StringBuilder sb = new StringBuilder(cap(f.result().rarity()) + " · " + f.result().family() + "\n");
		sb.append("Fuse ").append(ShardEngine.recipeText(w)).append(" (").append(Format.price(w.aEach())).append(" + ").append(Format.price(w.bEach()))
				.append(" each) -> ").append(w.out()).append("x ").append(f.result().name()).append(", ").append(sellLabel()).append(" ")
				.append(Format.price(f.sellEach())).append(" each after tax");
		for (ShardEngine.Way o : f.others()) {
			sb.append("\nor ").append(ShardEngine.recipeText(o)).append(": ").append(Format.shortCoins(o.cost()))
					.append(o.out() > 1 ? " for " + o.out() : "");
		}
		if (!f.risk().reasons().isEmpty()) sb.append("\n! ").append(f.risk().reasons().get(0));
		return sb.toString();
	}

	private static String cap(String s) {
		return s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	@Override
	protected void open(ShardEngine.Fusion f) {
		minecraft.gui.setScreen(new PriceDetailScreen(this, f.result().id()));
	}

	@Override
	protected String itemExplanation() {
		return "The shard you make <- the two you fuse (how many of each). Colour = rarity. Hover for the prices and other ways to make it; "
				+ "click for its Bazaar prices, chart and alerts.";
	}

	@Override
	protected String noun() {
		return "fusions";
	}

	@Override
	protected String hint() {
		return "A fusion can give up to 3 results: pick this one in the Fusion Machine · recipes from SkyShards (Campion, MIT)";
	}

	@Override
	protected String emptyText() {
		return Settings.shardProfitOnly ? "No shard fusion makes a profit right now." : "No shard fusions can be priced right now.";
	}

	// ---- sorting by clicking a column name ----

	@Override
	protected String sortKeyFor(String header) {
		return switch (header) {
			case "Profit" -> "profit";
			case "Return" -> "pct";
			case "Cost" -> "cost";
			case "Vol/wk" -> "vol";
			default -> null;
		};
	}

	@Override
	protected String currentSort() {
		return Settings.shardSort;
	}

	@Override
	protected void applySort(String key) {
		Settings.shardSort = key;
		Settings.save();
	}
}
