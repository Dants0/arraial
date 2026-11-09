package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;

/**
 * A snapshot of how an arraial is doing: people, beds, food, mood, money, stage, crisis, works and
 * trades. Used by {@code /arraial diagnostico} (handy to attach to a bug report) and by the
 * long-running test, which writes one CSV line per arraial per day.
 */
public final class Diagnostico {
	public static final String CABECALHO = "dia;vila;autonoma;hispano;estagio;era;moradores;criancas;camas;sem_casa;com_fome;doentes;comida;"
			+ "satisfacao;legitimidade;tesouro;crise;obras_prontas;obras_andando;obras;oficios;pedido";

	public record Retrato(String nome, boolean autonoma, boolean hispano, String estagio, String era, int moradores, int criancas, long camas,
			int semCasa, int comFome, int doentes, int comida, int satisfacao, int legitimidade, int tesouro, String crise, int prontas,
			int andando, String obras, String oficios, String pedido) {

		public String csv(long dia) {
			return String.join(";", String.valueOf(dia), nome, String.valueOf(autonoma), String.valueOf(hispano), estagio, era,
					String.valueOf(moradores), String.valueOf(criancas), String.valueOf(camas), String.valueOf(semCasa), String.valueOf(comFome),
					String.valueOf(doentes), String.valueOf(comida), String.valueOf(satisfacao), String.valueOf(legitimidade), String.valueOf(tesouro),
					crise, String.valueOf(prontas), String.valueOf(andando), obras, oficios, pedido.replace(';', ','));
		}

		public String resumo() {
			return nome + " | " + estagio + "/" + era + " | gente " + moradores + " (" + criancas + " crianças), camas " + camas + ", sem casa "
					+ semCasa + ", fome " + comFome + ", doentes " + doentes + " | comida " + comida + " | satisfação " + satisfacao
					+ ", legitimidade " + legitimidade + ", tesouro " + tesouro + " | crise " + crise + " | obras " + prontas + " prontas, "
					+ andando + " em andamento " + obras + " | ofícios " + oficios + " | pedido: " + pedido;
		}
	}

	public static Retrato de(ServerLevel level, ArraialBlockEntity a) {
		List<MoradorEntity> ms = a.moradores(level);
		int criancas = 0, semCasa = 0, fome = 0, doentes = 0;
		Map<String, Integer> oficios = new TreeMap<>();
		for (MoradorEntity m : ms) {
			if (m.crianca()) {
				criancas++;
			}
			if (m.casa() == null) {
				semCasa++;
			}
			if (m.comFome()) {
				fome++;
			}
			if (m.doente()) {
				doentes++;
			}
			if (m.profissao() != Profissao.NENHUMA) {
				oficios.merge(m.profissao().id(), 1, Integer::sum);
			}
		}
		int comida = Vilas.contar(Vilas.armazens(level, a.getBlockPos()), s -> s.has(DataComponents.FOOD));
		int prontas = 0, andando = 0;
		StringBuilder obras = new StringBuilder();
		for (BlockPos p : CanteiroBlockEntity.ativos(level)) {
			if (!p.closerThan(a.getBlockPos(), Vilas.RAIO) || !(level.getBlockEntity(p) instanceof CanteiroBlockEntity c) || c.projeto().isEmpty()) {
				continue;
			}
			String id = c.projeto().get().id();
			if (c.concluida()) {
				prontas++;
			} else {
				andando++;
				obras.append('[').append(id).append(' ').append(c.progresso()).append(']');
			}
		}
		Crise.Tipo crise = a.crise.tipo();
		return new Retrato(a.nomeCompleto(), a.autonoma, a.hispano(), a.estagio.name().toLowerCase(java.util.Locale.ROOT), a.era.id(), ms.size(),
				criancas, a.camas(level), semCasa, fome, doentes, comida, a.satisfacao, a.legitimidade, a.tesouro,
				crise == null ? "-" : crise.name().toLowerCase(java.util.Locale.ROOT), prontas, andando, obras.toString(), oficios.toString(),
				a.pedido.getString());
	}

	/** The arraiais within {@code raio} blocks of {@code centro} that are loaded right now. */
	public static List<ArraialBlockEntity> perto(ServerLevel level, BlockPos centro, int raio) {
		List<ArraialBlockEntity> l = new java.util.ArrayList<>();
		for (BlockPos p : Vilas.ativas(level)) {
			if (p.closerThan(centro, raio) && level.getBlockEntity(p) instanceof ArraialBlockEntity a) {
				l.add(a);
			}
		}
		return l;
	}

	private Diagnostico() {
	}
}
