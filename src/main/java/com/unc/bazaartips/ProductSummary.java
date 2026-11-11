package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the panel beside a bazaar item's page shows: prices, the flip (and its score), crafting it, selling it to an
 * NPC, and your own orders on it. No Minecraft code, so it can be tested on its own.
 */
public final class ProductSummary {
	private ProductSummary() {}

	/** One line: text on the left, an optional value on the right, colours for each. indent = small left gap. */
	public record Line(String text, int color, String value, int valueColor, boolean indent) {
		static Line head(String t) {
			return new Line(t, Colors.GOLD, "", 0, false);
		}

		static Line kv(String k, String v, int vc) {
			return new Line(k, Colors.GRAY, v, vc, true);
		}

		static Line note(String t, int c) {
			return new Line(t, c, "", 0, true);
		}
	}

	public static List<Line> build(String id) {
		List<Line> out = new ArrayList<>();
		BazaarPrices.Price p = BazaarPrices.get(id);
		if (p == null) return out;
		out.add(new Line(SiteData.prettyName(id), Colors.WHITE, "", 0, false));
		// The same numbers as the item tooltip: the top of the live order book (not Hypixel's averaged price).
		double instaBuy = p.instaBuy(), instaSell = p.instaSell();
		out.add(Line.kv("Insta-buy", Format.price(instaBuy), Colors.YELLOW));
		out.add(Line.kv("Insta-sell", Format.price(instaSell), Colors.YELLOW));
		out.add(Line.kv("Traded / week", Format.amount(Math.min(p.buyMovingWeek(), p.sellMovingWeek())), Colors.GRAY));
		Risk risk = Risk.of(p);
		if (!risk.reasons().isEmpty()) out.add(Line.note("! " + risk.reasons().get(0), risk.level() == Risk.Level.HIGH ? Colors.RED : Colors.ORANGE));

		// ---- flip ----
		out.add(Line.head("Flip"));
		List<Flipper.Flip> fl = Flipper.compute(List.of(id), BazaarPrices::get, Settings.tax());
		if (fl.isEmpty()) {
			out.add(Line.note("No margin after " + Settings.taxLabel() + " tax right now", Colors.GRAY));
		} else {
			Flipper.Flip f = fl.get(0);
			Flipper.Score s = Flipper.score(f, CoflData.move(id), CoflData.hist(id));
			out.add(Line.kv("Buy order at", Format.price(f.buyAt()), Colors.YELLOW));
			out.add(Line.kv("Sell offer at", Format.price(f.sellAt()), Colors.YELLOW));
			out.add(Line.kv("Profit each", Format.signedCoins(f.each()) + String.format(Locale.US, " (%.1f%%)", f.pct()), Colors.profit(f.each())));
			out.add(Line.kv("Trades / hr", "~" + Format.shortCoins(f.flow()), Colors.GRAY));
			out.add(Line.kv("Score", MoneyReport.scoreText(s), MoneyReport.scoreColor(s)));
		}

		// ---- craft ----
		Recipes.Recipe rec = Recipes.get(id);
		if (rec != null) {
			boolean chains = Settings.chains != Settings.Chains.EXCLUDE;
			CraftEngine e = CraftEngine.current();
			CraftEngine.Craft insta = e.craft(id, false, chains), order = e.craft(id, true, chains);
			if (insta != null || order != null) {
				out.add(Line.head("Craft it" + (rec.output() > 1 ? " (makes " + rec.output() + ")" : "")));
				for (CraftEngine.Craft c : new CraftEngine.Craft[] {insta, order}) {
					if (c == null) continue;
					String how = c.byOrder() ? "Buy orders" : "Insta-buy";
					double so = c.profitSellOffer() / c.output(), is = c.profitInstaSell() / c.output();
					out.add(Line.kv(how + ": cost", Format.price(c.cost() / c.output()) + " each", Colors.YELLOW));
					out.add(Line.kv("  Profit offer/insta", Format.signedCoins(so) + " / " + Format.signedCoins(is), Colors.profit(Math.max(so, is))));
				}
			}
		}

		// ---- NPC ----
		Double npc = NpcPrices.get(id);
		if (npc != null && npc > 0) {
			out.add(Line.head("NPC"));
			out.add(Line.kv("NPC pays", Format.price(npc), Colors.YELLOW));
			if (instaBuy > 0 && npc > instaBuy) out.add(Line.kv("Buy here, sell to NPC", Format.signedCoins(npc - instaBuy) + " each", Colors.GREEN));
		}

		// ---- your orders on it ----
		List<OrderTracker.Check> mine = new ArrayList<>();
		for (OrderTracker.Check c : OrderTracker.checkAll(BazaarPrices::get)) if (id.equals(c.order().productId())) mine.add(c);
		if (!mine.isEmpty()) {
			out.add(Line.head("Your orders"));
			for (OrderTracker.Check c : mine) {
				OrderTracker.Order o = c.order();
				boolean buy = o.type() == OrderTracker.Type.BUY;
				out.add(Line.kv((buy ? "Buy " : "Sell ") + Format.count(o.amount()) + " @ " + Format.price(o.price()), statusLabel(c), statusColor(c)));
			}
		}
		return out;
	}

	static String statusLabel(OrderTracker.Check c) {
		boolean buy = c.order().type() == OrderTracker.Type.BUY;
		return switch (c.status()) {
			case TOP -> "Top";
			case TIED -> "Tied";
			case OUTBID -> (buy ? "Outbid " : "Undercut ") + Format.price(c.best());
			case FILLED -> "Filled";
			default -> "?";
		};
	}

	static int statusColor(OrderTracker.Check c) {
		return switch (c.status()) {
			case TOP -> Colors.GREEN;
			case TIED -> Colors.YELLOW;
			case OUTBID -> Colors.RED;
			case FILLED -> Colors.AQUA;
			default -> Colors.GRAY;
		};
	}
}
