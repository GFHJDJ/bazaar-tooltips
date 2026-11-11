package com.unc.bazaartips;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Two things drawn over the game: a panel beside a bazaar item's page (its flip, craft, NPC numbers and your orders on
 * it), and a small box on your screen listing your bazaar orders and whether each is still on top.
 */
final class Overlays {
	private Overlays() {}

	static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof AbstractContainerScreen<?> c) {
				ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> bazaarPanel(c, g));
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR,
				net.minecraft.resources.Identifier.fromNamespaceAndPath("bazaartips", "orders"), (g, delta) -> orderBox(g));
	}

	// ---- the panel beside a bazaar item's page ----

	private static String lastQueued = "";
	private static String panelStamp = "", orderStamp = "";
	private static List<ProductSummary.Line> panelLines = List.of(), orderLinesCache = List.of();

	/** Everything the two boxes depend on. They're rebuilt when this changes (new prices, orders, settings), not every frame. */
	private static String stamp() {
		return BazaarPrices.lastFetchMillis() + "|" + CoflData.version() + "|" + OrderTracker.version() + "|" + Settings.tax() + "|"
				+ Settings.chains + "|" + Format.rounded();
	}

	/** A bazaar product page: its title has "➜" in it (e.g. "Mining ➜ Diamond") and the product is in slot 13. */
	static String productOnScreen(AbstractContainerScreen<?> c) {
		String title = OrderTracker.strip(c.getTitle().getString());
		if (!title.contains("➜") || c.getMenu().slots.size() <= 13) return null;
		boolean productPage = false;                // only product pages have these buttons (category pages don't)
		for (net.minecraft.world.inventory.Slot sl : c.getMenu().slots) {
			String n = OrderTracker.strip(sl.getItem().getHoverName().getString());
			if (n.equals("Buy Instantly") || n.equals("Create Buy Order")) { productPage = true; break; }
		}
		if (!productPage) return null;
		ItemStack stack = c.getMenu().slots.get(13).getItem();
		if (stack.isEmpty()) return null;
		String id = BazaarTooltipsClient.bazaarId(stack);
		return id != null && BazaarPrices.get(id) != null ? id : null;
	}

	private static void bazaarPanel(AbstractContainerScreen<?> c, GuiGraphicsExtractor g) {
		if (!Settings.bazaarOverlay) return;
		String id = productOnScreen(c);
		if (id == null) return;
		if (!id.equals(lastQueued)) {            // fetch its price history for the flip score (SkyCofl, one request)
			lastQueued = id;
			CoflData.ensureMovement();
			CoflData.queueHistory(List.of(id), true);
		}
		Font font = Minecraft.getInstance().font;
		int sw = c.width, sh = c.height, chest = 176;
		int rightX = (sw + chest) / 2 + 6, rightRoom = sw - rightX - 4, leftRoom = (sw - chest) / 2 - 10;
		int room = Math.max(rightRoom, leftRoom);
		if (room < 110) return;                  // no room beside the chest at this window size
		int w = Math.min(room, 200);
		int x = rightRoom >= leftRoom ? rightX : (sw - chest) / 2 - 6 - w;
		int y = Math.max(4, (sh - 222) / 2);
		String st = id + "|" + stamp() + "|" + SettingsScreen.keyName();
		if (!st.equals(panelStamp)) {
			List<ProductSummary.Line> lines = new ArrayList<>(ProductSummary.build(id));
			lines.add(new ProductSummary.Line("Bazaar Tooltips · " + SettingsScreen.keyName() + " for more", Colors.DARK_GRAY, "", 0, false));
			panelLines = lines;
			panelStamp = st;
		}
		panel(g, font, x, y, w, panelLines, sh - 4);
	}

	/**
	 * Draws lines in a dark box; stops before maxY. A line whose label and number don't fit side by side puts the
	 * number on its own row underneath (nothing gets cut short unless a single piece is wider than the box).
	 */
	static int panel(GuiGraphicsExtractor g, Font font, int x, int y, int w, List<ProductSummary.Line> lines, int maxY) {
		int lh = font.lineHeight + 1, pad = 4, inner = w - 2 * pad;
		record Row(String text, int color, int x, boolean right) {}
		List<Row> rows = new ArrayList<>();
		for (ProductSummary.Line l : lines) {
			int indent = l.indent() ? 4 : 0, vw = l.value().isEmpty() ? 0 : font.width(l.value());
			if (vw == 0) {
				rows.add(new Row(cut(font, l.text(), inner - indent), l.color(), indent, false));
			} else if (font.width(l.text()) + 6 + vw + indent <= inner) {
				rows.add(new Row(l.text(), l.color(), indent, false));
				rows.add(new Row(l.value(), l.valueColor(), 0, true));       // same row, right side (marked by right=true)
			} else {
				rows.add(new Row(cut(font, l.text(), inner - indent), l.color(), indent, false));
				rows.add(null);                                              // next row
				rows.add(new Row(cut(font, l.value(), inner - indent), l.valueColor(), 0, true));
			}
			rows.add(null);
		}
		// count rows (a null ends a row)
		int total = 0;
		for (Row r : rows) if (r == null) total++;
		int n = Math.min(total, Math.max(0, (maxY - y - 2 * pad) / lh));
		g.fill(x, y, x + w, y + n * lh + 2 * pad, Colors.PANEL);
		int row = 0;
		for (Row r : rows) {
			if (r == null) {
				row++;
				continue;
			}
			if (row >= n) break;
			int ty = y + pad + row * lh;
			if (r.right()) g.text(font, r.text(), x + w - pad - font.width(r.text()), ty, r.color());
			else g.text(font, r.text(), x + pad + r.x(), ty, r.color());
		}
		return n * lh + 2 * pad;
	}

	private static String cut(Font font, String s, int maxW) {
		if (maxW <= 0) return "";
		if (font.width(s) <= maxW) return s;
		while (!s.isEmpty() && font.width(s + "..") > maxW) s = s.substring(0, s.length() - 1);
		return s + "..";
	}

	// ---- the orders box on your screen ----

	static final String[][] CORNERS = {{"top-left", "Top left"}, {"top-right", "Top right"}, {"middle-left", "Middle left"}};

	/** The orders box's lines, rebuilt only when prices, orders or settings change. */
	static List<ProductSummary.Line> orderLines() {
		String st = stamp();
		if (!st.equals(orderStamp)) {
			orderLinesCache = buildOrderLines();
			orderStamp = st;
		}
		return orderLinesCache;
	}

	private static List<ProductSummary.Line> buildOrderLines() {
		List<ProductSummary.Line> out = new ArrayList<>();
		List<OrderTracker.Check> all = new ArrayList<>(OrderTracker.checkAll(BazaarPrices::get));
		if (all.isEmpty()) return out;
		all.sort((a, b) -> Integer.compare(rank(a.status()), rank(b.status())));
		long outbid = all.stream().filter(c -> c.status() == OrderTracker.Status.OUTBID).count();
		out.add(new ProductSummary.Line("Bazaar orders", Colors.GOLD, outbid > 0 ? outbid + " outbid" : "all good", outbid > 0 ? Colors.RED : Colors.GREEN, false));
		int shown = 0;
		for (OrderTracker.Check c : all) {
			if (shown == 8) {
				out.add(new ProductSummary.Line("+" + (all.size() - 8) + " more (Orders tab)", Colors.DARK_GRAY, "", 0, true));
				break;
			}
			OrderTracker.Order o = c.order();
			out.add(new ProductSummary.Line((o.type() == OrderTracker.Type.BUY ? "Buy " : "Sell ") + Format.count(o.amount()) + "x " + o.name(),
					Colors.WHITE, ProductSummary.statusLabel(c), ProductSummary.statusColor(c), true));
			shown++;
		}
		return out;
	}

	private static int rank(OrderTracker.Status s) {
		return switch (s) { case OUTBID -> 0; case TIED -> 1; case FILLED -> 2; case UNKNOWN -> 3; default -> 4; };
	}

	static void orderBox(GuiGraphicsExtractor g) {
		if (!Settings.orderHud) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.player == null) return;
		List<ProductSummary.Line> lines = orderLines();
		if (lines.isEmpty()) return;
		Font font = mc.font;
		int w = 0;
		for (ProductSummary.Line l : lines) w = Math.max(w, font.width(l.text()) + (l.indent() ? 4 : 0) + (l.value().isEmpty() ? 0 : font.width(l.value()) + 8));
		w = Math.min(w + 8, 220);
		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		int h = lines.size() * (font.lineHeight + 1) + 8;
		int x = Settings.orderHudCorner.equals("top-right") ? sw - w - 4 : 4;
		int y = Settings.orderHudCorner.equals("middle-left") ? Math.max(4, (sh - h) / 2) : 4;
		panel(g, font, x, y, w, lines, sh - 4);
	}
}
