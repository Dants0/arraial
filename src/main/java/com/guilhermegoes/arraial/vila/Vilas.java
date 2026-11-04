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

	private static final List<String> NOMES = List.of(
			"Arraial de Santa Luzia", "Arraial do Bom Jesus", "Arraial de São Bento", "Arraial da Boa Vista",
			"Arraial de Água Limpa", "Arraial do Rio Verde", "Arraial da Pedra Branca", "Arraial de São Gonçalo",
			"Arraial de Nossa Senhora da Conceição", "Arraial do Tijuco", "Arraial das Flores", "Arraial do Campo Alegre",
			"Arraial de Santo Antônio", "Arraial da Serra Azul", "Arraial do Ribeirão", "Arraial de São Miguel");

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
			double d = centro.distSqr(pos);
			if (d <= melhorDist && level.getBlockEntity(centro) instanceof ArraialBlockEntity) {
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

	static String nomeAleatorio(RandomSource random) {
		return NOMES.get(random.nextInt(NOMES.size()));
	}

	private Vilas() {
	}
}
