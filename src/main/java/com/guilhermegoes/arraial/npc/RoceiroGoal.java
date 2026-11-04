package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.vila.Vilas;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The roceiro tends the land around his balaio: harvests ripe crops, replants right away,
 * sows empty farmland, tills watered ground next to the field so it grows, and when there
 * are no seeds at all he cuts wild grass to find some. The harvest goes to the armazém.
 */
public class RoceiroGoal extends OficioGoal {
	private enum Fase { TRABALHAR, BUSCAR_SEMENTES, CAPINAR, ENTREGAR }

	private static final int RAIO = 9;
	/** Where he looks for wild grass when there are no seeds. */
	private static final int RAIO_CAPIM = 14;
	/** He stops tilling new ground once the field has this many blocks of farmland. */
	private static final int MAX_ROCA = 48;
	private static final int CARGA_CHEIA = 32;
	private static final int SEMENTES_NA_MAO = 16;

	private Fase fase = Fase.TRABALHAR;
	private @Nullable BlockPos alvo;
	private int espera;
	private int travado;

	public RoceiroGoal(MoradorEntity morador) {
		super(morador, Profissao.ROCEIRO);
	}

	@Override
	public boolean canUse() {
		if (!hora() || !podeTrabalhar()) {
			return false;
		}
		if (colheita() >= CARGA_CHEIA && !armazens().isEmpty()) {
			fase = Fase.ENTREGAR;
			return true;
		}
		boolean naMao = sementeNaMao() != null;
		boolean noArmazem = sementesNoArmazem() > 0;
		alvo = proximaTarefa(naMao || noArmazem);
		if (alvo != null) {
			// Only needs to fetch seeds when the task is sowing/tilling and he has none on him.
			fase = !naMao && !madura(alvo) ? Fase.BUSCAR_SEMENTES : Fase.TRABALHAR;
			return true;
		}
		if (colheita() > 0 && !armazens().isEmpty()) {
			fase = Fase.ENTREGAR;
			return true;
		}
		if (!naMao && !noArmazem && temTerraVazia()) {
			alvo = procurarCapim();
			if (alvo != null) {
				fase = Fase.CAPINAR;
				return true;
			}
			morador.setStatus(Component.translatable("arraial.roceiro.sem_semente"));
			return false;
		}
		morador.setStatus(Component.translatable("arraial.roceiro.esperando"));
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return podeTrabalhar() && (fase == Fase.ENTREGAR || fase == Fase.BUSCAR_SEMENTES || alvo != null);
	}

	@Override
	public void start() {
		espera = 0;
		travado = 0;
		morador.segurar(new ItemStack(Items.IRON_HOE));
	}

	@Override
	public void stop() {
		super.stop();
		alvo = null;
	}

	@Override
	public void tick() {
		if (espera > 0) {
			espera--;
			return;
		}
		switch (fase) {
			case TRABALHAR -> trabalhar();
			case CAPINAR -> capinar();
			case BUSCAR_SEMENTES -> {
				var a = armazemMaisProximo();
				if (a == null || sementesNoArmazem() == 0) {
					alvo = null;
					fase = Fase.TRABALHAR;
					return;
				}
				morador.setStatus(Component.translatable("arraial.roceiro.buscando_semente"));
				if (!perto(a.getBlockPos(), 2.5)) {
					andarAte(a.getBlockPos());
					return;
				}
				for (ItemStack s : Vilas.retirar(armazens(), st -> semente(st.getItem()) != null, SEMENTES_NA_MAO)) {
					morador.guardar(s.getItem(), s.getCount());
				}
				bater();
				travado = 0;
				fase = Fase.TRABALHAR;
			}
			case ENTREGAR -> {
				var a = armazemMaisProximo();
				if (a == null) {
					alvo = null;
					fase = Fase.TRABALHAR;
					return;
				}
				morador.setStatus(Component.translatable("arraial.oficio.entregando", Component.translatable("arraial.roceiro.colheita")));
				if (!perto(a.getBlockPos(), 2.5)) {
					andarAte(a.getBlockPos());
					return;
				}
				entregar(sementesParaGuardar());
				alvo = null;
				fase = Fase.TRABALHAR; // alvo == null ends the goal; canUse decides what's next
			}
		}
	}

	private void trabalhar() {
		BlockPos p = alvo;
		if (p == null) {
			return;
		}
		ServerLevel level = level();
		morador.setStatus(Component.translatable("arraial.roceiro.na_roca"));
		if (morador.getEyePosition().distanceTo(Vec3.atCenterOf(p)) > 3.5 && travado++ < 100) {
			andarAte(p);
			return;
		}
		morador.getNavigation().stop();
		morador.getLookControl().setLookAt(Vec3.atCenterOf(p));

		BlockState state = level.getBlockState(p);
		boolean fez = false;
		if (madura(p)) {
			for (ItemStack drop : Block.getDrops(state, level, p, null)) {
				morador.guardar(drop.getItem(), drop.getCount());
			}
			level.destroyBlock(p, false, morador, 512);
			state = level.getBlockState(p);
			fez = true;
		}
		// Till watered ground first, then sow.
		if (state.isAir() && podeArar(level, p.below())) {
			if (sementeNaMao() != null) {
				level.setBlockAndUpdate(p.below(), Blocks.FARMLAND.defaultBlockState());
				level.playSound(null, p, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
				fez = true;
			}
		}
		if (state.isAir() && level.getBlockState(p.below()).is(Blocks.FARMLAND)) {
			Item s = sementeNaMao();
			CropBlock crop = s == null ? null : semente(s);
			if (crop != null) {
				level.setBlockAndUpdate(p, crop.defaultBlockState());
				morador.gastar(s, 1);
				fez = true;
			}
		}
		bater();
		travado = 0;
		espera = 10;
		if (!fez) {
			// Nothing could be done here (out of seeds): stop instead of hitting the same spot forever.
			alvo = null;
			return;
		}
		boolean naMao = sementeNaMao() != null;
		alvo = proximaTarefa(naMao); // keep going row by row
		if (alvo != null && !naMao && !madura(alvo)) {
			alvo = null; // out of seeds: end, canUse will send him to the armazém
		}
	}

	/** Cuts wild grass for seeds when the arraial has none at all. */
	private void capinar() {
		BlockPos p = alvo;
		if (p == null) {
			return;
		}
		ServerLevel level = level();
		BlockState s = level.getBlockState(p);
		if (!capim(s) || sementeNaMao() != null) {
			alvo = sementeNaMao() != null ? null : procurarCapim();
			return;
		}
		morador.setStatus(Component.translatable("arraial.roceiro.capinando"));
		if (morador.getEyePosition().distanceTo(Vec3.atCenterOf(p)) > 3.5 && travado++ < 120) {
			andarAte(p);
			return;
		}
		morador.getNavigation().stop();
		morador.getLookControl().setLookAt(Vec3.atCenterOf(p));
		for (ItemStack drop : Block.getDrops(s, level, p, null)) {
			morador.guardar(drop.getItem(), drop.getCount());
		}
		level.destroyBlock(p, false, morador, 512);
		bater();
		travado = 0;
		espera = 6;
		alvo = sementeNaMao() != null ? null : procurarCapim();
	}

	/**
	 * The nearest ripe crop; with seeds, also empty farmland and watered ground next to the
	 * field that can be tilled.
	 */
	private @Nullable BlockPos proximaTarefa(boolean comSemente) {
		BlockPos centro = morador.posto();
		if (centro == null) {
			return null;
		}
		ServerLevel level = level();
		int roca = 0;
		BlockPos melhor = null;
		double melhorDist = Double.MAX_VALUE;
		BlockPos arar = null;
		double ararDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(centro.offset(-RAIO, -2, -RAIO), centro.offset(RAIO, 2, RAIO))) {
			BlockState chao = level.getBlockState(p);
			BlockPos cima = p.above();
			if (chao.is(Blocks.FARMLAND)) {
				roca++;
				BlockState s = level.getBlockState(cima);
				boolean madura = s.getBlock() instanceof CropBlock c && c.isMaxAge(s);
				if (madura || (comSemente && s.isAir())) {
					double d = cima.distSqr(morador.blockPosition());
					if (d < melhorDist) {
						melhor = cima.immutable();
						melhorDist = d;
					}
				}
			} else if (comSemente && podeArar(level, p) && level.getBlockState(cima).isAir()) {
				double d = cima.distSqr(morador.blockPosition());
				if (d < ararDist) {
					arar = cima.immutable();
					ararDist = d;
				}
			}
		}
		if (melhor != null) {
			return melhor;
		}
		return roca < MAX_ROCA ? arar : null;
	}

	/** Grass or dirt with water close enough to keep farmland wet (like vanilla: 4 blocks around). */
	private boolean podeArar(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		if (!(s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT)) || !level.getBlockState(p.above()).isAir()) {
			return false;
		}
		BlockPos posto = morador.posto();
		if (posto != null && p.equals(posto.below())) {
			return false; // never dig out the ground under the balaio
		}
		for (BlockPos q : BlockPos.betweenClosed(p.offset(-4, 0, -4), p.offset(4, 1, 4))) {
			if (level.getFluidState(q).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	private boolean temTerraVazia() {
		return proximaTarefa(true) != null;
	}

	private @Nullable BlockPos procurarCapim() {
		BlockPos centro = morador.posto();
		if (centro == null) {
			return null;
		}
		ServerLevel level = level();
		BlockPos melhor = null;
		double melhorDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(centro.offset(-RAIO_CAPIM, -3, -RAIO_CAPIM), centro.offset(RAIO_CAPIM, 3, RAIO_CAPIM))) {
			if (capim(level.getBlockState(p))) {
				double d = p.distSqr(morador.blockPosition());
				if (d < melhorDist) {
					melhor = p.immutable();
					melhorDist = d;
				}
			}
		}
		return melhor;
	}

	private static boolean capim(BlockState s) {
		return s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN);
	}

	private boolean madura(BlockPos p) {
		BlockState s = level().getBlockState(p);
		return s.getBlock() instanceof CropBlock c && c.isMaxAge(s);
	}

	private int sementesNoArmazem() {
		return Vilas.contar(armazens(), s -> semente(s.getItem()) != null);
	}

	private @Nullable Item sementeNaMao() {
		for (Map.Entry<Item, Integer> e : morador.cargaCopia().entrySet()) {
			if (e.getValue() > 0 && semente(e.getKey()) != null) {
				return e.getKey();
			}
		}
		return null;
	}

	/** The crop block an item plants (wheat seeds, carrot, potato, beetroot seeds...), or null. */
	private static @Nullable CropBlock semente(Item item) {
		return item instanceof BlockItem bi && bi.getBlock() instanceof CropBlock c ? c : null;
	}

	private int colheita() {
		int total = 0;
		for (Map.Entry<Item, Integer> e : morador.cargaCopia().entrySet()) {
			if (semente(e.getKey()) == null) {
				total += e.getValue();
			}
		}
		return total;
	}

	private Map<Item, Integer> sementesParaGuardar() {
		Map<Item, Integer> m = new HashMap<>();
		for (Item i : morador.cargaCopia().keySet()) {
			if (semente(i) != null) {
				m.put(i, SEMENTES_NA_MAO);
			}
		}
		return m;
	}
}
