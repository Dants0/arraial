package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import com.guilhermegoes.arraial.obra.Muralha;
import com.guilhermegoes.arraial.registry.ModItems;
import com.guilhermegoes.arraial.registry.ModPois;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The arraial's politics. The moradores are not a crowd that agrees with whoever talks loudest:
 * they belong to interest groups (by trade), each with a representative on the council, and vote
 * on public proposals by what is good for them.
 * <ul>
 * <li><b>Regime</b>: elected (the prefeito is voted among the councillors) or hereditary (a
 * coronel; the oldest takes over). A successful vote for a new election ends hereditary rule.</li>
 * <li><b>Proposals</b>: taxes up or down, security, an extra festa, a building, honorary
 * citizenship for a player, investigating the prefeito, a new election. The council proposes by
 * itself as needs arise; the player proposes at the Urna (paper in hand) and may be turned down.
 * Votes happen the next morning.</li>
 * <li><b>Vote</b>: each morador weighs their group's interest, their own mind, who proposed it
 * (the player's reputation, honorary citizenship) and the campaign. Buying support with réis
 * works, but can blow up in a scandal.</li>
 * <li><b>Taxes</b> fill the coffers and sour the mood. <b>Corruption</b>: some prefeitos skim the
 * treasury; rumours grow and an investigation can depose them.</li>
 * <li><b>Protests</b>: a group unhappy two days in a row stops work and gathers at the Cruzeiro,
 * and its demand becomes a proposal.</li>
 * </ul>
 */
public final class Politica {
	public enum Regime {
		ELEITO, HEREDITARIO
	}

	public enum Grupo {
		LAVOURA, OFICIOS, ORDEM, COMERCIO, POVO;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		public Component nome() {
			return Component.translatable("arraial.grupo." + id());
		}

		public static Grupo de(Profissao p) {
			return switch (p) {
				case ROCEIRO, FARINHEIRO, PASTOR, PESCADOR, CACADOR, MESTRE_ENGENHO, CAFEICULTOR, MOLEIRO -> LAVOURA;
				case PEDREIRO, LENHADOR, OLEIRO, BARREIRO, CAVOUQUEIRO, ENGENHEIRO, BOTICARIO, ENTALHADOR -> OFICIOS;
				case SOLDADO -> ORDEM;
				case FEIRANTE, TROPEIRO, RELACOES, MASCATE, BARQUEIRO, ESTIVADOR, FERROVIARIO -> COMERCIO;
				default -> POVO;
			};
		}
	}

	public enum Tipo {
		IMPOSTO_MAIS, IMPOSTO_MENOS, SEGURANCA, FESTA, CIDADANIA, INVESTIGAR, NOVA_ELEICAO, OBRA;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		static @Nullable Tipo de(String s) {
			for (Tipo t : values()) {
				if (t.id().equals(s)) {
					return t;
				}
			}
			return null;
		}
	}

	/** Buildings a proposal can ask for. */
	private static final List<String> OBRAS = List.of("casa", "roca", "poco", "guarita", Muralha.ID, "feira", "botica", "prefeitura");
	private static final int CUSTO_FESTA = 8;

	Regime regime = Regime.ELEITO;
	int imposto = 1;
	@Nullable Tipo proposta;
	String alvo = "";
	@Nullable UUID autorJogador;
	String autorNome = "";
	long propostaDia;
	int apoio;
	boolean escandalo;
	int desviado;
	int suspeita;
	final Set<UUID> cidadaos = new HashSet<>();
	long segurancaAte;
	String obraVotada = "";
	long festaVotada;
	final Map<Grupo, Integer> diasInsatisfeito = new EnumMap<>(Grupo.class);
	@Nullable Grupo protesto;
	long protestoDia;
	Component ultimoResultado = Component.empty();

	public Regime regime() {
		return regime;
	}

	public int imposto() {
		return imposto;
	}

	public boolean cidadao(UUID jogador) {
		return cidadaos.contains(jogador);
	}

	/** The group protesting today, if any (see ProtestarGoal). */
	public @Nullable Grupo protestoHoje(ServerLevel level) {
		return protesto != null && protestoDia == Vilas.dia(level) ? protesto : null;
	}

	// ---------------------------------------------------------------- the council

	/** Each group's representative: its longest-resident member. */
	public static Map<Grupo, MoradorEntity> conselho(List<MoradorEntity> moradores) {
		Map<Grupo, MoradorEntity> c = new EnumMap<>(Grupo.class);
		for (MoradorEntity m : moradores) {
			if (m.prefeito() || m.profissao() == Profissao.MASCATE || m.crianca()) {
				continue;
			}
			Grupo g = Grupo.de(m.profissao());
			MoradorEntity atual = c.get(g);
			if (atual == null || m.diaChegada() < atual.diaChegada()) {
				c.put(g, m);
			}
		}
		return c;
	}

	public static @Nullable Grupo grupoDoConselheiro(MoradorEntity m) {
		ArraialBlockEntity a = m.arraial();
		if (a == null || !(m.level() instanceof ServerLevel level)) {
			return null;
		}
		Grupo g = Grupo.de(m.profissao());
		return conselho(a.moradores(level)).get(g) == m ? g : null;
	}

	/** What the village looks like to its voters today. */
	private record Estado(int n, double comidaPorCabeca, int tesouro, int legitimidade, int satisfacao, long semCasa,
			long doentes, boolean salteadores, int rotasFechadas, int imposto, int suspeita, boolean muralha, boolean feira,
			boolean obras, long diasSemFesta) {
	}

	private static Estado estado(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores) {
		int n = Math.max(1, moradores.size());
		BlockPos vila = a.getBlockPos();
		int comida = Vilas.contar(Vilas.armazens(level, vila), s -> s.has(DataComponents.FOOD));
		String meu = Registro.id(level.dimension().identifier().toString(), vila);
		long hoje = Vilas.dia(level);
		int fechadas = (int) Registro.todas().keySet().stream().filter(k -> !k.equals(meu) && Economia.bloqueada(meu, k, hoje)).count();
		return new Estado(n, comida / (double) n, a.tesouro, a.legitimidade, a.satisfacao,
				moradores.stream().filter(m -> m.casa() == null).count(), moradores.stream().filter(MoradorEntity::doente).count(),
				a.crise.tipo() == Crise.Tipo.SALTEADORES, fechadas, a.politica.imposto, a.politica.suspeita,
				Prefeitura.obras(level, vila, Muralha.ID) > 0, a.temFeira(level), !CanteiroBlockEntity.daVila(level, vila).isEmpty(),
				hoje - a.festa.ultima);
	}

	/** How much a group stands to gain (+) or lose (-) from a proposal. */
	private static double interesse(Grupo g, Tipo t, String alvo, Estado e) {
		return switch (t) {
			case IMPOSTO_MAIS -> -1.2 + (e.tesouro() < 8 ? 1.0 : 0) + (g == Grupo.ORDEM ? 1.0 : 0) - (g == Grupo.LAVOURA || g == Grupo.COMERCIO ? 0.5 : 0);
			case IMPOSTO_MENOS -> 1.0 - (e.tesouro() < 8 ? 1.2 : 0) - (g == Grupo.ORDEM ? 0.8 : 0) + (e.satisfacao() < 40 ? 0.5 : 0);
			case SEGURANCA -> (g == Grupo.ORDEM ? 2.0 : 0.2) + (e.salteadores() ? 1.5 : 0) + (g == Grupo.COMERCIO && e.rotasFechadas() > 0 ? 1.0 : 0)
					+ (e.muralha() ? -0.6 : 0.3) - (e.tesouro() < 5 ? 0.6 : 0);
			case FESTA -> (g == Grupo.POVO ? 1.6 : 0.8) + (e.diasSemFesta() > 6 ? 0.6 : -0.4) - (e.comidaPorCabeca() < 2 ? 1.5 : 0)
					- (e.tesouro() < CUSTO_FESTA + 4 ? 1.0 : 0) - (g == Grupo.ORDEM ? 0.4 : 0);
			case CIDADANIA -> g == Grupo.COMERCIO ? 0.4 : 0.0; // the rest is the player's reputation
			case INVESTIGAR -> (50 - e.legitimidade()) / 25.0 + Math.min(2.0, e.suspeita() / 8.0) - 0.3;
			case NOVA_ELEICAO -> (40 - e.legitimidade()) / 15.0 - 0.2;
			case OBRA -> obra(g, alvo, e);
		};
	}

	private static double obra(Grupo g, String alvo, Estado e) {
		double v = g == Grupo.OFICIOS ? 1.2 : 0.2; // work for the trades
		switch (alvo) {
			case "casa" -> v += e.semCasa() > 0 ? (g == Grupo.POVO ? 2.0 : 1.0) : -0.5;
			case "roca" -> v += (g == Grupo.LAVOURA ? 1.5 : 0.3) + (e.comidaPorCabeca() < 2 ? 1.0 : -0.2);
			case "poco" -> v += g == Grupo.LAVOURA ? 1.0 : 0.2;
			case "guarita", Muralha.ID -> v += (g == Grupo.ORDEM ? 1.8 : 0.2) + (e.salteadores() ? 1.0 : 0);
			case "feira" -> v += (g == Grupo.COMERCIO ? 2.0 : 0.3) + (e.feira() ? -1.5 : 0);
			case "botica" -> v += e.doentes() > 0 ? 1.5 : 0.1;
			case "prefeitura" -> v += g == Grupo.ORDEM ? 0.8 : -0.1;
			default -> {
			}
		}
		return v - (e.obras() ? 0.3 : 0);
	}

	/** A morador's own mind on this proposal: steady for the same proposal, different between people. */
	private double ruido(MoradorEntity m) {
		long semente = m.getUUID().getLeastSignificantBits() ^ (propostaDia * 31 + (proposta == null ? 0 : proposta.ordinal()) * 977L + alvo.hashCode());
		return new Random(semente).nextGaussian() * 0.7;
	}

	private double opiniao(ServerLevel level, ArraialBlockEntity a, MoradorEntity m, Estado e) {
		double o = interesse(Grupo.de(m.profissao()), proposta, alvo, e) + ruido(m);
		if (autorJogador != null) {
			o += a.reputacao(autorJogador) / 40.0 + (cidadaos.contains(autorJogador) ? 0.5 : 0);
			if (Cultura.costume(level, a, "desconfiados")) {
				o -= 0.5; // wary of strangers' ideas
			}
			if (proposta == Tipo.CIDADANIA) {
				o += a.reputacao(autorJogador) / 25.0 - 1.0;
			}
		} else {
			o += 0.2; // the council's own proposal
		}
		o += apoio / (2.0 * e.n());
		if (m.prefeito() && (proposta == Tipo.INVESTIGAR || proposta == Tipo.NOVA_ELEICAO)) {
			o -= 5; // nobody votes against themselves
		}
		return o;
	}

	// ---------------------------------------------------------------- the day

	/** Morning: taxes, corruption, the mood of each group, the vote and new proposals. */
	static void diario(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores) {
		Politica p = a.politica;
		long hoje = Vilas.dia(level);
		if (moradores.isEmpty()) {
			return;
		}
		// Taxes.
		if (p.imposto > 0) {
			a.mudarTesouro(p.imposto * Math.max(1, moradores.size() / 2));
		}
		corrupcao(level, a, moradores);
		Estado e = estado(level, a, moradores);
		humores(level, a, moradores, e, hoje);
		if (p.proposta != null && p.propostaDia < hoje) {
			votar(level, a, moradores, e);
		}
		if (p.proposta == null && moradores.size() >= Prefeitura.MIN_ELEITORES && level.getRandom().nextFloat() < 0.35F) {
			propor(level, a, moradores, e);
		}
		a.setChanged();
	}

	private static void corrupcao(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores) {
		Politica p = a.politica;
		p.suspeita = Math.max(0, p.suspeita - 1);
		if (a.prefeito == null) {
			return;
		}
		int honestidade = Math.floorMod(a.prefeito.getLeastSignificantBits(), 100);
		if (honestidade < 30 && a.tesouro > 25 && level.getRandom().nextFloat() < 0.3F) {
			int x = Math.max(2, a.tesouro / 8);
			a.tesouro -= x;
			p.desviado += x;
			p.suspeita += x;
		}
	}

	/** Each group's mood today; two bad days in a row and they take to the street. */
	public static int humor(Grupo g, ArraialBlockEntity a, ServerLevel level, List<MoradorEntity> moradores) {
		return humor(g, estado(level, a, moradores));
	}

	private static int humor(Grupo g, Estado e) {
		double h = e.satisfacao();
		switch (g) {
			case LAVOURA -> h += -e.imposto() * 8 - (e.comidaPorCabeca() < 1 ? 20 : 0);
			case OFICIOS -> h += (e.obras() ? 5 : -8) - e.imposto() * 4;
			case ORDEM -> h += (e.salteadores() ? -20 : 0) + (e.muralha() ? 8 : 0) - (e.tesouro() < 5 ? 10 : 0);
			case COMERCIO -> h += -e.imposto() * 6 - 10 * e.rotasFechadas() + (e.feira() ? 5 : 0);
			case POVO -> h += -30.0 * e.semCasa() / e.n() - e.imposto() * 5 + (e.diasSemFesta() <= 6 ? 8 : 0);
		}
		return (int) Math.max(0, Math.min(100, Math.round(h)));
	}

	private static void humores(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores, Estado e, long hoje) {
		Politica p = a.politica;
		Set<Grupo> presentes = new HashSet<>();
		moradores.forEach(m -> presentes.add(Grupo.de(m.profissao())));
		for (Grupo g : Grupo.values()) {
			int dias = presentes.contains(g) && humor(g, e) < 25 ? p.diasInsatisfeito.getOrDefault(g, 0) + 1 : 0;
			p.diasInsatisfeito.put(g, dias);
			if (dias >= 2 && p.protestoHoje(level) == null) {
				p.protesto = g;
				p.protestoDia = hoje;
				p.diasInsatisfeito.put(g, 0);
				a.legitimidade = Math.max(0, a.legitimidade - 6);
				Component texto = Component.translatable("arraial.cronica.protesto", g.nome(), a.nomeCompleto());
				Cronica.anotar(level, a.getBlockPos(), texto);
				Prefeitura.avisar(level, a, Component.literal("📢 ").append(texto).withStyle(ChatFormatting.RED));
				// The demand goes to a vote.
				if (p.proposta == null) {
					Tipo exige = switch (g) {
						case ORDEM -> Tipo.SEGURANCA;
						case OFICIOS -> Tipo.OBRA;
						default -> e.imposto() > 0 ? Tipo.IMPOSTO_MENOS : Tipo.NOVA_ELEICAO;
					};
					MoradorEntity rep = conselho(moradores).get(g);
					p.abrir(exige, exige == Tipo.OBRA ? obraDesejada(e) : "", null, rep == null ? "" : rep.nome(), hoje);
				}
			}
		}
	}

	private static String obraDesejada(Estado e) {
		return e.semCasa() > 0 ? "casa" : e.comidaPorCabeca() < 2 ? "roca" : !e.muralha() ? "guarita" : "poco";
	}

	/** The council puts forward what the arraial needs most. */
	private static void propor(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores, Estado e) {
		List<Tipo> opcoes = new ArrayList<>();
		if (e.tesouro() < 8 && a.politica.imposto < 3) {
			opcoes.add(Tipo.IMPOSTO_MAIS);
		}
		if (e.satisfacao() < 45 && a.politica.imposto > 0) {
			opcoes.add(Tipo.IMPOSTO_MENOS);
		}
		if (e.salteadores() || e.rotasFechadas() > 0) {
			opcoes.add(Tipo.SEGURANCA);
		}
		if (e.suspeita() >= 8) {
			opcoes.add(Tipo.INVESTIGAR);
		}
		if (e.legitimidade() < 35) {
			opcoes.add(Tipo.NOVA_ELEICAO);
		}
		if (e.comidaPorCabeca() >= 4 && e.tesouro() >= CUSTO_FESTA + 6 && e.satisfacao() >= 50 && e.diasSemFesta() > 5) {
			opcoes.add(Tipo.FESTA);
		}
		if (e.semCasa() > 0 || e.comidaPorCabeca() < 1.5) {
			opcoes.add(Tipo.OBRA);
		}
		if (opcoes.isEmpty()) {
			return;
		}
		Tipo t = opcoes.get(level.getRandom().nextInt(opcoes.size()));
		String alvo = t == Tipo.OBRA ? obraDesejada(e) : "";
		// Proposed by the councillor whose group gains most.
		Map<Grupo, MoradorEntity> conselho = conselho(moradores);
		MoradorEntity autor = conselho.entrySet().stream().max(Comparator.comparingDouble(en -> interesse(en.getKey(), t, alvo, e)))
				.map(Map.Entry::getValue).orElse(null);
		a.politica.abrir(t, alvo, null, autor == null ? "" : autor.nome(), Vilas.dia(level));
		Prefeitura.avisar(level, a, Component.literal("📜 ").append(Component.translatable("arraial.politica.nova", autor == null ? "?" : autor.nome(),
				a.politica.descricao())).withStyle(ChatFormatting.YELLOW));
	}

	private void abrir(Tipo t, String alvo, @Nullable UUID jogador, String autor, long dia) {
		proposta = t;
		this.alvo = alvo;
		autorJogador = jogador;
		autorNome = autor;
		propostaDia = dia;
		apoio = 0;
		escandalo = false;
	}

	public Component descricao() {
		if (proposta == null) {
			return Component.translatable("arraial.politica.nenhuma");
		}
		return descricao(proposta, alvo);
	}

	private static Component descricao(Tipo t, String alvo) {
		if (t == Tipo.OBRA) {
			Component obra = alvo.equals("casa") ? Component.translatable("arraial.politica.obra_casa")
					: com.guilhermegoes.arraial.obra.Catalogo.porId(alvo).map(pr -> (Component) pr.nome()).orElse(Component.literal(alvo));
			return Component.translatable("arraial.politica.tipo.obra", obra);
		}
		return Component.translatable("arraial.politica.tipo." + t.id());
	}

	private static void votar(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores, Estado e) {
		Politica p = a.politica;
		int sim = 0, nao = 0;
		Map<Grupo, double[]> porGrupo = new EnumMap<>(Grupo.class);
		for (MoradorEntity m : moradores) {
			if (m.profissao() == Profissao.MASCATE || m.crianca()) {
				continue;
			}
			double o = p.opiniao(level, a, m, e);
			if (o > 0) {
				sim++;
			} else {
				nao++;
			}
			double[] s = porGrupo.computeIfAbsent(Grupo.de(m.profissao()), k -> new double[2]);
			s[0] += o;
			s[1]++;
		}
		boolean aprovada = sim > nao && !p.escandalo;
		String regimeNota = "";
		if (p.regime == Regime.HEREDITARIO && a.prefeito != null && level.getEntity(a.prefeito) instanceof MoradorEntity coronel
				&& p.proposta != Tipo.NOVA_ELEICAO && p.proposta != Tipo.INVESTIGAR) {
			// The coronel decides; the vote is only advice. Overruling a clear majority costs him.
			boolean quer = p.opiniao(level, a, coronel, e) > 0;
			if (quer != aprovada && Math.max(sim, nao) >= 0.6 * (sim + nao)) {
				a.legitimidade = Math.max(0, a.legitimidade - 10);
				regimeNota = "arraial.politica.coronel_contrariou";
			}
			aprovada = quer && !p.escandalo;
		}
		Grupo contra = porGrupo.entrySet().stream().min(Comparator.comparingDouble(en -> en.getValue()[0] / en.getValue()[1]))
				.map(Map.Entry::getKey).orElse(Grupo.POVO);
		Component desc = p.descricao();
		MutableComponent texto = Component.translatable(aprovada ? "arraial.cronica.aprovada" : "arraial.cronica.rejeitada", desc, sim, nao);
		if (!aprovada) {
			texto.append(" ").append(Component.translatable(p.escandalo ? "arraial.politica.por_escandalo" : "arraial.politica.contra", contra.nome()));
		}
		if (!regimeNota.isEmpty()) {
			texto.append(" ").append(Component.translatable(regimeNota));
		}
		Cronica.anotar(level, a.getBlockPos(), texto);
		Prefeitura.avisar(level, a, Component.literal("🗳 ").append(texto).withStyle(aprovada ? ChatFormatting.GREEN : ChatFormatting.GOLD));
		p.ultimoResultado = texto;
		if (p.autorJogador != null) {
			a.mudarReputacao(p.autorJogador, aprovada ? 3 : -2);
		}
		Tipo t = p.proposta;
		UUID jogador = p.autorJogador;
		String alvo = p.alvo;
		p.proposta = null;
		p.autorJogador = null;
		p.apoio = 0;
		if (aprovada) {
			aplicar(level, a, t, alvo, jogador, e);
		}
	}

	private static void aplicar(ServerLevel level, ArraialBlockEntity a, Tipo t, String alvo, @Nullable UUID jogador, Estado e) {
		Politica p = a.politica;
		long hoje = Vilas.dia(level);
		switch (t) {
			case IMPOSTO_MAIS -> p.imposto = Math.min(3, p.imposto + 1);
			case IMPOSTO_MENOS -> p.imposto = Math.max(0, p.imposto - 1);
			case SEGURANCA -> p.segurancaAte = hoje + 8;
			case FESTA -> {
				p.festaVotada = hoje;
				a.mudarTesouro(-CUSTO_FESTA);
			}
			case CIDADANIA -> {
				if (jogador != null) {
					p.cidadaos.add(jogador);
					ServerPlayer sp = level.getServer().getPlayerList().getPlayer(jogador);
					if (sp != null) {
						Conquistas.dar(sp, "cidadao");
						sp.sendSystemMessage(Component.translatable("arraial.politica.cidadao", a.nomeCompleto()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
					}
				}
			}
			case INVESTIGAR -> investigar(level, a, jogador);
			case NOVA_ELEICAO -> {
				if (p.regime == Regime.HEREDITARIO) {
					p.regime = Regime.ELEITO;
					Cronica.anotar(level, a.getBlockPos(), Component.translatable("arraial.cronica.republica", a.nomeCompleto()));
				}
				Prefeitura.depor(level, a);
			}
			case OBRA -> p.obraVotada = alvo;
		}
	}

	private static void investigar(ServerLevel level, ArraialBlockEntity a, @Nullable UUID denunciante) {
		Politica p = a.politica;
		String nome = a.prefeito != null && level.getEntity(a.prefeito) instanceof MoradorEntity m ? m.nome() : "?";
		Component texto;
		if (p.desviado > 0) {
			a.mudarTesouro(p.desviado / 2);
			texto = Component.translatable("arraial.cronica.corrupcao", nome, p.desviado);
			p.desviado = 0;
			p.suspeita = 0;
			a.legitimidade = Math.max(0, a.legitimidade - 40);
			Prefeitura.depor(level, a);
			if (denunciante != null) {
				a.mudarReputacao(denunciante, 10);
			}
		} else {
			texto = Component.translatable("arraial.cronica.investigacao_limpa", nome);
			a.legitimidade = Math.min(100, a.legitimidade + 8);
			if (denunciante != null) {
				a.mudarReputacao(denunciante, -8);
			}
		}
		Cronica.anotar(level, a.getBlockPos(), texto);
		Prefeitura.avisar(level, a, Component.literal("⚖ ").append(texto).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	/** /arraial conselho: a proposal now, or the vote now (the result goes to the command's source too). */
	public static int forcar(ServerLevel level, ArraialBlockEntity a, boolean votar, net.minecraft.commands.CommandSourceStack fonte) {
		List<MoradorEntity> moradores = a.moradores(level);
		Estado e = estado(level, a, moradores);
		Politica p = a.politica;
		if (votar && p.proposta != null) {
			votar(level, a, moradores, e);
			fonte.sendSystemMessage(p.ultimoResultado);
		} else if (!votar) {
			if (p.proposta == null) {
				propor(level, a, moradores, e);
			}
			if (p.proposta == null) {
				p.abrir(Tipo.FESTA, "", null, "?", Vilas.dia(level));
			}
			fonte.sendSystemMessage(Component.translatable("arraial.politica.em_votacao", p.descricao(), p.autorNome));
		}
		for (Map.Entry<Grupo, MoradorEntity> c : conselho(moradores).entrySet()) {
			fonte.sendSystemMessage(Component.translatable("arraial.politica.conselheiro", c.getKey().nome(), c.getValue().nome(), "-", humor(c.getKey(), e)));
		}
		a.setChanged();
		return 1;
	}

	// ---------------------------------------------------------------- elections and offices

	/** Who takes office: the oldest under a coronel; a vote among the councillors otherwise. */
	static @Nullable MoradorEntity escolher(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores) {
		List<MoradorEntity> aptos = moradores.stream().filter(m -> !m.getUUID().equals(a.deposto) && m.profissao() != Profissao.MASCATE && !m.crianca()).toList();
		if (aptos.isEmpty()) {
			return null;
		}
		Comparator<MoradorEntity> antiguidade = Comparator.comparingLong(MoradorEntity::diaChegada).thenComparing(MoradorEntity::nome);
		if (a.politica.regime == Regime.HEREDITARIO) {
			// The heir: the late chief's eldest grown child, or the spouse; else the oldest.
			MoradorEntity herdeiro = Familias.herdeiro(aptos, a.ultimoChefe);
			if (herdeiro != null) {
				Cronica.anotar(level, a.getBlockPos(), Component.translatable("arraial.cronica.herdou", herdeiro.nome(), herdeiro.sobrenome()));
				return herdeiro;
			}
			return aptos.stream().min(antiguidade).orElse(null);
		}
		List<MoradorEntity> candidatos = new ArrayList<>(conselho(aptos).values());
		if (candidatos.isEmpty()) {
			return aptos.stream().min(antiguidade).orElse(null);
		}
		Map<MoradorEntity, Integer> votos = new HashMap<>();
		for (MoradorEntity eleitor : aptos) {
			MoradorEntity escolha = candidatos.stream().max(Comparator.comparingDouble(c -> {
				double v = Grupo.de(c.profissao()) == Grupo.de(eleitor.profissao()) ? 2.0 : 0;
				v += Familias.parentes(c, eleitor) ? 2.5 : 0; // family votes for family
				v += (Vilas.dia(level) - c.diaChegada()) / 20.0; // experience
				v += new Random(eleitor.getUUID().getLeastSignificantBits() ^ c.getUUID().getMostSignificantBits()).nextGaussian();
				return v;
			})).orElse(candidatos.get(0));
			votos.merge(escolha, 1, Integer::sum);
		}
		MoradorEntity eleito = votos.entrySet().stream().max(Map.Entry.<MoradorEntity, Integer>comparingByValue()
				.thenComparing(en -> -en.getKey().diaChegada())).map(Map.Entry::getKey).orElse(null);
		if (eleito != null) {
			Cronica.anotar(level, a.getBlockPos(), Component.translatable("arraial.cronica.votos", eleito.nome(), votos.get(eleito), aptos.size()));
		}
		return eleito;
	}

	/** Buildings voted by the council (or demanded for security), to go first. */
	List<String> obrasVotadas(ServerLevel level, ArraialBlockEntity a) {
		List<String> l = new ArrayList<>();
		if (!obraVotada.isEmpty()) {
			l.add(obraVotada);
		}
		if (segurancaAte > Vilas.dia(level)) {
			if (Vilas.postosDe(level, a.getBlockPos(), ModPois.POSTO_GUARDA) == 0) {
				l.add("guarita");
			} else if (Prefeitura.obras(level, a.getBlockPos(), Muralha.ID) == 0) {
				l.add(Muralha.ID);
			}
		}
		return l;
	}

	void obraIniciada(String desejo) {
		if (desejo.equals(obraVotada)) {
			obraVotada = "";
		}
	}

	// ---------------------------------------------------------------- the urna (the player's way in)

	private static final Map<UUID, Integer> ESCOLHA = new HashMap<>();

	private static List<String> opcoes() {
		List<String> l = new ArrayList<>();
		for (Tipo t : Tipo.values()) {
			if (t != Tipo.OBRA) {
				l.add(t.id());
			}
		}
		OBRAS.forEach(o -> l.add("obra:" + o));
		return l;
	}

	public static void usar(ServerLevel level, BlockPos urna, Player p, ItemStack mao) {
		BlockPos vila = Vilas.maisProxima(level, urna);
		if (vila == null || !(level.getBlockEntity(vila) instanceof ArraialBlockEntity a)) {
			p.sendOverlayMessage(Component.translatable("arraial.feira.sem_vila"));
			return;
		}
		Politica pol = a.politica;
		List<String> opcoes = opcoes();
		int escolha = Math.floorMod(ESCOLHA.getOrDefault(p.getUUID(), 0), opcoes.size());
		if (mao.isEmpty() && p.isShiftKeyDown()) {
			escolha = (escolha + 1) % opcoes.size();
			ESCOLHA.put(p.getUUID(), escolha);
			p.sendOverlayMessage(Component.translatable("arraial.politica.escolhida", descricaoOpcao(opcoes.get(escolha))).withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (mao.isEmpty()) {
			painel(level, a, p, descricaoOpcao(opcoes.get(escolha)));
			return;
		}
		if (mao.is(Items.PAPER)) {
			if (pol.proposta != null) {
				p.sendSystemMessage(Component.translatable("arraial.politica.ocupada", pol.descricao()).withStyle(ChatFormatting.GRAY));
				return;
			}
			if (a.reputacao(p.getUUID()) < -10) {
				p.sendSystemMessage(Component.translatable("arraial.politica.ninguem_ouve").withStyle(ChatFormatting.RED));
				return;
			}
			String op = opcoes.get(escolha);
			Tipo t = op.startsWith("obra:") ? Tipo.OBRA : Tipo.de(op);
			if (t == null) {
				return;
			}
			mao.shrink(1);
			pol.abrir(t, op.startsWith("obra:") ? op.substring(5) : "", p.getUUID(), p.getName().getString(), Vilas.dia(level));
			a.setChanged();
			level.playSound(null, urna, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.0F);
			Prefeitura.avisar(level, a, Component.literal("📜 ").append(Component.translatable("arraial.politica.nova", p.getName().getString(),
					pol.descricao())).withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (mao.is(ModItems.REIS)) {
			if (pol.proposta == null) {
				p.sendSystemMessage(Component.translatable("arraial.politica.nada_para_apoiar").withStyle(ChatFormatting.GRAY));
				return;
			}
			int da = Math.min(10, mao.getCount());
			mao.shrink(da);
			pol.apoio += da * 2;
			a.mudarTesouro(da / 2); // half goes to the coffers as a "donation"
			// Buying votes may come to light.
			if (level.getRandom().nextFloat() < 0.15F) {
				pol.escandalo = true;
				a.mudarReputacao(p.getUUID(), -15);
				Component texto = Component.translatable("arraial.cronica.escandalo", p.getName().getString());
				Cronica.anotar(level, a.getBlockPos(), texto);
				Prefeitura.avisar(level, a, texto.copy().withStyle(ChatFormatting.RED));
			} else {
				p.sendSystemMessage(Component.translatable("arraial.politica.apoiou", da).withStyle(ChatFormatting.GREEN));
			}
			a.setChanged();
			return;
		}
		painel(level, a, p, descricaoOpcao(opcoes.get(escolha)));
	}

	private static Component descricaoOpcao(String op) {
		return op.startsWith("obra:") ? descricao(Tipo.OBRA, op.substring(5)) : descricao(Tipo.de(op), "");
	}

	/** The council at a glance: regime, taxes, councillors and their groups' mood, the proposal in vote. */
	public static void painel(ServerLevel level, ArraialBlockEntity a, Player p, @Nullable Component escolhida) {
		Politica pol = a.politica;
		List<MoradorEntity> moradores = a.moradores(level);
		Estado e = estado(level, a, moradores);
		p.sendSystemMessage(Component.literal("🏛 ").append(Component.translatable("arraial.politica.titulo", a.nomeCompleto(),
				Component.translatable("arraial.politica.regime." + pol.regime.name().toLowerCase(java.util.Locale.ROOT)),
				pol.imposto)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		Map<Grupo, Long> tamanho = new EnumMap<>(Grupo.class);
		moradores.forEach(m -> tamanho.merge(Grupo.de(m.profissao()), 1L, Long::sum));
		for (Map.Entry<Grupo, MoradorEntity> c : conselho(moradores).entrySet()) {
			int h = humor(c.getKey(), e);
			p.sendSystemMessage(Component.literal(" • ").append(Component.translatable("arraial.politica.conselheiro", c.getKey().nome(),
					c.getValue().nome(), tamanho.getOrDefault(c.getKey(), 0L), h))
					.withStyle(h < 25 ? ChatFormatting.RED : h < 50 ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
		}
		if (pol.proposta != null) {
			p.sendSystemMessage(Component.translatable("arraial.politica.em_votacao", pol.descricao(), pol.autorNome).withStyle(ChatFormatting.AQUA));
		}
		if (!pol.ultimoResultado.getString().isEmpty()) {
			p.sendSystemMessage(Component.translatable("arraial.politica.ultimo", pol.ultimoResultado).withStyle(ChatFormatting.GRAY));
		}
		if (pol.suspeita >= 8) {
			p.sendSystemMessage(Component.translatable("arraial.politica.boato").withStyle(ChatFormatting.DARK_PURPLE));
		}
		if (pol.cidadaos.contains(p.getUUID())) {
			p.sendSystemMessage(Component.translatable("arraial.politica.voce_cidadao").withStyle(ChatFormatting.GOLD));
		}
		if (escolhida != null) {
			p.sendSystemMessage(Component.translatable("arraial.politica.dica", escolhida).withStyle(ChatFormatting.GRAY));
		}
	}

	// ---------------------------------------------------------------- saving

	void ler(ValueInput in) {
		regime = in.getStringOr("Regime", "ELEITO").equals("HEREDITARIO") ? Regime.HEREDITARIO : Regime.ELEITO;
		imposto = in.getIntOr("Imposto", 1);
		proposta = Tipo.de(in.getStringOr("Proposta", ""));
		alvo = in.getStringOr("PropostaAlvo", "");
		autorJogador = in.read("PropostaJogador", UUIDUtil.CODEC).orElse(null);
		autorNome = in.getStringOr("PropostaAutor", "");
		propostaDia = in.getLongOr("PropostaDia", 0L);
		apoio = in.getIntOr("Apoio", 0);
		escandalo = in.getBooleanOr("Escandalo", false);
		desviado = in.getIntOr("Desviado", 0);
		suspeita = in.getIntOr("Suspeita", 0);
		cidadaos.clear();
		in.read("Cidadaos", UUIDUtil.STRING_CODEC.listOf()).ifPresent(cidadaos::addAll);
		segurancaAte = in.getLongOr("SegurancaAte", 0L);
		obraVotada = in.getStringOr("ObraVotada", "");
		festaVotada = in.getLongOr("FestaVotada", 0L);
		ultimoResultado = in.read("UltimoResultado", net.minecraft.network.chat.ComponentSerialization.CODEC).orElse(Component.empty());
		String pr = in.getStringOr("Protesto", "");
		protesto = null;
		for (Grupo g : Grupo.values()) {
			if (g.id().equals(pr)) {
				protesto = g;
			}
		}
		protestoDia = in.getLongOr("ProtestoDia", 0L);
	}

	void gravar(ValueOutput out) {
		out.putString("Regime", regime.name());
		out.putInt("Imposto", imposto);
		out.putString("Proposta", proposta == null ? "" : proposta.id());
		out.putString("PropostaAlvo", alvo);
		if (autorJogador != null) {
			out.store("PropostaJogador", UUIDUtil.CODEC, autorJogador);
		}
		out.putString("PropostaAutor", autorNome);
		out.putLong("PropostaDia", propostaDia);
		out.putInt("Apoio", apoio);
		out.putBoolean("Escandalo", escandalo);
		out.putInt("Desviado", desviado);
		out.putInt("Suspeita", suspeita);
		out.store("Cidadaos", UUIDUtil.STRING_CODEC.listOf(), List.copyOf(cidadaos));
		out.putLong("SegurancaAte", segurancaAte);
		out.putString("ObraVotada", obraVotada);
		out.putLong("FestaVotada", festaVotada);
		out.store("UltimoResultado", net.minecraft.network.chat.ComponentSerialization.CODEC, ultimoResultado);
		out.putString("Protesto", protesto == null ? "" : protesto.id());
		out.putLong("ProtestoDia", protestoDia);
	}
}
