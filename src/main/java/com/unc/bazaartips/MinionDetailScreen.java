package com.unc.bazaartips;

import com.unc.bazaartips.CraftReport.Kind;
import com.unc.bazaartips.CraftReport.Row;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** One minion: every tier's materials and cost, the running total, and everything needed to reach the top tier. */
public class MinionDetailScreen extends ReportScreen {
	private final String key;

	public MinionDetailScreen(Screen parent, String key) {
		super("Minion", parent);
		this.key = key;
	}

	private MinionEngine.Minion minion() {
		for (MinionEngine.Minion m : MoneyData.minions()) if (m.key().equals(key)) return m;
		return null;
	}

	@Override
	protected String heading() {
		MinionEngine.Minion m = minion();
		return m == null ? key : m.name();
	}

	@Override
	protected String bzName() {
		return null;
	}

	@Override
	protected String stamp() {
		return BazaarPrices.lastFetchMillis() + "";
	}

	@Override
	protected String missing() {
		return minion() == null ? "No data for this minion" : null;
	}

	private static Row mat(int indent, MinionEngine.Mat m) {
		String what = Format.count(m.qty()) + "x " + m.name();
		if (m.missing()) return Row.line(indent, what, "", "not on bazaar", Colors.RED, "There's no bazaar price for this, so it isn't counted in the cost.");
		return Row.line(indent, what, "order " + Format.coins(m.sell()), "insta " + Format.coins(m.buy()), Colors.YELLOW,
				"What these cost insta-buying (right) or with buy orders (left)." + (m.id() == null ? " A built minion counts as its own build cost." : ""));
	}

	@Override
	protected List<Row> buildRows() {
		MinionEngine.Minion m = minion();
		List<Row> rows = new ArrayList<>();
		if (m == null) return rows;
		rows.add(Row.line(0, "Build to tier " + SiteData.roman(m.maxTier()), "order " + Format.coins(m.totalOrder()), "insta " + Format.coins(m.total()),
				Colors.YELLOW, "Everything from the first tier to the top: insta-buying every material (right) or with buy orders (left)."));
		if (!m.req().isEmpty()) rows.add(new Row(Kind.NOTE, 0, "Requires " + m.req(), Colors.WHITE, "", "", 0, "What you need unlocked to craft this minion."));
		else rows.add(new Row(Kind.NOTE, 0, m.source().isEmpty() ? "No collection requirement listed" : "From " + m.source() + " · no collection",
				Colors.GRAY, "", "", 0, "Where the first tier comes from."));
		if (m.partial()) {
			rows.add(new Row(Kind.WARNING, 0, "! " + (m.source().isEmpty() ? "Tier I recipe missing" : "Tier I isn't craftable (" + m.source() + ")")
					+ ", so the cost starts at tier " + SiteData.roman(m.fromTier()), Colors.ORANGE, "", "", 0, "The total leaves out the tiers before this."));
		}
		for (MinionEngine.Tier t : m.tiers()) {
			rows.add(Row.gap());
			rows.add(Row.heading("Tier " + SiteData.roman(t.tier()) + " · insta " + Format.coins(t.insta()) + " · order " + Format.coins(t.order()),
					"What upgrading to this tier costs: insta-buying the materials, or with buy orders."));
			for (MinionEngine.Mat x : t.mats()) rows.add(mat(1, x));
			rows.add(Row.line(1, "Total to tier " + SiteData.roman(t.tier()), "order " + Format.coins(t.cumO()), "insta " + Format.coins(t.cumI()),
					Colors.GOLD, "The running total from the first tier up to and including this one."));
		}
		if (!m.tiers().isEmpty()) {
			MinionEngine.Tier last = m.tiers().get(m.tiers().size() - 1);
			rows.add(Row.gap());
			rows.add(Row.heading("Everything you need to reach tier " + SiteData.roman(last.tier()),
					"All the materials from the first tier to the top, added up: your shopping list."));
			for (MinionEngine.Mat x : last.cumMats()) rows.add(mat(1, x));
		}
		return rows;
	}
}
