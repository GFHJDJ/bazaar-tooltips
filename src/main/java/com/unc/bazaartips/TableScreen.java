package com.unc.bazaartips;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * A list tab (Flipper, NPC, Forge): tabs, search / coins / slots, a few setting buttons, then a table that sizes
 * its columns to fit. Hover anything for what it means; click a row for its details.
 */
abstract class TableScreen<T> extends BaseScreen {
	protected record Column<T>(String header, String explanation, Function<T, String> value, ToIntFunction<T> color) {}

	/** A setting button: its label and explanation (both re-read after every click), and what a click does. */
	protected record Control(Supplier<String> label, Supplier<String> explanation, Runnable action, Runnable back, Supplier<String> boxValue,
			java.util.function.Consumer<String> onType) {
		/** A button that does one thing (opens a screen, clears a list): right click does nothing. */
		public Control(Supplier<String> label, Supplier<String> explanation, Runnable action) {
			this(label, explanation, action, null, null, null);
		}

		/** A button that steps through options: step(+1) on a left click, step(-1) on a right click. */
		public Control(Supplier<String> label, Supplier<String> explanation, java.util.function.IntConsumer step) {
			this(label, explanation, () -> step.accept(1), () -> step.accept(-1), null, null);
		}

		/** A typing box: label() is the text beside it, boxValue() what's in it, onType() gets what you type. */
		static Control box(String label, Supplier<String> value, java.util.function.Consumer<String> onType, Supplier<String> explanation) {
			return new Control(() -> label, explanation, null, null, value, onType);
		}

		boolean isBox() {
			return onType != null;
		}
	}

	/** What's typed in each tab's search box (kept while the game is open, like the site's search). */
	private static final Map<String, String> SEARCH = new HashMap<>();

	private static final int MIN_COLUMN_WIDTH = 46;
	private static final int FOOTER_LINES = 2;
	private static final int CONTROL_ROW = 22;
	/** Where the scrolling page starts (below the tabs) and how far each item's numbers are indented. */
	/** Where the page starts: just below the tabs (set in init()). */
	private int PAGE_TOP = 20;
	private static final int INDENT = 12;

	protected final String tab;
	protected List<T> rows = List.of();
	private List<Column<T>> cols = List.of();
	private int[] colWidths = new int[0];
	/** Which line of an item each column sits on (0 = the first line under the name), and its right edge. */
	private int[] colLine = new int[0], colRight = new int[0];
	private int colLines = 1;
	private List<Control> controls = List.of();
	private final List<Button> controlButtons = new ArrayList<>();      // null for a typing box
	private final java.util.Map<Integer, EditBox> controlBoxes = new java.util.HashMap<>();
	private EditBox searchBox, coinsBox, slotsBox;
	private int perRow, controlW;
	private String builtFor = null;

	/**
	 * The page scrolls as a whole: the boxes, the buttons, the column names, then the items. Once the column names
	 * reach the top they stay there while the items scroll under them. pageScroll is in pixels.
	 */
	private int pageScroll = 0;
	private int controlsPageY, headerPageY, lineH, rowH, pageBottom, controlsEnd;
	private List<ProductSummary.Line> introLines = List.of();

	protected TableScreen(String title, String tab) {
		super(title, null);
		this.tab = tab;
	}

	// ---- what each tab fills in ----

	protected abstract List<Control> controls();

	protected abstract List<Column<T>> columns();

	/** Everything the list depends on; when it changes the list is rebuilt. */
	protected abstract String stamp(String search);

	protected abstract List<T> build(String search);

	protected abstract String name(T row);

	protected abstract int nameColor(T row);

	/** The hover tooltip for a row (" - click for details" is added). */
	protected abstract String tooltip(T row);

	protected abstract void open(T row);

	/** What the Item column means. */
	protected abstract String itemExplanation();

	/** "flips", "forge recipes", ... */
	protected abstract String noun();

	/** The footer's second line: what this tab does. */
	protected abstract String hint();

	protected abstract String emptyText();

	/** A message to show instead of the list while data is still loading, or null when ready. */
	protected String waiting() {
		return BazaarPrices.lastFetchMillis() == 0 ? "Waiting for bazaar prices..." : null;
	}

	/** Lines between the buttons and the column names (the advisor's suggested split), or empty. Read on each rebuild. */
	protected List<ProductSummary.Line> intro() {
		return List.of();
	}

	/** What hovering intro line i explains (null = nothing). */
	protected String introExplain(int i) {
		return null;
	}

	/** A click on intro line i; return true if it did something. */
	protected boolean introClicked(int i) {
		return false;
	}

	/** The footer's help while waiting() shows a message. */
	protected String waitingHint() {
		return "This downloads in the background. Give it a few seconds.";
	}

	/** Called with the rows on screen after a rebuild or scroll (the Flipper fetches their price history). */
	protected void onVisible(List<T> visible) {}

	protected String search() {
		return SEARCH.getOrDefault(tab, "");
	}

	// ---- layout ----

	@Override
	protected void init() {
		layoutPanel();
		addTabs(tab);
		PAGE_TOP = tabsBottom() + 4;
		addRoundedToggle(2, () -> rebuild(false));

		int coinsW = 64, slotsW = 34;
		int searchW = searchWidth();
		int x = left + font.width("Search") + 4;
		searchBox = addRenderableWidget(new EditBox(font, x, PAGE_TOP, searchW, 16, Component.literal("Search")));
		searchBox.setValue(search());
		searchBox.setResponder(v -> { SEARCH.put(tab, v); rebuild(true); });
		x += searchW + 6 + font.width("Coins") + 4;
		coinsBox = addRenderableWidget(new EditBox(font, x, PAGE_TOP, coinsW, 16, Component.literal("Your coins")));
		coinsBox.setValue(Settings.coins);
		coinsBox.setResponder(v -> { Settings.coins = v; Settings.save(); rebuild(false); });
		x += coinsW + 6 + font.width("Slots") + 4;
		slotsBox = addRenderableWidget(new EditBox(font, x, PAGE_TOP, slotsW, 16, Component.literal("Free slots")));
		slotsBox.setValue(Settings.slots);
		slotsBox.setResponder(v -> { Settings.slots = v; Settings.save(); rebuild(false); });

		controls = controls();
		controlButtons.clear();
		controlBoxes.clear();
		// as many buttons per row as fit with their whole labels (the page scrolls, so extra rows are fine)
		perRow = Math.max(1, Math.min(4, controls.size()));
		while (perRow > 1) {
			int w = (tableWidth - (perRow - 1) * 4) / perRow;
			boolean fits = true;
			for (Control c : controls) if (font.width(c.label().get()) + (c.isBox() ? 40 : 8) > w) fits = false;
			if (fits) break;
			perRow--;
		}
		controlW = (tableWidth - (perRow - 1) * 4) / perRow;
		controlsPageY = 22;
		for (int i = 0; i < controls.size(); i++) {
			Control c = controls.get(i);
			int cx = left + (i % perRow) * (controlW + 4);
			if (c.isBox()) {
				int lw = font.width(c.label().get()) + 6;
				EditBox box = addRenderableWidget(new EditBox(font, cx + lw, PAGE_TOP + controlY(i) + 2, controlW - lw, 16, Component.literal(c.label().get())));
				box.setValue(c.boxValue().get());
				box.setResponder(v -> { c.onType().accept(v); Settings.save(); relabelAll(); rebuild(true); });
				controlBoxes.put(i, box);
				controlButtons.add(null);
				continue;
			}
			Button btn = button(c.label().get(), cx, PAGE_TOP + controlY(i), controlW, b -> {
				c.action().run();
				Settings.save();
				if (minecraft.gui.screen() == this) {
					relabelAll();
					rebuild(true);
				}
			});
			if (c.back() != null) onRightClick(btn, () -> {
				c.back().run();
				Settings.save();
				if (minecraft.gui.screen() == this) {
					relabelAll();
					rebuild(true);
				}
			});
			controlButtons.add(btn);
		}
		int controlRows = (controls.size() + perRow - 1) / perRow;
		controlsEnd = controlsPageY + controlRows * CONTROL_ROW + 2;
		headerPageY = controlsEnd;
		lineH = font.lineHeight + 2;
		pageBottom = height - 6 - FOOTER_LINES * (font.lineHeight + 1);
		rebuild(false);
	}

	private void relabelAll() {
		for (int k = 0; k < controlButtons.size(); k++) {
			if (controlButtons.get(k) != null) relabel(controlButtons.get(k), controls.get(k).label().get(), controlW);
		}
	}

	private int controlY(int i) {
		return controlsPageY + (i / perRow) * CONTROL_ROW;
	}

	/** Where something at page position y is on screen right now. */
	private int screenY(int pageY) {
		return PAGE_TOP + pageY - pageScroll;
	}

	/** Shows the boxes and buttons that are fully inside the page area, at their scrolled position; hides the rest. */
	private void placeWidgets() {
		place(searchBox, 0);
		place(coinsBox, 0);
		place(slotsBox, 0);
		for (int i = 0; i < controlButtons.size(); i++) {
			Button b = controlButtons.get(i);
			int y = screenY(controlY(i));
			boolean vis = y >= PAGE_TOP && y + 20 <= Math.min(headerTop(), pageBottom);
			if (b != null) {
				b.setY(y);
				b.visible = vis;
			} else if (controlBoxes.containsKey(i)) {
				controlBoxes.get(i).setY(y + 2);
				controlBoxes.get(i).visible = vis;
			}
		}
	}

	private void place(EditBox box, int pageY) {
		int y = screenY(pageY);
		box.setY(y);
		box.visible = y >= PAGE_TOP && y + 16 <= Math.min(headerTop(), pageBottom);
	}

	private boolean shown(int pageY, int h) {
		int y = screenY(pageY);
		return y >= PAGE_TOP && y + h <= Math.min(headerTop(), pageBottom);
	}

	/** The column names: scroll up with the page, then stay at the top. */
	private int headerTop() {
		return Math.max(PAGE_TOP, screenY(headerPageY));
	}

	private int headerHeight() {
		return colLines * lineH + 3;
	}

	private int rowsTop() {
		return headerTop() + headerHeight() + 2;
	}

	/** Scroll needed before the column names stick to the top. */
	private int stickAt() {
		return headerPageY;
	}

	private int rowsPerScreen() {
		return Math.max(1, (pageBottom - (PAGE_TOP + headerHeight() + 2)) / rowH);
	}

	private int maxScroll() {
		return stickAt() + Math.max(0, rows.size() - rowsPerScreen()) * rowH;
	}

	/** Scrolls; once the column names are stuck, it moves a whole item at a time so none is cut in half. */
	private void scrollTo(int v) {
		v = Math.max(0, Math.min(v, maxScroll()));
		if (v > stickAt()) v = stickAt() + Math.round((float) (v - stickAt()) / rowH) * rowH;
		pageScroll = Math.min(v, maxScroll());
	}

	/** Index of the first item on screen. */
	private int firstRow() {
		return Math.max(0, (pageScroll - stickAt()) / rowH);
	}

	private int rowScreenY(int index) {
		return rowsTop() + (index - firstRow()) * rowH;
	}

	private int lastRowExclusive() {
		int n = firstRow();
		while (n < rows.size() && rowScreenY(n) + rowH <= pageBottom) n++;
		return n;
	}

	private int searchWidth() {
		int labelsW = font.width("Search") + font.width("Coins") + font.width("Slots") + 3 * 4 + 2 * 6;
		return Math.max(40, tableWidth - labelsW - 64 - 34);
	}

	/** Re-builds the list (also re-reads every control's label, since a control can change another's). */
	protected void rebuild(boolean backToTop) {
		String s = search();
		builtFor = stamp(s);
		rows = build(s);
		introLines = intro();
		headerPageY = controlsEnd + (introLines.isEmpty() ? 0 : introLines.size() * lineH + 8);
		cols = columns();
		layoutColumns();
		if (backToTop && pageScroll > stickAt()) pageScroll = stickAt();
		scrollTo(pageScroll);
		placeWidgets();
		onVisible(visible());
	}

	private List<T> visible() {
		return rows.subList(Math.min(firstRow(), rows.size()), Math.min(rows.size(), Math.max(firstRow(), lastRowExclusive())));
	}

	/**
	 * Sizes each column to its widest value in the whole list (so nothing jumps while you scroll), then puts as
	 * many as fit on each line under the item's name, right-aligned. Columns that don't fit go on another line
	 * instead of being dropped.
	 */
	private void layoutColumns() {
		int[] w = new int[cols.size()];
		for (int c = 0; c < w.length; c++) w[c] = Math.max(MIN_COLUMN_WIDTH, font.width(cols.get(c).header()) + 8);
		for (T r : rows) {
			for (int c = 0; c < w.length; c++) w[c] = Math.max(w[c], font.width(cols.get(c).value().apply(r)) + 8);
		}
		int room = tableWidth - INDENT - 2;
		int[] line = new int[w.length];
		int l = 0, used = 0;
		for (int c = 0; c < w.length; c++) {
			if (used > 0 && used + w[c] > room) { l++; used = 0; }
			line[c] = l;
			used += w[c];
		}
		int[] right = new int[w.length];
		for (int c = w.length - 1, edge = left + tableWidth - 2, cur = -1; c >= 0; c--) {
			if (line[c] != cur) { cur = line[c]; edge = left + tableWidth - 2; }
			right[c] = edge;
			edge -= w[c];
		}
		colWidths = w;
		colLine = line;
		colRight = right;
		colLines = w.length == 0 ? 1 : line[w.length - 1] + 1;
		rowH = (1 + colLines) * lineH + 3;
	}

	// ---- drawing ----

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (!stamp(search()).equals(builtFor)) {
			relabelAll();
			rebuild(false);
		}
		placeWidgets();
		explain.clear();
		explainRounded(2);
		drawPanel(graphics);
		drawTabs(graphics);

		if (shown(0, 16)) {
			int y1 = screenY(0) + 4, x = left, searchW = searchWidth();
			graphics.text(font, "Search", x, y1, Colors.GRAY);
			explain.add(x, y1 - 4, x + font.width("Search") + 4 + searchW, y1 + 12, "Search: type part of a name to filter the list.");
			x += font.width("Search") + 4 + searchW + 6;
			graphics.text(font, "Coins", x, y1, Colors.GRAY);
			explain.add(x, y1 - 4, x + font.width("Coins") + 4 + 64, y1 + 12, "Your coins: e.g. 70m, 250k or 1.2b (shared by every tab). "
					+ "With coins and/or slots set, each row gets a Best amount, and you can sort by it.");
			x += font.width("Coins") + 4 + 64 + 6;
			graphics.text(font, "Slots", x, y1, Colors.GRAY);
			explain.add(x, y1 - 4, x + font.width("Slots") + 4 + 34, y1 + 12,
					"Free inventory slots: Best amounts won't buy more than fits (most items stack to 64; books and unique items take a slot each).");
		}
		for (int i = 0; i < controls.size(); i++) {
			Button b = controlButtons.get(i);
			if (b != null) {
				if (b.visible) explain.add(b.getX(), b.getY(), b.getX() + controlW, b.getY() + 20, controls.get(i).explanation().get());
			} else if (controlBoxes.containsKey(i) && controlBoxes.get(i).visible) {
				int cx = left + (i % perRow) * (controlW + 4), cy = controlBoxes.get(i).getY() - 2;
				graphics.text(font, controls.get(i).label().get(), cx, cy + 6, Colors.GRAY);
				explain.add(cx, cy, cx + controlW, cy + 20, controls.get(i).explanation().get());
			}
		}

		int hoveredIntro = introAt(mouseX, mouseY);
		for (int i = 0; i < introLines.size(); i++) {
			int py = introY(i);
			if (!shown(py, lineH)) continue;
			int y = screenY(py);
			ProductSummary.Line l = introLines.get(i);
			graphics.fill(left, y - 1, left + tableWidth, y - 1 + lineH, i == hoveredIntro && introExplain(i) != null ? Colors.ROW_HOVER : Colors.ROW_STRIPE);
			int vw = l.value().isEmpty() ? 0 : font.width(l.value()) + 8, x = left + 3 + (l.indent() ? 6 : 0);
			graphics.text(font, fit(l.text(), left + tableWidth - 3 - vw - x), x, y + 1, l.color());
			if (vw > 0) rightText(graphics, l.value(), left + tableWidth - 3, y + 1, l.valueColor());
			String ex = introExplain(i);
			if (ex != null) explain.add(left, y - 1, left + tableWidth, y - 1 + lineH, ex);
		}

		super.extractRenderState(graphics, mouseX, mouseY, delta); // buttons and boxes

		// column names (stay at the top once you scroll down; not drawn while they're still below the bottom edge)
		int hy = headerTop();
		boolean headerShown = hy + headerHeight() <= pageBottom;
		if (headerShown) graphics.fill(left, hy - 1, left + tableWidth, hy + headerHeight(), Colors.PANEL);
		int firstLineLeft = left + tableWidth;
		for (int c = 0; c < cols.size(); c++) if (colLine[c] == 0) firstLineLeft = Math.min(firstLineLeft, colRight[c] - colWidths[c]);
		headerHits.clear();
		if (headerShown && font.width("Item") + 4 <= firstLineLeft - left) {
			drawHeader(graphics, "Item", left + 2 + font.width("Item"), hy, "name", mouseX, mouseY);
			headerHits.add(new Object[] {left, hy - 1, firstLineLeft, hy + lineH, "name"});
		}
		if (headerShown) explain.add(left, hy - 1, firstLineLeft, hy + lineH, itemExplanation() + sortHint("Item", "name"));
		for (int c = 0; c < (headerShown ? cols.size() : 0); c++) {
			int y = hy + colLine[c] * lineH;
			String h = cols.get(c).header(), key = sortKeyFor(h);
			drawHeader(graphics, h, colRight[c], y, key, mouseX, mouseY);
			if (key != null) headerHits.add(new Object[] {colRight[c] - colWidths[c] + 2, y - 1, colRight[c] + 2, y + lineH, key});
			explain.add(colRight[c] - colWidths[c] + 2, y - 1, colRight[c] + 2, y + lineH, cols.get(c).explanation() + sortHint(h, key));
		}
		if (headerShown) graphics.fill(left, hy + headerHeight() - 2, left + tableWidth, hy + headerHeight() - 1, Colors.DIVIDER);

		int footerY = pageBottom + 2;
		String wait = waiting();
		if (wait != null || rows.isEmpty()) {
			if (rowsTop() + 10 + font.lineHeight <= pageBottom)  // only when there's room above the footer
				graphics.centeredText(font, fit(wait != null ? wait : emptyText(), tableWidth), width / 2, rowsTop() + 10, Colors.GRAY);
			footer(graphics, mouseX, mouseY, footerY, wait != null ? waitingHint()
					: "Try a different search, or change the settings above.");
			return;
		}

		int hovered = rowAt(mouseX, mouseY);
		int first = firstRow(), last = lastRowExclusive();
		for (int index = first; index < last; index++) {
			T r = rows.get(index);
			int y = rowScreenY(index);
			if (index == hovered) graphics.fill(left, y - 1, left + tableWidth, y - 2 + rowH, Colors.ROW_HOVER);
			else if (index % 2 == 1) graphics.fill(left, y - 1, left + tableWidth, y - 2 + rowH, Colors.ROW_STRIPE);
			graphics.text(font, fit((index + 1) + ". " + name(r), tableWidth - 4), left + 2, y, nameColor(r));
			for (int col = 0; col < cols.size(); col++) {
				Column<T> cc = cols.get(col);
				rightText(graphics, cc.value().apply(r), colRight[col], y + (1 + colLine[col]) * lineH, cc.color().applyAsInt(r));
			}
		}

		String status = last <= first ? rows.size() + " " + noun() + " · scroll down for the list" : "Showing " + (first + 1) + "-" + last + " of " + rows.size() + " " + noun()
				+ (rows.size() > last - first ? " (scroll for more)" : "")
				+ "  |  prices " + Format.age(Math.max(0, BazaarPrices.ageMillis())) + " old  |  tax " + Settings.taxLabel();
		if (explain.at(mouseX, mouseY) != null) {
			drawExplainStrip(graphics, mouseX, mouseY, footerY, FOOTER_LINES, "");
		} else {
			if (font.width(status) > tableWidth) status = status.substring(0, status.indexOf("  |  "));
			graphics.centeredText(font, fit(status, tableWidth), width / 2, footerY, Colors.DARK_GRAY);
			graphics.centeredText(font, fit(hint(), tableWidth), width / 2, footerY + font.lineHeight + 1, Colors.DARK_GRAY);
		}
		if (hovered >= 0) {
			// wrapped onto several lines so a long tooltip doesn't stretch across the whole screen
			List<FormattedCharSequence> lines = new ArrayList<>();
			for (String l : tooltipLines(tooltip(rows.get(hovered)), Math.min(220, width / 2)))
				lines.add(Component.literal(l).getVisualOrderText());
			lines.add(Component.literal("Click for details").withStyle(net.minecraft.ChatFormatting.GRAY).getVisualOrderText());
			graphics.setTooltipForNextFrame(lines, mouseX, mouseY);
		}
	}

	/** Breaks a tooltip at its " · " separators first (so phrases stay together), then between words if still too wide. */
	private List<String> tooltipLines(String tip, int maxW) {
		List<String> out = new ArrayList<>();
		if (tip.contains("\n")) {                       // explicit lines: each one wrapped on its own
			for (String part : tip.split("\n")) out.addAll(tooltipLines(part, maxW));
			return out;
		}
		String line = "";
		for (String part : tip.split(" · ")) {
			String tryLine = line.isEmpty() ? part : line + " · " + part;
			if (font.width(tryLine) <= maxW) {
				line = tryLine;
				continue;
			}
			if (!line.isEmpty()) out.add(line);
			List<String> words = Explain.wrap(font, part, maxW);
			out.addAll(words.subList(0, words.size() - 1));
			line = words.get(words.size() - 1);
		}
		if (!line.isEmpty()) out.add(line);
		return out;
	}

	private int introY(int i) {
		return controlsEnd + 4 + i * lineH;
	}

	private int introAt(double x, double y) {
		if (x < left || x >= left + tableWidth) return -1;
		for (int i = 0; i < introLines.size(); i++) {
			int py = introY(i), sy = screenY(py);
			if (shown(py, lineH) && y >= sy - 1 && y < sy - 1 + lineH) return i;
		}
		return -1;
	}

	// ---- click a column name to sort by it ----

	/** Where each sortable column name is on screen this frame: x1, y1, x2, y2, sort key. */
	private final List<Object[]> headerHits = new ArrayList<>();

	/** The sort a column name stands for (null = that column has no sort). "Item" is always Name. */
	protected String sortKeyFor(String header) {
		return null;
	}

	/** The list's current sort key. */
	protected String currentSort() {
		return "";
	}

	protected void applySort(String key) {}

	/** Is the list sorted by this column? (A Best column also counts its "· sell offer / insta-sell" versions.) */
	protected boolean sortedBy(String key) {
		return key != null && key.equals(currentSort());
	}

	private String sortHint(String header, String key) {
		if (key == null) return " (This column can't be sorted.)";
		return sortedBy(key) ? " The list is sorted by this (underlined)." : " Click to sort by this.";
	}

	/** A column name: underlined when the list is sorted by it, brighter under the mouse when you can click it. */
	private void drawHeader(GuiGraphicsExtractor graphics, String text, int right, int y, String key, int mouseX, int mouseY) {
		int x1 = right - font.width(text);
		boolean hover = key != null && mouseX >= x1 - 2 && mouseX < right + 2 && mouseY >= y - 1 && mouseY < y + lineH;
		boolean sorted = sortedBy(key);
		graphics.text(font, text, x1, y, hover ? Colors.YELLOW : Colors.GOLD);
		if (sorted || hover) graphics.fill(x1, y + font.lineHeight - 1, right, y + font.lineHeight, sorted ? Colors.GOLD : Colors.YELLOW);
	}

	private String headerAt(double x, double y) {
		for (Object[] h : headerHits) {
			if (x >= (int) h[0] && x < (int) h[2] && y >= (int) h[1] && y < (int) h[3]) return (String) h[4];
		}
		return null;
	}

	private void footer(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int footerY, String fallback) {
		drawExplainStrip(graphics, mouseX, mouseY, footerY, FOOTER_LINES, fallback);
	}

	private int rowAt(double mouseX, double mouseY) {
		if (waiting() != null || mouseX < left || mouseX >= left + tableWidth) return -1;
		for (int i = firstRow(), last = lastRowExclusive(); i < last; i++) {
			int y = rowScreenY(i);
			if (mouseY >= y - 1 && mouseY < y - 1 + rowH) return i;
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true; // buttons and boxes
		if (event.button() == 0) {
			String key = headerAt(event.x(), event.y());
			if (key != null) {
				applySort(key);
				Settings.save();
				relabelAll();
				rebuild(true);
				return true;
			}
			int intro = introAt(event.x(), event.y());
			if (intro >= 0 && introClicked(intro)) return true;
			int index = rowAt(event.x(), event.y());
			if (index >= 0) {
				open(rows.get(index));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int before = firstRow();
		int step = pageScroll < stickAt() ? Math.max(rowH, CONTROL_ROW) : rowH;
		scrollTo(pageScroll + (scrollY > 0 ? -step : scrollY < 0 ? step : 0));
		placeWidgets();
		if (firstRow() != before) onVisible(visible());
		return true;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(null);
	}

	// ---- shared control helpers ----

	protected static String onOff(boolean v) {
		return v ? "On" : "Off";
	}

	protected Control sortControl(String heading, List<CraftEngine.SortOption> opts, Supplier<String> current,
			java.util.function.Consumer<String> choose) {
		return new Control(() -> "Sort: " + MoneyData.find(opts, current.get()).shortLabel(),
				() -> "Sort: " + MoneyData.find(opts, current.get()).label() + ". " + MoneyData.find(opts, current.get()).explanation()
						+ " Click to choose another.",
				() -> minecraft.gui.setScreen(new SortScreen(this, heading, opts, current, choose)));
	}

	protected Control taxControl() {
		return new Control(() -> "Tax: " + Settings.taxLabel(),
				() -> "Bazaar tax: " + Settings.taxLabel() + " (shared by every tab). Click to step through 1.25% (base), 1.125% and 1% "
						+ "(Bazaar Flipper upgrades); right click goes back. A custom tax can be typed in the Crafts tab's Filters & tax.",
				d -> Settings.stepTaxPreset(d));
	}
}
