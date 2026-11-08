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
	protected String stamp() {
		return BazaarPrices.lastFetchMillis() + "";
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
		CraftReport.marketRows(rows, SiteData.prettyName(id), p, true);
		return rows;
	}
}
