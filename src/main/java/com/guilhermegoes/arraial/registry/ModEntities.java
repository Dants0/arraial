package com.guilhermegoes.arraial.registry;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.npc.MoradorEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	private static final ResourceKey<EntityType<?>> MORADOR_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, Arraial.id("morador"));

	public static final EntityType<MoradorEntity> MORADOR = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			MORADOR_KEY,
			EntityType.Builder.of(MoradorEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F)
					.eyeHeight(1.62F)
					.clientTrackingRange(10)
					.build(MORADOR_KEY));

	// ---- The Brazilian fauna (see the fauna package)
	public static final EntityType<com.guilhermegoes.arraial.fauna.Capivara> CAPIVARA = bicho("capivara",
			EntityType.Builder.of(com.guilhermegoes.arraial.fauna.Capivara::new, MobCategory.CREATURE).sized(0.9F, 0.9F).clientTrackingRange(10));
	public static final EntityType<com.guilhermegoes.arraial.fauna.Tamandua> TAMANDUA = bicho("tamandua",
			EntityType.Builder.of(com.guilhermegoes.arraial.fauna.Tamandua::new, MobCategory.CREATURE).sized(0.9F, 1.0F).clientTrackingRange(10));
	public static final EntityType<com.guilhermegoes.arraial.fauna.Jacare> JACARE = bicho("jacare",
			EntityType.Builder.of(com.guilhermegoes.arraial.fauna.Jacare::new, MobCategory.CREATURE).sized(1.2F, 0.5F).clientTrackingRange(10));
	public static final EntityType<com.guilhermegoes.arraial.fauna.Onca> ONCA = bicho("onca",
			EntityType.Builder.of(com.guilhermegoes.arraial.fauna.Onca::new, MobCategory.CREATURE).sized(1.0F, 1.0F).clientTrackingRange(10));
	public static final EntityType<com.guilhermegoes.arraial.fauna.Arara> ARARA = bicho("arara",
			EntityType.Builder.of(com.guilhermegoes.arraial.fauna.Arara::new, MobCategory.CREATURE).sized(0.7F, 1.2F).clientTrackingRange(8));

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> bicho(String nome, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Arraial.id(nome));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(MORADOR, MoradorEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(CAPIVARA, com.guilhermegoes.arraial.fauna.Capivara.atributos());
		FabricDefaultAttributeRegistry.register(TAMANDUA, com.guilhermegoes.arraial.fauna.Tamandua.atributos());
		FabricDefaultAttributeRegistry.register(JACARE, com.guilhermegoes.arraial.fauna.Jacare.atributos());
		FabricDefaultAttributeRegistry.register(ONCA, com.guilhermegoes.arraial.fauna.Onca.atributos());
		FabricDefaultAttributeRegistry.register(ARARA, net.minecraft.world.entity.animal.parrot.Parrot.createAttributes());
		com.guilhermegoes.arraial.fauna.Fauna.init();
	}

	private ModEntities() {
	}
}
