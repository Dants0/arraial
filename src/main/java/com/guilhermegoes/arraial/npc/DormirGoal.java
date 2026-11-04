package com.guilhermegoes.arraial.npc;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** At night the morador walks home and sleeps in the bed he claimed; he wakes up at dawn. */
public class DormirGoal extends Goal {
	private final MoradorEntity morador;
	private int tentando;
	private int recalcular;

	public DormirGoal(MoradorEntity morador) {
		this.morador = morador;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		return getServerLevel(morador).isDarkOutside() && camaValida() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		tentando = 0;
		recalcular = 0;
		morador.segurar(net.minecraft.world.item.ItemStack.EMPTY);
		morador.setStatus(Component.translatable("arraial.morador.indo_dormir"));
	}

	@Override
	public void stop() {
		if (morador.isSleeping()) {
			morador.stopSleeping();
		}
		morador.setStatus(Component.empty());
	}

	@Override
	public void tick() {
		BlockPos cama = camaValida();
		if (cama == null || morador.isSleeping()) {
			return;
		}
		double dist = morador.position().distanceTo(Vec3.atBottomCenterOf(cama));
		if (dist <= 1.8 || ++tentando > 600) {
			// Close enough (or 30 s stuck on the way): get into bed.
			morador.getNavigation().stop();
			if (!estaOcupada(cama)) {
				morador.startSleeping(cama);
				morador.setStatus(Component.translatable("arraial.morador.dormindo"));
			}
			return;
		}
		if (--recalcular <= 0 || morador.getNavigation().isDone()) {
			recalcular = 20;
			morador.getNavigation().moveTo(cama.getX() + 0.5, cama.getY(), cama.getZ() + 0.5, 1, 0.8);
		}
	}

	private @Nullable BlockPos camaValida() {
		BlockPos casa = morador.casa();
		if (casa == null) {
			return null;
		}
		ServerLevel level = getServerLevel(morador);
		return level.isLoaded(casa) && level.getBlockState(casa).is(BlockTags.BEDS) ? casa : null;
	}

	private boolean estaOcupada(BlockPos cama) {
		BlockState state = getServerLevel(morador).getBlockState(cama);
		return state.hasProperty(BedBlock.OCCUPIED) && state.getValue(BedBlock.OCCUPIED);
	}
}
