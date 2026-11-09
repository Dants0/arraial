package com.guilhermegoes.arraial.fauna;

import com.guilhermegoes.arraial.registry.ModEntities;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Where the Brazilian fauna lives: capivaras by rivers, lakes and swamps of warm lands; the
 * tamanduá in savannas and plains; the jacaré in swamps and on riverbanks; the onça in jungles
 * (and, rarely, savannas); araras in jungles and savannas.
 */
public final class Fauna {
	public static void init() {
		SpawnPlacements.register(ModEntities.CAPIVARA, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
		SpawnPlacements.register(ModEntities.TAMANDUA, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
		SpawnPlacements.register(ModEntities.ONCA, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
		// The jacaré: on grass or mud, with water close by.
		SpawnPlacements.register(ModEntities.JACARE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				(type, level, reason, pos, random) -> {
					var chao = level.getBlockState(pos.below());
					if (!(chao.is(BlockTags.ANIMALS_SPAWNABLE_ON) || chao.is(BlockTags.SUBSTRATE_OVERWORLD))) {
						return false;
					}
					for (BlockPos q : BlockPos.betweenClosed(pos.offset(-3, -2, -3), pos.offset(3, 0, 3))) {
						if (level.getFluidState(q).is(FluidTags.WATER)) {
							return true;
						}
					}
					return false;
				});
		// Araras: on leaves or grass in daylight, like parrots.
		SpawnPlacements.register(ModEntities.ARARA, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING,
				(type, level, reason, pos, random) -> level.getBlockState(pos.below()).is(BlockTags.PARROTS_SPAWNABLE_ON)
						&& level.getRawBrightness(pos, 0) > 8);

		var quente = BiomeSelectors.tag(ConventionalBiomeTags.IS_JUNGLE).or(BiomeSelectors.tag(ConventionalBiomeTags.IS_SAVANNA));
		BiomeModifications.addSpawn(quente.or(BiomeSelectors.tag(ConventionalBiomeTags.IS_SWAMP)).or(BiomeSelectors.tag(ConventionalBiomeTags.IS_RIVER)),
				MobCategory.CREATURE, ModEntities.CAPIVARA, 8, 3, 6);
		BiomeModifications.addSpawn(BiomeSelectors.tag(ConventionalBiomeTags.IS_SAVANNA).or(BiomeSelectors.tag(ConventionalBiomeTags.IS_PLAINS)),
				MobCategory.CREATURE, ModEntities.TAMANDUA, 3, 1, 1);
		BiomeModifications.addSpawn(BiomeSelectors.tag(ConventionalBiomeTags.IS_SWAMP).or(BiomeSelectors.tag(ConventionalBiomeTags.IS_RIVER)),
				MobCategory.CREATURE, ModEntities.JACARE, 4, 1, 2);
		BiomeModifications.addSpawn(BiomeSelectors.tag(ConventionalBiomeTags.IS_JUNGLE), MobCategory.CREATURE, ModEntities.ONCA, 3, 1, 1);
		BiomeModifications.addSpawn(BiomeSelectors.tag(ConventionalBiomeTags.IS_SAVANNA), MobCategory.CREATURE, ModEntities.ONCA, 1, 1, 1);
		BiomeModifications.addSpawn(quente, MobCategory.CREATURE, ModEntities.ARARA, 10, 2, 4);
	}

	private Fauna() {
	}
}
