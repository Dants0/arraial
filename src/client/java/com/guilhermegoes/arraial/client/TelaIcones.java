package com.guilhermegoes.arraial.client;

import com.guilhermegoes.arraial.Arraial;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Dev only (used by the Roteiro): every item of the mod drawn on a magenta sheet, as the
 * inventory shows it, so tools/gerar_wiki.js can cut the icons out of a screenshot. The layout
 * goes to run/icones.json (item id → pixel box).
 */
class TelaIcones extends Screen {
	static final int CELULA = 20;
	static final int COLUNAS = 18;
	static final int MARGEM = 4;
	static final int MAGENTA = 0xFFFF00FF;

	final List<Item> itens = new ArrayList<>();

	TelaIcones() {
		super(Component.literal("icones"));
		for (Item i : BuiltInRegistries.ITEM) {
			if (BuiltInRegistries.ITEM.getKey(i).getNamespace().equals(Arraial.MOD_ID)) {
				itens.add(i);
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, width, height, MAGENTA);
		for (int n = 0; n < itens.size(); n++) {
			g.item(new ItemStack(itens.get(n)), MARGEM + (n % COLUNAS) * CELULA, MARGEM + (n / COLUNAS) * CELULA);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** {"guiScale":2,"itens":{"cepo":[x,y,size],...}} in window pixels. */
	String manifesto(double escala) {
		StringBuilder s = new StringBuilder("{\"escala\":").append(escala).append(",\"itens\":{");
		for (int n = 0; n < itens.size(); n++) {
			int x = (int) Math.round((MARGEM + (n % COLUNAS) * CELULA) * escala);
			int y = (int) Math.round((MARGEM + (n / COLUNAS) * CELULA) * escala);
			if (n > 0) {
				s.append(',');
			}
			s.append('"').append(BuiltInRegistries.ITEM.getKey(itens.get(n)).getPath()).append("\":[").append(x).append(',').append(y).append(',')
					.append((int) Math.round(16 * escala)).append(']');
		}
		return s.append("}}").toString();
	}
}
