package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.registry.ModPois;
import com.guilhermegoes.arraial.vila.ArraialBlockEntity;
import com.guilhermegoes.arraial.vila.Calendario;
import com.guilhermegoes.arraial.vila.Festa;
import com.guilhermegoes.arraial.vila.Vilas;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import org.jspecify.annotations.Nullable;

/**
 * A festa is on (see {@link Festa}): the morador drops work and celebrates. São João: the quadrilha
 * in a ring around the fogueira, with jumps and the caller's shouts. Padroeiro: a slow procession
 * around the Cruzeiro. Feira Grande: strolling from banca to banca. Soldiers keep watch instead.
 */
public class FestejarGoal extends Goal {
	private final MoradorEntity morador;
	private int checar;
	private double angulo;
	private int t;
	private @Nullable BlockPos banca;

	public FestejarGoal(MoradorEntity morador) {
		this.morador = morador;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	private ServerLevel level() {
		return getServerLevel(morador);
	}

	private Festa.@Nullable Tipo festa() {
		ArraialBlockEntity a = morador.arraial();
		return a == null ? null : a.festa().agora();
	}

	private boolean pode() {
		return festa() != null && !morador.isSleeping() && !morador.viajando() && !morador.doente()
				&& morador.profissao() != Profissao.SOLDADO && level().isBrightOutside();
	}

	@Override
	public boolean canUse() {
		if (--checar > 0) {
			return false;
		}
		checar = 20 + morador.getRandom().nextInt(20);
		return pode();
	}

	@Override
	public boolean canContinueToUse() {
		return pode();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		t = 0;
		banca = null;
		ArraialBlockEntity a = morador.arraial();
		if (a != null) {
			BlockPos c = a.festa().centro(a);
			angulo = Math.atan2(morador.getZ() - c.getZ() - 0.5, morador.getX() - c.getX() - 0.5);
		}
	}

	@Override
	public void stop() {
		morador.getNavigation().stop();
		banca = null;
	}

	@Override
	public void tick() {
		ArraialBlockEntity a = morador.arraial();
		Festa.Tipo tipo = festa();
		if (a == null || tipo == null) {
			return;
		}
		t++;
		BlockPos c = a.festa().centro(a);
		switch (tipo) {
			case SAO_JOAO -> roda(c, 3.5, 0.012, 0.55, true);
			case PADROEIRO -> roda(c, 7.0, 0.005, 0.4, false);
			case FEIRA_GRANDE -> feira(c);
		}
		if (t % 100 == 0 && morador.getRandom().nextFloat() < 0.15F) {
			morador.dizer(fala(tipo, a), 60);
		}
	}

	/** Walks around a ring, a little ahead of where they are; dancers face the fire. */
	private void roda(BlockPos c, double raio, double passo, double vel, boolean danca) {
		double cx = c.getX() + 0.5, cz = c.getZ() + 0.5;
		double dist = Math.sqrt(morador.distanceToSqr(cx, morador.getY(), cz));
		if (Math.abs(dist - raio) < 1.5) {
			angulo += passo;
		}
		double alvoX = cx + Math.cos(angulo + 0.4) * raio, alvoZ = cz + Math.sin(angulo + 0.4) * raio;
		if (t % 10 == 1) {
			morador.getNavigation().moveTo(alvoX, c.getY(), alvoZ, vel);
		}
		if (danca) {
			morador.getLookControl().setLookAt(cx, c.getY() + 0.5, cz);
			if (dist < raio + 1.5 && morador.getRandom().nextInt(60) == 0) {
				morador.getJumpControl().jump();
			}
		} else {
			morador.getLookControl().setLookAt(alvoX, morador.getEyeY(), alvoZ);
		}
	}

	/** Strolls between the market stalls, stopping at each for a while. */
	private void feira(BlockPos c) {
		if (banca == null || t % 300 == 0) {
			List<BlockPos> bancas = level().getPoiManager().getInRange(h -> h.value() == ModPois.BANCA_FEIRA, c, Vilas.RAIO,
					PoiManager.Occupancy.ANY).map(PoiRecord::getPos).toList();
			banca = bancas.isEmpty() ? c : bancas.get(morador.getRandom().nextInt(bancas.size()));
			BlockPos perto = banca.offset(morador.getRandom().nextInt(5) - 2, 0, morador.getRandom().nextInt(5) - 2);
			morador.getNavigation().moveTo(perto.getX() + 0.5, perto.getY(), perto.getZ() + 0.5, 0.5);
		}
		if (morador.distanceToSqr(banca.getX() + 0.5, banca.getY(), banca.getZ() + 0.5) < 4 * 4) {
			morador.getLookControl().setLookAt(banca.getX() + 0.5, banca.getY() + 0.5, banca.getZ() + 0.5);
		}
	}

	private Component fala(Festa.Tipo tipo, ArraialBlockEntity a) {
		return switch (tipo) {
			case SAO_JOAO -> Component.translatable("arraial.fala.quadrilha." + morador.getRandom().nextInt(6));
			case PADROEIRO -> Component.translatable("arraial.fala.procissao." + morador.getRandom().nextInt(3), Calendario.padroeiro(a.nome()));
			case FEIRA_GRANDE -> Component.translatable("arraial.fala.feira_grande." + morador.getRandom().nextInt(3));
		};
	}
}
