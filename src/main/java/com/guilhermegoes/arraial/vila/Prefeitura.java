package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import com.guilhermegoes.arraial.obra.Catalogo;
import com.guilhermegoes.arraial.obra.Muralha;
import com.guilhermegoes.arraial.obra.PlanoDeObra;
import com.guilhermegoes.arraial.obra.Prioridade;
import com.guilhermegoes.arraial.obra.Terreno;
import com.guilhermegoes.arraial.registry.ModPois;
import com.guilhermegoes.arraial.trabalho.Demanda;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * Self-government. Once the arraial has {@link #MIN_ELEITORES} moradores it elects its oldest
 * resident as prefeito. Every morning the prefeito looks at what the village lacks (houses,
 * a trade, food, defence, water), checks that the arraial can actually get the materials,
 * finds flat ground and puts down a Marco da Obra, hiring a pedreiro if there is none.
 * When something blocks the plan (a missing material nobody can make), the Cruzeiro says so,
 * and the player can step in.
 */
public final class Prefeitura {
	public static final int MIN_ELEITORES = 4;
	/** At most this many buildings going up at once; the second one only when it is urgent. */
	private static final int MAX_OBRAS = 2;

	private static final Map<Profissao, String> OFICINA = Map.ofEntries(
			Map.entry(Profissao.LENHADOR, "serraria"),
			Map.entry(Profissao.ROCEIRO, "roca"),
			Map.entry(Profissao.OLEIRO, "olaria"),
			Map.entry(Profissao.PASTOR, "curral"),
			Map.entry(Profissao.BARREIRO, "barreira"),
			Map.entry(Profissao.CAVOUQUEIRO, "pedreira"),
			Map.entry(Profissao.SOLDADO, "guarita"),
			Map.entry(Profissao.FARINHEIRO, "casa_de_farinha"),
			Map.entry(Profissao.FEIRANTE, "feira"),
			Map.entry(Profissao.TROPEIRO, "rancho_tropeiro"),
			Map.entry(Profissao.BOTICARIO, "botica"),
			Map.entry(Profissao.PESCADOR, "pesqueiro"),
			Map.entry(Profissao.CACADOR, "rancho_cacador"),
			Map.entry(Profissao.ENGENHEIRO, "oficina_engenheiro"),
			Map.entry(Profissao.RELACOES, "cartorio"),
			Map.entry(Profissao.MESTRE_ENGENHO, "engenho"),
			Map.entry(Profissao.CAFEICULTOR, "cafezal"),
			Map.entry(Profissao.ENTALHADOR, "oficina_entalhe"),
			Map.entry(Profissao.BARQUEIRO, "trapiche"),
			Map.entry(Profissao.ESTIVADOR, "porto"));

	/** Called every few seconds by the Cruzeiro. */
	static void cuidar(ServerLevel level, ArraialBlockEntity arraial, List<MoradorEntity> moradores) {
		MoradorEntity prefeito = eleger(level, arraial, moradores);
		if (prefeito == null) {
			return;
		}
		gabinete(level, arraial, prefeito);
		long hoje = Vilas.dia(level);
		if (hoje < arraial.ultimoDespacho) {
			arraial.ultimoDespacho = hoje - 1; // clock set back
		}
		if (hoje <= arraial.ultimoDespacho || !level.isBrightOutside()) {
			return;
		}
		arraial.ultimoDespacho = hoje;
		arraial.setChanged();
		ajustarPedreiros(level, arraial, prefeito, moradores);
		despachar(level, arraial, prefeito, moradores);
	}

	/** With a finished Prefeitura, the prefeito leaves his trade and keeps office at its lectern. */
	private static void gabinete(ServerLevel level, ArraialBlockEntity arraial, MoradorEntity prefeito) {
		BlockPos mesa = null;
		for (BlockPos p : CanteiroBlockEntity.ativos(level)) {
			if (mesa == null && p.closerThan(arraial.getBlockPos(), Vilas.RAIO) && level.getBlockEntity(p) instanceof CanteiroBlockEntity c
					&& c.concluida() && c.projeto().map(pr -> pr.id().equals("prefeitura")).orElse(false)) {
				mesa = c.plano(level).flatMap(pl -> pl.etapas().stream()
						.filter(e -> e.state().is(net.minecraft.world.level.block.Blocks.LECTERN) && level.getBlockState(e.pos()).is(net.minecraft.world.level.block.Blocks.LECTERN))
						.map(PlanoDeObra.Etapa::pos).findFirst()).orElse(null);
			}
		}
		if (mesa != null && prefeito.gabinete() == null && prefeito.profissao() != Profissao.NENHUMA) {
			Component antigo = prefeito.titulo();
			prefeito.largarOficio();
			Cronica.anotar(level, arraial.getBlockPos(), Component.translatable("arraial.cronica.gabinete", prefeito.nome(), cargo(prefeito), antigo));
		}
		prefeito.setGabinete(mesa);
	}

	// ------------------------------------------------------------ elections

	private static @Nullable MoradorEntity eleger(ServerLevel level, ArraialBlockEntity arraial, List<MoradorEntity> moradores) {
		MoradorEntity atual = null;
		for (MoradorEntity m : moradores) {
			if (arraial.prefeito != null && arraial.prefeito.equals(m.getUUID())) {
				atual = m;
			} else if (m.prefeito()) {
				m.setPrefeito(false);
			}
		}
		if (atual != null) {
			atual.setPrefeito(true);
			return atual;
		}
		if (arraial.prefeito != null && level.getEntity(arraial.prefeito) != null) {
			return null; // alive, just outside the village for now
		}
		if (moradores.size() < MIN_ELEITORES) {
			arraial.prefeito = null;
			return null;
		}
		// Elected among the councillors, or the oldest under a coronel (see Politica).
		MoradorEntity eleito = Politica.escolher(level, arraial, moradores);
		if (eleito == null) {
			return null;
		}
		arraial.prefeito = eleito.getUUID();
		arraial.ultimoChefe = eleito.getUUID();
		arraial.setChanged();
		eleito.setPrefeito(true);
		Component cargo = cargo(eleito);
		Cronica.anotar(level, arraial.getBlockPos(), Component.translatable("arraial.cronica.eleicao", eleito.nome(), cargo));
		// The swearing-in: fireworks over the Cruzeiro.
		Festa.soltarFogos(level, arraial.getBlockPos(), 3);
		avisar(level, arraial, Component.translatable("arraial.vila.eleicao", eleito.nome(), cargo, arraial.nomeCompleto()).withStyle(ChatFormatting.GOLD));
		return eleito;
	}

	/** The arraial lost faith in its prefeito: he steps down and someone else is elected. */
	static void depor(ServerLevel level, ArraialBlockEntity a) {
		if (a.prefeito == null) {
			return;
		}
		a.deposto = a.prefeito;
		if (level.getEntity(a.prefeito) instanceof MoradorEntity m) {
			m.setPrefeito(false);
			Cronica.anotar(level, a.getBlockPos(), Component.translatable("arraial.cronica.deposto", m.nome()));
		}
		a.prefeito = null;
		a.setChanged();
	}

	public static Component cargo(MoradorEntity m) {
		ArraialBlockEntity a = m.arraial();
		if (a != null && a.politica.regime() == Politica.Regime.HEREDITARIO) {
			return Component.translatable(m.feminino() ? "arraial.coronel.f" : "arraial.coronel.m");
		}
		return Component.translatable(m.feminino() ? "arraial.prefeito.f" : "arraial.prefeito.m");
	}

	// ------------------------------------------------------------ the morning dispatch

	private static void despachar(ServerLevel level, ArraialBlockEntity arraial, MoradorEntity prefeito, List<MoradorEntity> moradores) {
		BlockPos vila = arraial.getBlockPos();
		List<CanteiroBlockEntity> ativas = CanteiroBlockEntity.daVila(level, vila);
		// A site already going up that needs something nobody can get (the caravan's mud bricks ran
		// out, no barreiro to dig earth for more) would wait forever: improvise the missing trade.
		Set<Profissao> presentes = Demanda.oficios(level, vila);
		for (CanteiroBlockEntity c : ativas) {
			if (c.concluida()) {
				continue;
			}
			Optional<net.minecraft.world.item.Item> sem = Demanda.inviavel(level, vila, Demanda.restante(level, c), presentes);
			if (sem.isEmpty()) {
				continue;
			}
			Profissao quem = Demanda.faltante(level, vila, sem.get(), 1, presentes);
			if (quem != null && IMPROVISAVEIS.contains(quem) && postosDe(level, vila, quem) == 0) {
				improvisarPosto(level, arraial, prefeito, quem, moradores);
				presentes.add(quem);
			}
		}
		if (ativas.size() >= MAX_OBRAS) {
			contratarPedreiro(level, arraial, prefeito, moradores);
			arraial.pedido = Component.empty();
			return;
		}
		Set<String> emObra = new HashSet<>();
		ativas.forEach(c -> c.projeto().ifPresent(p -> emObra.add(p.id())));
		if (Vilas.armazens(level, vila).isEmpty()) {
			arraial.pedido = Component.translatable("arraial.vila.pedido_armazem");
			return;
		}

		int n = moradores.size();
		long semCasa = moradores.stream().filter(m -> m.casa() == null).count();
		long camasLivres = arraial.camas(level) - n;
		int comida = Vilas.contar(Vilas.armazens(level, vila), s -> s.has(DataComponents.FOOD));

		List<String> desejos = new ArrayList<>();
		if (semCasa > 0 || camasLivres <= 0) {
			desejos.add("casa");
		}
		if (postos(level, vila, ModPois.CEPO) == 0) {
			desejos.add("serraria");
		}
		// Food for less than four days: another roça (one per four people) before hunger comes.
		if (comida < n * 4 && postos(level, vila, ModPois.BALAIO) < 1 + n / 4) {
			desejos.add("roca");
		}
		if (n >= 5 && postos(level, vila, ModPois.POSTO_GUARDA) == 0) {
			desejos.add("guarita");
		}
		if (n >= 6 && postos(level, vila, ModPois.COCHO) == 0) {
			desejos.add("curral");
		}
		if (n >= 6 && postos(level, vila, ModPois.FORNO_OLARIA) == 0) {
			desejos.add("olaria");
		}
		if (n >= 3 && obras(level, vila, "poco") == 0) {
			desejos.add("poco");
		}
		if (n >= 5 && postos(level, vila, ModPois.FORNO_FARINHA) == 0) {
			desejos.add("casa_de_farinha");
		}
		// What each stage unlocks (see Estagio).
		Estagio estagio = arraial.estagio;
		if (estagio.pelo(Estagio.ARRAIAL) && !arraial.temFeira(level)) {
			desejos.add("feira");
		}
		boolean epidemia = arraial.crise.tipo() == Crise.Tipo.EPIDEMIA;
		if ((estagio.pelo(Estagio.VILA) || epidemia) && postos(level, vila, ModPois.BALCAO_BOTICA) == 0) {
			desejos.add(epidemia ? 0 : desejos.size(), "botica");
		}
		if (n >= 6 && postos(level, vila, ModPois.COVO) == 0 && temAgua(level, vila)) {
			desejos.add("pesqueiro");
		}
		// The river: a trapiche (and a barqueiro) once the arraial is established by the water.
		if (n >= 7 && estagio.pelo(Estagio.ARRAIAL) && postos(level, vila, ModPois.AMARRA) == 0 && Vilas.temAgua(level, vila)) {
			desejos.add("trapiche");
		}
		// The sea: a Vila on the coast (deep water nearby) raises a porto, and the ships start to call.
		if (n >= 9 && estagio.pelo(Estagio.VILA) && obras(level, vila, "porto") == 0 && Vilas.litoral(level, vila)) {
			desejos.add("porto");
		}
		if (n >= 7 && postos(level, vila, ModPois.VARAL_CACA) == 0) {
			desejos.add("rancho_cacador");
		}
		if (n >= 8 && estagio.pelo(Estagio.ARRAIAL) && postos(level, vila, ModPois.PRANCHETA) == 0) {
			desejos.add("oficina_engenheiro");
		}
		if (n >= 8 && (estagio.pelo(Estagio.VILA) || Registro.todas().size() > 1) && postos(level, vila, ModPois.ESCRIVANINHA) == 0) {
			desejos.add("cartorio");
		}
		if (estagio.pelo(Estagio.VILA) && postos(level, vila, ModPois.CANGALHA) == 0) {
			desejos.add("rancho_tropeiro");
		}
		if (n >= 8 && obras(level, vila, "prefeitura") == 0) {
			desejos.add("prefeitura");
		}
		// Safety: once there are soldados, a paliçada around the arraial keeps the night out.
		if (n >= 6 && estagio.pelo(Estagio.ARRAIAL) && postos(level, vila, ModPois.POSTO_GUARDA) > 0 && obras(level, vila, Muralha.ID) == 0) {
			desejos.add(Muralha.ID);
		}
		// The fazendas: an engenho by the water once the arraial is established; a cafezal in the Imperial era.
		if (n >= 7 && estagio.pelo(Estagio.ARRAIAL) && postos(level, vila, ModPois.MOENDA) == 0 && temAgua(level, vila)) {
			desejos.add("engenho");
		}
		if (n >= 8 && arraial.era.ordinal() >= Era.IMPERIAL.ordinal() && postos(level, vila, ModPois.TERREIRO) == 0) {
			desejos.add("cafezal");
		}
		// The church: a Spanish mission builds its own from the start; a Portuguese arraial its Capela
		// (Imperial) and later the Baroque Matriz, with a carver for the gilded talha.
		if (arraial.hispano()) {
			if (n >= 6 && semCasa == 0 && obras(level, vila, "missao") == 0) {
				desejos.add("missao");
			}
		} else {
			if (arraial.era.ordinal() >= Era.IMPERIAL.ordinal() && obras(level, vila, "capela") == 0) {
				desejos.add("capela");
			}
			boolean mineira = Era.estilo(level, arraial).mineiro();
			boolean barroco = arraial.era.ordinal() >= Era.IMPERIAL.ordinal() && estagio.pelo(mineira ? Estagio.VILA : Estagio.VILA_PROSPERA);
			if (barroco && obras(level, vila, "capela") > 0 && postos(level, vila, ModPois.BANCADA_ENTALHE) == 0) {
				desejos.add("oficina_entalhe");
			}
			if (barroco && postos(level, vila, ModPois.BANCADA_ENTALHE) > 0 && obras(level, vila, "matriz") == 0) {
				desejos.add("matriz");
			}
		}
		if (arraial.era == Era.REPUBLICANA && obras(level, vila, "chafariz") == 0) {
			desejos.add("chafariz");
		}
		if (desejos.isEmpty() && camasLivres <= 1) {
			desejos.add("casa");
		}

		// Urgent: people without a bed, or less than a day of food. Urgent sites go first and may
		// start while another building is still going up; everything else waits its turn.
		Set<String> urgentes = new HashSet<>();
		if (semCasa > 0) {
			urgentes.add("casa");
		}
		if (comida < n * 2) {
			urgentes.add("roca");
		}
		// Crises: a poço against the drought, a guarita against bandits, a curral (chickens) against the plague.
		Crise.Tipo crise = arraial.crise.tipo();
		if (crise == Crise.Tipo.SECA && obras(level, vila, "poco") == 0) {
			desejos.remove("poco");
			desejos.add(0, "poco");
			urgentes.add("poco");
		}
		if (crise == Crise.Tipo.EPIDEMIA) {
			urgentes.add("botica");
		}
		if (crise == Crise.Tipo.SALTEADORES && postos(level, vila, ModPois.POSTO_GUARDA) == 0) {
			desejos.remove("guarita");
			desejos.add(0, "guarita");
			urgentes.add("guarita");
		}
		if (crise == Crise.Tipo.PRAGA && postos(level, vila, ModPois.COCHO) == 0 && !desejos.contains("curral")) {
			desejos.add("curral");
		}
		// Voted by the council (or security it demanded): first, and urgent.
		for (String votada : arraial.politica.obrasVotadas(level, arraial)) {
			desejos.remove(votada);
			desejos.add(0, votada);
			urgentes.add(votada);
		}
		desejos.removeIf(d -> emObra.contains(d) || (d.equals("casa") && (emObra.contains("casebre") || emObra.contains("rancho"))));
		if (!ativas.isEmpty()) {
			desejos.removeIf(d -> !urgentes.contains(d));
		}
		if (desejos.isEmpty()) {
			if (!ativas.isEmpty()) {
				contratarPedreiro(level, arraial, prefeito, moradores);
			}
			arraial.pedido = Component.empty();
			return;
		}

		Set<Profissao> oficios = Demanda.oficios(level, vila);
		Set<String> tentados = new HashSet<>();
		Component primeiroPedido = null;
		for (int i = 0; i < desejos.size() && i < 16; i++) {
			String desejo = desejos.get(i);
			if (!tentados.add(desejo)) {
				continue;
			}
			List<String> opcoes = desejo.equals("casa") ? List.of("casebre", "rancho") : List.of(desejo);
			Item falta = null;
			Catalogo.Projeto bloqueado = null;
			for (String id : opcoes) {
				Catalogo.Projeto projeto = Catalogo.porId(id).orElse(null);
				if (projeto == null) {
					continue;
				}
				Map<Item, Integer> materiais = PlanoDeObra.criar(level, projeto, vila, Direction.NORTH)
						.map(p -> p.materiais(0)).orElse(null);
				if (materiais == null) {
					continue;
				}
				Optional<Item> semFonte = Demanda.inviavel(level, vila, materiais, oficios);
				if (semFonte.isPresent()) {
					if (falta == null) {
						falta = semFonte.get();
						bloqueado = projeto;
					}
					continue;
				}
				Optional<Terreno.Lugar> lugar = Terreno.procurar(level, vila, projeto);
				if (lugar.isEmpty()) {
					if (primeiroPedido == null) {
						primeiroPedido = Component.translatable("arraial.vila.pedido_lugar", projeto.nome());
					}
					continue;
				}
				Prioridade prioridade = urgentes.contains(desejo) ? Prioridade.URGENTE
						: desejo.equals(Muralha.ID) && arraial.crise.tipo() != Crise.Tipo.SALTEADORES ? Prioridade.BAIXA : Prioridade.NORMAL;
				if (Terreno.marcar(level, lugar.get(), projeto, prioridade)) {
					arraial.politica.obraIniciada(desejo);
					Cronica.anotar(level, vila, Component.translatable("arraial.cronica.ordem", prefeito.nome(), cargo(prefeito), projeto.nome()));
					avisar(level, arraial, Component.translatable("arraial.vila.ordem", cargo(prefeito), prefeito.nome(), projeto.nome())
							.withStyle(ChatFormatting.AQUA));
					contratarPedreiro(level, arraial, prefeito, moradores);
					arraial.pedido = Component.empty();
					return;
				}
			}
			if (falta != null && bloqueado != null) {
				if (primeiroPedido == null) {
					primeiroPedido = Component.translatable("arraial.vila.pedido", bloqueado.nome(), falta.getDefaultInstance().getHoverName());
				}
				// Nobody can make it: first build the workshop of the trade that would.
				Profissao quem = Demanda.faltante(level, vila, falta, 1, oficios);
				if (quem == null) {
					quem = Demanda.quemFaz(falta);
				}
				String oficina = quem == null ? null : OFICINA.get(quem);
				if (oficina != null && !tentados.contains(oficina) && postosDe(level, vila, quem) == 0) {
					desejos.add(i + 1, oficina);
					if (urgentes.contains(desejo)) {
						urgentes.add(oficina);
					}
				}
				// And if not even the workshop can be built (it needs the same thing), improvise the
				// workstation: a stump for a cepo, a tub for the clay... so the village never stays stuck.
				if (quem != null && IMPROVISAVEIS.contains(quem) && postosDe(level, vila, quem) == 0) {
					improvisarPosto(level, arraial, prefeito, quem, moradores);
				}
			}
		}
		arraial.pedido = primeiroPedido == null ? Component.empty() : primeiroPedido;
	}

	/** Trades whose workstation the moradores can improvise when the village is stuck without them. */
	private static final java.util.Set<Profissao> IMPROVISAVEIS = java.util.EnumSet.of(Profissao.LENHADOR, Profissao.ROCEIRO,
			Profissao.OLEIRO, Profissao.BARREIRO, Profissao.CAVOUQUEIRO, Profissao.PASTOR);

	/** Puts down the trade's workstation, free, a little away from the centre and facing outwards. */
	private static void improvisarPosto(ServerLevel level, ArraialBlockEntity arraial, MoradorEntity prefeito, Profissao quem,
			List<MoradorEntity> moradores) {
		net.minecraft.world.level.block.Block bloco = quem.posto();
		if (bloco == null) {
			return;
		}
		BlockPos vila = arraial.getBlockPos();
		Optional<BlockPos> lugar = Terreno.lugarParaPosto(level, vila, 12, Vilas.RAIO - 12);
		if (lugar.isEmpty()) {
			return;
		}
		BlockPos p = lugar.get();
		net.minecraft.world.level.block.state.BlockState estado = bloco.defaultBlockState();
		if (estado.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
			net.minecraft.core.Direction fora = net.minecraft.core.Direction.getApproximateNearest(p.getX() - vila.getX(), 0, p.getZ() - vila.getZ());
			estado = estado.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, fora);
		}
		level.setBlock(p, estado, net.minecraft.world.level.block.Block.UPDATE_ALL);
		Component texto = Component.translatable("arraial.cronica.improviso", prefeito.nome(), bloco.getName(), quem.titulo(false));
		Cronica.anotar(level, vila, texto);
		avisar(level, arraial, texto.copy().withStyle(ChatFormatting.AQUA));
		liberarAlguem(level, arraial, prefeito, moradores);
	}

	/**
	 * Nobody without a trade to take a new workstation? Then someone from a trade with two or more
	 * hands (never the last of one) is sent to find work again: the free workstation will be theirs.
	 */
	private static void liberarAlguem(ServerLevel level, ArraialBlockEntity arraial, MoradorEntity prefeito, List<MoradorEntity> moradores) {
		if (moradores.stream().anyMatch(m -> m.profissao() == Profissao.NENHUMA && !m.crianca() && m.gabinete() == null)) {
			return;
		}
		Map<Profissao, Long> conta = new java.util.EnumMap<>(Profissao.class);
		moradores.forEach(m -> conta.merge(m.profissao(), 1L, Long::sum));
		moradores.stream()
				.filter(m -> m != prefeito && m.profissao() != Profissao.NENHUMA && conta.getOrDefault(m.profissao(), 0L) >= 2 && m.obra() == null)
				.max(Comparator.comparingLong(m -> conta.get(m.profissao())))
				.ifPresent(m -> {
					Component antigo = m.titulo();
					m.setProfissao(Profissao.NENHUMA);
					Cronica.anotar(level, arraial.getBlockPos(), Component.translatable("arraial.cronica.remanejou", prefeito.nome(), m.nome(), antigo));
				});
	}

	/** Makes sure some pedreiro will take the job: an unemployed morador is hired if needed. */
	private static void contratarPedreiro(ServerLevel level, ArraialBlockEntity arraial, MoradorEntity prefeito, List<MoradorEntity> moradores) {
		// One pedreiro per building going up (within the limit for the population).
		int obras = limitePedreiros(level, arraial, moradores.size());
		long pedreiros = moradores.stream().filter(m -> m.profissao() == Profissao.PEDREIRO).count();
		if (pedreiros >= obras) {
			return;
		}
		moradores.stream().filter(m -> m.profissao() == Profissao.NENHUMA && m.gabinete() == null).findFirst().ifPresent(m -> {
			m.setProfissao(Profissao.PEDREIRO);
			Cronica.anotar(level, arraial.getBlockPos(),
					Component.translatable("arraial.cronica.contratou", prefeito.nome(), m.nome(), m.titulo()));
		});
	}

	/** How many pedreiros the arraial should have: one per site, at most a quarter of the people. */
	private static int limitePedreiros(ServerLevel level, ArraialBlockEntity arraial, int moradores) {
		int obras = CanteiroBlockEntity.daVila(level, arraial.getBlockPos()).size();
		return Math.max(1, Math.min(obras, Math.max(1, moradores / 4)));
	}

	/**
	 * Too many pedreiros and too few hands elsewhere: while there are free workstations, the
	 * prefeito sends the extra pedreiros (those without a site first) to find another trade.
	 */
	private static void ajustarPedreiros(ServerLevel level, ArraialBlockEntity arraial, MoradorEntity prefeito, List<MoradorEntity> moradores) {
		int limite = limitePedreiros(level, arraial, moradores.size());
		List<MoradorEntity> pedreiros = new ArrayList<>(moradores.stream().filter(m -> m.profissao() == Profissao.PEDREIRO).toList());
		long livres = arraial.postosLivres(level);
		pedreiros.sort(Comparator.comparing((MoradorEntity m) -> m.obra() != null).thenComparing(m -> m == prefeito));
		for (int i = 0; i < pedreiros.size() - limite && livres > 0; i++, livres--) {
			MoradorEntity m = pedreiros.get(i);
			Component antigo = m.titulo();
			m.setObra(null);
			m.setProfissao(Profissao.NENHUMA);
			Cronica.anotar(level, arraial.getBlockPos(), Component.translatable("arraial.cronica.remanejou", prefeito.nome(), m.nome(), antigo));
		}
	}

	/** Open water somewhere in the arraial (a few sampled spots), for a pesqueiro. */
	private static boolean temAgua(ServerLevel level, BlockPos vila) {
		return Vilas.temAgua(level, vila);
	}

	/** Finished (or started) buildings of a given project inside the arraial. */
	static int obras(ServerLevel level, BlockPos vila, String id) {
		int n = 0;
		for (BlockPos p : CanteiroBlockEntity.ativos(level)) {
			if (p.closerThan(vila, Vilas.RAIO) && level.getBlockEntity(p) instanceof CanteiroBlockEntity c
					&& c.projeto().map(pr -> pr.id().equals(id)).orElse(false)) {
				n++;
			}
		}
		return n;
	}

	private static long postos(ServerLevel level, BlockPos vila, PoiType tipo) {
		return level.getPoiManager().getCountInRange(h -> h.value() == tipo, vila, Vilas.RAIO, PoiManager.Occupancy.ANY);
	}

	private static long postosDe(ServerLevel level, BlockPos vila, Profissao p) {
		PoiType tipo = ModPois.de(p);
		return tipo == null ? 0 : postos(level, vila, tipo);
	}

	static void avisar(ServerLevel level, ArraialBlockEntity arraial, Component msg) {
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().closerThan(arraial.getBlockPos(), Vilas.RAIO * 2)) {
				p.sendSystemMessage(msg);
			}
		}
	}

	private Prefeitura() {
	}
}
