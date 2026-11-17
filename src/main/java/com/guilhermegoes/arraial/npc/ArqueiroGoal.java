package com.guilhermegoes.arraial.npc;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The arqueiro keeps watch from the torre de vigia, day and night. With a target (the same ones as
 * the soldados: monsters in the arraial, bandits, the prowling onça) he shoots from where he stands
 * if he can see it, or comes closer until he can; with none, he climbs back to his post. His arrows
 * cannot be picked up (no free arrows from the tower).
 */
public class ArqueiroGoal extends Goal {
	private static final double ALCANCE = 24.0;
	private static final int RECARGA = 25;

	private final MoradorEntity arqueiro;
	private int recarga;
	private int caminhar;

	public ArqueiroGoal(MoradorEntity arqueiro) {
		this.arqueiro = arqueiro;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return arqueiro.profissao() == Profissao.ARQUEIRO && arqueiro.vila() != null && arqueiro.posto() != null && !arqueiro.isSleeping();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		arqueiro.segurar(new ItemStack(Items.BOW));
	}

	@Override
	public void tick() {
		LivingEntity alvo = arqueiro.getTarget();
		if (alvo != null && alvo.isAlive()) {
			atirar(alvo);
			return;
		}
		// No danger: back up to the watch post and look out over the arraial.
		BlockPos posto = arqueiro.posto();
		if (posto == null) {
			return;
		}
		if (arqueiro.distanceToSqr(posto.getX() + 0.5, posto.getY(), posto.getZ() + 0.5) > 2.5 * 2.5) {
			arqueiro.setStatus(Component.translatable("arraial.arqueiro.subindo"));
			if (--caminhar <= 0) {
				caminhar = Porteira.caminhar(arqueiro, posto, 0.8);
			}
		} else {
			arqueiro.getNavigation().stop();
			arqueiro.setStatus(Component.translatable("arraial.arqueiro.vigiando"));
			if (arqueiro.tickCount % 60 == 0) {
				arqueiro.getLookControl().setLookAt(arqueiro.getX() + arqueiro.getRandom().nextInt(21) - 10, arqueiro.getEyeY(),
						arqueiro.getZ() + arqueiro.getRandom().nextInt(21) - 10);
			}
		}
	}

	private void atirar(LivingEntity alvo) {
		arqueiro.setStatus(Component.translatable("arraial.soldado.combate"));
		arqueiro.getLookControl().setLookAt(alvo, 30.0F, 30.0F);
		double d = arqueiro.distanceTo(alvo);
		boolean ve = arqueiro.getSensing().hasLineOfSight(alvo);
		if (d > ALCANCE || !ve) {
			if (--caminhar <= 0) {
				arqueiro.getNavigation().moveTo(alvo, 0.9);
				caminhar = 20;
			}
			return;
		}
		arqueiro.getNavigation().stop();
		if (--recarga > 0 || !(arqueiro.level() instanceof ServerLevel level)) {
			return;
		}
		recarga = RECARGA;
		Arrow flecha = new Arrow(level, arqueiro, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
		flecha.pickup = AbstractArrow.Pickup.DISALLOWED;
		flecha.setBaseDamage(2.5);
		double dx = alvo.getX() - arqueiro.getX();
		double dy = alvo.getY(0.33) - flecha.getY();
		double dz = alvo.getZ() - arqueiro.getZ();
		double h = Math.sqrt(dx * dx + dz * dz);
		flecha.shoot(dx, dy + h * 0.2, dz, 1.6F, 2.0F);
		level.addFreshEntity(flecha);
		arqueiro.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
		level.playSound(null, arqueiro.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0F, 0.9F + arqueiro.getRandom().nextFloat() * 0.2F);
	}
}
