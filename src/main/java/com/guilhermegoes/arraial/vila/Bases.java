package com.guilhermegoes.arraial.vila;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.guilhermegoes.arraial.Arraial;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * The player's own base can be abandoned too. The base is where the player last slept; if they stay
 * away long enough and it is not inside any arraial's land, a gang of bandits may take it as their
 * hideout. They are there when the player comes back; once they are all gone, the base is retaken.
 * Kept in {@code <world>/arraial_bases.json}.
 */
public final class Bases {
	/** Plain fields, saved with Gson. */
	public static final class Base {
		public String dimensao = "minecraft:overworld";
		public long pos;
		public long ultimaVisita;
		public boolean ocupada;
		public boolean spawnados;
		public int vazio;
	}

	private static final int DIAS_LONGE = 8;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<UUID, Base> BASES = new HashMap<>();
	private static @Nullable Path arquivo;
	private static long ultimoDia = -1;

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(Bases::carregar);
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> salvar());
		EntitySleepEvents.START_SLEEPING.register((quem, cama) -> {
			if (quem instanceof ServerPlayer p) {
				Base b = BASES.computeIfAbsent(p.getUUID(), k -> new Base());
				if (b.ocupada && b.pos == cama.asLong()) {
					return; // you cannot sleep there quietly while bandits live in it
				}
				b.dimensao = p.level().dimension().identifier().toString();
				b.pos = cama.asLong();
				b.ultimaVisita = Vilas.dia((ServerLevel) p.level());
				b.ocupada = false;
				b.spawnados = false;
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 100 != 0) {
				return;
			}
			ServerLevel overworld = server.overworld();
			long hoje = Vilas.dia(overworld);
			if (hoje != ultimoDia) {
				if (ultimoDia >= 0 && hoje > ultimoDia) {
					diario(server, hoje);
				}
				ultimoDia = hoje;
			}
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				vigiar(p, hoje);
			}
		});
	}

	public static @Nullable Base de(UUID jogador) {
		return BASES.get(jogador);
	}

	/** Every day: bases left alone too long, outside any arraial, may be taken. */
	private static void diario(MinecraftServer server, long hoje) {
		for (Base b : BASES.values()) {
			BlockPos pos = BlockPos.of(b.pos);
			if (b.ocupada || hoje - b.ultimaVisita < DIAS_LONGE
					|| !Territorio.donos(b.dimensao, pos.getX() >> 4, pos.getZ() >> 4).isEmpty()) {
				continue;
			}
			if (server.overworld().getRandom().nextFloat() < 0.25F) {
				ocupar(b);
			}
		}
	}

	public static void ocupar(Base b) {
		b.ocupada = true;
		b.spawnados = false;
		b.vazio = 0;
	}

	private static void vigiar(ServerPlayer p, long hoje) {
		Base b = BASES.get(p.getUUID());
		if (b == null || !b.dimensao.equals(p.level().dimension().identifier().toString())) {
			return;
		}
		BlockPos pos = BlockPos.of(b.pos);
		if (!p.blockPosition().closerThan(pos, 48)) {
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		if (!b.ocupada) {
			b.ultimaVisita = hoje;
			return;
		}
		if (!b.spawnados) {
			b.spawnados = true;
			int n = Ruinas.spawnar(level, pos, 3);
			Conquistas.bau(level, pos);
			p.sendSystemMessage(Component.literal("⚔ ").append(Component.translatable("arraial.base.ocupada", n)).withStyle(ChatFormatting.DARK_RED));
			return;
		}
		List<Mob> vivos = Ruinas.bandidos(level, pos);
		if (!vivos.isEmpty()) {
			b.vazio = 0;
			return;
		}
		if (++b.vazio >= 3) {
			b.ocupada = false;
			b.spawnados = false;
			b.ultimaVisita = hoje;
			Conquistas.dar(p, "base_retomada");
			p.sendSystemMessage(Component.literal("⚑ ").append(Component.translatable("arraial.base.retomada")).withStyle(ChatFormatting.GOLD));
		}
	}

	private static void carregar(MinecraftServer server) {
		BASES.clear();
		ultimoDia = -1;
		arquivo = server.getWorldPath(LevelResource.ROOT).resolve("arraial_bases.json");
		if (Files.exists(arquivo)) {
			try {
				Map<UUID, Base> lidas = GSON.fromJson(Files.readString(arquivo), new TypeToken<Map<UUID, Base>>() { }.getType());
				if (lidas != null) {
					BASES.putAll(lidas);
				}
			} catch (IOException | RuntimeException e) {
				Arraial.LOGGER.warn("Arraial: não consegui ler {}", arquivo, e);
			}
		}
	}

	private static void salvar() {
		if (arquivo == null) {
			return;
		}
		try {
			Files.writeString(arquivo, GSON.toJson(BASES));
		} catch (IOException e) {
			Arraial.LOGGER.warn("Arraial: não consegui salvar {}", arquivo, e);
		}
	}

	private Bases() {
	}
}
