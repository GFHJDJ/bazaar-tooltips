package com.unc.bazaartips;

import com.unc.bazaartips.CraftReport.Kind;
import com.unc.bazaartips.CraftReport.Row;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A details screen: Back, the title, /bz and Rounded on top; optionally an amount picker (- box + presets Best Max);
 * then scrolling lines, each explained when hovered. Long notes wrap onto extra lines.
 */
abstract class ReportScreen extends BaseScreen {
	private List<Row> rows = List.of();
	private String builtFor = null;
	private int scroll = 0;
	private EditBox qtyBox;
	private int qtyBottom = 50;
	/** 13 when the title is too long to sit between the top buttons and gets its own line underneath, else 0. */
	private int titleDrop = 0;
	private int rowsTop, rowHeight, visibleRows, explainLines;
	private final List<int[]> qtyButtons = new ArrayList<>();   // x, y, w for each preset/Best/Max button (for explanations)
	private final List<String> qtyButtonExplains = new ArrayList<>();

	protected ReportScreen(String title, Screen parent) {
		super(title, parent);
	}

	protected abstract String heading();

	/** The name to search with /bz, or null for no /bz button. */
	protected abstract String bzName();

	protected abstract String stamp();

	protected abstract List<Row> buildRows();

	/** A click on a row that has an action (like switching how an ingredient is bought). */
	protected void onAction(String action) {}

	/** A message instead of the lines (e.g. "no longer profitable"), or null. */
	protected String missing() {
		return null;
	}

	// ---- the amount picker (off unless a screen turns it on) ----

	protected boolean hasQty() {
		return false;
	}

	protected long qty() {
		return 1;
	}

	protected void setQty(long n) {}

	protected String qtyLabel() {
		return "Amount";
	}

	protected String qtyExplain() {
		return "";
	}

	protected long[] presets() {
		return new long[0];
	}

	/** The Best amount, or null when there's no Best/Max (no coins or slots set). */
	protected Long bestQty() {
		return null;
	}

	protected Long maxQty() {
		return null;
	}

	@Override
	protected void init() {
		layoutPanel();
		button("< Back", left, 4, 50, b -> onClose());
		addRoundedToggle(4, () -> builtFor = null);
		if (bzName() != null) {
			button("/bz", left + tableWidth - ROUNDED_BUTTON_WIDTH - 4 - 34, 4, 34, b -> searchBazaar());
		}
		titleDrop = font.width(heading()) > titleRoom() ? 13 : 0;
		qtyButtons.clear();
		qtyButtonExplains.clear();
		qtyBox = null;
		if (hasQty()) {
			int y = 28 + titleDrop;
			int x = left + font.width(qtyLabel()) + 6;
			button("-", x, y, 20, b -> changeQty(qty() - 1));
			qtyBox = addRenderableWidget(new EditBox(font, x + 22, y + 2, 56, 16, Component.literal(qtyLabel())));
			qtyBox.setValue(Long.toString(qty()));
			qtyBox.setResponder(v -> {
				try {
					long n = Long.parseLong(v.trim());
					if (n >= 1 && n <= 10_000_000 && n != qty()) {
						setQty(n);
						builtFor = null;
					}
				} catch (NumberFormatException ignored) {
					// keep the last good amount while typing
				}
			});
			button("+", x + 80, y, 20, b -> changeQty(qty() + 1));
			int bx = x + 104, right = left + tableWidth;
			List<String> labels = new ArrayList<>();
			List<Long> values = new ArrayList<>();
			List<String> explains = new ArrayList<>();
			for (long p : presets()) {
				labels.add(Format.count(p));
				values.add(p);
				explains.add("Sets the amount to " + Format.count(p) + ".");
			}
			Long best = bestQty(), max = maxQty();
			if (best != null) {
				labels.add("Best");
				values.add(Math.max(1, best));
				explains.add("Best: the most profitable amount for your coins and slots (" + Format.count(Math.max(1, best)) + ").");
				labels.add("Max");
				values.add(Math.max(1, max == null ? 1 : max));
				explains.add("Max: as many as your coins, slots and limits allow (" + Format.count(Math.max(1, max == null ? 1 : max)) + ").");
			}
			// buttons that don't fit go on a second line (nothing is dropped)
			int by = y;
			for (int i = 0; i < labels.size(); i++) {
				long v = values.get(i);
				int w = font.width(labels.get(i)) + 10;
				if (bx + w > right) {
					bx = x;
					by += 22;
				}
				button(labels.get(i), bx, by, w, b -> changeQty(v));
				qtyButtons.add(new int[] {bx, by, w});
				qtyButtonExplains.add(explains.get(i));
				bx += w + 2;
			}
			qtyBottom = by + 22;
		}
		explainLines = height < 240 ? 2 : 3;
		rowHeight = font.lineHeight + 2;
		rowsTop = hasQty() ? qtyBottom + 4 : 30 + titleDrop;
		visibleRows = Math.max(3, (height - rowsTop - 8 - explainLines * (font.lineHeight + 1)) / rowHeight);
		builtFor = null;
	}

	private int titleRoom() {
		return tableWidth - 2 * (ROUNDED_BUTTON_WIDTH + 44);
	}

	private void changeQty(long n) {
		setQty(Math.max(1, n));
		if (qtyBox != null) qtyBox.setValue(Long.toString(qty()));
		builtFor = null;
	}

	private void searchBazaar() {
		if (minecraft.player == null || bzName() == null) return;
		minecraft.player.connection.sendCommand("bz " + bzName());
		minecraft.gui.setScreen(null);
	}

	private void rebuild() {
		builtFor = stamp() + "|" + qty() + "|" + Format.rounded();
		rows = wrap(buildRows());
		scroll = Math.max(0, Math.min(scroll, rows.size() - visibleRows));
	}

	/**
	 * Nothing is cut short: long notes and warnings become several lines, and when a line's grey detail doesn't
	 * fit beside its label and number, the detail moves to the lines underneath (same explanation on each).
	 */
	private List<Row> wrap(List<Row> in) {
		List<Row> out = new ArrayList<>();
		for (Row r : in) {
			if (r.kind() == Kind.LINE && !r.note().isEmpty()) {
				int x = 4 + r.indent() * 8;
				int valueW = r.value().isEmpty() ? 0 : font.width(r.value()) + 8;
				if (x + font.width(r.text()) + 6 + font.width(r.note()) + valueW > tableWidth) {
					String ex = r.text() + " (" + r.note() + ")" + (r.value().isEmpty() ? "" : " = " + r.value()) + ". " + r.explain();
					out.add(new Row(Kind.LINE, r.indent(), r.text(), r.color(), "", r.value(), r.valueColor(), r.explain(), r.action()));
					for (String l : Explain.wrap(font, r.note(), tableWidth - x - 8)) {
						out.add(new Row(Kind.NOTE, r.indent() + 1, l, Colors.DARK_GRAY, "", "", 0, ex, r.action()));
					}
					continue;
				}
			}
			if (r.kind() != Kind.NOTE && r.kind() != Kind.WARNING) {
				out.add(r);
				continue;
			}
			int max = tableWidth - 4 - r.indent() * 8;
			List<String> lines = Explain.wrap(font, r.text(), max);
			for (int i = 0; i < lines.size(); i++) {
				out.add(new Row(r.kind(), r.indent() + (i > 0 && r.kind() == Kind.WARNING ? 1 : 0), lines.get(i), r.color(), "", "", 0, r.explain(), r.action()));
			}
		}
		return out;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (!(stamp() + "|" + qty() + "|" + Format.rounded()).equals(builtFor)) rebuild();
		explain.clear();
		explainRounded(4);
		if (bzName() != null) {
			int bzX = left + tableWidth - ROUNDED_BUTTON_WIDTH - 4 - 34;
			explain.add(bzX, 4, bzX + 34, 24, "/bz: closes this and opens the bazaar search for " + bzName() + " in game.");
		}
		explain.add(left, 4, left + 50, 24, "Back to the list.");
		if (hasQty()) {
			explain.add(left, 28 + titleDrop, left + font.width(qtyLabel()) + 6 + 102, 48 + titleDrop, qtyExplain());
			for (int i = 0; i < qtyButtons.size(); i++) {
				int[] b = qtyButtons.get(i);
				explain.add(b[0], b[1], b[0] + b[2], b[1] + 20, qtyButtonExplains.get(i));
			}
		}

		drawPanel(graphics);
		if (titleDrop == 0) graphics.centeredText(font, heading(), width / 2, 10, Colors.GOLD);
		else graphics.centeredText(font, fit(heading(), tableWidth), width / 2, 28, Colors.GOLD);
		if (hasQty()) graphics.text(font, qtyLabel(), left + 2, 34 + titleDrop, Colors.GRAY);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.fill(left, rowsTop - 3, left + tableWidth, rowsTop - 2, Colors.DIVIDER);

		int explainY = height - 6 - explainLines * (font.lineHeight + 1);
		String miss = missing();
		if (miss != null) {
			graphics.centeredText(font, fit(miss, tableWidth), width / 2, rowsTop + 10, Colors.GRAY);
			drawExplainStrip(graphics, mouseX, mouseY, explainY, explainLines, "Prices or settings changed. Go back to the list for what's profitable now.");
			return;
		}
		for (int i = 0; i < visibleRows; i++) {
			int index = scroll + i;
			if (index >= rows.size()) break;
			int ry = rowsTop + i * rowHeight;
			drawRow(graphics, rows.get(index), ry, mouseX >= left && mouseX < left + tableWidth && mouseY >= ry - 1 && mouseY < ry - 1 + rowHeight);
		}
		String hint = rows.size() > visibleRows
				? "Scroll for more (" + (scroll + 1) + "-" + Math.min(rows.size(), scroll + visibleRows) + " of " + rows.size()
						+ " lines). Hover any label or number to see what it means."
				: "Hover any label or number to see what it means.";
		drawExplainStrip(graphics, mouseX, mouseY, explainY, explainLines, hint);
	}

	private void drawRow(GuiGraphicsExtractor graphics, Row r, int y, boolean hovered) {
		if (r.kind() == Kind.GAP) return;
		if (!r.action().isEmpty()) {
			// clickable: a faint band, brighter under the mouse, and the value in a box like a button
			graphics.fill(left, y - 1, left + tableWidth, y - 1 + rowHeight, hovered ? Colors.ROW_HOVER : Colors.ROW_STRIPE);
			if (!r.value().isEmpty()) {
				int vr = left + tableWidth - 2, vw = font.width(r.value());
				graphics.fill(vr - vw - 4, y - 1, vr + 2, y - 1 + rowHeight, hovered ? 0xFF6A6A6A : 0xFF4A4A4A);
			}
		}
		int x = left + 2 + r.indent() * 8;
		int right = left + tableWidth - 2;
		int valueW = r.value().isEmpty() ? 0 : font.width(r.value()) + 8;
		if (r.kind() == Kind.HEADING) {
			graphics.text(font, fit(r.text(), right - x), x, y, r.color());
		} else {
			String text = fit(r.text(), right - x - valueW);
			graphics.text(font, text, x, y, r.color());
			if (!r.note().isEmpty()) {
				int nx = x + font.width(text) + 6;
				String note = fit(r.note(), right - valueW - nx);
				if (!note.isEmpty()) graphics.text(font, note, nx, y, Colors.DARK_GRAY);
			}
			if (!r.value().isEmpty()) rightText(graphics, r.value(), right, y, r.valueColor());
		}
		if (!r.explain().isEmpty()) {
			String what = r.kind() == Kind.LINE ? r.text() + (r.note().isEmpty() ? "" : " (" + r.note() + ")") + (r.value().isEmpty() ? "" : " = " + r.value()) + ". " : "";
			explain.add(left, y - 1, left + tableWidth, y - 1 + rowHeight, what + r.explain());
		}
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true; // buttons and boxes
		if (event.button() != 0 || missing() != null || event.x() < left || event.x() >= left + tableWidth) return false;
		for (int i = 0; i < visibleRows; i++) {
			int index = scroll + i, ry = rowsTop + i * rowHeight;
			if (index >= rows.size()) break;
			if (event.y() >= ry - 1 && event.y() < ry - 1 + rowHeight && !rows.get(index).action().isEmpty()) {
				onAction(rows.get(index).action());
				builtFor = null;
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int step = scrollY > 0 ? -3 : scrollY < 0 ? 3 : 0;
		scroll = Math.max(0, Math.min(scroll + step, rows.size() - visibleRows));
		return true;
	}
}
