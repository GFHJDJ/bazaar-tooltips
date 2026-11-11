package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The website's Coin advisor tab: the best use of your coins (and free slots) across every other tab. */
public class AdvisorScreen extends TableScreen<Advisor.Option> {
	private Advisor.Steps steps = new Advisor.Steps(List.of(), 0);

	public AdvisorScreen() {
		super("Coin advisor", "plan");
	}

	@Override
	protected void init() {
		AhPrices.ensure();
		super.init();
	}

	static String shortKind(String src) {
		return switch (src) {
			case "flip" -> "Flip";
			case "npc" -> "BZ>NPC";
			case "shop" -> "NPC shop";
			case "forge" -> "Forge";
			case "books" -> "Books";
			default -> "Craft";
		};
	}

	// ---- settings buttons ----

	@Override
	protected List<Control> controls() {
		List<Control> c = new ArrayList<>();
		c.add(sortControl("Sort ideas by", Advisor.SORTS, () -> Settings.planSort, k -> {
			Settings.planSort = k;
			MoneyData.syncSellFromSort(k);
		}));
		c.add(new Control(() -> "Show: " + (Settings.planSrc.equals("all") ? "Everything" : shortKind(Settings.planSrc)),
				() -> "Show: " + (Settings.planSrc.equals("all") ? "every kind of idea" : "only " + Advisor.SRC.get(Settings.planSrc))
						+ ". Click to step through flips, NPC flips, NPC shops, forge, books and crafts.",
				d -> {
					List<String> keys = new ArrayList<>(List.of("all"));
					keys.addAll(Advisor.SRC.keySet());
					Settings.planSrc = Cycle.step(keys, Settings.planSrc, d);
				}));
		c.add(new Control(() -> "Buy: " + (Settings.moneyBuyOrder ? "Buy order" : "Insta-buy"),
				() -> "Buy: how you buy on the Bazaar (shared with the NPC, Forge and Books tabs). Insta-buy = now, walking the order book. "
						+ "Buy order = cheaper, but you wait (capped at about an hour of what the market trades).",
				d -> Settings.moneyBuyOrder = !Settings.moneyBuyOrder));
		c.add(new Control(() -> "Sell: " + (Settings.moneySellOffer ? "Sell offer" : "Insta-sell"),
				() -> "Sell: how results sell on the Bazaar, after tax. Insta-sell = now, into buy orders. Sell offer = list it and wait "
						+ "(capped at about an hour of what the market trades).",
				d -> { Settings.moneySellOffer = !Settings.moneySellOffer; MoneyData.syncSortFromSell(); }));
		c.add(new Control(() -> "Leave out" + (Settings.planExSrc.size() + Settings.planEx.size() > 0
				? ": " + (Settings.planExSrc.size() + Settings.planEx.size()) : ": none"),
				() -> "Leave out kinds of ideas (flips, crafts...) or single items you don't want suggested. "
						+ (Settings.planExSrc.isEmpty() && Settings.planEx.isEmpty() ? "Nothing left out now." : Settings.planExSrc.size() + " kinds and "
								+ Settings.planEx.size() + " items left out now.") + " Click to change.",
				() -> minecraft.gui.setScreen(new AdvisorExcludeScreen(this))));
		c.add(new Control(() -> "HotM: " + (Settings.forgeHotm == 0 ? "any" : Integer.toString(Settings.forgeHotm)),
				() -> "Your Heart of the Mountain tier (" + (Settings.forgeHotm == 0 ? "any" : Settings.forgeHotm) + "): hides forge recipes that need "
						+ "a higher tier and sets your forge slots (" + MoneyEngine.forgeSlots(Settings.forgeHotm) + "). Click to step 1-10, then any.",
				d -> Settings.forgeHotm = Cycle.step(Settings.forgeHotm, 0, 10, d)));
		c.add(new Control(() -> "NPCs: " + switch (Settings.npcAvail) { case "all" -> "All"; case "nomayor" -> "+situational"; default -> "Always there"; },
				() -> "NPC shops: " + switch (Settings.npcAvail) {
					case "all" -> "All: includes seasonal (event), mayor-only and situational NPCs.";
					case "nomayor" -> "Hides seasonal (event) and mayor-only NPCs; shows situational ones.";
					default -> "Always there: hides seasonal (event), mayor-only and situational NPCs.";
				} + " Click to change.",
				d -> Settings.npcAvail = Cycle.step(List.of("always", "nomayor", "all"), Settings.npcAvail, d)));
		c.add(new Control(() -> "Compactor: " + switch (Settings.compactor) { case ONLY -> "Only"; case HIDE -> "Hidden"; default -> "Any"; },
				() -> "Personal Compactor crafts (shared with the Crafts tab): " + Settings.compactor.explanation + " Click to change.",
				d -> Settings.compactor = Cycle.step(Settings.compactor, d)));
		c.add(taxControl());
		return c;
	}

	// ---- the table ----

	@Override
	protected List<Column<Advisor.Option>> columns() {
		boolean slots = Settings.freeSlots() > 0;
		List<Column<Advisor.Option>> cols = new ArrayList<>();
		cols.add(new Column<>("Kind", "Kind: flip (buy order -> sell offer), Bazaar -> NPC, NPC shop -> Bazaar, forge, book combining or craft.",
				o -> shortKind(o.src), o -> Colors.AQUA));
		cols.add(new Column<>("Best amount", "Best amount: the most profitable amount for your coins" + (slots ? " and free slots" : "")
				+ " (orders are capped at about an hour of real trading, insta-buys at what's in the order book).",
				o -> Format.count(o.best.n()) + " " + Advisor.unit(o.src, o.best.n()), o -> Colors.WHITE));
		cols.add(new Column<>("Uses", "Uses: the coins the best amount ties up until it's done.", o -> Format.shortCoins(o.cost), o -> Colors.YELLOW));
		cols.add(new Column<>("Profit", "Profit: what the best amount makes, after tax.", o -> Format.signedShortCoins(o.coins), o -> Colors.profit(o.coins)));
		cols.add(new Column<>("Return", "Return: profit as a % of the coins it uses.", o -> String.format(Locale.US, "%.1f%%", o.pct), o -> Colors.GRAY));
		cols.add(new Column<>("Time", "Time: about how long it takes (orders filling, forging). Instant = insta-buy and insta-sell.",
				o -> Amounts.timeLabel(o.hrs), o -> Colors.GRAY));
		cols.add(new Column<>("Per hr", "Profit / hr: profit divided by the time (instant ones count as about 5 minutes).",
				o -> Format.signedShortCoins(o.perHour), o -> Colors.profit(o.perHour)));
		return cols;
	}

	@Override
	protected String stamp(String search) {
		return MoneyData.planStampNow() + "|" + Settings.planSort + "|" + search + "|" + Format.rounded();
	}

	@Override
	protected List<Advisor.Option> build(String search) {
		List<Advisor.Option> all = MoneyData.plan();
		steps = Advisor.steps(all, Settings.coinsBudget(), Settings.freeSlots());
		return Advisor.sorted(all, search, Settings.planSort);
	}

	@Override
	protected List<ProductSummary.Line> intro() {
		List<ProductSummary.Line> out = new ArrayList<>();
		if (steps.steps().isEmpty()) return out;
		double b = Settings.coinsBudget();
		out.add(new ProductSummary.Line("Suggested split of your " + Format.shortCoins(b) + " coins", Colors.GOLD, "uses -> profit", Colors.DARK_GRAY, false));
		int i = 1;
		for (Advisor.Step s : steps.steps()) {
			out.add(new ProductSummary.Line("#" + i++ + " " + shortKind(s.option().src) + ": " + s.option().title + " · " + Format.count(s.n()) + " "
					+ Advisor.unit(s.option().src, s.n()), Colors.WHITE, Format.shortCoins(s.cost()) + " -> " + Format.signedShortCoins(s.profit()),
					Colors.GREEN, true));
		}
		out.add(new ProductSummary.Line("Total" + (steps.left() >= 1 ? " · " + Format.shortCoins(steps.left()) + " left over" : ""), Colors.GRAY,
				Format.shortCoins(b - steps.left()) + " -> " + Format.signedShortCoins(steps.profit()), Colors.GREEN, true));
		return out;
	}

	@Override
	protected String introExplain(int i) {
		if (i == 0) return "Suggested split: the most profitable idea first, then the next best with the coins left over (up to 3). Flips, forges "
				+ "and orders tie up coins until they finish, so it's a starting point, not a schedule.";
		if (i <= steps.steps().size()) return "Step " + i + ": click for the details.";
		return "What the steps use together and make together.";
	}

	@Override
	protected boolean introClicked(int i) {
		if (i < 1 || i > steps.steps().size()) return false;
		open(steps.steps().get(i - 1).option());
		return true;
	}

	@Override
	protected String waiting() {
		String w = super.waiting();
		if (w != null) return w;
		return Settings.coinsBudget() > 0 ? null : "Type your coins in the Coins box above (5m, 250k, 1.2b)";
	}

	@Override
	protected String waitingHint() {
		if (BazaarPrices.lastFetchMillis() == 0) return super.waitingHint();
		return "This tab sizes every flip, NPC flip, forge, book combine and craft to your coins (and free slots, if you add them) and ranks them.";
	}

	@Override
	protected String name(Advisor.Option o) {
		return o.title;
	}

	@Override
	protected int nameColor(Advisor.Option o) {
		return Colors.WHITE;
	}

	@Override
	protected String tooltip(Advisor.Option o) {
		String s = o.kind() + " · best: " + Format.count(o.best.n()) + " " + Advisor.unit(o.src, o.best.n());
		if (Settings.freeSlots() > 0) s += " · " + o.slotsUsed(o.best.n()) + " slots";
		s += " · uses " + Format.coins(o.cost) + " -> " + Format.signedCoins(o.coins);
		if (!o.limit.isEmpty()) s += "\nLimit: " + o.limit;
		return s;
	}

	@Override
	protected void open(Advisor.Option o) {
		minecraft.gui.setScreen(new AdvisorDetailScreen(this, o.key));
	}

	@Override
	protected String itemExplanation() {
		return "Every way to make money here, sized to your coins and ranked by the Sort button. Bazaar sales only; risky and low-volume "
				+ "items are left out. Click a row for what to buy and how.";
	}

	@Override
	protected String noun() {
		return "ideas";
	}

	@Override
	protected String hint() {
		return "Sized to your coins & slots · Bazaar sales only · click a row for details";
	}

	@Override
	protected String emptyText() {
		return "Nothing makes a profit with " + Format.shortCoins(Settings.coinsBudget()) + " coins right now"
				+ (Settings.planSrc.equals("all") ? "" : " in " + Advisor.SRC.get(Settings.planSrc)) + " (try more coins, or orders)";
	}

	@Override
	protected String sortKeyFor(String header) {
		return switch (header) {
			case "Profit" -> "coins";
			case "Return" -> "pct";
			case "Time" -> "time";
			case "Per hr" -> "perHour";
			default -> null;
		};
	}

	@Override
	protected String currentSort() {
		return Settings.planSort;
	}

	@Override
	protected boolean sortedBy(String key) {
		return key != null && (key.equals(Settings.planSort) || key.equals("coins") && Settings.planSort.startsWith("coins"));
	}

	@Override
	protected void applySort(String key) {
		Settings.planSort = key;
	}
}
