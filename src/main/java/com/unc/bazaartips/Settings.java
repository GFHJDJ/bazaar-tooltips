package com.unc.bazaartips;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Everything you set on the screens, saved to config/bazaartips.json so it's still there next time
 * (the website keeps the same things in your browser). No Minecraft code, so it can be tested on its own.
 */
public final class Settings {
	/** Which list the flips screen shows: the site's two Craft tabs. */
	public enum Buy {
		INSTA("Insta-buy"), ORDER("Buy order");
		public final String label;
		Buy(String label) { this.label = label; }
	}

	/** Craft chains: the site's "chain" filter. */
	public enum Chains {
		EXCLUDE("Off", "Ingredients are always bought on the bazaar, never crafted from cheaper parts."),
		ALL("On", "When an ingredient is cheaper to craft from its own parts than to buy, it's crafted (and that's the price used)."),
		ONLY("Only chains", "Uses cheaper crafted parts, and shows only crafts where that actually happens.");
		public final String label, explanation;
		Chains(String label, String explanation) { this.label = label; this.explanation = explanation; }
	}

	/** Personal Compactor filter. */
	public enum Compactor {
		ANY("Show all", "Shows every craft."),
		ONLY("Only these", "Shows only crafts a Personal Compactor can make for you automatically."),
		HIDE("Hide these", "Hides crafts a Personal Compactor can make for you automatically.");
		public final String label, explanation;
		Compactor(String label, String explanation) { this.label = label; this.explanation = explanation; }
	}

	/** Requirement filter. */
	public enum Reqs {
		ALL("Show all", "Shows every craft, whatever it needs unlocked."),
		NONE("No requirement", "Shows only crafts with no collection, slayer or other unlock needed."),
		UNLOCKED("I've unlocked", "Shows only crafts you've unlocked (load your unlocks below).");
		public final String label, explanation;
		Reqs(String label, String explanation) { this.label = label; this.explanation = explanation; }
	}

	/** Tax presets, same as the site's settings. */
	public static final double[] TAX_PRESETS = {1.25, 1.125, 1.0};

	public static Buy buy = Buy.INSTA;
	public static String sort = "profitSellOrder";
	public static Chains chains = Chains.EXCLUDE;
	public static Compactor compactor = Compactor.ANY;
	public static Reqs reqs = Reqs.ALL;
	public static boolean hideLowVolume = false;
	public static double taxPercent = 1.25;
	/** Your coins as typed ("70m", "250k", "1.2b", "3,500,000"). */
	public static String coins = "";
	/** Free inventory slots as typed. */
	public static String slots = "";
	/** What you've unlocked: requirement key (e.g. "collection:Diamond") -> tier. */
	public static Map<String, Integer> unlocks = new HashMap<>();
	public static String lookupName = "";
	public static String lookupProfile = "";

	// ---- the Flipper, NPC and Forge tabs (the site keeps these in your browser too) ----
	/** Which tab B opens: crafts, flips, npc or forge (the last one you used). */
	public static String tab = "crafts";
	public static String flipSort = "score";
	public static String forgeSort = "coins";
	public static String npcSort = "coins";
	public static String npcShopSort = "coins";
	/** Flipper: hide items flagged as likely manipulated (the site's "Hide risky", on by default). */
	public static boolean hideRisky = true;
	/** Flipper: hide flips scoring under this (0 = show all). */
	public static int flipMinScore = 0;
	/** Chat alerts for flips that reach flipAlertScore. */
	public static boolean flipAlerts = false;
	/** Gear settings: the tab the key opens ("last" = the one you used last), tabs you hid, and what item tooltips show. */
	public static String startTab = "last";
	public static java.util.LinkedHashSet<String> hiddenTabs = new java.util.LinkedHashSet<>();
	public static boolean tooltips = true, tooltipCrafts = true;
	public static int flipAlertScore = 80;
	/** NPC & Forge: buy materials with buy orders (else insta-buy). */
	public static boolean moneyBuyOrder = false;
	/** NPC & Forge: sell on the bazaar with sell offers (else insta-sell). */
	public static boolean moneySellOffer = false;
	/** NPC & Forge: hide things you'd have to sell on the AH. */
	public static boolean bazaarOnly = true;
	/** NPC shops: "always" (hide event, mayor & situational NPCs), "nomayor" or "all". */
	public static String npcAvail = "always";
	/** NPC tab view: "bz" (Bazaar -> NPC) or "shop" (NPC shop -> Bazaar/AH). */
	public static String npcView = "bz";
	/** Your Heart of the Mountain tier for the Forge tab (0 = any). */
	public static int forgeHotm = 0;

	// ---- Prices, Books, Minions, Profit / hr, Essence, Currencies ----
	public static String pricesSort = "margin";
	public static String bookSort = "coins";
	public static String minionSort = "total";
	public static String profitSort = "total";
	public static String essenceSort = "profit";
	public static String curSort = "per";
	/** Profit / hr: tier, where drops are sold (best / bz / npc), fuel, two upgrades, auto-pick, must-use / never-use, hopper, chest. */
	public static int profitTier = 11;
	public static String profitSell = "best";
	public static String profitFuel = "None";
	public static String profitUp1 = "None", profitUp2 = "None";
	public static boolean profitAuto = false;
	/** Coin advisor: sort, which kind of idea to show ("all", "flip", ...), kinds and item ids you left out. */
	public static String planSort = "coins", planSrc = "all";
	/** Earnings tab: the period shown ("session", "today", "week", "all") and the sort. */
	public static String earnPeriod = "session", earnSort = "profit";
	/** Kat, Bits and AH tabs: their sorts, and the AH tab's view ("arb", "craft" or "lookup"). */
	public static String katSort = "profit", bitsSort = "per", ahView = "arb", ahSort = "profit";
	/** Shards tab: its sort, and whether fusions that lose coins are hidden. */
	public static String shardSort = "profit";
	public static boolean shardProfitOnly = true;
	/** The session profit box on your screen and where it sits; the Worth panel beside your inventory (closed until you click Worth). */
	public static String earnHudCorner = "top-right";
	public static boolean earnHud = true, invPanel = false;
	/** The Worth button and panel go on the left of your inventory (true) or the right. */
	public static boolean invPanelLeft = true;
	/** Download new versions from GitHub and install them when the game closes. */
	public static boolean autoUpdate = true;
	public static java.util.LinkedHashSet<String> planExSrc = new java.util.LinkedHashSet<>(), planEx = new java.util.LinkedHashSet<>();
	public static java.util.LinkedHashSet<String> profitRequire = new java.util.LinkedHashSet<>();
	public static java.util.LinkedHashSet<String> profitExclude = new java.util.LinkedHashSet<>();
	public static String profitHopper = "No hopper";
	public static String profitChest = "none";
	/** Essence: which essence ("all" or a type), sell by sell offer, profitable only. */
	public static String essenceType = "all";
	public static boolean essenceSellOffer = false;
	public static boolean essenceProfitableOnly = true;
	/** Currencies: which currency. */
	public static String curType = "copper";
	/** Chat alert when one of your bazaar orders is outbid or undercut. */
	public static boolean orderAlerts = true;
	/** The panel beside a bazaar item's page, and the orders box on your screen (and which corner it sits in). */
	public static boolean bazaarOverlay = true;
	public static boolean orderHud = true;
	public static String orderHudCorner = "top-left";

	private static Path file;

	private Settings() {}

	public static double tax() {
		return taxPercent / 100;
	}

	public static String taxLabel() {
		String s = String.format(Locale.US, "%.3f", taxPercent).replaceAll("0+$", "").replaceAll("\\.$", "");
		return s + "%";
	}

	/** "5m", "250k", "1.2b", "3,500,000" -> coins (0 if empty or not a number), like the site's parseCoins. */
	public static double parseCoins(String v) {
		String s = (v == null ? "" : v).trim().toLowerCase(Locale.ROOT).replaceAll("[, _]", "");
		var m = java.util.regex.Pattern.compile("^(\\d*\\.?\\d+)([kmb])?$").matcher(s);
		if (!m.matches()) return 0;
		double n = Double.parseDouble(m.group(1));
		String unit = m.group(2);
		return unit == null ? n : n * (unit.equals("k") ? 1e3 : unit.equals("m") ? 1e6 : 1e9);
	}

	public static double coinsBudget() {
		return parseCoins(coins);
	}

	public static int freeSlots() {
		try {
			int n = Integer.parseInt(slots.trim());
			return Math.max(n, 0);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** Loads saved settings (call once at startup). Missing or broken files just mean defaults. */
	public static void load(Path configFile) {
		file = configFile;
		try {
			if (!Files.exists(file)) return;
			JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			buy = enumOr(o, "buy", Buy.class, buy);
			if (o.has("sort")) sort = o.get("sort").getAsString();
			chains = enumOr(o, "chains", Chains.class, chains);
			compactor = enumOr(o, "compactor", Compactor.class, compactor);
			reqs = enumOr(o, "reqs", Reqs.class, reqs);
			if (o.has("hideLowVolume")) hideLowVolume = o.get("hideLowVolume").getAsBoolean();
			if (o.has("taxPercent")) {
				double t = o.get("taxPercent").getAsDouble();
				if (t >= 0 && t <= 50) taxPercent = t;
			}
			if (o.has("coins")) coins = o.get("coins").getAsString();
			if (o.has("slots")) slots = o.get("slots").getAsString();
			if (o.has("rounded")) Format.setRounded(o.get("rounded").getAsBoolean());
			if (o.has("lookupName")) lookupName = o.get("lookupName").getAsString();
			if (o.has("lookupProfile")) lookupProfile = o.get("lookupProfile").getAsString();
			tab = str(o, "tab", tab, "crafts", "flips", "npc", "forge", "prices", "books", "minions", "profit", "essence", "cur", "orders", "plan", "earned", "kat", "bits", "ah", "alerts", "shards");
			for (String k : new String[] {"pricesSort", "bookSort", "minionSort", "profitSort", "essenceSort", "curSort", "profitSell", "profitFuel",
					"profitUp1", "profitUp2", "profitHopper", "profitChest", "essenceType", "curType", "planSort", "planSrc", "earnPeriod", "earnSort", "katSort", "bitsSort", "ahView", "ahSort", "shardSort"}) {
				if (o.has(k)) Settings.class.getField(k).set(null, o.get(k).getAsString());
			}
			if (o.has("orderAlerts")) orderAlerts = o.get("orderAlerts").getAsBoolean();
			if (o.has("bazaarOverlay")) bazaarOverlay = o.get("bazaarOverlay").getAsBoolean();
			if (o.has("orderHud")) orderHud = o.get("orderHud").getAsBoolean();
			if (o.has("earnHud")) earnHud = o.get("earnHud").getAsBoolean();
			if (o.has("invPanel")) invPanel = o.get("invPanel").getAsBoolean();
			if (o.has("invPanelLeft")) invPanelLeft = o.get("invPanelLeft").getAsBoolean();
			if (o.has("autoUpdate")) autoUpdate = o.get("autoUpdate").getAsBoolean();
			if (o.has("shardProfitOnly")) shardProfitOnly = o.get("shardProfitOnly").getAsBoolean();
			earnHudCorner = str(o, "earnHudCorner", earnHudCorner, "top-left", "top-right", "middle-left");
			orderHudCorner = str(o, "orderHudCorner", orderHudCorner, "top-left", "top-right", "middle-left");
			if (o.has("profitTier")) profitTier = Math.max(1, Math.min(12, o.get("profitTier").getAsInt()));
			if (o.has("profitAuto")) profitAuto = o.get("profitAuto").getAsBoolean();
			if (o.has("essenceSellOffer")) essenceSellOffer = o.get("essenceSellOffer").getAsBoolean();
			if (o.has("essenceProfitableOnly")) essenceProfitableOnly = o.get("essenceProfitableOnly").getAsBoolean();
			if (o.has("profitRequire")) { profitRequire.clear(); o.getAsJsonArray("profitRequire").forEach(e -> profitRequire.add(e.getAsString())); }
			if (o.has("profitExclude")) { profitExclude.clear(); o.getAsJsonArray("profitExclude").forEach(e -> profitExclude.add(e.getAsString())); }
			if (o.has("planExSrc")) { planExSrc.clear(); o.getAsJsonArray("planExSrc").forEach(e -> planExSrc.add(e.getAsString())); }
			if (o.has("planEx")) { planEx.clear(); o.getAsJsonArray("planEx").forEach(e -> planEx.add(e.getAsString())); }
			if (o.has("flipSort")) flipSort = o.get("flipSort").getAsString();
			if (o.has("forgeSort")) forgeSort = o.get("forgeSort").getAsString();
			if (o.has("npcSort")) npcSort = o.get("npcSort").getAsString();
			if (o.has("npcShopSort")) npcShopSort = o.get("npcShopSort").getAsString();
			if (o.has("hideRisky")) hideRisky = o.get("hideRisky").getAsBoolean();
			startTab = str(o, "startTab", startTab, "last", "crafts", "flips", "plan", "npc", "forge", "prices", "books", "minions", "profit", "essence", "cur", "orders", "earned", "kat", "bits", "ah", "alerts", "shards");
			if (o.has("hiddenTabs")) { hiddenTabs.clear(); o.getAsJsonArray("hiddenTabs").forEach(e -> hiddenTabs.add(e.getAsString())); }
			if (o.has("tooltips")) tooltips = o.get("tooltips").getAsBoolean();
			if (o.has("tooltipCrafts")) tooltipCrafts = o.get("tooltipCrafts").getAsBoolean();
			if (o.has("flipAlerts")) flipAlerts = o.get("flipAlerts").getAsBoolean();
			if (o.has("flipAlertScore")) flipAlertScore = Math.max(1, Math.min(100, o.get("flipAlertScore").getAsInt()));
			if (o.has("flipMinScore")) flipMinScore = Math.max(0, Math.min(100, o.get("flipMinScore").getAsInt()));
			if (o.has("moneyBuyOrder")) moneyBuyOrder = o.get("moneyBuyOrder").getAsBoolean();
			if (o.has("moneySellOffer")) moneySellOffer = o.get("moneySellOffer").getAsBoolean();
			if (o.has("bazaarOnly")) bazaarOnly = o.get("bazaarOnly").getAsBoolean();
			npcAvail = str(o, "npcAvail", npcAvail, "always", "nomayor", "all");
			npcView = str(o, "npcView", npcView, "bz", "shop");
			if (o.has("forgeHotm")) forgeHotm = Math.max(0, Math.min(10, o.get("forgeHotm").getAsInt()));
			if (o.has("unlocks")) {
				Map<String, Integer> u = new HashMap<>();
				for (var e : o.getAsJsonObject("unlocks").entrySet()) u.put(e.getKey(), e.getValue().getAsInt());
				unlocks = u;
			}
		} catch (Exception e) {
			System.getLogger("bazaartips").log(System.Logger.Level.WARNING, "Couldn't read settings, using defaults: " + e);
		}
	}

	/** Saves right away. Called after every change on the screens. */
	public static void save() {
		if (file == null) return;
		try {
			JsonObject o = new JsonObject();
			o.addProperty("buy", buy.name());
			o.addProperty("sort", sort);
			o.addProperty("chains", chains.name());
			o.addProperty("compactor", compactor.name());
			o.addProperty("reqs", reqs.name());
			o.addProperty("hideLowVolume", hideLowVolume);
			o.addProperty("taxPercent", taxPercent);
			o.addProperty("coins", coins);
			o.addProperty("slots", slots);
			o.addProperty("rounded", Format.rounded());
			o.addProperty("lookupName", lookupName);
			o.addProperty("lookupProfile", lookupProfile);
			o.addProperty("tab", tab);
			o.addProperty("flipSort", flipSort);
			o.addProperty("forgeSort", forgeSort);
			o.addProperty("npcSort", npcSort);
			o.addProperty("npcShopSort", npcShopSort);
			o.addProperty("hideRisky", hideRisky);
			o.addProperty("flipMinScore", flipMinScore);
			o.addProperty("flipAlerts", flipAlerts);
			o.addProperty("startTab", startTab);
			com.google.gson.JsonArray ht = new com.google.gson.JsonArray();
			hiddenTabs.forEach(ht::add);
			o.add("hiddenTabs", ht);
			o.addProperty("tooltips", tooltips);
			o.addProperty("tooltipCrafts", tooltipCrafts);
			o.addProperty("flipAlertScore", flipAlertScore);
			o.addProperty("moneyBuyOrder", moneyBuyOrder);
			o.addProperty("moneySellOffer", moneySellOffer);
			o.addProperty("bazaarOnly", bazaarOnly);
			o.addProperty("npcAvail", npcAvail);
			o.addProperty("npcView", npcView);
			o.addProperty("forgeHotm", forgeHotm);
			for (String k : new String[] {"pricesSort", "bookSort", "minionSort", "profitSort", "essenceSort", "curSort", "profitSell", "profitFuel",
					"profitUp1", "profitUp2", "profitHopper", "profitChest", "essenceType", "curType", "planSort", "planSrc", "earnPeriod", "earnSort", "katSort", "bitsSort", "ahView", "ahSort", "shardSort"}) {
				o.addProperty(k, (String) Settings.class.getField(k).get(null));
			}
			o.addProperty("orderAlerts", orderAlerts);
			o.addProperty("bazaarOverlay", bazaarOverlay);
			o.addProperty("orderHud", orderHud);
			o.addProperty("earnHud", earnHud);
			o.addProperty("invPanel", invPanel);
			o.addProperty("invPanelLeft", invPanelLeft);
			o.addProperty("autoUpdate", autoUpdate);
			o.addProperty("shardProfitOnly", shardProfitOnly);
			o.addProperty("earnHudCorner", earnHudCorner);
			o.addProperty("orderHudCorner", orderHudCorner);
			o.addProperty("profitTier", profitTier);
			o.addProperty("profitAuto", profitAuto);
			o.addProperty("essenceSellOffer", essenceSellOffer);
			o.addProperty("essenceProfitableOnly", essenceProfitableOnly);
			com.google.gson.JsonArray req = new com.google.gson.JsonArray(), ex = new com.google.gson.JsonArray();
			profitRequire.forEach(req::add);
			profitExclude.forEach(ex::add);
			o.add("profitRequire", req);
			o.add("profitExclude", ex);
			com.google.gson.JsonArray pxs = new com.google.gson.JsonArray(), px = new com.google.gson.JsonArray();
			planExSrc.forEach(pxs::add);
			planEx.forEach(px::add);
			o.add("planExSrc", pxs);
			o.add("planEx", px);
			JsonObject u = new JsonObject();
			unlocks.forEach(u::addProperty);
			o.add("unlocks", u);
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			Files.createDirectories(file.getParent());
			Files.writeString(file, gson.toJson(o), StandardCharsets.UTF_8);
		} catch (Exception e) {
			System.getLogger("bazaartips").log(System.Logger.Level.WARNING, "Couldn't save settings: " + e);
		}
	}

	private static String str(JsonObject o, String key, String fallback, String... allowed) {
		if (!o.has(key)) return fallback;
		String v = o.get(key).getAsString();
		for (String a : allowed) if (a.equals(v)) return v;
		return fallback;
	}

	/** Steps the tax through the presets (1.25% -> 1.125% -> 1%); a custom tax goes back to 1.25%. */
	public static void nextTaxPreset() {
		stepTaxPreset(1);
	}

	/** The next (+1) or previous (-1) tax preset; a custom tax goes to the first (or last) preset. */
	public static void stepTaxPreset(int dir) {
		taxPercent = Cycle.step(TAX_PRESETS, taxPercent, dir);
	}

	private static <E extends Enum<E>> E enumOr(JsonObject o, String key, Class<E> type, E fallback) {
		try {
			return o.has(key) ? Enum.valueOf(type, o.get(key).getAsString()) : fallback;
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}
}
