package com.unc.bazaartips;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The website's craft filters and settings: low volume, chains, Personal Compactor, requirements, your unlocks, tax. */
public class FiltersScreen extends BaseScreen {
	private record Line(String label, int y, String explanation) {}

	private final List<Line> lines = new ArrayList<>();
	private int controlX;
	private int controlW;
	private int statusY;
	private int explainLines;
	private EditBox customTax;
	private EditBox nameBox;

	public FiltersScreen(Screen parent) {
		super("Filters & tax", parent);
	}

	@Override
	protected void init() {
		layoutPanel();
		lines.clear();
		controlX = left + Math.min(150, tableWidth / 3);
		controlW = left + tableWidth - controlX;
		button("< Back", left, 2, 50, b -> onClose());
		int y = 26, step = 22;

		line("Low volume", y, "Low volume: hide crafts whose result traded under 10,000 in the last 7 days (the lower of insta-bought "
				+ "and insta-sold). Those are slow to sell and easier to manipulate.");
		cycle(y, () -> "Low volume: " + (Settings.hideLowVolume ? "Hidden" : "Shown"), d -> Settings.hideLowVolume = !Settings.hideLowVolume);
		y += step;

		line("Craft chains", y, "Craft chains: " + "Off = " + Settings.Chains.EXCLUDE.explanation + " On = " + Settings.Chains.ALL.explanation
				+ " Only chains = " + Settings.Chains.ONLY.explanation);
		cycle(y, () -> "Chains: " + Settings.chains.label, d -> Settings.chains = Cycle.step(Settings.chains, d));
		y += step;

		line("Personal Compactor", y, "Personal Compactor: " + Settings.Compactor.ONLY.explanation + " / " + Settings.Compactor.HIDE.explanation);
		cycle(y, () -> "Compactor: " + Settings.compactor.label, d -> Settings.compactor = Cycle.step(Settings.compactor, d));
		y += step;

		line("Requirements", y, "Requirements: " + Settings.Reqs.NONE.explanation + " " + Settings.Reqs.UNLOCKED.explanation);
		cycle(y, () -> "Requirements: " + Settings.reqs.label, d -> Settings.reqs = Cycle.step(Settings.reqs, d));
		y += step;

		line("Bazaar tax", y, "Bazaar tax: what the bazaar keeps from every sale. 1.25% is the base; the Bazaar Flipper upgrade "
				+ "lowers it to 1.125% or 1%. Use Custom when a mayor changes it. Used for every profit.");
		int half = controlW / 2 - 2;
		Button taxBtn = button(taxLabel(), controlX, y, half, b -> {
			Settings.stepTaxPreset(1);
			Settings.save();
			relabel(b, taxLabel(), half);
			customTax.setValue(trim(Settings.taxPercent));
		});
		onRightClick(taxBtn, () -> {
			Settings.stepTaxPreset(-1);
			Settings.save();
			relabel(taxBtn, taxLabel(), half);
			customTax.setValue(trim(Settings.taxPercent));
		});
		customTax = addRenderableWidget(new EditBox(font, controlX + half + 4, y + 2, controlW - half - 4, 16, Component.literal("Custom tax %")));
		customTax.setValue(trim(Settings.taxPercent));
		customTax.setResponder(v -> {
			try {
				double t = Double.parseDouble(v.trim());
				if (t >= 0 && t <= 50) {
					Settings.taxPercent = t;
					Settings.save();
				}
			} catch (NumberFormatException ignored) {
				// keep the last good value while typing
			}
		});
		y += step;

		line("My unlocks", y, "My unlocks: type your Minecraft name and load your collections, slayers and dungeon bosses "
				+ "(through the website's lookup server). Then Requirements switches to 'I've unlocked'.");
		int nameW = controlW / 2 - 2;
		nameBox = addRenderableWidget(new EditBox(font, controlX, y + 2, nameW, 16, Component.literal("Minecraft name")));
		String defaultName = Settings.lookupName;
		if (defaultName.isEmpty() && minecraft != null && minecraft.player != null) defaultName = minecraft.player.getGameProfile().name();
		nameBox.setValue(defaultName);
		int bw2 = (controlW - nameW - 8) / 2;
		button("Load", controlX + nameW + 4, y, bw2, b -> UnlockLookup.start(nameBox.getValue(), ""));
		button("Clear", controlX + nameW + 8 + bw2, y, controlW - nameW - 8 - bw2, b -> UnlockLookup.clear());
		y += step;

		if (UnlockLookup.profiles.size() > 1) {
			line("Profile", y, "Profile: which of your SkyBlock profiles to load unlocks from. Click to switch and reload.");
			cycle(y, () -> "Profile: " + Settings.lookupProfile,
					d -> UnlockLookup.start(nameBox.getValue(), Cycle.step(UnlockLookup.profiles, Settings.lookupProfile, d)));
			y += step;
		}
		statusY = y + 2;
		explainLines = height < 240 ? 2 : 3;
	}

	private void line(String label, int y, String explanation) {
		lines.add(new Line(label, y, explanation));
	}

	/** A button that steps a setting forward (left click) or back (right click), saves, and relabels itself. */
	private void cycle(int y, java.util.function.Supplier<String> label, java.util.function.IntConsumer step) {
		Button btn = button(label.get(), controlX, y, controlW, b -> {
			step.accept(1);
			Settings.save();
			relabel(b, label.get(), controlW);
		});
		onRightClick(btn, () -> {
			step.accept(-1);
			Settings.save();
			relabel(btn, label.get(), controlW);
		});
	}

	private static String taxLabel() {
		for (double t : Settings.TAX_PRESETS) if (t == Settings.taxPercent) return "Tax: " + Settings.taxLabel();
		return "Tax: Custom " + Settings.taxLabel();
	}

	/** 1.25 -> 1.125 -> 1 -> (custom: whatever's typed) -> 1.25 */
	private static String trim(double v) {
		return String.format(Locale.US, "%.3f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		explain.clear();
		drawPanel(graphics);
		graphics.centeredText(font, "Filters & tax", width / 2, 8, Colors.GOLD);
		for (Line l : lines) {
			graphics.text(font, fit(l.label(), controlX - left - 6), left + 2, l.y() + 6, Colors.GRAY);
			explain.add(left, l.y(), left + tableWidth, l.y() + 20, l.explanation());
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		String status = UnlockLookup.status;
		if (status.isEmpty() && Recipes.hasUnlocks()) {
			status = Settings.unlocks.values().stream().filter(v -> v > 0).count() + " unlocks saved"
					+ (Settings.lookupName.isEmpty() ? "" : " for " + Settings.lookupName) + ".";
		}
		int explainY = height - 6 - explainLines * (font.lineHeight + 1);
		int statusLines = Math.min(3, (explainY - 4 - statusY) / (font.lineHeight + 1)); // only what fits above the strip
		if (!status.isEmpty() && statusLines > 0) {
			Explain.draw(graphics, font, status, width / 2, statusY, tableWidth, statusLines, UnlockLookup.error ? Colors.RED : Colors.GRAY);
		}
		drawExplainStrip(graphics, mouseX, mouseY, explainY, explainLines,
				"Hover a setting to see what it does. Changes are saved right away.");
	}
}
