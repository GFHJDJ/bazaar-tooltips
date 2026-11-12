package com.unc.bazaartips;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The website's Forge recipes, NPC shops, NPC locations, stack sizes and item names (copied by
 * tools/import_site_recipes.py), plus the site's ID helpers. IDs are in the site's format
 * (INK_SACK-3, SHARPNESS;5). No Minecraft code, so it can be tested on its own.
 */
public final class SiteData {
	/** A forge recipe: makes n of id in t seconds from materials + coins; needs HotM tier hotm. */
	public record Forge(String id, int n, long t, List<Mat> in, double coins, String req, int hotm) {}

	/** An NPC shop offer: pay coins + materials to get q of id. */
	public record Shop(int index, String npc, String id, int q, double coins, List<Mat> in) {}

	/** One Kat upgrade: pet "from" (ID;rarity) -> "to", for coins plus materials, taking t seconds. */
	public record Kat(String from, String to, double coins, long t, List<Mat> in) {}

	public record Mat(String id, int qty) {}

	/** An NPC: name, and where to find them (area, coordinates) when known. */
	public record Npc(String name, String area, int[] xyz, boolean confirmed) {}

	private static List<Forge> forge = List.of();
	private static List<Shop> shops = List.of();
	private static List<Kat> kat = List.of();
	private static Map<String, Npc> npcs = Map.of();
	private static Map<String, String[]> npcAvail = Map.of();
	private static Map<String, Integer> stack = Map.of();
	private static Map<String, String> names = Map.of();
	/** Raw site data for the Books, Currencies, Minions and Profit / hr tabs (read by their engines). */
	private static JsonObject extra = new JsonObject();

	private SiteData() {}

	public static void load() {
		try (InputStream in = SiteData.class.getResourceAsStream("/bazaartips/money.json")) {
			if (in == null) throw new IllegalStateException("money.json missing from the jar");
			load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		} catch (Exception e) {
			System.getLogger("bazaartips").log(System.Logger.Level.WARNING, "Couldn't load NPC/Forge data: " + e);
		}
	}

	static void load(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		List<Forge> f = new ArrayList<>();
		for (JsonElement e : root.getAsJsonArray("forge")) {
			JsonObject o = e.getAsJsonObject();
			f.add(new Forge(o.get("id").getAsString(), o.get("n").getAsInt(), o.get("t").getAsLong(), mats(o.getAsJsonArray("in")),
					o.has("c") ? o.get("c").getAsDouble() : 0, o.has("req") ? o.get("req").getAsString() : "",
					o.has("hotm") ? o.get("hotm").getAsInt() : 0));
		}
		List<Shop> s = new ArrayList<>();
		int i = 0;
		for (JsonElement e : root.getAsJsonArray("shops")) {
			JsonObject o = e.getAsJsonObject();
			s.add(new Shop(i++, o.get("npc").getAsString(), o.get("id").getAsString(), o.get("q").getAsInt(),
					o.has("c") ? o.get("c").getAsDouble() : 0, mats(o.getAsJsonArray("in"))));
		}
		Map<String, Npc> n = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("npcs").entrySet()) {
			JsonArray a = e.getValue().getAsJsonArray();
			String name = a.get(0).getAsString();
			if (a.size() >= 6) {
				n.put(e.getKey(), new Npc(name, a.get(4).getAsString(),
						new int[] {a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt()}, a.get(5).getAsInt() != 0));
			} else {
				n.put(e.getKey(), new Npc(name, a.size() == 2 ? a.get(1).getAsString() : "", null, false));
			}
		}
		Map<String, String[]> av = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("npcAvail").entrySet()) {
			JsonArray a = e.getValue().getAsJsonArray();
			av.put(e.getKey(), new String[] {a.get(0).getAsString(), a.get(1).getAsString()});
		}
		Map<String, Integer> st = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("stack").entrySet()) st.put(e.getKey(), e.getValue().getAsInt());
		Map<String, String> nm = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("siteNames").entrySet()) nm.put(e.getKey(), e.getValue().getAsString());
		JsonObject x = new JsonObject();
		for (String k : new String[] {"enchMax", "enchMap", "cur", "minions", "minProfit", "minStore", "comp", "sc"}) {
			if (root.has(k)) x.add(k, root.get(k));
		}
		// The bits shop works like any other currency shop: add it to the list as currency "bits".
		if (root.has("bits")) {
			JsonArray cur = x.has("cur") ? x.getAsJsonArray("cur").deepCopy() : new JsonArray();
			for (JsonElement e : root.getAsJsonArray("bits")) {
				JsonObject b = e.getAsJsonObject().deepCopy();
				b.addProperty("cur", "bits");
				b.add("amt", b.remove("bits"));
				cur.add(b);
			}
			x.add("cur", cur);
		}
		List<Kat> k = new ArrayList<>();
		if (root.has("kat")) for (JsonElement e : root.getAsJsonArray("kat")) {
			JsonObject o = e.getAsJsonObject();
			k.add(new Kat(o.get("from").getAsString(), o.get("to").getAsString(), o.has("c") ? o.get("c").getAsDouble() : 0,
					o.get("t").getAsLong(), mats(o.getAsJsonArray("in"))));
		}
		kat = List.copyOf(k);
		extra = x;
		forge = List.copyOf(f);
		shops = List.copyOf(s);
		npcs = n;
		npcAvail = av;
		stack = st;
		names = nm;
	}

	private static List<Mat> mats(JsonArray a) {
		List<Mat> out = new ArrayList<>();
		if (a != null) for (JsonElement e : a) out.add(new Mat(e.getAsJsonArray().get(0).getAsString(), e.getAsJsonArray().get(1).getAsInt()));
		return List.copyOf(out);
	}

	/** enchMax, enchMap, cur, minions, minProfit, minStore, comp, sc (as the site has them). */
	public static JsonElement extra(String key) {
		return extra.get(key);
	}

	public static List<Forge> forge() {
		return forge;
	}

	public static List<Kat> kat() {
		return kat;
	}

	/** "Common", "Uncommon", ... for a pet rarity number (0 = Common). */
	public static String rarity(int r) {
		return r >= 0 && r < RARITY.length ? RARITY[r] : "tier " + r;
	}

	public static List<Shop> shops() {
		return shops;
	}

	// ---- the site's ID and name helpers ----

	private static final Pattern BOOK = Pattern.compile("^(.+);(\\d+)$");
	private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"};
	private static final String[] RARITY = {"Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic"};

	/** INK_SACK-3 -> INK_SACK:3 */
	public static String bzId(String id) {
		return id.replace('-', ':');
	}

	public static boolean isBookId(String id) {
		return BOOK.matcher(id).matches() && names.getOrDefault(bzId(id), "").startsWith("Enchanted Book");
	}

	public static String bookBzId(String id) {
		Matcher m = BOOK.matcher(id);
		m.matches();
		return "ENCHANTMENT_" + m.group(1) + "_" + m.group(2);
	}

	/** The site's mktId: a book becomes its ENCHANTMENT_ product when that's on the bazaar; anything else stays as is. */
	public static String mktId(String id, Function<String, BazaarPrices.Price> prices) {
		if (isBookId(id)) {
			String b = bookBzId(id);
			if (prices.apply(b) != null) return b;
		}
		return id;
	}

	/** The bazaar product ID for an item: bzId(mktId(id)). */
	public static String marketId(String id, Function<String, BazaarPrices.Price> prices) {
		return bzId(mktId(id, prices));
	}

	static String roman(int n) {
		return n >= 0 && n < ROMAN.length ? ROMAN[n] : Integer.toString(n);
	}

	public static String prettyName(String id) {
		String n = names.get(bzId(id));
		return n != null ? n : Recipes.fallbackName(id);
	}

	/** Display name: books get "(book)", pets get their rarity, everything else its normal name. */
	public static String itemName(String id) {
		Matcher m = BOOK.matcher(id);
		if (isBookId(id)) {
			m.matches();
			return Recipes.fallbackName(m.group(1)) + " " + roman(Integer.parseInt(m.group(2))) + " (book)";
		}
		Matcher pet = Pattern.compile("^(.+);(\\d)$").matcher(id);
		if (pet.matches()) {
			int r = Integer.parseInt(pet.group(2));
			return prettyName(id).replaceAll(" \\((Common|Uncommon|Rare|Epic|Legendary|Mythic)\\)$", "")
					+ " (" + (r < RARITY.length ? RARITY[r] : "tier " + r) + ")";
		}
		return prettyName(id);
	}

	/** How many fit in one inventory slot (books don't stack; AH-only items are usually unique). */
	public static int stackOf(String id, boolean ahOnly) {
		if (id.startsWith("ENCHANTMENT_")) return 1;
		Integer s = stack.get(id);
		if (s == null) s = stack.get(bzId(id));
		return s != null ? s : ahOnly ? 1 : 64;
	}

	public static String npcName(String id) {
		Npc n = npcs.get(id);
		return n != null ? n.name() : prettyName(id.replaceAll("_NPC$", ""));
	}

	/** "Hub · 62, 73, -113" (plus "unconfirmed" when the spot isn't on current maps), the area alone, or "". */
	public static String npcWhere(String id) {
		Npc n = npcs.get(id);
		if (n == null) return "";
		if (n.xyz() == null) return n.area();
		return n.area() + " · " + n.xyz()[0] + ", " + n.xyz()[1] + ", " + n.xyz()[2] + (n.confirmed() ? "" : " (unconfirmed)");
	}

	/** Availability from the wiki: {"event"|"mayor"|"situational", why}, or null if always around. */
	public static String[] npcAvail(String id) {
		return npcAvail.get(id);
	}
}
