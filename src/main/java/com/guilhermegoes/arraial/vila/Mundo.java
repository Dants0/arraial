package com.guilhermegoes.arraial.vila;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;

/**
 * The living world: arraiais do not freeze when nobody is near. Once per game day every arraial
 * whose chunks are unloaded lives that day from its record in the {@link Registro}:
 * <ol>
 * <li>its trades produce by its specialty and the season (the roça yields less in the tempo da seca);</li>
 * <li>everyone eats; hungry days and unhappiness push people to migrate to the best neighbour
 * (straight into it when it is loaded, as a newcomer with a story);</li>
 * <li>with plenty of food, room and good mood, people arrive and it grows;</li>
 * <li>crises happen here too (drought, bandits, sickness);</li>
 * <li>merchants carry surplus to neighbours short of it, along roads that can be robbed and closed;</li>
 * <li>with nobody left, the arraial is abandoned.</li>
 * </ol>
 * All of it is written in its regional history, which joins the Crônica when the arraial loads,
 * and the goods and people that came and went are settled then too.
 */
public final class Mundo {
	private static final int MAX_DIAS_POR_VEZ = 30;
	private static final int HISTORIA_MAX = 60;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 200 == 0) {
				ServerLevel level = server.overworld();
				avancar(level, Vilas.dia(level), false);
			}
		});
	}

	/** Lives every unloaded arraial up to {@code hoje} (or {@code dias} more when forced by the command). */
	static void avancar(ServerLevel level, long hoje, boolean forcado) {
		String dim = level.dimension().identifier().toString();
		List<String> vivas = new ArrayList<>();
		for (Map.Entry<String, Registro.Info> e : new ArrayList<>(Registro.todas().entrySet())) {
			Registro.Info v = e.getValue();
			if (!v.dimensao.equals(dim)) {
				continue;
			}
			if (carregada(level, v)) {
				v.diaSimulado = Math.max(v.diaSimulado, hoje);
				continue;
			}
			if (v.diaSimulado == 0 || v.diaSimulado > hoje && !forcado) {
				v.diaSimulado = Math.max(1, v.dia); // first sight, or the clock was set back
			}
			vivas.add(e.getKey());
		}
		for (int n = 0; n < MAX_DIAS_POR_VEZ; n++) {
			boolean algum = false;
			for (String id : vivas) {
				Registro.Info v = Registro.get(id);
				if (v != null && v.diaSimulado < hoje) {
					long d = v.diaSimulado + 1;
					dia(level, id, v, d);
					mercador(level, id, v, d);
					v.diaSimulado = d;
					algum = true;
				}
			}
			if (!algum) {
				break;
			}
		}
	}

	/** /arraial avancar: N extra days for every unloaded arraial, now (the calendar does not move). */
	public static int forcarDias(ServerLevel level, int dias) {
		String dim = level.dimension().identifier().toString();
		long hoje = Vilas.dia(level);
		List<String> ids = new ArrayList<>();
		for (Map.Entry<String, Registro.Info> e : Registro.todas().entrySet()) {
			if (e.getValue().dimensao.equals(dim) && !carregada(level, e.getValue())) {
				ids.add(e.getKey());
			}
		}
		for (int i = 1; i <= dias; i++) {
			for (String id : ids) {
				Registro.Info v = Registro.get(id);
				if (v != null) {
					dia(level, id, v, hoje + i);
					mercador(level, id, v, hoje + i);
				}
			}
		}
		return ids.size();
	}

	public static boolean carregada(ServerLevel level, Registro.Info v) {
		return v.dimensao.equals(level.dimension().identifier().toString()) && level.isLoaded(v.posicao())
				&& level.getBlockEntity(v.posicao()) instanceof ArraialBlockEntity;
	}

	/** Writes into the regional history of {@code id} (and, for the Crônica, when it loads). */
	public static void historia(String id, long dia, String chave, String... args) {
		Registro.Info v = Registro.get(id);
		if (v == null) {
			return;
		}
		Registro.Evento e = new Registro.Evento(dia, chave, args);
		v.historia.add(e);
		while (v.historia.size() > HISTORIA_MAX) {
			v.historia.remove(0);
		}
		v.historiaPendente.add(e);
		while (v.historiaPendente.size() > HISTORIA_MAX) {
			v.historiaPendente.remove(0);
		}
	}

	// ---------------------------------------------------------------- one day of an unloaded arraial

	private static void dia(ServerLevel level, String id, Registro.Info v, long d) {
		if (v.abandonada) {
			return;
		}
		RandomSource r = level.getRandom();
		int pop = v.populacao;
		if (pop <= 0) {
			abandonar(id, v, d, "arraial.historia.motivo.vazia");
			return;
		}
		// Crises.
		if (!v.criseLonge.isEmpty() && d >= v.criseLongeFim) {
			historia(id, d, "arraial.historia.crise_fim", v.nome);
			v.criseLonge = "";
		}
		if (v.criseLonge.isEmpty() && r.nextFloat() < 0.06F) {
			crise(level, id, v, d, r);
			pop = v.populacao;
		}
		// Production by trade.
		Map<String, Integer> of = v.oficios.isEmpty() ? Map.of("roceiro", Math.max(1, pop / 2), "lenhador", 1) : v.oficios;
		// Those without a trade keep a small plot for themselves.
		int semOficio = Math.max(0, pop - of.values().stream().mapToInt(Integer::intValue).sum());
		double clima = v.criseLonge.equals("seca") ? 0.4 : Calendario.tempoDaSeca(d) ? 0.8 : 1.0;
		int roc = n(of, "roceiro"), far = n(of, "farinheiro");
		somar(v, "minecraft:bread", (int) Math.round((4 * roc + 0.6 * semOficio) * clima + r.nextFloat() - 0.5));
		somar(v, "arraial:farinha", (int) Math.round(5 * far * clima));
		somar(v, "minecraft:beef", n(of, "pastor") + 2 * n(of, "cacador"));
		somar(v, "minecraft:leather", n(of, "pastor") + n(of, "cacador"));
		somar(v, "minecraft:cod", 2 * n(of, "pescador"));
		somar(v, madeira(v), 8 * n(of, "lenhador"));
		somar(v, "minecraft:cobblestone", 16 * n(of, "cavouqueiro"));
		somar(v, "minecraft:clay_ball", 12 * n(of, "barreiro"));
		if (n(of, "oleiro") > 0 && v.estoque.getOrDefault("minecraft:clay_ball", 0) >= 6) {
			somar(v, "minecraft:clay_ball", -6);
			somar(v, "minecraft:bricks", 6);
		}
		somar(v, "arraial:remedio", n(of, "boticario"));

		// Everyone eats.
		int comeu = consumirComida(v, pop);
		int comida = comida(v);
		if (comeu < pop) {
			v.fome++;
			v.satisfacao -= 8;
		} else {
			v.fome = 0;
			v.satisfacao += comida >= pop * 3 ? 3 : 1;
		}
		if (!v.criseLonge.isEmpty()) {
			v.satisfacao -= 2;
		}
		// Small trade at the local feira and the tithe: a little money every day.
		int renda = Math.max(1, v.populacao / 3);
		v.tesouro += renda;
		v.tesouroPendente += renda;
		v.satisfacao = Math.max(0, Math.min(100, v.satisfacao));
		if (v.fome == 2) {
			historia(id, d, "arraial.historia.fome", v.nome);
		}

		// People leave...
		if (v.fome >= 2 && r.nextFloat() < 0.6F) {
			emigrar(level, id, v, d, "arraial.historia.motivo.fome");
		} else if (v.satisfacao < 25 && r.nextFloat() < 0.25F) {
			emigrar(level, id, v, d, "arraial.historia.motivo.insatisfeito");
		}
		// ...or arrive.
		if (v.fome == 0 && v.criseLonge.isEmpty() && comida >= v.populacao * 4 && v.populacao < Math.max(v.camas, 4) + 1
				&& v.satisfacao >= 50 && r.nextFloat() < 0.3F) {
			v.populacao++;
			v.saldoMoradores++;
			if (r.nextFloat() < 0.4F) {
				historia(id, d, "arraial.historia.cresceu", v.nome, String.valueOf(v.populacao));
			}
		}
		if (v.populacao <= 0) {
			abandonar(id, v, d, v.fome > 0 ? "arraial.historia.motivo.fome" : "arraial.historia.motivo.insatisfeito");
			return;
		}
		precos(v);
	}

	private static void crise(ServerLevel level, String id, Registro.Info v, long d, RandomSource r) {
		int seca = Calendario.tempoDaSeca(d) ? 4 : 1, salteadores = 2 + (int) v.reputacao.values().stream().filter(x -> x <= Diplomacia.INIMIGO).count() * 2;
		int epidemia = v.populacao >= 6 ? 1 : 0;
		int x = r.nextInt(seca + salteadores + epidemia);
		String tipo = x < seca ? "seca" : x < seca + salteadores ? "salteadores" : "epidemia";
		v.criseLonge = tipo;
		v.criseLongeFim = d + 2 + r.nextInt(3);
		historia(id, d, "arraial.historia.crise." + tipo, v.nome);
		if (tipo.equals("salteadores")) {
			for (Map.Entry<String, Integer> e : new ArrayList<>(v.estoque.entrySet())) {
				somar(v, e.getKey(), -e.getValue() / 6);
			}
			v.tesouro = v.tesouro * 4 / 5;
			v.criseLongeFim = d + 1;
		} else if (tipo.equals("epidemia") && v.populacao > 1) {
			v.populacao--;
			v.saldoMoradores--;
		}
	}

	private static void abandonar(String id, Registro.Info v, long d, String motivo) {
		v.abandonada = true;
		v.populacao = 0;
		historia(id, d, "arraial.historia.abandonada", v.nome, motivo);
	}

	/** One morador leaves for the best neighbour: food per head, friendship, open road. */
	private static void emigrar(ServerLevel level, String id, Registro.Info v, long d, String motivo) {
		if (v.populacao <= 0) {
			return;
		}
		String melhor = null;
		double nota = -Double.MAX_VALUE;
		for (Map.Entry<String, Registro.Info> e : Registro.todas().entrySet()) {
			Registro.Info o = e.getValue();
			if (e.getKey().equals(id) || o.abandonada || !o.dimensao.equals(v.dimensao)
					|| o.posicao().distSqr(v.posicao()) > (double) Economia.ALCANCE * Economia.ALCANCE
					|| Economia.bloqueada(id, e.getKey(), d) || comida(o) < 2 * Math.max(1, o.populacao)) {
				continue; // no better there: they would only go hungry again
			}
			double n = comida(o) / (double) Math.max(1, o.populacao) + v.reputacaoCom(e.getKey()) / 20.0 + o.satisfacao / 20.0;
			if (n > nota) {
				nota = n;
				melhor = e.getKey();
			}
		}
		v.populacao--;
		v.saldoMoradores--;
		if (melhor == null) {
			historia(id, d, "arraial.historia.partiu", v.nome, motivo);
			return;
		}
		Registro.Info destino = Registro.get(melhor);
		historia(id, d, "arraial.historia.emigrou", destino.nome, motivo);
		if (carregada(level, destino) && level.getBlockEntity(destino.posicao()) instanceof ArraialBlockEntity a) {
			a.receberMigrante(level, v.nome, motivo);
		} else {
			destino.populacao++;
			destino.saldoMoradores++;
			historia(melhor, d, "arraial.historia.imigrante", v.nome, motivo);
		}
	}

	// ---------------------------------------------------------------- merchants

	private static final List<String> MATERIAIS = List.of("minecraft:cobblestone", "minecraft:bricks", "minecraft:clay_ball",
			"minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log", "minecraft:leather", "arraial:remedio");

	/**
	 * Some days a merchant leaves an unloaded arraial with its surplus (food or materials) for the
	 * neighbour that lacks it most. Robbery on the road loses the load and can close the road.
	 */
	private static void mercador(ServerLevel level, String id, Registro.Info v, long d) {
		RandomSource r = level.getRandom();
		if (v.abandonada || v.populacao < 2 || r.nextFloat() > 0.4F) {
			return;
		}
		int comida = comida(v);
		String melhorDest = null, melhorItem = null;
		int melhorQtd = 0;
		double melhorNota = 0;
		for (Map.Entry<String, Registro.Info> e : Registro.todas().entrySet()) {
			Registro.Info o = e.getValue();
			if (e.getKey().equals(id) || o.abandonada || o.populacao <= 0 || !o.dimensao.equals(v.dimensao)) {
				continue;
			}
			double dist = Math.sqrt(o.posicao().distSqr(v.posicao()));
			if (dist > Economia.ALCANCE || Economia.bloqueada(id, e.getKey(), d) || v.reputacaoCom(e.getKey()) < -30) {
				continue;
			}
			// Food: our surplus against their hunger.
			int sobra = comida - v.populacao * 6;
			int falta = o.populacao * 3 - comida(o);
			if (sobra > 16 && falta > 0) {
				double nota = falta * 2 - dist / 100;
				if (nota > melhorNota) {
					melhorNota = nota;
					melhorDest = e.getKey();
					melhorItem = comidaQueSobra(v);
					melhorQtd = Math.min(Math.min(32, sobra / 2), falta + 8);
				}
			}
			for (String m : MATERIAIS) {
				int tem = v.estoque.getOrDefault(m, 0);
				if (tem > 64 && o.estoque.getOrDefault(m, 0) < 16) {
					double nota = 10 - dist / 100;
					if (nota > melhorNota) {
						melhorNota = nota;
						melhorDest = e.getKey();
						melhorItem = m;
						melhorQtd = Math.min(32, tem / 3);
					}
				}
			}
		}
		if (melhorDest == null || melhorItem == null || melhorQtd <= 0) {
			return;
		}
		Registro.Info o = Registro.get(melhorDest);
		somar(v, melhorItem, -melhorQtd);
		Economia.viagem(id, melhorDest);
		String qual = Simulacao.item(melhorItem).map(i -> i.getDefaultInstance().getHoverName().getString()).orElse(melhorItem);
		if (r.nextFloat() < Economia.risco(id, melhorDest, d)) {
			Economia.roubo(id, melhorDest, d);
			historia(id, d, "arraial.historia.mercador_roubado", o.nome, qual);
			historia(melhorDest, d, "arraial.historia.mercador_roubado_vinha", v.nome, qual);
			return;
		}
		double preco = o.precos.getOrDefault(melhorItem, 1.5);
		int pago = (int) Math.min(Math.floor(preco * melhorQtd), o.tesouro);
		if (pago < preco * melhorQtd / 2 && o.fome == 0) {
			somar(v, melhorItem, melhorQtd); // they cannot pay: the merchant stays home
			return;
		}
		int qtd = melhorQtd;
		String dest = melhorDest;
		Simulacao.item(melhorItem).ifPresent(i -> Caravana.entregar(level, dest, o, Map.of(i, qtd), -pago));
		v.tesouro += pago;
		v.tesouroPendente += pago;
		Registro.mudarReputacao(id, melhorDest, 1);
		if (r.nextFloat() < 0.5F || carregada(level, o)) {
			historia(id, d, "arraial.historia.mercador", String.valueOf(qtd), qual, o.nome, String.valueOf(pago));
			historia(melhorDest, d, "arraial.historia.mercador_chegou", v.nome, String.valueOf(qtd), qual);
		}
	}

	// ---------------------------------------------------------------- stock helpers

	private static int n(Map<String, Integer> m, String k) {
		return m.getOrDefault(k, 0);
	}

	/** Changes the snapshot and records the change for when the arraial loads. */
	static void somar(Registro.Info v, String item, int n) {
		if (n == 0) {
			return;
		}
		int antes = v.estoque.getOrDefault(item, 0);
		int depois = Math.max(0, antes + n);
		v.estoque.put(item, depois);
		v.pendentes.merge(item, depois - antes, Integer::sum);
	}

	private static boolean eComida(String id) {
		return Simulacao.item(id).map(i -> i.components().has(DataComponents.FOOD)).orElse(false);
	}

	static int comida(Registro.Info v) {
		int c = 0;
		for (Map.Entry<String, Integer> e : v.estoque.entrySet()) {
			if (eComida(e.getKey())) {
				c += e.getValue();
			}
		}
		return c;
	}

	private static String comidaQueSobra(Registro.Info v) {
		String melhor = "minecraft:bread";
		int max = 0;
		for (Map.Entry<String, Integer> e : v.estoque.entrySet()) {
			if (eComida(e.getKey()) && e.getValue() > max) {
				max = e.getValue();
				melhor = e.getKey();
			}
		}
		return melhor;
	}

	private static int consumirComida(Registro.Info v, int quanto) {
		int falta = quanto;
		for (Map.Entry<String, Integer> e : new ArrayList<>(v.estoque.entrySet())) {
			if (falta <= 0) {
				break;
			}
			if (eComida(e.getKey()) && e.getValue() > 0) {
				int tira = Math.min(falta, e.getValue());
				somar(v, e.getKey(), -tira);
				falta -= tira;
			}
		}
		return quanto - falta;
	}

	private static String madeira(Registro.Info v) {
		String melhor = "minecraft:oak_log";
		int max = 0;
		for (Map.Entry<String, Integer> e : v.estoque.entrySet()) {
			if (e.getKey().endsWith("_log") && e.getValue() > max) {
				max = e.getValue();
				melhor = e.getKey();
			}
		}
		return melhor;
	}

	/** Feira prices of an unloaded arraial, from its own stock (smoothed like {@link Feira}). */
	private static void precos(Registro.Info v) {
		int n = Math.max(4, v.populacao);
		for (Feira.Mercadoria m : Feira.TABELA) {
			String id = Feira.id(m.item());
			int estoque = v.estoque.getOrDefault(id, 0);
			int procura = m.item().components().has(DataComponents.FOOD) ? v.populacao * 3 : 8;
			double fator = Math.max(0.5, Math.min(3.0, (procura + n) / (double) (estoque + n)));
			double antes = v.precos.getOrDefault(id, m.base());
			v.precos.put(id, Math.round((antes * 0.6 + m.base() * fator * 0.4) * 10) / 10.0);
		}
	}

	static Item itemOu(String id, Item padrao) {
		return Simulacao.item(id).orElse(padrao);
	}

	private Mundo() {
	}
}
