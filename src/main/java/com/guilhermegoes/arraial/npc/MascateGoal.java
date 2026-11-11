package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.vila.Caravana;
import com.guilhermegoes.arraial.vila.Comercio;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The mascate's day (see {@link Comercio}): he hangs around the Cruzeiro crying his wares, and at
 * dusk (or when his time is up) walks off toward home and is gone, taking the unsold goods and the
 * money back to his arraial.
 */
public class MascateGoal extends Goal {
	private final MoradorEntity m;
	private int t;
	private int saindo;

	public MascateGoal(MoradorEntity m) {
		this.m = m;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return m.profissao() == Profissao.MASCATE && m.mascateVila() != null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		ServerLevel level = getServerLevel(m);
		BlockPos vila = m.mascateVila();
		if (vila == null) {
			return;
		}
		t++;
		if (com.guilhermegoes.arraial.vila.Navio.ORIGEM.equals(m.mascateOrigem())) {
			// The ship's captain keeps to the quay; he boards when the ship sails (Navio.partir).
			if (level.getGameTime() >= m.mascateFim() + 1200) {
				Comercio.irEmbora(level, m);
				return;
			}
			if (t % 160 == 1) {
				BlockPos alvo = vila.offset(m.getRandom().nextInt(5) - 2, 0, m.getRandom().nextInt(5) - 2);
				m.getNavigation().moveTo(alvo.getX() + 0.5, alvo.getY(), alvo.getZ() + 0.5, 0.4);
			}
			if (t % 300 == 0 && m.getRandom().nextFloat() < 0.5F) {
				m.dizer(Component.translatable("arraial.fala.capitao." + m.getRandom().nextInt(3)), 60);
			}
			return;
		}
		if (level.getGameTime() >= m.mascateFim() || level.isDarkOutside()) {
			saindo++;
			BlockPos borda = Caravana.borda(level, vila, m.mascateOrigem());
			if (saindo % 40 == 1) {
				m.getNavigation().moveTo(borda.getX() + 0.5, borda.getY(), borda.getZ() + 0.5, 0.6);
			}
			if (saindo > 1200 || m.distanceToSqr(borda.getX() + 0.5, borda.getY(), borda.getZ() + 0.5) < 9) {
				Comercio.irEmbora(level, m);
			}
			return;
		}
		if (t % 200 == 1) {
			BlockPos alvo = vila.offset(m.getRandom().nextInt(11) - 5, 0, m.getRandom().nextInt(11) - 5);
			m.getNavigation().moveTo(alvo.getX() + 0.5, alvo.getY(), alvo.getZ() + 0.5, 0.45);
		}
		if (t % 300 == 0 && m.getRandom().nextFloat() < 0.5F) {
			m.dizer(Component.translatable("arraial.fala.mascate." + m.getRandom().nextInt(3), Comercio.origemNome(m)), 60);
		}
	}
}
