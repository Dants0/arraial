package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import com.guilhermegoes.arraial.obra.PlanoDeObra;
import com.guilhermegoes.arraial.trabalho.Estoque;
import com.guilhermegoes.arraial.trabalho.Receitas;
import com.guilhermegoes.arraial.vila.Vilas;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * The engenheiro looks after the arraial's infrastructure, one job at a time:
 * <ul>
 * <li><b>Ruas</b>: a beaten-earth street from every finished building's door to the Cruzeiro;</li>
 * <li><b>Pontes</b>: where the street meets water, a plank deck across it;</li>
 * <li><b>Iluminação</b>: a lamp post (fence + torch) beside the street wherever it is dark.</li>
 * </ul>
 * Streets cost nothing (he beats the earth with a shovel); bridges take planks and lamp posts take
 * a fence and a torch from the armazém.
 */
public class EngenheiroGoal extends OficioGoal {
	private enum Tarefa { RUA, PONTE, POSTE }

	private record Trabalho(Tarefa tarefa, BlockPos pos) {
	}

	private @Nullable Trabalho trabalho;
	private int tempo;

	public EngenheiroGoal(MoradorEntity morador) {
		super(morador, Profissao.ENGENHEIRO);
	}

	@Override
	public boolean canUse() {
		if (!hora() || !podeTrabalhar() || morador.vila() == null) {
			return false;
		}
		trabalho = proximo();
		if (trabalho == null) {
			morador.setStatus(Component.translatable("arraial.engenheiro.tudo_pronto"));
			return false;
		}
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return podeTrabalhar() && trabalho != null && tempo < 1200;
	}

	@Override
	public void start() {
		tempo = 0;
	}

	@Override
	public void stop() {
		super.stop();
		trabalho = null;
	}

	/** Every street: from each finished building's marker towards the Cruzeiro. */
	private List<List<BlockPos>> ruas() {
		List<List<BlockPos>> ruas = new ArrayList<>();
		BlockPos vila = morador.vila();
		List<BoundingBox> predios = new ArrayList<>();
		List<BlockPos> marcos = new ArrayList<>();
		for (BlockPos p : CanteiroBlockEntity.ativos(level())) {
			if (p.closerThan(vila, Vilas.RAIO) && level().getBlockEntity(p) instanceof CanteiroBlockEntity c && c.concluida()
					&& !c.projeto().map(com.guilhermegoes.arraial.obra.Muralha::e).orElse(true)) {
				c.plano(level()).map(PlanoDeObra::area).ifPresent(predios::add);
				marcos.add(p);
			}
		}
		for (BlockPos m : marcos) {
			List<BlockPos> rua = new ArrayList<>();
			int x0 = m.getX(), z0 = m.getZ(), x1 = vila.getX(), z1 = vila.getZ();
			int passos = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
			for (int i = 1; i < passos - 2; i++) {
				int x = x0 + Math.round((x1 - x0) * i / (float) passos);
				int z = z0 + Math.round((z1 - z0) * i / (float) passos);
				BlockPos coluna = new BlockPos(x, 0, z);
				boolean dentro = false;
				for (BoundingBox b : predios) {
					if (x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ()) {
						dentro = true;
					}
				}
				if (!dentro) {
					rua.add(coluna);
				}
			}
			ruas.add(rua);
		}
		return ruas;
	}

	/** The surface block of a column (ignoring plants and leaves). */
	private BlockPos superficie(BlockPos coluna) {
		return level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, coluna).below();
	}

	/** The arraial's era decides the street surface and the lamp (see Era). */
	private com.guilhermegoes.arraial.vila.Era era() {
		var a = morador.arraial();
		return a == null ? com.guilhermegoes.arraial.vila.Era.COLONIAL : a.era();
	}

	private @Nullable Trabalho proximo() {
		for (List<BlockPos> rua : ruas()) {
			int desdeLuz = 99;
			for (BlockPos coluna : rua) {
				BlockPos s = superficie(coluna);
				if (!level().isLoaded(s)) {
					break;
				}
				BlockState st = level().getBlockState(s);
				if (level().getFluidState(s).is(FluidTags.WATER)) {
					return new Trabalho(Tarefa.PONTE, s.immutable());
				}
				if (st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT) || st.is(Blocks.COARSE_DIRT) || st.is(Blocks.PODZOL)
						|| st.is(Blocks.ROOTED_DIRT) || st.is(Blocks.MYCELIUM)) {
					return new Trabalho(Tarefa.RUA, s.immutable());
				}
				// A lamp post every few blocks where it is dark at night.
				desdeLuz++;
				if (desdeLuz >= 7) {
					BlockPos lado = s.above().east();
					if (level().getBrightness(LightLayer.BLOCK, s.above()) < 8 && level().getBlockState(lado).canBeReplaced()
							&& level().getBlockState(lado.above()).canBeReplaced()
							&& level().getBlockState(lado.below()).isFaceSturdy(level(), lado.below(), net.minecraft.core.Direction.UP)) {
						return new Trabalho(Tarefa.POSTE, lado.immutable());
					}
					if (level().getBrightness(LightLayer.BLOCK, s.above()) >= 8) {
						desdeLuz = 0;
					}
				}
			}
		}
		return null;
	}

	@Override
	public void tick() {
		Trabalho t = trabalho;
		if (t == null) {
			return;
		}
		tempo++;
		Receitas.Madeira madeira = Estoque.madeiraDaVila(Vilas.inventario(armazens()));
		Item precisa = switch (t.tarefa()) {
			case RUA -> null;
			case PONTE -> madeira.tabua();
			case POSTE -> morador.carregando(madeira.cerca()) > 0 && morador.carregando(Items.TORCH) == 0 ? Items.TORCH : madeira.cerca();
		};
		if (precisa != null && morador.carregando(precisa) == 0 || t.tarefa() == Tarefa.POSTE && morador.carregando(Items.TORCH) == 0) {
			// Fetch materials first (planks for a bridge; a fence and a torch for a lamp post).
			var a = armazemMaisProximo();
			if (a == null) {
				trabalho = null;
				return;
			}
			morador.setStatus(Component.translatable("arraial.engenheiro.material"));
			if (!perto(a.getBlockPos(), 2.5)) {
				andarAte(a.getBlockPos());
				return;
			}
			boolean ok = true;
			if (t.tarefa() == Tarefa.PONTE) {
				ok = pegar(madeira.tabua(), 16) > 0 || morador.carregando(madeira.tabua()) > 0;
			} else if (t.tarefa() == Tarefa.POSTE) {
				if (morador.carregando(madeira.cerca()) == 0) {
					ok = pegar(madeira.cerca(), 4) > 0;
				}
				if (ok && morador.carregando(Items.TORCH) == 0) {
					ok = pegar(Items.TORCH, 8) > 0;
				}
			}
			bater();
			if (!ok) {
				morador.setStatus(Component.translatable("arraial.engenheiro.falta"));
				trabalho = null;
			}
			return;
		}
		morador.setStatus(Component.translatable("arraial.engenheiro." + t.tarefa().name().toLowerCase(java.util.Locale.ROOT)));
		if (!perto(t.pos(), 3.0)) {
			andarAte(t.pos().above());
			return;
		}
		morador.getNavigation().stop();
		morador.getLookControl().setLookAt(net.minecraft.world.phys.Vec3.atCenterOf(t.pos()));
		switch (t.tarefa()) {
			case RUA -> {
				morador.segurar(new ItemStack(Items.IRON_SHOVEL));
				BlockState acima = level().getBlockState(t.pos().above());
				if (acima.canBeReplaced() && !acima.isAir()) {
					level().destroyBlock(t.pos().above(), false);
				}
				level().setBlock(t.pos(), era().rua().defaultBlockState(), Block.UPDATE_ALL);
				level().playSound(null, t.pos(), SoundEvents.SHOVEL_FLATTEN.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			case PONTE -> {
				morador.segurar(new ItemStack(madeira.tabua()));
				level().setBlock(t.pos(), Block.byItem(madeira.tabua()).defaultBlockState(), Block.UPDATE_ALL);
				morador.gastar(madeira.tabua(), 1);
				level().playSound(null, t.pos(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			case POSTE -> {
				morador.segurar(new ItemStack(Items.TORCH));
				level().setBlock(t.pos(), Block.byItem(madeira.cerca()).defaultBlockState(), Block.UPDATE_ALL);
				level().setBlock(t.pos().above(), era().luz().defaultBlockState(), Block.UPDATE_ALL);
				morador.gastar(madeira.cerca(), 1);
				morador.gastar(Items.TORCH, 1);
				level().playSound(null, t.pos(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
		}
		bater();
		trabalho = proximo();
	}
}
