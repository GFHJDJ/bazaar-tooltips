package com.unc.bazaartips;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "My unlocks": looks up a player's collections, slayers and dungeon bosses through the website's
 * lookup server, and fills in Settings.unlocks (the site's loadUnlocks + applyLookup).
 * No Minecraft code, so it can be tested on its own.
 */
public final class UnlockLookup {
	public static final String SERVER = "https://bazaar-unlocks.emanrox19.workers.dev";

	/** What the screen shows: a status line, and the player's profiles if there's more than one. */
	public static volatile String status = "";
	public static volatile boolean error = false;
	public static volatile boolean busy = false;
	public static volatile List<String> profiles = List.of();

	private UnlockLookup() {}

	/** Starts a lookup in the background (does nothing if one is already running). */
	public static void start(String name, String profile) {
		String n = name == null ? "" : name.trim();
		if (!n.matches("[A-Za-z0-9_]{1,16}") && !n.matches("[0-9a-fA-F-]{32,36}")) {
			setStatus("Enter a Minecraft username (letters, numbers and _ only).", true);
			return;
		}
		if (busy) return;
		busy = true;
		Settings.lookupName = n;
		Settings.save();
		setStatus("Looking up " + n + "...", false);
		Thread t = new Thread(() -> {
			try {
				String url = SERVER + "/unlocks?player=" + URLEncoder.encode(n, StandardCharsets.UTF_8)
						+ (profile == null || profile.isEmpty() ? "" : "&profile=" + URLEncoder.encode(profile, StandardCharsets.UTF_8));
				String body = BazaarPrices.download(URI.create(url), "Unlocks lookup");
				if (body == null) throw new IllegalStateException("the lookup server didn't answer");
				apply(JsonParser.parseString(body).getAsJsonObject());
			} catch (Exception e) {
				setStatus("Couldn't load that player: " + (e.getMessage() == null ? "network error" : e.getMessage()) + ".", true);
			} finally {
				busy = false;
			}
		}, "BazaarTooltips-unlocks");
		t.setDaemon(true);
		t.start();
	}

	private static void setStatus(String s, boolean err) {
		status = s;
		error = err;
	}

	static String normName(String x) {
		return x.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	/** Fills Settings.unlocks from the server's answer and switches the requirement filter to "I've unlocked". */
	static void apply(JsonObject d) {
		if (!d.has("ok") || !d.get("ok").getAsBoolean()) {
			throw new IllegalStateException(d.has("error") ? d.get("error").getAsString() : "the server said no");
		}
		Map<String, Integer> coll = new HashMap<>();
		if (d.has("collections")) for (var e : d.getAsJsonObject("collections").entrySet()) coll.put(normName(e.getKey()), e.getValue().getAsInt());
		JsonObject sl = d.has("slayers") ? d.getAsJsonObject("slayers") : new JsonObject();
		JsonObject bo = d.has("bosses") ? d.getAsJsonObject("bosses") : new JsonObject();
		Map<String, Integer> unlocks = new HashMap<>(Settings.unlocks);
		int n = 0;
		List<String> unmatched = new ArrayList<>();
		for (Recipes.Req e : Recipes.reqIndex().values()) {
			Integer v;
			switch (e.kind()) {
				case "collection" -> v = coll.get(normName(e.name()));
				case "slayer" -> v = sl.has(e.name()) ? sl.get(e.name()).getAsInt() : null;
				case "boss" -> v = bo.has(e.name()) ? bo.get(e.name()).getAsInt() : null;
				default -> { continue; }
			}
			if (v == null) {
				unmatched.add(e.name());
				continue;
			}
			unlocks.put(e.key(), v);
			n++;
		}
		List<String> profs = new ArrayList<>();
		if (d.has("profiles") && d.get("profiles").isJsonArray()) for (JsonElement p : d.getAsJsonArray("profiles")) profs.add(p.getAsString());
		profiles = List.copyOf(profs);
		String profile = d.has("profile") ? d.get("profile").getAsString() : "";
		Settings.unlocks = unlocks;
		Settings.lookupProfile = profile;
		Settings.reqs = Settings.Reqs.UNLOCKED;
		Settings.save();
		String user = d.has("username") ? d.get("username").getAsString() : "";
		setStatus("Loaded " + user + (profile.isEmpty() ? "" : " (profile " + profile + ")") + ": " + n
				+ " collection, slayer and dungeon boss tiers filled in, and the list now shows only what you can craft. "
				+ "Mutations, Garden milestones, skills and museum aren't part of the lookup."
				+ (d.has("collectionsApiOff") && d.get("collectionsApiOff").getAsBoolean()
						? " Your Collections API looks turned off, so collection tiers may read as 0. Turn it on in your SkyBlock API settings and try again in a few minutes." : "")
				+ (unmatched.isEmpty() ? "" : " Couldn't match: " + String.join(", ", unmatched.subList(0, Math.min(8, unmatched.size())))
						+ (unmatched.size() > 8 ? " and " + (unmatched.size() - 8) + " more" : "") + "."), false);
	}

	/** Forget every unlock (and go back to showing all crafts). */
	public static void clear() {
		Settings.unlocks = new HashMap<>();
		Settings.reqs = Settings.Reqs.ALL;
		Settings.save();
		setStatus("Unlocks cleared.", false);
	}
}
