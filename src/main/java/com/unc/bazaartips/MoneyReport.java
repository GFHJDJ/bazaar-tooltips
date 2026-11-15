package com.unc.bazaartips;

import com.unc.bazaartips.CraftReport.Kind;
import com.unc.bazaartips.CraftReport.Row;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Everything the website's Flipper, NPC and Forge cards and their open panels show, as labeled rows for the
 * details screens. Each row says what it means when you hover it. No Minecraft code, so it can be tested on its own.
 */
public final class MoneyReport {
	private MoneyReport() {}

	private static Row note(String text, int color, String explain) {
		return new Row(Kind.NOTE, 0, text, color, "", "", 0, explain);
	}

	// ---------------------------------------------------------------- Flipper

	/** "72 · Good*" (the * = price history not in yet). */
	public static String scoreText(Flipper.Score s) {
		return s.score() + " · " + s.grade() + (s.partial() ? "*" : "");
	}

	public static int scoreColor(Flipper.Score s) {
		return s.score() >= 75 ? Colors.GREEN : s.score() >= 60 ? 0xFF99E26B : s.score() >= 45 ? Colors.YELLOW
				: s.score() >= 30 ? Colors.ORANGE : Colors.RED;
	}

	public static final String SCORE_EXPLAIN = "Flip score (0-100): rates margin, volume, both sides filling evenly, competition, "
			+ "today's price move and price history, minus points for risk flags. * = history from SkyCofl not loaded yet "
			+ "(the score is rescaled over what's known). A guide, not a guarantee.";

	public static List<Row> flip(Flipper.Flip f, Flipper.Score s, Flipper.Qty q, double coins, int slots, double tax, String taxLabel) {
		List<Row> rows = new ArrayList<>();
		BazaarPrices.Price it = f.it();
		long n = q.n();

		// ---- plan, first: with your coins & slots it's your shopping list ----
		boolean sized = coins > 0 || slots > 0;
		rows.add(Row.heading(sized ? "Shopping list for your coins & slots" : "Plan (about an hour of trading)",
				"How many to flip and what it costs. Enter your coins and free slots on the list to size it for you."));
		if (n > 0) {
			int st = SiteData.stackOf(f.id(), false);
			rows.add(Row.line(1, "Flip", "", Format.count(n) + "x", Colors.WHITE, "How many to flip: " + (q.why().equals("coins") ? "as many as your coins buy"
					: q.why().equals("slots") ? "as many as fit in your free inventory slots" : "about one hour of trading on the slower side") + "."));
			rows.add(Row.line(1, "Buy order cost", Format.count(n) + " x " + Format.price(f.buyAt()), Format.coins(n * f.buyAt()), Colors.YELLOW,
					"What the buy orders cost in total."));
			rows.add(Row.line(1, "Sell offers get you", "after " + taxLabel + " tax", Format.coins(n * f.sellAt() * (1 - tax)), Colors.GREEN,
					"What selling them all by sell offer pays, after tax."));
			rows.add(Row.line(1, "Profit", "", Format.signedCoins(n * f.each()), Colors.profit(n * f.each()), "Sell offers minus buy orders."));
			String lim = q.why().equals("coins") ? "Limited by your coins."
					: q.why().equals("slots") ? "Limited by your free inventory slots (" + ((n + st - 1) / st) + " of " + slots + ")."
					: "About one hour of trading on the slower side" + (coins > 0 ? "" : "; enter your coins and free slots at the top to size it") + ".";
			rows.add(note(lim, Colors.DARK_GRAY, "What stopped the amount from being bigger."));
		} else {
			rows.add(note(q.why().equals("slots") ? "Your " + slots + " free inventory slots can't hold one." : "Your coins don't cover one at " + Format.price(f.buyAt()) + ".",
					Colors.RED, "The amount came out as zero with your coins and slots."));
		}
		double hrs = n > 0 && f.flow() > 0 ? n / f.flow() : -1;
		rows.add(Row.line(1, "Buy orders fill", "when people insta-sell", "~" + Format.coins(f.buyFill()) + "/hr", Colors.WHITE,
				"Items insta-sold per hour (last 7 days / 168): how fast buy orders like yours get filled."));
		rows.add(Row.line(1, "Sell offers fill", "when people insta-buy", "~" + Format.coins(f.sellFill()) + "/hr", Colors.WHITE,
				"Items insta-bought per hour (last 7 days / 168): how fast sell offers like yours sell."));
		if (hrs >= 0) {
			rows.add(Row.line(1, "Time per side", "if you caught every trade", hrs < 1 ? Math.max(1, Math.round(hrs * 60)) + " min"
					: String.format(Locale.US, "%.1f hr", hrs), Colors.WHITE, "Your amount divided by trades per hour on the slower side."));
		}
		rows.add(note("You're up against " + Format.count(it.buyOrderCount()) + " buy orders and " + Format.count(it.sellOfferCount())
				+ " sell offers, so expect it to take longer, and recheck both prices before placing: they move.", Colors.DARK_GRAY,
				"Other players' orders compete with yours, and the top prices change every minute."));
		if (sized && n > 0 && q.why().equals("cap")) {
			rows.add(note("Capped at about one hour of trading so you aren't stuck holding more than the market takes.", Colors.DARK_GRAY,
					"Your coins and slots would allow more, but more than an hour's trading is risky to hold."));
		}
		rows.add(Row.gap());

		// ---- the card ----
		rows.add(Row.line(0, "Buy order at", "0.1 above the top buy order", Format.price(f.buyAt()), Colors.YELLOW,
				"Place a buy order at this price: 0.1 coins above the current top buy order, so yours fills first."));
		rows.add(Row.line(0, "Sell offer at", "0.1 below the lowest sell offer", Format.price(f.sellAt()), Colors.YELLOW,
				"List a sell offer at this price: 0.1 coins below the current lowest sell offer, so yours sells first."));
		rows.add(Row.line(0, "Profit each", "after " + taxLabel + " tax", Format.signedCoins(f.each()), Colors.profit(f.each()),
				"Sell offer price minus tax, minus the buy order price: what each item makes."));
		rows.add(Row.line(0, "Margin", "", String.format(Locale.US, "%.1f%%", f.pct()), Colors.profit(f.each()),
				"Profit each as a % of the buy order price."));
		rows.add(Row.line(0, "Profit / hr", "if you caught every trade", Format.signedCoins(f.perHour()), Colors.profit(f.perHour()),
				"Profit each x trades per hour on the slower side. Real results are lower, since others are flipping too."));
		rows.add(Row.line(0, "Trades / hr", "the slower side", "~" + Format.coins(f.flow()), Colors.WHITE,
				"How many items trade per hour on the slower side: buy orders fill when people insta-sell, sell offers fill when people insta-buy."));
		rows.add(Row.line(0, "Open orders", Format.count(it.buyOrderCount()) + " buy orders · " + Format.count(it.sellOfferCount()) + " sell offers",
				Format.count(it.buyOrderCount() + it.sellOfferCount()), Colors.WHITE,
				"Everyone else's orders on this item: the people you compete with to fill first."));
		for (String w : f.risk().reasons()) {
			rows.add(new Row(Kind.WARNING, 0, "! " + w, f.risk().level() == Risk.Level.HIGH ? Colors.RED : Colors.ORANGE, "", "", 0,
					"A warning sign the price may be manipulated: flipping it can leave you holding items nobody buys at that price."));
		}

		// ---- score breakdown ----
		rows.add(Row.gap());
		rows.add(new Row(Kind.LINE, 0, "Flip score", Colors.GOLD, "", scoreText(s), scoreColor(s), SCORE_EXPLAIN));
		for (Flipper.Part p : s.parts()) {
			String v = p.of() > 0 ? (p.pts() >= 0 ? "+" : "-") + Math.abs(p.pts()) + " / " + p.of() : "-" + Math.abs(p.pts());
			int col = p.pts() < 0 ? Colors.RED : p.of() > 0 && p.pts() >= p.of() * 0.6 ? Colors.GREEN : Colors.WHITE;
			rows.add(Row.line(1, p.label(), p.why(), v, col, partExplain(p.label())));
		}
		String hs = s.histState();
		if (!"ok".equals(hs)) {
			rows.add(note("none".equals(hs) ? "SkyCofl has no price history for this item."
							: "err".equals(hs) ? "Couldn't load price history right now; it'll retry in a few minutes." : "Loading price history...",
					Colors.DARK_GRAY, "Price history comes from SkyCofl, one item at a time (about one a second), so it doesn't overload their site."));
		}
		rows.add(note("Points are a guide, not a guarantee. Missing parts are left out and the score is rescaled.", Colors.DARK_GRAY,
				"The score is your points divided by the most you could get from the parts that are known, x 100."));

		CraftReport.marketRows(rows, f.name(), it, true);
		rows.add(Row.gap());
		rows.add(note("Price history & 24h moves provided by SkyCofl (sky.coflnet.com).", Colors.DARK_GRAY,
				"The score's 'Price today', 'Vs. its usual price' and 'All-time range' parts use SkyCofl's free bazaar data."));
		return rows;
	}

	private static String partExplain(String label) {
		return switch (label) {
			case "Margin after tax" -> "Best between 5% and 15%: thinner is easily wiped out, much wider usually means a thin or odd market.";
			case "Trading volume" -> "More trades per hour on the slower side = your orders fill sooner.";
			case "Both sides fill evenly" -> "If one side fills much slower, you end up waiting with coins or items stuck.";
			case "Competition" -> "Open orders compared with trades per hour: fewer rivals per trade is better.";
			case "Price today" -> "The 24h price change from SkyCofl. Big moves mean prices may shift before you fill.";
			case "Vs. its usual price" -> "The current price against its 30-day typical price from SkyCofl's history.";
			case "All-time range" -> "Where today's price sits between the lowest and highest it's ever been.";
			case "Risk flags" -> "Points taken off for warning signs of manipulation.";
			default -> "";
		};
	}

	// ---------------------------------------------------------------- NPC & Forge

	/** The site's "best: buy 640 · 10 slots -> +12.3k" chip text, or null without coins/slots. */
	public static String bestText(MoneyEngine.Row r) {
		Amounts.Best b = r.best;
		if (b == null) return null;
		if (b.n() > 0 && !(b.profit() > 0)) return "loses coins at any amount";
		if (b.n() > 0) {
			long used = r.slotsUsed(b.n());
			return "best: " + r.qtyLabel.toLowerCase(Locale.ROOT) + " " + Format.count(b.n()) + (used > 0 ? " · " + used + (used == 1 ? " slot" : " slots") : "")
					+ " -> " + Format.signedCoins(b.profit());
		}
		if (b.why().equals("slots")) {
			long need = r.slotsUsed(1);
			return (need > 0 ? "one needs " + need + " inventory slots" : "doesn't fit") + "; you have " + Settings.freeSlots() + " free (Slots box)";
		}
		return "can't afford one";
	}

	/** Why the best amount stopped where it did. */
	public static String bestLimit(MoneyEngine.Row r) {
		Amounts.Best b = r.best;
		if (b == null) return "";
		return switch (b.why()) {
			case "slots" -> "fills your " + Settings.freeSlots() + " free inventory slots";
			case "coins" -> "all your coins";
			case "price" -> "more would cost more than it earns";
			case "cap" -> r.hardWhy().isEmpty() ? "" : "limit: " + r.hardWhy();
			default -> "";
		};
	}

	private static String statExplain(String label, boolean forge, boolean npcBz) {
		return switch (label) {
			case "Cost" -> "What the materials (and any coins) for one forge cost, bought the way the Buy button says.";
			case "Sells for" -> forge ? "What the forged result sells for (all of it), the way the Sell button says, after tax."
					: "What everything from one purchase sells for, the way the Sell button says (after bazaar tax; AH = lowest BIN before fees).";
			case "Profit" -> "Sells for minus cost.";
			case "Profit / hr" -> "Profit per forge divided by the forge time (at least 30 seconds).";
			case "Buy for" -> "What one costs on the bazaar, the way the Buy button says (Hypixel's averaged price).";
			case "NPC pays" -> "What any NPC pays for one (from Hypixel's item list). Selling to NPCs has no tax.";
			case "Profit each" -> "NPC pays minus the bazaar price.";
			case "Return" -> npcBz ? "Profit each as a % of what one costs." : "Profit as a % of what the NPC charges.";
			case "NPC cost" -> "What the NPC charges for one purchase: coins plus any materials (priced on the bazaar or AH).";
			case "Costs" -> "How much of the currency one purchase takes.";
			case "Net value" -> "What one purchase sells for, minus any extra coins or items it costs.";
			case "Total cost" -> "The pet's lowest BIN on the AH, plus Kat's coins and the materials.";
			case "Profit / day" -> "Profit divided by how long Kat takes (at least a minute).";
			default -> label.endsWith(" cost") ? "What two of the lower-level book cost, bought the way the Buy button says."
					: label.endsWith(" sells for") ? "What the higher-level book sells for, the way the Sell button says, after tax."
					: label.startsWith("Coins / ") ? "Net value divided by how much of the currency it costs: coins you get per one spent."
					: "";
		};
	}

	/**
	 * A Forge or NPC card and its panel. tab = "forge", "npc" (Bazaar -> NPC) or "shop" (NPC shop).
	 * qty = the calculator's amount.
	 */
	public static List<Row> money(String tab, MoneyEngine.Row r, long qty, String taxLabel) {
		List<Row> rows = new ArrayList<>();
		boolean forge = tab.equals("forge"), npcBz = tab.equals("npc");

		// ---- the calculator, first: at your best amount it's your shopping list ----
		MoneyEngine.Calc calc = r.calc(qty);
		if (calc != null) {
			boolean atBest = r.best != null && r.best.n() == qty && qty > 0;
			rows.add(Row.heading((atBest ? "Shopping list for your coins & slots: " : "How many? ") + r.qtyLabel + " " + Format.count(qty) + " " + r.qtyUnit,
					(atBest ? "The best amount for your coins and free slots, and exactly what to buy for it. " : "")
							+ "Change the amount above. Bazaar insta-buys and insta-sells walk the live order book, so big amounts show the real average price."));
			for (MoneyEngine.CalcLine l : calc.lines()) {
				String ex = l.what().equals("Inventory space") ? "How many inventory slots this takes, and whether it fits in your free slots."
						: l.what().equals("Wait") ? "How long the forge takes for this many (rounds = more forges than you have slots)."
						: l.what().startsWith("  incl.") ? "One material, already counted in the line above: how many to buy and what they cost."
						: l.selling() ? "What selling them pays (Bazaar after tax; insta-sells walk the order book)."
						: "What buying them costs (insta-buys walk the order book, so big amounts cost more each).";
				rows.add(Row.line(1, l.what(), l.how(), l.coins(), l.selling() ? Colors.GREEN : l.what().equals("Inventory space")
						&& l.how().contains("more than your") ? Colors.RED : l.what().startsWith("  incl.") ? Colors.GRAY : Colors.YELLOW, ex));
			}
			rows.add(Row.line(1, "Profit", "", Format.signedCoins(calc.profit()), Colors.profit(calc.profit()), "What you get selling minus what you pay."));
			if (!calc.footnote().isEmpty()) rows.add(note(calc.footnote(), Colors.ORANGE, "A limit the calculator can't see."));
			rows.add(Row.gap());
		}
		rows.add(note(r.flag, Colors.AQUA, forge ? "How long one forge takes." : npcBz ? "Buy it on the Bazaar, sell it to an NPC."
				: tab.equals("books") ? "Combine two books in an anvil (costs XP levels, not coins)."
				: tab.equals("cur") ? "What one purchase costs in the currency."
				: tab.equals("kat") ? "How long Kat takes to upgrade the pet."
				: "Buy it from an NPC shop, sell it on the " + (r.flag.endsWith("AH") ? "Auction House." : "Bazaar.")));
		if (r.slowWeek >= 0) {
			rows.add(note("! Slow seller: only " + Format.count(r.slowWeek) + " trade on the Bazaar a week", Colors.ORANGE,
					"Few people buy this, so selling more than a couple can take a long time, and insta-selling a few walks down to worse buy orders."));
		}
		if (!r.req.isEmpty()) {
			rows.add(note("Requires " + r.req, Colors.WHITE, "What you need to forge this (Heart of the Mountain tier and/or a collection)."));
		}
		for (String m : r.meta) {
			String ex = m.startsWith("sells on") ? "Where the result sells: the Bazaar (instant) or the Auction House (lowest BIN, may take a while)."
					: m.contains("for sale now") ? "Items listed in sell offers right now: how many you could buy at once."
					: m.contains("insta-bought") ? "Items insta-bought in the last 7 days."
					: m.contains("bought / week") ? "How many of the lower-level book were insta-bought in the last 7 days (how easy it is to buy two)."
					: m.contains("sold / week") ? "How many of the higher-level book were insta-sold in the last 7 days (how easy it is to sell)."
					: m.startsWith("plus ") ? "Extra coins or items the NPC wants on top of the currency."
					: m.startsWith("NPCs pay for up to") ? "NPCs pay at most 500M coins a day per profile (Hypixel's limit), so this is the most of it you can sell to them a day."
					: m.startsWith("Buy limit:") ? "NPC shops sell at most 640 of an item a day (Hypixel's limit for most shop items; resets at midnight GMT)."
					: m.startsWith("buy ") && m.contains(" · sell ") ? "The pet's lowest BIN on the AH now, and the upgraded pet's (before AH fees)."
					: m.equals("needs materials") ? "Kat wants materials as well as coins; they're in the cost table below."
					: m.startsWith("Seasonal") || m.startsWith("Mayor") || m.startsWith("Situational")
							? "This NPC isn't always around (from the Hypixel SkyBlock Wiki). Change which NPCs show with the NPCs button."
					: "The NPC and where to find them (area and x, y, z). Locations from SkyHanni's island maps and the Hypixel SkyBlock Wiki; 'unconfirmed' = not on current maps.";
			rows.add(note(m, Colors.GRAY, ex));
		}
		for (String[] st : r.stats) {
			int col = st[0].startsWith("Profit") || st[0].equals("Return") ? (st[1].startsWith("-") ? Colors.RED : Colors.GREEN) : Colors.YELLOW;
			rows.add(Row.line(0, st[0], "", st[1], col, statExplain(st[0], forge, npcBz)));
		}
		String best = bestText(r);
		if (best != null) {
			String lim = bestLimit(r);
			rows.add(note(best + (lim.isEmpty() ? "" : " (" + lim + ")"), r.best.n() > 0 && r.best.profit() > 0 ? Colors.GREEN : Colors.RED,
					"The most profitable amount per trip for your coins and free slots, walking the live order book (prices get worse as you buy or sell more)."));
		}

		// ---- materials / cost table ----
		if (!r.parts.isEmpty()) {
			rows.add(Row.gap());
			rows.add(Row.heading(r.partsLabel + (forge ? " (one forge)" : tab.equals("cur") || tab.equals("kat") ? "" : " (one purchase)"),
					"Everything one " + (forge ? "forge" : "purchase") + " costs: each material's price and where it's priced (Bazaar, or AH lowest BIN)."));
			for (MoneyEngine.Part p : r.parts) {
				if (p.id() == null) {
					rows.add(Row.line(1, p.label(), "", Format.coins(p.total()), Colors.YELLOW, "Coins paid directly."));
				} else {
					rows.add(Row.line(1, Format.count(p.qty()) + "x " + p.label(), Format.price(p.unit()) + " each · " + p.src(),
							Format.coins(p.total()), Colors.YELLOW, "Priced on the " + (p.src().equals("AH") ? "Auction House (lowest BIN)"
									: "Bazaar (the way the Buy button says)") + "; the right number is the total for that many."));
				}
			}
		}
		if (!r.note.isEmpty()) {
			rows.add(Row.gap());
			rows.add(note(r.note, Colors.GRAY, forge ? "How this forge works out." : tab.equals("cur") ? "Where to buy it and what it's worth."
					: tab.equals("kat") ? "How this upgrade works out." : "How to do this flip."));
		}
		if (r.market != null) CraftReport.marketRows(rows, r.name, r.market, true);
		return rows;
	}

	// ---------------------------------------------------------------- per-item buy method (details screens)

	/**
	 * "How you buy each item": one clickable line per item (action "buy:<id>") showing Insta-buy or Buy order,
	 * starting from the list's Buy setting, plus "Reset" (action "reset") once anything is switched.
	 * items = {id, name} pairs.
	 */
	public static List<Row> buyToggleRows(List<String[]> items, java.util.function.Predicate<String> orderNow, boolean listOrder) {
		List<Row> rows = new ArrayList<>();
		if (items.isEmpty()) return rows;
		String listLabel = listOrder ? "Buy order" : "Insta-buy";
		rows.add(Row.heading("How you buy each item (click to switch)",
				"Each item can be insta-bought or bought with a buy order, separately. They start at the list's Buy setting ("
						+ listLabel + "); every number below uses your choices. Kept until you close the game."));
		boolean changed = false;
		for (String[] it : items) {
			boolean order = orderNow.test(it[0]);
			boolean differs = order != listOrder;
			changed |= differs;
			rows.add(new Row(Kind.LINE, 1, it[1], Colors.WHITE, differs ? "changed" : "list setting", order ? "Buy order" : "Insta-buy",
					order ? Colors.GOLD : Colors.AQUA,
					"Click to switch. Insta-buy = buy now from the cheapest sell offers (big amounts walk the order book). "
							+ "Buy order = 0.1 above the top buy order: cheaper, but you wait for it to fill.", "buy:" + it[0]));
		}
		if (changed) {
			rows.add(new Row(Kind.LINE, 1, "Put every item back to the list's setting (" + listLabel + ")", Colors.GRAY, "", "Reset",
					Colors.WHITE, "Undoes your switches for this item's details.", "reset"));
		}
		rows.add(Row.gap());
		return rows;
	}
}
