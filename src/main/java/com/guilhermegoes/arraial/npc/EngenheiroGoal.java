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
 * <li><b>Pontes</b>: where a street or road meets water, a bridge: a deck one block over the water,
 * three wide, with railings and log pillars down to the bottom;</li>
 * <li><b>Estradas</b>: a beaten-earth road out of the arraial towards its two nearest neighbours
 * (some seventy blocks). A finished road (bridges included) makes the troop to that neighbour
 * quicker and safer (see {@link com.guilhermegoes.arraial.vila.Caravana});</li>
 * <li><b>Iluminação</b>: a lamp post (fence + torch) beside the street wherever it is dark.</li>
 * </ul>
 * Streets cost nothing (he beats the earth with a shovel); bridges take planks and lamp posts take
 * a fence and a torch from the armazém.
 */
public class EngenheiroGoal extends OficioGoal {
	private enum Tarefa { RUA, PONTE, POSTE }

	private record Trabalho(Tarefa tarefa, BlockPos pos, net.minecraft.core.Direction.Axis lateral, boolean pilar) {
		Trabalho(Tarefa tarefa, BlockPos pos) {
			this(tarefa, pos, net.minecraft.core.Direction.Axis.X, false);
		}
	}

	/** A street (to the Cruzeiro) or a road (towards a neighbour, {@code destino} its id). */
	private record Rua(List<BlockPos> colunas, net.minecraft.core.Direction.Axis lateral, @Nullable String destino) {
	}

	/** How far out of the Cruzeiro the road to a neighbour goes. */
	private static final int ESTRADA = Vilas.RAIO + 24;

	private @Nullable Trabalho trabalho;
	private int tempo;
	/** Spots he could not reach (a cliff, a fenced garden): skipped for a few days. */
	private final java.util.Map<BlockPos, Long> desistiu = new java.util.HashMap<>();
	private static final int DESISTE = 1200;

	/** A bridge span gets a minute; a patch of street he cannot reach, twenty seconds. */
	private static int limite(Trabalho t) {
		return t.tarefa() == Tarefa.PONTE ? DESISTE : DESISTE / 3;
	}
	/** The bridge span he last fetched planks for (a span with pillars takes a good dozen). */
	private @Nullable BlockPos abastecido;
	/** Ticks spent in the water (fell off a half-built bridge). */
	private int molhado;

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
		return podeTrabalhar() && trabalho != null && tempo < limite(trabalho);
	}

	@Override
	public void start() {
		tempo = 0;
	}

	@Override
	public void stop() {
		super.stop();
		if (trabalho != null && tempo >= limite(trabalho)) {
			desistiu.put(trabalho.pos(), level().getGameTime());
		}
		trabalho = null;
	}

	/** Across a line running (dx, dz): the horizontal axis the bridge's width and railings go along. */
	private static net.minecraft.core.Direction.Axis lateral(int dx, int dz) {
		return Math.abs(dx) >= Math.abs(dz) ? net.minecraft.core.Direction.Axis.Z : net.minecraft.core.Direction.Axis.X;
	}

	/** Every street (from each finished building's marker to the Cruzeiro) and the roads out. */
	private List<Rua> ruas() {
		List<Rua> ruas = new ArrayList<>();
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
			ruas.add(new Rua(rua, lateral(x1 - x0, z1 - z0), null));
		}
		// The roads: out of the arraial towards the two nearest neighbours (at peace, same world).
		var a = morador.arraial();
		if (a != null) { // (an engenheiro only comes once the arraial is established)
			String meu = com.guilhermegoes.arraial.vila.Economia.id(level(), vila);
			List<java.util.Map.Entry<String, com.guilhermegoes.arraial.vila.Registro.Info>> vizinhos = new ArrayList<>();
			for (var e : com.guilhermegoes.arraial.vila.Registro.todas().entrySet()) {
				var v = e.getValue();
				if (!e.getKey().equals(meu) && !v.abandonada && v.dimensao.equals(level().dimension().identifier().toString())
						&& v.posicao().distSqr(vila) > (double) ESTRADA * ESTRADA
						&& v.posicao().distSqr(vila) < (double) com.guilhermegoes.arraial.vila.Economia.ALCANCE * com.guilhermegoes.arraial.vila.Economia.ALCANCE) {
					vizinhos.add(e);
				}
			}
			vizinhos.sort(java.util.Comparator.comparingDouble(e -> e.getValue().posicao().distSqr(vila)));
			for (var e : vizinhos.subList(0, Math.min(2, vizinhos.size()))) {
				BlockPos alvo = e.getValue().posicao();
				double dx = alvo.getX() - vila.getX(), dz = alvo.getZ() - vila.getZ(), d = Math.sqrt(dx * dx + dz * dz);
				List<BlockPos> estrada = new ArrayList<>();
				for (int i = 6; i <= ESTRADA; i++) {
					int x = vila.getX() + (int) Math.round(dx / d * i), z = vila.getZ() + (int) Math.round(dz / d * i);
					boolean dentro = false;
					for (BoundingBox b : predios) {
						if (x >= b.minX() - 1 && x <= b.maxX() + 1 && z >= b.minZ() - 1 && z <= b.maxZ() + 1) {
							dentro = true;
						}
					}
					if (!dentro) {
						estrada.add(new BlockPos(x, 0, z));
					}
				}
				ruas.add(new Rua(estrada, lateral((int) Math.round(dx), (int) Math.round(dz)), e.getKey()));
			}
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
		var a = morador.arraial();
		desistiu.values().removeIf(t -> level().getGameTime() - t > 3 * 24000L);
		for (Rua rua : ruas()) {
			int desdeLuz = 99;
			int i = 0;
			boolean inteira = true;
			for (BlockPos coluna : rua.colunas()) {
				i++;
				BlockPos s = superficie(coluna);
				if (!level().isLoaded(s)) {
					inteira = false;
					break;
				}
				if (desistiu.containsKey(s.above()) && level().getFluidState(s).is(FluidTags.WATER)) {
					inteira = false;
					break; // a span he could not build: nothing beyond it until the bridge is whole
				}
				if (desistiu.containsKey(s)) {
					// A cliff or a fenced plot: the road goes as far as the land lets it (done if it got two thirds of the way).
					if (i < rua.colunas().size() * 2 / 3) {
						inteira = false;
					}
					break;
				}
				BlockState st = level().getBlockState(s);
				if (level().getFluidState(s).is(FluidTags.WATER)) {
					// The deck goes one block over the water, a pillar every third span.
					return new Trabalho(Tarefa.PONTE, s.above().immutable(), rua.lateral(), i % 3 == 0);
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
			// The whole road is done: from now on the troop to that neighbour goes by it.
			if (inteira && rua.destino() != null && a != null && a.marcarEstrada(rua.destino())) {
				var v = com.guilhermegoes.arraial.vila.Registro.get(rua.destino());
				Component texto = Component.translatable("arraial.cronica.estrada_pronta", morador.nome(), v == null ? "?" : v.nome);
				com.guilhermegoes.arraial.vila.Cronica.anotar(level(), morador.vila(), texto);
				com.guilhermegoes.arraial.vila.Prefeitura.avisar(level(), a, Component.literal("🛤 ").append(texto)
						.withStyle(net.minecraft.ChatFormatting.GOLD));
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
		// Fell off the edge of the bridge? Climb back out onto the deck or the bank.
		if (morador.isInWater()) {
			if (++molhado > 60) {
				sairDaAgua();
				molhado = 0;
			}
		} else {
			molhado = 0;
		}
		Receitas.Madeira madeira = Estoque.madeiraDaVila(Vilas.inventario(armazens()));
		Item precisa = switch (t.tarefa()) {
			case RUA -> null;
			case PONTE -> madeira.tabua();
			case POSTE -> morador.carregando(madeira.cerca()) > 0 && morador.carregando(Items.TORCH) == 0 ? Items.TORCH : madeira.cerca();
		};
		if (precisa != null && morador.carregando(precisa) == 0 || t.tarefa() == Tarefa.POSTE && morador.carregando(Items.TORCH) == 0
				|| t.tarefa() == Tarefa.PONTE && morador.carregando(madeira.tabua()) < 12 && !t.pos().equals(abastecido)) {
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
				ok = pegar(madeira.tabua(), 32 - morador.carregando(madeira.tabua())) > 0 || morador.carregando(madeira.tabua()) > 0;
			} else if (t.tarefa() == Tarefa.POSTE) {
				if (morador.carregando(madeira.cerca()) == 0) {
					ok = pegar(madeira.cerca(), 4) > 0;
				}
				if (ok && morador.carregando(Items.TORCH) == 0) {
					ok = pegar(Items.TORCH, 8) > 0;
				}
			}
			if (t.tarefa() == Tarefa.PONTE) {
				abastecido = t.pos();
			}
			tempo = 0; // the walk to the armazém does not count against the job
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
				ponte(t, madeira);
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

	/**
	 * One span of the bridge: the deck five wide (planks), railings on the outer two, and every third
	 * span log pillars under the railings down to the river bed. Every block costs a plank.
	 */
	private void ponte(Trabalho t, Receitas.Madeira madeira) {
		BlockState tabua = Block.byItem(madeira.tabua()).defaultBlockState();
		BlockState cerca = Block.byItem(madeira.cerca()).defaultBlockState();
		BlockState tora = Block.byItem(madeira.tora()).defaultBlockState();
		net.minecraft.core.Direction lado = t.lateral() == net.minecraft.core.Direction.Axis.X ? net.minecraft.core.Direction.EAST
				: net.minecraft.core.Direction.SOUTH;
		for (int l : new int[] {0, -1, 1, -2, 2}) { // the middle first: walkable even if the planks run out
			BlockPos deque = t.pos().relative(lado, l);
			if (!por(deque, tabua)) {
				continue;
			}
			if (Math.abs(l) == 2) {
				por(deque.above(), cerca);
				if (t.pilar()) {
					BlockPos p = deque.below();
					for (int n = 0; n < 8 && (level().getFluidState(p).is(FluidTags.WATER) || level().getBlockState(p).canBeReplaced()); n++) {
						por(p, tora);
						p = p.below();
					}
				}
			}
		}
	}

	/** The nearest dry footing (a solid block under two free ones) within a few blocks. */
	private void sairDaAgua() {
		BlockPos aqui = morador.blockPosition();
		BlockPos melhor = null;
		double d = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(aqui.offset(-4, -1, -4), aqui.offset(4, 3, 4))) {
			if (level().getBlockState(p.below()).isFaceSturdy(level(), p.below(), net.minecraft.core.Direction.UP)
					&& level().getFluidState(p).isEmpty() && level().getBlockState(p).getCollisionShape(level(), p).isEmpty()
					&& level().getBlockState(p.above()).getCollisionShape(level(), p.above()).isEmpty() && p.distSqr(aqui) < d) {
				d = p.distSqr(aqui);
				melhor = p.immutable();
			}
		}
		if (melhor != null) {
			morador.getNavigation().stop();
			morador.teleportTo(melhor.getX() + 0.5, melhor.getY(), melhor.getZ() + 0.5);
		}
	}

	/** Places a block where there is only water, air or plants, if a plank is left to pay for it. */
	private boolean por(BlockPos p, BlockState estado) {
		BlockState atual = level().getBlockState(p);
		if (atual.is(estado.getBlock())) {
			return true;
		}
		if (!atual.canBeReplaced() && !level().getFluidState(p).is(FluidTags.WATER)) {
			return false;
		}
		Item tabua = morador.cargaCopia().keySet().stream().filter(it -> it.getDefaultInstance().is(net.minecraft.tags.ItemTags.PLANKS)).findFirst().orElse(null);
		if (tabua == null) {
			return false;
		}
		level().setBlock(p, estado, Block.UPDATE_ALL);
		morador.gastar(tabua, 1);
		return true;
	}
}
