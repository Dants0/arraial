package com.guilhermegoes.arraial.vila;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Shared village rules and the index of loaded arraiais (by their Cruzeiro position). */
public final class Vilas {
	/** How far an arraial reaches from its Cruzeiro, in blocks. */
	public static final int RAIO = 48;
	/** Two Cruzeiros must be at least this far apart. */
	public static final int DISTANCIA_MINIMA = RAIO * 2;
	/** Time between newcomers while there are free beds: one in-game hour (50 s). */
	public static final int INTERVALO_CHEGADA = 1000;
	/** Job-seekers stop coming while this many moradores already have no bed. */
	public static final int MAX_SEM_CASA = 2;

	private static final List<String> NOMES = List.of(
			"Arraial de Santa Luzia", "Arraial do Bom Jesus", "Arraial de São Bento", "Arraial da Boa Vista",
			"Arraial de Água Limpa", "Arraial do Rio Verde", "Arraial da Pedra Branca", "Arraial de São Gonçalo",
			"Arraial de Nossa Senhora da Conceição", "Arraial do Tijuco", "Arraial das Flores", "Arraial do Campo Alegre",
			"Arraial de Santo Antônio", "Arraial da Serra Azul", "Arraial do Ribeirão", "Arraial de São Miguel",
			"Arraial de Sant'Ana", "Arraial do Rosário", "Arraial de São José", "Arraial do Carmo", "Arraial de Itapecerica",
			"Arraial do Pilar", "Arraial de São João del-Rei", "Arraial das Mercês", "Arraial do Cajueiro", "Arraial da Lagoa Santa",
			"Arraial do Brejo Grande", "Arraial de Bonfim", "Arraial da Penha", "Arraial do Sumidouro", "Arraial de Piracicaba",
			"Arraial do Morro Velho", "Arraial de Paracatu", "Arraial dos Remédios", "Arraial da Cachoeira", "Arraial do Juazeiro",
			"Arraial de Itabira", "Arraial do Curral del-Rei", "Arraial da Barra", "Arraial de São Vicente");

	private static final Map<ResourceKey<Level>, Set<BlockPos>> ATIVAS = new ConcurrentHashMap<>();

	static void registrar(Level level, BlockPos pos) {
		ATIVAS.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
	}

	static void remover(Level level, BlockPos pos) {
		Set<BlockPos> s = ATIVAS.get(level.dimension());
		if (s != null) {
			s.remove(pos);
		}
	}

	public static Set<BlockPos> ativas(Level level) {
		return ATIVAS.getOrDefault(level.dimension(), Set.of());
	}

	/** The Cruzeiro whose arraial covers {@code pos}, or null. */
	public static @Nullable BlockPos maisProxima(ServerLevel level, BlockPos pos) {
		BlockPos melhor = null;
		double melhorDist = (double) RAIO * RAIO;
		for (BlockPos centro : ativas(level)) {
			// Horizontal distance (a village on a hill is still near the valley below it), within 64 up or down.
			double dx = centro.getX() - pos.getX(), dz = centro.getZ() - pos.getZ();
			double d = dx * dx + dz * dz;
			if (Math.abs(centro.getY() - pos.getY()) <= 64 && d <= melhorDist && level.getBlockEntity(centro) instanceof ArraialBlockEntity) {
				melhor = centro;
				melhorDist = d;
			}
		}
		return melhor;
	}

	/** True when another arraial's Cruzeiro is too close to {@code pos}. */
	public static boolean temVizinha(ServerLevel level, BlockPos pos) {
		for (BlockPos centro : ativas(level)) {
			if (!centro.equals(pos) && centro.distSqr(pos) < (double) DISTANCIA_MINIMA * DISTANCIA_MINIMA) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------ armazéns (storehouses)

	private static final Map<ResourceKey<Level>, Set<BlockPos>> ARMAZENS = new ConcurrentHashMap<>();

	static void registrarArmazem(Level level, BlockPos pos) {
		ARMAZENS.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
	}

	static void removerArmazem(Level level, BlockPos pos) {
		Set<BlockPos> s = ARMAZENS.get(level.dimension());
		if (s != null) {
			s.remove(pos);
		}
	}

	/** Loaded storehouses inside the arraial whose Cruzeiro is at {@code vila}. */
	public static List<ArmazemBlockEntity> armazens(ServerLevel level, @Nullable BlockPos vila) {
		if (vila == null) {
			return List.of();
		}
		List<ArmazemBlockEntity> lista = new java.util.ArrayList<>();
		for (BlockPos p : ARMAZENS.getOrDefault(level.dimension(), Set.of())) {
			if (p.closerThan(vila, RAIO) && level.isLoaded(p) && level.getBlockEntity(p) instanceof ArmazemBlockEntity a) {
				lista.add(a);
			}
		}
		return lista;
	}

	public static int contar(List<? extends net.minecraft.world.Container> fontes, java.util.function.Predicate<net.minecraft.world.item.ItemStack> filtro) {
		int total = 0;
		for (net.minecraft.world.Container c : fontes) {
			for (int i = 0; i < c.getContainerSize(); i++) {
				net.minecraft.world.item.ItemStack s = c.getItem(i);
				if (!s.isEmpty() && filtro.test(s)) {
					total += s.getCount();
				}
			}
		}
		return total;
	}

	public static int contar(List<? extends net.minecraft.world.Container> fontes, net.minecraft.world.item.Item item) {
		return contar(fontes, s -> s.is(item));
	}

	/** Everything stored in {@code fontes}, counted per item. */
	public static Map<net.minecraft.world.item.Item, Integer> inventario(List<? extends net.minecraft.world.Container> fontes) {
		Map<net.minecraft.world.item.Item, Integer> mapa = new java.util.HashMap<>();
		for (net.minecraft.world.Container c : fontes) {
			for (int i = 0; i < c.getContainerSize(); i++) {
				net.minecraft.world.item.ItemStack s = c.getItem(i);
				if (!s.isEmpty()) {
					mapa.merge(s.getItem(), s.getCount(), Integer::sum);
				}
			}
		}
		return mapa;
	}

	/** Removes up to {@code quantidade} matching items; returns what was taken (may be several stacks merged into a list). */
	public static List<net.minecraft.world.item.ItemStack> retirar(List<? extends net.minecraft.world.Container> fontes,
			java.util.function.Predicate<net.minecraft.world.item.ItemStack> filtro, int quantidade) {
		List<net.minecraft.world.item.ItemStack> pegou = new java.util.ArrayList<>();
		int falta = quantidade;
		for (net.minecraft.world.Container c : fontes) {
			for (int i = 0; i < c.getContainerSize() && falta > 0; i++) {
				net.minecraft.world.item.ItemStack s = c.getItem(i);
				if (!s.isEmpty() && filtro.test(s)) {
					net.minecraft.world.item.ItemStack tirado = c.removeItem(i, falta);
					falta -= tirado.getCount();
					pegou.add(tirado);
				}
			}
			c.setChanged();
		}
		return pegou;
	}

	public static int retirar(List<? extends net.minecraft.world.Container> fontes, net.minecraft.world.item.Item item, int quantidade) {
		return retirar(fontes, s -> s.is(item), quantidade).stream().mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
	}

	/** Puts {@code quantidade} of {@code item} into the storehouses; returns how many did not fit. */
	public static int depositar(List<? extends net.minecraft.world.Container> fontes, net.minecraft.world.item.Item item, int quantidade) {
		int resta = quantidade;
		int max = item.getDefaultMaxStackSize();
		for (net.minecraft.world.Container c : fontes) {
			// First top up existing stacks, then use empty slots.
			for (int passo = 0; passo < 2 && resta > 0; passo++) {
				for (int i = 0; i < c.getContainerSize() && resta > 0; i++) {
					net.minecraft.world.item.ItemStack s = c.getItem(i);
					if (passo == 0 && s.is(item) && s.getCount() < max) {
						int cabe = Math.min(resta, max - s.getCount());
						s.grow(cabe);
						resta -= cabe;
					} else if (passo == 1 && s.isEmpty()) {
						int cabe = Math.min(resta, max);
						c.setItem(i, new net.minecraft.world.item.ItemStack(item, cabe));
						resta -= cabe;
					}
				}
			}
			c.setChanged();
		}
		return resta;
	}

	/** Workstations of a type inside the arraial at {@code vila}. */
	public static long postosDe(ServerLevel level, BlockPos vila, net.minecraft.world.entity.ai.village.poi.PoiType tipo) {
		return level.getPoiManager().getCountInRange(h -> h.value() == tipo, vila, RAIO,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY);
	}

	/** Current in-game day, starting at 1. */
	public static long dia(ServerLevel level) {
		return level.getOverworldClockTime() / 24000L + 1;
	}

	/** A name no arraial in the world uses yet (compared without the stage: "Vila do Tijuco" uses "do Tijuco"). */
	static String nomeAleatorio(RandomSource random) {
		java.util.Set<String> usados = new java.util.HashSet<>();
		for (Registro.Info v : Registro.todas().values()) {
			usados.add(lugar(v.nome));
		}
		List<String> livres = NOMES.stream().filter(n -> !usados.contains(lugar(n))).toList();
		if (!livres.isEmpty()) {
			return livres.get(random.nextInt(livres.size()));
		}
		String n = NOMES.get(random.nextInt(NOMES.size()));
		String novo = n + " de Cima";
		return usados.contains(lugar(novo)) ? novo + " " + (2 + random.nextInt(98)) : novo;
	}

	/** The place part of a name ("Povoado do Tijuco" -> "do Tijuco"). */
	private static String lugar(String nome) {
		int i = nome.indexOf(" d");
		return i < 0 ? nome : nome.substring(i + 1);
	}

	private Vilas() {
	}
}
