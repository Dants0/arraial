package com.guilhermegoes.arraial.vila;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The regional economy: what each arraial lives on, the roads between them, and what goods cost
 * once the neighbours are counted in.
 * <ul>
 * <li><b>Especialidade</b>: the trades decide it (a village of roceiros is agrícola, of
 * cavouqueiros and barreiros mineradora...). It drives production while the village is unloaded.</li>
 * <li><b>Rotas</b>: every pair of arraiais within {@link #ALCANCE} blocks has a road. Robberies make
 * it riskier; two robberies in a short time close it for a few days; enemies (reputation ≤
 * {@link Diplomacia#INIMIGO}) keep it closed: there is war between them.</li>
 * <li><b>Preço regional</b>: a good that is short here but plentiful next door is brought over,
 * so its price is capped at the neighbour's price plus transport (distance and risk). When the
 * roads are closed or robbed, that relief disappears and scarcity shows in the price.</li>
 * </ul>
 */
public final class Economia {
	/** Arraiais farther apart than this do not trade. */
	public static final int ALCANCE = 900;
	private static final int ROUBOS_PARA_BLOQUEAR = 2;
	private static final long JANELA_ROUBOS = 8;
	private static final long DIAS_BLOQUEIO = 4;

	public enum Especialidade {
		AGRICOLA, MINERADORA, MADEIREIRA, PESQUEIRA, PASTORIL, ARTESANAL, MISTA;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		public Component nome() {
			return Component.translatable("arraial.especialidade." + id());
		}

		public static Especialidade de(String id) {
			for (Especialidade e : values()) {
				if (e.id().equals(id)) {
					return e;
				}
			}
			return MISTA;
		}
	}

	/** The specialty from the trades: the group with the most people (at least 2). */
	public static Especialidade especialidade(Map<String, Integer> oficios) {
		int agricola = n(oficios, "roceiro") + n(oficios, "farinheiro");
		int mineradora = n(oficios, "cavouqueiro") + n(oficios, "barreiro");
		int madeireira = n(oficios, "lenhador");
		int pesqueira = n(oficios, "pescador");
		int pastoril = n(oficios, "pastor") + n(oficios, "cacador");
		int artesanal = n(oficios, "oleiro") + n(oficios, "boticario") + n(oficios, "feirante");
		int[] v = {agricola, mineradora, madeireira, pesqueira, pastoril, artesanal};
		int melhor = -1, max = 1;
		for (int i = 0; i < v.length; i++) {
			if (v[i] > max) {
				max = v[i];
				melhor = i;
			}
		}
		return melhor < 0 ? Especialidade.MISTA : Especialidade.values()[melhor];
	}

	private static int n(Map<String, Integer> m, String k) {
		return m.getOrDefault(k, 0);
	}

	// ---------------------------------------------------------------- roads

	public static Registro.Rota rota(String a, String b) {
		Registro.Info ia = Registro.obter(a);
		return ia.rotas.computeIfAbsent(b, k -> new Registro.Rota());
	}

	/** Closed: at war, or robbed too often lately. */
	public static boolean bloqueada(String a, String b, long hoje) {
		Registro.Info ia = Registro.get(a);
		if (ia != null && ia.reputacaoCom(b) <= Diplomacia.INIMIGO) {
			return true;
		}
		Registro.Rota r = ia == null ? null : ia.rotas.get(b);
		return r != null && hoje < r.bloqueadaAte;
	}

	public static boolean emGuerra(String a, String b) {
		Registro.Info ia = Registro.get(a);
		return ia != null && ia.reputacaoCom(b) <= Diplomacia.INIMIGO;
	}

	/** Chance of a robbery on the road (before escorts): grows with recent robberies and enemies around. */
	public static float risco(String a, String b, long hoje) {
		Registro.Info ia = Registro.get(a);
		Registro.Rota r = ia == null ? null : ia.rotas.get(b);
		float risco = 0.06F;
		if (r != null && hoje - r.ultimoRoubo < JANELA_ROUBOS) {
			risco += 0.06F * r.roubos;
		}
		if (ia != null) {
			long inimigos = ia.reputacao.values().stream().filter(v -> v <= Diplomacia.INIMIGO).count();
			risco += 0.04F * inimigos; // enemies hire bandits for the roads
		}
		return Math.min(0.5F, risco);
	}

	/** A caravan was robbed on the road from {@code a} to {@code b}. */
	public static void roubo(String a, String b, long hoje) {
		for (Registro.Rota r : new Registro.Rota[] {rota(a, b), rota(b, a)}) {
			if (hoje - r.ultimoRoubo >= JANELA_ROUBOS) {
				r.roubos = 0;
			}
			r.roubos++;
			r.ultimoRoubo = hoje;
			if (r.roubos >= ROUBOS_PARA_BLOQUEAR) {
				r.bloqueadaAte = hoje + DIAS_BLOQUEIO;
			}
		}
		Registro.Info ia = Registro.get(a), ib = Registro.get(b);
		if (ia != null && ib != null && rota(a, b).bloqueadaAte > hoje) {
			Mundo.historia(a, hoje, "arraial.historia.rota_bloqueada", ib.nome);
			Mundo.historia(b, hoje, "arraial.historia.rota_bloqueada", ia.nome);
		}
	}

	public static void viagem(String a, String b) {
		rota(a, b).viagens++;
		rota(b, a).viagens++;
	}

	// ---------------------------------------------------------------- prices

	/**
	 * The price after the neighbours: if someone within reach sells it cheaper (with transport),
	 * the local price comes down toward that. Closed roads and war push it up instead.
	 */
	public static double regional(String meu, BlockPos pos, String item, double local, double base, long hoje) {
		double melhor = local;
		int fechadas = 0, abertas = 0;
		for (Map.Entry<String, Registro.Info> e : Registro.todas().entrySet()) {
			Registro.Info v = e.getValue();
			if (e.getKey().equals(meu) || v.abandonada) {
				continue;
			}
			double dist = Math.sqrt(v.posicao().distSqr(pos));
			if (dist > ALCANCE) {
				continue;
			}
			if (bloqueada(meu, e.getKey(), hoje)) {
				fechadas++;
				continue;
			}
			abertas++;
			if (v.estoque.getOrDefault(item, 0) < 24) {
				continue; // nothing to spare there
			}
			double frete = base * (0.15 + dist / 1500.0) * (1 + risco(meu, e.getKey(), hoje) * 2);
			double la = v.precos.getOrDefault(item, base) + frete;
			melhor = Math.min(melhor, la);
		}
		double preco = local * 0.5 + melhor * 0.5;
		// Cut off from the region: everything that has to come from outside costs more.
		if (fechadas > 0) {
			preco *= 1 + 0.12 * fechadas / Math.max(1, fechadas + abertas) * 2;
		}
		return Math.max(base * 0.4, Math.min(base * 4, preco));
	}

	public static @Nullable String id(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
		return Registro.id(level.dimension().identifier().toString(), pos);
	}

	private Economia() {
	}
}
