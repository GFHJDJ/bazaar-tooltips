package com.unc.bazaartips;

import java.util.ArrayList;
import java.util.List;

/** The website's NPC flips and Forge tabs. */
public class MoneyScreen extends TableScreen<MoneyEngine.Row> {
	/** "forge", "books", "cur", "kat", "bits", "npc" (Bazaar -> NPC) or "shop" (NPC shop -> Bazaar/AH), from the tab and its View setting. */
	private String kind() {
		return switch (tab) { case "forge", "books", "cur", "kat", "bits" -> tab; default -> Settings.npcView.equals("shop") ? "shop" : "npc"; };
	}

	public MoneyScreen(String tab) {
		super(switch (tab) { case "forge" -> "Forge"; case "books" -> "Books"; case "cur" -> "Currencies"; case "kat" -> "Kat"; case "bits" -> "Bits";
			default -> "NPC flips"; }, tab);
	}

	/** The currency's names {plural title, one, many}: bits on the Bits tab, the chosen currency on Currencies. */
	private String[] names() {
		return MoneyEngine.CURRENCIES.getOrDefault(kind().equals("bits") ? "bits" : Settings.curType, MoneyEngine.CURRENCIES.get("copper"));
	}

	/** The Currency tab and the Bits tab are the same list (bits are just one more currency). */
	private boolean curLike() {
		return kind().equals("cur") || kind().equals("bits");
	}

	@Override
	protected void init() {
		AhPrices.ensure();
		super.init();
	}

	private static boolean sized() {
		return Settings.coinsBudget() > 0 || Settings.freeSlots() > 0;
	}

	// ---- settings buttons ----

	private Control buyControl() {
		return new Control(() -> "Buy: " + (Settings.moneyBuyOrder ? "Buy order" : "Insta-buy"),
				() -> "Buy: how " + (kind().equals("npc") ? "you buy on the Bazaar" : "materials are bought") + ". Insta-buy = now, at Hypixel's "
						+ "averaged sell-offer price (big amounts walk the order book). Buy order = at the averaged buy-order price: cheaper, but you wait.",
				d -> Settings.moneyBuyOrder = !Settings.moneyBuyOrder);
	}

	private Control sellControl() {
		return new Control(() -> "Sell: " + (Settings.moneySellOffer ? "Sell offer" : "Insta-sell"),
				() -> "Sell: how the result sells on the Bazaar, after tax. Insta-sell = now, into buy orders (big amounts walk the order book). "
						+ "Sell offer = list it at the averaged sell-offer price and wait.",
				d -> { Settings.moneySellOffer = !Settings.moneySellOffer; MoneyData.syncSortFromSell(); });
	}

	private Control bazaarOnlyControl() {
		return new Control(() -> "Bazaar only: " + onOff(Settings.bazaarOnly),
				() -> "Bazaar sales only (" + onOff(Settings.bazaarOnly) + "): hides results you'd have to sell on the Auction House. "
						+ "Off: those are valued at the AH lowest BIN (before AH fees), and listings may take a while to sell.",
				d -> Settings.bazaarOnly = !Settings.bazaarOnly);
	}

	@Override
	protected List<Control> controls() {
		List<Control> c = new ArrayList<>();
		String kind = kind();
		if (kind.equals("cur")) {
			c.add(new Control(() -> "Currency: " + names()[0].replace(" (bought with real money)", ""),
					() -> "Currency: " + names()[0] + ". What you're spending. Click to step through copper, medals, pelts, North Stars, pests, "
							+ "Rusty Coins, Bingo Points, kernels, Carnival Tokens and Gems.",
					d -> {
						List<String> keys = new ArrayList<>(MoneyEngine.CURRENCIES.keySet());
						keys.remove("bits");                                 // bits have their own tab
						Settings.curType = Cycle.step(keys, Settings.curType, d);
						if (!List.of("per", "value", "amt", "name").contains(Settings.curSort)) Settings.curSort = "per";
					}));
		} else if (kind.equals("books") || kind.equals("kat") || kind.equals("bits")) {
			// no extra button: these only need Buy, Sell, Sort and Tax (Kat: Buy and Sort)
		} else if (kind.equals("forge")) {
			c.add(new Control(() -> "HotM: " + (Settings.forgeHotm == 0 ? "any" : Integer.toString(Settings.forgeHotm)),
					() -> "Your Heart of the Mountain tier (" + (Settings.forgeHotm == 0 ? "any" : Settings.forgeHotm) + "): hides recipes that need a "
							+ "higher tier and sets your forge slots (" + MoneyEngine.forgeSlots(Settings.forgeHotm) + "). Click to step 1-10, then any.",
					d -> Settings.forgeHotm = Cycle.step(Settings.forgeHotm, 0, 10, d)));
		} else {
			c.add(new Control(() -> kind().equals("shop") ? "View: NPC shop" : "View: Bazaar>NPC",
					() -> "View: " + (kind().equals("shop") ? "NPC shop -> Bazaar/AH: buy from an NPC shop, sell on the Bazaar or AH."
							: "Bazaar -> NPC: buy on the Bazaar, sell to an NPC.") + " Click to switch.",
					d -> {
						Settings.npcView = Settings.npcView.equals("shop") ? "bz" : "shop";
						Settings.save();
						minecraft.gui.setScreen(new MoneyScreen(tab)); // the buttons change with the view
					}));
		}
		c.add(buyControl());
		if (!kind.equals("npc") && !kind.equals("kat")) {             // pets only sell on the AH
			c.add(sellControl());
			if (!kind.equals("books")) c.add(bazaarOnlyControl());
		}
		if (kind.equals("shop")) {
			c.add(new Control(() -> "NPCs: " + switch (Settings.npcAvail) { case "all" -> "All"; case "nomayor" -> "+situational"; default -> "Always there"; },
					() -> "NPCs: " + switch (Settings.npcAvail) {
						case "all" -> "All: includes seasonal (event), mayor-only and situational NPCs.";
						case "nomayor" -> "Hides seasonal (event) and mayor-only NPCs; shows situational ones (only in some places or moments).";
						default -> "Always there: hides seasonal (event), mayor-only and situational NPCs.";
					} + " From the Hypixel SkyBlock Wiki. Click to change.",
					d -> Settings.npcAvail = Cycle.step(List.of("always", "nomayor", "all"), Settings.npcAvail, d)));
		}
		c.add(sortControl());
		if (!kind.equals("kat")) c.add(taxControl());                 // Kat buys and sells on the AH: no bazaar tax
		return c;
	}

	private List<CraftEngine.SortOption> sorts() {
		return switch (kind()) {
			case "forge" -> MoneyData.FORGE_SORTS;
			case "shop" -> MoneyData.SHOP_SORTS;
			case "books" -> MoneyData.BOOK_SORTS;
			case "cur" -> MoneyData.curSorts();
			case "bits" -> MoneyData.curSorts("bits");
			case "kat" -> MoneyData.KAT_SORTS;
			default -> MoneyData.NPC_SORTS;
		};
	}

	private String sortKey() {
		return switch (kind()) {
			case "forge" -> Settings.forgeSort;
			case "shop" -> Settings.npcShopSort;
			case "books" -> Settings.bookSort;
			case "cur" -> Settings.curSort;
			case "bits" -> Settings.bitsSort;
			case "kat" -> Settings.katSort;
			default -> Settings.npcSort;
		};
	}

	private void setSort(String k) {
		switch (kind()) {
			case "forge" -> Settings.forgeSort = k;
			case "shop" -> Settings.npcShopSort = k;
			case "books" -> Settings.bookSort = k;
			case "cur" -> Settings.curSort = k;
			case "bits" -> Settings.bitsSort = k;
			case "kat" -> Settings.katSort = k;
			default -> Settings.npcSort = k;
		}
		MoneyData.syncSellFromSort(k);
	}

	private Control sortControl() {
		return sortControl(switch (kind()) { case "forge" -> "Sort forge recipes by"; case "books" -> "Sort book combinations by";
			case "cur", "bits" -> "Sort what to buy by"; case "kat" -> "Sort Kat upgrades by"; default -> "Sort NPC flips by"; }, sorts(), this::sortKey, this::setSort);
	}

	// ---- the table ----

	private static String bestCell(MoneyEngine.Row r) {
		if (r.best == null) return "-";
		if (r.best.n() > 0 && r.best.profit() > 0) return Format.signedShortCoins(r.best.profit());
		return r.best.n() > 0 ? "loses" : "none";
	}

	@Override
	protected List<Column<MoneyEngine.Row>> columns() {
		List<Column<MoneyEngine.Row>> cols = new ArrayList<>();
		switch (kind()) {
			case "forge" -> {
				cols.add(new Column<>("Cost", "Cost: what the materials (and coins) for one forge cost, bought the way the Buy button says.",
						r -> Format.shortCoins(r.costEach), r -> Colors.YELLOW));
				cols.add(new Column<>("Profit", "Profit: what one forge's result sells for (after tax), minus the cost.",
						r -> Format.signedShortCoins(r.profit), r -> Colors.profit(r.profit)));
				cols.add(new Column<>("Per hr", "Profit / hr: profit per forge divided by the forge time.",
						r -> Format.signedShortCoins(r.perHour), r -> Colors.profit(r.perHour)));
				cols.add(new Column<>("Time", "Time: how long one forge takes (Quick Forge and other perks not counted).",
						r -> r.flag, r -> Colors.GRAY));
			}
			case "books" -> {
				cols.add(new Column<>("Cost", "Cost: what two level-N books cost, bought the way the Buy button says.",
						r -> Format.shortCoins(r.costEach), r -> Colors.YELLOW));
				cols.add(new Column<>("Sells for", "Sells for: what the level N+1 book sells for, the way the Sell button says, after tax.",
						r -> Format.shortCoins(r.value), r -> Colors.YELLOW));
				cols.add(new Column<>("Profit", "Profit: sells for minus cost, per combine.",
						r -> Format.signedShortCoins(r.profit), r -> Colors.profit(r.profit)));
				cols.add(new Column<>("Return", "Return: profit as a % of what the two books cost.", r -> MoneyEngine.pctStr(r.pct), r -> Colors.GRAY));
				cols.add(new Column<>("Vol/wk", "Vol/wk: the slower of level N bought per week and level N+1 sold per week.",
						r -> Format.amount((long) r.vol), r -> r.vol >= CraftEngine.LOW_VOLUME ? Colors.GRAY : Colors.RED));
			}
			case "kat" -> {
				cols.add(new Column<>("Cost", "Cost: the pet's lowest BIN on the AH, plus Kat's coins and the materials (bought the way the Buy button says).",
						r -> Format.shortCoins(r.costEach), r -> Colors.YELLOW));
				cols.add(new Column<>("Sells for", "Sells for: the upgraded pet's lowest BIN on the AH, before AH fees.",
						r -> Format.shortCoins(r.value), r -> Colors.YELLOW));
				cols.add(new Column<>("Profit", "Profit: sells for minus cost.", r -> Format.signedShortCoins(r.profit), r -> Colors.profit(r.profit)));
				cols.add(new Column<>("Per day", "Profit / day: profit divided by how long Kat takes.",
						r -> Format.signedShortCoins(r.perHour * 24), r -> Colors.profit(r.perHour)));
				cols.add(new Column<>("Time", "Time: how long Kat takes (Kat Flowers and Bouquets that skip time aren't counted).", r -> r.flag, r -> Colors.GRAY));
			}
			case "cur", "bits" -> {
				String one = names()[1];
				cols.add(new Column<>("Costs", "Costs: how many " + names()[2] + " it takes.", r -> Format.count(r.amt), r -> Colors.YELLOW));
				cols.add(new Column<>("Sells for", "Sells for: what one purchase sells for minus any extra coins/items it costs (Bazaar after tax; AH = lowest BIN before fees).",
						r -> Format.signedShortCoins(r.value), r -> Colors.profit(r.value)));
				cols.add(new Column<>("Per " + one, "Coins per " + one + ": the value divided by how many " + names()[2] + " it costs. Higher is better.",
						r -> Format.shortCoins(r.per), r -> Colors.profit(r.per)));
			}
			case "shop" -> {
				cols.add(new Column<>("NPC cost", "NPC cost: what the NPC charges for one purchase (coins plus any materials).",
						r -> Format.shortCoins(r.costEach), r -> Colors.YELLOW));
				cols.add(new Column<>("Sells for", "Sells for: what one purchase sells for, the way the Sell button says (Bazaar after tax; AH = lowest BIN before fees).",
						r -> Format.shortCoins(r.value), r -> Colors.YELLOW));
				cols.add(new Column<>("Profit", "Profit: sells for minus NPC cost, for one purchase.",
						r -> Format.signedShortCoins(r.profit), r -> Colors.profit(r.profit)));
				cols.add(new Column<>("Return", "Return: profit as a % of the NPC cost.", r -> MoneyEngine.pctStr(r.pct), r -> Colors.GRAY));
				cols.add(new Column<>("Max/day", "Max / day: the most items you can buy from NPC shops a day (640 for most items; resets at midnight GMT). "
						+ "Best amounts never go over it.", r -> Format.count(r.maxDay), r -> Colors.GRAY));
			}
			default -> {
				cols.add(new Column<>("Buy for", "Buy for: what one costs on the Bazaar, the way the Buy button says.",
						r -> Format.price(r.costEach), r -> Colors.YELLOW));
				cols.add(new Column<>("NPC pays", "NPC pays: what any NPC pays for one (from Hypixel's item list). No tax.",
						r -> Format.price(r.costEach + r.profit), r -> Colors.YELLOW));
				cols.add(new Column<>("Profit", "Profit each: NPC pays minus the Bazaar price.",
						r -> Format.signedShortCoins(r.profit), r -> Colors.profit(r.profit)));
				cols.add(new Column<>("Return", "Return: profit each as a % of the Bazaar price.", r -> MoneyEngine.pctStr(r.pct), r -> Colors.GRAY));
				cols.add(new Column<>("For sale", "For sale: items listed in sell offers right now (how many you could buy at once).",
						r -> Format.amount((long) r.vol), r -> Colors.GRAY));
				cols.add(new Column<>("Max/day", "Max / day: the most of it NPCs will buy from you a day: they pay at most 500M coins a day per "
						+ "profile. Best amounts never go over it.", r -> Format.amount(r.maxDay), r -> Colors.GRAY));
			}
		}
		if (sized() && !curLike() && !kind().equals("kat")) {
			cols.add(cols.size() > 4 ? 4 : cols.size(), new Column<>("Best", "Best: profit from the best amount per trip for your coins and free slots, "
					+ "walking the live order book. Hover a row for the amount.", MoneyScreen::bestCell,
					r -> r.best != null && r.best.n() > 0 && r.best.profit() > 0 ? Colors.GREEN : Colors.DARK_GRAY));
		}
		return cols;
	}

	@Override
	protected String stamp(String search) {
		return kind() + "|" + MoneyData.listStamp(search, sortKey());
	}

	@Override
	protected List<MoneyEngine.Row> build(String search) {
		List<MoneyEngine.Row> all = switch (kind()) {
			case "forge" -> MoneyData.forge();
			case "shop" -> MoneyData.npcShops();
			case "books" -> MoneyData.books();
			case "cur" -> MoneyData.currencies();
			case "bits" -> MoneyData.bits();
			case "kat" -> MoneyData.kat();
			default -> MoneyData.npcFromBazaar();
		};
		return MoneyEngine.sort(all, sortKey(), search);
	}

	@Override
	protected String waiting() {
		String w = super.waiting();
		if (w != null) return w;
		if (kind().equals("npc") && NpcPrices.version() == 0) return "Loading NPC sell prices from Hypixel's item list...";
		if (kind().equals("kat") && !AhPrices.loaded()) return "Loading auction house prices...";
		return null;
	}

	@Override
	protected String name(MoneyEngine.Row r) {
		return r.title;
	}

	@Override
	protected int nameColor(MoneyEngine.Row r) {
		return r.slowWeek >= 0 ? Colors.ORANGE : Colors.WHITE;
	}

	@Override
	protected String tooltip(MoneyEngine.Row r) {
		String best = MoneyReport.bestText(r);
		String first = switch (kind()) {
			case "forge" -> "Takes " + r.flag + (r.req.isEmpty() ? "" : " · requires " + r.req);
			case "books" -> String.join(" · ", r.meta);
			case "cur", "bits", "kat" -> String.join(" · ", r.meta);
			case "shop" -> r.meta.isEmpty() ? "" : r.meta.get(0);
			default -> r.meta.isEmpty() ? "" : String.join(" · ", r.meta);
		};
		String slow = r.slowWeek >= 0 ? "Slow seller: only " + Format.count(r.slowWeek) + " trade on the Bazaar a week · " : "";
		return slow + first + (best != null ? " · " + best : "");
	}

	@Override
	protected void open(MoneyEngine.Row r) {
		minecraft.gui.setScreen(new MoneyDetailScreen(this, kind(), r.key));
	}

	@Override
	protected String itemExplanation() {
		return switch (kind()) {
			case "forge" -> "What you forge (x how many one forge makes). Click a row for the materials, a how-many calculator and more.";
			case "books" -> "Two books of one level -> one of the next level. Click a row for the calculator and the live order book.";
			case "cur", "bits" -> "What you buy with " + names()[2] + " (x how many per purchase). Hover for the NPC; click for every number.";
			case "kat" -> "The pet you buy -> the rarity Kat upgrades it to. Hover for the prices; click for every number.";
			case "shop" -> "What you buy from the NPC (x how many per purchase). Hover for the NPC and where they are; click for the calculator.";
			default -> "What you buy on the Bazaar to sell to an NPC. Click a row for the calculator and the live order book.";
		};
	}

	@Override
	protected String noun() {
		return switch (kind()) {
			case "forge" -> "forge recipes";
			case "books" -> "book combinations";
			case "cur", "bits" -> "things to buy";
			case "kat" -> "Kat upgrades";
			case "shop" -> "shop offers";
			default -> "items";
		};
	}

	@Override
	protected String hint() {
		return switch (kind()) {
			case "forge" -> MoneyEngine.forgeSlots(Settings.forgeHotm) + " forge slots" + (Settings.forgeHotm == 0 ? " (set your HotM for more)" : "")
					+ (Settings.bazaarOnly ? "" : (AhPrices.loaded() ? "" : " · AH prices loading")) + " · recipes and times from NEU";
			case "shop" -> {
				int h = MoneyData.hiddenShops();
				yield (h > 0 ? h + " offers from " + (Settings.npcAvail.equals("always") ? "seasonal, mayor & situational" : "seasonal & mayor")
						+ " NPCs hidden (NPCs button)" : "Shop offers from NEU") + (!Settings.bazaarOnly && !AhPrices.loaded() ? " · AH prices loading" : "")
						+ " · locations: SkyHanni & the wiki";
			}
			case "books" -> "Two level-N books make one level N+1 in an anvil · enchant limits from NEU · Ultimate enchants left out";
			case "kat" -> (AhPrices.loaded() ? "" : "AH prices loading · ") + "Buy on the AH, upgrade at Kat, sell on the AH · pet prices are lowest BIN, before AH fees · upgrades from NEU";
			case "bits" -> {
				int[] h = MoneyData.bitsHidden();
				BazaarPrices.Price cookie = BazaarPrices.get("BOOSTER_COOKIE");
				String c = cookie == null || !(cookie.instaBuy() > 0) ? ""
						: " · a Booster Cookie (" + Format.shortCoins(cookie.instaBuy()) + ") gives 4,800 bits: " + Format.price(cookie.instaBuy() / 4800) + " coins per bit";
				yield "Ranked by coins per bit" + c + (h[0] > 0 ? " · " + h[0] + " can't be sold" : "")
						+ (h[1] > 0 ? " · " + h[1] + " AH-only hidden (Bazaar only)" : "");
			}
			case "cur" -> {
				int[] h = MoneyData.curHidden();
				yield "Ranked by coins per " + names()[1] + (h[0] > 0 ? " · " + h[0] + " can't be sold" : "")
						+ (h[1] > 0 ? " · " + h[1] + " AH-only hidden (Bazaar only)" : "") + " · shop offers from NEU";
			}
			default -> "NPC prices from Hypixel's item list · selling to NPCs has no tax · these gaps close fast";
		};
	}

	@Override
	protected String emptyText() {
		return switch (kind()) {
			case "forge" -> "No forge recipes match.";
			case "books" -> "No book combinations make a profit right now.";
			case "cur", "bits" -> "Nothing bought with " + names()[2] + " has a market price right now.";
			case "kat" -> "No Kat upgrades have both pets listed on the AH right now.";
			case "shop" -> "No NPC shop items sell for more than they cost right now.";
			default -> "Nothing on the Bazaar is cheaper than an NPC pays right now.";
		};
	}

	@Override
	protected String sortKeyFor(String header) {
		String best = sortKey().startsWith("coins") ? sortKey() : "coins";
		return switch (kind()) {
			case "forge" -> switch (header) { case "Profit" -> "profit"; case "Per hr" -> "perHour"; case "Time" -> "time"; case "Best" -> best; default -> null; };
			case "shop" -> switch (header) { case "Profit" -> "profit"; case "Return" -> "pct"; case "Best" -> best; default -> null; };
			case "books" -> switch (header) { case "Profit" -> "profit"; case "Return" -> "pct"; case "Vol/wk" -> "vol"; case "Best" -> best; default -> null; };
			case "cur", "bits" -> header.startsWith("Per ") ? "per" : switch (header) { case "Costs" -> "amt"; case "Sells for" -> "value"; default -> null; };
			case "kat" -> switch (header) { case "Profit" -> "profit"; case "Per day" -> "perHour"; case "Time" -> "time"; default -> null; };
			default -> switch (header) { case "Profit" -> "profit"; case "Return" -> "pct"; case "For sale" -> "vol"; case "Best" -> "coins"; default -> null; };
		};
	}

	@Override
	protected String currentSort() {
		return sortKey();
	}

	@Override
	protected void applySort(String key) {
		setSort(key);
	}
}
