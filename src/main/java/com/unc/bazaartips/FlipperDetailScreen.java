package com.unc.bazaartips;

import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** One flip in full: the card, the score breakdown, a plan, how fast it fills, market stats and the live order book. */
public class FlipperDetailScreen extends ReportScreen {
	private final String id;

	public FlipperDetailScreen(Screen parent, String id) {
		super(SiteData.prettyName(id), parent);
		this.id = id;
	}

	@Override
	protected void init() {
		CoflData.ensureMovement();
		CoflData.queueHistory(List.of(id), true); // the item you opened goes first
		super.init();
	}

	@Override
	protected String heading() {
		return "Flip: " + SiteData.prettyName(id);
	}

	@Override
	protected String bzName() {
		return Recipes.name(id);
	}

	@Override
	protected String stamp() {
		return BazaarPrices.lastFetchMillis() + "|" + Settings.tax() + "|" + CoflData.version() + "|" + Settings.coins + "|" + Settings.slots;
	}

	@Override
	protected String missing() {
		return MoneyData.flip(id) == null ? "Not a profitable flip right now" : null;
	}

	@Override
	protected List<CraftReport.Row> buildRows() {
		Flipper.Flip f = MoneyData.flip(id);
		if (f == null) return List.of();
		Flipper.Score s = Flipper.score(f, CoflData.move(id), CoflData.hist(id));
		Flipper.Qty q = Flipper.qty(f, Settings.coinsBudget(), Settings.freeSlots());
		return MoneyReport.flip(f, s, q, Settings.coinsBudget(), Settings.freeSlots(), Settings.tax(), Settings.taxLabel());
	}
}
