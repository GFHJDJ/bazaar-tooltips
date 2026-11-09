package com.unc.bazaartips;

import com.unc.bazaartips.CraftReport.Kind;
import com.unc.bazaartips.CraftReport.Row;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One Coin advisor idea: the best amount for your coins, what to buy and sell for it, and a way to its own tab. */
public class AdvisorDetailScreen extends ReportScreen {
	private final String key;

	public AdvisorDetailScreen(Screen parent, String key) {
		super("Coin advisor", parent);
		this.key = key;
	}

	private Advisor.Option option() {
		return MoneyData.planOption(key);
	}

	@Override
	protected String heading() {
		Advisor.Option o = option();
		return o == null ? "Coin advisor" : AdvisorScreen.shortKind(o.src) + ": " + o.title;
	}

	@Override
	protected String bzName() {
		Advisor.Option o = option();
		if (o == null) return null;
		String mid = SiteData.marketId(o.itemId, BazaarPrices::get);
		return BazaarPrices.get(mid) == null ? null : Recipes.name(mid);
	}

	@Override
	protected String stamp() {
		return MoneyData.planStampNow() + "|" + Format.rounded();
	}

	@Override
	protected String missing() {
		return option() == null ? "This no longer makes a profit with your coins (or you left it out)" : null;
	}

	private static String tabName(String src) {
		return switch (src) {
			case "flip" -> "Flipper";
			case "npc", "shop" -> "NPC";
			case "forge" -> "Forge";
			case "books" -> "Books";
			default -> "Crafts";
		};
	}

	@Override
	protected List<Row> buildRows() {
		Advisor.Option o = option();
		if (o == null) return List.of();
		List<Row> rows = new ArrayList<>();
		long n = o.best.n();
		boolean slots = Settings.freeSlots() > 0;
		rows.add(Row.heading(o.kind() + " · best for your " + Format.shortCoins(Settings.coinsBudget()) + " coins" + (slots ? " & " + Settings.freeSlots() + " slots" : ""),
				"The most profitable amount for your coins" + (slots ? " and free slots" : "") + ", at today's prices."));
		rows.add(Row.line(1, "Best amount", o.limit.isEmpty() ? "" : "limit: " + o.limit, Format.count(n) + " " + Advisor.unit(o.src, n), Colors.WHITE,
				"How many: " + (o.limit.isEmpty() ? "the most profitable amount." : "stopped by " + o.limit + ".")));
		if (slots) rows.add(Row.line(1, "Slots", "of your " + Settings.freeSlots() + " free", Format.count(o.slotsUsed(n)), Colors.WHITE,
				"Inventory slots the best amount takes."));
		rows.add(Row.line(1, "Uses", "", Format.coins(o.cost), Colors.YELLOW, "Coins the best amount ties up until it's done."));
		rows.add(Row.line(1, "Profit", "after " + Settings.taxLabel() + " tax", Format.signedCoins(o.coins), Colors.profit(o.coins), "What it makes."));
		rows.add(Row.line(1, "Return", "", String.format(Locale.US, "%+.1f%%", o.pct), Colors.GRAY, "Profit as a % of the coins it uses."));
		rows.add(Row.line(1, "Time", "", Amounts.timeLabel(o.hrs), Colors.GRAY,
				"About how long it takes: orders filling at the market's real pace, forge time. Instant = insta-buy and insta-sell."));
		rows.add(Row.line(1, "Profit / hr", o.hrs > 0 && Double.isFinite(o.hrs) ? "" : "instant: counted as ~5 min", Format.signedCoins(o.perHour),
				Colors.profit(o.perHour), "Profit divided by the time."));
		rows.add(Row.gap());

		// what to buy and sell: the same lines its own tab shows, at this amount
		List<Row> how = switch (o.src) {
			case "flip" -> MoneyReport.flip(o.flip, Flipper.score(o.flip, CoflData.move(o.itemId), CoflData.hist(o.itemId)),
					new Flipper.Qty(n, o.best.why()), Settings.coinsBudget(), Settings.freeSlots(), Settings.tax(), Settings.taxLabel());
			case "craft" -> CraftReport.shoppingRows(CraftEngine.current(), o.craft, Settings.moneySellOffer ? "bestSO" : "bestIS",
					Settings.coinsBudget(), Settings.freeSlots());
			default -> MoneyReport.money(o.src.equals("shop") ? "shop" : o.src, o.row, n, Settings.taxLabel());
		};
		for (Row r : how) {
			if (r.kind() == Kind.GAP) break;
			rows.add(r);
		}
		rows.add(Row.gap());
		rows.add(new Row(Kind.LINE, 0, "Open in the " + tabName(o.src) + " tab ->", Colors.AQUA, "", "", 0,
				"Opens this in its own tab's details: everything that tab shows about it.", "open"));
		rows.add(new Row(Kind.LINE, 0, "Leave this item out of the advisor", Colors.RED, "", "", 0,
				"Stops suggesting this item. Bring it back with the Leave out button on the advisor.", "exclude"));
		return rows;
	}

	@Override
	protected void onAction(String action) {
		Advisor.Option o = option();
		if (o == null) return;
		if (action.equals("exclude")) {
			Settings.planEx.add(o.itemId);
			Settings.save();
			onClose();
			return;
		}
		Screen s = switch (o.src) {
			case "flip" -> new FlipperDetailScreen(this, o.itemId);
			case "craft" -> {
				Settings.buy = Settings.moneyBuyOrder ? Settings.Buy.ORDER : Settings.Buy.INSTA;   // the Craft tab bought the same way
				Settings.save();
				yield new FlipDetailScreen(this, o.itemId);
			}
			default -> new MoneyDetailScreen(this, o.src.equals("shop") ? "shop" : o.src, o.row.key);
		};
		minecraft.gui.setScreen(s);
	}
}
