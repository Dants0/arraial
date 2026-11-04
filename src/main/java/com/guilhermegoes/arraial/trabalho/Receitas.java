package com.guilhermegoes.arraial.trabalho;

import com.guilhermegoes.arraial.npc.Profissao;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * What each trade knows how to make at its workstation. Quantities follow vanilla crafting,
 * slightly simplified (e.g. the oleiro bakes one brick per clay ball; mud bricks take dirt + wheat
 * like vanilla packed mud).
 */
public final class Receitas {
	public record Receita(Profissao quem, Item saida, int quantidade, Map<Item, Integer> entrada) {
	}

	private static final List<Receita> TODAS = new ArrayList<>();

	static {
		// Lenhador: carpentry for each common wood.
		madeira(Items.OAK_LOG, Items.OAK_PLANKS, Items.OAK_DOOR, Items.OAK_FENCE, Items.OAK_SLAB, Items.OAK_STAIRS);
		madeira(Items.SPRUCE_LOG, Items.SPRUCE_PLANKS, Items.SPRUCE_DOOR, Items.SPRUCE_FENCE, Items.SPRUCE_SLAB, Items.SPRUCE_STAIRS);
		madeira(Items.BIRCH_LOG, Items.BIRCH_PLANKS, Items.BIRCH_DOOR, Items.BIRCH_FENCE, Items.BIRCH_SLAB, Items.BIRCH_STAIRS);
		madeira(Items.JUNGLE_LOG, Items.JUNGLE_PLANKS, Items.JUNGLE_DOOR, Items.JUNGLE_FENCE, Items.JUNGLE_SLAB, Items.JUNGLE_STAIRS);
		madeira(Items.ACACIA_LOG, Items.ACACIA_PLANKS, Items.ACACIA_DOOR, Items.ACACIA_FENCE, Items.ACACIA_SLAB, Items.ACACIA_STAIRS);
		madeira(Items.DARK_OAK_LOG, Items.DARK_OAK_PLANKS, Items.DARK_OAK_DOOR, Items.DARK_OAK_FENCE, Items.DARK_OAK_SLAB, Items.DARK_OAK_STAIRS);
		add(Profissao.LENHADOR, Items.STICK, 4, Map.of(Items.OAK_PLANKS, 2));
		add(Profissao.LENHADOR, Items.TORCH, 4, Map.of(Items.STICK, 1, Items.CHARCOAL, 1));
		add(Profissao.LENHADOR, Items.CHARCOAL, 1, Map.of(Items.OAK_LOG, 2));

		// Oleiro: clay, bricks and mud bricks.
		add(Profissao.OLEIRO, Items.BRICK, 1, Map.of(Items.CLAY_BALL, 1));
		add(Profissao.OLEIRO, Items.BRICKS, 1, Map.of(Items.BRICK, 4));
		add(Profissao.OLEIRO, Items.BRICK_STAIRS, 4, Map.of(Items.BRICKS, 6));
		add(Profissao.OLEIRO, Items.BRICK_SLAB, 6, Map.of(Items.BRICKS, 3));
		add(Profissao.OLEIRO, Items.MUD_BRICKS, 1, Map.of(Items.DIRT, 1, Items.WHEAT, 1));
		add(Profissao.OLEIRO, Items.FLOWER_POT, 1, Map.of(Items.BRICK, 3));

		// Lenhador: chests for new storehouses.
		add(Profissao.LENHADOR, Items.CHEST, 1, Map.of(Items.OAK_PLANKS, 8));

		// Pastor: beds for the houses (the red is dyed with urucum, so it costs nothing extra).
		add(Profissao.PASTOR, Items.BED.red(), 1, Map.of(Items.WOOL.white(), 3, Items.OAK_PLANKS, 3));
		add(Profissao.PASTOR, Items.BED.white(), 1, Map.of(Items.WOOL.white(), 3, Items.OAK_PLANKS, 3));

		// Cavouqueiro: dressed stone.
		add(Profissao.CAVOUQUEIRO, Items.COBBLESTONE_STAIRS, 4, Map.of(Items.COBBLESTONE, 6));
		add(Profissao.CAVOUQUEIRO, Items.COBBLESTONE_SLAB, 6, Map.of(Items.COBBLESTONE, 3));
		add(Profissao.CAVOUQUEIRO, Items.COBBLESTONE_WALL, 6, Map.of(Items.COBBLESTONE, 6));
		add(Profissao.CAVOUQUEIRO, Items.FURNACE, 1, Map.of(Items.COBBLESTONE, 8));

		// Roceiro: bread for the arraial's table.
		add(Profissao.ROCEIRO, Items.BREAD, 1, Map.of(Items.WHEAT, 3));
	}

	private static void madeira(Item tora, Item tabua, Item porta, Item cerca, Item laje, Item escada) {
		add(Profissao.LENHADOR, tabua, 4, Map.of(tora, 1));
		add(Profissao.LENHADOR, porta, 3, Map.of(tabua, 6));
		add(Profissao.LENHADOR, cerca, 3, Map.of(tabua, 4, Items.STICK, 2));
		add(Profissao.LENHADOR, laje, 6, Map.of(tabua, 3));
		add(Profissao.LENHADOR, escada, 4, Map.of(tabua, 6));
	}

	private static void add(Profissao quem, Item saida, int qtd, Map<Item, Integer> entrada) {
		TODAS.add(new Receita(quem, saida, qtd, entrada));
	}

	public static List<Receita> todas() {
		return TODAS;
	}

	public static List<Receita> de(Profissao p) {
		return TODAS.stream().filter(r -> r.quem() == p).toList();
	}

	public static Optional<Receita> para(Item saida) {
		return TODAS.stream().filter(r -> r.saida() == saida).findFirst();
	}

	private Receitas() {
	}
}
