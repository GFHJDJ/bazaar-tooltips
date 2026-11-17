package com.unc.bazaartips;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Shard fusions: fuse two shards (a few of each) into another, then sell it on the Bazaar. For every shard you can fuse,
 * the cheapest pair to make it right now and the profit. Recipes from SkyShards (Campion, MIT); prices from the Bazaar.
 * No Minecraft code, so it can be tested on its own.
 */
public final class ShardEngine {
	private ShardEngine() {}

	/** One shard: its Bazaar ID, name, rarity, how many one fusion uses, and family. */
	public record Shard(String code, String id, String name, String rarity, int fuse, String family) {}

	/** One way to make a shard: fuse `a` and `b` (each shard's own fuse amount of it) for `out` of the result. */
	record Pair(int a, int b, int out) {}

	/** A pair priced right now: cost of both inputs, and how many it makes. */
	public record Way(Shard a, Shard b, double aEach, double bEach, int out, double cost) {
		public double costEach() {
			return cost / out;
		}
	}

	/** The best fusion for one shard: the cheapest way, the next cheapest ones, and what selling the result pays. */
	public record Fusion(Shard result, Way best, List<Way> others, double sellEach, long weekVol, Risk risk) {
		/** What one fusion's result sells for (after tax, the way Sell says). */
		public double value() {
			return best.out() * sellEach;
		}

		public double profit() {
			return value() - best.cost();
		}

		public double pct() {
			return best.cost() > 0 ? profit() / best.cost() * 100 : 0;
		}
	}

	private static List<Shard> shards = List.of();
	private static Map<String, Integer> byCode = Map.of();
	/** For each result (by index): every pair that makes it. */
	private static Map<Integer, List<Pair>> recipes = Map.of();

	public static void load() {
		try (InputStream in = ShardEngine.class.getResourceAsStream("/bazaartips/shards.json")) {
			if (in == null) throw new IllegalStateException("shards.json missing from the jar");
			load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		} catch (Exception e) {
			System.getLogger("bazaartips").log(System.Logger.Level.WARNING, "Couldn't load shard fusions: " + e);
		}
	}

	static void load(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		List<Shard> list = new ArrayList<>();
		Map<String, Integer> idx = new HashMap<>();
		for (var e : root.getAsJsonObject("shards").entrySet()) {
			var a = e.getValue().getAsJsonArray();
			idx.put(e.getKey(), list.size());
			list.add(new Shard(e.getKey(), a.get(0).getAsString(), a.get(1).getAsString(), a.get(2).getAsString(), a.get(3).getAsInt(),
					a.size() > 4 ? a.get(4).getAsString() : ""));
		}
		Map<Integer, List<Pair>> rec = new LinkedHashMap<>();
		for (var e : root.getAsJsonObject("recipes").entrySet()) {
			Integer out = idx.get(e.getKey());
			if (out == null) continue;
			List<Pair> pairs = new ArrayList<>();
			for (var q : e.getValue().getAsJsonObject().entrySet()) {
				int n = Integer.parseInt(q.getKey());
				for (String p : q.getValue().getAsString().split(",")) {
					int sp = p.indexOf(' ');
					if (sp < 0) continue;
					Integer a = idx.get(p.substring(0, sp)), b = idx.get(p.substring(sp + 1));
					if (a != null && b != null) pairs.add(new Pair(a, b, n));
				}
			}
			rec.put(out, List.copyOf(pairs));
		}
		shards = List.copyOf(list);
		byCode = Map.copyOf(idx);
		recipes = rec;
	}

	public static int shardCount() {
		return shards.size();
	}

	/**
	 * Every shard you can fuse that's on the Bazaar, with its cheapest fusion right now. byOrder = buy the inputs with buy
	 * orders (0.1 above the top one) instead of insta-buying; sellOffer = sell the result with a sell offer (0.1 under the
	 * lowest) instead of insta-selling. Fusions with an input that has no price are skipped.
	 */
	public static List<Fusion> fusions(Function<String, BazaarPrices.Price> prices, double tax, boolean byOrder, boolean sellOffer) {
		// each shard's buy price once, not 131k times
		double[] buy = new double[shards.size()];
		for (int i = 0; i < buy.length; i++) {
			BazaarPrices.Price p = prices.apply(shards.get(i).id());
			buy[i] = p == null || p.sellOffers().isEmpty() && !byOrder || p.buyOrders().isEmpty() && byOrder ? Double.NaN
					: byOrder ? CraftEngine.orderBuyAt(p) : CraftEngine.instaBuyAt(p);
			if (!(buy[i] > 0)) buy[i] = Double.NaN;
		}
		List<Fusion> out = new ArrayList<>();
		for (var e : recipes.entrySet()) {
			Shard res = shards.get(e.getKey());
			BazaarPrices.Price rp = prices.apply(res.id());
			if (rp == null) continue;
			double sellEach = sellOffer ? (rp.sellOffers().isEmpty() ? Double.NaN : CraftEngine.offerSellAt(rp))
					: (rp.buyOrders().isEmpty() ? Double.NaN : CraftEngine.instaSellAt(rp));
			if (!(sellEach > 0)) continue;
			sellEach *= 1 - tax;
			// keep the 4 cheapest ways (per shard made)
			List<Way> best = new ArrayList<>();
			for (Pair p : e.getValue()) {
				double a = buy[p.a()], b = buy[p.b()];
				if (Double.isNaN(a) || Double.isNaN(b)) continue;
				Shard sa = shards.get(p.a()), sb = shards.get(p.b());
				double cost = sa.fuse() * a + sb.fuse() * b, each = cost / p.out();
				if (best.size() == 4 && each >= best.get(3).costEach()) continue;
				Way w = new Way(sa, sb, a, b, p.out(), cost);
				int at = 0;
				while (at < best.size() && best.get(at).costEach() <= each) at++;
				best.add(at, w);
				if (best.size() > 4) best.remove(4);
			}
			if (best.isEmpty()) continue;
			out.add(new Fusion(res, best.get(0), List.copyOf(best.subList(1, best.size())), sellEach,
					Math.min(rp.buyMovingWeek(), rp.sellMovingWeek()), Risk.of(rp)));
		}
		return out;
	}

	/** Sorts: "profit" (per fusion), "pct", "cost" (cheapest first), "vol", "name". */
	public static List<Fusion> sorted(List<Fusion> list, String sort, String search) {
		String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		List<Fusion> out = new ArrayList<>();
		for (Fusion f : list) {
			if (q.isEmpty() || f.result().name().toLowerCase(Locale.ROOT).contains(q) || f.result().family().toLowerCase(Locale.ROOT).contains(q)
					|| f.result().rarity().contains(q)) out.add(f);
		}
		java.util.Comparator<Fusion> c = switch (sort) {
			case "pct" -> (a, b) -> Double.compare(b.pct(), a.pct());
			case "cost" -> java.util.Comparator.comparingDouble(f -> f.best().cost());
			case "vol" -> (a, b) -> Long.compare(b.weekVol(), a.weekVol());
			case "name" -> java.util.Comparator.comparing(f -> f.result().name());
			default -> (a, b) -> Double.compare(b.profit(), a.profit());
		};
		out.sort(c);
		return out;
	}

	/** "5x Grove + 5x Mist". */
	public static String recipeText(Way w) {
		return w.a().fuse() + "x " + w.a().name() + " + " + w.b().fuse() + "x " + w.b().name();
	}
}
