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
 *
 * <p>Wood recipes exist once per wood family, so an arraial in a spruce forest makes sticks,
 * chests and beds out of spruce. Several recipes can therefore make the same item; {@link #todasPara}
 * lists them and {@link Demanda} picks the one the armazém can best supply.
 */
public final class Receitas {
	public record Receita(Profissao quem, Item saida, int quantidade, Map<Item, Integer> entrada) {
	}

	/** One wood family: log, planks and the carpentry made from them. */
	public record Madeira(Item tora, Item tabua, Item porta, Item cerca, Item laje, Item escada, Item portao) {
	}

	public static final List<Madeira> MADEIRAS = List.of(
			new Madeira(Items.OAK_LOG, Items.OAK_PLANKS, Items.OAK_DOOR, Items.OAK_FENCE, Items.OAK_SLAB, Items.OAK_STAIRS, Items.OAK_FENCE_GATE),
			new Madeira(Items.SPRUCE_LOG, Items.SPRUCE_PLANKS, Items.SPRUCE_DOOR, Items.SPRUCE_FENCE, Items.SPRUCE_SLAB, Items.SPRUCE_STAIRS, Items.SPRUCE_FENCE_GATE),
			new Madeira(Items.BIRCH_LOG, Items.BIRCH_PLANKS, Items.BIRCH_DOOR, Items.BIRCH_FENCE, Items.BIRCH_SLAB, Items.BIRCH_STAIRS, Items.BIRCH_FENCE_GATE),
			new Madeira(Items.JUNGLE_LOG, Items.JUNGLE_PLANKS, Items.JUNGLE_DOOR, Items.JUNGLE_FENCE, Items.JUNGLE_SLAB, Items.JUNGLE_STAIRS, Items.JUNGLE_FENCE_GATE),
			new Madeira(Items.ACACIA_LOG, Items.ACACIA_PLANKS, Items.ACACIA_DOOR, Items.ACACIA_FENCE, Items.ACACIA_SLAB, Items.ACACIA_STAIRS, Items.ACACIA_FENCE_GATE),
			new Madeira(Items.DARK_OAK_LOG, Items.DARK_OAK_PLANKS, Items.DARK_OAK_DOOR, Items.DARK_OAK_FENCE, Items.DARK_OAK_SLAB, Items.DARK_OAK_STAIRS, Items.DARK_OAK_FENCE_GATE),
			new Madeira(Items.CHERRY_LOG, Items.CHERRY_PLANKS, Items.CHERRY_DOOR, Items.CHERRY_FENCE, Items.CHERRY_SLAB, Items.CHERRY_STAIRS, Items.CHERRY_FENCE_GATE),
			new Madeira(Items.MANGROVE_LOG, Items.MANGROVE_PLANKS, Items.MANGROVE_DOOR, Items.MANGROVE_FENCE, Items.MANGROVE_SLAB, Items.MANGROVE_STAIRS, Items.MANGROVE_FENCE_GATE));

	private static final List<Receita> TODAS = new ArrayList<>();

	static {
		for (Madeira m : MADEIRAS) {
			// Lenhador: carpentry, sticks, charcoal and chests, from any wood.
			add(Profissao.LENHADOR, m.tabua(), 4, Map.of(m.tora(), 1));
			add(Profissao.LENHADOR, m.porta(), 3, Map.of(m.tabua(), 6));
			add(Profissao.LENHADOR, m.cerca(), 3, Map.of(m.tabua(), 4, Items.STICK, 2));
			add(Profissao.LENHADOR, m.laje(), 6, Map.of(m.tabua(), 3));
			add(Profissao.LENHADOR, m.escada(), 4, Map.of(m.tabua(), 6));
			add(Profissao.LENHADOR, m.portao(), 1, Map.of(m.tabua(), 2, Items.STICK, 4));
			add(Profissao.LENHADOR, Items.STICK, 4, Map.of(m.tabua(), 2));
			add(Profissao.LENHADOR, Items.CHARCOAL, 1, Map.of(m.tora(), 2));
			add(Profissao.LENHADOR, Items.CHEST, 1, Map.of(m.tabua(), 8));
			// Any wool will do for the red bed: it is dyed with urucum anyway.
			for (Item la : lans()) {
				add(Profissao.PASTOR, Items.BED.red(), 1, Map.of(la, 3, m.tabua(), 3));
			}
			add(Profissao.PASTOR, Items.BED.white(), 1, Map.of(Items.WOOL.white(), 3, m.tabua(), 3));
			// No pastor yet? A straw mattress: the roceiro stuffs it with wheat straw (a new village's first beds).
			add(Profissao.ROCEIRO, Items.BED.red(), 1, Map.of(Items.WHEAT, 3, m.tabua(), 3));
		}
		add(Profissao.LENHADOR, Items.TORCH, 4, Map.of(Items.STICK, 1, Items.CHARCOAL, 1));

		// Oleiro: clay, bricks and mud bricks.
		add(Profissao.OLEIRO, Items.BRICK, 1, Map.of(Items.CLAY_BALL, 1));
		add(Profissao.OLEIRO, Items.BRICKS, 1, Map.of(Items.BRICK, 4));
		add(Profissao.OLEIRO, Items.BRICK_STAIRS, 4, Map.of(Items.BRICKS, 6));
		add(Profissao.OLEIRO, Items.BRICK_SLAB, 6, Map.of(Items.BRICKS, 3));
		add(Profissao.OLEIRO, Items.MUD_BRICKS, 1, Map.of(Items.DIRT, 1)); // taipa: earth and water, no food wasted
		add(Profissao.OLEIRO, Items.FLOWER_POT, 1, Map.of(Items.BRICK, 3));

		// Cavouqueiro: dressed stone.
		add(Profissao.CAVOUQUEIRO, Items.COBBLESTONE_STAIRS, 4, Map.of(Items.COBBLESTONE, 6));
		add(Profissao.CAVOUQUEIRO, Items.COBBLESTONE_SLAB, 6, Map.of(Items.COBBLESTONE, 3));
		add(Profissao.CAVOUQUEIRO, Items.COBBLESTONE_WALL, 6, Map.of(Items.COBBLESTONE, 6));
		add(Profissao.CAVOUQUEIRO, Items.FURNACE, 1, Map.of(Items.COBBLESTONE, 8));

		// Farinheiro: the casa de farinha. Grated, pressed and toasted, one root gives two portions of farinha.
		add(Profissao.FARINHEIRO, com.guilhermegoes.arraial.registry.ModItems.FARINHA, 2, Map.of(com.guilhermegoes.arraial.registry.ModItems.MANDIOCA, 1));
		add(Profissao.FARINHEIRO, com.guilhermegoes.arraial.registry.ModItems.BEIJU, 2, Map.of(com.guilhermegoes.arraial.registry.ModItems.MANDIOCA, 1));
		add(Profissao.FARINHEIRO, com.guilhermegoes.arraial.registry.ModItems.PACOCA, 2, Map.of(com.guilhermegoes.arraial.registry.ModItems.FARINHA, 2, Items.COOKED_BEEF, 1));

		// Boticário: a garrafada of wild herbs (two flowers of any kind).
		for (Item flor : List.of(Items.DANDELION, Items.POPPY, Items.BLUE_ORCHID, Items.ALLIUM, Items.AZURE_BLUET, Items.RED_TULIP,
				Items.ORANGE_TULIP, Items.WHITE_TULIP, Items.PINK_TULIP, Items.OXEYE_DAISY, Items.CORNFLOWER, Items.LILY_OF_THE_VALLEY)) {
			add(Profissao.BOTICARIO, com.guilhermegoes.arraial.registry.ModItems.REMEDIO, 1, Map.of(flor, 2));
		}

		// Entalhador: gilded talha for the Matriz (planks and gold leaf).
		add(Profissao.ENTALHADOR, com.guilhermegoes.arraial.registry.ModBlocks.TALHA_DOURADA.asItem(), 2, Map.of(Items.OAK_PLANKS, 2, Items.GOLD_NUGGET, 1));

		// Moleiro: the moinho d'água grinds one sheaf into two portions of farinha de trigo.
		add(Profissao.MOLEIRO, com.guilhermegoes.arraial.registry.ModItems.FARINHA_TRIGO, 2, Map.of(Items.WHEAT, 1));

		// Roceiro: bread for the arraial's table (from the mill's flour when there is any: a third of the wheat).
		add(Profissao.ROCEIRO, Items.BREAD, 1, Map.of(Items.WHEAT, 3));
		add(Profissao.ROCEIRO, Items.BREAD, 1, Map.of(com.guilhermegoes.arraial.registry.ModItems.FARINHA_TRIGO, 2));
	}

	private static List<Item> lans() {
		List<Item> l = new ArrayList<>();
		Items.WOOL.forEach(l::add);
		return l;
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

	/** Every recipe that makes {@code saida} (one per wood family for wood goods). */
	public static List<Receita> todasPara(Item saida) {
		return TODAS.stream().filter(r -> r.saida() == saida).toList();
	}

	/** The first (default) recipe for {@code saida}. */
	public static Optional<Receita> para(Item saida) {
		return TODAS.stream().filter(r -> r.saida() == saida).findFirst();
	}

	private Receitas() {
	}
}
