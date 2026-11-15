package com.unc.bazaartips;

import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** One bazaar item: its numbers, warnings and the live order book (the site's open price row). */
public class PriceDetailScreen extends ReportScreen {
	private final String id;

	public PriceDetailScreen(Screen parent, String id) {
		super(SiteData.prettyName(id), parent);
		this.id = id;
	}

	@Override
	protected String heading() {
		return SiteData.prettyName(id);
	}

	@Override
	protected String bzName() {
		return Recipes.name(id);
	}

	@Override
	protected void init() {
		CoflData.queueHistory(List.of(id), true);            // the chart's prices (SkyCofl, kept 6 hours)
		super.init();
	}

	@Override
	protected String stamp() {
		return BazaarPrices.lastFetchMillis() + "|" + CoflData.version() + "|" + PriceAlerts.version();
	}

	@Override
	protected double[] chartData() {
		CoflData.Hist h = CoflData.hist(id);
		return h == null || !h.status().equals("ok") ? null : h.days();
	}

	@Override
	protected String chartExplain() {
		return "The last 30 days, one bar a day: the middle of the insta-buy and insta-sell price (from SkyCofl). The gold bar is today. "
				+ "Bars start a little under the lowest day, so small moves show; read the numbers below for the real range.";
	}

	@Override
	protected void onAction(String action) {
		if (action.equals("alert")) minecraft.gui.setScreen(new AlertsScreen(id));
	}

	@Override
	protected String missing() {
		return BazaarPrices.get(id) == null ? "Not on the bazaar right now" : null;
	}

	@Override
	protected List<CraftReport.Row> buildRows() {
		BazaarPrices.Price p = BazaarPrices.get(id);
		List<CraftReport.Row> rows = new ArrayList<>();
		if (p == null) return rows;
		rows.add(new CraftReport.Row(CraftReport.Kind.NOTE, 0, id, Colors.DARK_GRAY, "", "", 0, "The item's bazaar product ID."));
		// price alert shortcut
		List<PriceAlerts.Alert> mine = PriceAlerts.forItem(id);
		rows.add(new CraftReport.Row(CraftReport.Kind.LINE, 0, mine.isEmpty() ? "Set a price alert" : mine.size() + " price alert" + (mine.size() == 1 ? "" : "s")
				+ " on this item", Colors.AQUA, "", "Alerts ->", Colors.AQUA, "Opens the Alerts tab with this item filled in: get a chat message when its price crosses a number you pick.",
				"alert"));
		// price history
		CoflData.Hist h = CoflData.hist(id);
		rows.add(CraftReport.Row.gap());
		if (h != null && h.status().equals("ok") && h.days().length > 0) {
			rows.add(CraftReport.Row.heading("Last 30 days", chartExplain()));
			for (int i = 0; i < ReportScreen.CHART_ROWS; i++) rows.add(new CraftReport.Row(CraftReport.Kind.CHART, i, "", 0, "", "", 0, ""));
			double now = (p.instaBuy() + p.instaSell()) / 2;
			rows.add(CraftReport.Row.line(1, "30-day typical (median)", "", Format.price(h.med30()), Colors.YELLOW, "The middle day's price over the last 30 days."));
			rows.add(CraftReport.Row.line(1, "30-day low / high", "", Format.price(h.lo30()) + " / " + Format.price(h.hi30()), Colors.YELLOW,
					"The cheapest and dearest day in the last 30 days."));
			if (!Double.isNaN(now) && h.med30() > 0) {
				double dev = (now / h.med30() - 1) * 100;
				rows.add(CraftReport.Row.line(1, "Now vs typical", "", String.format(java.util.Locale.US, "%+.1f%%", dev),
						Math.abs(dev) < 5 ? Colors.GRAY : dev > 0 ? Colors.ORANGE : Colors.GREEN,
						"Today's middle price compared with the 30-day typical one. Well above = maybe a spike; well below = maybe cheap."));
			}
			rows.add(CraftReport.Row.line(1, "All-time low / high", "since " + java.time.Instant.ofEpochMilli(h.since()).atZone(java.time.ZoneOffset.UTC).getYear(),
					Format.price(h.allLo()) + " / " + Format.price(h.allHi()), Colors.GRAY, "The lowest and highest prices SkyCofl has for it."));
		} else {
			boolean err = h != null && h.status().equals("err");
			rows.add(new CraftReport.Row(CraftReport.Kind.NOTE, 0, h == null ? "Loading the price history..." : err ? "Couldn't load the price history (SkyCofl)."
					: "No price history for this item.", Colors.DARK_GRAY, "", "", 0, "Price history comes from SkyCofl (sky.coflnet.com)."));
		}
		rows.add(CraftReport.Row.gap());
		CraftReport.marketRows(rows, SiteData.prettyName(id), p, true);
		return rows;
	}
}
