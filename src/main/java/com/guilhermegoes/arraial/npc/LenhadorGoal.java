package com.guilhermegoes.arraial.npc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The lenhador fells whole natural trees near his cepo (only logs standing on dirt with leaves
 * on top, so houses are safe), always plants a sapling of the same wood where the trunk stood,
 * and carries the logs to the armazém.
 */
public class LenhadorGoal extends OficioGoal {
	private enum Fase { CORTAR, REPLANTAR, ENTREGAR }

	private static final int RAIO = 20;
	private static final int MAX_TORAS = 80;
	private static final int CARGA_CHEIA = 48;

	private Fase fase = Fase.CORTAR;
	private final List<BlockPos> toras = new ArrayList<>();
	private @Nullable BlockPos base;
	private @Nullable Block muda;
	private int espera;
	private int travado;

	public LenhadorGoal(MoradorEntity morador) {
		super(morador, Profissao.LENHADOR);
	}

	@Override
	public boolean canUse() {
		if (!hora() || !podeTrabalhar()) {
			return false;
		}
		if (morador.cargaTotal() >= CARGA_CHEIA) {
			fase = Fase.ENTREGAR;
			return !armazens().isEmpty();
		}
		BlockPos arvore = procurarArvore(level(), morador.posto());
		if (arvore != null) {
			base = arvore;
			toras.clear();
			toras.addAll(tronco(level(), arvore));
			toras.sort(Comparator.comparingInt(BlockPos::getY)); // bottom up
			muda = mudaPara(level().getBlockState(arvore).getBlock());
			fase = Fase.CORTAR;
			return true;
		}
		if (morador.cargaTotal() > 0 && !armazens().isEmpty()) {
			fase = Fase.ENTREGAR;
			return true;
		}
		morador.setStatus(Component.translatable("arraial.lenhador.sem_arvore"));
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return podeTrabalhar() && (fase != Fase.CORTAR || !toras.isEmpty());
	}

	@Override
	public void start() {
		espera = 0;
		travado = 0;
		morador.segurar(new ItemStack(Items.IRON_AXE));
	}

	@Override
	public void tick() {
		if (espera > 0) {
			espera--;
			return;
		}
		switch (fase) {
			case CORTAR -> cortar();
			case REPLANTAR -> replantar();
			case ENTREGAR -> {
				var a = armazemMaisProximo();
				if (a == null) {
					toras.clear();
					return;
				}
				morador.setStatus(Component.translatable("arraial.oficio.entregando", Component.translatable("arraial.lenhador.lenha")));
				if (!perto(a.getBlockPos(), 2.5)) {
					andarAte(a.getBlockPos());
					return;
				}
				entregar(Map.of());
				toras.clear();
				fase = Fase.CORTAR;
			}
		}
	}

	private void cortar() {
		ServerLevel level = level();
		while (!toras.isEmpty() && !level.getBlockState(toras.get(0)).is(BlockTags.LOGS)) {
			toras.remove(0);
		}
		if (toras.isEmpty()) {
			fase = Fase.REPLANTAR;
			return;
		}
		morador.setStatus(Component.translatable("arraial.lenhador.cortando"));
		BlockPos alvo = toras.get(0);
		double dist = morador.getEyePosition().distanceTo(Vec3.atCenterOf(alvo));
		if (dist > 5.0 && !(travado > 60 && dist < 10.0)) {
			travado++;
			andarAte(base != null ? base : alvo);
			return;
		}
		morador.getNavigation().stop();
		morador.getLookControl().setLookAt(Vec3.atCenterOf(alvo));
		BlockState state = level.getBlockState(alvo);
		for (ItemStack drop : Block.getDrops(state, level, alvo, null)) {
			morador.guardar(drop.getItem(), drop.getCount());
		}
		level.destroyBlock(alvo, false, morador, 512);
		bater();
		toras.remove(0);
		travado = 0;
		espera = 8;
	}

	private void replantar() {
		ServerLevel level = level();
		if (base != null && muda != null && level.getBlockState(base).isAir()
				&& level.getBlockState(base.below()).is(BlockTags.DIRT)) {
			if (!perto(base, 3.0)) {
				andarAte(base);
				return;
			}
			level.setBlockAndUpdate(base, muda.defaultBlockState());
			bater();
			morador.setStatus(Component.translatable("arraial.lenhador.replantou"));
		}
		base = null;
		fase = morador.cargaTotal() >= CARGA_CHEIA ? Fase.ENTREGAR : Fase.CORTAR;
		toras.clear(); // ends this tree; canUse looks for the next one
	}

	/** Nearest trunk base near the cepo: a log standing on dirt, part of a tree with leaves. */
	static @Nullable BlockPos procurarArvore(ServerLevel level, @Nullable BlockPos centro) {
		if (centro == null) {
			return null;
		}
		BlockPos melhor = null;
		double melhorDist = Double.MAX_VALUE;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -RAIO; dx <= RAIO; dx++) {
			for (int dz = -RAIO; dz <= RAIO; dz++) {
				for (int dy = -6; dy <= 8; dy++) {
					p.set(centro.getX() + dx, centro.getY() + dy, centro.getZ() + dz);
					if (!level.isLoaded(p) || !level.getBlockState(p).is(BlockTags.LOGS)
							|| !level.getBlockState(p.below()).is(BlockTags.DIRT)) {
						continue;
					}
					double d = p.distSqr(centro);
					if (d < melhorDist && temFolhas(level, p)) {
						melhor = p.immutable();
						melhorDist = d;
					}
				}
			}
		}
		return melhor;
	}

	/** True when the trunk above {@code base} ends in leaves (so it's a tree, not a fence post). */
	private static boolean temFolhas(ServerLevel level, BlockPos base) {
		BlockPos topo = base;
		while (level.getBlockState(topo.above()).is(BlockTags.LOGS) && topo.getY() - base.getY() < 30) {
			topo = topo.above();
		}
		int folhas = 0;
		for (BlockPos q : BlockPos.betweenClosed(topo.offset(-2, -1, -2), topo.offset(2, 2, 2))) {
			if (level.getBlockState(q).is(BlockTags.LEAVES) && ++folhas >= 6) {
				return true;
			}
		}
		return false;
	}

	/** All logs connected to the base (including diagonals, for branches). */
	private static List<BlockPos> tronco(ServerLevel level, BlockPos base) {
		List<BlockPos> achadas = new ArrayList<>();
		Set<BlockPos> vistas = new HashSet<>();
		ArrayDeque<BlockPos> fila = new ArrayDeque<>();
		fila.add(base);
		vistas.add(base);
		while (!fila.isEmpty() && achadas.size() < MAX_TORAS) {
			BlockPos atual = fila.poll();
			achadas.add(atual);
			for (BlockPos v : BlockPos.betweenClosed(atual.offset(-1, 0, -1), atual.offset(1, 1, 1))) {
				BlockPos q = v.immutable();
				if (!vistas.contains(q) && Math.abs(q.getX() - base.getX()) <= 6 && Math.abs(q.getZ() - base.getZ()) <= 6
						&& level.getBlockState(q).is(BlockTags.LOGS)) {
					vistas.add(q);
					fila.add(q);
				}
			}
		}
		return achadas;
	}

	private static Block mudaPara(Block tora) {
		if (tora == Blocks.SPRUCE_LOG) return Blocks.SPRUCE_SAPLING;
		if (tora == Blocks.BIRCH_LOG) return Blocks.BIRCH_SAPLING;
		if (tora == Blocks.JUNGLE_LOG) return Blocks.JUNGLE_SAPLING;
		if (tora == Blocks.ACACIA_LOG) return Blocks.ACACIA_SAPLING;
		if (tora == Blocks.DARK_OAK_LOG) return Blocks.DARK_OAK_SAPLING;
		if (tora == Blocks.CHERRY_LOG) return Blocks.CHERRY_SAPLING;
		if (tora == Blocks.MANGROVE_LOG) return Blocks.MANGROVE_PROPAGULE;
		if (tora == Blocks.PALE_OAK_LOG) return Blocks.PALE_OAK_SAPLING;
		return Blocks.OAK_SAPLING;
	}
}
