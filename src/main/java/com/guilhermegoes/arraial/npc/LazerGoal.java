package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.vila.ArraialBlockEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Life outside work (see {@link Pessoa}): the exhausted stop to rest wherever they are; at the end
 * of the afternoon everyone stops work for their favourite pastime: a stroll, a prayer at the
 * Cruzeiro, sitting by the water or by a fire. (Those who like to chat are left to ConversarGoal.)
 */
public class LazerGoal extends Goal {
	private final MoradorEntity m;
	private int checar;
	private int t;
	private @Nullable BlockPos alvo;
	private boolean descanso;

	public LazerGoal(MoradorEntity m) {
		this.m = m;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	public static boolean horaDeLazer(ServerLevel level) {
		long h = level.getOverworldClockTime() % 24000L;
		return h >= 10500 && h < 12000;
	}

	private boolean pode() {
		ServerLevel level = getServerLevel(m);
		ArraialBlockEntity a = m.arraial();
		if (a == null || m.isSleeping() || m.viajando() || m.profissao() == Profissao.MASCATE || !level.isBrightOutside()
				|| a.festa().agora() != null || m.profissao() == Profissao.SOLDADO) {
			return false;
		}
		return m.pessoa().cansado() || horaDeLazer(level) && !m.pessoa().lazer().equals("conversar");
	}

	@Override
	public boolean canUse() {
		if (--checar > 0) {
			return false;
		}
		checar = 40 + m.getRandom().nextInt(20);
		return pode();
	}

	@Override
	public boolean canContinueToUse() {
		return pode() && (!descanso || t < 600);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		t = 0;
		descanso = m.pessoa().cansado();
		alvo = descanso ? null : lugar();
		m.setStatus(Component.translatable(descanso ? "arraial.lazer.status.descansando" : "arraial.lazer.status." + m.pessoa().lazer()));
	}

	@Override
	public void stop() {
		m.getNavigation().stop();
		m.setStatus(Component.empty());
	}

	/** Where the pastime happens. */
	private @Nullable BlockPos lugar() {
		ServerLevel level = getServerLevel(m);
		BlockPos vila = m.vila();
		if (vila == null) {
			return null;
		}
		switch (m.pessoa().lazer()) {
			case "rezar" -> {
				return vila.offset(m.getRandom().nextInt(5) - 2, 0, 2 + m.getRandom().nextInt(2));
			}
			case "agua" -> {
				for (int r = 4; r <= 24; r += 2) {
					for (int i = 0; i < 12; i++) {
						double ang = i * Math.PI / 6;
						BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
								m.blockPosition().offset((int) (Math.cos(ang) * r), 0, (int) (Math.sin(ang) * r)));
						if (level.getFluidState(p.below()).is(FluidTags.WATER)) {
							return p;
						}
					}
				}
			}
			case "fogueira" -> {
				for (BlockPos p : BlockPos.betweenClosed(m.blockPosition().offset(-16, -4, -16), m.blockPosition().offset(16, 4, 16))) {
					var s = level.getBlockState(p);
					if (s.is(BlockTags.CAMPFIRES) || s.is(com.guilhermegoes.arraial.registry.ModBlocks.LAMPIAO) || s.is(Blocks.LANTERN)) {
						return p.immutable().offset(1, 0, 0);
					}
				}
			}
			default -> {
			}
		}
		// A stroll around the Cruzeiro.
		return vila.offset(m.getRandom().nextInt(21) - 10, 0, m.getRandom().nextInt(21) - 10);
	}

	@Override
	public void tick() {
		t++;
		if (descanso) {
			m.getNavigation().stop();
			if (t % 100 == 0) {
				m.pessoa().energia = Math.min(100, m.pessoa().energia + 4);
			}
			return;
		}
		if (alvo == null) {
			return;
		}
		if (m.distanceToSqr(alvo.getX() + 0.5, alvo.getY(), alvo.getZ() + 0.5) > 2.5 * 2.5) {
			if (t % 20 == 1) {
				m.getNavigation().moveTo(alvo.getX() + 0.5, alvo.getY(), alvo.getZ() + 0.5, 0.45);
			}
			return;
		}
		m.getNavigation().stop();
		if (m.pessoa().lazer().equals("rezar") && m.vila() != null) {
			m.getLookControl().setLookAt(m.vila().getX() + 0.5, m.vila().getY() + 1.5, m.vila().getZ() + 0.5);
		}
		if (t % 200 == 0) {
			m.pessoa().convivio = Math.min(100, m.pessoa().convivio + 1);
			if (m.pessoa().lazer().equals("passear")) {
				alvo = lugar();
			}
			if (m.getRandom().nextFloat() < 0.2F) {
				m.dizer(Component.translatable("arraial.fala.lazer." + m.pessoa().lazer()), 60);
			}
		}
	}
}
