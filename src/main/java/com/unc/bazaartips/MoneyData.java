package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Flipper, NPC and Forge lists, built from the live prices and your settings, and rebuilt only when
 * one of those changes (new prices, new AH or NPC prices, new SkyCofl data, or a setting).
 * No Minecraft code, so it can be tested on its own.
 */
public final class MoneyData {
	private MoneyData() {}

	// ---------------------------------------------------------------- sort options (the site's fillSort)

	private static CraftEngine.SortOption o(String key, String label, String shortLabel, String explanation) {
		return new CraftEngine.SortOption(key, label, shortLabel, explanation);
	}

	private static final String BEST = "Profit from the best amount for your coins and free slots, walking the live order book "
			+ "(items that can't be sized use their plain profit). Enter your coins and/or slots at the top first.";

	public static final List<CraftEngine.SortOption> FORGE_SORTS = List.of(
			o("coins", "Best for coins & slots", "Best for me", BEST),
			o("coinsSO", "Best for coins & slots · sell offer", "Best (offer)", BEST + " Also switches Sell to sell offer."),
			o("coinsIS", "Best for coins & slots · insta-sell", "Best (insta)", BEST + " Also switches Sell to insta-sell."),
			o("profit", "Profit per forge", "Profit / forge", "What one forge makes after buying the materials and selling the result."),
			o("perHour", "Profit / hr of forge time", "Profit / hr", "Profit per forge divided by how long it takes in the forge."),
			o("pct", "Return %", "Return %", "Profit as a % of what the materials cost."),
			o("time", "Fastest", "Fastest", "Shortest forge time first."),
			o("name", "Name", "Name", "Alphabetical."));

	public static final List<CraftEngine.SortOption> NPC_SORTS = List.of(
			o("coins", "Best for coins & slots", "Best for me", BEST),
			o("profit", "Profit each", "Profit each", "What an NPC pays minus what one costs on the bazaar."),
			o("pct", "Return %", "Return %", "Profit each as a % of the bazaar price."),
			o("vol", "Most for sale", "Most for sale", "Most items listed in sell offers right now (how many you could buy)."),
			o("name", "Name", "Name", "Alphabetical."));

	public static final List<CraftEngine.SortOption> SHOP_SORTS = List.of(
			o("coins", "Best for coins & slots", "Best for me", BEST),
			o("coinsSO", "Best for coins & slots · sell offer", "Best (offer)", BEST + " Also switches Sell to sell offer."),
			o("coinsIS", "Best for coins & slots · insta-sell", "Best (insta)", BEST + " Also switches Sell to insta-sell."),
			o("profit", "Profit", "Profit", "What one purchase sells for minus what the NPC charges."),
			o("pct", "Return %", "Return %", "Profit as a % of what the NPC charges."),
			o("name", "Name", "Name", "Alphabetical."));

	public static final List<CraftEngine.SortOption> BOOK_SORTS = List.of(
			o("coins", "Best for coins & slots", "Best for me", BEST),
			o("coinsSO", "Best for coins & slots · sell offer", "Best (offer)", BEST + " Also switches Sell to sell offer."),
			o("coinsIS", "Best for coins & slots · insta-sell", "Best (insta)", BEST + " Also switches Sell to insta-sell."),
			o("profit", "Profit", "Profit", "What the level N+1 book sells for minus two level N books."),
			o("pct", "Return %", "Return %", "Profit as a % of what the two books cost."),
			o("vol", "Volume", "Volume", "The slower of: level N bought per week, level N+1 sold per week."),
			o("name", "Name", "Name", "Alphabetical."));

	public static List<CraftEngine.SortOption> curSorts() {
		return curSorts(Settings.curType);
	}

	/** The sorts for one currency's shop (the Bits tab uses "bits"). */
	public static List<CraftEngine.SortOption> curSorts(String type) {
		String[] n = MoneyEngine.CURRENCIES.getOrDefault(type, MoneyEngine.CURRENCIES.get("copper"));
		String one = n[1];
		return List.of(
				o("per", "Coins per " + one, "Coins / " + one, "How many coins each " + one + " turns into: what it sells for (minus any extra cost), divided by its price."),
				o("value", "Total value", "Total value", "What one purchase sells for, minus any extra coins or items it costs."),
				o("amt", "Cheapest", "Cheapest", "Fewest " + n[2] + " first."),
				o("name", "Name", "Name", "Alphabetical."));
	}

	public static final List<CraftEngine.SortOption> KAT_SORTS = List.of(
			o("profit", "Profit", "Profit", "What the upgraded pet sells for minus the pet, Kat's coins and the materials."),
			o("perHour", "Profit / day of Kat time", "Profit / day", "Profit divided by how long Kat takes."),
			o("pct", "Return %", "Return %", "Profit as a % of everything it costs."),
			o("time", "Fastest", "Fastest", "Shortest Kat time first."),
			o("name", "Name", "Name", "Alphabetical."));

	public static CraftEngine.SortOption find(List<CraftEngine.SortOption> opts, String key) {
		for (CraftEngine.SortOption s : opts) if (s.key().equals(key)) return s;
		return opts.get(0);
	}

	/** syncSellFromSort: picking "· sell offer" / "· insta-sell" also sets how you sell. */
	public static void syncSellFromSort(String key) {
		if (key.equals("coinsSO")) Settings.moneySellOffer = true;
		else if (key.equals("coinsIS")) Settings.moneySellOffer = false;
	}

	/** When Sell changes while sorting by "Best · sell offer / insta-sell", the sort follows (like the site). */
	public static void syncSortFromSell() {
		String want = Settings.moneySellOffer ? "coinsSO" : "coinsIS";
		if (Settings.planSort.equals("coinsSO") || Settings.planSort.equals("coinsIS")) Settings.planSort = want;
		if (Settings.forgeSort.equals("coinsSO") || Settings.forgeSort.equals("coinsIS")) Settings.forgeSort = want;
		if (Settings.npcShopSort.equals("coinsSO") || Settings.npcShopSort.equals("coinsIS")) Settings.npcShopSort = want;
		if (Settings.bookSort.equals("coinsSO") || Settings.bookSort.equals("coinsIS")) Settings.bookSort = want;
	}

	// ---------------------------------------------------------------- the engine for the current settings

	public static MoneyEngine.Ctx ctx() {
		return new MoneyEngine.Ctx(BazaarPrices::get, NpcPrices::get, AhPrices::get, Settings.tax(), Settings.moneyBuyOrder,
				Settings.moneySellOffer, Settings.bazaarOnly, Settings.npcAvail, Settings.forgeHotm, Settings.coinsBudget(), Settings.freeSlots());
	}

	private static String moneyStamp() {
		return BazaarPrices.lastFetchMillis() + "|" + NpcPrices.version() + "|" + AhPrices.version() + "|" + Settings.tax() + "|"
				+ Settings.moneyBuyOrder + "|" + Settings.moneySellOffer + "|" + Settings.bazaarOnly + "|" + Settings.npcAvail + "|"
				+ Settings.forgeHotm + "|" + Settings.coins + "|" + Settings.slots + "|" + Format.rounded();
	}

	private static String forgeStamp = "", npcStamp = "", shopStamp = "";
	private static List<MoneyEngine.Row> forge = List.of(), npc = List.of(), shop = List.of();
	private static int hiddenShops = 0;

	/** The Forge rows for the current settings (unsorted). */
	public static synchronized List<MoneyEngine.Row> forge() {
		String st = moneyStamp();
		if (!st.equals(forgeStamp)) {
			forge = new MoneyEngine(ctx()).forge();
			forgeStamp = st;
		}
		return forge;
	}

	/** Bazaar -> NPC rows (unsorted). */
	public static synchronized List<MoneyEngine.Row> npcFromBazaar() {
		String st = moneyStamp();
		if (!st.equals(npcStamp)) {
			npc = new MoneyEngine(ctx()).npcFromBazaar(new ArrayList<>(BazaarPrices.ids()));
			npcStamp = st;
		}
		return npc;
	}

	/** NPC shop -> Bazaar/AH rows (unsorted). */
	public static synchronized List<MoneyEngine.Row> npcShops() {
		String st = moneyStamp();
		if (!st.equals(shopStamp)) {
			MoneyEngine e = new MoneyEngine(ctx());
			shop = e.npcShops();
			hiddenShops = e.hiddenShops;
			shopStamp = st;
		}
		return shop;
	}

	private static String bookStamp = "", curStamp = "";
	private static List<MoneyEngine.Row> books = List.of(), cur = List.of();
	private static int curUnsellable = 0, curViaAH = 0;

	/** Book combinations (unsorted). */
	public static synchronized List<MoneyEngine.Row> books() {
		String st = moneyStamp();
		if (!st.equals(bookStamp)) {
			books = new MoneyEngine(ctx()).books(new ArrayList<>(BazaarPrices.ids()));
			bookStamp = st;
		}
		return books;
	}

	/** What to buy with the chosen currency (unsorted). */
	public static synchronized List<MoneyEngine.Row> currencies() {
		String st = moneyStamp() + "|" + Settings.curType;
		if (!st.equals(curStamp)) {
			MoneyEngine e = new MoneyEngine(ctx());
			cur = e.currencies(Settings.curType);
			curUnsellable = e.unsellable;
			curViaAH = e.viaAH;
			curStamp = st;
		}
		return cur;
	}

	private static String katStamp = "", bitsStamp = "";
	private static List<MoneyEngine.Row> kat = List.of(), bits = List.of();
	private static int bitsUnsellable = 0, bitsViaAH = 0;

	/** Kat pet upgrades (unsorted). Needs the AH prices. */
	public static synchronized List<MoneyEngine.Row> kat() {
		String st = moneyStamp();
		if (!st.equals(katStamp)) {
			kat = new MoneyEngine(ctx()).kat();
			katStamp = st;
		}
		return kat;
	}

	/** What to buy with bits (unsorted). */
	public static synchronized List<MoneyEngine.Row> bits() {
		String st = moneyStamp();
		if (!st.equals(bitsStamp)) {
			MoneyEngine e = new MoneyEngine(ctx());
			bits = e.currencies("bits");
			bitsUnsellable = e.unsellable;
			bitsViaAH = e.viaAH;
			bitsStamp = st;
		}
		return bits;
	}

	public static synchronized int[] bitsHidden() {
		return new int[] {bitsUnsellable, bitsViaAH};
	}

	/** {items that can't be sold, items hidden because they only sell on the AH} for the last currency list. */
	public static synchronized int[] curHidden() {
		return new int[] {curUnsellable, curViaAH};
	}

	/** How many shop offers the NPCs filter hid. */
	public static synchronized int hiddenShops() {
		return hiddenShops;
	}

	// ---------------------------------------------------------------- the Coin advisor

	private static String planStamp = "";
	private static List<Advisor.Option> plan = List.of();

	/** Everything the advisor depends on. */
	public static String planStampNow() {
		return moneyStamp() + "|" + NpcPrices.version() + "|" + Settings.reqs + "|" + Settings.unlocks + "|" + Settings.compactor + "|"
				+ Settings.planSrc + "|" + Settings.planExSrc + "|" + Settings.planEx;
	}

	public static Advisor.Ctx planCtx() {
		MoneyEngine.Ctx b = ctx();
		MoneyEngine.Ctx m = new MoneyEngine.Ctx(b.prices(), b.npcSell(), b.ahBin(), b.tax(), b.buyOrder(), b.sellOffer(), true, b.npcAvail(),
				b.forgeHotm(), b.coins(), b.slots());
		return new Advisor.Ctx(m, new ArrayList<>(BazaarPrices.ids()), flips(), NpcPrices.version() > 0, CraftEngine.current(),
				Settings.reqs, Settings.compactor, Settings.planSrc, Set.copyOf(Settings.planExSrc), Set.copyOf(Settings.planEx));
	}

	/** Every idea that makes money with your coins and slots (unsorted; empty without coins). */
	public static synchronized List<Advisor.Option> plan() {
		String st = planStampNow();
		if (!st.equals(planStamp)) {
			plan = Settings.coinsBudget() > 0 ? Advisor.options(planCtx()) : List.of();
			planStamp = st;
		}
		return plan;
	}

	public static Advisor.Option planOption(String key) {
		for (Advisor.Option o : plan()) if (o.key.equals(key)) return o;
		return null;
	}

	// ---------------------------------------------------------------- the Flipper

	private static String flipStamp = "";
	private static List<Flipper.Flip> flips = List.of();

	/** Every profitable flip for the current prices and tax (unsorted, unscored). */
	public static synchronized List<Flipper.Flip> flips() {
		String st = BazaarPrices.lastFetchMillis() + "|" + Settings.tax();
		if (!st.equals(flipStamp)) {
			flips = Flipper.compute(new ArrayList<>(BazaarPrices.ids()), BazaarPrices::get, Settings.tax());
			flipStamp = st;
		}
		return flips;
	}

	/** Stamp for a Flipper list: what it depends on, so screens know when to rebuild. */
	public static String flipListStamp(String search) {
		return BazaarPrices.lastFetchMillis() + "|" + Settings.tax() + "|" + CoflData.version() + "|" + Settings.coins + "|" + Settings.slots
				+ "|" + Settings.flipSort + "|" + Settings.hideRisky + "|" + Settings.flipMinScore + "|" + Settings.hideLowVolume + "|" + search + "|" + Format.rounded();
	}

	public static List<Flipper.Listed> flipList(String search) {
		return Flipper.list(flips(), search, Settings.hideLowVolume, Settings.hideRisky, Settings.flipMinScore, Settings.flipSort,
				Settings.coinsBudget(), Settings.freeSlots(), CoflData::move, CoflData::hist);
	}

	public static Flipper.Flip flip(String id) {
		for (Flipper.Flip f : flips()) if (f.id().equals(id)) return f;
		return null;
	}

	/** Stamp for a money list. */
	public static String listStamp(String search, String sort) {
		return moneyStamp() + "|" + search + "|" + sort;
	}

	// ---------------------------------------------------------------- Prices

	/** One bazaar item for the Prices tab (the site's allItems entry). */
	public record PriceItem(String id, String name, BazaarPrices.Price p, double margin, double marginPct, long weekVol, Risk risk) {}

	public static final List<CraftEngine.SortOption> PRICE_SORTS = List.of(
			o("margin", "Margin", "Margin", "Insta-buy minus insta-sell price (the spread), biggest first."),
			o("marginPct", "Margin %", "Margin %", "The spread as a % of the insta-sell price."),
			o("buy", "Buy", "Buy", "Most expensive to insta-buy first."),
			o("sell", "Sell", "Sell", "Most you get insta-selling one first."),
			o("weekVol", "Volume", "Volume", "Most traded in the last 7 days (the lower of insta-bought and insta-sold)."),
			o("name", "Name", "Name", "Alphabetical."));

	public static List<PriceItem> prices(String search) {
		String q = search == null ? "" : search.trim().toLowerCase(java.util.Locale.ROOT);
		List<PriceItem> out = new ArrayList<>();
		for (String id : BazaarPrices.ids()) {
			BazaarPrices.Price p = BazaarPrices.get(id);
			String name = SiteData.prettyName(id);
			long vol = Math.min(p.buyMovingWeek(), p.sellMovingWeek());
			if (!(name.toLowerCase(java.util.Locale.ROOT).contains(q) || id.toLowerCase(java.util.Locale.ROOT).contains(q))) continue;
			if (Settings.hideLowVolume && vol < CraftEngine.LOW_VOLUME) continue;
			double margin = p.quickBuy() - p.quickSell();
			out.add(new PriceItem(id, name, p, margin, p.quickSell() != 0 ? margin / p.quickSell() * 100 : 0, vol, Risk.of(p)));
		}
		java.util.Comparator<PriceItem> c = switch (Settings.pricesSort) {
			case "name" -> java.util.Comparator.comparing(PriceItem::name);
			case "marginPct" -> (a, b) -> Double.compare(b.marginPct(), a.marginPct());
			case "buy" -> (a, b) -> Double.compare(b.p().quickBuy(), a.p().quickBuy());
			case "sell" -> (a, b) -> Double.compare(b.p().quickSell(), a.p().quickSell());
			case "weekVol" -> (a, b) -> Long.compare(b.weekVol(), a.weekVol());
			default -> (a, b) -> Double.compare(b.margin(), a.margin());
		};
		out.sort(c);
		return out;
	}

	// ---------------------------------------------------------------- Minions and Profit / hr

	private static String minionStamp = "", profitStamp = "";
	private static List<MinionEngine.Minion> minions = List.of();
	private static List<MinionEngine.Profit> profits = List.of();

	public static synchronized List<MinionEngine.Minion> minions() {
		String st = BazaarPrices.lastFetchMillis() + "";
		if (!st.equals(minionStamp)) {
			minions = MinionEngine.minions(BazaarPrices::get);
			minionStamp = st;
		}
		return minions;
	}

	public static MinionEngine.PCtx profitCtx() {
		return new MinionEngine.PCtx(BazaarPrices::get, Settings.tax(), Settings.moneySellOffer, Settings.profitSell, Settings.profitTier,
				Settings.profitFuel, Settings.profitUp1, Settings.profitUp2, Settings.profitAuto, new java.util.LinkedHashSet<>(Settings.profitRequire),
				new java.util.LinkedHashSet<>(Settings.profitExclude), Settings.profitHopper);
	}

	public static String profitStampNow() {
		return BazaarPrices.lastFetchMillis() + "|" + Settings.tax() + "|" + Settings.moneySellOffer + "|" + Settings.profitSell + "|" + Settings.profitTier
				+ "|" + Settings.profitFuel + "|" + Settings.profitUp1 + "|" + Settings.profitUp2 + "|" + Settings.profitAuto + "|" + Settings.profitRequire
				+ "|" + Settings.profitExclude + "|" + Settings.profitHopper;
	}

	public static synchronized List<MinionEngine.Profit> profits() {
		String st = profitStampNow();
		if (!st.equals(profitStamp)) {
			profits = MinionEngine.profits(profitCtx(), minions());
			profitStamp = st;
		}
		return profits;
	}

	public static int chestSlots() {
		for (String[] c : MinionEngine.CHESTS) if (c[0].equals(Settings.profitChest)) return Integer.parseInt(c[2]);
		return 0;
	}

	// ---------------------------------------------------------------- Essence

	private static String essStamp = "";
	private static List<EssenceEngine.Ess> ess = List.of();

	public static String essenceStampNow() {
		return BazaarPrices.lastFetchMillis() + "|" + AhPrices.version() + "|" + NpcPrices.version() + "|" + CoflData.version() + "|" + Settings.tax()
				+ "|" + Settings.essenceSellOffer + "|" + Settings.bazaarOnly;
	}

	public static synchronized List<EssenceEngine.Ess> essence() {
		String st = essenceStampNow();
		if (!st.equals(essStamp)) {
			ess = EssenceEngine.scan(new EssenceEngine.ECtx(BazaarPrices::get, AhPrices::get, Settings.tax(), Settings.essenceSellOffer, Settings.bazaarOnly),
					NpcPrices.salvage(), CoflData::bin);
			essStamp = st;
		}
		return ess;
	}

	public static EssenceEngine.Ess essence(String id) {
		for (EssenceEngine.Ess e : essence()) if (e.id().equals(id)) return e;
		return null;
	}

	/** Testing hook: forget the cached lists. */
	static synchronized void reset() {
		forgeStamp = npcStamp = shopStamp = flipStamp = bookStamp = curStamp = minionStamp = profitStamp = essStamp = "";
	}
}
