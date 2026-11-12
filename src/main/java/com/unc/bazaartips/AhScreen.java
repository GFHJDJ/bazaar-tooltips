package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.List;

/**
 * The website's Auction house tab, three views: items listed on the AH below their Bazaar price ("arb"), crafts using
 * AH-only ingredients and sold on the Bazaar ("craft"), and a lookup of any AH item's lowest BIN ("lookup").
 */
public class AhScreen extends TableScreen<AhEngine.Row> {
	private static final List<String> VIEWS = List.of("arb", "craft", "lookup");

	public AhScreen() {
		super("Auction house", "ah");
	}

	private static String view() {
		return VIEWS.contains(Settings.ahView) ? Settings.ahView : "arb";
	}

	private static String viewLabel() {
		return switch (view()) { case "craft" -> "AH crafts"; case "lookup" -> "Lookup"; default -> "AH -> Bazaar"; };
	}

	private static boolean offer() {
		return Settings.moneySellOffer;
	}

	@Override
	protected void init() {
		AhPrices.ensure();
		super.init();
	}

	@Override
	protected List<Control> controls() {
		List<Control> c = new ArrayList<>();
		c.add(new Control(() -> "View: " + viewLabel(),
				() -> "View: " + switch (view()) {
					case "craft" -> "AH crafts: recipes that need something only sold on the AH; buy it there (and the rest on the Bazaar), craft, sell on the Bazaar.";
					case "lookup" -> "Lookup: type an item's name in Search for its lowest BIN, and for craftable items what crafting it costs.";
					default -> "AH -> Bazaar: items someone listed on the AH for less than they sell for on the Bazaar. Buy the listing, sell it on the Bazaar.";
				} + " Click to switch (right click goes back).",
				d -> Settings.ahView = Cycle.step(VIEWS, view(), d)));
		if (!view().equals("lookup")) {
			c.add(new Control(() -> "Sell: " + (offer() ? "Sell offer" : "Insta-sell"),
					() -> "Sell: how it sells on the Bazaar, after tax (shared with the NPC, Forge and Books tabs). Insta-sell = now, at Hypixel's averaged "
							+ "buy-order price. Sell offer = list it at the averaged sell-offer price and wait.",
					d -> { Settings.moneySellOffer = !Settings.moneySellOffer; MoneyData.syncSortFromSell(); }));
			c.add(new Control(() -> "Risky: " + (Settings.hideRisky ? "Hidden" : "Shown"),
					() -> "Hide risky (" + onOff(Settings.hideRisky) + "): hides items whose Bazaar order book looks manipulated (an extreme spread with only "
							+ "a few orders). Shared with the Flipper tab.",
					d -> Settings.hideRisky = !Settings.hideRisky));
			c.add(new Control(() -> "Low vol: " + (Settings.hideLowVolume ? "Hidden" : "Shown"),
					() -> "Hide low volume (" + onOff(Settings.hideLowVolume) + "): hides items with under 10k Bazaar trades in the last 7 days (slow to "
							+ "sell). Shared with the Crafts, Flipper and Prices tabs.",
					d -> Settings.hideLowVolume = !Settings.hideLowVolume));
			c.add(taxControl());
		}
		return c;
	}

	// ---- the table ----

	@Override
	protected List<Column<AhEngine.Row>> columns() {
		List<Column<AhEngine.Row>> cols = new ArrayList<>();
		switch (view()) {
			case "craft" -> {
				cols.add(new Column<>("Mats", "Mats: what the ingredients for one craft cost (AH-only ones at their lowest BIN, the rest at the Bazaar's averaged insta-buy price).",
						r -> Format.shortCoins(r.cost()), r -> Colors.YELLOW));
				cols.add(new Column<>("Sells for", "Sells for: what one craft's result sells for on the Bazaar, the way the Sell button says, after tax.",
						r -> Format.shortCoins(offer() ? r.sellOffer() : r.instaSell()), r -> Colors.YELLOW));
				cols.add(new Column<>("Profit", "Profit: sells for minus mats, per craft.", r -> Format.signedShortCoins(r.profit(offer())), r -> Colors.profit(r.profit(offer()))));
				cols.add(new Column<>("Return", "Return: profit as a % of the mats.", r -> MoneyEngine.pctStr(r.pct(offer())), r -> Colors.GRAY));
			}
			case "lookup" -> {
				cols.add(new Column<>("Lowest BIN", "Lowest BIN: the cheapest Buy It Now listing on the AH right now (for Bazaar items, often just the Bazaar price).",
						r -> Format.shortCoins(r.bin()), r -> Colors.YELLOW));
				cols.add(new Column<>("Craft cost", "Craft cost: what crafting one costs (ingredients on the Bazaar at the averaged insta-buy price, the rest at the AH lowest BIN). "
						+ "'-' = not craftable, or an ingredient has no price.", r -> Double.isNaN(r.cost()) ? "-" : Format.shortCoins(r.cost()), r -> Colors.YELLOW));
				cols.add(new Column<>("Craft -> AH", "Craft -> AH: lowest BIN minus craft cost, before AH fees.",
						r -> Double.isNaN(r.cost()) ? "-" : Format.signedShortCoins(r.profit(true)), r -> Double.isNaN(r.cost()) ? Colors.GRAY : Colors.profit(r.profit(true))));
			}
			default -> {
				cols.add(new Column<>("AH BIN", "AH BIN: the cheapest Buy It Now listing on the AH (only real auctions: when the list just has the Bazaar's price it's left out).",
						r -> Format.shortCoins(r.bin()), r -> Colors.YELLOW));
				cols.add(new Column<>("Bazaar", "Bazaar: what one sells for on the Bazaar, the way the Sell button says, after tax.",
						r -> Format.shortCoins(offer() ? r.sellOffer() : r.instaSell()), r -> Colors.YELLOW));
				cols.add(new Column<>("Profit", "Profit: Bazaar sale minus the AH price, per item. (Buying on the AH has no fee.)",
						r -> Format.signedShortCoins(r.profit(offer())), r -> Colors.profit(r.profit(offer()))));
				cols.add(new Column<>("Return", "Return: profit as a % of the AH price.", r -> MoneyEngine.pctStr(r.pct(offer())), r -> Colors.GRAY));
				cols.add(new Column<>("Vol/wk", "Vol/wk: how many traded on the Bazaar in the last 7 days (the lower of insta-bought and insta-sold): how easy it is to sell.",
						r -> Format.amount(r.weekVol()), r -> r.weekVol() >= CraftEngine.LOW_VOLUME ? Colors.GRAY : Colors.RED));
			}
		}
		return cols;
	}

	@Override
	protected String stamp(String search) {
		return view() + "|" + AhPrices.version() + "|" + BazaarPrices.lastFetchMillis() + "|" + Settings.tax() + "|" + offer() + "|" + Settings.ahSort
				+ "|" + Settings.hideLowVolume + "|" + Settings.hideRisky + "|" + Settings.reqs + "|" + Settings.unlocks + "|" + Format.rounded() + "|" + search;
	}

	@Override
	protected List<AhEngine.Row> build(String search) {
		AhPrices.ensure();                                   // refreshes when the list is over 5 minutes old
		var bins = AhPrices.all();
		List<AhEngine.Row> all = switch (view()) {
			case "craft" -> AhEngine.crafts(Recipes.all(), bins, BazaarPrices::get, Settings.tax(), Settings.reqs);
			case "lookup" -> AhEngine.lookup(search, bins, BazaarPrices::get, 200);
			default -> AhEngine.arb(new ArrayList<>(BazaarPrices.ids()), bins, BazaarPrices::get, Settings.tax());
		};
		List<AhEngine.Row> shown = new ArrayList<>();
		for (AhEngine.Row r : all) {
			if (!view().equals("lookup")) {
				if (!(r.profit(offer()) > 0)) continue;           // only what makes money the way you sell
				if (Settings.hideLowVolume && r.weekVol() < CraftEngine.LOW_VOLUME) continue;
				if (Settings.hideRisky && r.risk().level() == Risk.Level.HIGH) continue;
			}
			shown.add(r);
		}
		return AhEngine.sorted(shown, sortFor(view()), offer(), search);
	}

	/** The sort, if it makes sense for this view (lookup only sorts by price or name). */
	private static String sortFor(String view) {
		String s = Settings.ahSort;
		if (view.equals("lookup")) return s.equals("bin") || s.equals("name") ? s : "profit";
		if (view.equals("craft") && s.equals("vol")) return "profit";
		return s.equals("bin") && !view.equals("arb") ? "profit" : s;
	}

	@Override
	protected String waiting() {
		String w = super.waiting();
		if (w != null) return w;
		return AhPrices.loaded() ? null : "Loading auction house prices...";
	}

	@Override
	protected String waitingHint() {
		return "The lowest-BIN list downloads in the background (lb.tricked.dev). Give it a few seconds.";
	}

	@Override
	protected String name(AhEngine.Row r) {
		return (r.risk().flagged() ? "! " : "") + r.name() + (r.output() > 1 ? " x" + r.output() : "");
	}

	@Override
	protected int nameColor(AhEngine.Row r) {
		return r.risk().level() == Risk.Level.HIGH ? Colors.RED : r.risk().level() == Risk.Level.CAUTION ? Colors.ORANGE : Colors.WHITE;
	}

	@Override
	protected String tooltip(AhEngine.Row r) {
		String risk = r.risk().reasons().isEmpty() ? "" : " · ! " + r.risk().reasons().get(0);
		return switch (r.kind()) {
			case "craft" -> {
				List<String> p = new ArrayList<>();
				for (AhEngine.Part part : r.parts()) p.add(part.qty() + "x " + part.name() + " (" + (part.ah() ? "AH " : "Bazaar ") + Format.shortCoins(part.unit()) + ")");
				yield String.join(", ", p) + risk;
			}
			case "lookup" -> r.id() + (r.weekVol() >= 0 ? " · also on the Bazaar" : "") + (r.parts().isEmpty() ? "" : " · " + r.parts().size()
					+ " ingredient" + (r.parts().size() == 1 ? "" : "s") + (r.usesAh() ? ", some priced from the AH" : ""));
			default -> "Buy the AH listing at " + Format.coins(r.bin()) + " · sell offer " + Format.signedCoins(r.profit(true)) + " · insta-sell "
					+ Format.signedCoins(r.profit(false)) + " · lowest BINs can be a single listing; check it's still there" + risk;
		};
	}

	@Override
	protected void open(AhEngine.Row r) {
		if (BazaarPrices.get(r.id()) != null) minecraft.gui.setScreen(new PriceDetailScreen(this, r.id()));
	}

	@Override
	protected String itemExplanation() {
		return switch (view()) {
			case "craft" -> "What you craft. Hover for the ingredients and where each is priced; click for the result's Bazaar order book.";
			case "lookup" -> "AH items matching your search. Click one that's also on the Bazaar for its order book.";
			default -> "The item listed cheap on the AH. Hover for both profits; click for its Bazaar order book.";
		};
	}

	@Override
	protected String noun() {
		return switch (view()) { case "craft" -> "AH crafts"; case "lookup" -> "AH items"; default -> "AH listings"; };
	}

	@Override
	protected String hint() {
		String age = AhPrices.loaded() ? "AH prices " + Format.age(System.currentTimeMillis() - AhPrices.version()) + " old" : "AH prices loading";
		return switch (view()) {
			case "craft" -> "Buy the AH-only mats on the AH, the rest on the Bazaar, craft, sell on the Bazaar · " + age;
			case "lookup" -> "Type 2+ letters in Search · lowest BINs from lb.tricked.dev · " + age;
			default -> "Buy on the AH, sell on the Bazaar · " + age;
		};
	}

	@Override
	protected String emptyText() {
		return switch (view()) {
			case "craft" -> "No crafts with AH-only ingredients make money right now.";
			case "lookup" -> search().trim().length() < 2 ? "Type an item's name in Search (2+ letters) to see its lowest BIN." : "No AH items match that.";
			default -> "Nothing on the AH is below its Bazaar price right now.";
		};
	}

	// ---- sorting by clicking a column name ----

	@Override
	protected String sortKeyFor(String header) {
		return switch (header) {
			case "Profit", "Craft -> AH" -> view().equals("lookup") ? null : "profit";
			case "Return" -> "pct";
			case "Vol/wk" -> "vol";
			case "AH BIN", "Lowest BIN" -> "bin";
			default -> null;
		};
	}

	@Override
	protected String currentSort() {
		return sortFor(view());
	}

	@Override
	protected void applySort(String key) {
		Settings.ahSort = key;
		Settings.save();
	}
}
