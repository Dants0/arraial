package com.guilhermegoes.arraial.vila;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The arraial's calendar (GDD: festas). A game year is short so festas come around while you
 * play: 12 months of {@link #DIAS_POR_MES} days, 24 days a year. Like the sertão, the year has
 * two seasons: the tempo das águas (November to March, rain and floods) and the tempo da seca
 * (April to October, droughts).
 * <p>Festas: Feira Grande on the 1st of March and September (needs a banca de feira), São João on
 * the 2nd of June, and each arraial's padroeiro on the 2nd of its own month.
 */
public final class Calendario {
	public static final int DIAS_POR_MES = 2;
	public static final int MESES = 12;
	public static final int DIAS_POR_ANO = DIAS_POR_MES * MESES;

	/** Month 1..12 of a game day (day 1 is the 1st of January, year 1). */
	public static int mes(long dia) {
		return (int) ((dia - 1) % DIAS_POR_ANO / DIAS_POR_MES) + 1;
	}

	public static int diaDoMes(long dia) {
		return (int) ((dia - 1) % DIAS_POR_MES) + 1;
	}

	public static long ano(long dia) {
		return (dia - 1) / DIAS_POR_ANO + 1;
	}

	/** The tempo da seca: April to October. */
	public static boolean tempoDaSeca(long dia) {
		int m = mes(dia);
		return m >= 4 && m <= 10;
	}

	public static Component nomeMes(int mes) {
		return Component.translatable("arraial.mes." + mes);
	}

	/** "2 de Junho, ano 1 · tempo da seca". */
	public static Component data(long dia) {
		return Component.translatable("arraial.calendario.data", diaDoMes(dia), nomeMes(mes(dia)), ano(dia),
				Component.translatable(tempoDaSeca(dia) ? "arraial.calendario.seca" : "arraial.calendario.aguas"));
	}

	public static Component hoje(ServerLevel level) {
		return data(Vilas.dia(level));
	}

	/** The festa that falls on {@code dia} for this arraial, if any. */
	public static Festa.@Nullable Tipo festaDoDia(ServerLevel level, ArraialBlockEntity a, long dia) {
		int m = mes(dia), d = diaDoMes(dia);
		if (m == 6 && d == 2) {
			return Festa.Tipo.SAO_JOAO;
		}
		if (d == 2 && m == mesDoPadroeiro(a.nome())) {
			return Festa.Tipo.PADROEIRO;
		}
		if (d == 1 && (m == 3 || m == 9) && a.temFeira(level)) {
			return Festa.Tipo.FEIRA_GRANDE;
		}
		if (d == 1 && Cultura.doMes(Registro.get(Economia.id(level, a.getBlockPos())), m) != null) {
			return Festa.Tipo.TRADICAO;
		}
		return null;
	}

	/** The next festa from today on: the type and how many days away (0 = today). */
	public record Proxima(Festa.Tipo tipo, int dias, Component nome) {
	}

	public static @Nullable Proxima proxima(ServerLevel level, ArraialBlockEntity a) {
		long hoje = Vilas.dia(level);
		for (int i = 0; i <= DIAS_POR_ANO; i++) {
			Festa.Tipo t = festaDoDia(level, a, hoje + i);
			if (t != null) {
				Component nome = t == Festa.Tipo.TRADICAO
						? Cultura.doMes(Registro.get(Economia.id(level, a.getBlockPos())), mes(hoje + i)).nome() : t.nome(a);
				return new Proxima(t, i, nome);
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- the patron saint

	private static final java.util.List<String> SANTOS = java.util.List.of(
			"São Sebastião", "Nossa Senhora do Rosário", "São Benedito", "Santa Rita", "São José", "Santana");

	/** The patron saint, taken from the name ("Arraial de Santa Luzia") or chosen by it. */
	public static String padroeiro(String nome) {
		String resto = nome.indexOf(' ') < 0 ? nome : nome.substring(nome.indexOf(' ') + 1);
		for (String prep : new String[] {"de ", "do ", "da ", "dos ", "das "}) {
			if (resto.startsWith(prep)) {
				resto = resto.substring(prep.length());
				break;
			}
		}
		if (resto.startsWith("São ") || resto.startsWith("Santa ") || resto.startsWith("Santo ")
				|| resto.startsWith("Nossa Senhora") || resto.startsWith("Bom Jesus")) {
			return resto;
		}
		return SANTOS.get(Math.floorMod(nome.hashCode(), SANTOS.size()));
	}

	/** The month of the padroeiro's festa: chosen by the name, never June (that is São João's). */
	public static int mesDoPadroeiro(String nome) {
		int m = Math.floorMod(nome.hashCode() * 31 + 7, MESES) + 1;
		return m == 6 ? 8 : m;
	}

	private Calendario() {
	}
}
