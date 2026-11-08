package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.List;

/** The website's Profit / hr tab: coins per hour from each minion's drops. */
public class ProfitScreen extends TableScreen<MinionEngine.Profit> {
	public ProfitScreen() {
		super("Profit / hr", "profit");
	}

	private static <T> T next(List<T> list, T cur) {
		int i = list.indexOf(cur);
		return list.get((i + 1) % list.size());
	}

	static String hopperShort(String h) {
		return switch (h) {
			case "Budget Hopper (50% NPC)" -> "Budget (50%)";
			case "Enchanted Hopper (70% NPC, per official wiki)" -> "Ench (70%)";
			case "Enchanted Hopper (90% NPC, per other sources)" -> "Ench (90%)";
			default -> "none";
		};
	}

	static String chestName(String key) {
		for (String[] c : MinionEngine.CHESTS) if (c[0].equals(key)) return c[1];
		return "No storage chest";
	}

	@Override
	protected List<Control> controls() {
		List<Control> c = new ArrayList<>();
		c.add(new Control(() -> "Tier " + SiteData.roman(Settings.profitTier),
				() -> "Minion tier " + SiteData.roman(Settings.profitTier) + ": the tier every minion is compared at (a minion that doesn't go that high "
						+ "shows its top tier, marked). Click to step I-XII.",
				() -> Settings.profitTier = Settings.profitTier % 12 + 1));
		c.add(new Control(() -> "Sell to: " + switch (Settings.profitSell) { case "bz" -> "Bazaar"; case "npc" -> "NPC"; default -> "Best"; },
				() -> "Where drops are sold. Best = whichever of NPC and Bazaar pays more for each item; Bazaar = Bazaar (NPC* when it isn't on the "
						+ "Bazaar); NPC = always the NPC price.",
				() -> Settings.profitSell = switch (Settings.profitSell) { case "best" -> "bz"; case "bz" -> "npc"; default -> "best"; }));
		c.add(new Control(() -> "Bazaar: " + (Settings.moneySellOffer ? "Sell offer" : "Insta-sell"),
				() -> "How drops sell on the Bazaar, after tax: insta-sell (now) or sell offer (more, but you wait). Shared with the NPC, Forge and Books tabs.",
				() -> { Settings.moneySellOffer = !Settings.moneySellOffer; MoneyData.syncSortFromSell(); }));
		c.add(new Control(() -> "Auto: " + onOff(Settings.profitAuto),
				() -> "Auto-pick (" + onOff(Settings.profitAuto) + "): picks the best fuel and two upgrades for each minion (permanent fuels only). "
						+ "Off: you choose the fuel and upgrades for every minion.",
				() -> {
					Settings.profitAuto = !Settings.profitAuto;
					Settings.save();
					minecraft.gui.setScreen(new ProfitScreen()); // the buttons change
				}));
		if (Settings.profitAuto) {
			c.add(new Control(() -> "Must / never use" + (Settings.profitRequire.size() + Settings.profitExclude.size() > 0
							? " (" + (Settings.profitRequire.size() + Settings.profitExclude.size()) + ")" : ""),
					() -> "Upgrades and fuels auto-pick must include, or must never use. Must use: " + (Settings.profitRequire.isEmpty() ? "nothing"
							: String.join(", ", Settings.profitRequire)) + ". Never use: " + (Settings.profitExclude.isEmpty() ? "nothing"
							: String.join(", ", Settings.profitExclude)) + ".",
					() -> minecraft.gui.setScreen(new ProfitPickScreen(this))));
		} else {
			List<String> fuels = new ArrayList<>(MinionEngine.FUELS.keySet());
			c.add(new Control(() -> "Fuel: " + Settings.profitFuel,
					() -> "Fuel: " + Settings.profitFuel + ". Speeds the minion up (or multiplies drops, for catalysts). Click for the next one.",
					() -> Settings.profitFuel = next(fuels, Settings.profitFuel)));
			c.add(new Control(() -> "Up 1: " + Settings.profitUp1,
					() -> "Upgrade slot 1: " + Settings.profitUp1 + ". Click for the next one.",
					() -> Settings.profitUp1 = next(MinionEngine.UPGRADES, Settings.profitUp1)));
			c.add(new Control(() -> "Up 2: " + Settings.profitUp2,
					() -> "Upgrade slot 2: " + Settings.profitUp2 + ". Click for the next one.",
					() -> Settings.profitUp2 = next(MinionEngine.UPGRADES, Settings.profitUp2)));
		}
		List<String> hoppers = new ArrayList<>(MinionEngine.HOPPERS.keySet());
		c.add(new Control(() -> "Hopper: " + hopperShort(Settings.profitHopper),
				() -> "Hopper: " + Settings.profitHopper + ". A hopper sells to the NPC automatically, for part of the NPC price. Sources disagree on the "
						+ "Enchanted Hopper (70% on Hypixel's wiki, 90% elsewhere), so both are offered.",
				() -> Settings.profitHopper = next(hoppers, Settings.profitHopper)));
		List<String> chests = new ArrayList<>();
		for (String[] ch : MinionEngine.CHESTS) chests.add(ch[0]);
		c.add(new Control(() -> "Chest: " + chestName(Settings.profitChest).replace(" Storage", "").replace("No storage chest", "none"),
				() -> "Storage chest next to the minion: " + chestName(Settings.profitChest) + ". Adds slots, so it fills up later.",
				() -> Settings.profitChest = next(chests, Settings.profitChest)));
		c.add(sortControl("Sort minions by", MinionEngine.PROFIT_SORTS, () -> Settings.profitSort, k -> Settings.profitSort = k));
		c.add(taxControl());
		return c;
	}

	@Override
	protected List<Column<MinionEngine.Profit>> columns() {
		int cs = MoneyData.chestSlots();
		return List.of(
				new Column<>("Per hr", "Per hr: coins per hour from one minion's drops (a drop every 2nd action), sold the way 'Sell to' says.",
						p -> Format.shortCoins(p.total()), p -> Colors.GREEN),
				new Column<>("Per day", "Per day: coins per hour x 24 (while it's running and not full).",
						p -> Format.shortCoins(p.total() * 24), p -> Colors.GREEN),
				new Column<>("Payback", "Payback: how long until it earns back what it cost to build to this tier (insta-buying the materials).",
						p -> { String h = Amounts.hours(p.paybackInsta() == null ? 0 : p.paybackInsta()); return h == null ? "-" : h; }, p -> Colors.GRAY),
				new Column<>("Full in", "Full in: how long until its storage (plus your storage chest) is full and it stops. With a hopper it sells instead.",
						p -> { Double h = p.fillHours(cs); return h == null ? "-" : fill(h); }, p -> Colors.GRAY));
	}

	static String fill(double h) {
		if (h > 24 * 60) return "60+ days";
		String s = Amounts.hours(h);
		return s == null ? "-" : s;
	}

	@Override
	protected String stamp(String search) {
		return MoneyData.profitStampNow() + "|" + Settings.profitSort + "|" + Settings.profitChest + "|" + Format.rounded() + "|" + search;
	}

	@Override
	protected List<MinionEngine.Profit> build(String search) {
		return MinionEngine.sortProfits(MoneyData.profits(), Settings.profitSort, search, MoneyData.chestSlots());
	}

	@Override
	protected String name(MinionEngine.Profit p) {
		return p.name() + (p.usedMax() ? " (T" + SiteData.roman(p.tier()) + ")" : "");
	}

	@Override
	protected int nameColor(MinionEngine.Profit p) {
		return p.usedMax() ? Colors.ORANGE : Colors.WHITE;
	}

	@Override
	protected String tooltip(MinionEngine.Profit p) {
		String s = p.type().toLowerCase() + " · acts every " + Amounts.trim1(p.actionSec()) + "s";
		if (Settings.profitAuto) s += " · " + p.combo();
		String pb = Amounts.hours(p.paybackInsta() == null ? 0 : p.paybackInsta());
		if (pb != null) s += " · pays back in " + pb;
		if (p.usedMax()) s += " · no tier " + SiteData.roman(Settings.profitTier) + ", showing tier " + SiteData.roman(p.tier());
		return s;
	}

	@Override
	protected void open(MinionEngine.Profit p) {
		minecraft.gui.setScreen(new ProfitDetailScreen(this, p.name()));
	}

	@Override
	protected String itemExplanation() {
		return "Each minion, by coins per hour (or the Sort button). Orange (T..) = it doesn't go up to your tier, so its top tier is shown. Click for every drop.";
	}

	@Override
	protected String noun() {
		return "minions";
	}

	@Override
	protected String hint() {
		return "Tier " + SiteData.roman(Settings.profitTier) + " · " + (Settings.profitAuto ? "best fuel + upgrades auto-picked" : comboNow())
				+ (Settings.profitHopper.equals("No hopper") ? "" : " · " + hopperShort(Settings.profitHopper) + " hopper") + " · one minion, a drop every 2nd action";
	}

	static String comboNow() {
		List<String> p = new ArrayList<>();
		for (String s : new String[] {Settings.profitFuel, Settings.profitUp1, Settings.profitUp2}) if (!s.equals("None")) p.add(s);
		return p.isEmpty() ? "no fuel or upgrades" : String.join(" + ", p);
	}

	@Override
	protected String emptyText() {
		return "No minions match.";
	}

	@Override
	protected String sortKeyFor(String header) {
		return switch (header) {
			case "Per hr", "Per day" -> "total";
			case "Payback" -> "payback";
			case "Full in" -> "fill";
			default -> null;
		};
	}

	@Override
	protected String currentSort() {
		return Settings.profitSort;
	}

	@Override
	protected void applySort(String key) {
		Settings.profitSort = key;
	}
}
