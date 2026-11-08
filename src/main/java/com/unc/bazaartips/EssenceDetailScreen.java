package com.unc.bazaartips;

import com.unc.bazaartips.CraftReport.Kind;
import com.unc.bazaartips.CraftReport.Row;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** One essence flip: what it turns into, what that's worth, and the live cheapest listing (with a button to open it). */
public class EssenceDetailScreen extends ReportScreen {
	private final String id;

	public EssenceDetailScreen(Screen parent, String id) {
		super(SiteData.prettyName(id), parent);
		this.id = id;
	}

	@Override
	protected void init() {
		CoflData.queueBin(List.of(id), true); // the one you opened goes first
		super.init();
	}

	@Override
	protected String heading() {
		EssenceEngine.Ess e = MoneyData.essence(id);
		return e == null ? SiteData.prettyName(id) : e.name();
	}

	@Override
	protected String bzName() {
		return null;
	}

	@Override
	protected String stamp() {
		return MoneyData.essenceStampNow();
	}

	@Override
	protected String missing() {
		return MoneyData.essence(id) == null ? "Not listed on the AH right now (or can't be priced with these settings)" : null;
	}

	@Override
	protected void onAction(String action) {
		if (action.startsWith("auction:") && minecraft.player != null) {
			minecraft.player.connection.sendCommand("viewauction " + action.substring(8));
			minecraft.gui.setScreen(null);
		}
	}

	@Override
	protected List<Row> buildRows() {
		EssenceEngine.Ess e = MoneyData.essence(id);
		List<Row> rows = new ArrayList<>();
		if (e == null) return rows;
		rows.add(new Row(Kind.NOTE, 0, e.sacrifice() ? "Sacrifice (Draconic Altar)" : "Salvage", Colors.AQUA, "", "", 0,
				e.sacrifice() ? "Dragon Essence comes from sacrificing at the Draconic Altar." : "Salvage it to get what's listed below."));
		if (!e.tier().isEmpty()) rows.add(new Row(Kind.NOTE, 0, SiteData.prettyName(e.tier()), Colors.GRAY, "", "", 0, "The item's rarity."));
		rows.add(Row.line(0, "AH lowest BIN", "", Format.coins(e.cost()), Colors.YELLOW, "What it costs to buy (the cheapest Buy-It-Now listing)."));
		rows.add(Row.line(0, "Essence value", "", Format.coins(e.value()), Colors.YELLOW, "What everything it turns into sells for."));
		rows.add(Row.line(0, "Profit", "", Format.signedCoins(e.profit()), Colors.profit(e.profit()), "Value minus the AH price. AH fees aren't counted."));
		rows.add(Row.line(0, "Return", "", MoneyEngine.pctStr(e.pct()), Colors.profit(e.profit()), "Profit as a % of the AH price."));
		rows.add(Row.gap());
		rows.add(Row.heading("What it turns into", "Each output: how many, the price each and where it sells, and the total."));
		for (EssenceEngine.Val v : e.vals()) {
			rows.add(Row.line(1, Format.count(v.o().amount()) + "x " + v.o().label(), Format.price(v.each()) + " each · " + v.where(),
					Format.coins(v.total()), Colors.GREEN, v.where().equals("Bazaar") ? "Sold on the Bazaar " + (Settings.essenceSellOffer ? "by sell offer" : "by insta-sell")
							+ " after tax." : "Not on the Bazaar, so priced at the AH lowest BIN (before AH fees)."));
		}
		rows.add(Row.line(1, "Total value", "vs. " + Format.coins(e.cost()) + " to buy", Format.coins(e.value()), Colors.GOLD, "All outputs added up."));
		rows.add(Row.gap());
		EssenceEngine.BinCheck c = e.check();
		if (c != null) {
			rows.add(Row.heading("The listing right now", "Checked live with SkyCofl (re-checked every couple of minutes while you look)."));
			if (e.outlier()) rows.add(new Row(Kind.WARNING, 1, "! One listing is up for just " + Format.coins(c.lowest()) + ", far below the next ("
					+ Format.coins(c.second()) + "). Listings like that are usually mistakes that get bought in seconds, so the profit uses the next listing.",
					Colors.ORANGE, "", "", 0, "Why the AH price above isn't the very cheapest."));
			rows.add(Row.line(1, "Cheapest listing", c.second() > 0 ? "next cheapest " + Format.coins(c.second()) : "", Format.coins(c.lowest()),
					Colors.YELLOW, "The cheapest Buy-It-Now right now, and the next one."));
			if (!c.uuid().isEmpty()) {
				rows.add(new Row(Kind.LINE, 1, "Open the cheapest auction in game", Colors.WHITE, "", "/viewauction", Colors.AQUA,
						"Click to close this and run /viewauction for that listing, so you can buy it.", "auction:" + c.uuid()));
			}
		} else {
			rows.add(new Row(Kind.NOTE, 0, "Checking that this is still listed...", Colors.GRAY, "", "", 0,
					"Asking SkyCofl for the live cheapest listing (one item about every second)."));
		}
		rows.add(Row.gap());
		rows.add(new Row(Kind.NOTE, 0, (e.sacrifice() ? "Dragon Essence comes from sacrificing at the Draconic Altar, which can also drop bonus items at random; "
				+ "those aren't counted. " : "") + "The lowest BIN can be a different version of the item (stars, reforge, enchants), so check the listing before "
				+ "you buy. Upgraded items may salvage for more than shown.", Colors.GRAY, "", "", 0, "Things to watch out for."));
		return rows;
	}
}
