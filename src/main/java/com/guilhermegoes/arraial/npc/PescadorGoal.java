package com.guilhermegoes.arraial.npc;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The pescador walks from his covo to the nearest open water, stands on the bank with his rod,
 * and now and then pulls out a fish (cod or salmon, sometimes nothing). Fish feed the arraial.
 */
public class PescadorGoal extends OficioGoal {
	private static final int RAIO = 28;
	private static final int CARGA = 8;

	private @Nullable BlockPos agua;
	private @Nullable BlockPos margem;
	private int esperando;
	private boolean entregando;

	public PescadorGoal(MoradorEntity morador) {
		super(morador, Profissao.PESCADOR);
	}

	@Override
	public boolean canUse() {
		if (!hora() || !podeTrabalhar()) {
			return false;
		}
		if (peixes() >= CARGA && !armazens().isEmpty()) {
			entregando = true;
			return true;
		}
		entregando = false;
		if (agua == null || !level().getFluidState(agua).is(FluidTags.WATER)) {
			procurarAgua();
		}
		if (agua == null) {
			morador.setStatus(Component.translatable("arraial.pescador.sem_agua"));
			return false;
		}
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return podeTrabalhar() && (entregando || agua != null);
	}

	@Override
	public void start() {
		esperando = 200 + morador.getRandom().nextInt(300);
		morador.segurar(new ItemStack(entregando ? Items.COD : Items.FISHING_ROD));
	}

	private int peixes() {
		int n = 0;
		for (var e : morador.cargaCopia().entrySet()) {
			n += e.getValue();
		}
		return n;
	}

	/** Open water (with air above) next to standable ground, nearest to the covo. */
	private void procurarAgua() {
		BlockPos centro = morador.posto() != null ? morador.posto() : morador.blockPosition();
		agua = null;
		margem = null;
		double d = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(centro.offset(-RAIO, -4, -RAIO), centro.offset(RAIO, 3, RAIO))) {
			if (!level().getFluidState(p).is(FluidTags.WATER) || !level().getBlockState(p.above()).isAir()) {
				continue;
			}
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos m = p.relative(dir);
				BlockPos chao = m.above();
				if (level().getFluidState(m).isEmpty() && level().getBlockState(m).isFaceSturdy(level(), m, Direction.UP)
						&& level().getBlockState(chao).isAir() && level().getBlockState(chao.above()).isAir()) {
						double x = p.distSqr(centro);
						if (x < d) {
							d = x;
							agua = p.immutable();
							margem = chao.immutable();
						}
				}
			}
		}
	}

	@Override
	public void tick() {
		if (entregando) {
			var a = armazemMaisProximo();
			if (a == null) {
				entregando = false;
				return;
			}
			morador.setStatus(Component.translatable("arraial.oficio.entregando", Component.translatable("arraial.pescador.peixe")));
			if (!perto(a.getBlockPos(), 2.5)) {
				andarAte(a.getBlockPos());
				return;
			}
			entregar(Map.of());
			entregando = false;
			agua = null;
			return;
		}
		if (agua == null || margem == null) {
			return;
		}
		if (!perto(margem, 1.8)) {
			morador.setStatus(Component.translatable("arraial.pescador.indo"));
			andarAte(margem);
			return;
		}
		morador.getNavigation().stop();
		morador.getLookControl().setLookAt(Vec3.atCenterOf(agua));
		morador.setStatus(Component.translatable("arraial.pescador.pescando"));
		if (morador.tickCount % 40 == 0) {
			level().sendParticles(ParticleTypes.FISHING, agua.getX() + 0.5, agua.getY() + 1.0, agua.getZ() + 0.5, 3, 0.2, 0.0, 0.2, 0.0);
		}
		if (--esperando > 0) {
			return;
		}
		esperando = 300 + morador.getRandom().nextInt(400);
		float r = morador.getRandom().nextFloat();
		Item peixe = r < 0.55F ? Items.COD : r < 0.8F ? Items.SALMON : null;
		bater();
		level().playSound(null, agua, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.6F, 1.0F);
		level().sendParticles(ParticleTypes.SPLASH, agua.getX() + 0.5, agua.getY() + 1.0, agua.getZ() + 0.5, 15, 0.3, 0.1, 0.3, 0.1);
		if (peixe != null) {
			morador.guardar(peixe, 1);
		}
		if (peixes() >= CARGA) {
			agua = null; // ends; canUse sends him to the armazém
		}
	}
}
