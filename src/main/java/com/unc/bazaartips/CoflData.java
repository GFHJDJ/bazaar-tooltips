package com.unc.bazaartips;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Price history and 24h price moves from SkyCofl (sky.coflnet.com), for the flip score, fetched the same
 * polite way as the website: one request at a time, about one a second, only for items on screen and the one
 * you open; history is kept 6 hours (and saved, like the site's browser cache).
 * Data provided by SkyCofl.
 */
public final class CoflData {
	static final String API = "https://sky.coflnet.com/api";
	private static final long HIST_TTL = 6 * 3600 * 1000L, ERR_TTL = 10 * 60 * 1000L;

	/** Summary of an item's price history: all-time high/low, since when, and the last 30 days' median/high/low. */
	public record Hist(long at, String status, double allHi, double allLo, long since, double med30, double hi30, double lo30) {}

	/** % change in the last 24h: b = insta-buy price, s = buy-order price (NaN when unknown). */
	public record Move(double b, double s) {}

	private static final Map<String, Hist> hist = new ConcurrentHashMap<>();
	private static volatile Map<String, Move> move24 = null;
	private static volatile long move24At = 0;
	private static volatile boolean moveBusy = false;
	private static final Deque<String> queue = new ArrayDeque<>();
	private static volatile String inflight = null;
	private static Thread worker;
	private static Path file;
	/** Changes whenever new data arrives, so the screen knows to re-score. */
	private static volatile long version = 0;

	private CoflData() {}

	public static long version() {
		return version;
	}

	public static Move move(String id) {
		Map<String, Move> m = move24;
		return m == null ? null : m.get(id);
	}

	public static Hist hist(String id) {
		return hist.get(id);
	}

	public static boolean queued(String id) {
		synchronized (queue) {
			return queue.contains("h:" + id) || ("h:" + id).equals(inflight);
		}
	}

	// ---- live lowest-BIN checks (the Essence tab): kept 2 minutes (1 after an error), not saved ----

	private static final Map<String, EssenceEngine.BinCheck> bins = new ConcurrentHashMap<>();

	public static EssenceEngine.BinCheck bin(String id) {
		return bins.get(id);
	}

	private static boolean binFresh(String id) {
		EssenceEngine.BinCheck c = bins.get(id);
		return c != null && System.currentTimeMillis() - c.at() < (c.status().equals("err") ? 60_000 : 120_000);
	}

	/** Queues live listing checks for these AH items (front = the one you opened). */
	public static void queueBin(List<String> ids, boolean front) {
		synchronized (queue) {
			for (String id : ids) {
				String k = "b:" + id;
				if (binFresh(id) || k.equals(inflight)) continue;
				if (front) {
					queue.remove(k);
					queue.addFirst(k);
				} else if (!queue.contains(k)) {
					queue.addLast(k);
				}
			}
		}
		startWorker();
	}

	/** For tests. */
	static void setBinForTests(String id, EssenceEngine.BinCheck c) {
		bins.put(id, c);
	}

	/** Loads saved history (call once at startup). */
	public static void load(Path saveFile) {
		file = saveFile;
		try {
			if (!Files.exists(file)) return;
			JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			for (var e : o.entrySet()) {
				JsonObject h = e.getValue().getAsJsonObject();
				hist.put(e.getKey(), new Hist(h.get("at").getAsLong(), h.get("status").getAsString(), d(h, "allHi"), d(h, "allLo"),
						h.has("since") ? h.get("since").getAsLong() : 0, d(h, "med30"), d(h, "hi30"), d(h, "lo30")));
			}
		} catch (Exception e) {
			System.getLogger("bazaartips").log(System.Logger.Level.WARNING, "Couldn't read saved price history: " + e);
		}
	}

	private static double d(JsonObject o, String k) {
		return o.has(k) ? o.get(k).getAsDouble() : Double.NaN;
	}

	private static void save() {
		if (file == null) return;
		try {
			JsonObject o = new JsonObject();
			for (var e : hist.entrySet()) {
				Hist h = e.getValue();
				JsonObject j = new JsonObject();
				j.addProperty("at", h.at());
				j.addProperty("status", h.status());
				if (h.status().equals("ok")) {
					j.addProperty("allHi", h.allHi());
					j.addProperty("allLo", h.allLo());
					j.addProperty("since", h.since());
					j.addProperty("med30", h.med30());
					j.addProperty("hi30", h.hi30());
					j.addProperty("lo30", h.lo30());
				}
				o.add(e.getKey(), j);
			}
			Files.createDirectories(file.getParent());
			Files.writeString(file, o.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			System.getLogger("bazaartips").log(System.Logger.Level.WARNING, "Couldn't save price history: " + e);
		}
	}

	static boolean fresh(String id) {
		Hist h = hist.get(id);
		return h != null && System.currentTimeMillis() - h.at() < (h.status().equals("err") ? ERR_TTL : HIST_TTL);
	}

	/** Queues history downloads; front = the item you opened jumps the queue. */
	public static void queueHistory(List<String> ids, boolean front) {
		synchronized (queue) {
			for (String id : ids) {
				String k = "h:" + id;
				if (fresh(id) || k.equals(inflight)) continue;
				if (front) {
					queue.remove(k);
					queue.addFirst(k);
				} else if (!queue.contains(k)) {
					queue.addLast(k);
				}
			}
		}
		startWorker();
	}

	private static synchronized void startWorker() {
		if (worker != null && worker.isAlive()) return;
		worker = new Thread(CoflData::pump, "BazaarTooltips-skycofl");
		worker.setDaemon(true);
		worker.start();
	}

	private static void pump() {
		long next = 0;
		while (true) {
			String job;
			synchronized (queue) {
				job = queue.pollFirst();
				inflight = job;
			}
			if (job == null) return;
			String id = job.substring(2);
			try {
				long wait = next - System.currentTimeMillis();
				if (wait > 0) Thread.sleep(wait);
				next = System.currentTimeMillis() + 1100;              // about one request a second, like the site
				if (job.startsWith("b:")) {
					checkBin(id);
					version++;
					synchronized (queue) {
						inflight = null;
					}
					continue;
				}
				String end = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00:00.000'Z'").withZone(ZoneOffset.UTC)
						.format(Instant.now().truncatedTo(ChronoUnit.HOURS)); // whole hour, so SkyCofl's cache is shared
				String body = BazaarPrices.download(URI.create(API + "/bazaar/" + URLEncoder.encode(id, StandardCharsets.UTF_8)
						+ "/history?start=2020-03-10T00:00:00Z&end=" + URLEncoder.encode(end, StandardCharsets.UTF_8)), "SkyCofl history");
				Hist sum = body == null ? null : summarize(JsonParser.parseString(body), System.currentTimeMillis());
				hist.put(id, sum != null ? sum : new Hist(System.currentTimeMillis(), body != null ? "none" : "err", 0, 0, 0, 0, 0, 0));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (Exception e) {
				hist.put(id, new Hist(System.currentTimeMillis(), "err", 0, 0, 0, 0, 0, 0));
			}
			save();
			version++;
			synchronized (queue) {
				inflight = null;
			}
		}
	}

	/** pumpBin: SkyCofl's live lowest BIN (and the next lowest, and the auction's id for /viewauction). */
	private static void checkBin(String id) {
		try {
			String body = BazaarPrices.download(URI.create(API + "/item/price/" + URLEncoder.encode(id, StandardCharsets.UTF_8) + "/bin"), "SkyCofl BIN");
			JsonElement d = body == null ? null : JsonParser.parseString(body);
			long now = System.currentTimeMillis();
			if (d != null && d.isJsonObject() && d.getAsJsonObject().has("lowest") && d.getAsJsonObject().get("lowest").isJsonPrimitive()) {
				JsonObject o = d.getAsJsonObject();
				double low = o.get("lowest").getAsDouble();
				bins.put(id, low > 0 ? new EssenceEngine.BinCheck(now, "ok", low, num(o, "secondLowest", "SecondLowest") > 0 ? num(o, "secondLowest", "SecondLowest") : 0,
						o.has("uuid") && !o.get("uuid").isJsonNull() ? o.get("uuid").getAsString() : "") : new EssenceEngine.BinCheck(now, "none", 0, 0, ""));
			} else {
				bins.put(id, new EssenceEngine.BinCheck(now, "err", 0, 0, ""));
			}
		} catch (Exception e) {
			bins.put(id, new EssenceEngine.BinCheck(System.currentTimeMillis(), "err", 0, 0, ""));
		}
	}

	/** summarizeHistory: needs 5+ points with both prices; the 30-day numbers fall back to the last 30 points. */
	static Hist summarize(JsonElement data, long now) {
		if (data == null || !data.isJsonArray()) return null;
		record Pt(long t, double mid) {}
		List<Pt> pts = new ArrayList<>();
		for (JsonElement e : data.getAsJsonArray()) {
			JsonObject p = e.getAsJsonObject();
			String ts = p.has("timestamp") ? p.get("timestamp").getAsString() : p.has("Timestamp") ? p.get("Timestamp").getAsString() : null;
			double b = num(p, "buy", "Buy"), s = num(p, "sell", "Sell");
			long t = parseTime(ts);
			if (t != 0 && b > 0 && s > 0) pts.add(new Pt(t, (b + s) / 2));
		}
		pts.sort((a, b) -> Long.compare(a.t(), b.t()));
		if (pts.size() < 5) return null;
		List<Double> mids = new ArrayList<>();
		for (Pt p : pts) mids.add(p.mid());
		long cut = now - 30L * 86_400_000L;
		List<Double> recent = new ArrayList<>();
		for (Pt p : pts) if (p.t() >= cut) recent.add(p.mid());
		List<Double> r = recent.size() >= 3 ? recent : mids.subList(Math.max(0, mids.size() - 30), mids.size());
		return new Hist(now, "ok", Collections.max(mids), Collections.min(mids), pts.get(0).t(), median(r), Collections.max(r), Collections.min(r));
	}

	private static double num(JsonObject p, String a, String b) {
		JsonElement e = p.has(a) && !p.get(a).isJsonNull() ? p.get(a) : p.has(b) && !p.get(b).isJsonNull() ? p.get(b) : null;
		try {
			return e == null ? Double.NaN : e.getAsDouble();
		} catch (Exception ex) {
			return Double.NaN;
		}
	}

	static long parseTime(String ts) {
		if (ts == null) return 0;
		try {
			return Instant.parse(ts.endsWith("Z") || ts.matches(".*[+-]\\d\\d:\\d\\d$") ? ts : ts + "Z").toEpochMilli();
		} catch (Exception e) {
			return 0;
		}
	}

	static double median(List<Double> a) {
		List<Double> s = new ArrayList<>(a);
		Collections.sort(s);
		int m = s.size() >> 1;
		return s.size() % 2 == 1 ? s.get(m) : (s.get(m - 1) + s.get(m)) / 2;
	}

	/** ensureMovement: the 24h price moves, refreshed at most every 5 minutes. */
	public static void ensureMovement() {
		if (moveBusy || (move24 != null && System.currentTimeMillis() - move24At < 5 * 60 * 1000)) return;
		moveBusy = true;
		Thread t = new Thread(() -> {
			try {
				String a = BazaarPrices.download(URI.create(API + "/bazaar/movement?hours=24"), "SkyCofl movement");
				String b = BazaarPrices.download(URI.create(API + "/bazaar/movement?hours=24&usebuyOrders=true"), "SkyCofl movement");
				Map<String, Move> m = movement(a == null ? null : JsonParser.parseString(a), b == null ? null : JsonParser.parseString(b));
				if (m != null) {
					move24 = m;
					move24At = System.currentTimeMillis();
					version++;
				}
			} catch (Exception e) {
				// try again next time
			} finally {
				moveBusy = false;
			}
		}, "BazaarTooltips-skycofl-move");
		t.setDaemon(true);
		t.start();
	}

	static Map<String, Move> movement(JsonElement a, JsonElement b) {
		boolean okA = a != null && a.isJsonArray(), okB = b != null && b.isJsonArray();
		if (!okA && !okB) return null;
		Map<String, double[]> m = new HashMap<>();
		if (okA) add(a.getAsJsonArray(), 0, m);
		if (okB) add(b.getAsJsonArray(), 1, m);
		Map<String, Move> out = new HashMap<>();
		m.forEach((k, v) -> out.put(k, new Move(v[0], v[1])));
		return out;
	}

	private static void add(JsonArray arr, int k, Map<String, double[]> m) {
		for (JsonElement e : arr) {
			JsonObject x = e.getAsJsonObject();
			String id = x.has("itemId") ? x.get("itemId").getAsString() : x.has("ItemId") ? x.get("ItemId").getAsString() : null;
			double pv = num(x, "previousPrice", "PreviousPrice"), cv = num(x, "currentPrice", "CurrentPrice");
			if (id != null && pv > 0 && cv > 0) m.computeIfAbsent(id, z -> new double[] {Double.NaN, Double.NaN})[k] = (cv - pv) / pv * 100;
		}
	}

	/** For tests. */
	static void setForTests(Map<String, Move> m, Map<String, Hist> h) {
		move24 = m;
		hist.clear();
		hist.putAll(h);
		version++;
	}
}
