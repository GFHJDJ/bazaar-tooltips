package com.unc.bazaartips;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The website's recipes (copied by tools/import_site_recipes.py), plus names, the Personal Compactor
 * list and the unlock requirements. No Minecraft code, so it can be tested on its own.
 */
public final class Recipes {
	/** One ingredient line: how many of which item. */
	public record Ing(String id, int qty) {}

	/** name, how many 1 craft makes, ingredients in the site's order, requirement text ("" if none). */
	public record Recipe(String id, String name, int output, List<Ing> ingredients, String req) {}

	/** One parsed requirement, e.g. Diamond IV -> key "collection:Diamond", level 4. */
	public record Req(String key, String kind, String name, int level) {}

	public static final String REQ_SEP = " · ";

	private static Map<String, Recipe> recipes = Map.of();
	private static Map<String, String> names = Map.of();
	private static Set<String> compactor = Set.of();
	/** Every requirement any recipe asks for -> the kind of thing it is (for the unlocks lookup). */
	private static Map<String, Req> reqIndex = Map.of();

	private Recipes() {}

	public static void load() {
		try (InputStream in = Recipes.class.getResourceAsStream("/bazaartips/recipes.json")) {
			if (in == null) throw new IllegalStateException("recipes.json missing from the jar");
			load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		} catch (Exception e) {
			System.getLogger("bazaartips").log(System.Logger.Level.WARNING, "Couldn't load recipes: " + e);
		}
	}

	static void load(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		Map<String, Recipe> r = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("recipes").entrySet()) {
			JsonObject o = e.getValue().getAsJsonObject();
			List<Ing> ins = new ArrayList<>();
			for (JsonElement i : o.getAsJsonArray("in")) {
				ins.add(new Ing(i.getAsJsonArray().get(0).getAsString(), i.getAsJsonArray().get(1).getAsInt()));
			}
			r.put(e.getKey(), new Recipe(e.getKey(), o.get("name").getAsString(), o.get("out").getAsInt(),
					List.copyOf(ins), o.has("req") ? o.get("req").getAsString() : ""));
		}
		Map<String, String> n = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("names").entrySet()) n.put(e.getKey(), e.getValue().getAsString());
		Set<String> c = new HashSet<>();
		if (root.has("compactor")) for (JsonElement e : root.getAsJsonArray("compactor")) c.add(e.getAsString());

		Map<String, Req> idx = new HashMap<>();
		for (Recipe rec : r.values()) for (Req q : parseReq(rec.req())) idx.putIfAbsent(q.key(), q);

		recipes = r;
		names = n;
		compactor = c;
		reqIndex = idx;
	}

	public static Recipe get(String id) {
		return recipes.get(id);
	}

	public static Iterable<Recipe> all() {
		return recipes.values();
	}

	/** Display name, e.g. "INK_SACK:3" -> "Cocoa Beans"; unknown IDs are prettified ("FOO_BAR" -> "Foo Bar"). */
	public static String name(String id) {
		Recipe r = recipes.get(id);
		if (r != null) return r.name();
		String n = names.get(id);
		return n != null ? n : fallbackName(id);
	}

	/** The site's fallbackName: underscores to spaces, then capitalize the first letter after every word boundary. */
	static String fallbackName(String id) {
		Matcher m = WORD_START.matcher(id.replace('_', ' ').toLowerCase(Locale.ROOT));
		StringBuilder sb = new StringBuilder();
		while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(m.group().toUpperCase(Locale.ROOT)));
		m.appendTail(sb);
		return sb.toString();
	}

	private static final Pattern WORD_START = Pattern.compile("\\b\\w");

	/** A Personal Compactor can make this (from a one-ingredient recipe), like the site's "personal compactor" tag. */
	/** Gemstones a Personal Compactor makes too (80 of the tier below), though they aren't on the Super Compactor list. */
	private static final java.util.regex.Pattern COMPACTOR_GEM = java.util.regex.Pattern.compile("^(FLAWED|FINE|FLAWLESS)_[A-Z]+_GEM$");

	public static boolean compactorMakes(Recipe r) {
		return (compactor.contains(r.id()) || COMPACTOR_GEM.matcher(r.id()).matches()) && r.ingredients().size() == 1;
	}

	public static Map<String, Req> reqIndex() {
		return reqIndex;
	}

	// ---- requirements (the site's parseReq / reqMet) ----

	private static final Set<String> DUNGEON_BOSSES = Set.of("Bonzo", "Scarf", "The Professor", "Thorn", "Livid", "Sadan", "Necron");
	private static final Pattern ANALYZED = Pattern.compile("^(.*) analyzed$");
	private static final Pattern MUSEUM = Pattern.compile("^(\\d+) Museum Donations$");
	private static final Pattern LEVELED = Pattern.compile("^(.*?) ([IVXLC]+|\\d+)$");
	private static final Map<String, List<Req>> REQ_CACHE = new ConcurrentHashMap<>();

	public static List<Req> parseReq(String req) {
		if (req == null || req.isEmpty()) return List.of();
		return REQ_CACHE.computeIfAbsent(req, Recipes::parseUncached);
	}

	private static List<Req> parseUncached(String req) {
		List<Req> out = new ArrayList<>();
		for (String t : req.split(Pattern.quote(REQ_SEP))) {
			Matcher m;
			if ((m = ANALYZED.matcher(t)).matches()) {
				for (String n : m.group(1).split(", ")) out.add(new Req("mutation:" + n, "mutation", n, 1));
				continue;
			}
			if ((m = MUSEUM.matcher(t)).matches()) {
				out.add(new Req("museum:Museum donations", "museum", "Museum donations", Integer.parseInt(m.group(1))));
				continue;
			}
			if ((m = LEVELED.matcher(t)).matches()) {
				String name = m.group(1);
				int lvl = m.group(2).matches("\\d+") ? Integer.parseInt(m.group(2)) : romanToInt(m.group(2));
				String kind = name.endsWith(" Slayer") ? "slayer" : DUNGEON_BOSSES.contains(name) ? "boss"
						: name.equals("Crop Analyzer Milestone") ? "milestone" : name.equals("Enchanting") ? "skill" : "collection";
				out.add(new Req(kind + ":" + name, kind, name, lvl));
				continue;
			}
			out.add(new Req("other:" + t, "other", t, 1));
		}
		return List.copyOf(out);
	}

	static int romanToInt(String r) {
		int n = 0;
		for (int i = 0; i < r.length(); i++) {
			int a = roman(r.charAt(i)), b = i + 1 < r.length() ? roman(r.charAt(i + 1)) : 0;
			n += a < b ? -a : a;
		}
		return n;
	}

	private static int roman(char c) {
		return switch (c) { case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50; case 'C' -> 100; default -> 0; };
	}

	/** Have you unlocked everything in this requirement text (per Settings.unlocks)? */
	public static boolean reqMet(String req) {
		for (Req r : parseReq(req)) if (Settings.unlocks.getOrDefault(r.key(), 0) < r.level()) return false;
		return true;
	}

	public static boolean hasUnlocks() {
		for (int v : Settings.unlocks.values()) if (v > 0) return true;
		return false;
	}
}
