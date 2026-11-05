package com.guilhermegoes.arraial.registry;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.obra.CanteiroBlock;
import com.guilhermegoes.arraial.vila.ArraialBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	/** "Marco da Obra": the construction-site marker the pedreiro works from. */
	public static final Block CANTEIRO = register("canteiro",
			new CanteiroBlock(props("canteiro")
					.mapColor(MapColor.WOOD)
					.strength(1.5F)
					.sound(SoundType.WOOD)
					.noOcclusion()
					.noCollision()));

	/** "Cruzeiro": the wooden cross that founds an arraial and marks its centre. */
	public static final Block CRUZEIRO = register("cruzeiro",
			new ArraialBlock(props("cruzeiro")
					.mapColor(MapColor.WOOD)
					.strength(2.0F)
					.sound(SoundType.WOOD)
					.noOcclusion()));

	/** "Armazém": the arraial's shared storehouse (54 slots). Workers deposit here; builders and the hungry take from it. */
	public static final Block ARMAZEM = register("armazem",
			new com.guilhermegoes.arraial.vila.ArmazemBlock(props("armazem")
					.mapColor(MapColor.WOOD)
					.strength(2.5F)
					.sound(SoundType.WOOD)));

	/** Workstation of the lenhador (woodcutter). */
	public static final Block CEPO = register("cepo",
			new Block(props("cepo").mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));

	/** Workstation of the roceiro (farmer): a big woven basket. */
	public static final Block BALAIO = register("balaio",
			new Block(props("balaio").mapColor(MapColor.COLOR_YELLOW).strength(1.0F).sound(SoundType.GRASS).noOcclusion()));

	/** Workstation of the oleiro (potter): a brick kiln. */
	public static final Block FORNO_OLARIA = register("forno_olaria",
			new Block(props("forno_olaria").mapColor(MapColor.COLOR_RED).strength(3.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)));


	/** Workstation of the soldado: a rack of weapons by the guard post. */
	public static final Block POSTO_GUARDA = register("posto_guarda",
			new Block(props("posto_guarda").mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));

	/** Workstation of the pastor (herder): a feeding trough. */
	public static final Block COCHO = register("cocho",
			new Block(props("cocho").mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion()));

	/** Workstation of the barreiro (clay digger): a tub of wet clay; the pit opens in front of it. */
	public static final Block GAMELA = register("gamela",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("gamela").mapColor(MapColor.CLAY).strength(1.0F).sound(SoundType.MUD).noOcclusion()));

	/** Workstation of the cavouqueiro (quarryman): a stonecutting bench; the quarry opens in front of it. */
	public static final Block BANCADA_CANTEIRO = register("bancada_canteiro",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("bancada_canteiro").mapColor(MapColor.STONE).strength(2.5F)
					.requiresCorrectToolForDrops().sound(SoundType.STONE)));

	/** Lampião: an oil lamp of tin and glass, standing or hanging, with a flickering flame. */
	public static final Block LAMPIAO = register("lampiao",
			new net.minecraft.world.level.block.LanternBlock(props("lampiao").mapColor(MapColor.METAL).forceSolidOn().strength(2.0F)
					.sound(SoundType.LANTERN).lightLevel(s -> 15).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)));

	/** Mandioca crop; its item is the maniva (registered in ModItems), so no block item here. */
	public static final Block MANDIOCA = registerSemItem("mandioca",
			new com.guilhermegoes.arraial.trabalho.MandiocaBlock(props("mandioca").mapColor(MapColor.PLANT).noCollision().randomTicks()
					.instabreak().sound(SoundType.CROP).pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)));

	/** Workstation of the farinheiro: the clay oven with its big copper pan where the farinha is toasted. */
	public static final Block FORNO_FARINHA = register("forno_farinha",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("forno_farinha").mapColor(MapColor.TERRACOTTA_ORANGE).strength(2.0F)
					.sound(SoundType.MUD_BRICKS).noOcclusion()));

	/** Workstation of the feirante: a market stall with an awning. */
	public static final Block BANCA_FEIRA = register("banca_feira",
			new com.guilhermegoes.arraial.vila.BancaFeiraBlock(props("banca_feira").mapColor(MapColor.WOOD).strength(1.5F)
					.sound(SoundType.WOOD).noOcclusion()));

	/** Workstation of the tropeiro: the cangalha, the pack saddle of the mule troop. */
	public static final Block CANGALHA = register("cangalha",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("cangalha").mapColor(MapColor.COLOR_BROWN).strength(1.0F)
					.sound(SoundType.WOOD).noOcclusion()));

	/** Workstation of the boticário: the counter with jars of remedies. */
	public static final Block BALCAO_BOTICA = register("balcao_botica",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("balcao_botica").mapColor(MapColor.WOOD).strength(1.5F)
					.sound(SoundType.WOOD).noOcclusion()));

	private static BlockBehaviour.Properties props(String name) {
		return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, Arraial.id(name)));
	}

	private static Block register(String name, Block block) {
		Registry.register(BuiltInRegistries.BLOCK, Arraial.id(name), block);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Arraial.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	private static Block registerSemItem(String name, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK, Arraial.id(name), block);
	}

	public static void init() {
	}

	private ModBlocks() {
	}
}
