package com.unc.bazaartips;

import com.unc.bazaartips.CraftReport.Kind;
import com.unc.bazaartips.CraftReport.Row;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** One minion's coins per hour: every drop, how often to empty it, and how long it takes to pay for itself. */
public class ProfitDetailScreen extends ReportScreen {
	private final String name;

	public ProfitDetailScreen(Screen parent, String name) {
		super(name, parent);
		this.name = name;
	}

	private MinionEngine.Profit profit() {
		for (MinionEngine.Profit p : MoneyData.profits()) if (p.name().equals(name)) return p;
		return null;
	}

	@Override
	protected String heading() {
		MinionEngine.Profit p = profit();
		return name + (p != null ? " · tier " + SiteData.roman(p.tier()) : "");
	}

	@Override
	protected String bzName() {
		return null;
	}

	@Override
	protected String stamp() {
		return MoneyData.profitStampNow() + "|" + Settings.profitChest;
	}

	@Override
	protected String missing() {
		return profit() == null ? "No data for this minion" : null;
	}

	private static String emptyHint(Double h) {
		if (h == null || h == 0) return "";
		if (h < 24) return " · empty ~" + Math.max(1, Math.round(24 / h)) + "x/day";
		if (h <= 24 * 60) return " · empty every ~" + Amounts.hours(h);
		return " · almost never";
	}

	@Override
	protected List<Row> buildRows() {
		MinionEngine.Profit p = profit();
		List<Row> rows = new ArrayList<>();
		if (p == null) return rows;
		if (p.usedMax()) rows.add(new Row(Kind.WARNING, 0, "! No tier " + SiteData.roman(Settings.profitTier) + " for this minion, so this is tier "
				+ SiteData.roman(p.tier()) + " (its top tier)", Colors.ORANGE, "", "", 0, "This minion doesn't go as high as the tier you picked."));
		rows.add(new Row(Kind.NOTE, 0, (Settings.profitAuto ? "Best setup: " : "Setup: ") + p.combo(), Colors.AQUA, "", "", 0,
				Settings.profitAuto ? "The fuel and upgrades that make the most coins per hour for this minion (permanent fuels only)."
						: "The fuel and upgrades you picked on the list."));
		rows.add(Row.line(0, "Per hour", "", Format.coins(p.total()), Colors.GREEN, "Coins per hour from all its drops, sold the way 'Sell to' says."));
		rows.add(Row.line(0, "Per day", "", Format.coins(p.total() * 24), Colors.GREEN, "Coins per hour x 24, while it's running and not full."));
		rows.add(Row.gap());
		rows.add(Row.heading("Each drop · acts every " + Amounts.trim1(p.actionSec()) + "s (a drop every " + Amounts.trim1(p.perDrop()) + "s)",
				"Minions drop something every second action. Each line: how many per hour, the price each and where it sells, and coins per hour."));
		boolean npcStar = false;
		for (MinionEngine.PRow r : p.rows()) {
			npcStar |= r.mk().equals("NPC*");
			rows.add(Row.line(1, r.name() + (r.bonus().isEmpty() ? "" : " (" + r.bonus() + ")"),
					Format.coins(r.perHour()) + "/hr @ " + Format.price(r.price()) + " " + r.mk(), Format.coins(r.sub()) + "/hr", Colors.GREEN,
					"Per hour x price each = coins per hour. " + switch (r.mk()) {
						case "Bazaar" -> "Sold on the Bazaar after tax.";
						case "NPC*" -> "Not on the Bazaar, so priced at what an NPC pays.";
						default -> "Sold to an NPC" + (Settings.profitHopper.equals("No hopper") ? "." : " through your hopper.");
					} + (r.bonus().isEmpty() ? "" : " Comes from the " + r.bonus() + ".")));
		}
		rows.add(Row.line(1, "Total", Format.coins(p.total() * 24) + "/day", Format.coins(p.total()) + "/hr", Colors.GOLD, "All drops added up."));
		if (npcStar) rows.add(new Row(Kind.NOTE, 1, "NPC* = not on Bazaar, priced at NPC instead", Colors.DARK_GRAY, "", "", 0, "What NPC* means."));
		if (p.storeItems() != null && p.stacksPerHour() > 0) {
			rows.add(Row.gap());
			rows.add(Row.heading("How often to empty it · " + Amounts.trim1(Math.round(p.stacksPerHour() * 100) / 100.0) + " stacks/hr · holds "
							+ Format.count(p.storeItems()) + " items (" + Format.count(p.storeItems() / 64.0) + " slots)",
					"How fast it fills: stacks made per hour (after compactors) vs. its own storage at this tier, plus a storage chest."));
			for (String[] c : MinionEngine.CHESTS) {
				Double h = p.fillHours(Integer.parseInt(c[2]));
				boolean mine = c[0].equals(Settings.profitChest);
				rows.add(Row.line(1, (mine ? "> " : "") + c[1] + (c[2].equals("0") ? "" : " (+" + c[2] + " slots)"), "",
						"full in " + (h == null ? "-" : ProfitScreen.fill(h)) + emptyHint(h), mine ? Colors.GOLD : Colors.WHITE,
						"How long until it's full with this chest next to it." + (mine ? " '>' = the chest you picked on the list." : "")));
			}
			if (!Settings.profitHopper.equals("No hopper")) rows.add(new Row(Kind.NOTE, 1, "With your hopper, items are sold automatically once storage "
					+ "is full, so you don't have to empty it (at the hopper's lower NPC rate).", Colors.DARK_GRAY, "", "", 0, "What the hopper does."));
		}
		rows.add(Row.gap());
		String pi = Amounts.hours(p.paybackInsta() == null ? 0 : p.paybackInsta()), po = Amounts.hours(p.paybackOrder() == null ? 0 : p.paybackOrder());
		if (pi != null || po != null) {
			rows.add(Row.heading("Pays back its build cost (to this tier), at this rate",
					"Build cost from the Minions tab divided by coins per hour."));
			rows.add(Row.line(1, "Insta-buy build cost", "", pi == null ? "-" : pi, Colors.WHITE, "If you insta-bought every material."));
			rows.add(Row.line(1, "Buy-order build cost", "", po == null ? "-" : po, Colors.WHITE, "If you bought every material with buy orders."));
		} else {
			rows.add(new Row(Kind.NOTE, 0, "Can't estimate payback: this minion's build cost isn't fully priceable at this tier", Colors.DARK_GRAY,
					"", "", 0, "Some of its materials have no bazaar price."));
		}
		return rows;
	}
}
