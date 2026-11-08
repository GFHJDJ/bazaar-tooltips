package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.List;

/** The website's Essence tab: buy the cheapest AH listing, salvage or sacrifice it, sell what comes out. */
public class EssenceScreen extends TableScreen<EssenceEngine.Ess> {
	public EssenceScreen() {
		super("Essence", "essence");
	}

	@Override
	protected void init() {
		AhPrices.ensure();
		super.init();
	}

	@Override
	protected List<Control> controls() {
		List<String> types = new ArrayList<>(List.of("all"));
		types.addAll(EssenceEngine.ESSENCE_NAMES.keySet());
		return List.of(
				new Control(() -> "Essence: " + (Settings.essenceType.equals("all") ? "All" : EssenceEngine.ESSENCE_NAMES.getOrDefault(Settings.essenceType, Settings.essenceType)),
						() -> "Which essence: shows only items that turn into it (All = everything). Click for the next one.",
						() -> Settings.essenceType = types.get((types.indexOf(Settings.essenceType) + 1) % types.size())),
				new Control(() -> "Sell: " + (Settings.essenceSellOffer ? "Sell offer" : "Insta-sell"),
						() -> "How the essence sells on the Bazaar, after tax: insta-sell (now) or sell offer (more, but you wait).",
						() -> Settings.essenceSellOffer = !Settings.essenceSellOffer),
				new Control(() -> "Profitable only: " + onOff(Settings.essenceProfitableOnly),
						() -> "Profitable only (" + onOff(Settings.essenceProfitableOnly) + "): hides items that don't make money.",
						() -> Settings.essenceProfitableOnly = !Settings.essenceProfitableOnly),
				new Control(() -> "Bazaar only: " + onOff(Settings.bazaarOnly),
						() -> "Bazaar sales only (" + onOff(Settings.bazaarOnly) + "): hides items where something they turn into would have to be sold on the AH. "
								+ "Shared with the NPC, Forge and Currency tabs.",
						() -> Settings.bazaarOnly = !Settings.bazaarOnly),
				sortControl("Sort essence flips by", EssenceEngine.SORTS, () -> Settings.essenceSort, k -> Settings.essenceSort = k),
				taxControl());
	}

	@Override
	protected List<Column<EssenceEngine.Ess>> columns() {
		return List.of(
				new Column<>("AH price", "AH price: the cheapest Buy-It-Now listing (checked live with SkyCofl when it's on screen; a lone very cheap listing "
						+ "uses the next one instead).", e -> Format.shortCoins(e.cost()), e -> Colors.YELLOW),
				new Column<>("Value", "Value: what everything it salvages (or sacrifices) into sells for: essence on the Bazaar after tax, items on the Bazaar or AH.",
						e -> Format.shortCoins(e.value()), e -> Colors.YELLOW),
				new Column<>("Profit", "Profit: value minus the AH price. AH fees and the Draconic Altar's random bonus drops aren't counted.",
						e -> Format.signedShortCoins(e.profit()), e -> Colors.profit(e.profit())),
				new Column<>("Return", "Return: profit as a % of the AH price.", e -> MoneyEngine.pctStr(e.pct()), e -> Colors.GRAY));
	}

	@Override
	protected String stamp(String search) {
		return MoneyData.essenceStampNow() + "|" + Settings.essenceType + "|" + Settings.essenceProfitableOnly + "|" + Settings.essenceSort + "|"
				+ Format.rounded() + "|" + search;
	}

	@Override
	protected List<EssenceEngine.Ess> build(String search) {
		AhPrices.ensure();
		return EssenceEngine.list(MoneyData.essence(), search, Settings.essenceType, Settings.essenceProfitableOnly, Settings.essenceSort);
	}

	@Override
	protected String waiting() {
		String w = super.waiting();
		if (w != null) return w;
		if (NpcPrices.salvage().isEmpty()) return "Loading Hypixel's item list (what everything salvages into)...";
		if (!AhPrices.loaded()) return "Loading auction-house prices...";
		return null;
	}

	@Override
	protected void onVisible(List<EssenceEngine.Ess> visible) {
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < Math.min(12, visible.size()); i++) ids.add(visible.get(i).id());
		CoflData.queueBin(ids, false);
	}

	@Override
	protected String name(EssenceEngine.Ess e) {
		return (e.outlier() ? "! " : "") + e.name() + (e.sacrifice() ? " (sacrifice)" : "");
	}

	@Override
	protected int nameColor(EssenceEngine.Ess e) {
		return e.outlier() ? Colors.ORANGE : e.check() != null ? Colors.WHITE : Colors.GRAY;
	}

	@Override
	protected String tooltip(EssenceEngine.Ess e) {
		StringBuilder s = new StringBuilder(e.check() != null ? (e.outlier() ? "! cheapest looks like a mistake, using the next listing" : "listed now")
				: "checking the listing...");
		for (EssenceEngine.Val v : e.vals()) s.append(" · +").append(Format.count(v.o().amount())).append(" ").append(v.o().label());
		return s.toString();
	}

	@Override
	protected void open(EssenceEngine.Ess e) {
		minecraft.gui.setScreen(new EssenceDetailScreen(this, e.id()));
	}

	@Override
	protected String itemExplanation() {
		return "What to buy on the AH and salvage. White = the listing was just checked live; grey = still checking; orange '!' = the cheapest listing "
				+ "looked like a mistake, so the next one is used. Click for details.";
	}

	@Override
	protected String noun() {
		return "items";
	}

	@Override
	protected String hint() {
		return "Live listing checks provided by SkyCofl · AH fees and Draconic Altar bonus drops aren't counted";
	}

	@Override
	protected String emptyText() {
		return "No " + (Settings.essenceProfitableOnly ? "profitable " : "") + "items match right now" + (Settings.essenceProfitableOnly ? " (try Profitable only: Off)" : "");
	}

	@Override
	protected String sortKeyFor(String header) {
		return switch (header) {
			case "AH price" -> "cost";
			case "Value" -> "value";
			case "Profit" -> "profit";
			case "Return" -> "pct";
			default -> null;
		};
	}

	@Override
	protected String currentSort() {
		return Settings.essenceSort;
	}

	@Override
	protected void applySort(String key) {
		Settings.essenceSort = key;
	}
}
