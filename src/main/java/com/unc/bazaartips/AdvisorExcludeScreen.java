package com.unc.bazaartips;

import com.unc.bazaartips.CraftReport.Kind;
import com.unc.bazaartips.CraftReport.Row;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** What the Coin advisor leaves out: whole kinds of ideas, and single items. Click a line to switch it. */
public class AdvisorExcludeScreen extends ReportScreen {
	public AdvisorExcludeScreen(Screen parent) {
		super("Leave out", parent);
	}

	@Override
	protected String heading() {
		return "Coin advisor: leave out";
	}

	@Override
	protected String bzName() {
		return null;
	}

	@Override
	protected String stamp() {
		return Settings.planExSrc + "|" + Settings.planEx;
	}

	static String itemName(String id) {
		return id.startsWith("ENCHANTMENT_") ? SiteData.prettyName(id) : Recipes.name(id);
	}

	@Override
	protected List<Row> buildRows() {
		List<Row> rows = new ArrayList<>();
		rows.add(Row.heading("Kinds of ideas (click to switch)", "Leave out a whole kind: the advisor stops suggesting any of it."));
		for (var e : Advisor.SRC.entrySet()) {
			boolean out = Settings.planExSrc.contains(e.getKey());
			rows.add(new Row(Kind.LINE, 1, e.getValue(), Colors.WHITE, "", out ? "Left out" : "Shown", out ? Colors.RED : Colors.GREEN,
					e.getValue() + ": " + (out ? "left out of the advisor. Click to show it again." : "suggested by the advisor. Click to leave it out."),
					"src:" + e.getKey()));
		}
		rows.add(Row.gap());
		rows.add(Row.heading("Items you left out (click to bring back)",
				"Single items you left out from an idea's details (Leave this item out of the advisor)."));
		if (Settings.planEx.isEmpty()) {
			rows.add(new Row(Kind.NOTE, 1, "None yet. Open an idea on the advisor and click \"Leave this item out\".", Colors.DARK_GRAY, "", "", 0,
					"Nothing left out yet."));
		} else {
			for (String id : Settings.planEx) {
				rows.add(new Row(Kind.LINE, 1, itemName(id), Colors.WHITE, "", "Bring back", Colors.AQUA,
						itemName(id) + " is left out of the advisor. Click to bring it back.", "item:" + id));
			}
			rows.add(new Row(Kind.LINE, 0, "Bring all back", Colors.AQUA, "", "", 0, "Clears the list of left-out items.", "clear"));
		}
		return rows;
	}

	@Override
	protected void onAction(String action) {
		if (action.startsWith("src:")) {
			String k = action.substring(4);
			if (!Settings.planExSrc.remove(k)) Settings.planExSrc.add(k);
		} else if (action.startsWith("item:")) {
			Settings.planEx.remove(action.substring(5));
		} else if (action.equals("clear")) {
			Settings.planEx.clear();
		}
		Settings.save();
	}
}
