package com.unc.bazaartips;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Your bazaar orders, read from Hypixel's "Your Bazaar Orders" / "Co-op Bazaar Orders" screen (Manage Orders), and
 * checked against the live order book every time prices refresh: is your buy order still the highest, is your sell
 * offer still the lowest? Remembered between game sessions. No Minecraft code, so it can be tested on its own.
 */
public final class OrderTracker {
	private OrderTracker() {}

	public enum Type { BUY, SELL }

	/** TOP = yours is the best price; TIED = someone else has the same price; OUTBID = someone beat it; FILLED = nothing left to fill. */
	public enum Status { TOP, TIED, OUTBID, FILLED, UNKNOWN }

	/** One of your orders. productId is null when the name couldn't be matched to a bazaar product. */
	public record Order(Type type, String name, String productId, long amount, long filled, boolean full, double price, int claimable, long seenAt) {
		String key() {
			return type + "|" + name + "|" + price + "|" + amount;
		}
	}

	/** An order checked against the market: best = the top buy order (for a buy order) or lowest sell offer (for a sell offer) right now. */
	public record Check(Order order, Status status, double best, int ordersAtBest) {}

	// ---- reading the Manage Orders screen (patterns as SkyHanni reads them) ----

	private static final Pattern NAME = Pattern.compile("^(BUY|SELL) (.+)$");
	private static final Pattern AMOUNT = Pattern.compile("(?:Order|Offer) amount: ([\\d,]+)x");
	private static final Pattern FILLED = Pattern.compile("Filled: ([\\d,.kKmM]+)/[\\d,.kKmM]+ (\\S+)");
	private static final Pattern PRICE = Pattern.compile("Price per unit: ([\\d,.]+) coins");
	private static final Pattern CLAIM = Pattern.compile("You have ([\\d,]+) items? to claim!");
	private static final Pattern OWNER = Pattern.compile("By: (?:\\[[^]]+] )?(\\S+)");

	public static String strip(String s) {
		return s == null ? "" : s.replaceAll("§.", "").trim();
	}

	public static boolean isOrdersTitle(String title) {
		String t = strip(title);
		return t.equals("Your Bazaar Orders") || t.equals("Co-op Bazaar Orders");
	}

	public static boolean looksLikeOrder(String itemName) {
		return NAME.matcher(strip(itemName)).matches();
	}

	/** "1.2k" -> 1200, "2,000" -> 2000. */
	static long number(String s) {
		String t = s.replace(",", "").toLowerCase(Locale.ROOT);
		double mult = t.endsWith("k") ? 1e3 : t.endsWith("m") ? 1e6 : 1;
		if (mult != 1) t = t.substring(0, t.length() - 1);
		try {
			return Math.round(Double.parseDouble(t) * mult);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** An order item from the screen, or null if it isn't one (or belongs to a co-op member, not you). */
	public static Order parse(String itemName, List<String> lore, String player, long now) {
		Matcher n = NAME.matcher(strip(itemName));
		if (!n.matches()) return null;
		Type type = n.group(1).equals("BUY") ? Type.BUY : Type.SELL;
		String name = n.group(2).trim();
		Long amount = null, filled = null;
		Double price = null;
		boolean full = false;
		int claim = 0;
		String owner = null;
		for (String raw : lore) {
			String l = strip(raw);
			Matcher m;
			if ((m = AMOUNT.matcher(l)).find()) amount = number(m.group(1));
			else if ((m = FILLED.matcher(l)).find()) { filled = number(m.group(1)); full = m.group(2).equals("100%!"); }
			else if ((m = PRICE.matcher(l)).find()) price = Double.parseDouble(m.group(1).replace(",", ""));
			else if ((m = CLAIM.matcher(l)).find()) claim = (int) number(m.group(1));
			else if ((m = OWNER.matcher(l)).find()) owner = m.group(1);
		}
		if (amount == null || price == null) return null;
		if (owner != null && player != null && !player.isEmpty() && !owner.equalsIgnoreCase(player)) return null;  // a co-op member's order
		return new Order(type, name, productFor(name), amount, full ? amount : filled == null ? 0 : filled, full, price, claim, now);
	}

	// ---- item name -> bazaar product ----

	private static final Pattern BOOK = Pattern.compile("^ENCHANTMENT_(.+)_(\\d+)$");
	private static Map<String, String> nameIndex = Map.of();
	private static long indexFor = -1;

	private static String norm(String s) {
		return strip(s).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
	}

	/** The bazaar product ID for an item name as the bazaar shows it ("Enchanted Diamond" -> ENCHANTED_DIAMOND), or null. */
	public static synchronized String productFor(String name) {
		if (indexFor != BazaarPrices.lastFetchMillis()) {
			Map<String, String> idx = new HashMap<>();
			for (String id : BazaarPrices.ids()) {
				idx.putIfAbsent(norm(SiteData.prettyName(id)), id);
				idx.putIfAbsent(norm(Recipes.name(id)), id);
				Matcher b = BOOK.matcher(id);                    // books show as "Sharpness V" on the bazaar
				if (b.matches()) idx.putIfAbsent(norm(Recipes.fallbackName(b.group(1)) + " " + SiteData.roman(Integer.parseInt(b.group(2)))), id);
			}
			nameIndex = idx;
			indexFor = BazaarPrices.lastFetchMillis();
		}
		return nameIndex.get(norm(name));
	}

	// ---- the orders you have ----

	private static List<Order> orders = new ArrayList<>();
	/** The last status each order was checked at, so each outbid only alerts once. */
	private static final Map<String, Status> last = new HashMap<>();
	private static long updatedAt = 0;
	private static Path file;

	/** Goes up whenever the list of orders changes, so screens know to redraw. */
	private static volatile long version = 0;

	public static long version() {
		return version;
	}

	public static synchronized List<Order> orders() {
		return List.copyOf(orders);
	}

	/** One key per order, in list order. Two identical orders get "...", "...#2", so each keeps its own status. */
	static List<String> keys(List<Order> list) {
		Map<String, Integer> seen = new HashMap<>();
		List<String> out = new ArrayList<>(list.size());
		for (Order o : list) {
			int n = seen.merge(o.key(), 1, Integer::sum);
			out.add(n == 1 ? o.key() : o.key() + "#" + n);
		}
		return out;
	}

	/** When you last opened Manage Orders (0 = never). */
	public static synchronized long updatedAt() {
		return updatedAt;
	}

	/** Replaces the list with what's on the Manage Orders screen (that screen is always the full, current list). */
	public static synchronized void replace(List<Order> now) {
		List<Order> fixed = new ArrayList<>();
		for (Order o : now) fixed.add(o.productId() == null ? new Order(o.type(), o.name(), productFor(o.name()), o.amount(), o.filled(), o.full(),
				o.price(), o.claimable(), o.seenAt()) : o);
		orders = fixed;
		updatedAt = System.currentTimeMillis();
		last.keySet().retainAll(keys(fixed));
		version++;
		save();
	}

	public static synchronized void clear() {
		orders = new ArrayList<>();
		last.clear();
		updatedAt = 0;
		version++;
		save();
	}

	private static double round1(double v) {
		return Math.round(v * 10) / 10.0;
	}

	/** Checks one order against the live order book. */
	public static Check check(Order o, Function<String, BazaarPrices.Price> prices) {
		if (o.full()) return new Check(o, Status.FILLED, Double.NaN, 0);
		BazaarPrices.Price p = o.productId() == null ? null : prices.apply(o.productId());
		List<BazaarPrices.Level> book = p == null ? List.of() : o.type() == Type.BUY ? p.buyOrders() : p.sellOffers();
		if (book.isEmpty()) return new Check(o, Status.UNKNOWN, Double.NaN, 0);
		BazaarPrices.Level top = book.get(0);
		double best = round1(top.price()), mine = round1(o.price());
		boolean beaten = o.type() == Type.BUY ? best > mine : best < mine;
		Status s = beaten ? Status.OUTBID : best == mine && top.orders() > 1 ? Status.TIED : Status.TOP;
		return new Check(o, s, top.price(), top.orders());
	}

	public static List<Check> checkAll(Function<String, BazaarPrices.Price> prices) {
		List<Check> out = new ArrayList<>();
		for (Order o : orders()) out.add(check(o, prices));
		return out;
	}

	/**
	 * Orders that have just been outbid (or undercut) since the last check: each one only once, until it's back on top.
	 * Call after new prices arrive.
	 */
	public static synchronized List<Check> newlyOutbid(Function<String, BazaarPrices.Price> prices) {
		List<Check> out = new ArrayList<>();
		List<String> keys = keys(orders);
		boolean changed = false;
		for (int i = 0; i < orders.size(); i++) {
			Check c = check(orders.get(i), prices);
			Status before = last.put(keys.get(i), c.status());
			if (before != c.status()) changed = true;
			if (c.status() == Status.OUTBID && before != Status.OUTBID) out.add(c);
		}
		if (changed) save();                         // only write the file when a status actually changed
		return out;
	}

	// ---- Hypixel's own bazaar chat messages: keep the list right between visits to Manage Orders ----

	private static final Pattern CHAT_FILLED = Pattern.compile("^\\[Bazaar] Your (Buy Order|Sell Offer) for ([\\d,]+)x (.+?) was filled!$");
	private static final Pattern CHAT_CLAIM_BUY = Pattern.compile("^\\[Bazaar] Claimed ([\\d,]+)x (.+?) worth .+ bought for .+$");
	private static final Pattern CHAT_CLAIM_SELL = Pattern.compile("^\\[Bazaar] Claimed .+ coins? from selling ([\\d,]+)x (.+?) at .+$");

	/**
	 * Reads one chat line. "Your Buy Order for 64x Diamond was filled!" marks that order Filled (so it stops showing
	 * as outbid just because it's no longer in the order book); claiming a whole filled order removes it. Anything
	 * else, or a message that matches none of your orders, changes nothing. Returns true if the list changed.
	 */
	public static synchronized boolean onChat(String message) {
		String msg = strip(message);
		if (!msg.startsWith("[Bazaar]")) return false;
		Matcher m;
		if ((m = CHAT_FILLED.matcher(msg)).matches()) {
			Type type = m.group(1).equals("Buy Order") ? Type.BUY : Type.SELL;
			int i = find(type, m.group(3), number(m.group(2)), false);
			if (i < 0) return false;
			Order o = orders.get(i);
			List<String> before = keys(orders);
			orders.set(i, new Order(o.type(), o.name(), o.productId(), o.amount(), o.amount(), true, o.price(),
					(int) Math.min(Integer.MAX_VALUE, o.amount()), o.seenAt()));
			rekey(before);
			return true;
		}
		boolean buy = (m = CHAT_CLAIM_BUY.matcher(msg)).matches();
		if (buy || (m = CHAT_CLAIM_SELL.matcher(msg)).matches()) {
			int i = find(buy ? Type.BUY : Type.SELL, m.group(2), number(m.group(1)), true);
			if (i < 0) return false;
			List<String> before = keys(orders);
			before.remove(i);
			orders.remove(i);
			rekey(before);
			return true;
		}
		return false;
	}

	/** The first order of this kind, item and size; full = only a filled one (a claim), otherwise only an unfilled one. */
	private static int find(Type type, String name, long amount, boolean full) {
		String want = norm(name);
		for (int i = 0; i < orders.size(); i++) {
			Order o = orders.get(i);
			if (o.type() == type && o.amount() == amount && o.full() == full && norm(o.name()).equals(want)) return i;
		}
		return -1;
	}

	/** After the list changed: carry each order's last status over to its new key, then save. */
	private static void rekey(List<String> before) {
		List<String> after = keys(orders);
		Map<String, Status> moved = new HashMap<>();
		for (int i = 0; i < after.size() && i < before.size(); i++) {
			Status s = last.get(before.get(i));
			if (s != null) moved.put(after.get(i), s);
		}
		last.clear();
		last.putAll(moved);
		version++;
		save();
	}

	/** The chat alert for an outbid order, as plain text parts: {headline, detail}. */
	public static String[] alertText(Check c) {
		Order o = c.order();
		boolean buy = o.type() == Type.BUY;
		double diff = Math.abs(c.best() - o.price());
		return new String[] {buy ? "Outbid: " : "Undercut: ",
				"your " + (buy ? "buy order" : "sell offer") + " for " + Format.count(o.amount() - o.filled()) + "x " + o.name() + " at "
						+ Format.price(o.price()) + " · " + (buy ? "top buy order" : "lowest sell offer") + " is now " + Format.price(c.best())
						+ " (" + (buy ? "+" : "-") + Format.price(diff) + ")"};
	}

	// ---- saved between sessions ----

	public static synchronized void load(Path saveFile) {
		file = saveFile;
		try {
			if (!Files.exists(file)) return;
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			List<Order> list = new ArrayList<>();
			for (JsonElement e : root.getAsJsonArray("orders")) {
				JsonObject o = e.getAsJsonObject();
				list.add(new Order(Type.valueOf(o.get("type").getAsString()), o.get("name").getAsString(),
						o.has("product") ? o.get("product").getAsString() : null, o.get("amount").getAsLong(), o.get("filled").getAsLong(),
						o.get("full").getAsBoolean(), o.get("price").getAsDouble(), o.get("claim").getAsInt(), o.get("seen").getAsLong()));
			}
			orders = list;
			updatedAt = root.has("updated") ? root.get("updated").getAsLong() : 0;
			if (root.has("last")) for (var e : root.getAsJsonObject("last").entrySet()) last.put(e.getKey(), Status.valueOf(e.getValue().getAsString()));
		} catch (Exception e) {
			System.getLogger("bazaartips").log(System.Logger.Level.WARNING, "Couldn't read saved bazaar orders: " + e);
		}
	}

	private static void save() {
		if (file == null) return;
		try {
			JsonObject root = new JsonObject();
			JsonArray arr = new JsonArray();
			for (Order o : orders) {
				JsonObject j = new JsonObject();
				j.addProperty("type", o.type().name());
				j.addProperty("name", o.name());
				if (o.productId() != null) j.addProperty("product", o.productId());
				j.addProperty("amount", o.amount());
				j.addProperty("filled", o.filled());
				j.addProperty("full", o.full());
				j.addProperty("price", o.price());
				j.addProperty("claim", o.claimable());
				j.addProperty("seen", o.seenAt());
				arr.add(j);
			}
			root.add("orders", arr);
			root.addProperty("updated", updatedAt);
			JsonObject l = new JsonObject();
			last.forEach((k, v) -> l.addProperty(k, v.name()));
			root.add("last", l);
			Files.createDirectories(file.getParent());
			Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			System.getLogger("bazaartips").log(System.Logger.Level.WARNING, "Couldn't save bazaar orders: " + e);
		}
	}

	/** For tests. */
	static synchronized void resetForTests() {
		orders = new ArrayList<>();
		last.clear();
		updatedAt = 0;
		file = null;
		indexFor = -1;
		version++;
	}
}
