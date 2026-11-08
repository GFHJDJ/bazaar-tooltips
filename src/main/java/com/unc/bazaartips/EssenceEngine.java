package com.unc.bazaartips;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The website's Essence tab (scanEssence, outValue): buy the cheapest AH listing, salvage it (or sacrifice it for
 * Dragon Essence), sell what comes out. No Minecraft code, so it can be tested on its own.
 */
public final class EssenceEngine {
	private EssenceEngine() {}

	public static final LinkedHashMap<String, String> ESSENCE_NAMES = new LinkedHashMap<>();
	static {
		for (String[] e : new String[][] {{"WITHER", "Wither"}, {"UNDEAD", "Undead"}, {"DRAGON", "Dragon"}, {"SPIDER", "Spider"}, {"GOLD", "Gold"},
				{"DIAMOND", "Diamond"}, {"ICE", "Ice"}, {"CRIMSON", "Crimson"}, {"FOREST", "Forest"}, {"FOSSIL", "Fossil"}}) ESSENCE_NAMES.put(e[0], e[1]);
	}

	/** One thing an item turns into: essence (type) or an item (id), and how many. */
	public record Out(boolean essence, String type, String id, double amount) {
		public String label() {
			return essence ? ESSENCE_NAMES.getOrDefault(type, SiteData.prettyName(type)) + " Essence" : SiteData.prettyName(id);
		}
	}

	public record Salvage(String name, String tier, List<Out> outs) {}

	/** salvage / salvages from Hypixel's item list (the site's loadSalvageMapOnce). */
	static Map<String, Salvage> parseSalvage(JsonArray items) {
		Map<String, Salvage> map = new LinkedHashMap<>();
		if (items == null) return map;
		for (JsonElement e : items) {
			JsonObject it = e.getAsJsonObject();
			List<JsonElement> raw = new ArrayList<>();
			if (it.has("salvages") && it.get("salvages").isJsonArray()) it.getAsJsonArray("salvages").forEach(raw::add);
			else if (it.has("salvage") && it.get("salvage").isJsonObject()) raw.add(it.get("salvage"));
			List<Out> outs = new ArrayList<>();
			for (JsonElement r : raw) {
				JsonObject s = r.getAsJsonObject();
				double amt = s.has("amount") ? s.get("amount").getAsDouble() : 0;
				if (!(amt > 0)) continue;
				if (s.has("essence_type")) outs.add(new Out(true, s.get("essence_type").getAsString().toUpperCase(Locale.ROOT), null, amt));
				else if (s.has("item_id")) outs.add(new Out(false, null, s.get("item_id").getAsString(), amt));
			}
			if (!outs.isEmpty() && it.has("id")) {
				String id = it.get("id").getAsString();
				String name = it.has("name") ? it.get("name").getAsString().replaceAll("§.", "") : "";
				map.put(id, new Salvage(name.isEmpty() ? SiteData.prettyName(id) : name, it.has("tier") ? it.get("tier").getAsString() : "", List.copyOf(outs)));
			}
		}
		return map;
	}

	/** A live lowest-BIN check from SkyCofl: ok (lowest, next lowest, auction id), none (not listed), or err. */
	public record BinCheck(long at, String status, double lowest, double second, String uuid) {}

	/** What one output is worth: each, total, and where it sells ("Bazaar", "AH (lowest BIN, before AH fees)" or "no price"). */
	public record Val(Out o, String id, double each, double total, String where) {}

	public record Ess(String id, String name, String tier, double cost, double value, double profit, double pct, List<Val> vals,
			List<String> types, BinCheck check, boolean checking, boolean outlier, boolean sacrifice) {}

	public record ECtx(Function<String, BazaarPrices.Price> prices, Function<String, Double> ahBin, double tax, boolean sellOffer, boolean bazaarOnly) {}

	static Val outValue(ECtx x, Out o) {
		String id = o.essence() ? "ESSENCE_" + o.type() : o.id();
		BazaarPrices.Price p = x.prices().apply(id);
		if (p != null) {
			double each = (x.sellOffer() ? p.quickBuy() : p.quickSell()) * (1 - x.tax());
			return new Val(o, id, each, each * o.amount(), "Bazaar");
		}
		Double l = x.ahBin().apply(id);
		if (l != null && l > 0) return new Val(o, id, l, l * o.amount(), "AH (lowest BIN, before AH fees)");
		return new Val(o, id, 0, 0, "no price");
	}

	/** scanEssence: everything that salvages into something, priced. */
	public static List<Ess> scan(ECtx x, Map<String, Salvage> map, Function<String, BinCheck> checks) {
		List<Ess> out = new ArrayList<>();
		for (var e : map.entrySet()) {
			String id = e.getKey();
			Salvage s = e.getValue();
			Double bulk = x.ahBin().apply(id);
			if (bulk == null || !(bulk > 0)) continue;
			double cost = bulk;
			BinCheck chk = checks.apply(id);
			if (chk != null && chk.status().equals("none")) continue;      // checked: not listed anymore
			boolean outlier = false;
			if (chk != null && chk.status().equals("ok")) {
				cost = chk.lowest();
				// a lone very cheap listing is usually a mistake that gets bought in seconds: price it at the next one
				if (chk.second() > 0 && chk.lowest() < chk.second() * 0.5) { cost = chk.second(); outlier = true; }
			}
			List<Val> vals = new ArrayList<>();
			boolean noPrice = false, ahOut = false;
			for (Out o : s.outs()) {
				Val v = outValue(x, o);
				vals.add(v);
				if (v.where().equals("no price")) noPrice = true;
				if (!v.where().equals("Bazaar")) ahOut = true;
			}
			if (noPrice || (x.bazaarOnly() && ahOut)) continue;
			double value = 0;
			for (Val v : vals) value += v.total();
			LinkedHashSet<String> types = new LinkedHashSet<>();
			for (Out o : s.outs()) if (o.essence()) types.add(o.type());
			out.add(new Ess(id, s.name(), s.tier(), cost, value, value - cost, (value - cost) / cost * 100, List.copyOf(vals), List.copyOf(types),
					chk != null && chk.status().equals("ok") ? chk : null, chk == null || chk.status().equals("err"), outlier, types.contains("DRAGON")));
		}
		return out;
	}

	public static final List<CraftEngine.SortOption> SORTS = List.of(
			new CraftEngine.SortOption("profit", "Profit", "Profit", "Essence value minus the AH price, highest first."),
			new CraftEngine.SortOption("pct", "Return %", "Return %", "Profit as a % of the AH price."),
			new CraftEngine.SortOption("value", "Essence value", "Value", "What everything it turns into sells for."),
			new CraftEngine.SortOption("cost", "Cheapest to buy", "Cheapest", "Lowest AH price first."),
			new CraftEngine.SortOption("name", "Name", "Name", "Alphabetical."));

	/** The list: search, essence type ("all" or a type), profitable only, sorted. */
	public static List<Ess> list(List<Ess> all, String search, String type, boolean profitableOnly, String key) {
		String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		List<Ess> list = new ArrayList<>();
		for (Ess e : all) {
			if (!q.isEmpty() && !e.name().toLowerCase(Locale.ROOT).contains(q) && !e.id().toLowerCase(Locale.ROOT).contains(q)) continue;
			if (!type.equals("all") && !e.types().contains(type)) continue;
			if (profitableOnly && !(e.profit() > 0)) continue;
			list.add(e);
		}
		Comparator<Ess> c = switch (key) {
			case "name" -> Comparator.comparing(Ess::name);
			case "cost" -> (a, b) -> Double.compare(a.cost(), b.cost());
			case "pct" -> (a, b) -> Double.compare(b.pct(), a.pct());
			case "value" -> (a, b) -> Double.compare(b.value(), a.value());
			default -> (a, b) -> Double.compare(b.profit(), a.profit());
		};
		list.sort(c);
		return list;
	}
}
