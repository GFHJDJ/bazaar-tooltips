package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The Alerts tab: your price alerts, and boxes to add one (item, price, which price and which way). */
public class AlertsScreen extends TableScreen<PriceAlerts.Alert> {
	/** What's typed in the add boxes (kept while the game is open). */
	private static String draftItem = "", draftPrice = "";
	/** 0 = insta-buy below, 1 = insta-buy above, 2 = insta-sell below, 3 = insta-sell above. */
	private static int draftWhen = 0;
	private static final String[] WHEN = {"Insta-buy below", "Insta-buy above", "Insta-sell below", "Insta-sell above"};

	public AlertsScreen() {
		super("Alerts", "alerts");
	}

	/** Opened from an item's details: that item and its current insta-buy price are filled in. */
	public AlertsScreen(String id) {
		this();
		draftItem = SiteData.prettyName(id);
		BazaarPrices.Price p = BazaarPrices.get(id);
		if (p != null && !Double.isNaN(p.instaBuy())) {
			double v = Math.round(p.instaBuy() * 10) / 10.0;
			draftPrice = v == Math.floor(v) ? Long.toString((long) v) : Double.toString(v);
		}
	}

	private static String draftId() {
		return PriceAlerts.find(draftItem, BazaarPrices.ids());
	}

	/** The typed price ("29k", "1.2m" and "29,000" work), or NaN. */
	private static double draftValue() {
		double v = Settings.parseCoins(draftPrice);
		return v > 0 ? v : Double.NaN;
	}

	@Override
	protected List<Control> controls() {
		return List.of(
				Control.box("Item", () -> draftItem, v -> draftItem = v,
						() -> "The item to watch: type part of its name. " + (draftId() == null ? "No bazaar item matches yet." : "Matches: " + SiteData.prettyName(draftId()) + ".")),
				Control.box("Price", () -> draftPrice, v -> draftPrice = v,
						() -> "The price to watch for, per item: 29000, 29k, 1.2m all work."),
				new Control(() -> WHEN[draftWhen],
						() -> "Which price and which way. Insta-buy = what you pay to buy one now (the lowest sell offer); insta-sell = what you get "
								+ "selling one now (the top buy order). Below = alert when it drops to the price or under; above = rises to it or over. "
								+ "Right click goes back.",
						d -> draftWhen = Math.floorMod(draftWhen + d, WHEN.length)),
				new Control(AlertsScreen::addLabel,
						() -> draftId() == null ? "Type an item name first." : Double.isNaN(draftValue()) ? "Type a price first."
								: "Adds: " + SiteData.prettyName(draftId()) + ", " + WHEN[draftWhen].toLowerCase(Locale.ROOT) + " " + Format.price(draftValue()) + ".",
						AlertsScreen::addDraft));
	}

	private static String addLabel() {
		String id = draftId();
		if (id == null) return "Add: type an item";
		if (Double.isNaN(draftValue())) return "Add: type a price";
		return "Add: " + SiteData.prettyName(id);
	}

	private static void addDraft() {
		String id = draftId();
		double v = draftValue();
		if (id == null || Double.isNaN(v)) return;
		PriceAlerts.add(new PriceAlerts.Alert(id, draftWhen < 2, draftWhen % 2 == 0, v));
	}

	@Override
	protected List<Column<PriceAlerts.Alert>> columns() {
		return List.of(
				new Column<>("When", "When: which price is watched and which way it has to go.",
						a -> (a.buy() ? "Insta-buy" : "Insta-sell") + (a.below() ? " <=" : " >="), a -> Colors.GRAY),
				new Column<>("Price", "Price: the number you picked.", a -> Format.price(a.price()), a -> Colors.YELLOW),
				new Column<>("Now", "Now: that price right now.", a -> Format.price(a.now(BazaarPrices::get)), a -> a.hit(BazaarPrices::get) ? Colors.GREEN : Colors.YELLOW),
				new Column<>("Status", "Status: Waiting = not there yet. Hit = it's there (you got a chat message); it messages again only after the "
						+ "price goes back the other way and returns.",
						a -> a.hit(BazaarPrices::get) ? "Hit" : "Waiting", a -> a.hit(BazaarPrices::get) ? Colors.GREEN : Colors.GRAY));
	}

	@Override
	protected String stamp(String search) {
		return PriceAlerts.version() + "|" + BazaarPrices.lastFetchMillis() + "|" + Format.rounded() + "|" + search;
	}

	@Override
	protected List<PriceAlerts.Alert> build(String search) {
		String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		List<PriceAlerts.Alert> out = new ArrayList<>();
		for (PriceAlerts.Alert a : PriceAlerts.all()) if (SiteData.prettyName(a.id()).toLowerCase(Locale.ROOT).contains(q)) out.add(a);
		return out;
	}

	@Override
	protected String waiting() {
		return null;
	}

	@Override
	protected String name(PriceAlerts.Alert a) {
		return SiteData.prettyName(a.id());
	}

	@Override
	protected int nameColor(PriceAlerts.Alert a) {
		return a.hit(BazaarPrices::get) ? Colors.GREEN : Colors.WHITE;
	}

	@Override
	protected String tooltip(PriceAlerts.Alert a) {
		return "Alerts when " + a.condition() + " · click to delete this alert";
	}

	@Override
	protected void open(PriceAlerts.Alert a) {
		PriceAlerts.remove(a);
	}

	@Override
	protected String itemExplanation() {
		return "Your price alerts. You get a chat message (with a ding and a [/bz] link) when one's price gets there. Click one to delete it.";
	}

	@Override
	protected String noun() {
		return "alerts";
	}

	@Override
	protected String hint() {
		return "Checked every time prices refresh (about once a minute) · set one from any item's details too";
	}

	@Override
	protected String emptyText() {
		return "No alerts yet: type an item and a price above, then click Add.";
	}
}
