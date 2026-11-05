package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import com.guilhermegoes.arraial.vila.ArraialBlockEntity;
import com.guilhermegoes.arraial.vila.Crise;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * What moradores say, to the player and to each other. Lines are hand-written (lang files,
 * "arraial.fala.*" and "arraial.conversa.*") and chosen by context: hunger, the current crisis,
 * having no home, being prefeito, the trade, the weather. No generative AI (DECISIONS D-009).
 */
public final class Falas {
	/** How many variants each line key has in the lang files. */
	private static final Map<String, Integer> VARIANTES = Map.ofEntries(
			Map.entry("fome", 3), Map.entry("sem_casa", 2), Map.entry("prefeito", 2), Map.entry("prefeito.pedido", 1),
			Map.entry("noite", 2), Map.entry("chuva", 2), Map.entry("generico", 5), Map.entry("presente", 2),
			Map.entry("crise.seca", 2), Map.entry("crise.enchente", 2), Map.entry("crise.praga", 2), Map.entry("crise.salteadores", 2),
			Map.entry("crise.epidemia", 2), Map.entry("crise.incendio", 2), Map.entry("doente", 2));
	/** Conversation topics (each variant has an opening ".a" and a reply ".b"). */
	private static final Map<String, Integer> CONVERSAS = Map.ofEntries(
			Map.entry("generico", 6), Map.entry("chuva", 2), Map.entry("fome", 2), Map.entry("obra", 2), Map.entry("oficio", 1),
			Map.entry("crise.seca", 1), Map.entry("crise.enchente", 1), Map.entry("crise.praga", 1), Map.entry("crise.salteadores", 1),
			Map.entry("crise.epidemia", 1), Map.entry("crise.incendio", 1), Map.entry("feira", 1), Map.entry("prefeito", 1));
	private static final int VARIANTES_OFICIO = 2;

	private static Component linha(RandomSource r, String chave, Object... args) {
		int n = VARIANTES.getOrDefault(chave, 1);
		return Component.translatable("arraial.fala." + chave + "." + r.nextInt(n), args);
	}

	private static Component oficio(RandomSource r, MoradorEntity m) {
		return Component.translatable("arraial.fala.oficio." + m.profissao().id() + "." + r.nextInt(VARIANTES_OFICIO));
	}

	/** What a morador answers when the player talks to them. */
	public static Component paraJogador(ServerLevel level, MoradorEntity m, String jogador) {
		RandomSource r = level.getRandom();
		ArraialBlockEntity a = m.arraial();
		Crise.Tipo crise = a == null ? null : a.crise().tipo();
		if (m.doente() && r.nextFloat() < 0.8F) {
			return linha(r, "doente");
		}
		if (m.comFome() && r.nextFloat() < 0.8F) {
			return linha(r, "fome");
		}
		if (crise != null && r.nextFloat() < 0.6F) {
			return linha(r, "crise." + crise.id(), jogador);
		}
		if (m.casa() == null && r.nextFloat() < 0.5F) {
			return linha(r, "sem_casa");
		}
		if (m.prefeito() && a != null && r.nextFloat() < 0.6F) {
			if (!a.pedido().getString().isEmpty()) {
				return Component.translatable("arraial.fala.prefeito.pedido.0", a.pedido());
			}
			return linha(r, "prefeito", a.nomeCompleto(), jogador);
		}
		if (r.nextFloat() < 0.5F) {
			return oficio(r, m);
		}
		if (level.isRaining() && r.nextFloat() < 0.5F) {
			return linha(r, "chuva");
		}
		if (level.isDarkOutside() && r.nextFloat() < 0.5F) {
			return linha(r, "noite");
		}
		return linha(r, "generico", jogador, a == null ? "" : a.nomeCompleto());
	}

	/** Thanks for a gift of food. */
	public static Component presente(ServerLevel level, String jogador) {
		return linha(level.getRandom(), "presente", jogador);
	}

	/** A short exchange between two moradores: the opening line and the reply. */
	public static List<Component> conversa(ServerLevel level, MoradorEntity a, MoradorEntity b) {
		RandomSource r = level.getRandom();
		List<String> temas = new ArrayList<>();
		ArraialBlockEntity vila = a.arraial();
		Crise.Tipo crise = vila == null ? null : vila.crise().tipo();
		if (crise != null) {
			temas.add("crise." + crise.id());
			temas.add("crise." + crise.id());
		}
		if (level.isRaining()) {
			temas.add("chuva");
		}
		if (a.comFome() || b.comFome()) {
			temas.add("fome");
		}
		String obra = null;
		if (a.vila() != null) {
			List<CanteiroBlockEntity> obras = CanteiroBlockEntity.daVila(level, a.vila());
			if (!obras.isEmpty() && obras.get(0).projeto().isPresent()) {
				obra = obras.get(0).projeto().get().nome().getString();
				temas.add("obra");
			}
		}
		if (b.profissao() != Profissao.NENHUMA) {
			temas.add("oficio");
		}
		if (b.prefeito() || a.prefeito()) {
			temas.add("prefeito");
		}
		if (vila != null && vila.temFeira(level)) {
			temas.add("feira");
		}
		temas.add("generico");
		temas.add("generico");
		String tema = temas.get(r.nextInt(temas.size()));
		int v = r.nextInt(CONVERSAS.getOrDefault(tema, 1));
		String base = "arraial.conversa." + tema + "." + v;
		String nomeA = a.nome(), nomeB = b.nome();
		if (tema.equals("oficio")) {
			return List.of(Component.translatable(base + ".a", nomeB, b.titulo()), oficio(r, b));
		}
		Object extra = obra != null ? obra : "";
		return List.of(Component.translatable(base + ".a", nomeB, extra), Component.translatable(base + ".b", nomeA, extra));
	}

	private Falas() {
	}
}
