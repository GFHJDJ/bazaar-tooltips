package com.unc.bazaartips;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Keeps the mod up to date from the GitHub releases: checks a little after the game starts and every 6 hours, downloads a
 * newer jar into a folder Fabric doesn't load from (mods/.bazaartips-update), checks it really is this mod at that version,
 * and swaps it in when the game closes (the running jar can't be replaced while it's in use). Turn it off in the gear.
 * No Minecraft code, so it can be tested on its own.
 */
public final class Updater {
	private Updater() {}

	static final String REPO = "GFHJDJ/bazaar-tooltips";
	static final String LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";
	static final String MOD_ID = "bazaartips";
	private static final System.Logger LOG = System.getLogger("bazaartips");

	/** The newest release: its version ("0.18.0"), page, and the jar to download. */
	public record Release(String version, String page, String jarName, String jarUrl, long size) {}

	// ---------------------------------------------------------------- pure helpers (tested)

	/** -1, 0 or 1, comparing "0.17.2" style versions number by number (a leading "v" and anything after "-" or "+" ignored). */
	static int compare(String a, String b) {
		int[] x = parts(a), y = parts(b);
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
			if (p != q) return p < q ? -1 : 1;
		}
		return 0;
	}

	private static int[] parts(String v) {
		String s = v.trim();
		if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
		int cut = s.length();
		for (char c : new char[] {'-', '+', ' '}) if (s.indexOf(c) >= 0) cut = Math.min(cut, s.indexOf(c));
		String[] p = s.substring(0, cut).split("\\.");
		int[] out = new int[p.length];
		for (int i = 0; i < p.length; i++) {
			try {
				out[i] = Integer.parseInt(p[i]);
			} catch (NumberFormatException e) {
				out[i] = 0;
			}
		}
		return out;
	}

	/** The release from GitHub's "latest release" answer, or null if it has no mod jar. */
	static Release parseRelease(String json) {
		JsonObject o = JsonParser.parseString(json).getAsJsonObject();
		if (!o.has("tag_name") || !o.has("assets")) return null;
		String version = o.get("tag_name").getAsString().replaceFirst("^[vV]", "");
		String page = o.has("html_url") ? o.get("html_url").getAsString() : "https://github.com/" + REPO + "/releases";
		for (JsonElement e : o.getAsJsonArray("assets")) {
			JsonObject a = e.getAsJsonObject();
			String name = a.get("name").getAsString();
			if (!name.endsWith(".jar") || name.contains("-sources") || name.contains("-dev")) continue;
			if (!name.matches("[A-Za-z0-9._+-]+")) continue;             // a plain file name, nothing that could escape the folder
			return new Release(version, page, name, a.get("browser_download_url").getAsString(), a.has("size") ? a.get("size").getAsLong() : -1);
		}
		return null;
	}

	/** The mod ID and version inside a jar's fabric.mod.json, or null if it doesn't have one. */
	static String[] modInfo(InputStream jar) throws java.io.IOException {
		try (ZipInputStream z = new ZipInputStream(jar)) {
			ZipEntry e;
			while ((e = z.getNextEntry()) != null) {
				if (!e.getName().equals("fabric.mod.json")) continue;
				JsonObject o = JsonParser.parseString(new String(z.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
				return new String[] {o.get("id").getAsString(), o.get("version").getAsString()};
			}
		}
		return null;
	}

	/** True when the file is this mod at exactly that version. */
	static boolean isMod(Path jar, String version) {
		try (InputStream in = Files.newInputStream(jar)) {
			String[] info = modInfo(in);
			return info != null && info[0].equals(MOD_ID) && compare(info[1], version) == 0;
		} catch (Exception e) {
			return false;
		}
	}

	/** PowerShell's single-quoted string: ' doubled. */
	static String psQuote(String s) {
		return "'" + s.replace("'", "''") + "'";
	}

	/**
	 * The Windows swap, run after the game closes: keep trying to delete the old jar (it's locked until Java exits), then
	 * move the new one in. Never leaves both in the mods folder, so Fabric can't find the mod twice.
	 */
	static String windowsScript(Path oldJar, Path staged, Path dest, Path folder) {
		return "$old=" + psQuote(oldJar.toString()) + "; $new=" + psQuote(staged.toString()) + "; $dest=" + psQuote(dest.toString())
				+ "; $dir=" + psQuote(folder.toString()) + "; for ($i = 0; $i -lt 60; $i++) { Remove-Item -LiteralPath $old -Force "
				+ "-ErrorAction SilentlyContinue; if (-not (Test-Path -LiteralPath $old)) { Move-Item -LiteralPath $new -Destination $dest -Force; "
				+ "Remove-Item -LiteralPath $dir -Recurse -Force -ErrorAction SilentlyContinue; break }; Start-Sleep -Seconds 1 }";
	}

	// ---------------------------------------------------------------- the running updater

	private static volatile String current = "";
	private static volatile Path ownJar;
	private static volatile Release staged;
	private static volatile Path stagedFile;
	private static volatile String status = "Not checked yet";
	private static volatile String pendingMessage;
	private static volatile boolean hookAdded = false;
	private static ScheduledExecutorService timer;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL).build();

	/** What the gear shows: "Up to date (0.17.2)", "0.18.0 installs when you close the game", ... */
	public static String status() {
		return status;
	}

	/** A chat line to show once you're in a world (null = none). Taken once. */
	public static Release takeMessage() {
		String m = pendingMessage;
		pendingMessage = null;
		return m == null ? null : staged;
	}

	/** jar = the mod's own jar (null when it isn't one, e.g. running from an IDE: then it never updates itself). */
	public static synchronized void start(String version, Path jar) {
		current = version;
		ownJar = jar != null && Files.isRegularFile(jar) && jar.toString().endsWith(".jar") ? jar : null;
		if (ownJar == null) {
			status = "Off: not running from a jar";
			return;
		}
		cleanUp();
		if (timer != null) return;
		timer = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "BazaarTooltips-update");
			t.setDaemon(true);
			return t;
		});
		timer.scheduleAtFixedRate(Updater::check, 20, 6 * 3600, TimeUnit.SECONDS);
	}

	/** Checks right now (the gear's button). */
	public static void checkNow() {
		if (timer != null) timer.execute(Updater::check);
	}

	private static Path folder() {
		return ownJar.getParent().resolve(".bazaartips-update");
	}

	/** Leftovers from an update that has been installed (or an older one): deleted. */
	private static void cleanUp() {
		Path dir = folder();
		if (!Files.isDirectory(dir)) return;
		try (var files = Files.list(dir)) {
			for (Path p : files.toList()) {
				boolean keep = false;
				try (InputStream in = Files.newInputStream(p)) {
					String[] info = modInfo(in);
					keep = info != null && info[0].equals(MOD_ID) && compare(info[1], current) > 0;
				} catch (Exception ignored) {
					// a broken download: delete it
				}
				if (!keep) Files.deleteIfExists(p);
			}
		} catch (Exception e) {
			LOG.log(System.Logger.Level.WARNING, "Couldn't tidy the update folder: " + e);
		}
	}

	static void check() {
		try {
			if (!Settings.autoUpdate || ownJar == null) {
				if (!Settings.autoUpdate) status = "Off";
				return;
			}
			if (staged != null) return;                                  // already waiting to install
			status = "Checking...";
			HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(LATEST)).timeout(Duration.ofSeconds(20))
					.header("Accept", "application/vnd.github+json").header("User-Agent", "BazaarTooltips/" + current).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			if (r.statusCode() != 200) {
				status = "Couldn't check (GitHub said " + r.statusCode() + ")";
				return;
			}
			Release rel = parseRelease(r.body());
			if (rel == null || compare(rel.version(), current) <= 0) {
				status = "Up to date (" + current + ")";
				return;
			}
			Path dir = folder();
			Files.createDirectories(dir);
			Path dest = dir.resolve(rel.jarName());
			if (!isMod(dest, rel.version())) {
				status = "Downloading " + rel.version() + "...";
				Path part = dir.resolve(rel.jarName() + ".part");
				HttpResponse<Path> d = HTTP.send(HttpRequest.newBuilder(URI.create(rel.jarUrl())).timeout(Duration.ofMinutes(2))
						.header("User-Agent", "BazaarTooltips/" + current).GET().build(), HttpResponse.BodyHandlers.ofFile(part));
				if (d.statusCode() != 200 || (rel.size() > 0 && Files.size(part) != rel.size()) || !isMod(part, rel.version())) {
					Files.deleteIfExists(part);
					status = "Download of " + rel.version() + " failed; will try again later";
					return;
				}
				Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING);
			}
			staged = rel;
			stagedFile = dest;
			addHook();
			status = rel.version() + " installs when you close the game";
			pendingMessage = rel.version();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			status = "Couldn't check for updates";
			LOG.log(System.Logger.Level.WARNING, "Update check failed: " + e);
		}
	}

	private static synchronized void addHook() {
		if (hookAdded) return;
		hookAdded = true;
		Runtime.getRuntime().addShutdownHook(new Thread(Updater::install, "BazaarTooltips-install-update"));
	}

	/** At exit: swap the new jar in (now on Mac/Linux; on Windows a hidden PowerShell does it once Java has let go of the old jar). */
	static void install() {
		Release rel = staged;
		Path file = stagedFile, old = ownJar;
		if (rel == null || file == null || old == null || !Settings.autoUpdate || !Files.isRegularFile(file)) return;
		Path dest = old.getParent().resolve(rel.jarName());
		try {
			if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
				List<String> cmd = new ArrayList<>(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command",
						windowsScript(old, file, dest, folder())));
				new ProcessBuilder(cmd).start();
			} else {
				Files.delete(old);                                           // fine on Mac/Linux even while the jar is open
				Files.move(file, dest, StandardCopyOption.REPLACE_EXISTING);
				Files.deleteIfExists(folder());
			}
		} catch (Exception e) {
			LOG.log(System.Logger.Level.WARNING, "Couldn't install the update: " + e);
		}
	}
}
