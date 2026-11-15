package com.unc.bazaartips;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What's in your sacks, read off each sack's screen whenever you open it ("Stored: 4,521/71,680" under each item; the
 * Gemstone Sack lists "Rough: / Flawed: / Fine:" per gem). Remembered per sack between sessions, for the Worth panel.
 * No Minecraft code, so it can be tested on its own.
 */
public final class SackStore {
	private SackStore() {}

	/** One item in a sack: its name as shown, and how many. */
	public record Entry(String name, long count) {}

	private static final Pattern STORED = Pattern.compile("Stored:\\s*([\\d,]+)");
	private static final Pattern GEM = Pattern.compile("^(Rough|Flawed|Fine|Flawless|Perfect)(?: Gemstones?)?:\\s*([\\d,]+)");

	/** A sack's screen (e.g. "Mining Sack", "Large Enchanted Gemstone Sack"), not the list of sacks. */
	public static boolean isSackTitle(String title) {
		String t = OrderTracker.strip(title);
		return t.contains("Sack") && !t.equals("Sack of Sacks") && !t.equals("Sacks");
	}

	/** The items one slot of a sack screen shows (none for anything else, like the Back arrow). */
	public static List<Entry> parse(String itemName, List<String> lore) {
		String name = OrderTracker.strip(itemName).replaceAll("^[^A-Za-z0-9]+", "").trim();
		List<Entry> out = new ArrayList<>();
		String gem = name.replaceAll("(?i)\\s*gemstones?$", "").trim();
		for (String raw : lore) {
			String l = OrderTracker.strip(raw);
			Matcher m = STORED.matcher(l);
			if (m.find()) {
				long n = OrderTracker.number(m.group(1));
				if (n > 0) out.add(new Entry(name, n));
				return out;                                    // an ordinary sack item: one count
			}
			Matcher g = GEM.matcher(l);
			if (g.find()) {
				long n = OrderTracker.number(g.group(2));
				if (n > 0) out.add(new Entry(g.group(1) + " " + gem + " Gemstone", n));
			}
		}
		return out;
	}

	private static final Map<String, List<Entry>> sacks = new LinkedHashMap<>();
	private static long updatedAt = 0;
	private static volatile long version = 0;
	private static Path file;

	public static long version() {
		return version;
	}

	/** When a sack was last read (0 = never). */
	public static synchronized long updatedAt() {
		return updatedAt;
	}

	public static synchronized int sackCount() {
		return sacks.size();
	}

	/** Replaces one sack's contents with what its screen shows now. Saves only when something changed. */
	public static synchronized void update(String sackTitle, List<Entry> entries, long now) {
		List<Entry> was = sacks.get(sackTitle);
		updatedAt = now;
		if (entries.equals(was)) return;
		sacks.put(sackTitle, List.copyOf(entries));
		version++;
		save();
	}

	/** Everything in every sack you've opened, the same item added up. */
	public static synchronized List<Entry> all() {
		Map<String, Long> sum = new LinkedHashMap<>();
		for (List<Entry> l : sacks.values()) for (Entry e : l) sum.merge(e.name(), e.count(), Long::sum);
		List<Entry> out = new ArrayList<>();
		sum.forEach((k, v) -> out.add(new Entry(k, v)));
		return out;
	}

	public static synchronized void clear() {
		sacks.clear();
		updatedAt = 0;
		version++;
		save();
	}

	// ---- saved between sessions ----

	public static synchronized void load(Path saveFile) {
		file = saveFile;
		try {
			if (!Files.exists(file)) return;
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			updatedAt = root.has("updated") ? root.get("updated").getAsLong() : 0;
			for (var e : root.getAsJsonObject("sacks").entrySet()) {
				List<Entry> l = new ArrayList<>();
				for (JsonElement x : e.getValue().getAsJsonArray()) {
					JsonArray a = x.getAsJsonArray();
					l.add(new Entry(a.get(0).getAsString(), a.get(1).getAsLong()));
				}
				sacks.put(e.getKey(), List.copyOf(l));
			}
			version++;
		} catch (Exception e) {
			System.getLogger("bazaartips").log(System.Logger.Level.WARNING, "Couldn't read saved sacks: " + e);
		}
	}

	private static void save() {
		if (file == null) return;
		try {
			JsonObject root = new JsonObject();
			root.addProperty("updated", updatedAt);
			JsonObject s = new JsonObject();
			sacks.forEach((k, l) -> {
				JsonArray arr = new JsonArray();
				for (Entry e : l) {
					JsonArray a = new JsonArray();
					a.add(e.name());
					a.add(e.count());
					arr.add(a);
				}
				s.add(k, arr);
			});
			root.add("sacks", s);
			Files.createDirectories(file.getParent());
			Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			System.getLogger("bazaartips").log(System.Logger.Level.WARNING, "Couldn't save sacks: " + e);
		}
	}

	/** For tests. */
	static synchronized void resetForTests() {
		sacks.clear();
		updatedAt = 0;
		file = null;
		version++;
	}
}
