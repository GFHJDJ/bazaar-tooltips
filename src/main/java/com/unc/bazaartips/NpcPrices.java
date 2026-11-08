package com.unc.bazaartips;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * NPC sell prices from Hypixel's public item list (the "npc_sell_price" field).
 * No Minecraft code, so it can be tested on its own.
 */
public final class NpcPrices {
	private static final URI ITEMS_URL = URI.create("https://api.hypixel.net/v2/resources/skyblock/items");
	private static final long REFRESH_EVERY_MILLIS = 6 * 60 * 60 * 1000;

	private static volatile Map<String, Double> prices = Map.of();
	private static volatile long fetchedAtMillis = 0;
	private static volatile Map<String, EssenceEngine.Salvage> salvage = Map.of();

	private NpcPrices() {}

	/** What an NPC pays for one of this Skyblock item ID, or null if NPCs don't buy it (or nothing has loaded yet). */
	/** Changes each time new NPC prices arrive. */
	public static long version() {
		return fetchedAtMillis;
	}

	public static Double get(String skyblockId) {
		return prices.get(skyblockId);
	}

	/** What each item salvages (or sacrifices) into, from the same item list, in the list's order. */
	public static Map<String, EssenceEngine.Salvage> salvage() {
		return salvage;
	}

	/** For tests. */
	static void setSalvage(Map<String, EssenceEngine.Salvage> m) {
		salvage = m;
	}

	/** Downloads the item list if it's missing or more than 6 hours old. Never throws. */
	static void refreshIfNeeded() {
		if (fetchedAtMillis != 0 && System.currentTimeMillis() - fetchedAtMillis < REFRESH_EVERY_MILLIS) return;
		try {
			String body = BazaarPrices.download(ITEMS_URL, "Item list");
			if (body == null) return;
			Map<String, Double> parsed = parse(body);
			if (!parsed.isEmpty()) {
				salvage = EssenceEngine.parseSalvage(JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("items"));
				prices = parsed;
				fetchedAtMillis = System.currentTimeMillis();
			}
		} catch (Exception e) {
			System.getLogger("bazaartips").log(System.Logger.Level.WARNING, "Item list unusable: " + e);
		}
	}

	/** Item list JSON -> item ID -> NPC sell price. Items NPCs won't buy (no price, or 0) are left out. */
	static Map<String, Double> parse(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		JsonElement success = root.get("success");
		JsonArray items = root.getAsJsonArray("items");
		if (success == null || !success.getAsBoolean() || items == null) return Map.of();

		Map<String, Double> out = new HashMap<>();
		for (JsonElement e : items) {
			JsonObject item = e.getAsJsonObject();
			JsonElement id = item.get("id");
			JsonElement price = item.get("npc_sell_price");
			if (id == null || price == null || price.isJsonNull()) continue;
			double value = price.getAsDouble();
			if (value > 0) out.put(id.getAsString(), value);
		}
		return Map.copyOf(out);
	}
}
