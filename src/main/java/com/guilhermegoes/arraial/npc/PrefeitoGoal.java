package com.guilhermegoes.arraial.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Once the arraial has a Prefeitura, the prefeito governs full time: by day he keeps office at
 * the lectern, receiving people and looking over the papers, and goes home at night.
 */
public class PrefeitoGoal extends OficioGoal {
	private int folga;

	public PrefeitoGoal(MoradorEntity morador) {
		super(morador, Profissao.NENHUMA);
	}

	private boolean expediente() {
		return morador.prefeito() && morador.gabinete() != null && !morador.comFome() && level().isBrightOutside();
	}

	@Override
	public boolean canUse() {
		return hora() && expediente();
	}

	@Override
	public boolean canContinueToUse() {
		return expediente();
	}

	@Override
	public void start() {
		folga = 0;
	}

	@Override
	public void tick() {
		BlockPos mesa = morador.gabinete();
		if (mesa == null) {
			return;
		}
		morador.setStatus(Component.translatable("arraial.prefeito.gabinete"));
		if (!perto(mesa, 2.5)) {
			andarAte(mesa);
			return;
		}
		morador.getNavigation().stop();
		// Now the papers, now a look around the room.
		if (--folga <= 0) {
			folga = 60 + morador.getRandom().nextInt(80);
			if (morador.getRandom().nextBoolean()) {
				bater();
			}
		}
		if (folga > 40) {
			morador.getLookControl().setLookAt(Vec3.atCenterOf(mesa));
		}
	}
}
