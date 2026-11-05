package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * Achievements (the "Arraial" advancement tab), the loot of bandits and hideouts, the mod's goods
 * in the chests of vanilla structures, and the vaqueiro set bonus.
 * <p>Most achievements are about things the mod does (a festa, a treaty, a cleared hideout); they
 * use an impossible criterion "feito" and are granted from code with {@link #dar}.
 */
public final class Conquistas {
	public static final ResourceKey<LootTable> BAU_ESCONDERIJO = ResourceKey.create(Registries.LOOT_TABLE, Arraial.id("chests/esconderijo"));

	public static void dar(ServerPlayer p, String id) {
		var h = p.level().getServer().getAdvancements().get(Arraial.id(id));
		if (h != null) {
			p.getAdvancements().award(h, "feito");
		}
	}

	public static void darPerto(ServerLevel level, BlockPos c, int raio, String id) {
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().closerThan(c, raio)) {
				dar(p, id);
			}
		}
	}

	public static void init() {
		// Bandits and highwaymen carry a sack of loot.
		ServerLivingEntityEvents.AFTER_DEATH.register((morto, fonte) -> {
			if (!(morto.level() instanceof ServerLevel level)) {
				return;
			}
			var tags = morto.entityTags();
			if (tags.contains(Ruinas.TAG) || tags.contains(Crise.TAG)) {
				int n = 1 + (level.getRandom().nextBoolean() ? 1 : 0);
				Block.popResource(level, morto.blockPosition(), new ItemStack(ModItems.SACO_SAQUE, n));
				if (fonte.getEntity() instanceof ServerPlayer p) {
					dar(p, "cacador_de_bandidos");
				}
			}
		});
		// The vaqueiro's full leather: quick on their feet.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 100 != 0) {
				return;
			}
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.CHAPEU_COURO) && p.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.GIBAO)
						&& p.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.PERNEIRA) && p.getItemBySlot(EquipmentSlot.FEET).is(ModItems.ALPERCATAS)) {
					p.addEffect(new MobEffectInstance(MobEffects.SPEED, 140, 0, true, false, true));
					dar(p, "vaqueiro");
				}
			}
		});
		// The mod's goods turn up in the world's chests.
		LootTableEvents.MODIFY.register((chave, tabela, fonte, registros) -> {
			if (!fonte.isBuiltin()) {
				return;
			}
			String path = chave.identifier().getPath();
			if (path.startsWith("chests/village/village_") && path.endsWith("_house")) {
				tabela.withPool(pool(ModItems.MANIVA, 2, 5, 0.5F));
				tabela.withPool(pool(ModItems.RAPADURA, 1, 3, 0.35F));
				tabela.withPool(pool(ModItems.REIS, 2, 8, 0.4F));
			} else if (path.equals("chests/desert_pyramid")) {
				tabela.withPool(pool(ModItems.PEIXEIRA, 1, 1, 0.15F));
				tabela.withPool(pool(ModItems.CANTIL, 1, 1, 0.3F));
			} else if (path.equals("chests/shipwreck_supply")) {
				tabela.withPool(pool(ModItems.CANTIL, 1, 1, 0.25F));
				tabela.withPool(pool(ModItems.FARINHA, 2, 6, 0.4F));
			} else if (path.equals("chests/pillager_outpost")) {
				tabela.withPool(pool(ModItems.FACAO, 1, 1, 0.25F));
				tabela.withPool(pool(ModItems.SACO_SAQUE, 1, 2, 0.5F));
			} else if (path.equals("chests/abandoned_mineshaft")) {
				tabela.withPool(pool(ModItems.FOICE, 1, 1, 0.1F));
			}
		});
	}

	private static LootPool.Builder pool(Item item, int min, int max, float chance) {
		return LootPool.lootPool()
				.add(LootItem.lootTableItem(item).apply(SetItemCountFunction.setCount(ContextIntProviders.between(min, max))))
				.when(LootItemRandomChanceCondition.randomChance(chance));
	}

	/** A chest with the bandits' loot near a hideout (an empty arraial or a taken base). */
	static void bau(ServerLevel level, BlockPos c) {
		for (int r = 2; r <= 5; r++) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.relative(d, r).relative(d.getCounterClockWise(), 1));
				if (Math.abs(p.getY() - c.getY()) > 3 || !Festa.livre(level, p)) {
					continue;
				}
				level.setBlock(p, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
				if (level.getBlockEntity(p) instanceof ChestBlockEntity bau) {
					bau.setLootTable(BAU_ESCONDERIJO);
					bau.setLootTableSeed(level.getRandom().nextLong());
				}
				return;
			}
		}
	}

	private Conquistas() {
	}
}
