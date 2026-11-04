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
					.noOcclusion()));

	/** "Cruzeiro": the wooden cross that founds an arraial and marks its centre. */
	public static final Block CRUZEIRO = register("cruzeiro",
			new ArraialBlock(props("cruzeiro")
					.mapColor(MapColor.WOOD)
					.strength(2.0F)
					.sound(SoundType.WOOD)
					.noOcclusion()));

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

	public static void init() {
	}

	private ModBlocks() {
	}
}
