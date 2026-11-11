package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.registry.ModBlocks;
import com.guilhermegoes.arraial.registry.ModItems;
import com.guilhermegoes.arraial.trabalho.CafeeiroBlock;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The cafeicultor keeps the cafezal around his terreiro: he picks the ripe bushes, plants new ones
 * until there are enough, spreads the cherries on the terreiro to dry and roasts them (two cherries
 * make one portion of roasted coffee), and takes the coffee to the armazém.
 */
public class CafezalGoal extends OficioGoal {
	private static final int RAIO = 12;
	private static final int CARGA = 10;
	private static final int PES = 16;
	private static final int TICKS_TORRA = 100;

	private enum Fase { COLHER, PLANTAR, TORRAR, ENTREGAR }

	private Fase fase = Fase.COLHER;
	private @Nullable BlockPos alvo;
	private int trabalho;

	public CafezalGoal(MoradorEntity morador) {
		super(morador, Profissao.CAFEICULTOR);
	}

	private int cerejas() {
		return morador.cargaCopia().getOrDefault(ModItems.CAFE_CEREJA, 0);
	}

	@Override
	public boolean canUse() {
		if (!hora() || !podeTrabalhar() || morador.posto() == null) {
			return false;
		}
		if (morador.cargaCopia().getOrDefault(ModItems.CAFE_TORRADO, 0) > 0) {
			fase = Fase.ENTREGAR;
			return !armazens().isEmpty();
		}
		if (cerejas() >= CARGA) {
			fase = Fase.TORRAR;
			return true;
		}
		alvo = buscar(true);
		if (alvo != null) {
			fase = Fase.COLHER;
			return true;
		}
		if (cerejas() > 0 && pes() < PES) {
			alvo = lugarDePlantar();
			if (alvo != null) {
				fase = Fase.PLANTAR;
				return true;
			}
		}
		if (cerejas() >= 4) {
			fase = Fase.TORRAR;
			return true;
		}
		// No cherries at all and too few bushes: seedlings from the armazém.
		if (cerejas() == 0 && pes() < PES && pegar(ModItems.CAFE_CEREJA, 4) > 0) {
			return false;
		}
		morador.setStatus(Component.translatable("arraial.cafezal.esperando"));
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return podeTrabalhar() && (fase == Fase.TORRAR || fase == Fase.ENTREGAR || alvo != null);
	}

	@Override
	public void start() {
		trabalho = 0;
		morador.segurar(new ItemStack(fase == Fase.ENTREGAR ? ModItems.CAFE_TORRADO : ModItems.CAFE_CEREJA));
	}

	@Override
	public void tick() {
		switch (fase) {
			case COLHER -> colher();
			case PLANTAR -> plantar();
			case TORRAR -> torrar();
			case ENTREGAR -> entregarCafe();
		}
	}

	private void colher() {
		BlockPos a = alvo;
		if (a == null || !CafeeiroBlock.maduro(level().getBlockState(a))) {
			alvo = null;
			return;
		}
		morador.setStatus(Component.translatable("arraial.cafezal.colhendo"));
		if (!perto(a, 2.2)) {
			andarAte(a);
			return;
		}
		morador.getLookControl().setLookAt(Vec3.atCenterOf(a));
		if (++trabalho < 12) {
			return;
		}
		trabalho = 0;
		bater();
		morador.guardar(ModItems.CAFE_CEREJA, CafeeiroBlock.colher(level(), a, level().getBlockState(a)));
		alvo = cerejas() < CARGA ? buscar(true) : null;
	}

	private void plantar() {
		BlockPos a = alvo;
		if (a == null || cerejas() == 0 || !level().getBlockState(a).isAir()) {
			alvo = null;
			return;
		}
		morador.setStatus(Component.translatable("arraial.cafezal.plantando"));
		if (!perto(a, 2.2)) {
			andarAte(a);
			return;
		}
		BlockState muda = ModBlocks.CAFEEIRO.defaultBlockState();
		if (muda.canSurvive(level(), a)) {
			level().setBlock(a, muda, Block.UPDATE_ALL);
			morador.gastar(ModItems.CAFE_CEREJA, 1);
			level().playSound(null, a, SoundEvents.SWEET_BERRY_BUSH_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
			bater();
		}
		alvo = null;
	}

	private void torrar() {
		BlockPos t = morador.posto();
		if (t == null) {
			fase = Fase.ENTREGAR;
			return;
		}
		morador.setStatus(Component.translatable("arraial.cafezal.torrando"));
		if (!perto(t, 2.2)) {
			andarAte(t);
			return;
		}
		morador.getNavigation().stop();
		morador.getLookControl().setLookAt(Vec3.atCenterOf(t));
		if (morador.tickCount % 15 == 0) {
			bater();
			level().sendParticles(ParticleTypes.SMOKE, t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5, 3, 0.3, 0.05, 0.3, 0.01);
		}
		// With a monjolo the coffee is hulled by the water: twice as fast, and nothing lost to the pilão.
		boolean monjolo = morador.vila() != null && com.guilhermegoes.arraial.vila.Vilas.obraPronta(level(), morador.vila(), "monjolo") != null;
		if (++trabalho < (monjolo ? TICKS_TORRA / 2 : TICKS_TORRA)) {
			return;
		}
		// Keep a few cherries to plant while the cafezal is still small.
		int guardar = pes() < PES ? Math.min(2, cerejas()) : 0;
		int porTorrado = monjolo ? 1 : 2;
		int torrar = (cerejas() - guardar) / porTorrado;
		morador.gastar(ModItems.CAFE_CEREJA, torrar * porTorrado);
		morador.guardar(ModItems.CAFE_TORRADO, torrar);
		level().playSound(null, t, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 1.4F);
		fase = Fase.ENTREGAR;
		trabalho = 0;
	}

	private void entregarCafe() {
		var a = armazemMaisProximo();
		if (a == null) {
			return;
		}
		morador.setStatus(Component.translatable("arraial.oficio.entregando", Component.translatable("item.arraial.cafe_torrado")));
		if (!perto(a.getBlockPos(), 2.5)) {
			andarAte(a.getBlockPos());
			return;
		}
		entregar(Map.of(ModItems.CAFE_CEREJA, cerejas()));
		fase = Fase.COLHER;
	}

	// ---------------------------------------------------------------- the cafezal

	/** The nearest bush around the terreiro (ripe only, if asked). */
	private @Nullable BlockPos buscar(boolean maduro) {
		BlockPos centro = morador.posto();
		if (centro == null) {
			return null;
		}
		BlockPos melhor = null;
		double d = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(centro.offset(-RAIO, -2, -RAIO), centro.offset(RAIO, 2, RAIO))) {
			BlockState s = level().getBlockState(p);
			if (s.is(ModBlocks.CAFEEIRO) && (!maduro || CafeeiroBlock.maduro(s))) {
				double x = p.distSqr(morador.blockPosition());
				if (x < d) {
					d = x;
					melhor = p.immutable();
				}
			}
		}
		return melhor;
	}

	private int pes() {
		BlockPos centro = morador.posto();
		if (centro == null) {
			return 0;
		}
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(centro.offset(-RAIO, -2, -RAIO), centro.offset(RAIO, 2, RAIO))) {
			if (level().getBlockState(p).is(ModBlocks.CAFEEIRO)) {
				n++;
			}
		}
		return n;
	}

	/** Bare dirt or grass under open air, away from paths and with a free block between bushes. */
	private @Nullable BlockPos lugarDePlantar() {
		BlockPos centro = morador.posto();
		if (centro == null) {
			return null;
		}
		BlockPos melhor = null;
		double d = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(centro.offset(-RAIO + 2, -2, -RAIO + 2), centro.offset(RAIO - 2, 2, RAIO - 2))) {
			BlockState chao = level().getBlockState(p);
			BlockPos acima = p.above();
			if (!chao.is(BlockTags.SUBSTRATE_OVERWORLD) || chao.is(Blocks.DIRT_PATH) || !level().getBlockState(acima).isAir()
					|| !level().getBlockState(acima.above()).isAir() || p.distSqr(centro) < 4) {
				continue;
			}
			boolean vizinho = false;
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				vizinho |= level().getBlockState(acima.relative(dir)).is(ModBlocks.CAFEEIRO);
			}
			if (!vizinho && level().canSeeSky(acima)) {
				double x = p.distSqr(centro);
				if (x < d) {
					d = x;
					melhor = acima.immutable();
				}
			}
		}
		return melhor;
	}
}
