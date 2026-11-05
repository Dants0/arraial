package com.guilhermegoes.arraial.trabalho;

import com.guilhermegoes.arraial.npc.Profissao;
import com.guilhermegoes.arraial.vila.ArraialBlockEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The prefeito's minimum stock: goods the arraial keeps on hand even when no building asks for
 * them, so the next house starts without waiting. Only an arraial with a prefeito plans ahead;
 * before the election the player is in charge. Targets grow with the population and only cover
 * goods some trade of the village can make. Reserve crafting never touches materials that a
 * construction site still needs (see {@link Demanda.Resultado#livre}).
 */
public final class Estoque {
	public static Map<Item, Integer> metas(ServerLevel level, BlockPos vila, int moradores, Set<Profissao> oficios, Map<Item, Integer> inventario) {
		Map<Item, Integer> metas = new LinkedHashMap<>();
		if (!(level.getBlockEntity(vila) instanceof ArraialBlockEntity arraial) || arraial.prefeito() == null) {
			return metas;
		}
		int n = moradores;
		if (oficios.contains(Profissao.LENHADOR)) {
			Receitas.Madeira m = madeiraDaVila(inventario);
			metas.put(m.tabua(), 16 + 4 * n);
			metas.put(Items.STICK, 8 + n);
			metas.put(Items.TORCH, 8 + 2 * n);
			metas.put(m.porta(), 2);
		}
		if (oficios.contains(Profissao.ENGENHEIRO)) {
			Receitas.Madeira m = madeiraDaVila(inventario);
			metas.merge(Items.TORCH, 16, Math::max);
			metas.merge(m.cerca(), 8, Math::max);
			metas.merge(m.tabua(), 24, Integer::sum);
		}
		if (oficios.contains(Profissao.BOTICARIO)) {
			metas.put(com.guilhermegoes.arraial.registry.ModItems.REMEDIO, 2 + n / 6);
		}
		if (oficios.contains(Profissao.FARINHEIRO)) {
			metas.put(com.guilhermegoes.arraial.registry.ModItems.BEIJU, 4);
		}
		if (oficios.contains(Profissao.OLEIRO)) {
			metas.put(Items.BRICKS, 8 + 2 * n);
		}
		if (oficios.contains(Profissao.CAVOUQUEIRO)) {
			metas.put(Items.COBBLESTONE_STAIRS, 8);
			metas.put(Items.COBBLESTONE_SLAB, 6 + n);
		}
		if (oficios.contains(Profissao.PASTOR)) {
			metas.put(Items.BED.red(), 1);
		}
		return metas;
	}

	/** The wood family the arraial has the most of (logs + planks); oak when there is none. */
	public static Receitas.Madeira madeiraDaVila(Map<Item, Integer> inventario) {
		Receitas.Madeira melhor = Receitas.MADEIRAS.get(0);
		int max = 0;
		for (Receitas.Madeira m : Receitas.MADEIRAS) {
			int q = inventario.getOrDefault(m.tora(), 0) * 4 + inventario.getOrDefault(m.tabua(), 0);
			if (q > max) {
				max = q;
				melhor = m;
			}
		}
		return melhor;
	}

	private Estoque() {
	}
}
