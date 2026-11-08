package com.unc.bazaartips;

import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Everything on the website's craft card and its open panel, for one craft. Scroll for more; hover anything to see what it means. */
public class FlipDetailScreen extends ReportScreen {
	/** The craft amount you picked, per item (kept while the game is open, like the site's qtyByKey). */
	private static final Map<String, Long> QTY = new HashMap<>();
	/** How you chose to buy each item, per craft (item id -> buy order?), kept while the game is open. */
	private static final Map<String, Map<String, Boolean>> CHOICE = new HashMap<>();

	private final String key;

	public FlipDetailScreen(Screen parent, String key) {
		super(Recipes.name(key), parent);
		this.key = key;
	}

	private static boolean listOrder() {
		return Settings.buy == Settings.Buy.ORDER;
	}

	/** Items you switched away from the list's Buy setting. */
	private java.util.Set<String> flipped() {
		java.util.Set<String> f = new java.util.TreeSet<>();
		CHOICE.getOrDefault(key, Map.of()).forEach((id, order) -> { if (order != listOrder()) f.add(id); });
		return f;
	}

	private CraftEngine.Craft craft() {
		return CraftEngine.current().craft(key, listOrder(), Settings.chains != Settings.Chains.EXCLUDE, flipped());
	}

	/** Every item you'd actually buy for this craft (crafted parts broken down), with names. */
	private List<String[]> buyItems(CraftEngine.Craft c) {
		List<String[]> out = new ArrayList<>();
		if (c != null) {
			for (String id : CraftEngine.leaf(c, 1).keySet()) if (BazaarPrices.get(id) != null) out.add(new String[] {id, Recipes.name(id)});
		} else {
			Recipes.Recipe rec = Recipes.get(key);
			if (rec != null) for (Recipes.Ing i : rec.ingredients()) out.add(new String[] {i.id(), Recipes.name(i.id())});
		}
		return out;
	}

	@Override
	protected void onAction(String action) {
		if (action.equals("reset")) {
			CHOICE.remove(key);
		} else if (action.startsWith("buy:")) {
			String id = action.substring(4);
			CraftEngine.Craft c = craft();
			boolean now = c != null ? c.orderFor(id) : listOrder() != flipped().contains(id);
			CHOICE.computeIfAbsent(key, k -> new HashMap<>()).put(id, !now);
		}
	}

	@Override
	protected String heading() {
		CraftEngine.Craft c = craft();
		return Recipes.name(key) + (c != null && c.output() > 1 ? " x" + c.output() : "");
	}

	@Override
	protected String bzName() {
		return Recipes.name(key);
	}

	@Override
	protected String stamp() {
		return BazaarPrices.lastFetchMillis() + "|" + Settings.tax() + "|" + Settings.buy + "|" + Settings.chains + "|" + Settings.coins + "|"
				+ Settings.slots + "|" + Settings.sort + "|" + Settings.unlocks.hashCode() + "|" + flipped();
	}

	@Override
	protected String missing() {
		return craft() == null && flipped().isEmpty() ? "Can't be crafted this way right now (no bazaar price for it or an ingredient with this Buy setting)" : null;
	}

	@Override
	protected List<CraftReport.Row> buildRows() {
		CraftEngine.Craft c = craft();
		List<CraftReport.Row> rows = new ArrayList<>(MoneyReport.buyToggleRows(buyItems(c),
				id -> c != null ? c.orderFor(id) : listOrder() != flipped().contains(id), listOrder()));
		if (c == null) {
			rows.add(new CraftReport.Row(CraftReport.Kind.WARNING, 0, "! Can't be priced this way: an item has no sell offers or buy orders to use. "
					+ "Switch it back, or press Reset.", Colors.RED, "", "", 0, "With this mix, something can't be bought on the bazaar right now."));
			return rows;
		}
		rows.add(new CraftReport.Row(CraftReport.Kind.NOTE, 0, (c.mixed() ? "Ingredients: your mix (above)" : listOrder() ? "Ingredients by buy order" : "Ingredients by insta-buy")
				+ ", tax " + Settings.taxLabel(), Colors.DARK_GRAY, "", "", 0,
				"How these numbers were worked out: the Buy setting and the bazaar tax from the list screen."));
		rows.addAll(CraftReport.build(CraftEngine.current(), c, qty(), Settings.sort, Settings.coinsBudget(), Settings.freeSlots()));
		return rows;
	}

	@Override
	protected boolean hasQty() {
		return true;
	}

	@Override
	protected long qty() {
		return QTY.getOrDefault(key, 1L);
	}

	@Override
	protected void setQty(long n) {
		QTY.put(key, Math.max(1, n));
	}

	@Override
	protected String qtyLabel() {
		return "Craft amount";
	}

	@Override
	protected String qtyExplain() {
		return "Craft amount: how many crafts the Plan section works out (materials, cost, profit). Use - / + or type a number.";
	}
}
