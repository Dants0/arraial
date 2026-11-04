package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.vila.Vilas;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * The roceiro tends the farmland around his balaio: harvests ripe crops, replants right away,
 * sows empty farmland with seeds from the armazém, and stores the harvest. (The player
 * prepares the farmland; the roceiro keeps it going.)
 */
public class RoceiroGoal extends OficioGoal {
	private enum Fase { TRABALHAR, BUSCAR_SEMENTES, ENTREGAR }

	private static final int RAIO = 9;
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
		alvo = proximaTarefa();
		if (alvo != null) {
			boolean precisaSemente = level().getBlockState(alvo).isAir() && sementeNaMao() == null;
			fase = precisaSemente ? Fase.BUSCAR_SEMENTES : Fase.TRABALHAR;
			if (precisaSemente && Vilas.contar(armazens(), s -> semente(s.getItem()) != null) == 0) {
				morador.setStatus(Component.translatable("arraial.roceiro.sem_semente"));
				if (colheita() > 0 && !armazens().isEmpty()) {
					fase = Fase.ENTREGAR; // at least store what was harvested
					return true;
				}
				return false;
			}
			return true;
		}
		if (colheita() > 0 && !armazens().isEmpty()) {
			fase = Fase.ENTREGAR;
			return true;
		}
		morador.setStatus(Component.translatable("arraial.roceiro.esperando"));
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return podeTrabalhar() && (fase != Fase.TRABALHAR || alvo != null);
	}

	@Override
	public void start() {
		espera = 0;
		travado = 0;
		morador.segurar(new ItemStack(Items.IRON_HOE));
	}

	@Override
	public void tick() {
		if (espera > 0) {
			espera--;
			return;
		}
		switch (fase) {
			case TRABALHAR -> trabalhar();
			case BUSCAR_SEMENTES -> {
				var a = armazemMaisProximo();
				if (a == null) {
					alvo = null;
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
				fase = Fase.TRABALHAR;
			}
			case ENTREGAR -> {
				var a = armazemMaisProximo();
				if (a == null) {
					alvo = null;
					return;
				}
				morador.setStatus(Component.translatable("arraial.oficio.entregando", Component.translatable("arraial.roceiro.colheita")));
				if (!perto(a.getBlockPos(), 2.5)) {
					andarAte(a.getBlockPos());
					return;
				}
				entregar(sementesParaGuardar());
				alvo = null;
				fase = Fase.TRABALHAR;
			}
		}
	}

	private void trabalhar() {
		if (alvo == null) {
			return;
		}
		ServerLevel level = level();
		morador.setStatus(Component.translatable("arraial.roceiro.na_roca"));
		if (morador.getEyePosition().distanceTo(Vec3.atCenterOf(alvo)) > 3.5 && travado++ < 100) {
			andarAte(alvo);
			return;
		}
		morador.getNavigation().stop();
		morador.getLookControl().setLookAt(Vec3.atCenterOf(alvo));
		BlockState state = level.getBlockState(alvo);
		if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
			for (ItemStack drop : Block.getDrops(state, level, alvo, null)) {
				morador.guardar(drop.getItem(), drop.getCount());
			}
			level.destroyBlock(alvo, false, morador, 512);
			state = level.getBlockState(alvo);
		}
		if (state.isAir() && level.getBlockState(alvo.below()).is(Blocks.FARMLAND)) {
			Item s = sementeNaMao();
			if (s != null && semente(s) != null) {
				level.setBlockAndUpdate(alvo, semente(s).defaultBlockState());
				morador.gastar(s, 1);
			}
		}
		bater();
		travado = 0;
		espera = 10;
		alvo = proximaTarefa(); // keep going row by row
	}

	/** The nearest ripe crop, or empty farmland we have seeds for (or can fetch seeds for). */
	private @Nullable BlockPos proximaTarefa() {
		BlockPos centro = morador.posto();
		if (centro == null) {
			return null;
		}
		ServerLevel level = level();
		BlockPos melhor = null;
		double melhorDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(centro.offset(-RAIO, -2, -RAIO), centro.offset(RAIO, 2, RAIO))) {
			if (!level.getBlockState(p).is(Blocks.FARMLAND)) {
				continue;
			}
			BlockPos cima = p.above();
			BlockState s = level.getBlockState(cima);
			boolean madura = s.getBlock() instanceof CropBlock c && c.isMaxAge(s);
			if (madura || s.isAir()) {
				double d = cima.distSqr(morador.blockPosition());
				if (d < melhorDist) {
					melhor = cima.immutable();
					melhorDist = d;
				}
			}
		}
		return melhor;
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
