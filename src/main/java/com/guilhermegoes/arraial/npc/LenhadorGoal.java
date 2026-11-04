package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.vila.Vilas;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The lenhador fells whole natural trees near his cepo (only logs standing on dirt with leaves
 * on top, so houses are safe). Every stump gets a sapling the moment it is cut, so an
 * interrupted job never leaves bare ground. Then he clears the canopy (collecting saplings,
 * sticks and apples), carries everything to the armazém, and when the woods get thin he
 * plants his spare saplings around the cepo.
 */
public class LenhadorGoal extends OficioGoal {
	private enum Fase { CORTAR, FOLHAS, BUSCAR_MUDAS, REFLORESTAR, ENTREGAR }

	private static final int RAIO = 20;
	/** Where he plants new trees (closer to the cepo, so the grove stays his). */
	private static final int RAIO_PLANTIO = 12;
	/** He stops planting while this many saplings are already growing. */
	private static final int MAX_MUDAS = 8;
	private static final int MAX_TORAS = 80;
	private static final int MAX_FOLHAS = 160;
	private static final int CARGA_CHEIA = 48;
	private static final int MUDAS_GUARDADAS = 16;

	private Fase fase = Fase.CORTAR;
	private final List<BlockPos> toras = new ArrayList<>();
	private final List<BlockPos> folhas = new ArrayList<>();
	private @Nullable BlockPos base;
	private @Nullable BlockPos covaNova;
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
		if (cargaSemMudas() >= CARGA_CHEIA && !armazens().isEmpty()) {
			fase = Fase.ENTREGAR;
			return true;
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
		if (cargaSemMudas() > 0 && !armazens().isEmpty()) {
			fase = Fase.ENTREGAR;
			return true;
		}
		covaNova = lugarParaMuda();
		if (covaNova != null && mudaNaMao() != null) {
			fase = Fase.REFLORESTAR;
			return true;
		}
		if (covaNova != null && Vilas.contar(armazens(), st -> eMuda(st.getItem())) > 0) {
			fase = Fase.BUSCAR_MUDAS;
			return true;
		}
		morador.setStatus(Component.translatable(covaNova == null ? "arraial.lenhador.esperando_mudas" : "arraial.lenhador.sem_arvore"));
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		if (!podeTrabalhar()) {
			return false;
		}
		return switch (fase) {
			case CORTAR -> !toras.isEmpty();
			case FOLHAS -> !folhas.isEmpty();
			case REFLORESTAR, BUSCAR_MUDAS -> covaNova != null;
			case ENTREGAR -> true;
		};
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
			case FOLHAS -> folhas();
			case REFLORESTAR -> reflorestar();
			case BUSCAR_MUDAS -> buscarMudas();
			case ENTREGAR -> {
				var a = armazemMaisProximo();
				if (a == null) {
					fase = Fase.CORTAR;
					toras.clear();
					return;
				}
				morador.setStatus(Component.translatable("arraial.oficio.entregando", Component.translatable("arraial.lenhador.lenha")));
				if (!perto(a.getBlockPos(), 2.5)) {
					andarAte(a.getBlockPos());
					return;
				}
				entregar(mudasParaGuardar());
				toras.clear();
				fase = Fase.CORTAR; // ends the goal; canUse decides what's next
			}
		}
	}

	private void cortar() {
		ServerLevel level = level();
		while (!toras.isEmpty() && !level.getBlockState(toras.get(0)).is(BlockTags.LOGS)) {
			toras.remove(0);
		}
		if (toras.isEmpty()) {
			folhas.clear();
			if (base != null) {
				folhas.addAll(copa(level, base));
			}
			fase = Fase.FOLHAS;
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
		// A stump on the ground gets its sapling right away (every one of a 2x2 trunk, too).
		if (muda != null && level.getBlockState(alvo.below()).is(BlockTags.DIRT) && muda.defaultBlockState().canSurvive(level, alvo)) {
			level.setBlockAndUpdate(alvo, muda.defaultBlockState());
			morador.setStatus(Component.translatable("arraial.lenhador.replantou"));
		}
		bater();
		toras.remove(0);
		travado = 0;
		espera = 8;
	}

	/** Clears the fallen tree's leaves a few at a time; they give saplings, sticks and apples. */
	private void folhas() {
		ServerLevel level = level();
		morador.setStatus(Component.translatable("arraial.lenhador.limpando"));
		for (int n = 0; n < 4 && !folhas.isEmpty(); n++) {
			BlockPos p = folhas.remove(folhas.size() - 1);
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.LEAVES)) {
				for (ItemStack drop : Block.getDrops(s, level, p, null)) {
					morador.guardar(drop.getItem(), drop.getCount());
				}
				level.destroyBlock(p, false, morador, 512);
			}
		}
		bater();
		espera = 2;
		if (folhas.isEmpty()) {
			base = null;
			fase = Fase.CORTAR; // ends the goal; canUse decides what's next
		}
	}

	private void reflorestar() {
		ServerLevel level = level();
		BlockPos cova = covaNova;
		if (cova == null) {
			return;
		}
		Item item = mudaNaMao();
		if (item == null || !podePlantar(level, cova)) {
			covaNova = null;
			return;
		}
		morador.setStatus(Component.translatable("arraial.lenhador.plantando"));
		if (!perto(cova, 2.5) && travado++ < 200) {
			andarAte(cova);
			return;
		}
		morador.getNavigation().stop();
		morador.getLookControl().setLookAt(Vec3.atCenterOf(cova));
		BlockState planta = ((BlockItem) item).getBlock().defaultBlockState();
		if (planta.canSurvive(level, cova)) {
			level.setBlockAndUpdate(cova, planta);
			morador.gastar(item, 1);
			bater();
		}
		travado = 0;
		espera = 10;
		covaNova = null;
	}

	// ------------------------------------------------------------ saplings

	private static boolean eMuda(Item item) {
		return item instanceof BlockItem bi && bi.getDefaultInstance().is(ItemTags.SAPLINGS) && bi != Items.MANGROVE_PROPAGULE;
	}

	private @Nullable Item mudaNaMao() {
		for (Map.Entry<Item, Integer> e : morador.cargaCopia().entrySet()) {
			if (e.getValue() > 0 && eMuda(e.getKey())) {
				return e.getKey();
			}
		}
		return null;
	}

	/** Walks to the armazém for saplings, then goes planting. */
	private void buscarMudas() {
		var a = armazemMaisProximo();
		if (a == null) {
			covaNova = null;
			return;
		}
		morador.setStatus(Component.translatable("arraial.lenhador.buscando_mudas"));
		if (!perto(a.getBlockPos(), 2.5)) {
			andarAte(a.getBlockPos());
			return;
		}
		for (ItemStack s : Vilas.retirar(armazens(), st -> eMuda(st.getItem()), MUDAS_GUARDADAS)) {
			morador.guardar(s.getItem(), s.getCount());
		}
		bater();
		travado = 0;
		fase = mudaNaMao() != null ? Fase.REFLORESTAR : Fase.CORTAR;
		if (fase == Fase.CORTAR) {
			covaNova = null;
		}
	}

	private int cargaSemMudas() {
		int total = 0;
		for (Map.Entry<Item, Integer> e : morador.cargaCopia().entrySet()) {
			if (!eMuda(e.getKey())) {
				total += e.getValue();
			}
		}
		return total;
	}

	private Map<Item, Integer> mudasParaGuardar() {
		Map<Item, Integer> m = new HashMap<>();
		for (Item i : morador.cargaCopia().keySet()) {
			if (eMuda(i)) {
				m.put(i, MUDAS_GUARDADAS);
			}
		}
		return m;
	}

	/** A free spot near the cepo for a new tree, or null if enough saplings are already growing. */
	private @Nullable BlockPos lugarParaMuda() {
		BlockPos centro = morador.posto();
		if (centro == null) {
			return null;
		}
		ServerLevel level = level();
		int mudas = 0;
		List<BlockPos> livres = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(centro.offset(-RAIO_PLANTIO, -3, -RAIO_PLANTIO), centro.offset(RAIO_PLANTIO, 3, RAIO_PLANTIO))) {
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.SAPLINGS)) {
				mudas++;
			} else if ((p.getX() + p.getZ()) % 3 == 0 && p.distManhattan(centro) > 3 && podePlantar(level, p)) {
				livres.add(p.immutable());
			}
		}
		if (mudas >= MAX_MUDAS || livres.isEmpty()) {
			return null;
		}
		return livres.get(morador.getRandom().nextInt(livres.size()));
	}

	/** Open grass or dirt under the sky, with room around it to grow into a tree. */
	private static boolean podePlantar(ServerLevel level, BlockPos p) {
		if (!level.getBlockState(p).isAir() || !level.getBlockState(p.below()).is(BlockTags.DIRT)
				|| level.getBlockState(p.below()).is(Blocks.FARMLAND) || !level.canSeeSky(p)) {
			return false;
		}
		for (BlockPos q : BlockPos.betweenClosed(p.offset(-2, 0, -2), p.offset(2, 4, 2))) {
			BlockState s = level.getBlockState(q);
			if (s.is(BlockTags.LOGS) || s.is(BlockTags.SAPLINGS) || (!s.isAir() && !s.canBeReplaced() && !s.is(BlockTags.LEAVES))) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------ finding trees

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

	/** True when the trunk above {@code base} ends in natural leaves (so it's a tree, not a fence post). */
	private static boolean temFolhas(ServerLevel level, BlockPos base) {
		BlockPos topo = base;
		while (level.getBlockState(topo.above()).is(BlockTags.LOGS) && topo.getY() - base.getY() < 30) {
			topo = topo.above();
		}
		int folhas = 0;
		for (BlockPos q : BlockPos.betweenClosed(topo.offset(-2, -1, -2), topo.offset(2, 2, 2))) {
			BlockState s = level.getBlockState(q);
			if (s.is(BlockTags.LEAVES) && !(s.hasProperty(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.PERSISTENT)) && ++folhas >= 6) {
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

	/** Natural leaves left hanging where the tree stood (not the ones still held up by another tree). */
	private static List<BlockPos> copa(ServerLevel level, BlockPos base) {
		List<BlockPos> lista = new ArrayList<>();
		for (BlockPos q : BlockPos.betweenClosed(base.offset(-4, 0, -4), base.offset(4, 30, 4))) {
			BlockState s = level.getBlockState(q);
			if (!s.is(BlockTags.LEAVES) || (s.hasProperty(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.PERSISTENT))) {
				continue;
			}
			boolean deOutra = false;
			for (BlockPos r : BlockPos.betweenClosed(q.offset(-2, -2, -2), q.offset(2, 2, 2))) {
				if (level.getBlockState(r).is(BlockTags.LOGS)) {
					deOutra = true;
					break;
				}
			}
			if (!deOutra) {
				lista.add(q.immutable());
				if (lista.size() >= MAX_FOLHAS) {
					break;
				}
			}
		}
		// Break from the top down (the list is consumed from its end).
		lista.sort(Comparator.comparingInt(BlockPos::getY));
		return lista;
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
