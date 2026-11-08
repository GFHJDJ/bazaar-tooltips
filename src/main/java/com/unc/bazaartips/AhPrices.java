package com.unc.bazaartips;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Auction house lowest-BIN prices for every item, like the website's loadAHBulk(): used for forge and
 * NPC-shop ingredients that aren't on the bazaar, and (with "Bazaar sales only" off) results sold on the AH.
 * Downloaded when an NPC or Forge tab needs it, at most every 5 minutes.
 */
public final class AhPrices {
	private static final String[] SOURCES = {"https://lb.tricked.dev/lowestbins", "https://moulberry.codes/lowestbin.json"};
	private static volatile Map<String, Double> bins = Map.of();
	private static volatile long fetchedAt = 0;
	private static volatile boolean busy = false;

	private AhPrices() {}

	/** Lowest BIN for an item (site ID format), or null if unknown / not loaded yet. */
	public static Double get(String id) {
		Double v = bins.get(id);
		return v != null && v > 0 ? v : null;
	}

	public static boolean loaded() {
		return fetchedAt != 0;
	}

	/** Changes each time new AH prices arrive (screens re-rank when it does). */
	public static long version() {
		return fetchedAt;
	}

	/** Starts a download in the background if the prices are missing or over 5 minutes old. */
	public static void ensure() {
		if (busy || System.currentTimeMillis() - fetchedAt < 5 * 60 * 1000) return;
		busy = true;
		Thread t = new Thread(() -> {
			try {
				for (String url : SOURCES) {
					String body = BazaarPrices.download(URI.create(url), "AH lowest BIN");
					if (body == null) continue;
					Map<String, Double> parsed = parse(body);
					if (parsed.size() > 50) {
						bins = parsed;
						fetchedAt = System.currentTimeMillis();
						break;
					}
				}
			} catch (Exception e) {
				System.getLogger("bazaartips").log(System.Logger.Level.WARNING, "AH prices unusable: " + e);
			} finally {
				busy = false;
			}
		}, "BazaarTooltips-ah");
		t.setDaemon(true);
		t.start();
	}

	static Map<String, Double> parse(String json) {
		JsonObject o = JsonParser.parseString(json).getAsJsonObject();
		Map<String, Double> out = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : o.entrySet()) {
			if (e.getValue().isJsonPrimitive()) out.put(e.getKey(), e.getValue().getAsDouble());
		}
		return Map.copyOf(out);
	}

	/** For tests. */
	static void set(Map<String, Double> m) {
		bins = Map.copyOf(m);
		fetchedAt = System.currentTimeMillis();
	}
}
