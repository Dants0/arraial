package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.registry.ModItems;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The mestre de engenho runs the sugar mill. Around his moenda he cuts the ripe cane (leaving the
 * root to grow again), plants new cane on the banks of the water, grinds the cane at the moenda
 * (half becomes sugar, half is boiled down into rapadura) and takes it to the armazém.
 */
public class EngenhoGoal extends OficioGoal {
	private static final int RAIO = 14;
	private static final int CARGA = 12;
	private static final int TOUCEIRAS = 10;
	private static final int TICKS_MOAGEM = 80;

	private enum Fase { CORTAR, PLANTAR, MOER, ENTREGAR }

	private Fase fase = Fase.CORTAR;
	private @Nullable BlockPos alvo;
	private int trabalho;

	public EngenhoGoal(MoradorEntity morador) {
		super(morador, Profissao.MESTRE_ENGENHO);
	}

	private int cana() {
		return morador.cargaCopia().getOrDefault(Items.SUGAR_CANE, 0);
	}

	private boolean temProduto() {
		Map<net.minecraft.world.item.Item, Integer> c = morador.cargaCopia();
		return c.getOrDefault(Items.SUGAR, 0) + c.getOrDefault(ModItems.RAPADURA, 0) > 0;
	}

	@Override
	public boolean canUse() {
		if (!hora() || !podeTrabalhar() || morador.posto() == null) {
			return false;
		}
		if (temProduto()) {
			fase = Fase.ENTREGAR;
			return !armazens().isEmpty();
		}
		if (cana() >= CARGA) {
			fase = Fase.MOER;
			return true;
		}
		alvo = canaMadura();
		if (alvo != null) {
			fase = Fase.CORTAR;
			return true;
		}
		if (cana() > 0) {
			alvo = touceiras() < TOUCEIRAS ? lugarDePlantar() : null;
			if (alvo != null) {
				fase = Fase.PLANTAR;
				return true;
			}
			if (cana() >= 4) {      // nothing left to cut: grind what he has
				fase = Fase.MOER;
				return true;
			}
		}
		morador.setStatus(Component.translatable("arraial.engenho.esperando"));
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return podeTrabalhar() && (fase == Fase.MOER || fase == Fase.ENTREGAR || alvo != null);
	}

	@Override
	public void start() {
		trabalho = 0;
		morador.segurar(new ItemStack(fase == Fase.CORTAR ? com.guilhermegoes.arraial.registry.ModItems.FACAO : Items.SUGAR_CANE));
	}

	@Override
	public void tick() {
		switch (fase) {
			case CORTAR -> cortar();
			case PLANTAR -> plantar();
			case MOER -> moer();
			case ENTREGAR -> entregarProduto();
		}
	}

	private void cortar() {
		BlockPos a = alvo;
		if (a == null || !level().getBlockState(a).is(Blocks.SUGAR_CANE)) {
			alvo = null;
			return;
		}
		morador.setStatus(Component.translatable("arraial.engenho.cortando"));
		if (!perto(a, 2.5)) {
			andarAte(a);
			return;
		}
		morador.getLookControl().setLookAt(Vec3.atCenterOf(a));
		if (++trabalho < 15) {
			return;
		}
		trabalho = 0;
		// Everything above the root comes down; the root grows again.
		int n = 0;
		for (BlockPos p = a; level().getBlockState(p).is(Blocks.SUGAR_CANE); p = p.above()) {
			level().setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			n++;
		}
		bater();
		level().playSound(null, a, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
		morador.guardar(Items.SUGAR_CANE, n);
		alvo = canaMadura();
		if (alvo == null || cana() >= CARGA) {
			alvo = null;
		}
	}

	private void plantar() {
		BlockPos a = alvo;
		if (a == null || cana() == 0 || !level().getBlockState(a).isAir()) {
			alvo = null;
			return;
		}
		morador.setStatus(Component.translatable("arraial.engenho.plantando"));
		if (!perto(a, 2.5)) {
			andarAte(a);
			return;
		}
		if (Blocks.SUGAR_CANE.defaultBlockState().canSurvive(level(), a)) {
			level().setBlock(a, Blocks.SUGAR_CANE.defaultBlockState(), Block.UPDATE_ALL);
			morador.gastar(Items.SUGAR_CANE, 1);
			bater();
		}
		alvo = null;
	}

	private void moer() {
		BlockPos m = morador.posto();
		if (m == null || cana() == 0) {
			fase = Fase.ENTREGAR;
			return;
		}
		morador.setStatus(Component.translatable("arraial.engenho.moendo"));
		if (!perto(m, 2.5)) {
			andarAte(m);
			return;
		}
		morador.getNavigation().stop();
		morador.getLookControl().setLookAt(Vec3.atCenterOf(m));
		if (morador.tickCount % 10 == 0) {
			bater();
			level().sendParticles(ParticleTypes.SMOKE, m.getX() + 0.5, m.getY() + 1.2, m.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.0);
		}
		if (morador.tickCount % 20 == 0) {
			level().playSound(null, m, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.5F, 0.8F);
		}
		if (++trabalho < TICKS_MOAGEM) {
			return;
		}
		// Keep a couple of canes to plant; half of the rest becomes sugar, half rapadura (two canes each).
		int moer = Math.max(0, cana() - (touceiras() < TOUCEIRAS ? 2 : 0));
		int rapadura = moer / 4;
		int acucar = moer - rapadura * 2;
		morador.gastar(Items.SUGAR_CANE, moer);
		morador.guardar(Items.SUGAR, acucar);
		morador.guardar(ModItems.RAPADURA, rapadura);
		fase = Fase.ENTREGAR;
		trabalho = 0;
	}

	private void entregarProduto() {
		var a = armazemMaisProximo();
		if (a == null) {
			return;
		}
		morador.setStatus(Component.translatable("arraial.oficio.entregando", Component.translatable("item.minecraft.sugar")));
		if (!perto(a.getBlockPos(), 2.5)) {
			andarAte(a.getBlockPos());
			return;
		}
		entregar(Map.of(Items.SUGAR_CANE, cana()));
		fase = Fase.CORTAR;
		alvo = null;
	}

	// ---------------------------------------------------------------- the canavial

	/** Cane two or more blocks tall near the moenda: the block above the root. */
	private @Nullable BlockPos canaMadura() {
		BlockPos centro = morador.posto();
		if (centro == null) {
			return null;
		}
		BlockPos melhor = null;
		double d = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(centro.offset(-RAIO, -3, -RAIO), centro.offset(RAIO, 3, RAIO))) {
			if (level().getBlockState(p).is(Blocks.SUGAR_CANE) && !level().getBlockState(p.below()).is(Blocks.SUGAR_CANE)
					&& level().getBlockState(p.above()).is(Blocks.SUGAR_CANE)) {
				double x = p.distSqr(morador.blockPosition());
				if (x < d) {
					d = x;
					melhor = p.above().immutable();
				}
			}
		}
		return melhor;
	}

	/** How many cane roots grow around the moenda. */
	private int touceiras() {
		BlockPos centro = morador.posto();
		if (centro == null) {
			return 0;
		}
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(centro.offset(-RAIO, -3, -RAIO), centro.offset(RAIO, 3, RAIO))) {
			if (level().getBlockState(p).is(Blocks.SUGAR_CANE) && !level().getBlockState(p.below()).is(Blocks.SUGAR_CANE)) {
				n++;
			}
		}
		return n;
	}

	/** Free ground next to water where cane can grow, close to the moenda. */
	private @Nullable BlockPos lugarDePlantar() {
		BlockPos centro = morador.posto();
		if (centro == null) {
			return null;
		}
		BlockPos melhor = null;
		double d = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(centro.offset(-RAIO, -3, -RAIO), centro.offset(RAIO, 2, RAIO))) {
			BlockState chao = level().getBlockState(p);
			if (!(chao.is(BlockTags.SUBSTRATE_OVERWORLD) || chao.is(BlockTags.SAND)) || !level().getBlockState(p.above()).isAir()) {
				continue;
			}
			boolean agua = false;
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				agua |= level().getFluidState(p.relative(dir)).is(FluidTags.WATER);
			}
			if (agua && !level().getBlockState(p).is(Blocks.DIRT_PATH)) {
				double x = p.distSqr(centro);
				if (x < d) {
					d = x;
					melhor = p.above().immutable();
				}
			}
		}
		return melhor;
	}
}
