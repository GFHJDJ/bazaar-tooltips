package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The Earnings tab: the coins you actually made on the bazaar, from the buys and sells Hypixel reports in chat. */
public class EarningsScreen extends TableScreen<Earnings.Row> {
	/** "Forget trades" needs a second click within this long, so one stray click can't wipe your history. */
	private static final long CONFIRM_MILLIS = 5000;
	private static long forgetArmedAt = 0;

	private Earnings.Summary summary = Earnings.summarize(List.of(), 0);

	public EarningsScreen() {
		super("Earnings", "earned");
	}

	private static boolean armed() {
		return System.currentTimeMillis() - forgetArmedAt < CONFIRM_MILLIS;
	}

	@Override
	protected List<Control> controls() {
		return List.of(
				new Control(() -> "Show: " + Earnings.periodLabel(Settings.earnPeriod),
						() -> "Which trades count (" + Earnings.periodLabel(Settings.earnPeriod) + "): this session (since you started the game), "
								+ "today (since midnight), the last 7 days, or all time. Click to change.",
						d -> {
							List<String> keys = new ArrayList<>();
							for (String[] p : Earnings.PERIODS) keys.add(p[0]);
							Settings.earnPeriod = Cycle.step(keys, Settings.earnPeriod, d);
						}),
				new Control(() -> armed() ? "Click again to forget" : "Forget trades",
						() -> "Deletes every trade written down so far (all time, not just the ones shown). It can't be undone. Click twice to confirm.",
						() -> {
							if (armed()) {
								Earnings.clear();
								forgetArmedAt = 0;
							} else {
								forgetArmedAt = System.currentTimeMillis();
							}
						}));
	}

	@Override
	protected List<Column<Earnings.Row>> columns() {
		return List.of(
				new Column<>("Profit", "Profit: what you sold these for minus what they cost you: the ones you bought here (average price), and the "
						+ "ones you crafted from ingredients you bought here (what those ingredients cost). '-' = no tracked cost (farmed, or bought before).",
						r -> r.hasProfit() ? Format.signedShortCoins(r.profit()) : "-", r -> r.hasProfit() ? Colors.profit(r.profit()) : Colors.GRAY),
				new Column<>("Bought", "Bought: how many you bought (insta-buys and claimed buy orders).", r -> Format.count(r.bought()), r -> Colors.GRAY),
				new Column<>("Spent", "Spent: the coins those cost.", r -> Format.shortCoins(r.spent()), r -> Colors.YELLOW),
				new Column<>("Sold", "Sold: how many you sold (insta-sells and claimed sell offers).", r -> Format.count(r.sold()), r -> Colors.GRAY),
				new Column<>("Earned", "Earned: the coins you got for those, as Hypixel's message reports them.", r -> Format.shortCoins(r.earned()), r -> Colors.YELLOW),
				new Column<>("Holding", "Holding: bought here and not sold yet, as of now. Crafting it into something you sell on the bazaar takes it off.",
						r -> r.holding() > 0 ? Format.count(r.holding()) : "-", r -> Colors.GRAY));
	}

	@Override
	protected String stamp(String search) {
		// the minute is in here so "per hour" and "Today" stay current while the screen is open
		return Earnings.version() + "|" + Settings.earnPeriod + "|" + Settings.earnSort + "|" + Format.rounded() + "|" + search + "|" + armed()
				+ "|" + System.currentTimeMillis() / 60_000;
	}

	@Override
	protected List<Earnings.Row> build(String search) {
		summary = Earnings.summary(Settings.earnPeriod, System.currentTimeMillis());
		String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		List<Earnings.Row> out = new ArrayList<>();
		for (Earnings.Row r : Earnings.sorted(summary.rows(), Settings.earnSort)) {
			if (r.name().toLowerCase(Locale.ROOT).contains(q)) out.add(r);
		}
		return out;
	}

	// ---- the totals above the list ----

	private static final String[] INTRO_EXPLAIN = {
			"Profit: for everything you sold that you bought on the bazaar (or crafted from ingredients you bought there), what you sold it for "
					+ "minus what it cost you. It's from Hypixel's own chat messages, not an estimate. Per hour counts from the first trade shown.",
			"Coins in minus coins out: everything you got from selling minus everything you spent buying. It's below zero while you're still "
					+ "holding what you bought.",
			"Still holding: what you bought here and haven't sold here yet, at what you paid. Items you used up (in a craft, say) stay here.",
			"Other sales: coins from selling things with no tracked cost: not bought here, and not crafted from ingredients bought here (farmed, or bought earlier). "
					+ "They're real coins, but with no buy price there's no profit to work out."};

	@Override
	protected List<ProductSummary.Line> intro() {
		Earnings.Summary s = summary;
		List<ProductSummary.Line> out = new ArrayList<>();
		double perHr = s.perHour(System.currentTimeMillis());
		out.add(new ProductSummary.Line("Profit · " + Earnings.periodLabel(Settings.earnPeriod).toLowerCase(Locale.ROOT), Colors.GOLD,
				Format.signedCoins(s.profit()) + (Double.isNaN(perHr) ? "" : "  (" + Format.signedShortCoins(perHr) + "/hr)"), Colors.profit(s.profit()), false));
		out.add(new ProductSummary.Line("Coins in - coins out", Colors.GRAY,
				Format.signedCoins(s.net()) + "  (" + Format.shortCoins(s.earned()) + " in, " + Format.shortCoins(s.spent()) + " out)", Colors.profit(s.net()), true));
		out.add(new ProductSummary.Line("Still holding", Colors.GRAY,
				s.holdingItems() > 0 ? Format.count(s.holdingItems()) + " items, cost " + Format.shortCoins(s.holdingCost()) : "nothing", Colors.YELLOW, true));
		out.add(new ProductSummary.Line("Other sales (no buy seen)", Colors.GRAY, Format.shortCoins(s.otherSales()), Colors.YELLOW, true));
		return out;
	}

	@Override
	protected String introExplain(int i) {
		return i >= 0 && i < INTRO_EXPLAIN.length ? INTRO_EXPLAIN[i] : null;
	}

	// ---- sorting by clicking a column name ----

	@Override
	protected String sortKeyFor(String header) {
		return switch (header) {
			case "Profit" -> "profit";
			case "Bought" -> "bought";
			case "Spent" -> "spent";
			case "Sold" -> "sold";
			case "Earned" -> "earned";
			case "Holding" -> "holding";
			default -> null;
		};
	}

	@Override
	protected String currentSort() {
		return Settings.earnSort;
	}

	@Override
	protected void applySort(String key) {
		Settings.earnSort = key;
		Settings.save();
	}

	// ---- the rest ----

	@Override
	protected String waiting() {
		return null;
	}

	@Override
	protected String name(Earnings.Row r) {
		return r.name();
	}

	@Override
	protected int nameColor(Earnings.Row r) {
		return r.hasProfit() && r.profit() < 0 ? Colors.RED : Colors.WHITE;
	}

	@Override
	protected String tooltip(Earnings.Row r) {
		List<String> parts = new ArrayList<>();
		if (r.bought() > 0) parts.add("bought " + Format.count(r.bought()) + " at " + Format.price(r.spent() / r.bought()) + " each");
		if (r.sold() > 0) parts.add("sold " + Format.count(r.sold()) + " at " + Format.price(r.earned() / r.sold()) + " each");
		if (r.crafted() > 0) parts.add(Format.count(r.crafted()) + " crafted from ingredients you bought (" + Format.coins(r.craftCost()) + ")");
		if (r.hasProfit()) parts.add("profit " + Format.signedCoins(r.profit()) + " on " + Format.count(r.matched() + r.crafted()));
		if (r.usedInCrafts() > 0) parts.add(Format.count(r.usedInCrafts()) + " crafted into something you sold");
		if (r.otherSales() > 0) parts.add(Format.coins(r.otherSales()) + " from ones with no buy seen");
		if (r.holding() > 0) parts.add("holding " + Format.count(r.holding()) + " (cost " + Format.coins(r.holdingCost()) + ")");
		return String.join(" · ", parts);
	}

	@Override
	protected void open(Earnings.Row r) {
		String id = OrderTracker.productFor(r.name());
		if (id != null) minecraft.gui.setScreen(new PriceDetailScreen(this, id));
	}

	@Override
	protected String itemExplanation() {
		return "Every item you traded in this period. Click one for its live order book.";
	}

	@Override
	protected String noun() {
		return "items";
	}

	@Override
	protected String hint() {
		return summary.trades() + " trade" + (summary.trades() == 1 ? "" : "s") + " · written down from Hypixel's [Bazaar] chat messages while you play";
	}

	@Override
	protected String emptyText() {
		return Earnings.trades().isEmpty() ? "Nothing yet: buy or sell on the bazaar and it shows up here."
				: "No trades in this period. Try Show: All time.";
	}
}
