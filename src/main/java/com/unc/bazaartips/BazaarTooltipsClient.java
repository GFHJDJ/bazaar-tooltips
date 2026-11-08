package com.unc.bazaartips;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

public class BazaarTooltipsClient implements ClientModInitializer {
	/** The key that opens the bazaar screen (B unless you change it), in its own "Bazaar Tooltips" section of Key Binds. */
	static KeyMapping openKey;
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			net.minecraft.resources.Identifier.fromNamespaceAndPath("bazaartips", "main"));

	/** Show a warning on the tooltip once prices are older than this. */
	private static final long STALE_AFTER_MILLIS = 3 * 60 * 1000;

	@Override
	public void onInitializeClient() {
		Settings.load(FabricLoader.getInstance().getConfigDir().resolve("bazaartips.json"));
		Recipes.load();
		SiteData.load();
		CoflData.load(FabricLoader.getInstance().getConfigDir().resolve("bazaartips-history.json"));
		// After each price update, build the list the flips screen will show (on the download thread, so opening it is instant).
		BazaarPrices.afterRefresh = () -> {
			CraftEngine.current().crafts(Settings.buy == Settings.Buy.ORDER, Settings.chains != Settings.Chains.EXCLUDE);
			MoneyData.flips();
		};
		BazaarPrices.start();
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> addPriceLines(stack, lines));

		// B opens the bazaar screen on the tab you used last. Rebind it in Options > Controls > Key Binds > Bazaar Tooltips
		// (or the Key button on the screen).
		KeyMapping openFlips = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.bazaartips.open_flips", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY));
		openKey = openFlips;
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openFlips.consumeClick()) {
				if (client.player != null) client.gui.setScreen(BaseScreen.screenFor(Settings.tab));
			}
		});
	}

	private static void addPriceLines(ItemStack stack, List<Component> lines) {
		CompoundTag tag = skyblockData(stack);
		if (tag == null) return;
		String skyblockId = tag.getString("id").orElse("");
		if (skyblockId.isEmpty()) return; // not a Skyblock item

		String bazaarId = bazaarId(skyblockId, tag);
		BazaarPrices.Price price = bazaarId == null ? null : BazaarPrices.get(bazaarId);
		Double npc = NpcPrices.get(skyblockId);
		if (price == null && npc == null) return; // nothing to show, or prices haven't loaded yet

		int count = stack.getCount();
		lines.add(Component.empty());

		/*
		 * Bazaar price for 1:
		 *   Insta-buy: 1,300 (what you pay to buy one now)
		 *   Insta-sell: 1,250 (what you get selling one now)
		 * Bazaar price for all 64: Insta-buy 83,200 | Insta-sell 80,000
		 * Traded last 7 days: 1.2M insta-bought, 980k insta-sold
		 */
		if (price != null) {
			lines.add(Component.literal("Bazaar price for 1:").withStyle(ChatFormatting.GOLD));
			lines.add(labeled("  Insta-buy: ", Format.coins(price.instaBuy()), " (what you pay to buy one now)"));
			lines.add(labeled("  Insta-sell: ", Format.coins(price.instaSell()), " (what you get selling one now)"));
			if (count > 1) {
				lines.add(Component.literal("Bazaar price for all " + count + ": ").withStyle(ChatFormatting.GOLD)
						.append(buySell(price.instaBuy() * count, price.instaSell() * count)));
			}
			lines.add(Component.literal("Traded last 7 days: " + Format.amount(price.buyMovingWeek()) + " insta-bought, "
					+ Format.amount(price.sellMovingWeek()) + " insta-sold").withStyle(ChatFormatting.DARK_GRAY));
		}

		if (npc != null) addNpcLine(npc, count, lines);

		if (price != null) {
			addCraftLines(bazaarId, lines);
			long age = BazaarPrices.ageMillis();
			if (age > STALE_AFTER_MILLIS) {
				lines.add(Component.literal("Prices are " + Format.age(age) + " old (can't reach Hypixel's API)")
						.withStyle(ChatFormatting.RED));
			}
		}
	}

	/** "NPC pays: 5 each" for one item, "NPC pays: 5 each | 320 for all 64" for a stack. */
	private static void addNpcLine(double each, int count, List<Component> lines) {
		var line = Component.literal("NPC pays: ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(Format.coins(each)).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(" each").withStyle(ChatFormatting.GRAY));
		if (count > 1) {
			line.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(Format.coins(each * count)).withStyle(ChatFormatting.YELLOW))
					.append(Component.literal(" for all " + count).withStyle(ChatFormatting.GRAY));
		}
		lines.add(line);
	}

	/** "  Insta-buy: 1,300 (what you pay to buy one now)" with the number highlighted. */
	private static Component labeled(String label, String value, String note) {
		return Component.literal(label).withStyle(ChatFormatting.GRAY)
				.append(Component.literal(value).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(note).withStyle(ChatFormatting.DARK_GRAY));
	}

	/** Most ingredients to list before collapsing the rest into "+N more". */
	private static final int MAX_INGREDIENT_LINES = 4;

	/**
	 * Craft it (1 craft makes 9):
	 *   Needs 160x Diamond
	 *   Insta-buying ingredients: costs 1,244.4 per item
	 *     Profit per item: +39.3 by sell offer | -10.1 by insta-sell
	 *   Ingredients by buy order: costs 1,155.6 per item
	 *     Profit per item: +128.2 by sell offer | +78.8 by insta-sell
	 * Same numbers as the flips screen (and the website): sell offers 0.1 under the lowest offer,
	 * buy orders 0.1 over the top order, craft chains per your Filters setting.
	 */
	private static void addCraftLines(String id, List<Component> lines) {
		CraftEngine engine = CraftEngine.current();
		boolean chains = Settings.chains != Settings.Chains.EXCLUDE;
		CraftEngine.Craft insta = engine.craft(id, false, chains);
		CraftEngine.Craft order = engine.craft(id, true, chains);
		CraftEngine.Craft craft = insta != null ? insta : order;
		if (craft == null) return;

		String makes = craft.output() > 1 ? " (1 craft makes " + craft.output() + ")" : "";
		lines.add(Component.literal("Craft it" + makes + ":").withStyle(ChatFormatting.GOLD));

		var ingredients = craft.ingredients();
		// If listing them all would only hide one, just list them all.
		int shown = ingredients.size() <= MAX_INGREDIENT_LINES + 1 ? ingredients.size() : MAX_INGREDIENT_LINES;
		if (ingredients.size() == 1) {
			var in = ingredients.get(0);
			lines.add(Component.literal("  Needs " + in.qty() + "x " + in.name()).withStyle(ChatFormatting.DARK_GRAY));
		} else {
			lines.add(Component.literal("  Needs:").withStyle(ChatFormatting.DARK_GRAY));
			for (int i = 0; i < shown; i++) {
				var in = ingredients.get(i);
				lines.add(Component.literal("    " + in.qty() + "x " + in.name()).withStyle(ChatFormatting.DARK_GRAY));
			}
			if (ingredients.size() > shown) {
				lines.add(Component.literal("    +" + (ingredients.size() - shown) + " more (press B, click it for all)")
						.withStyle(ChatFormatting.DARK_GRAY));
			}
		}

		addOptionLines("Insta-buying ingredients", insta, lines);
		addOptionLines("Ingredients by buy order", order, lines);
		if (!craft.warnings().isEmpty()) {
			lines.add(Component.literal("  ! " + craft.warnings().get(0)).withStyle(
					craft.risk() == Risk.Level.HIGH ? ChatFormatting.RED : ChatFormatting.GOLD));
		}
	}

	/** One way of buying the ingredients: its cost per item, then profit for each way of selling. */
	private static void addOptionLines(String label, CraftEngine.Craft c, List<Component> lines) {
		if (c == null) return;
		lines.add(Component.literal("  " + label + ": costs ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(Format.coins(c.cost() / c.output())).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(" per item").withStyle(ChatFormatting.GRAY)));
		lines.add(Component.literal("    Profit per item: ").withStyle(ChatFormatting.GRAY)
				.append(profit(c.profitSellOffer() / c.output()))
				.append(Component.literal(" by sell offer | ").withStyle(ChatFormatting.GRAY))
				.append(profit(c.profitInstaSell() / c.output()))
				.append(Component.literal(" by insta-sell").withStyle(ChatFormatting.GRAY)));
	}

	/** Green for profit, red for loss, gray when there's no price. */
	private static Component profit(double value) {
		ChatFormatting color = Double.isNaN(value) ? ChatFormatting.GRAY
				: value >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
		return Component.literal(Format.signedCoins(value)).withStyle(color);
	}

	/** "Buy 1,234.5 | Sell 1,200" with the numbers highlighted. */
	private static Component buySell(double buy, double sell) {
		return Component.literal("Insta-buy ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(Format.coins(buy)).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(" | Insta-sell ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(Format.coins(sell)).withStyle(ChatFormatting.YELLOW));
	}

	/**
	 * The data Hypixel stores on every Skyblock item (its "custom_data"), including the item ID.
	 * Null for items without it.
	 */
	static CompoundTag skyblockData(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : data.copyTag();
	}

	/** Bazaar product ID for an item, or null for non-Skyblock items. */
	static String bazaarId(ItemStack stack) {
		CompoundTag tag = skyblockData(stack);
		if (tag == null) return null;
		String id = tag.getString("id").orElse("");
		return id.isEmpty() ? null : bazaarId(id, tag);
	}

	/** Turns a Skyblock item ID into the matching bazaar product ID (null if it can't be on the bazaar). */
	static String bazaarId(String id, CompoundTag tag) {
		// Enchanted books are all "ENCHANTED_BOOK"; the bazaar lists them per enchant and level.
		// Only single-enchant books are on the bazaar.
		if (id.equals("ENCHANTED_BOOK")) {
			CompoundTag enchants = tag.getCompound("enchantments").orElse(null);
			if (enchants == null || enchants.keySet().size() != 1) return null;
			String name = enchants.keySet().iterator().next();
			int level = enchants.getInt(name).orElse(0);
			return Format.enchantmentId(name, level);
		}
		return id;
	}
}
