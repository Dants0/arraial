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
	public boolean canSitOnShoulder() {
		return false;
	}
}
