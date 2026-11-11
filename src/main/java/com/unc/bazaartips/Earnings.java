package com.unc.bazaartips;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What you actually made on the bazaar. Every time Hypixel tells you in chat that you bought or sold something
 * (an insta-buy, an insta-sell, or claiming a buy order / sell offer), the trade is written down. Profit is worked
 * out per item: what you sold it for minus what those same items cost you (average cost of the ones you bought here).
 * Trades are kept in a file, one line each, so they survive restarts. No Minecraft code, so it can be tested on its own.
 */
public final class Earnings {
	private Earnings() {}

	public enum Kind { BUY, SELL }

	/** One trade: coins is the total for the whole amount, exactly as Hypixel's message says it. instant = insta-buy / insta-sell. */
	public record Trade(long at, Kind kind, String name, long qty, double coins, boolean instant) {}

	/**
	 * One item over a period. profit = coins from the ones you sold minus what those ones cost you (only for items
	 * both bought and sold here; matched = how many). otherSales = coins from selling more than you'd bought here
	 * (things you farmed, crafted or already had). holding / holdingCost = bought here and not sold here yet (as of now).
	 */
	public record Row(String name, long bought, double spent, long sold, double earned, double profit, long matched,
			double otherSales, long holding, double holdingCost) {}

	/** A period's totals. from = when counting starts; firstAt = the first trade in the period (0 = none). */
	public record Summary(long from, long firstAt, int trades, double spent, double earned, double profit, double otherSales,
			double holdingCost, long holdingItems, List<Row> rows) {
		/** Coins in minus coins out. Goes negative while you're still holding what you bought. */
		public double net() {
			return earned - spent;
		}

		/** Profit per hour since the period's first trade (NaN before the first trade, or in its first minute). */
		public double perHour(long now) {
			long ms = firstAt == 0 ? 0 : now - firstAt;
			return ms < 60_000 ? Double.NaN : profit / (ms / 3_600_000.0);
		}
	}

	// ---- Hypixel's bazaar chat messages ----

	private static final String NUM = "([\\d,]+(?:\\.\\d+)?)";
	private static final Pattern INSTA_BUY = Pattern.compile("^\\[Bazaar] Bought " + NUM + "x (.+?) for " + NUM + " coins?!$");
	private static final Pattern INSTA_SELL = Pattern.compile("^\\[Bazaar] Sold " + NUM + "x (.+?) for " + NUM + " coins?!$");
	private static final Pattern CLAIM_BUY = Pattern.compile("^\\[Bazaar] Claimed " + NUM + "x (.+?) worth " + NUM + " coins? bought for .+$");
	private static final Pattern CLAIM_SELL = Pattern.compile("^\\[Bazaar] Claimed " + NUM + " coins? from selling " + NUM + "x (.+?) at .+$");

	private static double num(String s) {
		try {
			return Double.parseDouble(s.replace(",", ""));
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	/** The trade a chat line reports, or null if it isn't one. */
	public static Trade parse(String message, long now) {
		String msg = OrderTracker.strip(message);
		if (!msg.startsWith("[Bazaar]")) return null;
		Matcher m;
		Trade t = null;
		if ((m = INSTA_BUY.matcher(msg)).matches()) t = new Trade(now, Kind.BUY, m.group(2).trim(), Math.round(num(m.group(1))), num(m.group(3)), true);
		else if ((m = INSTA_SELL.matcher(msg)).matches()) t = new Trade(now, Kind.SELL, m.group(2).trim(), Math.round(num(m.group(1))), num(m.group(3)), true);
		else if ((m = CLAIM_BUY.matcher(msg)).matches()) t = new Trade(now, Kind.BUY, m.group(2).trim(), Math.round(num(m.group(1))), num(m.group(3)), false);
		else if ((m = CLAIM_SELL.matcher(msg)).matches()) t = new Trade(now, Kind.SELL, m.group(3).trim(), Math.round(num(m.group(2))), num(m.group(1)), false);
		if (t == null || t.qty() <= 0 || !(t.coins() >= 0)) return null;
		return t;
	}

	// ---- the trades ----

	private static final List<Trade> trades = new ArrayList<>();
	private static volatile long version = 0;
	private static Path file;
	/** When the game started: the "This session" period counts from here. */
	private static long sessionStart = System.currentTimeMillis();

	/** Goes up whenever a trade is added or the list is cleared, so the screen knows to redraw. */
	public static long version() {
		return version;
	}

	public static long sessionStart() {
		return sessionStart;
	}

	public static synchronized List<Trade> trades() {
		return List.copyOf(trades);
	}

	/** Reads one chat line; if it's a bazaar trade, writes it down. Returns true if it was one. */
	public static synchronized boolean onChat(String message, long now) {
		Trade t = parse(message, now);
		if (t == null) return false;
		trades.add(t);
		version++;
		append(t);
		return true;
	}

	/** Forgets every trade (and deletes the file). */
	public static synchronized void clear() {
		trades.clear();
		version++;
		if (file == null) return;
		try {
			Files.deleteIfExists(file);
		} catch (Exception e) {
			System.getLogger("bazaartips").log(System.Logger.Level.WARNING, "Couldn't delete saved trades: " + e);
		}
	}

	// ---- the sums ----

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
	}

	/**
	 * Totals and per-item rows for trades at or after `from`. Earlier trades still count towards what an item cost
	 * you (so selling today what you bought yesterday shows today's profit correctly), but not towards the totals.
	 */
	public static Summary summarize(List<Trade> all, long from) {
		final class Acc {
			String name;
			double held, heldCost;                    // bought here and not sold yet: how many, and what they cost
			long bought, sold, matched;
			double spent, earned, profit, other;
			boolean inPeriod;
		}
		Map<String, Acc> accs = new LinkedHashMap<>();
		int count = 0;
		long firstAt = 0;
		for (Trade t : all) {
			Acc a = accs.computeIfAbsent(key(t.name()), k -> new Acc());
			a.name = t.name();
			boolean in = t.at() >= from;
			if (in) {
				a.inPeriod = true;
				count++;
				if (firstAt == 0 || t.at() < firstAt) firstAt = t.at();
			}
			if (t.kind() == Kind.BUY) {
				a.held += t.qty();
				a.heldCost += t.coins();
				if (in) {
					a.bought += t.qty();
					a.spent += t.coins();
				}
			} else {
				double match = Math.min(t.qty(), a.held);
				double cost = a.held > 0 ? a.heldCost * match / a.held : 0;
				double matchedCoins = t.coins() * match / t.qty();
				a.held -= match;
				a.heldCost -= cost;
				if (a.held < 1e-9) {                 // nothing left: don't let rounding leave a few stray coins behind
					a.held = 0;
					a.heldCost = 0;
				}
				if (in) {
					a.sold += t.qty();
					a.earned += t.coins();
					a.matched += Math.round(match);
					a.profit += matchedCoins - cost;
					a.other += t.coins() - matchedCoins;
				}
			}
		}
		List<Row> rows = new ArrayList<>();
		double spent = 0, earned = 0, profit = 0, other = 0, holdCost = 0;
		long holdItems = 0;
		for (Acc a : accs.values()) {
			if (!a.inPeriod) continue;
			rows.add(new Row(a.name, a.bought, a.spent, a.sold, a.earned, a.profit, a.matched, a.other, Math.round(a.held), a.heldCost));
			spent += a.spent;
			earned += a.earned;
			profit += a.profit;
			other += a.other;
			holdCost += a.heldCost;
			holdItems += Math.round(a.held);
		}
		return new Summary(from, firstAt, count, spent, earned, profit, other, holdCost, holdItems, List.copyOf(rows));
	}

	// ---- periods ----

	public static final String[][] PERIODS = {{"session", "This session"}, {"today", "Today"}, {"week", "Last 7 days"}, {"all", "All time"}};

	public static String periodLabel(String period) {
		for (String[] p : PERIODS) if (p[0].equals(period)) return p[1];
		return PERIODS[0][1];
	}

	/** When a period starts: this game session, midnight today (your computer's clock), 7 days ago, or the beginning. */
	public static long periodStart(String period, long now, long session) {
		return switch (period) {
			case "today" -> java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
					.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
			case "week" -> now - 7L * 86_400_000L;
			case "all" -> 0;
			default -> session;
		};
	}

	/** The summary for a period, as of now. */
	public static Summary summary(String period, long now) {
		return summarize(trades(), periodStart(period, now, sessionStart));
	}

	/** Sorts rows: "profit" (most first), "spent", "earned", "bought", "sold", "holding" (biggest first), or "name". */
	public static List<Row> sorted(List<Row> rows, String sort) {
		List<Row> out = new ArrayList<>(rows);
		java.util.Comparator<Row> c = switch (sort) {
			case "spent" -> (a, b) -> Double.compare(b.spent(), a.spent());
			case "earned" -> (a, b) -> Double.compare(b.earned(), a.earned());
			case "bought" -> (a, b) -> Long.compare(b.bought(), a.bought());
			case "sold" -> (a, b) -> Long.compare(b.sold(), a.sold());
			case "holding" -> (a, b) -> Double.compare(b.holdingCost(), a.holdingCost());
			case "name" -> java.util.Comparator.comparing(r -> r.name().toLowerCase(Locale.ROOT));
			default -> (a, b) -> Double.compare(b.profit(), a.profit());
		};
		out.sort(c);
		return out;
	}

	// ---- saved between sessions: one trade per line, added to the end (the file is never rewritten) ----

	private static String line(Trade t) {
		JsonArray a = new JsonArray();
		a.add(t.at());
		a.add(t.kind() == Kind.BUY ? "B" : "S");
		a.add(t.name());
		a.add(t.qty());
		a.add(t.coins());
		a.add(t.instant());
		return a.toString();
	}

	static Trade fromLine(String line) {
		try {
			JsonArray a = JsonParser.parseString(line).getAsJsonArray();
			Trade t = new Trade(a.get(0).getAsLong(), a.get(1).getAsString().equals("B") ? Kind.BUY : Kind.SELL, a.get(2).getAsString(),
					a.get(3).getAsLong(), a.get(4).getAsDouble(), a.get(5).getAsBoolean());
			return t.qty() > 0 && t.coins() >= 0 ? t : null;
		} catch (Exception e) {
			return null;                             // a damaged line (the game closed mid-write): skip it
		}
	}

	/** Loads saved trades (call once at startup). The session starts now. */
	public static synchronized void load(Path saveFile) {
		file = saveFile;
		sessionStart = System.currentTimeMillis();
		trades.clear();
		version++;
		try {
			if (!Files.exists(file)) return;
			for (String l : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				if (l.isBlank()) continue;
				Trade t = fromLine(l);
				if (t != null) trades.add(t);
			}
			trades.sort((a, b) -> Long.compare(a.at(), b.at()));
		} catch (Exception e) {
			System.getLogger("bazaartips").log(System.Logger.Level.WARNING, "Couldn't read saved trades: " + e);
		}
	}

	private static void append(Trade t) {
		if (file == null) return;
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, line(t) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Exception e) {
			System.getLogger("bazaartips").log(System.Logger.Level.WARNING, "Couldn't save a trade: " + e);
		}
	}

	/** For tests. */
	static synchronized void resetForTests(long session) {
		trades.clear();
		file = null;
		sessionStart = session;
		version++;
	}
}
