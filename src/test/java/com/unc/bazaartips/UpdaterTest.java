package com.unc.bazaartips;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class UpdaterTest {
	@Test
	void versionsCompareNumberByNumber() {
		assertEquals(1, Updater.compare("0.18.0", "0.17.2"));
		assertEquals(1, Updater.compare("v0.10.0", "0.9.9"));          // 10 > 9, not "1" < "9"
		assertEquals(0, Updater.compare("v0.17.2", "0.17.2"));
		assertEquals(0, Updater.compare("0.17", "0.17.0"));
		assertEquals(-1, Updater.compare("0.17.2", "0.17.10"));
		assertEquals(0, Updater.compare("0.17.2+build.5", "0.17.2"));
	}

	@Test
	void picksTheModJarFromTheRelease() {
		String json = """
				{"tag_name":"v0.18.0","html_url":"https://github.com/GFHJDJ/bazaar-tooltips/releases/tag/v0.18.0","assets":[
				  {"name":"bazaar-tooltips-0.18.0-sources.jar","browser_download_url":"https://x/s.jar","size":5},
				  {"name":"bazaar-tooltips-0.18.0.jar","browser_download_url":"https://x/m.jar","size":650000}]}""";
		Updater.Release r = Updater.parseRelease(json);
		assertEquals("0.18.0", r.version());
		assertEquals("bazaar-tooltips-0.18.0.jar", r.jarName());
		assertEquals("https://x/m.jar", r.jarUrl());
		assertEquals(650000, r.size());
		assertNull(Updater.parseRelease("{\"tag_name\":\"v1\",\"assets\":[]}"));
		assertNull(Updater.parseRelease("""
				{"tag_name":"v9","assets":[{"name":"../../evil.jar","browser_download_url":"https://x/e.jar"}]}"""));   // not a plain file name
	}

	private static byte[] jar(String json) throws Exception {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		try (ZipOutputStream z = new ZipOutputStream(b)) {
			z.putNextEntry(new ZipEntry("com/x/A.class"));
			z.write(new byte[] {1, 2, 3});
			z.putNextEntry(new ZipEntry("fabric.mod.json"));
			z.write(json.getBytes(StandardCharsets.UTF_8));
		}
		return b.toByteArray();
	}

	@Test
	void onlyAcceptsThisModAtThatVersion(@TempDir Path dir) throws Exception {
		Path good = dir.resolve("good.jar"), other = dir.resolve("other.jar"), junk = dir.resolve("junk.jar");
		Files.write(good, jar("{\"id\":\"bazaartips\",\"version\":\"0.18.0\"}"));
		Files.write(other, jar("{\"id\":\"somethingelse\",\"version\":\"0.18.0\"}"));
		Files.writeString(junk, "<html>not found</html>");
		assertTrue(Updater.isMod(good, "0.18.0"));
		assertFalse(Updater.isMod(good, "0.19.0"));
		assertFalse(Updater.isMod(other, "0.18.0"));
		assertFalse(Updater.isMod(junk, "0.18.0"));
		assertFalse(Updater.isMod(dir.resolve("missing.jar"), "0.18.0"));
	}

	@Test
	void theWindowsSwapQuotesPathsSafely() {
		String s = Updater.windowsScript(Path.of("C:/Users/O'Neil/mods/a.jar"), Path.of("C:/u/b.jar"), Path.of("C:/m/b.jar"), Path.of("C:/u"));
		assertTrue(s.contains("'C:/Users/O''Neil/mods/a.jar'") || s.contains("O''Neil"));
		assertTrue(s.indexOf("Remove-Item -LiteralPath $old") < s.indexOf("Move-Item"));   // old jar gone before the new one goes in
		assertEquals("'it''s'", Updater.psQuote("it's"));
	}
}
