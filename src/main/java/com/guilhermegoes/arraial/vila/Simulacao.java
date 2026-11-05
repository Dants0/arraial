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

		// The days we missed.
		long agora = level.getGameTime();
		if (a.ultimoTick > 0 && agora - a.ultimoTick > DIA && !armazens.isEmpty()) {
			int dias = (int) Math.min(MAX_DIAS, (agora - a.ultimoTick) / DIA);
			int roceiros = conta(moradores, Profissao.ROCEIRO), farinheiros = conta(moradores, Profissao.FARINHEIRO);
			int pastores = conta(moradores, Profissao.PASTOR), lenhadores = conta(moradores, Profissao.LENHADOR);
			int comida = dias * (roceiros * 2 + farinheiros * 4 + pastores);
			int comeram = dias * moradores.size();
			int saldo = comida - comeram;
			if (saldo > 0) {
				Vilas.depositar(armazens, farinheiros > 0 ? ModItems.FARINHA : Items.BREAD, saldo);
			} else if (saldo < 0) {
				Vilas.retirar(armazens, s -> s.has(DataComponents.FOOD), -saldo);
			}
			if (lenhadores > 0) {
				Item tora = Estoque.madeiraDaVila(Vilas.inventario(armazens)).tora();
				Vilas.depositar(armazens, tora, dias * lenhadores * 8);
			}
			Cronica.anotar(level, a.getBlockPos(), Component.translatable("arraial.cronica.longe", dias, comida, comeram));
		}

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
