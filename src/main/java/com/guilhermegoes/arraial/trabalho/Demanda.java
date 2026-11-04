package com.guilhermegoes.arraial.trabalho;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
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

	// ------------------------------------------------------------ can the arraial get it?

	/** Raw materials and which trade gathers them. */
	private static final Map<Item, Profissao> FONTES = new java.util.HashMap<>();

	static {
		for (Item i : List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.ANDESITE, Items.DIORITE, Items.GRANITE,
				Items.COAL, Items.RAW_IRON, Items.RAW_COPPER)) {
			FONTES.put(i, Profissao.CAVOUQUEIRO);
		}
		for (Item i : List.of(Items.DIRT, Items.CLAY_BALL, Items.SAND, Items.GRAVEL, Items.MUD)) {
			FONTES.put(i, Profissao.BARREIRO);
		}
		for (Item i : List.of(Items.WHEAT, Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.BEETROOT_SEEDS)) {
			FONTES.put(i, Profissao.ROCEIRO);
		}
		for (Item i : List.of(Items.MUTTON, Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.LEATHER, Items.FEATHER, Items.EGG)) {
			FONTES.put(i, Profissao.PASTOR);
		}
		Items.WOOL.forEach(i -> FONTES.put(i, Profissao.PASTOR));
		FONTES.put(Items.APPLE, Profissao.LENHADOR);
	}

	/** The trades present in the arraial. */
	public static java.util.Set<Profissao> oficios(ServerLevel level, BlockPos vila) {
		java.util.Set<Profissao> set = java.util.EnumSet.noneOf(Profissao.class);
		for (MoradorEntity m : level.getEntitiesOfClass(MoradorEntity.class, new AABB(vila).inflate(Vilas.RAIO), m -> vila.equals(m.vila()))) {
			set.add(m.profissao());
		}
		return set;
	}

	/**
	 * The first material of {@code materiais} the arraial cannot get: not in stock, nobody
	 * gathers it, and no trade present can make it. Empty when everything is reachable.
	 */
	public static java.util.Optional<Item> inviavel(ServerLevel level, BlockPos vila, Map<Item, Integer> materiais, java.util.Set<Profissao> oficios) {
		List<ArmazemBlockEntity> armazens = Vilas.armazens(level, vila);
		for (Map.Entry<Item, Integer> e : materiais.entrySet()) {
			if (!obtivel(armazens, e.getKey(), e.getValue(), oficios, 0)) {
				return java.util.Optional.of(e.getKey());
			}
		}
		return java.util.Optional.empty();
	}

	private static boolean obtivel(List<ArmazemBlockEntity> armazens, Item item, int quantidade, java.util.Set<Profissao> oficios, int nivel) {
		if (Vilas.contar(armazens, item) >= quantidade) {
			return true;
		}
		Profissao fonte = item.getDefaultInstance().is(net.minecraft.tags.ItemTags.LOGS) ? Profissao.LENHADOR : FONTES.get(item);
		if (fonte != null && oficios.contains(fonte)) {
			return true;
		}
		if (nivel >= 4) {
			return false;
		}
		var receita = Receitas.para(item);
		if (receita.isEmpty() || !oficios.contains(receita.get().quem())) {
			return false;
		}
		int lotes = (quantidade + receita.get().quantidade() - 1) / receita.get().quantidade();
		for (Map.Entry<Item, Integer> ing : receita.get().entrada().entrySet()) {
			if (!obtivel(armazens, ing.getKey(), ing.getValue() * lotes, oficios, nivel + 1)) {
				return false;
			}
		}
		return true;
	}

	/** The trade that gathers or makes {@code item}, or null. */
	public static @org.jspecify.annotations.Nullable Profissao quemFaz(Item item) {
		var r = Receitas.para(item);
		if (r.isPresent()) {
			return r.get().quem();
		}
		return item.getDefaultInstance().is(net.minecraft.tags.ItemTags.LOGS) ? Profissao.LENHADOR : FONTES.get(item);
	}

	private Demanda() {
	}
}
