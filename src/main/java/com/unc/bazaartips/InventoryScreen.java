package com.unc.bazaartips;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The Inventory tab: what everything you're carrying is worth, and whether to sell each item on the Bazaar or to an NPC. */
public class InventoryScreen extends TableScreen<InventoryValue.Row> {
	private List<InventoryValue.Row> all = List.of();

	public InventoryScreen() {
		super("Inventory", "inv");
	}

	@Override
	protected void init() {
		AhPrices.ensure();
		super.init();
	}

	/** The main inventory and hotbar (36 slots; armor and the off-hand aren't for sale). */
	private static List<ItemStack> carried() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return List.of();
		return new ArrayList<>(mc.player.getInventory().getNonEquipmentItems());
	}

	/** Changes whenever a stack is added, removed or changes size (cheap enough to check every frame). */
	private static long signature() {
		long h = 1;
		for (ItemStack s : carried()) h = h * 31 + (s.isEmpty() ? 0 : s.getCount() * 7919L + System.identityHashCode(s.get(DataComponents.CUSTOM_DATA)));
		return h;
	}

	private static List<InventoryValue.Stack> read() {
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

	@Override
	protected List<Control> controls() {
		return List.of(
				sortControl("Sort your items by", List.of(
						new CraftEngine.SortOption("value", "Worth the most", "Value", "What each item is worth right now, sold the best way, most first."),
						new CraftEngine.SortOption("offer", "Most extra from a sell offer", "Offer extra", "How much more a sell offer pays than selling now."),
						new CraftEngine.SortOption("count", "How many you have", "Count", "Biggest stacks first."),
						new CraftEngine.SortOption("name", "Name", "Name", "Alphabetical.")), () -> Settings.invSort, k -> Settings.invSort = k),
				taxControl());
	}

	@Override
	protected List<Column<InventoryValue.Row>> columns() {
		return List.of(
				new Column<>("Insta-sell", "Insta-sell: what selling all of them on the Bazaar right now pays, walking the buy orders, after tax.",
						r -> money(r.instaSell()), r -> r.best().equals("bazaar") ? Colors.GREEN : Colors.YELLOW),
				new Column<>("Sell offer", "Sell offer: what listing them all 0.1 under the lowest sell offer pays once they sell, after tax. More, but you wait.",
						r -> money(r.sellOffer()), r -> r.offerExtra() > 0 ? Colors.AQUA : Colors.GRAY),
				new Column<>("NPC", "NPC: what any NPC pays for all of them. No tax, instant. Green = beats the Bazaar right now.",
						r -> money(r.npc()), r -> r.best().equals("npc") ? Colors.GREEN : Colors.YELLOW),
				new Column<>("Sell to", "Sell to: the most you get right now. Bazaar = insta-sell, NPC = any NPC, AH = the lowest BIN for things not on the Bazaar "
						+ "(before AH fees, and it may take a while to sell).",
						r -> switch (r.best()) { case "npc" -> "NPC"; case "bazaar" -> "Bazaar"; case "ah" -> "AH"; default -> "-"; },
						r -> r.best().equals("npc") ? Colors.GREEN : r.best().isEmpty() ? Colors.DARK_GRAY : Colors.WHITE));
	}

	private static String money(double v) {
		return Double.isNaN(v) ? "-" : Format.shortCoins(v);
	}

	@Override
	protected String stamp(String search) {
		return signature() + "|" + BazaarPrices.lastFetchMillis() + "|" + NpcPrices.version() + "|" + AhPrices.version() + "|" + Settings.tax()
				+ "|" + Settings.invSort + "|" + Format.rounded() + "|" + search;
	}

	@Override
	protected List<InventoryValue.Row> build(String search) {
		all = InventoryValue.rows(read(), BazaarPrices::get, NpcPrices::get, AhPrices::get, Settings.tax());
		return InventoryValue.sorted(all, Settings.invSort, search);
	}

	// ---- the totals above the list ----

	@Override
	protected List<ProductSummary.Line> intro() {
		InventoryValue.Totals t = InventoryValue.totals(all);
		List<ProductSummary.Line> out = new ArrayList<>();
		out.add(new ProductSummary.Line("Worth right now", Colors.GOLD, Format.coins(t.now()), Colors.YELLOW, false));
		if (t.withOffers() > t.now()) {
			out.add(new ProductSummary.Line("With sell offers instead", Colors.GRAY,
					Format.coins(t.withOffers()) + " (" + Format.signedShortCoins(t.withOffers() - t.now()) + ", but you wait)", Colors.AQUA, true));
		}
		if (t.npcCount() > 0) {
			out.add(new ProductSummary.Line("NPC pays more than the Bazaar for " + t.npcCount() + " item" + (t.npcCount() == 1 ? "" : "s"), Colors.GRAY,
					Format.signedShortCoins(t.npcBeatsBazaar()) + " vs insta-selling", Colors.GREEN, true));
		}
		if (t.ahValue() > 0) {
			out.add(new ProductSummary.Line("Of that, AH-only items", Colors.GRAY, "~" + Format.shortCoins(t.ahValue()) + " (lowest BINs, before fees)", Colors.YELLOW, true));
		}
		if (t.unpriced() > 0) {
			out.add(new ProductSummary.Line(t.unpriced() + " item" + (t.unpriced() == 1 ? " has" : "s have") + " no price", Colors.DARK_GRAY, "", 0, true));
		}
		return out;
	}

	@Override
	protected String introExplain(int i) {
		InventoryValue.Totals t = InventoryValue.totals(all);
		List<String> ex = new ArrayList<>();
		ex.add("Everything in your inventory and hotbar, each sold the best way right now (insta-sell, an NPC, or the AH for things not on the Bazaar).");
		if (t.withOffers() > t.now()) ex.add("If you listed the Bazaar items with sell offers instead of selling now: more coins, but you wait for buyers.");
		if (t.npcCount() > 0) ex.add("Items an NPC pays more for than insta-selling on the Bazaar. Sell them to any NPC (they're green in the list).");
		if (t.ahValue() > 0) ex.add("Items that aren't on the Bazaar, valued at their AH lowest BIN. Listing them costs AH fees, and they may take a while to sell.");
		if (t.unpriced() > 0) ex.add("Pets, gear and other things with no Bazaar, NPC or AH price (or the AH prices are still loading).");
		return i >= 0 && i < ex.size() ? ex.get(i) : null;
	}

	// ---- the rest ----

	@Override
	protected String waiting() {
		if (Minecraft.getInstance().player == null) return "Join a world first.";
		return super.waiting();
	}

	@Override
	protected String name(InventoryValue.Row r) {
		return Format.count(r.count()) + "x " + r.name();
	}

	@Override
	protected int nameColor(InventoryValue.Row r) {
		return r.priced() ? Colors.WHITE : Colors.DARK_GRAY;
	}

	@Override
	protected String tooltip(InventoryValue.Row r) {
		if (!r.priced()) return r.skyblockId() + " · no Bazaar, NPC or AH price";
		String s = switch (r.best()) {
			case "npc" -> "Sell to any NPC for " + Format.coins(r.npc()) + (Double.isNaN(r.instaSell()) ? "" : " (" + Format.signedCoins(r.npc() - r.instaSell()) + " vs insta-selling)");
			case "bazaar" -> "Insta-sell for " + Format.coins(r.instaSell());
			default -> "AH lowest BIN ~" + Format.coins(r.ah()) + " for all, before fees";
		};
		if (r.offerExtra() > 0) s += " · a sell offer pays " + Format.signedCoins(r.offerExtra()) + " more";
		if (r.weekVol() >= 0 && r.weekVol() < CraftEngine.LOW_VOLUME) s += " · slow on the Bazaar (" + Format.amount(r.weekVol()) + "/wk)";
		return s;
	}

	@Override
	protected void open(InventoryValue.Row r) {
		if (r.bazaarId() != null && BazaarPrices.get(r.bazaarId()) != null) minecraft.gui.setScreen(new PriceDetailScreen(this, r.bazaarId()));
	}

	@Override
	protected String itemExplanation() {
		return "Everything in your inventory and hotbar, stacks of the same item added up. Click a Bazaar item for its order book.";
	}

	@Override
	protected String noun() {
		return "items";
	}

	@Override
	protected String hint() {
		return "Your inventory and hotbar (not sacks, storage or equipped gear) · updates as it changes";
	}

	@Override
	protected String emptyText() {
		return "Nothing to sell in your inventory.";
	}
}
