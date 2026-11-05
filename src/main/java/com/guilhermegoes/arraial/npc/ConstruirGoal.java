package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.obra.CanteiroBlock;
import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import com.guilhermegoes.arraial.obra.PlanoDeObra;
import com.guilhermegoes.arraial.trabalho.Demanda;
import com.guilhermegoes.arraial.vila.ArmazemBlockEntity;
import com.guilhermegoes.arraial.vila.Cronica;
import com.guilhermegoes.arraial.vila.Vilas;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The pedreiro's job, as a small state machine:
 * find a site → (go to the chest and load up) → walk to the next block → place it → repeat.
 */
public class ConstruirGoal extends Goal {
	private static final int RAIO_BUSCA = 32;
	/** At most this many pedreiros share a site while others wait for hands. */
	private static final int MAX_POR_OBRA = 2;
	/** How many upcoming steps he loads materials for on each chest trip. */
	private static final int ETAPAS_POR_VIAGEM = 32;
	private static final double ALCANCE = 4.5;
	private static final double VELOCIDADE = 1.0;

	private final MoradorEntity pedreiro;
	private int espera;
	private int travado;
	private double ultimaDistancia = Double.MAX_VALUE;
	private int recalcularCaminho;
	private int proximaBusca;
	private int esperas;

	public ConstruirGoal(MoradorEntity pedreiro) {
		this.pedreiro = pedreiro;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		// The goal selector only polls on every other tick (parity depends on the entity id),
		// so throttle with our own countdown instead of tickCount % N.
		if (--proximaBusca > 0) {
			return false;
		}
		proximaBusca = 10;
		ServerLevel level = getServerLevel(pedreiro);
		if (pedreiro.profissao() != Profissao.PEDREIRO || !level.isBrightOutside() || pedreiro.comFome()) {
			return false; // only pedreiros build, and only by day
		}
		CanteiroBlockEntity atual = canteiro(level, pedreiro.obra());
		if (atual != null && atual.temTrabalho()) {
			return true;
		}
		BlockPos encontrado = procurarObra(level);
		pedreiro.setObra(encontrado);
		if (encontrado == null) {
			pedreiro.setStatus(Component.translatable("arraial.pedreiro.sem_obra"));
		}
		return encontrado != null;
	}

	@Override
	public boolean canContinueToUse() {
		ServerLevel level = getServerLevel(pedreiro);
		CanteiroBlockEntity c = canteiro(level, pedreiro.obra());
		return c != null && c.temTrabalho() && level.isBrightOutside();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		espera = 0;
		travado = 0;
		ultimaDistancia = Double.MAX_VALUE;
	}

	@Override
	public void stop() {
		pedreiro.getNavigation().stop();
		pedreiro.segurar(ItemStack.EMPTY);
		pedreiro.setStatus(Component.empty());
	}

	@Override
	public void tick() {
		if (espera > 0) {
			espera--;
			return;
		}
		ServerLevel level = getServerLevel(pedreiro);
		CanteiroBlockEntity canteiro = canteiro(level, pedreiro.obra());
		if (canteiro == null) {
			return;
		}
		PlanoDeObra plano = canteiro.plano(level).orElse(null);
		if (plano == null) {
			pedreiro.setStatus(Component.translatable("arraial.pedreiro.sem_planta"));
			espera = 100;
			return;
		}

		List<PlanoDeObra.Etapa> etapas = plano.etapas();
		while (canteiro.progresso() < etapas.size() && PlanoDeObra.pronta(level, etapas.get(canteiro.progresso()))) {
			canteiro.avancar();
		}
		if (canteiro.progresso() >= etapas.size()) {
			concluir(level, canteiro);
			return;
		}

		PlanoDeObra.Etapa etapa = etapas.get(canteiro.progresso());
		PlanoDeObra.Custo custo = PlanoDeObra.custo(etapa.state());
		if (custo != null && pedreiro.carregando(custo.item()) < custo.quantidade()) {
			buscarMateriais(level, canteiro, plano, custo);
		} else {
			trabalhar(level, canteiro, plano, etapa, custo);
		}
	}

	private void buscarMateriais(ServerLevel level, CanteiroBlockEntity canteiro, PlanoDeObra plano, PlanoDeObra.Custo precisa) {
		// Where to fetch from: the site chest if it has the item, otherwise any armazém of the arraial.
		Container bauDaObra = canteiro.bau(level);
		BlockPos vila = vilaDa(level, canteiro);
		List<ArmazemBlockEntity> armazens = Vilas.armazens(level, vila);
		Container bau = null;
		boolean reservado = false;
		if (bauDaObra != null && CanteiroBlock.contar(bauDaObra, precisa.item()) > 0) {
			bau = bauDaObra;
		} else if (livreNoArmazem(level, canteiro, precisa.item()) > 0) {
			for (ArmazemBlockEntity a : armazens) {
				if (CanteiroBlock.contar(a, precisa.item()) > 0) {
					bau = a;
					break;
				}
			}
		} else {
			// The armazém may have it, but a more urgent site has claimed it.
			reservado = Vilas.contar(armazens, precisa.item()) > 0;
		}
		if (bau == null) {
			if (bauDaObra == null && armazens.isEmpty()) {
				pedreiro.setStatus(Component.translatable("arraial.pedreiro.sem_bau"));
			} else if (reservado) {
				pedreiro.setStatus(Component.translatable("arraial.pedreiro.reservado", precisa.item().getDefaultInstance().getHoverName()));
			} else {
				// There is somewhere to take from, it just doesn't have this yet: wait for the
				// trades (or the player) to bring it, and say exactly what is missing.
				int total = plano.materiais(canteiro.progresso()).getOrDefault(precisa.item(), 0) - pedreiro.carregando(precisa.item());
				pedreiro.setStatus(Component.translatable("arraial.pedreiro.esperando",
						Math.max(total, 1), precisa.item().getDefaultInstance().getHoverName()));
			}
			andarAte(canteiro.getBlockPos());
			espera = 60;
			// After a while, look again: another site may have what it needs.
			if (++esperas >= 3) {
				esperas = 0;
				pedreiro.setObra(null);
			}
			return;
		}
		esperas = 0;
		BlockPos posBau = ((net.minecraft.world.level.block.entity.BlockEntity) bau).getBlockPos();
		if (!perto(posBau, 3.0)) {
			pedreiro.setStatus(Component.translatable("arraial.pedreiro.indo_bau"));
			andarAte(posBau);
			return;
		}

		pedreiro.getNavigation().stop();
		pedreiro.getLookControl().setLookAt(Vec3.atCenterOf(posBau));
		// Load what the next stretch of work needs (minus what he already carries).
		Map<Item, Integer> lista = new LinkedHashMap<>();
		List<PlanoDeObra.Etapa> etapas = plano.etapas();
		int fim = Math.min(etapas.size(), canteiro.progresso() + ETAPAS_POR_VIAGEM);
		for (int i = canteiro.progresso(); i < fim; i++) {
			PlanoDeObra.Custo c = PlanoDeObra.custo(etapas.get(i).state());
			if (c != null) {
				lista.merge(c.item(), c.quantidade(), Integer::sum);
			}
		}
		boolean doArmazem = bau != bauDaObra;
		for (Map.Entry<Item, Integer> e : lista.entrySet()) {
			int falta = e.getValue() - pedreiro.carregando(e.getKey());
			if (doArmazem) {
				// Leave what more urgent sites have claimed.
				falta = Math.min(falta, livreNoArmazem(level, canteiro, e.getKey()));
			}
			if (falta > 0) {
				pedreiro.guardar(e.getKey(), retirar(bau, e.getKey(), falta));
			}
		}
		pedreiro.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);

		if (pedreiro.carregando(precisa.item()) < precisa.quantidade()) {
			int noBau = CanteiroBlock.contar(bau, precisa.item());
			int total = plano.materiais(canteiro.progresso()).getOrDefault(precisa.item(), 0) - pedreiro.carregando(precisa.item()) - noBau;
			pedreiro.setStatus(Component.translatable("arraial.pedreiro.falta", Math.max(total, 1), precisa.item().getDefaultInstance().getHoverName()));
			espera = 100;
		}
	}

	private void trabalhar(ServerLevel level, CanteiroBlockEntity canteiro, PlanoDeObra plano, PlanoDeObra.Etapa etapa,
			PlanoDeObra.@Nullable Custo custo) {
		int pct = canteiro.progresso() * 100 / plano.etapas().size();
		pedreiro.setStatus(Component.translatable("arraial.pedreiro.construindo", canteiro.projeto().get().nome(), pct));
		pedreiro.segurar(custo == null ? ItemStack.EMPTY : new ItemStack(custo.item()));

		BlockPos alvo = etapa.pos();
		double dist = pedreiro.getEyePosition().distanceTo(Vec3.atCenterOf(alvo));
		// Leniency: if he cannot path closer (e.g. roof blocks), he still reaches a bit further after a while.
		boolean alcanca = dist <= ALCANCE || (travado > 60 && dist <= 8.0);
		if (!alcanca) {
			if (dist < ultimaDistancia - 0.1) {
				ultimaDistancia = dist;
				travado = 0;
			} else if (++travado > 200) {
				// Truly stuck: hop next to the marker and try again from there.
				BlockPos m = canteiro.getBlockPos();
				pedreiro.teleportTo(m.getX() + 0.5, m.getY() + 1, m.getZ() + 0.5);
				travado = 0;
				ultimaDistancia = Double.MAX_VALUE;
			}
			andarAte(alvo);
			return;
		}

		pedreiro.getNavigation().stop();
		pedreiro.getLookControl().setLookAt(Vec3.atCenterOf(alvo));
		colocar(level, etapa, custo);
		canteiro.avancar();
		travado = 0;
		ultimaDistancia = Double.MAX_VALUE;
		espera = etapa.limpeza() ? 3 : 6;
	}

	private void colocar(ServerLevel level, PlanoDeObra.Etapa etapa, PlanoDeObra.@Nullable Custo custo) {
		BlockPos pos = etapa.pos();
		BlockState atual = level.getBlockState(pos);
		if (!atual.isAir() && !atual.canBeReplaced()) {
			level.destroyBlock(pos, true, pedreiro, 512);
		} else if (!atual.isAir() && etapa.limpeza()) {
			level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		if (!etapa.limpeza()) {
			if (custo != null) {
				pedreiro.gastar(custo.item(), custo.quantidade());
			}
			// UPDATE_CLIENTS only: two-part blocks (doors) must not pop off before their other half is placed.
			level.setBlock(pos, etapa.state(), Block.UPDATE_CLIENTS);
			conectar(level, pos);
			level.playSound(null, pos, etapa.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
		}
		pedreiro.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
	}

	/**
	 * Fences, walls, panes, gates and stairs join up with their neighbours, like when a player
	 * places them (the blueprint stores them unconnected). Doors and beds are left alone: their
	 * other half may not be placed yet.
	 */
	private static void conectar(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (!(s.is(net.minecraft.tags.BlockTags.FENCES) || s.is(net.minecraft.tags.BlockTags.WALLS)
				|| s.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock
				|| s.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock
				|| s.getBlock() instanceof net.minecraft.world.level.block.StairBlock)) {
			return;
		}
		BlockState ajustado = Block.updateFromNeighbourShapes(s, level, pos);
		if (ajustado != s) {
			level.setBlock(pos, ajustado, Block.UPDATE_CLIENTS);
		}
		ajustado.updateNeighbourShapes(level, pos, Block.UPDATE_CLIENTS);
	}

	private void concluir(ServerLevel level, CanteiroBlockEntity canteiro) {
		if (canteiro.concluida()) {
			pedreiro.setObra(null); // a colleague placed the last block and already announced it
			return;
		}
		canteiro.concluir();
		pedreiro.segurar(ItemStack.EMPTY);
		Component nomeObra = canteiro.projeto().get().nome();
		pedreiro.setStatus(Component.translatable("arraial.pedreiro.terminou", nomeObra));
		Cronica.anotar(level, Vilas.maisProxima(level, canteiro.getBlockPos()),
				Component.translatable("arraial.cronica.obra", pedreiro.nome(), nomeObra));
		Component aviso = Component.translatable("arraial.pedreiro.aviso_fim", pedreiro.nome(), nomeObra);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(pedreiro) < 64 * 64) {
				p.sendSystemMessage(aviso);
			}
		}
		pedreiro.setObra(null);
	}

	private static int retirar(Container bau, Item item, int quantidade) {
		int pegou = 0;
		for (int i = 0; i < bau.getContainerSize() && pegou < quantidade; i++) {
			ItemStack s = bau.getItem(i);
			if (s.is(item)) {
				pegou += bau.removeItem(i, quantidade - pegou).getCount();
			}
		}
		bau.setChanged();
		return pegou;
	}

	private boolean perto(BlockPos pos, double distancia) {
		return pedreiro.position().distanceTo(Vec3.atCenterOf(pos)) <= distancia;
	}

	private void andarAte(BlockPos pos) {
		if (--recalcularCaminho <= 0 || pedreiro.getNavigation().isDone()) {
			recalcularCaminho = 20;
			pedreiro.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 2, VELOCIDADE);
		}
	}

	private @Nullable BlockPos procurarObra(ServerLevel level) {
		// His own arraial first: the most urgent site that can move forward right now
		// (materials at hand); if every site is waiting for materials, the most urgent one.
		if (pedreiro.vila() != null) {
			List<CanteiroBlockEntity> obras = CanteiroBlockEntity.daVila(level, pedreiro.vila());
			// Spread out: first a site with room for one more pair of hands.
			for (CanteiroBlockEntity c : obras) {
				if (podeAvancar(level, c) && ocupacao(level, c) < MAX_POR_OBRA) {
					return c.getBlockPos();
				}
			}
			for (CanteiroBlockEntity c : obras) {
				if (podeAvancar(level, c)) {
					return c.getBlockPos();
				}
			}
			if (!obras.isEmpty()) {
				return obras.get(0).getBlockPos();
			}
		}
		// No village work: help at any site nearby.
		BlockPos melhor = null;
		double melhorDist = (double) RAIO_BUSCA * RAIO_BUSCA;
		for (BlockPos pos : CanteiroBlockEntity.ativos(level)) {
			double d = pedreiro.distanceToSqr(Vec3.atCenterOf(pos));
			if (d < melhorDist) {
				CanteiroBlockEntity c = canteiro(level, pos);
				if (c != null && c.temTrabalho()) {
					melhor = pos;
					melhorDist = d;
				}
			}
		}
		return melhor;
	}

	/** Other pedreiros working on {@code c}. */
	private int ocupacao(ServerLevel level, CanteiroBlockEntity c) {
		BlockPos pos = c.getBlockPos();
		return level.getEntitiesOfClass(MoradorEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(Vilas.RAIO * 2),
				m -> m != pedreiro && pos.equals(m.obra())).size();
	}

	/** True when the site's next step needs nothing, or its material is in the site chest or free in the armazém. */
	private boolean podeAvancar(ServerLevel level, CanteiroBlockEntity c) {
		PlanoDeObra plano = c.plano(level).orElse(null);
		if (plano == null) {
			return false;
		}
		if (c.progresso() >= plano.etapas().size()) {
			return true;
		}
		PlanoDeObra.Custo custo = PlanoDeObra.custo(plano.etapas().get(c.progresso()).state());
		if (custo == null || pedreiro.carregando(custo.item()) >= custo.quantidade()) {
			return true;
		}
		Container bau = c.bau(level);
		if (bau != null && CanteiroBlock.contar(bau, custo.item()) > 0) {
			return true;
		}
		return livreNoArmazem(level, c, custo.item()) > 0;
	}

	/** Armazém stock of {@code item} not claimed by more urgent sites. */
	private int livreNoArmazem(ServerLevel level, CanteiroBlockEntity c, Item item) {
		BlockPos vila = vilaDa(level, c);
		if (vila == null) {
			return 0;
		}
		return Vilas.contar(Vilas.armazens(level, vila), item) - Demanda.reservadoAntes(level, vila, c, item);
	}

	private @Nullable BlockPos vilaDa(ServerLevel level, CanteiroBlockEntity c) {
		BlockPos vila = Vilas.maisProxima(level, c.getBlockPos());
		return vila != null ? vila : pedreiro.vila();
	}

	private static @Nullable CanteiroBlockEntity canteiro(ServerLevel level, @Nullable BlockPos pos) {
		if (pos == null || !level.isLoaded(pos)) {
			return null;
		}
		return level.getBlockEntity(pos) instanceof CanteiroBlockEntity c ? c : null;
	}
}
