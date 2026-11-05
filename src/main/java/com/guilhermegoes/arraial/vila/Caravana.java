package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import com.guilhermegoes.arraial.trabalho.Demanda;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The tropeiro and his mule troop (GDD 6.1 and 7): trade between arraiais.
 * <ol>
 * <li>Some mornings the tropeiro picks a destination (the friendliest arraial known, then the nearest).</li>
 * <li>He loads the armazém's surplus and walks out of the arraial towards it.</li>
 * <li>Away (a day or more, by distance) the troop sells the load at the other arraial's prices and
 * buys what home is short of; the other arraial's armazém and coffers change too, even unloaded.</li>
 * <li>On the road the troop can be robbed (fewer soldiers at home, more risk).</li>
 * <li>Back home he unloads, and the Crônica tells the story.</li>
 * </ol>
 * When the other arraial is in a crisis, a friendly troop brings food as a gift instead, and
 * reputation between them grows a lot (it will be remembered).
 */
public final class Caravana {
	private static final long DIA = 24000L;
	private static final int CARGA_MAXIMA = 128;

	static String id(ServerLevel level, BlockPos pos) {
		return Registro.id(level.dimension().identifier().toString(), pos);
	}

	/** Morning: a tropeiro at home may set out. */
	static void diario(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores) {
		MoradorEntity t = moradores.stream().filter(m -> m.profissao() == Profissao.TROPEIRO && m.posto() != null
				&& m.viagemFase() == 0 && !m.comFome() && !m.doente()).findFirst().orElse(null);
		if (t == null || level.getRandom().nextFloat() > 0.6F) {
			return;
		}
		String meu = id(level, a.getBlockPos());
		String destino = destino(level, meu, a.getBlockPos());
		if (destino != null) {
			t.setViagem(1, destino, 0);
		}
	}

	/** For the /arraial tropa command: set out today, whatever the dice say. */
	public static void diarioForcado(ServerLevel level, ArraialBlockEntity a, MoradorEntity t) {
		String destino = destino(level, id(level, a.getBlockPos()), a.getBlockPos());
		if (destino != null) {
			t.setViagem(1, destino, 0);
		}
	}

	private static @Nullable String destino(ServerLevel level, String meu, BlockPos pos) {
		Registro.Info eu = Registro.get(meu);
		String dim = level.dimension().identifier().toString();
		String melhor = null;
		double nota = -Double.MAX_VALUE;
		for (Map.Entry<String, Registro.Info> e : Registro.todas().entrySet()) {
			Registro.Info v = e.getValue();
			if (e.getKey().equals(meu) || !v.dimensao.equals(dim)) {
				continue;
			}
			if (level.isLoaded(v.posicao()) && !(level.getBlockEntity(v.posicao()) instanceof ArraialBlockEntity)) {
				continue; // the Cruzeiro is gone
			}
			int rep = eu == null ? 0 : eu.reputacaoCom(e.getKey());
			if (rep < -30) {
				continue; // not welcome there
			}
			if (Economia.bloqueada(meu, e.getKey(), Vilas.dia(level))) {
				continue; // the road is closed (robbers or war)
			}
			double n = rep * 10 - Math.sqrt(v.posicao().distSqr(pos)) / 50 + (v.crise.isEmpty() ? 0 : 200);
			if (n > nota) {
				nota = n;
				melhor = e.getKey();
			}
		}
		return melhor;
	}

	/** The surplus worth taking: what the arraial has beyond its needs and a reserve. */
	public static Map<Item, Integer> carga(ServerLevel level, ArraialBlockEntity a, int moradores) {
		List<ArmazemBlockEntity> armazens = Vilas.armazens(level, a.getBlockPos());
		Map<Item, Integer> inv = Vilas.inventario(armazens);
		Demanda.Resultado demanda = Demanda.calcular(level, a.getBlockPos());
		Map<Item, Integer> carga = new LinkedHashMap<>();
		int total = 0;
		for (Feira.Mercadoria m : Feira.TABELA) {
			int reserva = m.item().components().has(DataComponents.FOOD) ? moradores * 4 : 16;
			int sobra = inv.getOrDefault(m.item(), 0) - demanda.reservado().getOrDefault(m.item(), 0) - reserva;
			if (sobra > 8 && total < CARGA_MAXIMA) {
				int leva = Math.min(Math.min(sobra / 2, 32), CARGA_MAXIMA - total);
				carga.put(m.item(), leva);
				total += leva;
			}
		}
		return carga;
	}

	/** Where the troop leaves (and comes back): the edge of the arraial towards the destination. */
	public static BlockPos borda(ServerLevel level, BlockPos vila, String destino) {
		Registro.Info d = Registro.get(destino);
		double dx = 1, dz = 0;
		if (d != null) {
			BlockPos p = d.posicao();
			double len = Math.max(1, Math.sqrt(p.distSqr(vila)));
			dx = (p.getX() - vila.getX()) / len;
			dz = (p.getZ() - vila.getZ()) / len;
		}
		BlockPos alvo = vila.offset((int) (dx * (Vilas.RAIO - 6)), 0, (int) (dz * (Vilas.RAIO - 6)));
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, alvo);
	}

	/** The tropeiro reached the edge of the arraial: the troop is on the road. */
	public static void partir(ServerLevel level, MoradorEntity t) {
		ArraialBlockEntity a = t.arraial();
		Registro.Info d = Registro.get(t.viagemDestino());
		if (a == null || d == null) {
			t.setViagem(0, "", 0);
			return;
		}
		double dist = Math.sqrt(d.posicao().distSqr(a.getBlockPos()));
		long duracao = (long) Math.max(DIA / 2, Math.min(3 * DIA, dist / 400.0 * DIA));
		if (t.profissao() == Profissao.TROPEIRO) {
			Cronica.anotar(level, a.getBlockPos(), Component.translatable("arraial.cronica.tropa_partiu", t.nome(), d.nome));
		}
		Comercio.partida(level, t);
		BlockPos casa = t.posto() != null ? t.posto() : a.getBlockPos();
		t.teleportTo(casa.getX() + 0.5, casa.getY() + 1, casa.getZ() + 0.5);
		t.setViagem(3, t.viagemDestino(), level.getGameTime() + duracao);
	}

	/** The troop is back: settle the trade (or the robbery) and show up at the edge of the arraial. */
	public static void voltar(ServerLevel level, MoradorEntity t) {
		ArraialBlockEntity a = t.arraial();
		String destinoId = t.viagemDestino();
		Registro.Info d = Registro.get(destinoId);
		if (a == null) {
			t.setViagem(0, "", 0);
			return;
		}
		BlockPos vila = a.getBlockPos();
		Map<Item, Integer> levou = t.cargaCopia();
		t.esvaziarCarga();
		String meuId = id(level, vila);
		long soldados = a.moradores(level).stream().filter(m -> m.profissao() == Profissao.SOLDADO).count();
		long hoje = Vilas.dia(level);
		float risco = Math.max(0.03F, Economia.risco(meuId, destinoId, hoje) + 0.08F - 0.04F * soldados);
		if (t.escoltada()) {
			risco /= 4; // a player rides with the troop
		}
		int antes = a.tesouro();
		boolean roubada = false;
		if (d == null) {
			levou.forEach(t::guardar);
		} else if (level.getRandom().nextFloat() < risco) {
			// Caravana assaltada: the load is lost on the road.
			Economia.roubo(meuId, destinoId, hoje);
			roubada = true;
			Component texto = Component.translatable("arraial.cronica.tropa_assaltada", t.nome(), d.nome);
			Cronica.anotar(level, vila, texto);
			Prefeitura.avisar(level, a, texto.copy().withStyle(ChatFormatting.RED));
			Satisfacao.agradar(level, vila, -5);
		} else if (!d.crise.isEmpty() && Registro.get(meuId) != null && Registro.get(meuId).reputacaoCom(destinoId) >= 0
				&& levou.keySet().stream().anyMatch(i -> i.components().has(DataComponents.FOOD))) {
			// A friend in trouble: the food goes as a gift.
			Map<Item, Integer> doacao = new LinkedHashMap<>();
			levou.forEach((i, n) -> {
				if (i.components().has(DataComponents.FOOD)) {
					doacao.put(i, n);
				} else {
					t.guardar(i, n);
				}
			});
			entregar(level, destinoId, d, doacao, 0);
			Registro.mudarReputacao(meuId, destinoId, 15);
			Cronica.anotar(level, vila, Component.translatable("arraial.cronica.tropa_ajuda", t.nome(), d.nome));
		} else {
			Economia.viagem(meuId, destinoId);
			negociar(level, a, t, meuId, destinoId, d, levou);
		}
		Comercio.pagarEscolta(level, a, t, Math.max(0, a.tesouro() - antes), roubada);
		BlockPos borda = borda(level, vila, destinoId);
		t.teleportTo(borda.getX() + 0.5, borda.getY(), borda.getZ() + 0.5);
		t.setViagem(4, destinoId, 0);
	}

	private static void negociar(ServerLevel level, ArraialBlockEntity a, MoradorEntity t, String meuId, String destinoId,
			Registro.Info d, Map<Item, Integer> levou) {
		int rep = Registro.get(meuId) == null ? 0 : Registro.get(meuId).reputacaoCom(destinoId);
		double fator = 1.0 + rep / 500.0;
		// Sell.
		double valor = 0;
		for (Map.Entry<Item, Integer> e : levou.entrySet()) {
			valor += e.getValue() * d.precos.getOrDefault(Feira.id(e.getKey()), Math.max(0.5, Feira.base(e.getKey()))) * fator;
		}
		int pago = (int) Math.min(Math.floor(valor), d.tesouro);
		entregar(level, destinoId, d, levou, -pago);
		a.mudarTesouro(pago);
		// Buy what home is short of, if they have plenty.
		Demanda.Resultado demanda = Demanda.calcular(level, a.getBlockPos());
		Map<Item, Integer> comprou = new LinkedHashMap<>();
		int verba = a.tesouro() / 2;
		for (Map.Entry<Item, Integer> pedido : demanda.pedidos()) {
			String id = Feira.id(pedido.getKey());
			int tem = d.estoque.getOrDefault(id, 0);
			double preco = d.precos.getOrDefault(id, Math.max(0.5, Feira.base(pedido.getKey()))) / fator;
			int qtd = Math.min(Math.min(pedido.getValue(), 32), tem / 2);
			while (qtd > 0 && preco * qtd > verba) {
				qtd--;
			}
			if (qtd <= 0) {
				continue;
			}
			int custo = (int) Math.ceil(preco * qtd);
			verba -= custo;
			a.mudarTesouro(-custo);
			comprou.merge(pedido.getKey(), qtd, Integer::sum);
			d.estoque.merge(id, -qtd, Integer::sum);
			if (comprou.size() >= 4) {
				break;
			}
		}
		Map<Item, Integer> retirar = new LinkedHashMap<>();
		comprou.forEach((i, n) -> retirar.put(i, -n));
		int gasto = comprou.entrySet().stream().mapToInt(e -> (int) Math.ceil(
				d.precos.getOrDefault(Feira.id(e.getKey()), Math.max(0.5, Feira.base(e.getKey()))) / fator * e.getValue())).sum();
		entregar(level, destinoId, d, retirar, gasto);
		comprou.forEach(t::guardar);
		Registro.mudarReputacao(meuId, destinoId, 3);
		int itens = comprou.values().stream().mapToInt(Integer::intValue).sum();
		Cronica.anotar(level, a.getBlockPos(), Component.translatable("arraial.cronica.tropa_voltou", t.nome(), d.nome, pago, itens));
	}

	/**
	 * Goods (negative = taken) and money for the other arraial: straight into its armazém and
	 * coffers when it is loaded, otherwise kept in the registry until it wakes up.
	 */
	static void entregar(ServerLevel level, String id, Registro.Info d, Map<Item, Integer> itens, int dinheiro) {
		BlockPos pos = d.posicao();
		boolean carregada = d.dimensao.equals(level.dimension().identifier().toString()) && level.isLoaded(pos)
				&& level.getBlockEntity(pos) instanceof ArraialBlockEntity;
		if (carregada && level.getBlockEntity(pos) instanceof ArraialBlockEntity outra) {
			List<ArmazemBlockEntity> armazens = Vilas.armazens(level, pos);
			itens.forEach((i, n) -> {
				if (n > 0) {
					Vilas.depositar(armazens, i, n);
				} else {
					Vilas.retirar(armazens, i, -n);
				}
			});
			outra.mudarTesouro(dinheiro);
		} else {
			itens.forEach((i, n) -> Mundo.somar(d, Feira.id(i), n));
			d.tesouroPendente += dinheiro;
			d.tesouro = Math.max(0, d.tesouro + dinheiro);
		}
	}

	private Caravana() {
	}
}
