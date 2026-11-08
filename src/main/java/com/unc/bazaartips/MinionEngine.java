package com.unc.bazaartips;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The website's Minions tab (computeMinions + buildMinionCumCosts: what each minion costs to build, tier by tier)
 * and Profit / hr tab (computeProfit, evalConfig, bestConfig: coins per hour from each minion's drops).
 * Uses Hypixel's averaged prices like the site. No Minecraft code, so it can be tested on its own.
 */
public final class MinionEngine {
	private MinionEngine() {}

	// ---------------------------------------------------------------- Minions

	/** A material line: qty x name, insta-buy cost (buy) and buy-order cost (sell); missing = no bazaar price. */
	public record Mat(String name, double qty, String id, double buy, double sell, boolean missing) {}

	/** One tier: this tier's materials and cost, and the running total from the first tier. */
	public record Tier(int tier, List<Mat> mats, double insta, double order, double cumI, double cumO, List<Mat> cumMats) {}

	public record Minion(String key, String name, int maxTier, double total, double totalOrder, List<Tier> tiers, String req,
			String source, int fromTier, boolean partial) {
		public Tier tier(int t) {
			for (Tier x : tiers) if (x.tier() == t) return x;
			return null;
		}
	}

	private record Built(Map<Integer, double[]> tierCum, List<Tier> tiers, double cumI, double cumO, JsonObject mn) {}

	private static List<Integer> tierNums(JsonObject tiers) {
		List<Integer> nums = new ArrayList<>();
		for (String k : tiers.keySet()) nums.add(Integer.parseInt(k));
		nums.sort(Integer::compare);
		return nums;
	}

	private static Map<String, Built> buildCum(Function<String, BazaarPrices.Price> prices, boolean resolveRefs, Map<String, Map<Integer, double[]>> cumBy) {
		JsonObject all = SiteData.extra("minions").getAsJsonObject();
		Map<String, Built> result = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : all.entrySet()) {
			JsonObject mn = e.getValue().getAsJsonObject();
			JsonObject tiers = mn.getAsJsonObject("tiers");
			double cumI = 0, cumO = 0;
			List<Tier> out = new ArrayList<>();
			Map<String, Double> cumQty = new LinkedHashMap<>();
			List<Mat> cumRefs = new ArrayList<>();
			Map<Integer, double[]> tierCum = new LinkedHashMap<>();
			for (int t : tierNums(tiers)) {
				double insta = 0, order = 0;
				List<Mat> mats = new ArrayList<>();
				for (JsonElement ie : tiers.getAsJsonArray(Integer.toString(t))) {
					JsonObject ing = ie.getAsJsonObject();
					double qty = ing.get("qty").getAsDouble();
					if (ing.has("ref")) {
						if (!resolveRefs) continue;            // pass 1: skip, no circular dependency risk
						String ref = ing.get("ref").getAsString();
						int rt = ing.get("tier").getAsInt();
						double[] refCum = cumBy.containsKey(ref) ? cumBy.get(ref).get(rt) : null;
						String refName = (all.has(ref) ? all.getAsJsonObject(ref).get("name").getAsString() : ref) + " " + SiteData.roman(rt) + " (built)";
						Mat row = refCum != null ? new Mat(refName, qty, null, qty * refCum[0], qty * refCum[1], false)
								: new Mat(refName, qty, null, 0, 0, true);
						if (refCum != null) { insta += qty * refCum[0]; order += qty * refCum[1]; }
						mats.add(row);
						cumRefs.add(row);
						continue;
					}
					String id = ing.get("id").getAsString();
					BazaarPrices.Price p = prices.apply(SiteData.bzId(id));
					String nm = SiteData.prettyName(id);
					cumQty.merge(id, qty, Double::sum);
					if (p == null) { mats.add(new Mat(nm, qty, id, 0, 0, true)); continue; }
					insta += qty * p.quickBuy();
					order += qty * p.quickSell();
					mats.add(new Mat(nm, qty, id, qty * p.quickBuy(), qty * p.quickSell(), false));
				}
				cumI += insta;
				cumO += order;
				List<Mat> cumMats = new ArrayList<>();
				for (var c : cumQty.entrySet()) {
					BazaarPrices.Price p = prices.apply(SiteData.bzId(c.getKey()));
					String nm = SiteData.prettyName(c.getKey());
					double q = c.getValue();
					cumMats.add(p != null ? new Mat(nm, q, c.getKey(), q * p.quickBuy(), q * p.quickSell(), false) : new Mat(nm, q, c.getKey(), 0, 0, true));
				}
				cumMats.addAll(cumRefs);
				out.add(new Tier(t, List.copyOf(mats), insta, order, cumI, cumO, List.copyOf(cumMats)));
				tierCum.put(t, new double[] {cumI, cumO});
			}
			result.put(e.getKey(), new Built(tierCum, out, cumI, cumO, mn));
		}
		return result;
	}

	/** computeMinions: every minion's build cost (two passes, so minions built from other minions use their cost). */
	public static List<Minion> minions(Function<String, BazaarPrices.Price> prices) {
		Map<String, Built> pass1 = buildCum(prices, false, Map.of());
		Map<String, Map<Integer, double[]>> cumBy = new LinkedHashMap<>();
		pass1.forEach((k, v) -> cumBy.put(k, v.tierCum()));
		List<Minion> out = new ArrayList<>();
		for (var e : buildCum(prices, true, cumBy).entrySet()) {
			Built r = e.getValue();
			JsonObject mn = r.mn();
			List<Integer> nums = tierNums(mn.getAsJsonObject("tiers"));
			int from = nums.isEmpty() ? 1 : nums.get(0);
			out.add(new Minion(e.getKey(), mn.get("name").getAsString(), mn.get("maxTier").getAsInt(), r.cumI(), r.cumO(), r.tiers(),
					str(mn, "req"), str(mn, "source"), from, from > 1));
		}
		return out;
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
	}

	public static final List<CraftEngine.SortOption> MINION_SORTS = List.of(
			new CraftEngine.SortOption("total", "Cost to build", "Cost to build", "Cheapest to build (to its top tier, insta-buying) first."),
			new CraftEngine.SortOption("name", "Name", "Name", "Alphabetical."));

	public static List<Minion> sortMinions(List<Minion> all, String key, String search) {
		String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		List<Minion> list = new ArrayList<>();
		for (Minion m : all) if (m.name().toLowerCase(Locale.ROOT).contains(q)) list.add(m);
		list.sort(key.equals("name") ? Comparator.comparing(Minion::name) : (a, b) -> Double.compare(a.total(), b.total()));
		return list;
	}

	// ---------------------------------------------------------------- Profit / hr

	/** Fuels: {speed bonus, drop multiplier}. */
	public static final LinkedHashMap<String, double[]> FUELS = new LinkedHashMap<>();
	public static final List<String> UPGRADES = List.of("None", "Super Compactor 3000", "Compactor", "Diamond Spreading", "Potato Spreading",
			"Soulflow Engine", "Lesser Soulflow Engine", "Minion Expander", "Flycatcher", "Corrupt Soil");
	public static final List<String> AUTO_FUELS = List.of("None", "Enchanted Lava Bucket", "Magma Bucket", "Plasma Bucket");
	/** Hopper -> share of the NPC price it sells for. */
	public static final LinkedHashMap<String, Double> HOPPERS = new LinkedHashMap<>();
	/** Storage chests: {key, name, extra slots}. */
	public static final String[][] CHESTS = {{"none", "No storage chest", "0"}, {"small", "Small Storage", "3"}, {"medium", "Medium Storage", "9"},
			{"large", "Large Storage", "15"}, {"xlarge", "X-Large Storage", "21"}, {"xxlarge", "XX-Large Storage", "27"}};
	private static final Map<String, String> PLABEL = Map.of("SMALL_FLOWER", "Flowers (short)", "TALL_FLOWER", "Flowers (tall)",
			"DYE_FLAME", "Flame Dye", "SNOW_BALL", "Snowball", "CRUDE_GABAGOOL", "Crude Gabagool");

	static {
		FUELS.put("None", new double[] {0, 1});
		FUELS.put("Enchanted Coal", new double[] {0.10, 1});
		FUELS.put("Enchanted Charcoal", new double[] {0.20, 1});
		FUELS.put("Solar Panel (day)", new double[] {0.25, 1});
		FUELS.put("Enchanted Lava Bucket", new double[] {0.25, 1});
		FUELS.put("Magma Bucket", new double[] {0.30, 1});
		FUELS.put("Plasma Bucket", new double[] {0.35, 1});
		FUELS.put("Hamster Wheel", new double[] {0.50, 1});
		FUELS.put("Catalyst (3x drops)", new double[] {0, 3});
		FUELS.put("Hyper Catalyst (4x drops)", new double[] {0, 4});
		HOPPERS.put("No hopper", 1.0);
		HOPPERS.put("Budget Hopper (50% NPC)", 0.5);
		HOPPERS.put("Enchanted Hopper (70% NPC, per official wiki)", 0.7);
		HOPPERS.put("Enchanted Hopper (90% NPC, per other sources)", 0.9);
	}

	/** Everything Profit / hr depends on. sell = "best", "bz" or "npc"; sellOffer = sell on the bazaar with sell offers. */
	public record PCtx(Function<String, BazaarPrices.Price> prices, double tax, boolean sellOffer, String sell, int tier, String fuel,
			String up1, String up2, boolean auto, Set<String> require, Set<String> exclude, String hopper) {}

	/** One drop line: per hour, price each, where it's sold (Bazaar / NPC / NPC* = not on bazaar), coins per hour, which upgrade adds it. */
	public record PRow(String name, String id, double perHour, double price, String mk, double sub, String bonus) {}

	public record Config(double total, List<PRow> rows, double s, String fuel, String up1, String up2) {}

	public record Profit(String name, String type, int tier, boolean usedMax, double actionSec, Integer storeItems, double stacksPerHour,
			double perDrop, double total, List<PRow> rows, String fuel, String up1, String up2, Double paybackInsta, Double paybackOrder) {
		/** Hours until the minion's storage (plus a chest) is full, or null. */
		public Double fillHours(int chestSlots) {
			if (storeItems == null || storeItems == 0 || !(stacksPerHour > 0)) return null;
			return (storeItems / 64.0 + chestSlots) / stacksPerHour;
		}

		public String combo() {
			List<String> p = new ArrayList<>();
			for (String s : new String[] {fuel, up1, up2}) if (!s.equals("None")) p.add(s);
			return p.isEmpty() ? "no fuel or upgrades" : String.join(" + ", p);
		}
	}

	private record Price(double price, String mk) {}

	private static Price priceFor(PCtx x, String id, double npc) {
		BazaarPrices.Price p = x.prices().apply(id);
		double bz = p != null && p.quickSell() > 0 && p.quickBuy() > 0 ? (x.sellOffer() ? p.quickBuy() : p.quickSell()) * (1 - x.tax()) : 0;
		npc = npc * HOPPERS.getOrDefault(x.hopper(), 1.0);
		if (x.sell().equals("npc")) return new Price(npc, "NPC");
		if (x.sell().equals("bz")) return bz > 0 ? new Price(bz, "Bazaar") : new Price(npc, "NPC*");
		return bz > npc ? new Price(bz, "Bazaar") : new Price(npc, "NPC");
	}

	private record Ench(String item, double count) {}

	private static Ench enchantItem(String item, String up1, String up2) {
		boolean comp = up1.equals("Compactor") || up2.equals("Compactor");
		boolean sc = up1.equals("Super Compactor 3000") || up2.equals("Super Compactor 3000");
		JsonObject C = SiteData.extra("comp").getAsJsonObject(), SC = SiteData.extra("sc").getAsJsonObject();
		String cur = item;
		double count = 1;
		for (int guard = 0; guard < 12; guard++) {
			String nxt = "";
			if (comp && C.has(cur)) { nxt = C.getAsJsonObject(cur).get("to").getAsString(); count *= C.getAsJsonObject(cur).get("c").getAsDouble(); }
			if (sc && SC.has(cur)) { nxt = SC.getAsJsonObject(cur).get("to").getAsString(); count *= SC.getAsJsonObject(cur).get("c").getAsDouble(); }
			if (nxt.isEmpty()) break;
			cur = nxt;
		}
		return new Ench(cur, count);
	}

	private static double upgradeSpeed(String up1, String up2, String fuel, int tier, String minion) {
		double b = 0;
		int exp = (up1.equals("Minion Expander") ? 1 : 0) + (up2.equals("Minion Expander") ? 1 : 0);
		if (exp == 2) b += 0.1025;
		else if (exp == 1) b += 0.05;
		if (!fuel.equals("None")) {                       // Flycatcher only works with fuel burning
			if (up1.equals("Flycatcher")) b += 0.2;
			if (up2.equals("Flycatcher")) b += 0.2;
		}
		if ((up1.equals("Soulflow Engine") || up2.equals("Soulflow Engine")) && minion.equals("Voidling Minion")) b += 0.03 * tier;
		return b;
	}

	static String dropName(String id) {
		return PLABEL.getOrDefault(id, SiteData.prettyName(id));
	}

	private static Config evalConfig(PCtx x, JsonObject m, String name, int t, String fuel, String up1, String up2) {
		double[] f = FUELS.getOrDefault(fuel, new double[] {0, 1});
		boolean diamond = up1.equals("Diamond Spreading") || up2.equals("Diamond Spreading");
		boolean potato = up1.equals("Potato Spreading") || up2.equals("Potato Spreading");
		boolean fullSoul = up1.equals("Soulflow Engine") || up2.equals("Soulflow Engine");
		boolean lesserSoul = up1.equals("Lesser Soulflow Engine") || up2.equals("Lesser Soulflow Engine");
		boolean soul = fullSoul || lesserSoul;
		double baseS = m.getAsJsonObject("tiers").getAsJsonObject(Integer.toString(t)).get("s").getAsDouble();
		double s = baseS / (1 + f[0] + upgradeSpeed(up1, up2, fuel, t, name));
		double mult = f[1];
		if (soul) mult /= 2;                               // a soulflow engine halves normal drops
		double total = 0, baseItems = 0;
		List<PRow> rows = new ArrayList<>();
		for (JsonElement ie : m.getAsJsonArray("items")) {
			JsonObject it = ie.getAsJsonObject();
			double basePerHour = mult * it.get("per").getAsDouble() * 3600 / (s * 2);    // a drop every 2nd action
			baseItems += basePerHour;
			Ench en = enchantItem(it.get("n").getAsString(), up1, up2);
			double fin = basePerHour / en.count();
			Price pr = priceFor(x, en.item(), it.get("npc").getAsDouble() * en.count());
			double sub = fin * pr.price();
			total += sub;
			rows.add(new PRow(dropName(en.item()), en.item(), fin, pr.price(), pr.mk(), sub, ""));
		}
		if (diamond) {
			Ench en = enchantItem("DIAMOND", up1, up2);
			double dph = baseItems / 10 / en.count();
			Price pr = priceFor(x, en.item(), 8 * en.count());
			total += dph * pr.price();
			rows.add(new PRow(dropName(en.item()), en.item(), dph, pr.price(), pr.mk(), dph * pr.price(), "Diamond Spreading"));
		}
		if (potato) {
			Ench en = enchantItem("POTATO_ITEM", up1, up2);
			double pph = baseItems / 10 / en.count();
			Price pr = priceFor(x, en.item(), 1 * en.count());
			total += pph * pr.price();
			rows.add(new PRow(dropName(en.item()), en.item(), pph, pr.price(), pr.mk(), pph * pr.price(), "Potato Spreading"));
		}
		if (soul) {
			double raw = fullSoul ? 40 : 20;
			Ench en = enchantItem("RAW_SOULFLOW", up1, up2);
			double sph = raw / en.count();
			Price pr = priceFor(x, en.item(), 0);
			total += sph * pr.price();
			rows.add(new PRow(dropName(en.item()), en.item(), sph, pr.price(), pr.mk(), sph * pr.price(), fullSoul ? "Soulflow Engine" : "Lesser Soulflow Engine"));
		}
		if ((up1.equals("Corrupt Soil") || up2.equals("Corrupt Soil")) && m.get("type").getAsString().equals("COMBAT")) {
			double cycle = mult * 3600 / (s * 2), sph = cycle * 0.5, fph = cycle * 0.5;
			Price prS = priceFor(x, "SULPHUR", 10), prF = priceFor(x, "CORRUPTED_FRAGMENT", 20);
			total += sph * prS.price() + fph * prF.price();
			rows.add(new PRow("Sulphur", "SULPHUR", sph, prS.price(), prS.mk(), sph * prS.price(), "Corrupt Soil"));
			rows.add(new PRow("Corrupted Fragment", "CORRUPTED_FRAGMENT", fph, prF.price(), prF.mk(), fph * prF.price(), "Corrupt Soil"));
		}
		return new Config(total, rows, s, fuel, up1, up2);
	}

	private static Config bestConfig(PCtx x, JsonObject m, String name, int t) {
		String reqFuel = null;
		for (String r : x.require()) if (AUTO_FUELS.contains(r)) { reqFuel = r; break; }
		List<String> reqUps = new ArrayList<>();
		for (String r : x.require()) if (UPGRADES.contains(r)) reqUps.add(r);
		if (reqUps.size() > 2) reqUps = reqUps.subList(0, 2);       // only two upgrade slots exist
		List<String> fuels = new ArrayList<>();
		if (reqFuel != null) fuels.add(reqFuel);
		else for (String f : AUTO_FUELS) if (f.equals("None") || !x.exclude().contains(f)) fuels.add(f);
		List<String> ups = new ArrayList<>();
		for (String u : UPGRADES) if (u.equals("None") || !x.exclude().contains(u)) ups.add(u);
		for (String r : reqUps) if (!ups.contains(r)) ups.add(r);  // a required upgrade overrides an exclude
		Config best = null;
		for (String f : fuels) {
			for (int i = 0; i < ups.size(); i++) {
				for (int j = i; j < ups.size(); j++) {
					String a = ups.get(i), b = ups.get(j);
					boolean ok = true;
					for (String r : reqUps) if (!r.equals(a) && !r.equals(b)) ok = false;
					if (!ok) continue;
					Config c = evalConfig(x, m, name, t, f, a, b);
					if (best == null || c.total() > best.total()) best = c;
				}
			}
		}
		if (best == null) best = evalConfig(x, m, name, t, fuels.isEmpty() ? "None" : fuels.get(0),
				reqUps.isEmpty() ? "None" : reqUps.get(0), reqUps.size() < 2 ? "None" : reqUps.get(1));
		return best;
	}

	/** computeProfit: coins per hour for every minion at the chosen tier (its top tier if it doesn't go that high). */
	public static List<Profit> profits(PCtx x, List<Minion> minions) {
		Map<String, Minion> byName = new LinkedHashMap<>();
		for (Minion mn : minions) byName.put(mn.name(), mn);
		JsonObject store = SiteData.extra("minStore").getAsJsonObject();
		List<Profit> out = new ArrayList<>();
		for (var e : SiteData.extra("minProfit").getAsJsonObject().entrySet()) {
			String name = e.getKey();
			JsonObject m = e.getValue().getAsJsonObject();
			List<Integer> avail = tierNums(m.getAsJsonObject("tiers"));
			int t = x.tier();
			boolean usedMax = !m.getAsJsonObject("tiers").has(Integer.toString(t));
			if (usedMax) t = avail.get(avail.size() - 1);
			Config cfg = x.auto() ? bestConfig(x, m, name, t) : evalConfig(x, m, name, t, x.fuel(), x.up1(), x.up2());
			List<PRow> rows = new ArrayList<>(cfg.rows());
			rows.sort((a, b) -> Double.compare(b.sub(), a.sub()));
			Minion mn = byName.get(name);
			Tier tr = mn == null ? null : mn.tier(t);
			Double pI = null, pO = null;
			if (tr != null && cfg.total() > 0) {
				pI = tr.cumI() / cfg.total();
				pO = tr.cumO() / cfg.total();
			}
			Map<String, Double> perId = new LinkedHashMap<>();
			for (PRow r : rows) perId.merge(r.id(), r.perHour(), Double::sum);
			double stacks = 0;
			for (var p : perId.entrySet()) stacks += p.getValue() / SiteData.stackOf(p.getKey(), false);
			Integer storeItems = null;
			if (mn != null && store.has(mn.key()) && store.getAsJsonObject(mn.key()).has(Integer.toString(t))) {
				storeItems = store.getAsJsonObject(mn.key()).get(Integer.toString(t)).getAsInt();
			}
			out.add(new Profit(name, m.get("type").getAsString(), t, usedMax, CraftEngine.round1(cfg.s()), storeItems, stacks,
					CraftEngine.round1(cfg.s() * 2), cfg.total(), List.copyOf(rows), cfg.fuel(), cfg.up1(), cfg.up2(), pI, pO));
		}
		return out;
	}

	public static final List<CraftEngine.SortOption> PROFIT_SORTS = List.of(
			new CraftEngine.SortOption("total", "Profit / hr", "Profit / hr", "Most coins per hour first."),
			new CraftEngine.SortOption("payback", "Fastest payback", "Payback", "Earns back its insta-buy build cost soonest."),
			new CraftEngine.SortOption("fill", "Empty least often", "Fills slowest", "Takes longest to fill its storage (with your storage chest)."),
			new CraftEngine.SortOption("name", "Name", "Name", "Alphabetical."));

	public static List<Profit> sortProfits(List<Profit> all, String key, String search, int chestSlots) {
		String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		List<Profit> list = new ArrayList<>();
		for (Profit p : all) if (p.name().toLowerCase(Locale.ROOT).contains(q)) list.add(p);
		Comparator<Profit> c = switch (key) {
			case "payback" -> (a, b) -> {
				Double pa = a.paybackInsta(), pb = b.paybackInsta();
				if (pa == null && pb == null) return Double.compare(b.total(), a.total());
				if (pa == null) return 1;
				if (pb == null) return -1;
				return Double.compare(pa, pb);
			};
			case "name" -> Comparator.comparing(Profit::name);
			case "fill" -> (a, b) -> Double.compare(orNeg(b.fillHours(chestSlots)), orNeg(a.fillHours(chestSlots)));
			default -> (a, b) -> Double.compare(b.total(), a.total());
		};
		list.sort(c);
		return list;
	}

	private static double orNeg(Double d) {
		return d == null ? -1 : d;
	}
}
