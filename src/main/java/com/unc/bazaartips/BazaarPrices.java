package com.unc.bazaartips;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Downloads the Hypixel bazaar once a minute and keeps the latest prices in memory.
 * No Minecraft code in here, so it can be tested on its own.
 */
public final class BazaarPrices {
	/** One price level in the order book: this many items, from this many orders, at this price. */
	public record Level(double price, long amount, int orders) {
		public Level(double price, long amount) {
			this(price, amount, 1);
		}
	}

	/**
	 * Prices for one bazaar product.
	 * instaBuy  = cheapest sell offer (what you pay to buy instantly).
	 * instaSell = highest buy order (what you get selling instantly).
	 * Either can be NaN when that side of the order book is empty.
	 * sellOffers = sell offers, cheapest first (what insta-buying eats through).
	 * buyOrders  = buy orders, highest first (what insta-selling eats through).
	 * Hypixel only sends the best ~30 levels of each.
	 * quickBuy / quickSell = Hypixel's own averaged insta-buy / insta-sell prices ("quick_status"),
	 * used for the spread check in {@link Risk}, like the website.
	 * sellOfferCount / buyOrderCount = how many orders are open on each side.
	 * itemsInSellOffers / itemsInBuyOrders = how many items those orders hold in total.
	 */
	public record Price(double instaBuy, double instaSell, long buyMovingWeek, long sellMovingWeek,
			List<Level> sellOffers, List<Level> buyOrders,
			double quickBuy, double quickSell, int sellOfferCount, int buyOrderCount,
			long itemsInSellOffers, long itemsInBuyOrders) {
		/** A price with no order book (just the top prices), for tests. */
		public Price(double instaBuy, double instaSell, long buyMovingWeek, long sellMovingWeek) {
			this(instaBuy, instaSell, buyMovingWeek, sellMovingWeek,
					Double.isNaN(instaBuy) ? List.of() : List.of(new Level(instaBuy, Long.MAX_VALUE / 4)),
					Double.isNaN(instaSell) ? List.of() : List.of(new Level(instaSell, Long.MAX_VALUE / 4)));
		}

		/** A price with an order book but no quick_status, for tests. */
		public Price(double instaBuy, double instaSell, long buyMovingWeek, long sellMovingWeek,
				List<Level> sellOffers, List<Level> buyOrders) {
			this(instaBuy, instaSell, buyMovingWeek, sellMovingWeek, sellOffers, buyOrders,
					Double.isNaN(instaBuy) ? 0 : instaBuy, Double.isNaN(instaSell) ? 0 : instaSell, 99, 99, 0, 0);
		}
	}

	private static final URI BAZAAR_URL = URI.create("https://api.hypixel.net/v2/skyblock/bazaar");
	private static final System.Logger LOG = System.getLogger("bazaartips");
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	// Swapped in as a whole, so the render thread never sees a half-filled map.
	private static volatile Map<String, Price> prices = Map.of();
	private static volatile long fetchedAtMillis = 0;
	private static ScheduledExecutorService timer;
	/** Runs on the download thread after new prices arrive (the client uses it to pre-build the flips list). */
	public static volatile Runnable afterRefresh;

	private BazaarPrices() {}

	/** Starts the background refresh: right away, then every 60 seconds. */
	public static synchronized void start() {
		if (timer != null) return;
		timer = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "BazaarTooltips-fetch");
			t.setDaemon(true); // don't keep the game open on exit
			return t;
		});
		// Hypixel updates the bazaar about once a minute, so faster polling is pointless.
		timer.scheduleAtFixedRate(BazaarPrices::refresh, 0, 60, TimeUnit.SECONDS);
		// NPC prices barely ever change: this only downloads when they're missing or 6+ hours old.
		timer.scheduleAtFixedRate(NpcPrices::refreshIfNeeded, 0, 5, TimeUnit.MINUTES);
	}

	/** Latest price for a bazaar product ID, or null if it isn't a bazaar item (or nothing has loaded yet). */
	public static Price get(String productId) {
		return prices.get(productId);
	}

	/** Every bazaar product ID in the latest prices. */
	public static java.util.Set<String> ids() {
		return prices.keySet();
	}

	/** How old the prices are, in milliseconds. -1 if nothing has loaded yet. */
	public static long ageMillis() {
		long t = fetchedAtMillis;
		return t == 0 ? -1 : System.currentTimeMillis() - t;
	}

	/** When the current prices were downloaded (0 = never). Changes each time new prices arrive. */
	public static long lastFetchMillis() {
		return fetchedAtMillis;
	}

	/** For tests: puts these prices in as if they had just been downloaded. */
	static void setForTests(Map<String, Price> p) {
		prices = Map.copyOf(p);
		fetchedAtMillis = System.currentTimeMillis();
	}

	static void refresh() {
		// Every error is caught here: an uncaught one would stop the timer for good.
		try {
			String body = download(BAZAAR_URL, "Bazaar");
			if (body == null) return;
			Map<String, Price> parsed = parse(body);
			if (!parsed.isEmpty()) {
				prices = parsed;
				fetchedAtMillis = System.currentTimeMillis();
				Runnable after = afterRefresh;
				if (after != null) after.run();
			}
		} catch (Exception e) {
			LOG.log(System.Logger.Level.WARNING, "Bazaar data unusable: " + e);
		}
	}

	/** GETs a URL and returns the body, or null (with a log line) if it failed. Never throws. */
	static String download(URI url, String what) {
		try {
			HttpRequest request = HttpRequest.newBuilder(url)
					.timeout(Duration.ofSeconds(30))
					.header("User-Agent", "BazaarTooltips/0.20")
					.GET()
					.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200) return response.body();
			LOG.log(System.Logger.Level.WARNING, what + " request failed: HTTP " + response.statusCode());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			LOG.log(System.Logger.Level.WARNING, what + " request failed: " + e);
		}
		return null;
	}

	/** Turns the API's JSON into product ID -> Price. Returns an empty map if the response isn't usable. */
	static Map<String, Price> parse(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		JsonElement success = root.get("success");
		JsonObject products = root.getAsJsonObject("products");
		if (success == null || !success.getAsBoolean() || products == null) return Map.of();

		Map<String, Price> out = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : products.entrySet()) {
			JsonObject product = entry.getValue().getAsJsonObject();
			JsonObject quick = product.getAsJsonObject("quick_status");

			// Careful, the names are backwards from what you'd guess:
			// buy_summary  = SELL OFFERS, cheapest first -> the insta-buy price
			// sell_summary = BUY ORDERS, highest first   -> the insta-sell price
			List<Level> sellOffers = levels(product, "buy_summary");
			List<Level> buyOrders = levels(product, "sell_summary");

			// quick_status is named the same backwards way: its "buy" fields describe the sell offers.
			out.put(entry.getKey(), new Price(
					sellOffers.isEmpty() ? Double.NaN : sellOffers.get(0).price(),
					buyOrders.isEmpty() ? Double.NaN : buyOrders.get(0).price(),
					wholeNumber(quick, "buyMovingWeek"),
					wholeNumber(quick, "sellMovingWeek"),
					sellOffers,
					buyOrders,
					number(quick, "buyPrice"),
					number(quick, "sellPrice"),
					(int) wholeNumber(quick, "buyOrders"),
					(int) wholeNumber(quick, "sellOrders"),
					wholeNumber(quick, "buyVolume"),
					wholeNumber(quick, "sellVolume")));
		}
		return Map.copyOf(out);
	}

	private static List<Level> levels(JsonObject product, String key) {
		JsonArray orders = product.getAsJsonArray(key);
		if (orders == null) return List.of();
		List<Level> out = new ArrayList<>(orders.size());
		for (JsonElement e : orders) {
			JsonObject o = e.getAsJsonObject();
			JsonElement price = o.get("pricePerUnit");
			JsonElement amount = o.get("amount");
			JsonElement count = o.get("orders");
			if (price == null || amount == null) continue;
			out.add(new Level(price.getAsDouble(), amount.getAsLong(), count == null ? 1 : count.getAsInt()));
		}
		return List.copyOf(out);
	}

	private static double number(JsonObject obj, String key) {
		if (obj == null) return 0;
		JsonElement value = obj.get(key);
		return value == null ? 0 : value.getAsDouble();
	}

	private static long wholeNumber(JsonObject obj, String key) {
		if (obj == null) return 0;
		JsonElement value = obj.get(key);
		return value == null ? 0 : value.getAsLong();
	}
}
