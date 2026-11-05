package com.guilhermegoes.arraial.vila;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.guilhermegoes.arraial.Arraial;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Every arraial in the world, loaded or not (GDD 15.2): a small record kept in
 * {@code <world>/arraial_vilas.json} so villages can know about each other while their chunks
 * sleep: a snapshot of stock and treasury for the tropeiros, reputation between villages,
 * and goods/money owed to a village that is not loaded right now ("pendentes").
 */
public final class Registro {
	/** What the world knows about one arraial. Plain fields: saved with Gson. */
	public static final class Info {
		public String dimensao = "minecraft:overworld";
		public long pos;
		public String nome = "";
		public int populacao;
		public int tesouro;
		public String crise = "";
		public int estagio;
		public boolean autonoma;
		/** Stock snapshot (item id → count). */
		public Map<String, Integer> estoque = new HashMap<>();
		/** Prices at its feira (item id → réis). */
		public Map<String, Double> precos = new HashMap<>();
		/** Reputation with other arraiais (their id → -100..100). */
		public Map<String, Integer> reputacao = new HashMap<>();
		/** Goods and money waiting for this arraial to be loaded again. */
		public Map<String, Integer> pendentes = new HashMap<>();
		public int tesouroPendente;
		public long dia;

		public BlockPos posicao() {
			return BlockPos.of(pos);
		}

		public int reputacaoCom(String outro) {
			return reputacao.getOrDefault(outro, 0);
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Info> VILAS = new LinkedHashMap<>();
	private static @Nullable Path arquivo;

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(Registro::carregar);
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> salvar());
	}

	public static String id(String dimensao, BlockPos pos) {
		return dimensao + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	public static Map<String, Info> todas() {
		return VILAS;
	}

	public static @Nullable Info get(String id) {
		return VILAS.get(id);
	}

	public static Info obter(String id) {
		return VILAS.computeIfAbsent(id, k -> new Info());
	}

	public static void remover(String id) {
		VILAS.remove(id);
	}

	/** Reputation between two arraiais moves for both of them. */
	public static void mudarReputacao(String a, String b, int delta) {
		Info ia = VILAS.get(a), ib = VILAS.get(b);
		if (ia != null) {
			ia.reputacao.merge(b, delta, (x, y) -> Math.max(-100, Math.min(100, x + y)));
		}
		if (ib != null) {
			ib.reputacao.merge(a, delta, (x, y) -> Math.max(-100, Math.min(100, x + y)));
		}
	}

	private static void carregar(MinecraftServer server) {
		VILAS.clear();
		arquivo = server.getWorldPath(LevelResource.ROOT).resolve("arraial_vilas.json");
		if (Files.exists(arquivo)) {
			try {
				Map<String, Info> lidas = GSON.fromJson(Files.readString(arquivo), new TypeToken<Map<String, Info>>() { }.getType());
				if (lidas != null) {
					VILAS.putAll(lidas);
				}
			} catch (IOException | RuntimeException e) {
				Arraial.LOGGER.warn("Arraial: não consegui ler {}", arquivo, e);
			}
		}
	}

	public static void salvar() {
		if (arquivo == null) {
			return;
		}
		try {
			Files.writeString(arquivo, GSON.toJson(VILAS));
		} catch (IOException e) {
			Arraial.LOGGER.warn("Arraial: não consegui salvar {}", arquivo, e);
		}
	}

	private Registro() {
	}
}
