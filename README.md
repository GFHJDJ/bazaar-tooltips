# Bazaar Tooltips

A Fabric mod for Minecraft 26.2 that adds live Hypixel Skyblock bazaar prices to item tooltips.

```
Bazaar price for 1:
  Insta-buy: 1,300 (what you pay to buy one now)
  Insta-sell: 1,250 (what you get selling one now)
Bazaar price for all 64: Insta-buy 83,200 | Insta-sell 80,000
Traded last 7 days: 1.2M insta-bought, 980k insta-sold
NPC pays: 1,280 each | 81,920 for all 64
Craft it (1 craft makes 9):
  Needs 160x Block of Diamond · max 72.2
  Insta-buying ingredients: costs 1,244.4 per item
    Profit per item: +39.3 by sell offer | -10.1 by insta-sell
  Ingredients by buy order: costs 1,155.6 per item
    Profit per item: +128.2 by sell offer | +78.8 by insta-sell
```

- **Insta-buy / Insta-sell**: the price to buy one right now (cheapest sell offer) and what you get
  selling one right now (highest buy order).
- **Bazaar price for all 64** shows up for stacks: the whole stack's value.
- **Traded last 7 days**: how many were insta-bought and insta-sold this week (how easy it is to trade).
- Enchanted books with one enchant are priced too (e.g. Ultimate Wise V).
- **NPC pays**: what an NPC gives you, for one and for the stack. Shows on any Skyblock item NPCs buy,
  even ones not on the bazaar (from Hypixel's item list, refreshed every 6 hours).
- **Craft it**: the cheapest recipe right now, what the ingredients cost per item you make (two ways:
  insta-buying them, or with buy orders, which is cheaper but you wait), and the profit per item if you
  sell by sell offer or insta-sell, after bazaar tax (1.25% unless you change it on the B screen).
  Only shows when the item and every ingredient are on the bazaar. **max** after each ingredient is the most you could
  pay for one (the rest at today's prices) and not lose coins selling by sell offer: green = today's price is under it.
  Hovering a craft on the Crafts tab lists the same for every ingredient.
- Prices refresh every 60 seconds. A red "Prices are 5m old" line shows if updates stop.

## The bazaar screen (press B)

Press **B** in game. To change the key, click the gear (⚙) next to Rounded, then the key button (or Options > Controls > Key Binds > Bazaar Tooltips). It opens on the tab you
used last (or one you pick in the gear). Tabs along the top (they wrap onto two rows; hide the ones you don't use in the gear): **Crafts**, **Flipper**, **Advisor**, **NPC**, **Forge**, **Prices**, **Books**,
**Minions**, **Profit/hr**, **Essence**, **Currency**, **Kat**, **Bits**, **Shards**, **AH**, **Alerts**, **Earnings**, **Orders** (most are ports of your website's tabs:
same data, same math, checked against the site's own script number for number). Your coins, free slots,
tax and Rounded setting are shared by every tab.

Nothing is squeezed: each tab is one page you scroll with the mouse wheel. Every item gets two lines (its
full name, then its numbers), and columns that don't fit side by side go on another line instead of being
hidden. Once you scroll past the buttons, the column names stay pinned at the top. Click a column name (the orange
words) to sort by it; the one you're sorted by is underlined. "Item" sorts by name. The details screens
wrap long text onto extra lines instead of cutting it off.

**Mix how you buy:** at the top of a Crafts, Forge or NPC details screen, "How you buy each item" lists
every item you'd buy. Click one to switch it between Insta-buy and Buy order; they start at the list's
Buy setting, and every number on that screen (cost, profit, best amount, shopping list, calculator)
uses your mix. Reset puts them all back. Your choices last until you close the game.

## Crafts tab (your website's Craft tabs)

- **Search / Coins / Slots** boxes. Coins take "70m", "250k", "1.2b" or "3,500,000". With coins and/or slots
  set, a **Best** column appears and every craft gets a best amount and a shopping list.
- **Buy:** Insta-buy or Buy order (the site's two Craft tabs). Buy orders are placed 0.1 above the top
  buy order; sell offers 0.1 below the lowest offer; insta-buy / insta-sell use the top of the order book.
- **Sort:** all 12 of the site's sorts (sell-order / insta-sell profit and margin, profit per slot,
  best for your coins & slots, craft cost, requirement, name).
- **Filters & tax:** low volume (under 10,000/week), craft chains (Off / On / Only chains: price an
  ingredient as the cheaper of buying it or crafting it from its own parts), Personal Compactor
  (show all / only / hide; the Super Compactor 3000 list plus Flawed, Fine and Flawless gemstones), requirements (all / no requirement / I've unlocked), **My unlocks** (loads
  your collections, slayers and dungeon bosses through the site's lookup server), and bazaar tax
  (1.25%, 1.125%, 1%, or custom).
- Names in orange or red with **!** have a manipulation warning (very wide spread, very few orders,
  or one order controlling the book), on the item or one of its ingredients.
- **Rounded: On / Off** (top-right): short numbers (162.8k) or every digit (162,844.8), everywhere.
- **Hover anything** (a column name, a box, a button) to see what it means. **Click a row** for details.
- **Buttons that step through options** (Sort aside, which opens a list): left click goes to the next one, **right
  click goes back** to the one before. Works on every tab, the gear's settings and Filters & tax.
- Your settings are saved in `config/bazaartips.json`, so they're still there next time.

### Details screen (the site's open craft card)

Back, **/bz** (opens the bazaar search for the item), Rounded, and a **Craft amount** box with - / +.
Scroll through:

- Requirements (red if your loaded unlocks say you don't have them), chained craft, Personal Compactor,
  break-even sell price, "insta-sell wins", and any manipulation warnings.
- Craft cost, profit by sell offer and by insta-sell.
- **Ingredients:** amount, price each (and whether it's crafted), and **max** (the most you could pay each
  and still profit).
- **Plan for your amount:** items made, material slots, every ingredient down to the bottom of its
  chain, sell prices, total cost and profit.
- **Insta-flip depth:** how many crafts you could insta-buy and insta-sell before profit runs out.
- **Buy-order depth:** how many crafts you could make buying every ingredient with buy orders (the price
  doesn't climb as you buy) and insta-selling the result before profit runs out, the total profit, and
  roughly how long the buy orders take to fill (the slowest ingredient decides).
- **For your coins and slots:** budget, the **best amount** by sell offer and by insta-sell (walking the
  live order book), why it stops there, a full **shopping list** (how many of each item to buy and at what
  price, what you'd sell for, and the profit), and how many crafts fit in your slots.
- **The item's market:** insta-buy, insta-sell, spread, margin, open sell offers and buy orders, items in
  them, and how many were insta-bought / insta-sold this week.

Every line explains itself when you hover it.

## Flipper tab

Buy with a buy order 0.1 above the top buy order, sell with a sell offer 0.1 below the lowest sell offer,
keep the gap after tax.

- Columns: **Score** (0-100 and a grade), profit **Each**, **Margin**, profit **Per hr** (if you caught every
  trade on the slower side), **Trades/hr**, and **Best** (with coins/slots set).
- Buttons: **Sort** (flip score, profit / hr, best for your coins & slots, profit each, margin, trades / hr,
  name), **Risky** (hide items that look manipulated, on by default), **Low vol**, **Min score** (type 0–100; flips scoring lower are hidden, empty = show all), **Tax**.
- **Flip score**: margin, volume, both sides filling evenly, competition, today's price move and price
  history, minus points for warning signs. Price history and 24h moves are provided by SkyCofl
  (sky.coflnet.com), fetched politely (about one item a second, only what's on screen, kept 6 hours in
  `config/bazaartips-history.json`). `*` = history not loaded yet.
- Details: the score breakdown part by part, a plan (how many to flip for your coins and slots, cost,
  what you get, profit, what limited it), how fast each side fills, market stats, warnings, and the live
  order book level by level.

## Advisor tab (the site's Coin advisor)

Type your coins (and free slots, if you like) and it sizes every flip, Bazaar -> NPC flip, NPC shop, forge,
book combine and craft to them, then ranks them: **Kind**, **Best amount**, **Uses**, **Profit**, **Return**, **Time**,
**Per hr**. At the top, a **suggested split**: the most profitable idea first, then the next best with the
coins left over (up to 3; click one for its details). Bazaar sales only; risky and low-volume items are left out.

- Buttons: **Sort**, **Show** (everything or one kind), **Buy** / **Sell** (shared with NPC, Forge and Books),
  **Leave out** (whole kinds, or items you left out), **HotM**, **NPCs**, **Compactor**, **Tax**.
- Click an idea for its best amount, what to buy and sell for it, a link to its own tab, and
  **Leave this item out of the advisor**.

## NPC tab

- **View: Bazaar>NPC**: buy on the Bazaar, sell to an NPC. Columns: buy for, NPC pays, profit, return,
  how many are for sale, Best.
- **View: NPC shop**: buy from an NPC shop, sell on the Bazaar (or the AH with **Bazaar only: Off**).
  Hover a row for the NPC and where they are (x, y, z). **NPCs** button: always there / + situational /
  all (seasonal and mayor-only NPCs too).
- **Buy** (insta-buy / buy order) and **Sell** (insta-sell / sell offer). Insta-buys and insta-sells of
  bigger amounts walk the live order book, like the site.
- Details have the site's **how many?** calculator: - / + / type it, presets (64, 640, 2,240), **Best** (most
  profitable for your coins and slots) and **Max** (as many as they allow), with cost, what you get,
  inventory space and profit.

**Hypixel's NPC limits are built in**: NPCs pay at most 500M coins a day per profile, and NPC shops sell at most 640 of
an item a day (resets at midnight GMT). Both views have a **Max/day** column, Best amounts (and the Advisor) never go
over the limit, and the calculator warns when an amount would take more than a day.

## Forge tab

- Every forge recipe that makes money: cost, profit, profit / hr, forge time, Best. Best is sized by your coins and forge slots only
  (free inventory slots don't count here, since forge materials usually go in sacks).
- **HotM**: your Heart of the Mountain tier (hides recipes you can't forge, sets your forge slots).
- **Buy**, **Sell**, **Bazaar only**, **Sort** (best for your coins & slots, profit per forge, profit / hr,
  return %, fastest, name), **Tax**.
- Details: materials table, notes, and the how-many calculator (presets 1 and your forge slots).

## Prices tab

Every bazaar item: insta-buy and insta-sell price, margin, margin % and weekly volume (sortable, low
volume can be hidden). Click one for its full stats, manipulation warnings and the live order book.

An item's details also show a **30-day price chart** (one bar a day, the middle of insta-buy and insta-sell, from
SkyCofl; today in gold), its 30-day typical, low and high, how today compares, and its all-time range. A
**Set a price alert** line takes you to the Alerts tab with the item filled in.

## Alerts tab (price alerts)

Type an item (part of its name is enough) and a price (29000, 29k, 1.2m), pick **Insta-buy below / above** or
**Insta-sell below / above**, and click **Add**. Every time prices refresh (about once a minute), an alert whose price
gets there sends one chat message with a ding and a [/bz] link; it messages again only after the price has gone back
the other way. Click an alert to delete it. Alerts are saved in `config/bazaartips-alerts.json`.

## Books tab

Two level-N enchanted books make one level N+1 in an anvil. Lists the combinations that make money (up to
the enchanting table's max level; Ultimate enchants left out), with Buy / Sell / Sort / Tax, a Best amount,
the how-many calculator (presets 1, 8, 32) and the higher book's order book.

## Minions tab

What each minion costs to build to its top tier, insta-buying or with buy orders. Click one for every
tier's materials and running total, and the full shopping list to reach the top tier.

## Profit / hr tab

Coins per hour from each minion's drops. Buttons: **Tier**, **Sell to** (best / Bazaar / NPC), how the
Bazaar sells, **Auto** (picks the best fuel and two upgrades per minion; then **Must / never use** lets you
force or ban upgrades and fuels), or your own **Fuel** / **Up 1** / **Up 2**, **Hopper**, **Chest**, Sort,
Tax. Columns: per hour, per day, payback time and how long until it's full. Details list every drop, how
often to empty it with each storage chest, and payback.

## Essence tab

Buy the cheapest AH listing, salvage it (or sacrifice it for Dragon Essence), sell what comes out. What
each item turns into comes from Hypixel's item list. Listings on screen are checked live with SkyCofl
(a lone very cheap listing that's probably a mistake uses the next one). Details have an **Open the
cheapest auction** line that runs /viewauction for you.

## Currency tab

What to buy with copper, medals, pelts, North Stars, pests, Rusty Coins, Bingo Points, kernels,
Carnival Tokens or Gems, ranked by coins per one. The **Currency** button switches between them.

## Slow sellers

On the Forge, NPC shop, Books, Currency and Bits lists, a result that trades fewer than 100 times a week on the Bazaar
has an orange name, and its hover and details say "Slow seller: only N trade on the Bazaar a week". Selling more than
a couple can take a long time, and insta-selling several walks down to worse buy orders.

## Kat tab

Buy a pet on the AH, have Kat upgrade its rarity, sell it on the AH. Each upgrade shows what it costs (the pet's lowest
BIN, plus Kat's coins and materials), what the upgraded pet sells for, the profit, profit per day of Kat time, and how
long Kat takes. **Buy** sets how the materials are bought. Upgrade costs and times are from NEU (the site's data).
Pet prices are lowest BINs (usually low-level pets, before AH fees), so check matching listings before you buy.

## Bits tab

What to buy with bits at Elizabeth, ranked by coins per bit (what it sells for, minus any extra cost, divided by its
bits price). Same buttons as the Currency tab: Buy, Sell, Bazaar only (hides things that only sell on the AH), Sort,
Tax. The bottom line shows what a Booster Cookie costs and what that makes each of its 4,800 bits cost you.

## Shards tab

Fuse two shards into another and sell it on the Bazaar. For every shard you can fuse, the cheapest pair to make it
right now (each fusion uses 5 of most shards, 2 of Reptile, Amphibian and Elemental ones, 1 of Chameleon; some make
2), what the inputs cost, what the result sells for after tax, the profit, return and weekly volume. Names are
coloured by rarity and read "Result <- 5x A + 5x B"; hover for the prices and the next cheapest pairs, click for the
result's Bazaar prices, chart and alerts. **Buy**, **Sell**, **Risky**, **Profitable** (hide fusions that lose
coins) and **Tax** buttons. A fusion can give up to 3 results: pick the one shown in the Fusion Machine.
Recipes are from [SkyShards](https://github.com/Campionnn/SkyShards) by Campion (MIT licence, included in the jar);
`tools/import_shards.py` updates them.

## AH tab

Three views (the **View** button):
- **AH -> Bazaar**: items someone listed on the AH for less than they sell for on the Bazaar. Buy the listing, sell
  it on the Bazaar. The AH price list also carries Bazaar quotes for most Bazaar items, so a "lowest BIN" within 3% of
  the Bazaar's own price isn't counted as an auction.
- **AH crafts**: recipes needing at least one thing that's only on the AH (at its lowest BIN; the rest at Bazaar
  prices), whose result sells on the Bazaar for more. Hover a row for the ingredients and where each is priced.
- **Lookup**: type 2+ letters in Search for any AH item's lowest BIN, and for craftable items the craft cost and
  craft -> AH profit (before AH fees). Pets show as "Seal pet (Epic)".

**Sell**, **Risky**, **Low vol** and **Tax** work like on the other tabs. AH prices are refreshed every 5 minutes.

## Worth button in your inventory

Open your inventory (E): a **Worth** button sits above its top-left corner. Click it to show a panel beside the
inventory with what everything you're carrying is worth right now, each item sold the best way: insta-sell on the
Bazaar (walking the buy orders, after tax), any NPC (green, no tax), or the AH lowest BIN for things not on the Bazaar.
It also says how much more sell offers would pay and how many items an NPC pays more for.
**Sacks**: open each of your sacks once (from /sacks) and the panel adds what's in them, remembered between sessions
and updated whenever you reopen a sack (the Gemstone Sack's rough / flawed / fine counts too). Click **Hide** to close it;
it stays the way you left it. Sacks, storage and equipped gear aren't counted.

## Earnings tab (what you actually made)

Every other tab says what *should* make money. This one says what *did*. Whenever Hypixel tells you in chat that you
bought or sold on the bazaar (an insta-buy, an insta-sell, or claiming a buy order or sell offer), the mod writes the
trade down. Nothing to set up: just trade.

- **Profit**: for items you both bought and sold, what you sold them for minus what those same items cost you (the
  average of what you paid). With coins per hour, counted from the first trade shown.
- **Crafts count too**: sell something you didn't buy, but crafted from ingredients you did buy here (every
  ingredient, in the amounts the recipe needs), and those ingredients' cost is counted against the sale: buy 80 of
  each robot part, sell 80 Precursor Apparatus, and you see the real profit. The ingredients stop counting as held.
- **Coins in - coins out**: everything earned minus everything spent. Below zero while you still hold stock.
- **Still holding**: bought and not sold yet, at what you paid (items you used in a craft stay here).
- **Other sales**: coins from selling things with no tracked cost (farmed, bought earlier, or crafted from things the
  mod didn't see you buy). Real coins,
  but there's no buy price to work a profit out from.
- **Show**: this session, today, the last 7 days, or all time. Below the totals, one row per item (bought, spent,
  sold, earned, profit, holding); click a column name to sort, click an item for its order book.
- **Session profit box**: a small box on your screen with this session's profit, coins per hour, and coins in minus
  coins out. It appears once you've traded on the bazaar; turn it off or move it in the gear (it goes under the orders
  box if they share a corner).
- **Forget trades** (click twice) deletes the history. Trades are kept in `config/bazaartips-earnings.jsonl`.

It only knows about bazaar trades made while the mod is running, so a craft flip shows as coins out on the
ingredients and "other sales" on the result; the **Coins in - coins out** line covers those.

## Orders tab and outbid alerts

Open **/bz -> Manage Orders** once: the mod reads your buy orders and sell offers off that screen (only yours,
not a co-op member's) and remembers them, even after you restart. Every time prices refresh (about once a
minute) it checks each one against the live order book. When someone beats your buy order or undercuts
your sell offer, you get one chat message (click **[/bz]** in it to open that item). The **Orders** tab lists
your orders, the ones that need you first at the top: Outbid / Undercut, Tied (someone has the same price),
Filled (claim it), Top. Hover one for the price to re-place it at. **Alerts** turns the chat messages on or
off; **Forget orders** clears the list.

New orders and cancellations also come from chat: "Buy Order Setup!" / "Sell Offer Setup!" adds the order (price each =
the total / amount), and "Cancelled! Refunded ..." removes it: a sell offer by item and amount, a buy order by the coins
refunded. If it can't tell which order was cancelled, nothing changes (reopen Manage Orders to be sure).

The mod also reads Hypixel's own bazaar chat lines: "Your Buy Order for 64x Diamond was filled!" marks that order
Filled straight away (so it isn't called outbid just because it left the order book), and claiming a filled order
takes it off the list. New and cancelled orders still need a visit to Manage Orders to show up or go away.

## Shopping list for your coins & slots

Enter your coins and/or free slots at the top of any list. Then:
- **Hover a craft** on the Crafts tab: the best amount, everything to buy for it (how many, and the cost) and what you'd sell for.
- **Every details screen opens with the shopping list**: Crafts (each ingredient, the price and how to buy it), Flipper
  (the buy orders and sell offers to place), NPC / Forge / Books (the best amount, each material, selling, slots).

## Panel in the bazaar

Open any item in /bz and a panel appears beside the chest: insta-buy / insta-sell (the same numbers as the item tooltip), weekly volume and
warnings; the flip (buy order and sell offer prices, profit each, trades per hour, flip score); crafting it
(cost and profit, by insta-buy and by buy orders); what an NPC pays; and your own orders on that item with
their status. It needs room beside the chest; if it doesn't show, try a smaller GUI scale.

## Orders box on your screen

A small box (top left by default) lists your bazaar orders with their status: Top, Tied, Outbid / Undercut
(red, with the price that beat you), Filled. The header says how many are outbid. It shows once you've
opened Manage Orders.

## Settings (the gear)

The ⚙ next to Rounded (it scrolls when the window is small):

- **General**: the open-screen key, which tab the key opens (last used, or always the same one), and which tabs are shown.
- **Item tooltips**: price lines on/off, craft lines on/off.
- **Bazaar**: the panel beside an item in /bz on/off.
- **Your orders**: the orders box on/off and where it sits, outbid chat alerts on/off.
- **Flip alerts**: a chat message (with a /bz link) when a flip reaches your alert score (default 80). The first
  check just says how many there are; after that you hear about new ones, at most 3 per price update and each
  item at most once an hour. Risky and low-volume flips never alert, and a flip only alerts once its SkyCofl
  price history has loaded, so the score is the full one.

## What it connects to

Only public data: Hypixel's bazaar and item list, your site's unlock lookup (only when you press Load),
AH lowest BINs (lb.tricked.dev, or moulberry.codes as a backup; NPC, Forge, Currency, Kat, Bits, AH and Essence tabs) and
SkyCofl (Flipper price history, Essence listing checks). The only commands it ever sends in game are /bz
(when you click /bz) and /viewauction (when you click "Open the cheapest auction" on the Essence tab).

## Build it (Windows)

1. Install **Java 25 (JDK)**, e.g. Microsoft Build of OpenJDK 25 or Eclipse Temurin 25.
2. Unzip this folder. Open a terminal in it (Shift + right-click > "Open in Terminal").
3. Run:
   ```
   .\gradlew.bat build
   ```
   The first build downloads Gradle, Minecraft and Fabric, so it takes a few minutes.
4. The mod is at `build\libs\bazaar-tooltips-0.20.0.jar`.

The build also runs the tests in `src/test` (prices, risk warnings, flips, crafts, NPC and forge math, the advisor,
best amounts, order tracking, alerts, earnings). If one
fails, the build fails and no jar is made, here and on GitHub.

## Auto-update

The mod checks this repo's Releases a little after Minecraft starts and every 6 hours. When there's a newer version it
downloads it into `mods/.bazaartips-update` (Fabric doesn't load from there), checks it really is this mod at that
version, and tells you in chat. When you close Minecraft it swaps the new jar in for the old one (on Windows a hidden
PowerShell does it a second later, once Java has let go of the old file), so the next launch runs the new version.
Turn it off with **Auto-update** in the gear; **Updates** shows the status and checks now when clicked.

## Install it

Put these in your Minecraft 26.2 Fabric `mods` folder:
- `bazaar-tooltips-0.20.0.jar`
- Fabric API for 26.2 (from Modrinth or CurseForge)

## Test it from IntelliJ (optional)

Open the folder in IntelliJ IDEA, let it import, then run the Gradle task `runClient`. That launches Minecraft with the mod loaded.

## Files

- `BazaarTooltipsClient.java` - startup, the B key, and the item tooltip lines.
- `BazaarPrices.java` - downloads the bazaar every 60 seconds (prices, order books, order counts).
- `NpcPrices.java` - NPC sell prices from Hypixel's item list.
- `Recipes.java` + `resources/bazaartips/recipes.json` - the website's recipes, names, requirements.
- `CraftEngine.java` - the website's Craft-tab math (chains, best amounts, shopping lists, filters, sorts).
- `Risk.java` - the manipulation warnings.
- `CraftReport.java` - every line of the craft details screen, with its explanation.
- `Flipper.java` - the Flipper tab's math and flip score. `CoflData.java` - SkyCofl history and 24h moves.
- `MoneyEngine.java` - the NPC and Forge tabs' math. `Amounts.java` - best amounts for coins & slots.
- `SiteData.java` + `resources/bazaartips/money.json` - the site's forge recipes, NPC shops, NPC locations.
- `MinionEngine.java` - the Minions and Profit / hr math. `EssenceEngine.java` - the Essence math.
- `AhPrices.java` - AH lowest BINs. `MoneyData.java` - builds the three tabs' lists. `MoneyReport.java` - their details lines.
- `OrderTracker.java` + `OrdersScreen.java` - your bazaar orders and outbid alerts.
- `Earnings.java` + `EarningsScreen.java` - the Earnings tab: your real trades and profit.
- `MoneyEngine.kat()` - the Kat tab (on `MoneyScreen`); the Bits tab is `MoneyEngine.currencies("bits")`.
- `AhEngine.java` + `AhScreen.java` - the AH tab. `InventoryValue.java` + `InventoryPanel.java` - the Worth button and panel in your inventory. `Cycle.java` - left / right click stepping through a button's options.
- `Overlays.java` + `ProductSummary.java` - the panel in the bazaar and the orders box on your screen.
- `SettingsScreen.java` + `TabsPickScreen.java` - the gear's settings. `FlipAlerts.java` - flip chat alerts.
- `Advisor.java` + `AdvisorScreen.java`, `AdvisorDetailScreen.java`, `AdvisorExcludeScreen.java` - the Coin advisor.
- `ShardEngine.java` + `ShardsScreen.java` + `resources/bazaartips/shards.json` - the Shards tab.
- `PriceAlerts.java` + `AlertsScreen.java` - price alerts. `SackStore.java` - what's in your sacks.
- `Updater.java` - the auto-updater. `Settings.java` - your saved settings. `UnlockLookup.java` - My unlocks.
- `FlipsScreen.java`, `FlipDetailScreen.java`, `SortScreen.java`, `FiltersScreen.java`, `BaseScreen.java`,
  `TableScreen.java`, `FlipperScreen.java`, `MoneyScreen.java`, `ReportScreen.java`, `FlipperDetailScreen.java`,
  `MoneyDetailScreen.java`, `PricesScreen.java`, `PriceDetailScreen.java`, `MinionScreen.java`, `MinionDetailScreen.java`,
  `ProfitScreen.java`, `ProfitDetailScreen.java`, `ProfitPickScreen.java`, `EssenceScreen.java`, `EssenceDetailScreen.java`,
  `Explain.java` - the screens.
- `Format.java` - number formatting. `Colors.java` - screen colors.
- `src/test/java/...` - the tests that run on every build.
- `tools/import_site_recipes.py` - copies the recipes, forge recipes and NPC shops from your site file again
  after you update the site:
  `python tools/import_site_recipes.py path/to/bazaar-vNN.html`
