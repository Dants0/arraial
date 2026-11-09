package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.vila.Colonos;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A settler on the way: the leader walks to the chosen land and the others follow him; on arrival
 * the leader plants the Cruzeiro ({@link Colonos#fundar}). Long walks go in legs of about 16 blocks
 * (the pathfinder's reach). If the leader is gone, another settler takes the lead.
 */
public class ColonosGoal extends Goal {
	/** After this long on the road (2 Minecraft days) they settle wherever they are. */
	private static final long PACIENCIA = 48000;
	private final MoradorEntity m;
	private int t;
	private int semLider;

	public ColonosGoal(MoradorEntity m) {
		this.m = m;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return m.fundacao() != null && m.vila() == null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		ServerLevel level = getServerLevel(m);
		BlockPos destino = m.fundacao();
		if (destino == null) {
			return;
		}
		t++;
		if (t % 20 != 1) {
			return;
		}
		m.setStatus(net.minecraft.network.chat.Component.translatable(m.liderColonos() ? "arraial.colonos.status_lider" : "arraial.colonos.status"));
		Vec3 alvo;
		if (m.liderColonos()) {
			double dx = destino.getX() + 0.5 - m.getX(), dz = destino.getZ() + 0.5 - m.getZ();
			boolean cansou = level.getGameTime() - m.colonosDesde() > PACIENCIA;
			if (dx * dx + dz * dz < 9 || cansou) {
				Colonos.fundar(level, m, cansou ? m.blockPosition() : destino);
				return;
			}
			alvo = Vec3.atBottomCenterOf(destino);
		} else {
			MoradorEntity lider = lider(level, destino);
			if (lider == null) {
				if (++semLider > 15) { // the leader is gone: someone else leads
					m.setColono(destino, true, m.colonosHispanos(), m.colonosDesde());
				}
				alvo = Vec3.atBottomCenterOf(destino);
			} else {
				semLider = 0;
				if (m.distanceToSqr(lider) < 16) {
					m.getLookControl().setLookAt(lider);
					return; // close behind the leader
				}
				alvo = lider.position();
			}
		}
		andar(level, alvo);
	}

	/** Walks towards {@code alvo}, a leg of at most 16 blocks at a time. */
	private void andar(ServerLevel level, Vec3 alvo) {
		Vec3 d = alvo.subtract(m.position());
		double h = Math.sqrt(d.x * d.x + d.z * d.z);
		if (h > 16) {
			Vec3 passo = m.position().add(d.x / h * 16, 0, d.z / h * 16);
			BlockPos chao = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(passo));
			alvo = Vec3.atBottomCenterOf(chao);
		}
		if (!m.getNavigation().moveTo(alvo.x, alvo.y, alvo.z, 0.6) && m.getRandom().nextInt(4) == 0) {
			// No path (a river, a cliff): a small detour to the side.
			m.getNavigation().moveTo(m.getX() + m.getRandom().nextInt(17) - 8, m.getY(), m.getZ() + m.getRandom().nextInt(17) - 8, 0.6);
		}
	}

	private @Nullable MoradorEntity lider(ServerLevel level, BlockPos destino) {
		return level.getEntitiesOfClass(MoradorEntity.class, m.getBoundingBox().inflate(48),
				o -> o != m && o.liderColonos() && destino.equals(o.fundacao())).stream().findFirst().orElse(null);
	}
}
