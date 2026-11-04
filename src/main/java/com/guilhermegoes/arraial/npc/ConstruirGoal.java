package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.obra.CanteiroBlock;
import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import com.guilhermegoes.arraial.obra.PlanoDeObra;
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
		BlockPos vila = Vilas.maisProxima(level, canteiro.getBlockPos());
		List<ArmazemBlockEntity> armazens = Vilas.armazens(level, vila != null ? vila : pedreiro.vila());
		Container bau = null;
		if (bauDaObra != null && CanteiroBlock.contar(bauDaObra, precisa.item()) > 0) {
			bau = bauDaObra;
		} else {
			for (ArmazemBlockEntity a : armazens) {
				if (CanteiroBlock.contar(a, precisa.item()) > 0) {
					bau = a;
					break;
				}
			}
		}
		if (bau == null) {
			if (bauDaObra == null && armazens.isEmpty()) {
				pedreiro.setStatus(Component.translatable("arraial.pedreiro.sem_bau"));
			} else {
				// There is somewhere to take from, it just doesn't have this yet: wait for the
				// trades (or the player) to bring it, and say exactly what is missing.
				int total = plano.materiais(canteiro.progresso()).getOrDefault(precisa.item(), 0) - pedreiro.carregando(precisa.item());
				pedreiro.setStatus(Component.translatable("arraial.pedreiro.esperando",
						Math.max(total, 1), precisa.item().getDefaultInstance().getHoverName()));
			}
			andarAte(canteiro.getBlockPos());
			espera = 60;
			return;
		}
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
		for (Map.Entry<Item, Integer> e : lista.entrySet()) {
			int falta = e.getValue() - pedreiro.carregando(e.getKey());
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
			level.playSound(null, pos, etapa.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
		}
		pedreiro.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
	}

	private void concluir(ServerLevel level, CanteiroBlockEntity canteiro) {
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

	private static @Nullable CanteiroBlockEntity canteiro(ServerLevel level, @Nullable BlockPos pos) {
		if (pos == null || !level.isLoaded(pos)) {
			return null;
		}
		return level.getBlockEntity(pos) instanceof CanteiroBlockEntity c ? c : null;
	}
}
