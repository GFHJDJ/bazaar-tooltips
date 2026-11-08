package com.unc.bazaartips;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** For auto-pick: upgrades and fuels it must use, or must never use. Each click steps Any -> Must use -> Never use. */
public class ProfitPickScreen extends BaseScreen {
	private final List<String> options = new ArrayList<>();
	private final List<Button> buttons = new ArrayList<>();
	private int top, bw, gap, explainLines;

	public ProfitPickScreen(Screen parent) {
		super("Must use / never use", parent);
		for (String u : MinionEngine.UPGRADES) if (!u.equals("None")) options.add(u);
		for (String f : MinionEngine.AUTO_FUELS) if (!f.equals("None")) options.add(f);
	}

	private static String state(String o) {
		return Settings.profitRequire.contains(o) ? "Must use" : Settings.profitExclude.contains(o) ? "Never use" : "Any";
	}

	private static String label(String o) {
		return o.replace("Super Compactor 3000", "Super Compactor").replace("Enchanted Lava Bucket", "Ench. Lava").replace(" Bucket", "")
				.replace("Lesser Soulflow Engine", "Lesser Soulflow") + ": " + state(o);
	}

	@Override
	protected void init() {
		layoutPanel();
		button("< Back", left, 2, 50, b -> onClose());
		explainLines = height < 240 ? 2 : 3;
		top = 26;
		gap = 4;
		bw = (tableWidth - gap) / 2;
		int rows = (options.size() + 1) / 2;
		int step = Math.max(20, Math.min(24, (height - top - 10 - explainLines * (font.lineHeight + 1)) / rows));
		buttons.clear();
		for (int i = 0; i < options.size(); i++) {
			String o = options.get(i);
			buttons.add(button(label(o), left + (i % 2) * (bw + gap), top + (i / 2) * step, bw, b -> {
				if (Settings.profitRequire.remove(o)) Settings.profitExclude.add(o);
				else if (!Settings.profitExclude.remove(o)) Settings.profitRequire.add(o);
				Settings.save();
				relabel(b, label(o), bw);
			}));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		explain.clear();
		for (int i = 0; i < options.size(); i++) {
			Button b = buttons.get(i);
			explain.add(b.getX(), b.getY(), b.getX() + bw, b.getY() + 20, options.get(i) + ": " + state(options.get(i))
					+ ". Click to step Any -> Must use -> Never use. Auto-pick has two upgrade slots and one fuel, so at most two upgrades and one fuel count as must-use.");
		}
		explain.add(left, 2, left + 50, 22, "Back to Profit / hr.");
		drawPanel(graphics);
		graphics.centeredText(font, "Auto-pick: must use / never use", width / 2, 8, Colors.GOLD);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		drawExplainStrip(graphics, mouseX, mouseY, height - 6 - explainLines * (font.lineHeight + 1), explainLines,
				"Hover an option to see what it does.");
	}
}
