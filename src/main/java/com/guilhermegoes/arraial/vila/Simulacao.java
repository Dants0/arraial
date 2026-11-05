package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import com.guilhermegoes.arraial.registry.ModItems;
import com.guilhermegoes.arraial.trabalho.Estoque;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Simulation at a distance (GDD 15.3, level C). An arraial only ticks while its chunks are loaded.
 * When it wakes up after a long sleep, the days it missed are settled in one go from its trades
 * (the roça and the casa de farinha fed people, the lenhador cut wood, everyone ate), and the
 * world registry gets a fresh snapshot of it for the tropeiros of other villages. Goods and
 * money other villages sent while it slept are delivered too.
 */
public final class Simulacao {
	private static final long DIA = 24000L;
	private static final int MAX_DIAS = 10;

	static void recuperar(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores) {
		String id = Registro.id(level.dimension().identifier().toString(), a.getBlockPos());
		Registro.Info info = Registro.obter(id);
		List<ArmazemBlockEntity> armazens = Vilas.armazens(level, a.getBlockPos());

		// What other villages sent while we slept.
		if (!armazens.isEmpty() && (!info.pendentes.isEmpty() || info.tesouroPendente != 0)) {
			info.pendentes.forEach((item, n) -> item(item).ifPresent(i -> {
				if (n > 0) {
					Vilas.depositar(armazens, i, n);
				} else if (n < 0) {
					Vilas.retirar(armazens, i, -n);
				}
			}));
			info.pendentes.clear();
			a.tesouro = Math.max(0, a.tesouro + info.tesouroPendente);
			info.tesouroPendente = 0;
			a.setChanged();
		}

		// People who came or went while we slept (see Mundo), the history, and abandonment.
		if (info.abandonada && !a.abandonada) {
			a.abandonar(level, moradores);
			moradores = List.of();
		} else if (!info.abandonada && a.abandonada && info.saldoMoradores > 0) {
			// Resettled by a neighbour while nobody was looking.
			a.abandonada = false;
			Registro.Info colono = Registro.get(info.colonia);
			a.anotar(new Cronica.Entrada(Vilas.dia(level), Component.translatable("arraial.cronica.recolonizada", colono == null ? "?" : colono.nome)));
		}
		if (info.saldoMoradores > 0) {
			for (int i = 0; i < Math.min(6, info.saldoMoradores); i++) {
				a.receberMigrante(level, "", "");
			}
		} else if (info.saldoMoradores < 0) {
			moradores.stream().filter(m -> !m.prefeito()).limit(-info.saldoMoradores).toList().forEach(m -> m.irEmboraDaVila(level));
		}
		info.saldoMoradores = 0;
		for (Registro.Evento e : info.historiaPendente) {
			a.anotar(new Cronica.Entrada(e.dia, e.texto()));
		}
		info.historiaPendente.clear();

		// Fresh snapshot for the world.
		info.pos = a.getBlockPos().asLong();
		info.dimensao = level.dimension().identifier().toString();
		info.nome = a.nomeCompleto();
		info.populacao = moradores.size();
		info.tesouro = a.tesouro;
		info.crise = a.crise.tipo() == null ? "" : a.crise.tipo().id();
		info.estagio = a.estagio.ordinal();
		info.autonoma = a.autonoma;
		info.dia = Vilas.dia(level);
		info.estoque.clear();
		for (Map.Entry<Item, Integer> e : Vilas.inventario(armazens).entrySet()) {
			info.estoque.put(BuiltInRegistries.ITEM.getKey(e.getKey()).toString(), e.getValue());
		}
		info.precos.clear();
		info.precos.putAll(a.precos);
		info.oficios.clear();
		for (MoradorEntity m : moradores) {
			if (m.profissao() != Profissao.NENHUMA) {
				info.oficios.merge(m.profissao().id(), 1, Integer::sum);
			}
		}
		info.camas = (int) a.camas(level);
		info.satisfacao = a.satisfacao;
		info.especialidade = Economia.especialidade(info.oficios).id();
		info.abandonada = a.abandonada;
		Cultura.garantir(level, a, info);
		if (!a.abandonada) {
			info.fome = 0;
			info.criseLonge = "";
		}
	}

	private static int conta(List<MoradorEntity> moradores, Profissao p) {
		return (int) moradores.stream().filter(m -> m.profissao() == p).count();
	}

	static java.util.Optional<Item> item(String id) {
		Identifier i = Identifier.tryParse(id);
		return i == null ? java.util.Optional.empty() : BuiltInRegistries.ITEM.getOptional(i);
	}

	private Simulacao() {
	}
}
