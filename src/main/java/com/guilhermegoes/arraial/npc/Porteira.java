package com.guilhermegoes.arraial.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Fence gates for moradores. Mob pathfinding treats a closed gate as a fence, so a pastor could
 * never get into his own curral. When a walk cannot reach its destination, the morador heads for
 * a gate near it, opens it by hand, goes through, and closes it behind him (and behind the
 * animals he leads) so the herd stays in.
 */
final class Porteira {
	private static final int RAIO = 8;

	/**
	 * Walks {@code quem} towards {@code pos}, going through a gate when the way is fenced off (a
	 * curral, the arraial's wall). Returns how many ticks to wait before calling again.
	 */
	static int caminhar(MoradorEntity quem, BlockPos pos, double velocidade) {
		if (!(quem.level() instanceof ServerLevel level)) {
			return 20;
		}
		var nav = quem.getNavigation();
		int espera = 20;
		net.minecraft.world.level.pathfinder.Path caminho = nav.createPath(pos, 1);
		if (caminho != null && !caminho.canReach()) {
			BlockPos portao = procurar(level, pos, quem.blockPosition());
			if (portao == null) {
				portao = procurar(level, quem.blockPosition(), quem.blockPosition()); // fenced in, going out
			}
			if (portao == null) {
				portao = daMuralha(level, quem.vila(), quem.blockPosition()); // the arraial's wall
			}
			if (portao != null) {
				if (quem.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(portao)) <= 2.2) {
					abrir(level, portao, quem);
					caminho = nav.createPath(pos, 1);
					espera = 5;
				} else {
					caminho = nav.createPath(portao, 1);
					espera = 10;
				}
			}
		}
		if (caminho != null) {
			nav.moveTo(caminho, velocidade);
		} else {
			nav.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1, velocidade);
		}
		return espera;
	}

	/** The closed gate nearest to {@code origem} around {@code destino}, or null. */
	static @Nullable BlockPos procurar(ServerLevel level, BlockPos destino, BlockPos origem) {
		BlockPos melhor = null;
		double d = 24 * 24;
		for (BlockPos p : BlockPos.betweenClosed(destino.offset(-RAIO, -3, -RAIO), destino.offset(RAIO, 3, RAIO))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof FenceGateBlock && !s.getValue(FenceGateBlock.OPEN)) {
				double x = p.distSqr(origem);
				if (x < d) {
					d = x;
					melhor = p.immutable();
				}
			}
		}
		return melhor;
	}

	/** The closed gate of the arraial's wall (paliçada) nearest to {@code origem}, or null. */
	static @Nullable BlockPos daMuralha(ServerLevel level, @Nullable BlockPos vila, BlockPos origem) {
		if (vila == null) {
			return null;
		}
		BlockPos melhor = null;
		double d = Double.MAX_VALUE;
		for (BlockPos p : com.guilhermegoes.arraial.obra.Muralha.portoes(level, vila)) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof FenceGateBlock && !s.getValue(FenceGateBlock.OPEN)) {
				double x = p.distSqr(origem);
				if (x < d) {
					d = x;
					melhor = p;
				}
			}
		}
		return melhor;
	}

	static void abrir(ServerLevel level, BlockPos pos, MoradorEntity quem) {
		BlockState s = level.getBlockState(pos);
		if (!(s.getBlock() instanceof FenceGateBlock) || s.getValue(FenceGateBlock.OPEN)) {
			return;
		}
		level.setBlock(pos, s.setValue(FenceGateBlock.OPEN, true), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
		quem.abriuPortao(pos, level.getGameTime());
	}

	/** Closes the gate once its opener (and anything on his lead) has moved away from it. */
	static boolean podeFechar(ServerLevel level, BlockPos pos, MoradorEntity quem) {
		if (quem.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < 3.0 * 3.0) {
			return false;
		}
		for (Entity e : level.getEntities(quem, new net.minecraft.world.phys.AABB(pos).inflate(2.5))) {
			if (e instanceof Leashable l && l.getLeashHolder() == quem) {
				return false;
			}
		}
		return true;
	}

	static void fechar(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (s.getBlock() instanceof FenceGateBlock && s.getValue(FenceGateBlock.OPEN)) {
			level.setBlock(pos, s.setValue(FenceGateBlock.OPEN, false), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
	}

	private Porteira() {
	}
}
