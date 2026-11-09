package com.guilhermegoes.arraial.trabalho;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import com.guilhermegoes.arraial.obra.CanteiroBlock;
import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import com.guilhermegoes.arraial.obra.PlanoDeObra;
import com.guilhermegoes.arraial.vila.ArmazemBlockEntity;
import com.guilhermegoes.arraial.vila.Vilas;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * What the arraial is short of right now, and how urgently:
 * <ul>
 * <li>materials its construction sites still need (beyond their chests), ranked by each site's
 * {@link com.guilhermegoes.arraial.obra.Prioridade};</li>
 * <li>bread when food runs low (top urgency when there is not even a day's worth);</li>
 * <li>the prefeito's minimum stock ({@link Estoque}), lowest urgency;</li>
 * <li>all of that expanded through the trades' recipes (a missing door means planks, which means
 * logs...), choosing for each item the recipe variant the armazém can best supply.</li>
 * </ul>
 */
public final class Demanda {
	private static final int PROFUNDIDADE = 4;
	/** The arraial wants this many days of food per morador in stock. */
	private static final int DIAS_DE_RESERVA = 3;

	/** Urgency ranks: site priorities map to 10..40; reserve stock is the lowest. */
	public static final int URGENCIA_META = 5;
	private static final int URGENCIA_PAO = 25;
	private static final int URGENCIA_FOME = 45;

	/**
	 * @param falta      what is missing, per item
	 * @param urgencia   how urgent each missing item is (higher first)
	 * @param reservado  materials the construction sites still need straight from the armazém;
	 *                   artisans must not use them as ingredients
	 * @param estoque    what the armazéns hold
	 */
	public record Resultado(Map<Item, Integer> falta, Map<Item, Integer> urgencia, Map<Item, Integer> reservado, Map<Item, Integer> estoque) {
		/** What of {@code item} the armazém holds beyond what the sites have claimed. */
		public int livre(Item item) {
			return estoque.getOrDefault(item, 0) - reservado.getOrDefault(item, 0);
		}

		public int urgenciaDe(Item item) {
			return urgencia.getOrDefault(item, 0);
		}

		/** The missing items, most urgent first. */
		public List<Map.Entry<Item, Integer>> pedidos() {
			List<Map.Entry<Item, Integer>> lista = new ArrayList<>(falta.entrySet());
			lista.sort((a, b) -> Integer.compare(urgenciaDe(b.getKey()), urgenciaDe(a.getKey())));
			return lista;
		}
	}

	public static int urgencia(com.guilhermegoes.arraial.obra.Prioridade p) {
		return 10 + 10 * p.ordinal();
	}

	public static Resultado calcular(ServerLevel level, BlockPos vila) {
		List<ArmazemBlockEntity> armazens = Vilas.armazens(level, vila);
		Map<Item, Integer> inv = Vilas.inventario(armazens);
		Map<Item, Integer> bruto = new LinkedHashMap<>();
		Map<Item, Integer> urgencia = new HashMap<>();
		Map<Item, Integer> reservado = new HashMap<>();

		// 1. Construction sites, most urgent first.
		for (CanteiroBlockEntity c : CanteiroBlockEntity.daVila(level, vila)) {
			for (Map.Entry<Item, Integer> e : restante(level, c).entrySet()) {
				bruto.merge(e.getKey(), e.getValue(), Integer::sum);
				reservado.merge(e.getKey(), e.getValue(), Integer::sum);
				urgencia.merge(e.getKey(), urgencia(c.prioridade()), Math::max);
			}
		}

		// 2. Food: keep a few days of bread for everyone.
		List<MoradorEntity> moradores = level.getEntitiesOfClass(MoradorEntity.class, new AABB(vila).inflate(Vilas.RAIO),
				m -> vila.equals(m.vila()));
		int n = moradores.size();
		int comida = Vilas.contar(armazens, s -> s.has(DataComponents.FOOD));
		int querComida = n * DIAS_DE_RESERVA - comida;
		if (querComida > 0) {
			// Gross need = what is there + what is missing, so that need - stock = the shortfall.
			bruto.merge(Items.BREAD, querComida + inv.getOrDefault(Items.BREAD, 0), Integer::sum);
			urgencia.merge(Items.BREAD, comida < n ? URGENCIA_FOME : URGENCIA_PAO, Math::max);
			// With a casa de farinha, farinha is the staple (cassava feeds far more people than wheat).
			if (moradores.stream().anyMatch(m -> m.profissao() == Profissao.FARINHEIRO)) {
				Item farinha = com.guilhermegoes.arraial.registry.ModItems.FARINHA;
				bruto.merge(farinha, querComida + inv.getOrDefault(farinha, 0), Integer::sum);
				urgencia.merge(farinha, (comida < n ? URGENCIA_FOME : URGENCIA_PAO) + 1, Math::max);
			}
		}

		// 3. The prefeito's minimum stock.
		Set<Profissao> oficios = EnumSet.noneOf(Profissao.class);
		moradores.forEach(m -> oficios.add(m.profissao()));
		Estoque.metas(level, vila, n, oficios, inv).forEach((item, alvo) -> {
			bruto.merge(item, alvo, Integer::sum);
			urgencia.merge(item, URGENCIA_META, Math::max);
		});

		// 4. Expand through recipes: whatever will be crafted needs its ingredients.
		Map<Item, Integer> expandido = new HashMap<>();
		for (int nivel = 0; nivel < PROFUNDIDADE; nivel++) {
			boolean mudou = false;
			for (Item item : new ArrayList<>(bruto.keySet())) {
				int f = bruto.get(item) - inv.getOrDefault(item, 0) - expandido.getOrDefault(item, 0);
				if (f <= 0) {
					continue;
				}
				Receitas.Receita r = escolher(item, inv);
				if (r == null) {
					continue;
				}
				int lotes = (f + r.quantidade() - 1) / r.quantidade();
				int u = urgencia.getOrDefault(item, 0);
				r.entrada().forEach((ing, q) -> {
					bruto.merge(ing, lotes * q, Integer::sum);
					urgencia.merge(ing, u, Math::max);
				});
				expandido.merge(item, lotes * r.quantidade(), Integer::sum);
				mudou = true;
			}
			if (!mudou) {
				break;
			}
		}

		Map<Item, Integer> falta = new LinkedHashMap<>();
		bruto.forEach((item, q) -> {
			int f = q - inv.getOrDefault(item, 0);
			if (f > 0) {
				falta.put(item, f);
			}
		});
		return new Resultado(falta, urgencia, reservado, inv);
	}

	/** What a site still needs from the armazém: its remaining materials minus its own chest. */
	public static Map<Item, Integer> restante(ServerLevel level, CanteiroBlockEntity c) {
		Map<Item, Integer> mapa = new LinkedHashMap<>();
		PlanoDeObra plano = c.plano(level).orElse(null);
		if (plano == null) {
			return mapa;
		}
		Container bau = c.bau(level);
		for (Map.Entry<Item, Integer> e : plano.materiais(c.progresso()).entrySet()) {
			int n = e.getValue() - (bau == null ? 0 : CanteiroBlock.contar(bau, e.getKey()));
			if (n > 0) {
				mapa.put(e.getKey(), n);
			}
		}
		return mapa;
	}

	/**
	 * How much of {@code item} the armazém must keep for sites more urgent than {@code site}
	 * (those that come before it in {@link CanteiroBlockEntity#ORDEM}).
	 */
	public static int reservadoAntes(ServerLevel level, BlockPos vila, CanteiroBlockEntity site, Item item) {
		int total = 0;
		for (CanteiroBlockEntity c : CanteiroBlockEntity.daVila(level, vila)) {
			if (c == site || CanteiroBlockEntity.ORDEM.compare(c, site) >= 0
					|| inviavel(level, vila, restante(level, c), oficios(level, vila)).isPresent()) {
				break;
			}
			total += restante(level, c).getOrDefault(item, 0);
		}
		return total;
	}

	/** The recipe for {@code item} whose ingredients the armazém can best supply (ties: the first). */
	public static Receitas.@Nullable Receita escolher(Item item, Map<Item, Integer> inv) {
		List<Receitas.Receita> opcoes = Receitas.todasPara(item);
		if (opcoes.size() <= 1) {
			return opcoes.isEmpty() ? null : opcoes.get(0);
		}
		Receitas.Receita melhor = opcoes.get(0);
		int melhorLotes = -1;
		for (Receitas.Receita r : opcoes) {
			int lotes = lotesPossiveis(r, inv, 1);
			if (lotes > melhorLotes) {
				melhorLotes = lotes;
				melhor = r;
			}
		}
		return melhor;
	}

	private static int lotesPossiveis(Receitas.Receita r, Map<Item, Integer> inv, int nivel) {
		int lotes = Integer.MAX_VALUE;
		for (Map.Entry<Item, Integer> ing : r.entrada().entrySet()) {
			lotes = Math.min(lotes, disponivel(ing.getKey(), inv, nivel) / ing.getValue());
		}
		return lotes == Integer.MAX_VALUE ? 0 : lotes;
	}

	/** Stock of {@code item} plus what could be made of it from stock, {@code nivel} recipes deep. */
	private static int disponivel(Item item, Map<Item, Integer> inv, int nivel) {
		int d = inv.getOrDefault(item, 0);
		if (nivel > 0) {
			int fazer = 0;
			for (Receitas.Receita r : Receitas.todasPara(item)) {
				fazer = Math.max(fazer, lotesPossiveis(r, inv, nivel - 1) * r.quantidade());
			}
			d += fazer;
		}
		return d;
	}

	// ------------------------------------------------------------ can the arraial get it?

	/** Raw materials and which trade gathers them. */
	private static final Map<Item, Profissao> FONTES = new HashMap<>();

	static {
		for (Item i : List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.ANDESITE, Items.DIORITE, Items.GRANITE,
				Items.COAL, Items.RAW_IRON, Items.RAW_COPPER)) {
			FONTES.put(i, Profissao.CAVOUQUEIRO);
		}
		for (Item i : List.of(Items.DIRT, Items.CLAY_BALL, Items.SAND, Items.GRAVEL, Items.MUD)) {
			FONTES.put(i, Profissao.BARREIRO);
		}
		for (Item i : List.of(Items.WHEAT, Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.BEETROOT_SEEDS)) {
			FONTES.put(i, Profissao.ROCEIRO);
		}
		for (Item i : List.of(Items.MUTTON, Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.LEATHER, Items.FEATHER, Items.EGG)) {
			FONTES.put(i, Profissao.PASTOR);
		}
		Items.WOOL.forEach(i -> FONTES.put(i, Profissao.PASTOR));
		FONTES.put(Items.APPLE, Profissao.LENHADOR);
		FONTES.put(Items.SUGAR_CANE, Profissao.MESTRE_ENGENHO);
		FONTES.put(com.guilhermegoes.arraial.registry.ModItems.CAFE_CEREJA, Profissao.CAFEICULTOR);
	}

	/** The trades present in the arraial. */
	public static Set<Profissao> oficios(ServerLevel level, BlockPos vila) {
		Set<Profissao> set = EnumSet.noneOf(Profissao.class);
		for (MoradorEntity m : level.getEntitiesOfClass(MoradorEntity.class, new AABB(vila).inflate(Vilas.RAIO), m -> vila.equals(m.vila()))) {
			set.add(m.profissao());
		}
		return set;
	}

	/**
	 * The first material of {@code materiais} the arraial cannot get: not in stock, nobody
	 * gathers it, and no trade present can make it. Empty when everything is reachable.
	 */
	public static Optional<Item> inviavel(ServerLevel level, BlockPos vila, Map<Item, Integer> materiais, Set<Profissao> oficios) {
		Map<Item, Integer> inv = Vilas.inventario(Vilas.armazens(level, vila));
		for (Map.Entry<Item, Integer> e : materiais.entrySet()) {
			if (!obtivel(inv, e.getKey(), e.getValue(), oficios, 0)) {
				return Optional.of(e.getKey());
			}
		}
		return Optional.empty();
	}

	private static boolean obtivel(Map<Item, Integer> inv, Item item, int quantidade, Set<Profissao> oficios, int nivel) {
		if (inv.getOrDefault(item, 0) >= quantidade) {
			return true;
		}
		Profissao fonte = item.getDefaultInstance().is(ItemTags.LOGS) ? Profissao.LENHADOR : FONTES.get(item);
		if (fonte != null && oficios.contains(fonte)) {
			return true;
		}
		if (nivel >= 4) {
			return false;
		}
		// Any recipe variant will do (oak or spruce planks, whichever the arraial can get).
		for (Receitas.Receita r : Receitas.todasPara(item)) {
			if (!oficios.contains(r.quem())) {
				continue;
			}
			int lotes = (quantidade + r.quantidade() - 1) / r.quantidade();
			boolean ok = true;
			for (Map.Entry<Item, Integer> ing : r.entrada().entrySet()) {
				if (!obtivel(inv, ing.getKey(), ing.getValue() * lotes, oficios, nivel + 1)) {
					ok = false;
					break;
				}
			}
			if (ok) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The trade the arraial lacks to get {@code item}, following the chain down: mud bricks need
	 * the oleiro, who needs earth, which the barreiro digs; with an oleiro already there, the answer
	 * is the barreiro. Null when nothing is missing (or no trade at all can make it).
	 */
	public static @Nullable Profissao faltante(ServerLevel level, BlockPos vila, Item item, int quantidade, Set<Profissao> oficios) {
		return faltante(Vilas.inventario(Vilas.armazens(level, vila)), item, quantidade, oficios, 0);
	}

	private static @Nullable Profissao faltante(Map<Item, Integer> inv, Item item, int quantidade, Set<Profissao> oficios, int nivel) {
		if (obtivel(inv, item, quantidade, oficios, nivel)) {
			return null;
		}
		Profissao fonte = item.getDefaultInstance().is(ItemTags.LOGS) ? Profissao.LENHADOR : FONTES.get(item);
		if (fonte != null) {
			return fonte;
		}
		Profissao primeiro = null;
		for (Receitas.Receita r : Receitas.todasPara(item)) {
			if (!oficios.contains(r.quem())) {
				if (primeiro == null) {
					primeiro = r.quem();
				}
				continue;
			}
			if (nivel >= 4) {
				continue;
			}
			int lotes = (quantidade + r.quantidade() - 1) / r.quantidade();
			for (Map.Entry<Item, Integer> ing : r.entrada().entrySet()) {
				Profissao f = faltante(inv, ing.getKey(), ing.getValue() * lotes, oficios, nivel + 1);
				if (f != null) {
					return f;
				}
			}
		}
		return primeiro;
	}

	/** The trade that gathers or makes {@code item}, or null. */
	public static @Nullable Profissao quemFaz(Item item) {
		var r = Receitas.para(item);
		if (r.isPresent()) {
			return r.get().quem();
		}
		return item.getDefaultInstance().is(ItemTags.LOGS) ? Profissao.LENHADOR : FONTES.get(item);
	}

	private Demanda() {
	}
}
