package com.unc.bazaartips;

import net.minecraft.client.gui.screens.Screen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One NPC flip or forge recipe in full, with the site's "how many?" calculator. */
public class MoneyDetailScreen extends ReportScreen {
	/** The amount you typed, per row (kept while the game is open, like the site's moneyQty). */
	private static final Map<String, Long> QTY = new HashMap<>();
	/** How you chose to buy each item, per row (item id -> buy order?), kept while the game is open. */
	private static final Map<String, Map<String, Boolean>> CHOICE = new HashMap<>();

	private final String kind, key;

	public MoneyDetailScreen(Screen parent, String kind, String key) {
		super(switch (kind) { case "forge" -> "Forge"; case "books" -> "Books"; case "cur" -> "Currencies"; case "bits" -> "Bits"; case "kat" -> "Kat";
			default -> "NPC flip"; }, parent);
		this.kind = kind;
		this.key = kind + ":" + key;
		this.rowKey = key;
	}

	private final String rowKey;

	/** Items you switched away from the list's Buy setting. */
	private java.util.Set<String> flipped() {
		java.util.Set<String> f = new java.util.TreeSet<>();
		CHOICE.getOrDefault(key, Map.of()).forEach((id, order) -> { if (order != Settings.moneyBuyOrder) f.add(id); });
		return f;
	}

	private String cachedFor = null;
	private MoneyEngine.Row cached;

	/** This row, priced with your per-item choices (the list's own row when you haven't switched anything). */
	private MoneyEngine.Row row() {
		java.util.Set<String> f = flipped();
		String st = MoneyData.listStamp("", "") + "|" + f;
		if (st.equals(cachedFor)) return cached;
		List<MoneyEngine.Row> all;
		if (f.isEmpty()) {
			all = switch (kind) {
				case "forge" -> MoneyData.forge();
				case "shop" -> MoneyData.npcShops();
				case "books" -> MoneyData.books();
				case "cur" -> MoneyData.currencies();
				case "bits" -> MoneyData.bits();
				case "kat" -> MoneyData.kat();
				default -> MoneyData.npcFromBazaar();
			};
		} else {
			MoneyEngine.Ctx b = MoneyData.ctx();
			MoneyEngine e = new MoneyEngine(new MoneyEngine.Ctx(b.prices(), b.npcSell(), b.ahBin(), b.tax(), b.buyOrder(), b.sellOffer(),
					b.bazaarOnly(), b.npcAvail(), b.forgeHotm(), b.coins(), b.slots(), f));
			all = switch (kind) {
				case "forge" -> e.forge();
				case "shop" -> e.npcShops();
				case "books" -> e.books(new java.util.ArrayList<>(BazaarPrices.ids()));
				case "cur" -> e.currencies(Settings.curType);
				case "bits" -> e.currencies("bits");
				case "kat" -> e.kat();
				default -> e.npcFromBazaar(List.of(rowKey));
			};
		}
		cached = null;
		for (MoneyEngine.Row r : all) if (r.key.equals(rowKey)) cached = r;
		cachedFor = st;
		return cached;
	}

	/** The list's own row (always there while it's on the list), for names and the items to switch. */
	private MoneyEngine.Row listRow() {
		List<MoneyEngine.Row> all = switch (kind) {
			case "forge" -> MoneyData.forge();
			case "shop" -> MoneyData.npcShops();
			case "books" -> MoneyData.books();
			case "cur" -> MoneyData.currencies();
			case "bits" -> MoneyData.bits();
			case "kat" -> MoneyData.kat();
			default -> MoneyData.npcFromBazaar();
		};
		for (MoneyEngine.Row r : all) if (r.key.equals(rowKey)) return r;
		return null;
	}

	/** What can be bought either way: bazaar materials (forge, shop), or the item itself (Bazaar -> NPC). */
	private List<String[]> buyItems() {
		MoneyEngine.Row r = listRow();
		List<String[]> out = new java.util.ArrayList<>();
		if (r == null) return out;
		if (kind.equals("npc")) {
			out.add(new String[] {r.itemId, r.name});
		} else if (kind.equals("books")) {
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(ENCHANTMENT_.+_)(\\d+)$").matcher(r.key);
			if (m.matches()) {
				String a = m.group(1) + (Integer.parseInt(m.group(2)) - 1);
				out.add(new String[] {a, r.name + " " + SiteData.roman(Integer.parseInt(m.group(2)) - 1) + " (the two you buy)"});
			}
		} else {
			for (MoneyEngine.Part p : r.parts) if (p.id() != null && p.src().equals("Bazaar")) out.add(new String[] {p.id(), p.label()});
		}
		return out;
	}

	@Override
	protected void onAction(String action) {
		if (action.equals("reset")) {
			CHOICE.remove(key);
		} else if (action.startsWith("buy:")) {
			String id = action.substring(4);
			boolean now = Settings.moneyBuyOrder != flipped().contains(id);
			CHOICE.computeIfAbsent(key, k -> new HashMap<>()).put(id, !now);
		}
	}

	@Override
	protected String heading() {
		MoneyEngine.Row r = row() != null ? row() : listRow();
		return r == null ? "" : r.title;
	}

	@Override
	protected String bzName() {
		MoneyEngine.Row r = row() != null ? row() : listRow();
		if (r == null) return null;
		String mid = SiteData.marketId(r.itemId, BazaarPrices::get);
		return BazaarPrices.get(mid) == null ? null : Recipes.name(mid);
	}

	@Override
	protected String stamp() {
		return MoneyData.listStamp("", "") + "|" + flipped();
	}

	@Override
	protected String missing() {
		return row() == null && flipped().isEmpty() ? (kind.equals("forge") ? "This recipe isn't on the list with the current prices and settings"
				: "Not profitable right now with the current prices and settings") : null;
	}

	@Override
	protected List<CraftReport.Row> buildRows() {
		MoneyEngine.Row r = row();
		java.util.Set<String> f = flipped();
		List<CraftReport.Row> rows = new java.util.ArrayList<>(MoneyReport.buyToggleRows(buyItems(),
				id -> Settings.moneyBuyOrder != f.contains(id), Settings.moneyBuyOrder));
		if (r == null) {
			rows.add(new CraftReport.Row(CraftReport.Kind.WARNING, 0, "! Not profitable (or can't be priced) with this mix. Switch an item back, or press Reset.",
					Colors.RED, "", "", 0, "With your choices this doesn't make money right now, so there's nothing to show."));
			return rows;
		}
		rows.addAll(MoneyReport.money(kind.equals("bits") ? "cur" : kind, r, qty(), Settings.taxLabel()));
		return rows;
	}

	// ---- the calculator ----

	@Override
	protected boolean hasQty() {
		MoneyEngine.Row r = row();
		return r != null && r.calc(1) != null;
	}

	@Override
	protected long qty() {
		Long typed = QTY.get(key);
		if (typed != null) return typed;
		MoneyEngine.Row r = row();
		if (r == null) return 1;
		return r.best != null && r.best.n() > 0 ? r.best.n() : Math.max(1, r.defQty);
	}

	@Override
	protected void setQty(long n) {
		QTY.put(key, Math.max(1, n));
	}

	@Override
	protected String qtyLabel() {
		MoneyEngine.Row r = row();
		return r == null ? "Buy" : r.qtyLabel;
	}

	@Override
	protected String qtyExplain() {
		MoneyEngine.Row r = row();
		String unit = r == null ? "" : r.qtyUnit;
		return qtyLabel() + " (" + unit + "): the amount the How many? section works out. Use - / +, type a number, or a preset."
				+ (Settings.coinsBudget() > 0 || Settings.freeSlots() > 0 ? "" : " Enter your coins/slots on the list for Best and Max.");
	}

	@Override
	protected long[] presets() {
		MoneyEngine.Row r = row();
		return r == null ? new long[0] : r.presets;
	}

	@Override
	protected Long bestQty() {
		MoneyEngine.Row r = row();
		if (r == null || new MoneyEngine(MoneyData.ctx()).maxQty(r) == null) return null;
		return r.best != null && r.best.n() > 0 ? r.best.n() : 1;
	}

	@Override
	protected Long maxQty() {
		MoneyEngine.Row r = row();
		return r == null ? null : new MoneyEngine(MoneyData.ctx()).maxQty(r);
	}
}
