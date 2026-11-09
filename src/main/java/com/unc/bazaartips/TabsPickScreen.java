package com.unc.bazaartips;

import com.unc.bazaartips.CraftReport.Kind;
import com.unc.bazaartips.CraftReport.Row;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** Settings: which tabs show along the top. Click a tab to hide or show it. */
public class TabsPickScreen extends ReportScreen {
	public TabsPickScreen(Screen parent) {
		super("Tabs", parent);
	}

	@Override
	protected String heading() {
		return "Tabs shown";
	}

	@Override
	protected String bzName() {
		return null;
	}

	@Override
	protected String stamp() {
		return Settings.hiddenTabs.toString();
	}

	@Override
	protected List<Row> buildRows() {
		List<Row> rows = new ArrayList<>();
		rows.add(Row.heading("Click a tab to hide or show it", "Hidden tabs disappear from the top of the bazaar screen (the one you're on always shows)."));
		for (String[] t : BaseScreen.tabs()) {
			boolean hidden = Settings.hiddenTabs.contains(t[0]);
			rows.add(new Row(Kind.LINE, 1, t[1], Colors.WHITE, "", hidden ? "Hidden" : "Shown", hidden ? Colors.RED : Colors.GREEN,
					t[2] + (hidden ? " Hidden: click to show it." : " Click to hide it."), "tab:" + t[0]));
		}
		if (!Settings.hiddenTabs.isEmpty()) {
			rows.add(Row.gap());
			rows.add(new Row(Kind.LINE, 0, "Show them all", Colors.AQUA, "", "", 0, "Brings every hidden tab back.", "all"));
		}
		return rows;
	}

	@Override
	protected void onAction(String action) {
		if (action.equals("all")) {
			Settings.hiddenTabs.clear();
		} else {
			String k = action.substring(4);
			if (!Settings.hiddenTabs.remove(k)) {
				if (Settings.hiddenTabs.size() + 1 >= BaseScreen.tabs().length) return;   // keep at least one tab
				Settings.hiddenTabs.add(k);
			}
		}
		Settings.save();
	}
}
