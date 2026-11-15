package com.unc.bazaartips;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Price alerts: "tell me when this item's insta-buy (or insta-sell) price goes below (or above) a number". Checked every
 * time prices refresh; each alert messages once when its price crosses, then again only after it has crossed back.
 * Saved between sessions. No Minecraft code, so it can be tested on its own.
 */
public final class PriceAlerts {
	private PriceAlerts() {}

	/** buy = watch the insta-buy price (what you pay), else the insta-sell price (what you get). below = fire at or under the price. */
	public record Alert(String id, boolean buy, boolean below, double price) {
		String key() {
			return id + "|" + buy + "|" + below + "|" + price;
		}

		/** "insta-buy below 29,000". */
		public String condition() {
			return (buy ? "insta-buy" : "insta-sell") + (below ? " below " : " above ") + Format.price(price);
		}

		/** The watched price right now (NaN if unknown). */
		public double now(Function<String, BazaarPrices.Price> prices) {
			BazaarPrices.Price p = prices.apply(id);
			return p == null ? Double.NaN : buy ? p.instaBuy() : p.instaSell();
		}

		public boolean hit(Function<String, BazaarPrices.Price> prices) {
			double v = now(prices);
			return !Double.isNaN(v) && (below ? v <= price : v >= price);
		}
	}

	/** An alert that just fired, and the price that set it off. */
	public record Hit(Alert alert, double price) {}

	private static List<Alert> alerts = new ArrayList<>();
	private static final Set<String> fired = new HashSet<>();
	private static volatile long version = 0;
	private static Path file;

	public static long version() {
		return version;
	}

	public static synchronized List<Alert> all() {
		return List.copyOf(alerts);
	}

	public static synchronized List<Alert> forItem(String id) {
		List<Alert> out = new ArrayList<>();
		for (Alert a : alerts) if (a.id().equals(id)) out.add(a);
		return out;
	}

	/** Adds an alert (an identical one isn't added twice). */
	public static synchronized boolean add(Alert a) {
		for (Alert x : alerts) if (x.key().equals(a.key())) return false;
		alerts.add(a);
		version++;
		save();
		return true;
	}

	public static synchronized void remove(Alert a) {
		if (alerts.removeIf(x -> x.key().equals(a.key()))) {
			fired.remove(a.key());
			version++;
			save();
		}
	}

	/** Alerts that have just crossed their price. Call after new prices arrive. */
	public static synchronized List<Hit> check(Function<String, BazaarPrices.Price> prices) {
		List<Hit> out = new ArrayList<>();
		for (Alert a : alerts) {
			if (a.hit(prices)) {
				if (fired.add(a.key())) out.add(new Hit(a, a.now(prices)));
			} else if (!Double.isNaN(a.now(prices))) {
				fired.remove(a.key());                        // crossed back: it can fire again
			}
		}
		return out;
	}

	/** Whether an alert has fired and is waiting for its price to cross back. */
	public static synchronized boolean isFired(Alert a) {
		return fired.contains(a.key());
	}

	/** The chat text: {headline, detail}. */
	public static String[] text(Hit h) {
		Alert a = h.alert();
		return new String[] {"Price alert: ", SiteData.prettyName(a.id()) + " " + (a.buy() ? "insta-buy" : "insta-sell") + " is " + Format.price(h.price())
				+ " (" + (a.below() ? "at or below" : "at or above") + " your " + Format.price(a.price()) + ")"};
	}

	/**
	 * The item a typed name means: an exact name match first, then the shortest item name containing it. Null if none
	 * (or nothing typed).
	 */
	public static String find(String typed, Iterable<String> ids) {
		String q = typed == null ? "" : typed.trim().toLowerCase(java.util.Locale.ROOT);
		if (q.length() < 2) return null;
		String best = null;
		for (String id : ids) {
			String n = SiteData.prettyName(id).toLowerCase(java.util.Locale.ROOT);
			if (n.equals(q) || id.equalsIgnoreCase(q)) return id;
			if (n.contains(q) && (best == null || n.length() < SiteData.prettyName(best).length())) best = id;
		}
		return best;
	}

	// ---- saved between sessions ----

	public static synchronized void load(Path saveFile) {
		file = saveFile;
		try {
			if (!Files.exists(file)) return;
			List<Alert> list = new ArrayList<>();
			for (JsonElement e : JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonArray()) {
				JsonObject o = e.getAsJsonObject();
				list.add(new Alert(o.get("id").getAsString(), o.get("buy").getAsBoolean(), o.get("below").getAsBoolean(), o.get("price").getAsDouble()));
			}
			alerts = list;
			version++;
		} catch (Exception e) {
			System.getLogger("bazaartips").log(System.Logger.Level.WARNING, "Couldn't read saved price alerts: " + e);
		}
	}

	private static void save() {
		if (file == null) return;
		try {
			JsonArray arr = new JsonArray();
			for (Alert a : alerts) {
				JsonObject o = new JsonObject();
				o.addProperty("id", a.id());
				o.addProperty("buy", a.buy());
				o.addProperty("below", a.below());
				o.addProperty("price", a.price());
				arr.add(o);
			}
			Files.createDirectories(file.getParent());
			Files.writeString(file, arr.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			System.getLogger("bazaartips").log(System.Logger.Level.WARNING, "Couldn't save price alerts: " + e);
		}
	}

	/** For tests. */
	static synchronized void resetForTests() {
		alerts = new ArrayList<>();
		fired.clear();
		file = null;
		version++;
	}
}
