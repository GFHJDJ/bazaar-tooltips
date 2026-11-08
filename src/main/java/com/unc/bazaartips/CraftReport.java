package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything the website's craft card and its open panel show, as labeled rows for the details screen.
 * Each row has an explanation shown when you hover it. No Minecraft code, so it can be tested on its own.
 */
public final class CraftReport {
	public enum Kind { HEADING, LINE, WARNING, NOTE, GAP }

	/**
	 * One row. text = the label (left), note = extra detail after it (dark gray), value = the number (right).
	 * indent is in steps (each step is a few pixels on screen).
	 */
	public record Row(Kind kind, int indent, String text, int color, String note, String value, int valueColor, String explain, String action) {
		/** A row that does nothing when clicked. */
		public Row(Kind kind, int indent, String text, int color, String note, String value, int valueColor, String explain) {
			this(kind, indent, text, color, note, value, valueColor, explain, "");
		}

		static Row heading(String text, String explain) {
			return new Row(Kind.HEADING, 0, text, Colors.GOLD, "", "", 0, explain);
		}

		static Row line(int indent, String text, String note, String value, int valueColor, String explain) {
			return new Row(Kind.LINE, indent, text, Colors.GRAY, note, value, valueColor, explain);
		}

		static Row gap() {
			return new Row(Kind.GAP, 0, "", 0, "", "", 0, "");
		}
	}

	private CraftReport() {}

	public static List<Row> build(CraftEngine e, CraftEngine.Craft c, long qty, String sortKey, double coins, int slots) {
		List<Row> rows = new ArrayList<>();
		String buyHow = c.mixed() ? "your own mix of insta-buy and buy orders (set per item at the top)"
				: c.byOrder() ? "with buy orders, 0.1 above the top buy order (cheaper, but you wait for them to fill)"
				: "by insta-buying (the cheapest sell offer)";

		// ---- tags (the card's meta line) ----
		if (c.usesChain()) {
			rows.add(new Row(Kind.NOTE, 0, "Chained craft", Colors.AQUA, "", "", 0,
					"At least one ingredient is cheaper to craft from its own parts than to buy, so it's crafted. Ingredients marked 'crafted' show the parts."));
		}
		if (c.compactor()) {
			rows.add(new Row(Kind.NOTE, 0, "A Personal Compactor can make this", Colors.AQUA, "", "", 0,
					"A Personal Compactor turns the ingredient into this for you automatically (the Super Compactor 3000 list, plus Flawed, Fine and Flawless gemstones)."));
		}
		if (!c.req().isEmpty()) rows.add(reqRow("Requires: ", c.req(), "What you need unlocked to craft this."));
		if (!c.chainReq().isEmpty()) rows.add(reqRow("Chain needs: ", c.chainReq(),
				"Unlocks needed for the parts you'd craft yourself in the chain (not already on this recipe)."));
		if (c.breakEven() > 0) {
			rows.add(Row.line(0, "Break-even sell price each", "", Format.price(c.breakEven()), Colors.YELLOW,
					"Sell each item for at least this (after " + e.taxLabel() + " tax) and you don't lose coins. The site's 'sell >= X ea' tag."));
		}
		if (c.instaWins()) {
			rows.add(new Row(Kind.NOTE, 0, "Insta-sell wins", Colors.GREEN, "", "", 0,
					"Insta-selling makes more than a sell offer right now (buy orders are close to or above the lowest offer)."));
		}

		// ---- manipulation warnings ----
		for (String w : c.warnings()) {
			rows.add(new Row(Kind.WARNING, 0, "! " + w, c.risk() == Risk.Level.HIGH ? Colors.RED : Colors.ORANGE, "", "", 0,
					"Market-manipulation warning: a very wide spread, very few orders, or one order controlling the book. "
							+ "It flags suspicious patterns, not proven manipulation; a quiet market looks the same. Be careful either way."));
		}

		// ---- the three big numbers ----
		rows.add(Row.gap());
		rows.add(Row.line(0, "Craft cost", "1 craft", Format.coins(c.cost()), Colors.YELLOW,
				"What the ingredients for 1 craft cost, bought " + buyHow + "."));
		rows.add(Row.line(0, "Profit · sell offer", "1 craft", Format.signedCoins(c.profitSellOffer()), Colors.profit(c.profitSellOffer()),
				"What 1 craft earns if you list the result 0.1 below the lowest sell offer (" + Format.price(c.sellEach())
						+ " each) and wait for buyers, after " + e.taxLabel() + " tax."));
		rows.add(Row.line(0, "Profit · insta-sell", "1 craft", Format.signedCoins(c.profitInstaSell()), Colors.profit(c.profitInstaSell()),
				"What 1 craft earns if you insta-sell the result into the top buy order (" + Format.price(c.instaEach())
						+ " each), after " + e.taxLabel() + " tax."));

		// ---- ingredients ----
		rows.add(Row.gap());
		rows.add(Row.heading("Ingredients (" + c.ingredients().size() + ")",
				"What 1 craft uses. '@' is the price each, 'max' the most you could pay each and still profit selling by sell offer."));
		for (CraftEngine.Ingredient i : c.ingredients()) {
			boolean ok = i.maxSO() > i.unit();
			rows.add(new Row(Kind.LINE, 1, i.qty() + "x " + i.name() + (i.risky() ? " !" : ""), i.risky() ? Colors.ORANGE : Colors.WHITE,
					"@ " + Format.price(i.unit()) + (i.viaCraft() ? " crafted" : c.mixed() ? (c.orderFor(i.id()) ? " order" : " insta") : ""),
					"max " + (i.maxSO() > 0 ? Format.price(i.maxSO()) : "none"), ok ? Colors.GREEN : Colors.RED,
					i.name() + ": 1 craft needs " + i.qty() + ". "
							+ (i.viaCraft() ? "Cheaper to craft than to buy, at " + Format.price(i.unit()) + " each. "
									: (c.orderFor(i.id()) ? "Buy order" : "Insta-buy") + " price " + Format.price(i.unit()) + " each. ")
							+ "Max: you'd still profit paying up to " + (i.maxSO() > 0 ? Format.price(i.maxSO()) : "nothing")
							+ " each selling by sell offer, or " + (i.maxIS() > 0 ? Format.price(i.maxIS()) : "nothing")
							+ " by insta-sell (other ingredients at today's prices). Green = today's price is under it."
							+ (i.risky() ? " ! = this ingredient's market looks suspicious." : "")));
		}

		// ---- the planner for your chosen amount (planCalc) ----
		rows.add(Row.gap());
		long makes = c.output() * qty;
		Map<String, Double> leaf = CraftEngine.leaf(c, qty);
		int matSlots = 0;
		for (double q : leaf.values()) matSlots += (int) Math.ceil(q / 64);
		rows.add(Row.heading("Plan for " + Format.count(qty) + " craft" + (qty == 1 ? "" : "s"),
				"Change the amount with the -/+ buttons or the box at the top."));
		rows.add(Row.line(1, "Makes", "", Format.count(makes) + (makes == 1 ? " item" : " items"), Colors.WHITE,
				Format.count(qty) + " crafts x " + c.output() + " per craft."));
		rows.add(Row.line(1, "You need", "", matSlots + " material slot" + (matSlots == 1 ? "" : "s"), Colors.WHITE,
				"Inventory slots the materials take (64 per slot), counting what you'd really buy for crafted parts."));
		for (CraftEngine.Ingredient i : c.ingredients()) chainRows(rows, i.asPart(), (double) i.qty() * qty, 0, i, c);
		rows.add(Row.line(1, "Sell each at", Format.price(c.sellEach()) + " sell offer · " + Format.price(c.instaEach()) + " insta-sell",
				"", 0, "Sell offer: 0.1 below the lowest sell offer. Insta-sell: the top buy order. Both before tax."));
		rows.add(Row.line(1, "Total cost", "", Format.coins(c.cost() * qty), Colors.YELLOW, "Craft cost x " + Format.count(qty) + "."));
		rows.add(Row.line(1, "Profit · sell offer", "", Format.signedCoins(c.profitSellOffer() * qty), Colors.profit(c.profitSellOffer()),
				"Profit per craft by sell offer x " + Format.count(qty) + ", at today's prices (big amounts move prices; see Best below)."));
		rows.add(Row.line(1, "Profit · insta-sell", "", Format.signedCoins(c.profitInstaSell() * qty), Colors.profit(c.profitInstaSell()),
				"Profit per craft by insta-sell x " + Format.count(qty) + ", at today's prices (big amounts move prices; see Best below)."));

		// ---- order-book depth (depthNote) ----
		CraftEngine.Depth d = e.depth(c);
		if (d != null) {
			rows.add(Row.line(0, "Insta-flip depth", d.crafts() < 1 ? "not profitable to insta-flip now" : "≈ " + Format.signedCoins(d.profit()) + " total",
					d.crafts() < 1 ? "0" : Format.count(d.crafts()) + " craft" + (d.crafts() == 1 ? "" : "s"), d.crafts() < 1 ? Colors.RED : Colors.WHITE,
					"How many crafts you could do right now by insta-buying every ingredient and insta-selling the result, "
							+ "before the order book runs out of profitable prices."));
		}
		CraftEngine.OrderDepth od = e.orderDepth(c);
		if (od != null) {
			String wait = od.crafts() < 1 ? "" : Amounts.timeLabel(od.hours());
			rows.add(Row.line(0, "Buy-order depth", od.crafts() < 1 ? "not profitable even with buy orders now"
							: "≈ " + Format.signedCoins(od.profit()) + " total · orders fill in ~" + wait,
					od.crafts() < 1 ? "0" : Format.count(od.crafts()) + " craft" + (od.crafts() == 1 ? "" : "s"), od.crafts() < 1 ? Colors.RED : Colors.WHITE,
					"How many crafts you could do by buying every ingredient with buy orders (0.1 above the top buy order, "
							+ Format.price(od.costEach()) + " per craft; the price doesn't climb as you buy more) and insta-selling the result, "
							+ "before today's buy orders for it run out of profitable prices."
							+ (od.crafts() < 1 ? "" : " Your buy orders wait to fill: about " + wait + " at the rate people insta-sell "
									+ (od.slowest().isEmpty() ? "the ingredients" : od.slowest() + " (the slowest ingredient)")
									+ ", if you got every one of those trades. Others compete, so expect longer.")));
		}

		// ---- your coins and slots (budgetNote, best rows, shopping list, slotsNote) ----
		if (coins > 0 || slots > 0) {
			rows.add(Row.gap());
			rows.add(Row.heading("For your " + (coins > 0 ? Format.coins(coins) + " coins" : "")
					+ (coins > 0 && slots > 0 ? " and " : "") + (slots > 0 ? slots + " free slots" : ""),
					"Set your coins and free slots at the top of the list screen."));
			if (coins > 0 && c.cost() > 0) {
				long n = (long) Math.floor(coins / c.cost());
				rows.add(Row.line(1, "Budget", n < 1 ? "not enough: one craft costs " + Format.coins(c.cost()) : Format.coins(coins - n * c.cost()) + " left over",
						n < 1 ? "0" : "buys up to " + Format.count(n), n < 1 ? Colors.RED : Colors.WHITE,
						"How many crafts your coins cover at today's craft cost, ignoring that prices rise as you buy."));
			}
			for (CraftEngine.Sell s : List.of(CraftEngine.Sell.OFFER, CraftEngine.Sell.INSTA)) {
				CraftEngine.Best b = e.best(c, s, coins, slots);
				String lbl = s == CraftEngine.Sell.OFFER ? "Best · sell offer" : "Best · insta-sell";
				if (b != null) {
					String why = switch (b.why()) {
						case "coins" -> "all your coins";
						case "slots" -> "fills your slots";
						case "price" -> "more would cost more than it earns";
						default -> "limit: " + b.sizing().hardWhy();
					};
					rows.add(Row.line(1, lbl, "craft " + Format.count(b.n()) + " (" + why + ")" + (slots > 0 ? " · " + b.sizing().slotsUsed(b.n()) + " slots" : ""),
							Format.signedCoins(b.profit()), Colors.GREEN,
							"The most profitable number of crafts for your coins and slots if you "
									+ (s == CraftEngine.Sell.OFFER ? "sell by sell offer" : "insta-sell")
									+ ", walking the live order book one craft at a time (prices get worse as you buy and sell more)."));
				} else {
					String bl = e.blocker(c, s);
					rows.add(Row.line(1, lbl, bl != null ? bl : "no profitable amount at today's order-book prices", "none", Colors.RED,
							"Not profitable this way right now, or it can't be done even once with what's in the order book."));
				}
			}
			CraftEngine.ShopList sl = e.shopList(c, sortKey, coins, slots);
			if (sl != null) {
				rows.add(Row.heading("Shopping list · best amount: " + Format.count(sl.crafts()) + " craft" + (sl.crafts() == 1 ? "" : "s")
								+ ", " + (sl.sell() == CraftEngine.Sell.OFFER ? "sell offer" : "insta-sell"),
						"Exactly what to buy (and at what price) to make the best amount, and what you'd sell it for. Prices are the live order book right now"
								+ (sl.usesOrders() ? "; orders fill over time and others may outbid you" : "") + "."));
				for (CraftEngine.ShopLine l : sl.lines()) {
					rows.add(Row.line(1, l.what(), l.how(), Double.isNaN(l.coins()) ? "none" : Format.coins(l.coins()),
							l.selling() ? Colors.GREEN : Colors.YELLOW, l.what() + ": " + l.how() + "."));
				}
				rows.add(Row.line(1, "Profit", "", Format.signedCoins(sl.profit()), Colors.profit(sl.profit()),
						"What you get selling, minus what you spend buying."));
			}
			if (slots > 0) {
				int n = CraftEngine.maxCraftsForSlots(c, slots);
				rows.add(Row.line(1, "Slots", n < 1 ? "won't fit: one craft's materials need " + CraftEngine.slotsForCrafts(c, 1) + " slots"
								: "materials use " + CraftEngine.slotsForCrafts(c, n) + " of " + slots + " slots",
						n < 1 ? "0" : "fits " + Format.count(n), n < 1 ? Colors.RED : Colors.WHITE,
						"How many crafts' materials fit in your free slots (64 per slot)."));
			}
		}

		// ---- the item's market (statGrid) ----
		BazaarPrices.Price p = e.price(c.key());
		if (p != null) marketRows(rows, c.name(), p, false);
		return rows;
	}

	/**
	 * The item's own bazaar numbers (the site's statGrid), and with book = true its risk flags and the live
	 * order book (priceDetail), level by level.
	 */
	public static void marketRows(List<Row> rows, String name, BazaarPrices.Price p, boolean book) {
		rows.add(Row.gap());
		rows.add(Row.heading(name + " · market", "The item's own bazaar numbers, from Hypixel's summary (quick_status)."));
		double spread = p.quickBuy() - p.quickSell();
		double pct = p.quickSell() > 0 ? spread / p.quickSell() * 100 : 0;
		rows.add(Row.line(1, "Insta-buy", "", Format.price(p.quickBuy()), Colors.YELLOW, "Hypixel's averaged price to buy one instantly."));
		rows.add(Row.line(1, "Insta-sell", "", Format.price(p.quickSell()), Colors.YELLOW, "Hypixel's averaged price you get selling one instantly."));
		rows.add(Row.line(1, "Spread", "", Format.signedCoins(spread), Colors.profit(spread), "Insta-buy minus insta-sell: what a flipper could make per item before tax."));
		rows.add(Row.line(1, "Margin", "", String.format(Locale.US, "%.1f%%", pct), Colors.profit(spread), "The spread as a % of the insta-sell price."));
		rows.add(Row.line(1, "Sell offers", "open orders", Format.count(p.sellOfferCount()), Colors.WHITE, "How many sell offers are open (people waiting to sell)."));
		rows.add(Row.line(1, "Buy orders", "open orders", Format.count(p.buyOrderCount()), Colors.WHITE, "How many buy orders are open (people waiting to buy)."));
		rows.add(Row.line(1, "Items in sell offers", "", Format.amount(p.itemsInSellOffers()), Colors.WHITE, "Total items listed for sale in all sell offers."));
		rows.add(Row.line(1, "Items in buy orders", "", Format.amount(p.itemsInBuyOrders()), Colors.WHITE, "Total items wanted by all buy orders."));
		rows.add(Row.line(1, "Insta-bought / week", "", Format.amount(p.buyMovingWeek()), Colors.WHITE, "Items bought instantly in the last 7 days."));
		rows.add(Row.line(1, "Insta-sold / week", "", Format.amount(p.sellMovingWeek()), Colors.WHITE, "Items sold instantly in the last 7 days."));
		if (!book) return;
		Risk risk = Risk.of(p);
		for (String w : risk.reasons()) {
			rows.add(new Row(Kind.WARNING, 1, "! " + w, risk.level() == Risk.Level.HIGH ? Colors.RED : Colors.ORANGE, "", "", 0,
					"A warning sign the price may be manipulated or easy to move. Be careful with big amounts."));
		}
		bookSide(rows, "Sell offers · you buy from the lowest", p.sellOffers(),
				"People selling: insta-buying takes the cheapest first, then the next level up.");
		bookSide(rows, "Buy orders · you sell to the highest", p.buyOrders(),
				"People buying: insta-selling fills the highest first, then the next level down.");
	}

	private static void bookSide(List<Row> rows, String title, List<BazaarPrices.Level> levels, String explain) {
		rows.add(Row.gap());
		rows.add(Row.heading("Order book · " + title + " (" + levels.size() + " levels)",
				explain + " Each line: price per item (right), how many items, and how many orders at that price."));
		if (levels.isEmpty()) {
			rows.add(new Row(Kind.NOTE, 1, "no open orders", Colors.DARK_GRAY, "", "", 0, "Nobody has an order on this side right now."));
			return;
		}
		for (BazaarPrices.Level l : levels) {
			rows.add(Row.line(1, Format.amount(l.amount()) + " items", Format.count(l.orders()) + " order" + (l.orders() == 1 ? "" : "s"),
					Format.price(l.price()), Colors.YELLOW, "Price per item on the right. " + explain));
		}
	}

	private static Row reqRow(String label, String req, String explain) {
		boolean locked = Recipes.hasUnlocks() && !Recipes.reqMet(req);
		return new Row(Kind.NOTE, 0, label + req + (locked ? " (not unlocked)" : ""), locked ? Colors.RED : Colors.WHITE, "", "", 0,
				explain + (locked ? " Red: your loaded unlocks say you don't have this yet." : ""));
	}

	/** An ingredient line in the plan; crafted ones list their own parts underneath (the site's chainRows). */
	private static void chainRows(List<Row> rows, CraftEngine.Part item, double totalQty, int depth, CraftEngine.Ingredient top, CraftEngine.Craft c) {
		String bought = c.orderFor(item.id()) ? "buy order" : "insta-buy";
		Recipes.Recipe rec = item.viaCraft() ? Recipes.get(item.id()) : null;
		String note = item.viaCraft() ? "crafted, not bought" : bought + " at " + Format.price(item.unit()) + " ea";
		String explain = Format.count(totalQty) + "x " + item.name() + ": " + note + "."
				+ (depth == 0 && top != null ? " Profit up to " + (top.maxSO() > 0 ? Format.price(top.maxSO()) : "none") + " each (sell offer) / "
						+ (top.maxIS() > 0 ? Format.price(top.maxIS()) : "none") + " (insta-sell): the most you could pay and still profit." : "");
		rows.add(new Row(Kind.LINE, 2 + depth, Format.count(totalQty) + "x " + item.name(), Colors.WHITE, note,
				Format.coins(totalQty * item.unit()), Colors.YELLOW, explain));
		if (rec != null && item.parts() != null) {
			double crafts = totalQty / rec.output();
			for (CraftEngine.Part sub : item.parts()) chainRows(rows, sub, sub.qty() * crafts, depth + 1, null, c);
		}
	}
}
