package com.unc.bazaartips;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SackStoreTest {
	@BeforeEach
	void reset() {
		SackStore.resetForTests();
	}

	@Test
	void readsAnOrdinarySackItem() {
		assertEquals(List.of(new SackStore.Entry("Enchanted Diamond", 4521)),
				SackStore.parse("§aEnchanted Diamond", List.of("§7Stored: §e4,521§7/71,680", "", "§eClick to pickup!")));
		assertTrue(SackStore.parse("Go Back", List.of("To Sack of Sacks")).isEmpty());
		assertTrue(SackStore.parse("Mithril", List.of("Stored: 0/71,680")).isEmpty());   // empty slot in the sack
	}

	@Test
	void readsTheGemstoneSack() {
		List<SackStore.Entry> e = SackStore.parse("§a☘ Jade Gemstones", List.of("§7Rough: §a12,345", "§7Flawed: §a1,920", "§7Fine: §a0"));
		assertEquals(List.of(new SackStore.Entry("Rough Jade Gemstone", 12345), new SackStore.Entry("Flawed Jade Gemstone", 1920)), e);
	}

	@Test
	void sackTitles() {
		assertTrue(SackStore.isSackTitle("Large Enchanted Mining Sack"));
		assertTrue(SackStore.isSackTitle("Gemstone Sack"));
		assertFalse(SackStore.isSackTitle("Sack of Sacks"));
		assertFalse(SackStore.isSackTitle("Your Bazaar Orders"));
	}

	@Test
	void eachSackIsReplacedAndTheSameItemAddsUp(@TempDir Path dir) {
		Path f = dir.resolve("sacks.json");
		SackStore.load(f);
		SackStore.update("Mining Sack", List.of(new SackStore.Entry("Coal", 100), new SackStore.Entry("Diamond", 5)), 1000);
		SackStore.update("Large Mining Sack", List.of(new SackStore.Entry("Coal", 50)), 2000);
		SackStore.update("Mining Sack", List.of(new SackStore.Entry("Coal", 10)), 3000);           // reopened: replaces what it had
		assertEquals(List.of(new SackStore.Entry("Coal", 60)), SackStore.all());
		assertEquals(2, SackStore.sackCount());
		assertEquals(3000, SackStore.updatedAt());
		SackStore.resetForTests();
		SackStore.load(f);
		assertEquals(List.of(new SackStore.Entry("Coal", 60)), SackStore.all());
	}
}
