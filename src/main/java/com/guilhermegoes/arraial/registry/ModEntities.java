package com.guilhermegoes.arraial.registry;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.npc.PedreiroEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	private static final ResourceKey<EntityType<?>> PEDREIRO_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, Arraial.id("pedreiro"));

	public static final EntityType<PedreiroEntity> PEDREIRO = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			PEDREIRO_KEY,
			EntityType.Builder.of(PedreiroEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F)
					.eyeHeight(1.62F)
					.clientTrackingRange(10)
					.build(PEDREIRO_KEY));

	public static void init() {
		FabricDefaultAttributeRegistry.register(PEDREIRO, PedreiroEntity.createAttributes());
	}

	private ModEntities() {
	}
}
