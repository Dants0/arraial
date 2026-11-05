package com.guilhermegoes.arraial.trabalho;

import com.guilhermegoes.arraial.registry.ModItems;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

/** Wild cassava: now and then, cutting grass turns up a maniva to plant. */
public final class Mato {
	public static void init() {
		LootTableEvents.MODIFY.register((chave, tabela, fonte, registros) -> {
			String path = chave.identifier().getPath();
			if (fonte.isBuiltin() && (path.equals("blocks/short_grass") || path.equals("blocks/tall_grass") || path.equals("blocks/fern"))) {
				tabela.withPool(LootPool.lootPool()
						.add(LootItem.lootTableItem(ModItems.MANIVA))
						.when(LootItemRandomChanceCondition.randomChance(0.05F)));
			}
		});
	}

	private Mato() {
	}
}
