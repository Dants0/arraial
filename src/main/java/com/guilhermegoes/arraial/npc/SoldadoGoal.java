package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.vila.Vilas;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The soldado never sleeps on duty: day and night he walks rounds between the Cruzeiro,
 * his guard post and random points of the arraial. The combat itself is in the nested goals.
 */
public class SoldadoGoal extends Goal {
	private final MoradorEntity soldado;
	private @Nullable BlockPos destino;
	private int proxima;
	private int parado;

	public SoldadoGoal(MoradorEntity soldado) {
		this.soldado = soldado;
		setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (soldado.profissao() != Profissao.SOLDADO || soldado.vila() == null || soldado.getTarget() != null) {
			return false;
		}
		if (--proxima > 0) {
			return false;
		}
		proxima = 40 + soldado.getRandom().nextInt(80);
		destino = escolherPonto();
		return destino != null;
	}

	@Override
	public boolean canContinueToUse() {
		return destino != null && soldado.getTarget() == null && !soldado.getNavigation().isDone() && parado < 200;
	}

	@Override
	public void start() {
		parado = 0;
		BlockPos d = destino;
		if (d != null) {
			soldado.getNavigation().moveTo(d.getX() + 0.5, d.getY(), d.getZ() + 0.5, 0.7);
			soldado.setStatus(Component.translatable(getServerLevel(soldado).isDarkOutside() ? "arraial.soldado.vigiando" : "arraial.soldado.ronda"));
		}
	}

	@Override
	public void tick() {
		parado++;
	}

	@Override
	public void stop() {
		destino = null;
	}

	private @Nullable BlockPos escolherPonto() {
		ServerLevel level = getServerLevel(soldado);
		BlockPos vila = soldado.vila();
		if (vila == null) {
			return null;
		}
		int sorte = soldado.getRandom().nextInt(4);
		if (sorte == 0) {
			return vila;
		}
		if (sorte == 1 && soldado.posto() != null) {
			return soldado.posto();
		}
		int raio = Vilas.RAIO - 8;
		BlockPos alvo = vila.offset(soldado.getRandom().nextInt(raio * 2) - raio, 0, soldado.getRandom().nextInt(raio * 2) - raio);
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, alvo);
	}

	/** Only soldados go for the throat. */
	public static class Atacar extends MeleeAttackGoal {
		private final MoradorEntity soldado;

		public Atacar(MoradorEntity soldado) {
			super(soldado, 1.15, true);
			this.soldado = soldado;
		}

		@Override
		public boolean canUse() {
			return soldado.profissao() == Profissao.SOLDADO && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return soldado.profissao() == Profissao.SOLDADO && super.canContinueToUse();
		}

		@Override
		public void start() {
			super.start();
			soldado.setStatus(Component.translatable("arraial.soldado.combate"));
		}
	}

	/** Hunts monsters inside the arraial (not creepers: those are best left alone). */
	public static class Cacar extends NearestAttackableTargetGoal<Monster> {
		private final MoradorEntity soldado;

		public Cacar(MoradorEntity soldado) {
			super(soldado, Monster.class, 5, true, false, (alvo, level) -> dentroDaVila(soldado, alvo) && !(alvo instanceof Creeper));
			this.soldado = soldado;
		}

		@Override
		public boolean canUse() {
			return soldado.profissao() == Profissao.SOLDADO && super.canUse();
		}
	}

	/** Strikes back at whoever hits him. */
	public static class Revidar extends HurtByTargetGoal {
		private final MoradorEntity soldado;

		public Revidar(MoradorEntity soldado) {
			super(soldado, MoradorEntity.class);
			this.soldado = soldado;
		}

		@Override
		public boolean canUse() {
			return soldado.profissao() == Profissao.SOLDADO && super.canUse();
		}
	}

	static boolean dentroDaVila(MoradorEntity soldado, LivingEntity alvo) {
		BlockPos vila = soldado.vila();
		return vila != null && alvo.blockPosition().closerThan(vila, Vilas.RAIO + 8);
	}
}
