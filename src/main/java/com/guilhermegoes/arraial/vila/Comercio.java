package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import com.guilhermegoes.arraial.registry.ModItems;
import com.guilhermegoes.arraial.trabalho.Demanda;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Trade beyond the feira:
 * <ul>
 * <li><b>Mascate</b>: some mornings a peddler from a neighbouring arraial walks in with real goods
 * from its storehouse (its surplus), stays until dusk and sells to the player; he also buys what
 * his home lacks. Unsold goods and the money go back home with him.</li>
 * <li><b>Escort</b>: a player with a sword in hand can join a tropeiro about to leave; if they are
 * there when the troop sets out, the risk of robbery falls to a quarter and the escort is paid.</li>
 * <li><b>Encomendas</b>: at the banca, paper in hand, the arraial offers a supply order (what it
 * lacks most, a quantity, a deadline and a premium). Deliveries are made by selling that good at
 * the banca; done in time, it pays the premium and reputation; late, the reputation suffers.</li>
 * <li><b>Contratos entre vilas</b>: an arraial short of food, stone or wood signs a supply
 * contract with a neighbour that has plenty (preferably of that specialty). Every day the goods
 * go by road; a closed road breaks the contract. A farming village can grow rich feeding a
 * mining one, the player's own arraial included (straight from its armazém).</li>
 * </ul>
 */
public final class Comercio {
	private static final long DIA = 24000L;
	private static final int LOTE = 8;

	// ================================================================ the mascate

	static void diario(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores) {
		verificarEncomendas(level, a);
		if (level.getRandom().nextFloat() < 0.3F) {
			mascate(level, a, moradores);
		}
	}

	/** A mascate from the neighbour with most to sell (if none is here already). */
	public static void mascate(ServerLevel level, ArraialBlockEntity a, List<MoradorEntity> moradores) {
		if (moradores.size() < 2 || a.abandonada) {
			return;
		}
		BlockPos vila = a.getBlockPos();
		if (!level.getEntitiesOfClass(MoradorEntity.class, new AABB(vila).inflate(Vilas.RAIO * 2),
				m -> m.profissao() == Profissao.MASCATE).isEmpty()) {
			return;
		}
		String meu = Economia.id(level, vila);
		long hoje = Vilas.dia(level);
		String origem = null;
		double nota = -Double.MAX_VALUE;
		for (Map.Entry<String, Registro.Info> e : Registro.todas().entrySet()) {
			Registro.Info v = e.getValue();
			if (e.getKey().equals(meu) || v.abandonada || v.populacao < 2 || !v.dimensao.equals(level.dimension().identifier().toString())
					|| v.posicao().distSqr(vila) > (double) Economia.ALCANCE * Economia.ALCANCE
					|| Economia.bloqueada(meu, e.getKey(), hoje) || v.reputacaoCom(meu) < -30 || Mundo.carregada(level, v)) {
				continue;
			}
			double n = v.estoque.values().stream().mapToInt(Integer::intValue).sum() / 50.0 - Math.sqrt(v.posicao().distSqr(vila)) / 100
					+ level.getRandom().nextInt(5);
			if (n > nota) {
				nota = n;
				origem = e.getKey();
			}
		}
		if (origem == null) {
			return;
		}
		Registro.Info v = Registro.get(origem);
		Map<Item, Integer> carga = new LinkedHashMap<>();
		for (Feira.Mercadoria m : Feira.TABELA) {
			String id = Feira.id(m.item());
			int reserva = m.item().components().has(DataComponents.FOOD) ? v.populacao * 4 : 16;
			int sobra = v.estoque.getOrDefault(id, 0) - reserva;
			if (sobra >= 8 && carga.size() < 5) {
				int leva = Math.min(24, sobra / 2);
				carga.put(m.item(), leva);
				Mundo.somar(v, id, -leva);
			}
		}
		if (carga.isEmpty()) {
			return;
		}
		int bolsa = Math.min(40, v.tesouro / 3);
		v.tesouro -= bolsa;
		v.tesouroPendente -= bolsa;
		BlockPos borda = Caravana.borda(level, vila, origem);
		MoradorEntity m = MoradorEntity.criar(level, borda, Profissao.NENHUMA);
		if (m == null) {
			carga.forEach((i, n) -> Mundo.somar(v, Feira.id(i), n));
			return;
		}
		m.virarMascate(origem, vila, level.getGameTime() + DIA / 2, bolsa);
		carga.forEach(m::guardar);
		MutableComponent lista = Component.empty();
		int i = 0;
		for (Item it : carga.keySet()) {
			lista.append(Component.literal(i++ == 0 ? "" : ", ")).append(it.getDefaultInstance().getHoverName());
		}
		Prefeitura.avisar(level, a, Component.literal("🧺 ").append(Component.translatable("arraial.comercio.mascate_chegou", m.nome(), v.nome, lista))
				.withStyle(ChatFormatting.AQUA));
		Cronica.anotar(level, vila, Component.translatable("arraial.cronica.mascate", m.nome(), v.nome));
	}

	public static String origemNome(MoradorEntity m) {
		Registro.Info v = Registro.get(m.mascateOrigem());
		return v == null ? "?" : v.nome;
	}

	/** The mascate's price: his home's price plus the road (distance and risk). */
	private static double preco(ServerLevel level, MoradorEntity m, Item item) {
		Registro.Info v = Registro.get(m.mascateOrigem());
		double base = Math.max(0.5, Feira.base(item));
		double la = v == null ? base : v.precos.getOrDefault(Feira.id(item), base);
		double dist = v == null || m.mascateVila() == null ? 300 : Math.sqrt(v.posicao().distSqr(m.mascateVila()));
		return Math.round(la * (1.15 + dist / 3000.0) * 10) / 10.0;
	}

	private static final Map<UUID, Integer> ESCOLHA = new HashMap<>();

	/** A player talks business with the mascate. */
	public static void negociar(ServerLevel level, MoradorEntity m, Player p, ItemStack mao) {
		List<Map.Entry<Item, Integer>> bens = new ArrayList<>(m.cargaCopia().entrySet());
		bens.removeIf(e -> e.getValue() <= 0);
		int escolha = bens.isEmpty() ? 0 : Math.floorMod(ESCOLHA.getOrDefault(p.getUUID(), 0), bens.size());
		if (mao.isEmpty() && p.isShiftKeyDown() && !bens.isEmpty()) {
			escolha = (escolha + 1) % bens.size();
			ESCOLHA.put(p.getUUID(), escolha);
			Item it = bens.get(escolha).getKey();
			p.sendOverlayMessage(Component.translatable("arraial.feira.escolhida", it.getDefaultInstance().getHoverName(),
					String.format(java.util.Locale.ROOT, "%.1f", preco(level, m, it)), bens.get(escolha).getValue()).withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (mao.is(ModItems.REIS) && !bens.isEmpty()) {
			Map.Entry<Item, Integer> e = bens.get(escolha);
			double unidade = preco(level, m, e.getKey());
			int qtd = Math.min(LOTE, e.getValue());
			while (qtd > 0 && Math.ceil(unidade * qtd) > mao.getCount()) {
				qtd--;
			}
			if (qtd == 0) {
				p.sendOverlayMessage(Component.translatable("arraial.comercio.sem_reis").withStyle(ChatFormatting.RED));
				return;
			}
			int custo = (int) Math.ceil(unidade * qtd);
			mao.shrink(custo);
			m.gastar(e.getKey(), qtd);
			m.mudarBolsa(custo);
			ItemStack compra = new ItemStack(e.getKey(), qtd);
			if (!p.getInventory().add(compra)) {
				net.minecraft.world.level.block.Block.popResource(level, p.blockPosition(), compra);
			}
			level.playSound(null, m.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.7F, 1.1F);
			p.sendSystemMessage(Component.translatable("arraial.feira.comprou", qtd, e.getKey().getDefaultInstance().getHoverName(), custo)
					.withStyle(ChatFormatting.GREEN));
			return;
		}
		if (!mao.isEmpty() && Feira.base(mao.getItem()) > 0) {
			// He buys what his home lacks.
			Registro.Info v = Registro.get(m.mascateOrigem());
			String id = Feira.id(mao.getItem());
			if (v == null || v.estoque.getOrDefault(id, 0) >= 24) {
				m.dizer(Component.translatable("arraial.fala.mascate_nao_quer"), 60);
				return;
			}
			double unidade = v.precos.getOrDefault(id, Feira.base(mao.getItem())) * 0.85;
			int qtd = Math.min(16, mao.getCount());
			while (qtd > 0 && Math.floor(unidade * qtd) > m.bolsa()) {
				qtd--;
			}
			int pago = (int) Math.floor(unidade * qtd);
			if (qtd == 0 || pago == 0) {
				m.dizer(Component.translatable("arraial.fala.mascate_sem_dinheiro"), 60);
				return;
			}
			m.guardar(mao.getItem(), qtd);
			mao.shrink(qtd);
			m.mudarBolsa(-pago);
			ItemStack reis = new ItemStack(ModItems.REIS, pago);
			if (!p.getInventory().add(reis)) {
				net.minecraft.world.level.block.Block.popResource(level, p.blockPosition(), reis);
			}
			level.playSound(null, m.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.7F, 1.0F);
			p.sendSystemMessage(Component.translatable("arraial.comercio.mascate_comprou", m.nome(), qtd, pago).withStyle(ChatFormatting.GREEN));
			return;
		}
		// The list.
		p.sendSystemMessage(Component.literal("🧺 ").append(Component.translatable("arraial.comercio.mascate_titulo", m.nome(), origemNome(m), m.bolsa()))
				.withStyle(ChatFormatting.GOLD));
		for (int i = 0; i < bens.size(); i++) {
			Map.Entry<Item, Integer> e = bens.get(i);
			p.sendSystemMessage(Component.literal(i == escolha ? " ▶ " : " • ").append(e.getKey().getDefaultInstance().getHoverName())
					.append(Component.literal(" — " + String.format(java.util.Locale.ROOT, "%.1f", preco(level, m, e.getKey())) + " réis (" + e.getValue() + ")"))
					.withStyle(i == escolha ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
		}
		p.sendSystemMessage(Component.translatable("arraial.comercio.mascate_dica").withStyle(ChatFormatting.GRAY));
	}

	/** Dusk: the mascate leaves with what is left and the money, back to his arraial. */
	public static void irEmbora(ServerLevel level, MoradorEntity m) {
		Registro.Info v = Registro.get(m.mascateOrigem());
		if (v != null) {
			Map<Item, Integer> sobra = new LinkedHashMap<>(m.cargaCopia());
			Caravana.entregar(level, m.mascateOrigem(), v, sobra, m.bolsa());
			if (m.mascateVila() != null) {
				Registro.mudarReputacao(m.mascateOrigem(), Economia.id(level, m.mascateVila()), 2);
				Economia.viagem(m.mascateOrigem(), Economia.id(level, m.mascateVila()));
			}
		}
		m.esvaziarCarga();
		m.discard();
	}

	// ================================================================ escort

	/** The troop sets out: is the escort there? */
	static void partida(ServerLevel level, MoradorEntity t) {
		UUID j = t.escolta();
		if (j == null) {
			return;
		}
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(j);
		boolean presente = p != null && p.level() == level && p.distanceToSqr(t) < 40 * 40;
		t.setEscolta(presente ? j : null, presente);
		if (presente) {
			p.sendSystemMessage(Component.translatable("arraial.comercio.escolta_partiu", t.nome()).withStyle(ChatFormatting.GOLD));
		}
	}

	/** Back home: pay the escort (kept at the Cruzeiro until they come for it). */
	static void pagarEscolta(ServerLevel level, ArraialBlockEntity a, MoradorEntity t, int lucro, boolean roubada) {
		UUID j = t.escolta();
		if (j == null || !t.escoltada()) {
			t.setEscolta(null, false);
			return;
		}
		t.setEscolta(null, false);
		if (roubada) {
			return;
		}
		int paga = Math.max(3, lucro / 10);
		a.pagamentos.merge(j, paga, Integer::sum);
		a.mudarReputacao(j, 5);
		a.setChanged();
		Cronica.anotar(level, a.getBlockPos(), Component.translatable("arraial.cronica.escolta", t.nome(), nomeJogador(level, j), paga));
	}

	/** Money owed to a player (escorts, encomendas without cash at the time), paid at the Cruzeiro. */
	static void receber(ServerLevel level, ArraialBlockEntity a, Player p) {
		Integer v = a.pagamentos.remove(p.getUUID());
		if (v == null || v <= 0) {
			return;
		}
		ItemStack reis = new ItemStack(ModItems.REIS, v);
		if (!p.getInventory().add(reis)) {
			net.minecraft.world.level.block.Block.popResource(level, p.blockPosition(), reis);
		}
		a.setChanged();
		p.sendSystemMessage(Component.translatable("arraial.comercio.recebeu", v).withStyle(ChatFormatting.GOLD));
	}

	private static String nomeJogador(ServerLevel level, UUID j) {
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(j);
		return p == null ? "?" : p.getName().getString();
	}

	// ================================================================ encomendas (orders to the player)

	/** One player order: item, how many, how many already delivered, deadline day, price. */
	record Encomenda(String item, int qtd, int entregue, long prazo, int valor) {
		static @Nullable Encomenda de(String s) {
			String[] p = s.split(";");
			if (p.length != 5) {
				return null;
			}
			try {
				return new Encomenda(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Long.parseLong(p[3]), Integer.parseInt(p[4]));
			} catch (NumberFormatException e) {
				return null;
			}
		}

		String texto() {
			return item + ";" + qtd + ";" + entregue + ";" + prazo + ";" + valor;
		}

		Component nomeItem() {
			return Simulacao.item(item).map(i -> i.getDefaultInstance().getHoverName()).orElse(Component.literal(item));
		}
	}

	private static final Map<UUID, Encomenda> OFERTAS = new HashMap<>();

	/** Paper in hand at the banca: see the offer, sign it, or check the one in course. */
	static void encomenda(ServerLevel level, ArraialBlockEntity a, Player p, ItemStack mao) {
		long hoje = Vilas.dia(level);
		Encomenda atual = Encomenda.de(a.encomendas.getOrDefault(p.getUUID(), ""));
		if (atual != null) {
			p.sendSystemMessage(Component.translatable("arraial.comercio.encomenda_andamento", atual.entregue(), atual.qtd(), atual.nomeItem(),
					Math.max(0, atual.prazo() - hoje), atual.valor()).withStyle(ChatFormatting.AQUA));
			return;
		}
		Encomenda oferta = OFERTAS.get(p.getUUID());
		if (oferta != null && oferta.prazo() > hoje) {
			mao.shrink(1);
			a.encomendas.put(p.getUUID(), oferta.texto());
			OFERTAS.remove(p.getUUID());
			a.setChanged();
			level.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.0F);
			p.sendSystemMessage(Component.translatable("arraial.comercio.encomenda_assinada", oferta.qtd(), oferta.nomeItem(), oferta.prazo() - hoje,
					oferta.valor()).withStyle(ChatFormatting.GOLD));
			return;
		}
		// What the arraial needs most, at a premium.
		List<ArmazemBlockEntity> armazens = Vilas.armazens(level, a.getBlockPos());
		Map<Item, Integer> inv = Vilas.inventario(armazens);
		Item quer = null;
		for (Map.Entry<Item, Integer> e : Demanda.calcular(level, a.getBlockPos()).pedidos()) {
			if (Feira.base(e.getKey()) > 0) {
				quer = e.getKey();
				break;
			}
		}
		if (quer == null) {
			int n = Math.max(1, a.populacao);
			if (Vilas.contar(armazens, s -> s.has(DataComponents.FOOD)) < n * 4) {
				quer = Items.BREAD;
			} else {
				quer = Feira.TABELA.stream().map(Feira.Mercadoria::item).min(java.util.Comparator.comparingInt(i -> inv.getOrDefault(i, 0)))
						.orElse(Items.BREAD);
			}
		}
		int qtd = quer.components().has(DataComponents.FOOD) ? 32 : 24;
		int valor = (int) Math.ceil(Math.max(0.5, Feira.preco(a, quer)) * qtd * 1.4);
		Encomenda nova = new Encomenda(Feira.id(quer), qtd, 0, hoje + 4, valor);
		OFERTAS.put(p.getUUID(), nova);
		p.sendSystemMessage(Component.translatable("arraial.comercio.encomenda_oferta", a.nomeCompleto(), qtd, nova.nomeItem(), 4, valor)
				.withStyle(ChatFormatting.YELLOW));
	}

	/** Selling the ordered good at the banca counts toward the order. Returns true if it did. */
	static boolean entregarEncomenda(ServerLevel level, ArraialBlockEntity a, Player p, ItemStack mao, List<ArmazemBlockEntity> armazens) {
		Encomenda e = Encomenda.de(a.encomendas.getOrDefault(p.getUUID(), ""));
		if (e == null || !Feira.id(mao.getItem()).equals(e.item())) {
			return false;
		}
		int falta = e.qtd() - e.entregue();
		int da = Math.min(falta, mao.getCount());
		int sobrou = Vilas.depositar(armazens, mao.getItem(), da);
		da -= sobrou;
		if (da <= 0) {
			return false;
		}
		mao.shrink(da);
		e = new Encomenda(e.item(), e.qtd(), e.entregue() + da, e.prazo(), e.valor());
		if (e.entregue() >= e.qtd()) {
			a.encomendas.remove(p.getUUID());
			int pago = Math.min(e.valor(), a.tesouro);
			a.mudarTesouro(-pago);
			if (pago < e.valor()) {
				a.pagamentos.merge(p.getUUID(), e.valor() - pago, Integer::sum); // the rest when there is money
			}
			ItemStack reis = new ItemStack(ModItems.REIS, pago);
			if (pago > 0 && !p.getInventory().add(reis)) {
				net.minecraft.world.level.block.Block.popResource(level, p.blockPosition(), reis);
			}
			a.mudarReputacao(p.getUUID(), 8);
			Cronica.anotar(level, a.getBlockPos(), Component.translatable("arraial.cronica.encomenda_cumprida", p.getName().getString(), e.qtd(), e.nomeItem()));
			p.sendSystemMessage(Component.translatable("arraial.comercio.encomenda_cumprida", e.valor()).withStyle(ChatFormatting.GOLD));
		} else {
			a.encomendas.put(p.getUUID(), e.texto());
			p.sendSystemMessage(Component.translatable("arraial.comercio.encomenda_entregou", da, e.entregue(), e.qtd(), e.nomeItem())
					.withStyle(ChatFormatting.GREEN));
		}
		a.setChanged();
		level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.7F, 1.0F);
		return true;
	}

	private static void verificarEncomendas(ServerLevel level, ArraialBlockEntity a) {
		long hoje = Vilas.dia(level);
		a.encomendas.entrySet().removeIf(en -> {
			Encomenda e = Encomenda.de(en.getValue());
			if (e == null) {
				return true;
			}
			if (hoje > e.prazo()) {
				a.mudarReputacao(en.getKey(), -10);
				Cronica.anotar(level, a.getBlockPos(), Component.translatable("arraial.cronica.encomenda_falhou", nomeJogador(level, en.getKey()), e.nomeItem()));
				return true;
			}
			return false;
		});
	}

	// ================================================================ contracts between arraiais

	private static long ultimoDia = -1;

	/** Once a day for the whole region: sign new contracts and carry out the running ones. */
	static void diaRegional(ServerLevel level) {
		long hoje = Vilas.dia(level);
		if (ultimoDia < 0 || hoje < ultimoDia) {
			ultimoDia = hoje;
			return;
		}
		if (hoje == ultimoDia) {
			return;
		}
		ultimoDia = hoje;
		String dim = level.dimension().identifier().toString();
		for (Map.Entry<String, Registro.Info> e : new ArrayList<>(Registro.todas().entrySet())) {
			Registro.Info v = e.getValue();
			if (!v.dimensao.equals(dim) || v.abandonada) {
				continue;
			}
			executar(level, e.getKey(), v, hoje);
			assinar(level, e.getKey(), v, hoje);
		}
		Territorio.diario(level, hoje);
		Cultura.intercambio(level, hoje);
	}

	private static String categoria(String item) {
		return Simulacao.item(item).map(i -> i.components().has(DataComponents.FOOD)).orElse(false) ? "comida"
				: item.endsWith("_log") ? "madeira" : item.equals("minecraft:cobblestone") ? "pedra" : item;
	}

	private static void assinar(ServerLevel level, String id, Registro.Info v, long hoje) {
		if (v.populacao < 2 || v.contratos.size() >= 2) {
			return;
		}
		List<String> precisa = new ArrayList<>();
		if (Mundo.comida(v) < v.populacao * 2) {
			precisa.add("comida");
		}
		if (v.estoque.getOrDefault("minecraft:cobblestone", 0) < 16) {
			precisa.add("pedra");
		}
		if (v.estoque.entrySet().stream().filter(x -> x.getKey().endsWith("_log")).mapToInt(Map.Entry::getValue).sum() < 16) {
			precisa.add("madeira");
		}
		for (String cat : precisa) {
			if (v.contratos.stream().anyMatch(c -> categoria(c.item).equals(cat))) {
				continue;
			}
			String melhor = null, item = null;
			double nota = -Double.MAX_VALUE;
			for (Map.Entry<String, Registro.Info> o : Registro.todas().entrySet()) {
				Registro.Info f = o.getValue();
				if (o.getKey().equals(id) || f.abandonada || !f.dimensao.equals(v.dimensao) || f.populacao < 2
						|| f.posicao().distSqr(v.posicao()) > (double) Economia.ALCANCE * Economia.ALCANCE
						|| Economia.bloqueada(id, o.getKey(), hoje) || v.reputacaoCom(o.getKey()) < 0) {
					continue;
				}
				String oferta = melhorItem(f, cat);
				int tem = oferta == null ? 0 : f.estoque.getOrDefault(oferta, 0);
				int necessario = cat.equals("comida") ? f.populacao * 4 + 40 : 64;
				if (oferta == null || tem < necessario) {
					continue;
				}
				Economia.Especialidade esp = Economia.Especialidade.de(f.especialidade);
				boolean especialista = cat.equals("comida") && esp == Economia.Especialidade.AGRICOLA
						|| cat.equals("pedra") && esp == Economia.Especialidade.MINERADORA
						|| cat.equals("madeira") && esp == Economia.Especialidade.MADEIREIRA;
				double n = tem / 20.0 + (especialista ? 10 : 0) + v.reputacaoCom(o.getKey()) / 10.0 - Math.sqrt(f.posicao().distSqr(v.posicao())) / 100;
				if (n > nota) {
					nota = n;
					melhor = o.getKey();
					item = oferta;
				}
			}
			if (melhor == null) {
				continue;
			}
			Registro.Info f = Registro.get(melhor);
			Registro.Contrato c = new Registro.Contrato();
			c.fornecedor = melhor;
			c.item = item;
			c.qtd = cat.equals("comida") ? Math.min(10, Math.max(3, v.populacao)) : 8;
			c.preco = Math.max(0.5, f.precos.getOrDefault(item, 1.0)) * 1.1;
			c.ate = hoje + 8;
			v.contratos.add(c);
			String qual = Simulacao.item(item).map(i -> i.getDefaultInstance().getHoverName().getString()).orElse(item);
			Mundo.historia(id, hoje, "arraial.historia.contrato", f.nome, String.valueOf(c.qtd), qual);
			Mundo.historia(melhor, hoje, "arraial.historia.contrato_fornece", v.nome, String.valueOf(c.qtd), qual);
		}
	}

	private static @Nullable String melhorItem(Registro.Info f, String cat) {
		String melhor = null;
		int max = 0;
		for (Map.Entry<String, Integer> e : f.estoque.entrySet()) {
			if (categoria(e.getKey()).equals(cat) && e.getValue() > max) {
				max = e.getValue();
				melhor = e.getKey();
			}
		}
		return melhor;
	}

	private static void executar(ServerLevel level, String id, Registro.Info v, long hoje) {
		v.contratos.removeIf(c -> {
			Registro.Info f = Registro.get(c.fornecedor);
			String qual = Simulacao.item(c.item).map(i -> i.getDefaultInstance().getHoverName().getString()).orElse(c.item);
			if (f == null || f.abandonada) {
				return true;
			}
			if (Economia.bloqueada(id, c.fornecedor, hoje)) {
				Registro.mudarReputacao(id, c.fornecedor, -6);
				Mundo.historia(id, hoje, "arraial.historia.contrato_rompido", f.nome, qual);
				Mundo.historia(c.fornecedor, hoje, "arraial.historia.contrato_rompido", v.nome, qual);
				return true;
			}
			if (hoje > c.ate) {
				Registro.mudarReputacao(id, c.fornecedor, 4);
				Mundo.historia(id, hoje, "arraial.historia.contrato_fim", f.nome, qual);
				return true;
			}
			Item item = Simulacao.item(c.item).orElse(null);
			if (item == null) {
				return true;
			}
			// The supplier sends it: straight from the armazém when the supplier is loaded.
			int enviou;
			if (Mundo.carregada(level, f)) {
				enviou = Vilas.retirar(Vilas.armazens(level, f.posicao()), item, c.qtd);
			} else {
				enviou = Math.min(c.qtd, f.estoque.getOrDefault(c.item, 0));
				Mundo.somar(f, c.item, -enviou);
			}
			if (enviou <= 0) {
				c.falhas++;
				return c.falhas >= 3;
			}
			if (level.getRandom().nextFloat() < Economia.risco(id, c.fornecedor, hoje) / 3) {
				Economia.roubo(id, c.fornecedor, hoje);
				Mundo.historia(id, hoje, "arraial.historia.mercador_roubado_vinha", f.nome, qual);
				return false;
			}
			int valor = (int) Math.round(enviou * c.preco);
			int pago = Math.min(valor, Math.max(0, v.tesouro));
			Caravana.entregar(level, id, v, Map.of(item, enviou), -pago);
			if (Mundo.carregada(level, f) && level.getBlockEntity(f.posicao()) instanceof ArraialBlockEntity fa) {
				fa.mudarTesouro(pago);
			} else {
				f.tesouro += pago;
				f.tesouroPendente += pago;
			}
			Economia.viagem(id, c.fornecedor);
			return false;
		});
	}

	private Comercio() {
	}
}
