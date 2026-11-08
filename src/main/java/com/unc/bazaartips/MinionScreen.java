package com.unc.bazaartips;

import java.util.List;

/** The website's Minions tab: what each minion costs to build to its top tier. */
public class MinionScreen extends TableScreen<MinionEngine.Minion> {
	public MinionScreen() {
		super("Minions", "minions");
	}

	@Override
	protected List<Control> controls() {
		return List.of(sortControl("Sort minions by", MinionEngine.MINION_SORTS, () -> Settings.minionSort, k -> Settings.minionSort = k));
	}

	@Override
	protected List<Column<MinionEngine.Minion>> columns() {
		return List.of(
				new Column<>("Insta-buy", "Insta-buy: what building it from tier I to its top tier costs if you insta-buy every material (Hypixel's averaged price).",
						m -> Format.shortCoins(m.total()), m -> Colors.YELLOW),
				new Column<>("Buy order", "Buy order: the same, buying every material with buy orders (cheaper, but you wait).",
						m -> Format.shortCoins(m.totalOrder()), m -> Colors.YELLOW),
				new Column<>("Top tier", "Top tier: the highest tier the costs go up to.", m -> SiteData.roman(m.maxTier()), m -> Colors.GRAY));
	}

	@Override
	protected String stamp(String search) {
		return BazaarPrices.lastFetchMillis() + "|" + Settings.minionSort + "|" + Format.rounded() + "|" + search;
	}

	@Override
	protected List<MinionEngine.Minion> build(String search) {
		return MinionEngine.sortMinions(MoneyData.minions(), Settings.minionSort, search);
	}

	@Override
	protected String name(MinionEngine.Minion m) {
		return (m.partial() ? "! " : "") + m.name();
	}

	@Override
	protected int nameColor(MinionEngine.Minion m) {
		return m.partial() ? Colors.ORANGE : Colors.WHITE;
	}

	@Override
	protected String tooltip(MinionEngine.Minion m) {
		String s = !m.req().isEmpty() ? "Requires " + m.req() : !m.source().isEmpty() ? "From " + m.source() + " · no collection" : "No collection requirement listed";
		if (m.partial()) s += " · ! " + (m.source().isEmpty() ? "tier I recipe missing" : "tier I not craftable") + ", cost from tier " + SiteData.roman(m.fromTier());
		return s;
	}

	@Override
	protected void open(MinionEngine.Minion m) {
		minecraft.gui.setScreen(new MinionDetailScreen(this, m.key()));
	}

	@Override
	protected String itemExplanation() {
		return "Each minion, cheapest to build first (or by name). Orange '!' = the cost starts above tier I. Click one for every tier's materials.";
	}

	@Override
	protected String noun() {
		return "minions";
	}

	@Override
	protected String hint() {
		return "Costs use Hypixel's averaged bazaar prices · click a minion for each tier's materials";
	}

	@Override
	protected String emptyText() {
		return "No minions match.";
	}

	@Override
	protected String sortKeyFor(String header) {
		return header.equals("Insta-buy") ? "total" : null;
	}

	@Override
	protected String currentSort() {
		return Settings.minionSort;
	}

	@Override
	protected void applySort(String key) {
		Settings.minionSort = key;
	}
}
