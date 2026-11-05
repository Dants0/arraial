package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Relações públicas (GDD 7): the arraial's envoy visits the neighbours with letters and gifts.
 * A good visit raises the reputation between the two villages; a blunder lowers it.
 * <ul>
 * <li><b>Aliados</b> (reputation ≥ {@value #ALIADO}): in a crisis an ally sends food at once.</li>
 * <li><b>Inimigos</b> (reputation ≤ {@value #INIMIGO}): they may hire bandits against the arraial.</li>
 * </ul>
 * The chance of success grows with the arraial's satisfaction, with a gift in the envoy's bag and
 * with the reputation already earned; a village in bad shape sends a poorer impression.
 */
public final class Diplomacia {
	public static final int ALIADO = 50;
	public static final int INIMIGO = -50;
	private static final long DIA = 24000L;

	/** Morning: the envoy may set out on a mission. */
	static void diario(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores) {
		MoradorEntity e = moradores.stream().filter(m -> m.profissao() == Profissao.RELACOES && m.posto() != null
				&& m.viagemFase() == 0 && !m.comFome() && !m.doente()).findFirst().orElse(null);
		if (e == null || level.getRandom().nextFloat() > 0.5F) {
			return;
		}
		String destino = destino(level, a);
		if (destino != null) {
			e.setViagem(2, destino, 0); // straight out: letters need no loading
			Cronica.anotar(level, a.getBlockPos(), Component.translatable("arraial.cronica.missao_partiu", e.nome(),
					Registro.get(destino).nome));
		}
	}

	/** For the /arraial missao command. */
	public static void forcar(ServerLevel level, ArraialBlockEntity a, MoradorEntity e) {
		String destino = destino(level, a);
		if (destino != null) {
			e.setViagem(2, destino, 0);
		}
	}

	/** The neighbour whose relations most need attention: the nearest not yet allied. */
	private static @Nullable String destino(ServerLevel level, ArraialBlockEntity a) {
		String meu = Caravana.id(level, a.getBlockPos());
		Registro.Info eu = Registro.get(meu);
		String dim = level.dimension().identifier().toString();
		String melhor = null;
		double nota = -Double.MAX_VALUE;
		for (Map.Entry<String, Registro.Info> e : Registro.todas().entrySet()) {
			if (e.getKey().equals(meu) || !e.getValue().dimensao.equals(dim)) {
				continue;
			}
			int rep = eu == null ? 0 : eu.reputacaoCom(e.getKey());
			double n = -Math.abs(rep - 60) - Math.sqrt(e.getValue().posicao().distSqr(a.getBlockPos())) / 100
					+ level.getRandom().nextInt(20);
			if (n > nota) {
				nota = n;
				melhor = e.getKey();
			}
		}
		return melhor;
	}

	/** The envoy is back: how did it go? */
	public static void voltar(ServerLevel level, MoradorEntity e) {
		ArraialBlockEntity a = e.arraial();
		String destinoId = e.viagemDestino();
		Registro.Info d = Registro.get(destinoId);
		if (a == null || d == null) {
			e.setViagem(0, "", 0);
			return;
		}
		String meuId = Caravana.id(level, a.getBlockPos());
		Registro.Info eu = Registro.obter(meuId);
		int antes = eu.reputacaoCom(destinoId);
		boolean presente = e.carregando(Items.BREAD) + e.carregando(com.guilhermegoes.arraial.registry.ModItems.RAPADURA)
				+ e.carregando(com.guilhermegoes.arraial.registry.ModItems.FARINHA) > 0;
		e.esvaziarCarga();
		double chance = 0.55 + 0.25 * (a.satisfacao() - 50) / 50.0 + (presente ? 0.15 : 0) + antes / 300.0;
		chance = Math.max(0.15, Math.min(0.9, chance));
		BlockPos vila = a.getBlockPos();
		if (level.getRandom().nextDouble() < chance) {
			int ganho = 6 + level.getRandom().nextInt(9);
			Registro.mudarReputacao(meuId, destinoId, ganho);
			Cronica.anotar(level, vila, Component.translatable("arraial.cronica.missao_boa", e.nome(), d.nome, ganho));
			if (antes < ALIADO && antes + ganho >= ALIADO) {
				Component texto = Component.translatable("arraial.cronica.alianca", a.nomeCompleto(), d.nome);
				Cronica.anotar(level, vila, texto);
				Prefeitura.avisar(level, a, Component.literal("🤝 ").append(texto).withStyle(ChatFormatting.GREEN));
			}
		} else {
			int perda = 5 + level.getRandom().nextInt(9);
			Registro.mudarReputacao(meuId, destinoId, -perda);
			Cronica.anotar(level, vila, Component.translatable("arraial.cronica.missao_gafe", e.nome(), d.nome, perda));
			if (antes > INIMIGO && antes - perda <= INIMIGO) {
				Component texto = Component.translatable("arraial.cronica.inimizade", a.nomeCompleto(), d.nome);
				Cronica.anotar(level, vila, texto);
				Prefeitura.avisar(level, a, Component.literal("⚔ ").append(texto).withStyle(ChatFormatting.RED));
			}
		}
		BlockPos borda = Caravana.borda(level, vila, destinoId);
		e.teleportTo(borda.getX() + 0.5, borda.getY(), borda.getZ() + 0.5);
		e.setViagem(4, destinoId, 0);
	}

	/** Neighbours with a reputation at or beyond {@code limite} (allies: ≥, enemies: ≤). */
	public static List<Registro.Info> com(ServerLevel level, BlockPos vila, boolean aliados) {
		String meu = Caravana.id(level, vila);
		Registro.Info eu = Registro.get(meu);
		if (eu == null) {
			return List.of();
		}
		return Registro.todas().entrySet().stream().filter(e -> !e.getKey().equals(meu))
				.filter(e -> aliados ? eu.reputacaoCom(e.getKey()) >= ALIADO : eu.reputacaoCom(e.getKey()) <= INIMIGO)
				.map(Map.Entry::getValue).toList();
	}

	/** A crisis began: allies send food straight away. */
	static void socorro(ServerLevel level, ArraialBlockEntity a) {
		List<Registro.Info> aliados = com(level, a.getBlockPos(), true);
		if (aliados.isEmpty()) {
			return;
		}
		List<ArmazemBlockEntity> armazens = Vilas.armazens(level, a.getBlockPos());
		if (armazens.isEmpty()) {
			return;
		}
		Registro.Info aliado = aliados.get(level.getRandom().nextInt(aliados.size()));
		Vilas.depositar(armazens, Items.BREAD, 16);
		Cronica.anotar(level, a.getBlockPos(), Component.translatable("arraial.cronica.socorro", aliado.nome));
	}

	/** Enemies make bandit raids more likely. */
	static int peso(ServerLevel level, BlockPos vila) {
		return com(level, vila, false).isEmpty() ? 0 : 3;
	}

	/** Takes a small gift for the hosts, if the armazém can spare it. */
	public static void presente(ServerLevel level, MoradorEntity e) {
		List<ArmazemBlockEntity> armazens = Vilas.armazens(level, e.vila());
		int comida = Vilas.contar(armazens, s -> s.has(DataComponents.FOOD));
		if (comida > 24) {
			for (var s : Vilas.retirar(armazens, s -> s.is(Items.BREAD) || s.is(com.guilhermegoes.arraial.registry.ModItems.RAPADURA)
					|| s.is(com.guilhermegoes.arraial.registry.ModItems.FARINHA), 4)) {
				e.guardar(s.getItem(), s.getCount());
			}
		}
	}

	private Diplomacia() {
	}
}
