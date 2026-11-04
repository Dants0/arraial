package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.Arraial;
import java.lang.reflect.Field;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.raid.Raider;

/**
 * The night is dangerous for moradores too: zombies, skeletons, spiders and raiders now hunt
 * them like they hunt villagers. (Hooked when those mobs are loaded into the world.)
 */
public final class Ameacas {
	private static Field alvos;

	public static void init() {
		try {
			alvos = Mob.class.getDeclaredField("targetSelector");
			alvos.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			Arraial.LOGGER.error("Arraial: não consegui ensinar os monstros a atacar moradores", e);
			return;
		}
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && cacaMoradores(mob)) {
				try {
					GoalSelector selector = (GoalSelector) alvos.get(mob);
					selector.addGoal(3, new NearestAttackableTargetGoal<>(mob, MoradorEntity.class, true));
				} catch (IllegalAccessException e) {
					Arraial.LOGGER.warn("Arraial: falhou ao configurar {}", mob, e);
				}
			}
		});
	}

	private static boolean cacaMoradores(Mob mob) {
		if (mob instanceof ZombifiedPiglin) {
			return false;
		}
		return mob instanceof Zombie || mob instanceof AbstractSkeleton || mob instanceof Spider
				|| mob instanceof Raider || mob instanceof Vex;
	}

	private Ameacas() {
	}
}
