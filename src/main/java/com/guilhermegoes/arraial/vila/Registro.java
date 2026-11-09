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
		/** Trades (profession id → how many), for the simulation while unloaded. */
		public Map<String, Integer> oficios = new HashMap<>();
		public int camas;
		public int satisfacao = 60;
		/** What the economy lives on ({@link Economia.Especialidade} id). */
		public String especialidade = "";
		/** Hungry days in a row while unloaded. */
		public int fome;
		/** Moradores who arrived (+) or left (-) while unloaded; settled when it loads. */
		public int saldoMoradores;
		/** Nobody lives here any more. */
		public boolean abandonada;
		/** The last day simulated while unloaded (see {@link Mundo}). */
		public long diaSimulado;
		/** A crisis while unloaded, and the day it ends. */
		public String criseLonge = "";
		public long criseLongeFim;
		/** Roads to other arraiais (their id → state of the road). */
		public Map<String, Rota> rotas = new HashMap<>();
		/** Things that happened while nobody was looking (copied into the Crônica on load). */
		public java.util.List<Evento> historia = new java.util.ArrayList<>();
		public java.util.List<Evento> historiaPendente = new java.util.ArrayList<>();
		// ---- Territory (see Territorio)
		/** Chunks (ChunkPos.asLong) where a Marco de Fronteira stands: each claims the 3x3 around it. */
		public java.util.List<Long> marcos = new java.util.ArrayList<>();
		/** Chunks taken by treaty: they belong only to this arraial. */
		public java.util.List<Long> anexos = new java.util.ArrayList<>();
		public Map<String, Integer> tensao = new HashMap<>();
		public java.util.List<String> disputas = new java.util.ArrayList<>();
		public Map<String, Long> guerras = new HashMap<>();
		public Map<String, Tratado> tratados = new HashMap<>();
		public long ultimaProposta;
		/** Neighbours this arraial refuses to trade with (an embargo: closed road, no merchants or contracts). */
		public java.util.List<String> embargos = new java.util.ArrayList<>();
		// ---- Ruins (see Ruinas)
		public int diasAbandonada;
		public int ruinaPendente;
		/** Who took the empty arraial: "" (nobody) or "bandidos". */
		public String ocupadaPor = "";
		public int bandidos;
		public boolean bandidosSpawnados;
		/** The arraial that resettled it, if any. */
		public String colonia = "";
		// ---- Culture (see Cultura)
		public String simbolo = "";
		public String cor1 = "";
		public String cor2 = "";
		public String bioma = "";
		/** The era (see Era), copied from the arraial when it is loaded. */
		public String era = "";
		/** "" (Portuguese) or "hispano" (a Spanish mission). */
		public String povo = "";
		/** Players (UUIDs) who have been on its land: it shows on their maps. */
		public java.util.List<String> conhecida = new java.util.ArrayList<>();
		public java.util.List<String> costumes = new java.util.ArrayList<>();
		public java.util.List<String> tradicoes = new java.util.ArrayList<>();
		public boolean bandeiraHasteada;
		/** Supply contracts this arraial has signed as buyer. */
		public java.util.List<Contrato> contratos = new java.util.ArrayList<>();

		public BlockPos posicao() {
			return BlockPos.of(pos);
		}

		public int reputacaoCom(String outro) {
			return reputacao.getOrDefault(outro, 0);
		}
	}

	/** A road between two arraiais: how often it was used, robbed, and whether it is closed. */
	/** A treaty with another arraial: kind, until when, and tribute (+ received, - paid) per day. */
	public static final class Tratado {
		public String tipo = "";
		public long ate;
		public int tributo;
	}

	/** A supply contract (kept by the buyer): every day {@code qtd} of {@code item} from the supplier. */
	public static final class Contrato {
		public String fornecedor = "";
		public String item = "";
		public int qtd;
		public double preco;
		public long ate;
		public int falhas;
	}

	public static final class Rota {
		public int viagens;
		public int roubos;
		public long ultimoRoubo;
		public long bloqueadaAte;
	}

	/** An event of the regional history: a lang key and its arguments. */
	public static final class Evento {
		public long dia;
		public String chave = "";
		public java.util.List<String> args = new java.util.ArrayList<>();

		public Evento() {
		}

		public Evento(long dia, String chave, String... args) {
			this.dia = dia;
			this.chave = chave;
			this.args = new java.util.ArrayList<>(java.util.List.of(args));
		}

		public net.minecraft.network.chat.Component texto() {
			// Arguments that are lang keys themselves ("arraial.historia.motivo.fome") are translated too.
			return net.minecraft.network.chat.Component.translatable(chave, args.stream()
					.map(a -> a.startsWith("arraial.") ? (Object) net.minecraft.network.chat.Component.translatable(a) : a).toArray());
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
