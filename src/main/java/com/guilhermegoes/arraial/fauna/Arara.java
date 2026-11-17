package com.guilhermegoes.arraial.fauna;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.level.Level;

/**
 * Arara: the macaws of the forest and the savanna. They fly, perch and dance like parrots (and like
 * them can be tamed with seeds), but they are too big to ride on a shoulder. The parrot colour
 * variant picks the species: vermelha, canindé or azul (see the client AraraRenderer).
 */
public class Arara extends Parrot {
	public Arara(EntityType<? extends Arara> type, Level level) {
		super(type, level);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		// Now and then a feather falls (about every seven minutes).
		if (level() instanceof net.minecraft.server.level.ServerLevel server && !isBaby() && getRandom().nextInt(8400) == 0) {
			spawnAtLocation(server, com.guilhermegoes.arraial.registry.ModItems.PENA_ARARA);
		}
	}

	@Override
	public boolean canSitOnShoulder() {
		return false;
	}
}
