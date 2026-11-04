package com.guilhermegoes.arraial.trabalho;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.obra.CanteiroBlock;
import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import com.guilhermegoes.arraial.obra.PlanoDeObra;
import com.guilhermegoes.arraial.vila.ArmazemBlockEntity;
import com.guilhermegoes.arraial.vila.Vilas;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * What the arraial is short of right now: materials its construction sites still need
 * (beyond what is in their chests and the armazém), expanded through the trades' recipes
 * (a missing door means planks are needed, which means logs...), plus bread when food runs low.
 */
public final class Demanda {
	private static final int PROFUNDIDADE = 3;
	/** The arraial wants this many days of food per morador in stock. */
	private static final int DIAS_DE_RESERVA = 3;

	public static Map<Item, Integer> calcular(ServerLevel level, BlockPos vila) {
		List<ArmazemBlockEntity> armazens = Vilas.armazens(level, vila);
		Map<Item, Integer> falta = new LinkedHashMap<>();

		for (BlockPos p : CanteiroBlockEntity.ativos(level)) {
			if (!p.closerThan(vila, Vilas.RAIO) || !(level.getBlockEntity(p) instanceof CanteiroBlockEntity c) || !c.temTrabalho()) {
				continue;
			}
			PlanoDeObra plano = c.plano(level).orElse(null);
			if (plano == null) {
				continue;
			}
			Container bau = c.bau(level);
			for (Map.Entry<Item, Integer> e : plano.materiais(c.progresso()).entrySet()) {
				int temBau = bau == null ? 0 : CanteiroBlock.contar(bau, e.getKey());
				int n = e.getValue() - temBau;
				if (n > 0) {
					falta.merge(e.getKey(), n, Integer::sum);
				}
			}
		}
		// Subtract what the armazém already holds.
		falta.replaceAll((item, n) -> n - Vilas.contar(armazens, item));
		falta.values().removeIf(n -> n <= 0);

		// Food: keep a few days of bread for everyone.
		int moradores = level.getEntitiesOfClass(MoradorEntity.class, new AABB(vila).inflate(Vilas.RAIO),
				m -> vila.equals(m.vila())).size();
		int comida = Vilas.contar(armazens, s -> s.has(DataComponents.FOOD));
		int querComida = moradores * DIAS_DE_RESERVA - comida;
		if (querComida > 0) {
			falta.merge(Items.BREAD, querComida, Integer::sum);
		}

		// Expand through recipes: what is needed to make what is missing.
		for (int nivel = 0; nivel < PROFUNDIDADE; nivel++) {
			Map<Item, Integer> extra = new LinkedHashMap<>();
			for (Map.Entry<Item, Integer> e : new ArrayList<>(falta.entrySet())) {
				Receitas.para(e.getKey()).ifPresent(r -> {
					int lotes = (e.getValue() + r.quantidade() - 1) / r.quantidade();
					r.entrada().forEach((ing, q) -> {
						int precisa = lotes * q - Vilas.contar(armazens, ing);
						if (precisa > 0 && !falta.containsKey(ing)) {
							extra.merge(ing, precisa, Integer::sum);
						}
					});
				});
			}
			if (extra.isEmpty()) {
				break;
			}
			extra.forEach((k, v) -> falta.merge(k, v, Integer::sum));
		}
		return falta;
	}

	private Demanda() {
	}
}
