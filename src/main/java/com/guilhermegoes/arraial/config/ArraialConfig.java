package com.guilhermegoes.arraial.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.guilhermegoes.arraial.Arraial;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The mod's settings, in {@code config/arraial.json} (edited in game through Mod Menu, or by hand on a
 * server). Plain public fields, read where they matter; out-of-range values are clamped on load.
 */
public final class ArraialConfig {
	// ---- The world
	/** Autonomous villages spring up near the player's arraial, so there are neighbours to deal with. */
	public boolean vizinhasPerto = true;
	/** At most this many autonomous villages in the world. */
	public int maxAutonomas = 10;
	/** Chance (%) that a new autonomous village is a Spanish mission. */
	public int chanceMissao = 30;
	/** New villages are founded live: a caravan of settlers arrives, plants the Cruzeiro and builds (false: they appear ready). */
	public boolean fundacaoAoVivo = true;
	/** A player's base left alone for days may be taken by bandits. */
	public boolean basesOcupadas = true;

	// ---- Village life
	/** Crises happen (drought, flood, pests, highwaymen, epidemics, fire). */
	public boolean crises = true;
	/** How often crises are rolled (%; 100 = normal). */
	public int frequenciaCrises = 100;
	/** Seconds between newcomers while there are free beds. */
	public int chegadaSegundos = 50;
	/** Soldiers take better armour and weapons from the storehouse (from any mod). */
	public boolean soldadosUsamArmazem = true;

	// ---- Display
	/** The "lands of X" message when crossing a border. */
	public boolean avisoFronteira = true;
	/** Known villages appear as waypoints on Xaero's Minimap / World Map. */
	public boolean waypointsNoMapa = true;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ArraialConfig atual = new ArraialConfig();

	public static ArraialConfig get() {
		return atual;
	}

	private static Path arquivo() {
		return FabricLoader.getInstance().getConfigDir().resolve("arraial.json");
	}

	public static void carregar() {
		Path f = arquivo();
		if (Files.exists(f)) {
			try {
				ArraialConfig lida = GSON.fromJson(Files.readString(f), ArraialConfig.class);
				if (lida != null) {
					atual = lida;
				}
			} catch (IOException | RuntimeException e) {
				Arraial.LOGGER.warn("arraial.json ilegível; usando os valores padrão", e);
			}
		}
		atual.limitar();
		salvar();   // writes any new options with their defaults
	}

	public static void salvar() {
		try {
			Files.writeString(arquivo(), GSON.toJson(atual));
		} catch (IOException e) {
			Arraial.LOGGER.warn("não foi possível salvar arraial.json", e);
		}
	}

	private void limitar() {
		maxAutonomas = Math.max(0, Math.min(40, maxAutonomas));
		chanceMissao = Math.max(0, Math.min(100, chanceMissao));
		frequenciaCrises = Math.max(0, Math.min(300, frequenciaCrises));
		chegadaSegundos = Math.max(10, Math.min(600, chegadaSegundos));
	}

	/** Ticks between newcomers. */
	public int intervaloChegada() {
		return chegadaSegundos * 20;
	}
}
