package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.vila.ArraialBlockEntity;
import com.guilhermegoes.arraial.vila.Crise;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** "Fogo!": in an incêndio, every morador nearby drops what they are doing and runs with a bucket. */
public class ApagarFogoGoal extends Goal {
	private final MoradorEntity morador;
	private @Nullable BlockPos chama;
	private int recalcular;

	public ApagarFogoGoal(MoradorEntity morador) {
		this.morador = morador;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	private @Nullable BlockPos fogo() {
		ArraialBlockEntity a = morador.arraial();
		return a == null ? null : a.crise().fogo();
	}

	@Override
	public boolean canUse() {
		if (morador.isSleeping() || morador.viajando() || (morador.tickCount + morador.getId()) % 20 != 0) {
			return false;
		}
		BlockPos fogo = fogo();
		if (fogo == null || morador.blockPosition().distSqr(fogo) > 40 * 40) {
			return false;
		}
		chama = maisPerto(Crise.chamas(getServerLevel(morador), fogo));
		return chama != null;
	}

	private @Nullable BlockPos maisPerto(List<BlockPos> chamas) {
		BlockPos melhor = null;
		double d = Double.MAX_VALUE;
		for (BlockPos p : chamas) {
			double x = p.distSqr(morador.blockPosition());
			if (x < d) {
				d = x;
				melhor = p;
			}
		}
		return melhor;
	}

	@Override
	public boolean canContinueToUse() {
		return chama != null && !morador.isSleeping();
	}

	@Override
	public void start() {
		morador.segurar(new ItemStack(Items.WATER_BUCKET));
		morador.setStatus(Component.translatable("arraial.morador.apagando"));
		if (morador.getRandom().nextInt(3) == 0) {
			morador.dizer(Component.translatable("arraial.fala.fogo." + morador.getRandom().nextInt(2)), 40);
		}
	}

	@Override
	public void stop() {
		morador.segurar(ItemStack.EMPTY);
		morador.setStatus(Component.empty());
		chama = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		ServerLevel level = getServerLevel(morador);
		if (chama == null) {
			return;
		}
		if (!(level.getBlockState(chama).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock)) {
			BlockPos fogo = fogo();
			chama = fogo == null ? null : maisPerto(Crise.chamas(level, fogo));
			return;
		}
		if (morador.position().distanceTo(Vec3.atCenterOf(chama)) > 2.8) {
			if (--recalcular <= 0) {
				recalcular = 10;
				morador.getNavigation().moveTo(chama.getX() + 0.5, chama.getY(), chama.getZ() + 0.5, 1.2);
			}
			return;
		}
		morador.getLookControl().setLookAt(Vec3.atCenterOf(chama));
		morador.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
		level.setBlock(chama, Blocks.AIR.defaultBlockState(), 3);
		level.playSound(null, chama, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 1.0F);
		level.sendParticles(ParticleTypes.SPLASH, chama.getX() + 0.5, chama.getY() + 0.5, chama.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.1);
		level.sendParticles(ParticleTypes.CLOUD, chama.getX() + 0.5, chama.getY() + 0.5, chama.getZ() + 0.5, 6, 0.2, 0.3, 0.2, 0.02);
		if (morador.vila() != null) {
			Crise.apagou(level, morador.vila());
		}
		BlockPos fogo = fogo();
		chama = fogo == null ? null : maisPerto(Crise.chamas(level, fogo));
	}
}
