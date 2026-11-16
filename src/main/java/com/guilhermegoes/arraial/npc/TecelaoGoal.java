package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.registry.ModBlocks;
import com.guilhermegoes.arraial.registry.ModItems;
import com.guilhermegoes.arraial.trabalho.AlgodoeiroBlock;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * The tecelão keeps the algodoal around the tear: he picks the open bolls, plants new bushes until
 * there are enough, spins and weaves at the tear (three bolls make a length of pano) and takes the
 * cloth to the armazém. Strings and hammocks he makes on demand at the same tear (see Receitas).
 */
public class TecelaoGoal extends OficioGoal {
	private static final int RAIO = 10;
	private static final int CARGA = 9;
	private static final int PES = 12;
	private static final int TICKS_TEAR = 120;
	private static final int POR_PANO = 3;

	private enum Fase { COLHER, PLANTAR, TECER, ENTREGAR }

	private Fase fase = Fase.COLHER;
	private @Nullable BlockPos alvo;
	private int trabalho;

	public TecelaoGoal(MoradorEntity morador) {
		super(morador, Profissao.TECELAO);
	}

	private int algodao() {
		return morador.cargaCopia().getOrDefault(ModItems.ALGODAO, 0);
	}

	@Override
	public boolean canUse() {
		if (!hora() || !podeTrabalhar() || morador.posto() == null) {
			return false;
		}
		if (morador.cargaCopia().getOrDefault(ModItems.PANO, 0) > 0) {
			fase = Fase.ENTREGAR;
			return !armazens().isEmpty();
		}
		if (algodao() >= CARGA) {
			fase = Fase.TECER;
			return true;
		}
		alvo = buscar(true);
		if (alvo != null) {
			fase = Fase.COLHER;
			return true;
		}
		if (algodao() > 0 && pes() < PES) {
			alvo = lugarDePlantar();
			if (alvo != null) {
				fase = Fase.PLANTAR;
				return true;
			}
		}
		if (algodao() >= POR_PANO + 2) {
			fase = Fase.TECER;
			return true;
		}
		// No bolls and too few bushes: seedlings from the armazém.
		if (algodao() == 0 && pes() < PES && pegar(ModItems.ALGODAO, 4) > 0) {
			return false;
		}
		morador.setStatus(Component.translatable("arraial.tecelao.esperando"));
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return podeTrabalhar() && (fase == Fase.TECER || fase == Fase.ENTREGAR || alvo != null);
	}

	@Override
	public void start() {
		trabalho = 0;
		morador.segurar(new ItemStack(fase == Fase.ENTREGAR ? ModItems.PANO : ModItems.ALGODAO));
	}

	@Override
	public void tick() {
		switch (fase) {
			case COLHER -> colher();
			case PLANTAR -> plantar();
			case TECER -> tecer();
			case ENTREGAR -> entregarPano();
		}
	}

	private void colher() {
		BlockPos a = alvo;
		if (a == null || !AlgodoeiroBlock.maduro(level().getBlockState(a))) {
			alvo = null;
			return;
		}
		morador.setStatus(Component.translatable("arraial.tecelao.colhendo"));
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
		morador.guardar(ModItems.ALGODAO, AlgodoeiroBlock.colher(level(), a, level().getBlockState(a)));
		alvo = algodao() < CARGA ? buscar(true) : null;
	}

	private void plantar() {
		BlockPos a = alvo;
		if (a == null || algodao() == 0 || !level().getBlockState(a).isAir()) {
			alvo = null;
			return;
		}
		morador.setStatus(Component.translatable("arraial.tecelao.plantando"));
		if (!perto(a, 2.2)) {
			andarAte(a);
			return;
		}
		BlockState muda = ModBlocks.ALGODOEIRO.defaultBlockState();
		if (muda.canSurvive(level(), a)) {
			level().setBlock(a, muda, Block.UPDATE_ALL);
			morador.gastar(ModItems.ALGODAO, 1);
			level().playSound(null, a, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
			bater();
		}
		alvo = null;
	}

	private void tecer() {
		BlockPos t = morador.posto();
		if (t == null) {
			fase = Fase.ENTREGAR;
			return;
		}
		morador.setStatus(Component.translatable("arraial.tecelao.tecendo"));
		if (!perto(t, 2.2)) {
			andarAte(t);
			return;
		}
		morador.getNavigation().stop();
		morador.getLookControl().setLookAt(Vec3.atCenterOf(t));
		if (morador.tickCount % 10 == 0) {
			bater();
			level().playSound(null, t, SoundEvents.UI_LOOM_SELECT_PATTERN, SoundSource.BLOCKS, 0.5F, 0.8F + morador.getRandom().nextFloat() * 0.4F);
		}
		if (++trabalho < TICKS_TEAR) {
			return;
		}
		// Keep a couple of bolls to plant while the algodoal is still small.
		int guardar = pes() < PES ? Math.min(2, algodao()) : 0;
		int panos = (algodao() - guardar) / POR_PANO;
		morador.gastar(ModItems.ALGODAO, panos * POR_PANO);
		morador.guardar(ModItems.PANO, panos);
		fase = Fase.ENTREGAR;
		trabalho = 0;
	}

	private void entregarPano() {
		var a = armazemMaisProximo();
		if (a == null) {
			return;
		}
		morador.setStatus(Component.translatable("arraial.oficio.entregando", Component.translatable("item.arraial.pano")));
		if (!perto(a.getBlockPos(), 2.5)) {
			andarAte(a.getBlockPos());
			return;
		}
		entregar(Map.of(ModItems.ALGODAO, algodao()));
		fase = Fase.COLHER;
	}

	// ---------------------------------------------------------------- the algodoal

	/** The nearest bush around the tear (ripe only, if asked). */
	private @Nullable BlockPos buscar(boolean maduro) {
		BlockPos centro = morador.posto();
		if (centro == null) {
			return null;
		}
		BlockPos melhor = null;
		double d = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(centro.offset(-RAIO, -2, -RAIO), centro.offset(RAIO, 2, RAIO))) {
			BlockState s = level().getBlockState(p);
			if (s.is(ModBlocks.ALGODOEIRO) && (!maduro || AlgodoeiroBlock.maduro(s))) {
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
			if (level().getBlockState(p).is(ModBlocks.ALGODOEIRO)) {
				n++;
			}
		}
		return n;
	}

	/** Bare dirt or grass under open sky, away from paths, with a free block between bushes. */
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
				vizinho |= level().getBlockState(acima.relative(dir)).is(ModBlocks.ALGODOEIRO);
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
