package com.guilhermegoes.arraial.registry;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import com.guilhermegoes.arraial.vila.ArraialBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<CanteiroBlockEntity> CANTEIRO = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Arraial.id("canteiro"),
			FabricBlockEntityTypeBuilder.create(CanteiroBlockEntity::new, ModBlocks.CANTEIRO).build());

	public static final BlockEntityType<ArraialBlockEntity> ARRAIAL = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Arraial.id("arraial"),
			FabricBlockEntityTypeBuilder.create(ArraialBlockEntity::new, ModBlocks.CRUZEIRO).build());

	public static void init() {
	}

	private ModBlockEntities() {
	}
}
