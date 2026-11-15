package com.unc.bazaartips;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * A "Worth" button above the top-left corner of your inventory (E). It starts closed; click it to show a panel beside the
 * inventory with what everything you're carrying is worth and where to sell each item, click again to hide it.
 */
final class InventoryPanel {
	private InventoryPanel() {}

	static final int BUTTON_W = 46, BUTTON_H = 18, MAX_ITEMS = 14;

	static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof InventoryScreen inv)) return;
			Button button = Button.builder(label(), b -> {
				Settings.invPanel = !Settings.invPanel;
				Settings.save();
				b.setMessage(label());
			}).bounds(0, 0, BUTTON_W, BUTTON_H).tooltip(Tooltip.create(Component.literal(
					"What everything you're carrying is worth, and whether to sell each item on the Bazaar or to an NPC. Click to show or hide."))).build();
			place(inv, button);
			Screens.getWidgets(screen).add(button);
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> {
				place(inv, button);                           // the recipe book moves the inventory, so follow it
				if (Settings.invPanel) draw(inv, g);
			});
		});
	}

	private static Component label() {
		return Component.literal(Settings.invPanel ? "Hide" : "Worth");
	}

	/** Just above the inventory's top-left corner. */
	private static void place(InventoryScreen inv, Button b) {
		int[] r = bounds(inv);
		b.setX(r[0]);
		b.setY(Math.max(0, r[1] - BUTTON_H - 2));
	}

	// ---- where the inventory is drawn ----

	private static Field leftPos, topPos;
	private static boolean looked;

	/** {left, top, width, height} of the inventory panel. Falls back to the usual centred spot if the fields can't be read. */
	static int[] bounds(AbstractContainerScreen<?> c) {
		int w = 176, h = 166;
		if (!looked) {
			looked = true;
			try {
				leftPos = AbstractContainerScreen.class.getDeclaredField("leftPos");
				topPos = AbstractContainerScreen.class.getDeclaredField("topPos");
				leftPos.setAccessible(true);
				topPos.setAccessible(true);
			} catch (Exception e) {
				leftPos = null;
				topPos = null;
			}
		}
		if (leftPos != null) {
			try {
				return new int[] {leftPos.getInt(c), topPos.getInt(c), w, h};
			} catch (Exception ignored) {
				// fall through
			}
		}
		return new int[] {(c.width - w) / 2, (c.height - h) / 2, w, h};
	}

	// ---- reading and pricing the inventory (shared with the cache below) ----

	/** The main inventory and hotbar (36 slots; armor and the off-hand aren't for sale). */
	static List<ItemStack> carried() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return List.of();
		return new ArrayList<>(mc.player.getInventory().getNonEquipmentItems());
	}

	/** Changes whenever a stack is added, removed or changes size. */
	static long signature() {
		long h = 1;
		for (ItemStack s : carried()) h = h * 31 + (s.isEmpty() ? 0 : s.getCount() * 7919L + System.identityHashCode(s.get(DataComponents.CUSTOM_DATA)));
		return h;
	}

	static List<InventoryValue.Stack> read() {
		List<InventoryValue.Stack> out = new ArrayList<>();
		for (ItemStack s : carried()) {
			if (s.isEmpty()) continue;
			CompoundTag tag = BazaarTooltipsClient.skyblockData(s);
			String id = tag == null ? "" : tag.getString("id").orElse("");
			if (id.isEmpty() || id.equals("SKYBLOCK_MENU")) continue;
			out.add(new InventoryValue.Stack(id, BazaarTooltipsClient.bazaarId(id, tag), OrderTracker.strip(s.getHoverName().getString()), s.getCount()));
		}
		return out;
	}

	private static String stamp = "";
	private static List<ProductSummary.Line> lines = List.of();

	/** The panel's lines, rebuilt only when the inventory, prices or settings change. */
	static List<ProductSummary.Line> lines() {
		String st = signature() + "|" + BazaarPrices.lastFetchMillis() + "|" + NpcPrices.version() + "|" + AhPrices.version() + "|" + Settings.tax()
				+ "|" + Format.rounded() + "|" + SackStore.version() + "|" + System.currentTimeMillis() / 60_000;
		if (st.equals(stamp)) return lines;
		AhPrices.ensure();
		List<InventoryValue.Row> rows = InventoryValue.rows(read(), BazaarPrices::get, NpcPrices::get, AhPrices::get, Settings.tax());
		InventoryValue.Totals t = InventoryValue.totals(rows);
		// your sacks, as last read off their screens
		List<InventoryValue.Stack> sackStacks = new ArrayList<>();
		for (SackStore.Entry e : SackStore.all()) {
			String id = OrderTracker.productFor(e.name());
			sackStacks.add(new InventoryValue.Stack(id != null ? id : e.name(), id, e.name(), (int) Math.min(Integer.MAX_VALUE, e.count())));
		}
		List<InventoryValue.Row> sackRows = InventoryValue.rows(sackStacks, BazaarPrices::get, NpcPrices::get, AhPrices::get, Settings.tax());
		InventoryValue.Totals st2 = InventoryValue.totals(sackRows);
		List<ProductSummary.Line> out = new ArrayList<>();
		if (SackStore.sackCount() > 0) {
			out.add(new ProductSummary.Line("Worth now (with sacks)", Colors.GOLD, Format.shortCoins(t.now() + st2.now()), Colors.YELLOW, false));
			out.add(ProductSummary.Line.kv("Inventory", Format.shortCoins(t.now()), Colors.YELLOW));
			out.add(ProductSummary.Line.kv("Sacks (" + SackStore.sackCount() + " read " + Format.age(System.currentTimeMillis() - SackStore.updatedAt()) + " ago)",
					Format.shortCoins(st2.now()), Colors.YELLOW));
		} else {
			out.add(new ProductSummary.Line("Worth now", Colors.GOLD, Format.shortCoins(t.now()), Colors.YELLOW, false));
			out.add(ProductSummary.Line.note("Open your sacks once to add them", Colors.DARK_GRAY));
		}
		if (t.withOffers() - t.now() >= 1000) {                // not worth a line for a few hundred coins
			out.add(ProductSummary.Line.kv("With sell offers", Format.signedShortCoins(t.withOffers() - t.now()) + " more", Colors.AQUA));
		}
		if (t.npcCount() > 0) {
			out.add(ProductSummary.Line.kv("Sell " + t.npcCount() + " to an NPC", Format.signedShortCoins(t.npcBeatsBazaar()) + " vs Bazaar", Colors.GREEN));
		}
		int shown = 0;
		for (InventoryValue.Row r : InventoryValue.sorted(rows, "value", "")) {
			if (!r.priced()) continue;
			if (shown == MAX_ITEMS) {
				out.add(ProductSummary.Line.note("+" + (rows.size() - shown - t.unpriced()) + " more", Colors.DARK_GRAY));
				break;
			}
			String where = switch (r.best()) { case "npc" -> "NPC "; case "ah" -> "AH "; default -> ""; };
			out.add(new ProductSummary.Line(Format.count(r.count()) + "x " + r.name(), Colors.WHITE, where + Format.shortCoins(r.bestValue()),
					r.best().equals("npc") ? Colors.GREEN : r.best().equals("ah") ? Colors.ORANGE : Colors.YELLOW, true));
			shown++;
		}
		if (t.unpriced() > 0) out.add(ProductSummary.Line.note(t.unpriced() + " with no price", Colors.DARK_GRAY));
		out.add(new ProductSummary.Line("Green: sell to NPC · AH: lowest BIN", Colors.DARK_GRAY, "", 0, false));
		lines = out;
		stamp = st;
		return lines;
	}

	/** Beside the inventory: on the right if there's room, else on the left. */
	private static void draw(InventoryScreen inv, GuiGraphicsExtractor g) {
		if (Minecraft.getInstance().player == null) return;
		Font font = Minecraft.getInstance().font;
		int[] r = bounds(inv);
		int rightX = r[0] + r[2] + 4, rightRoom = inv.width - rightX - 4, leftRoom = r[0] - 8;
		int room = Math.max(rightRoom, leftRoom);
		if (room < 110) return;                               // no room at this GUI scale
		int w = Math.min(room, 200);
		int x = rightRoom >= leftRoom ? rightX : r[0] - 4 - w;
		Overlays.panel(g, font, x, r[1], w, lines(), inv.height - 4, Colors.PANEL_SOLID, true);
	}
}
