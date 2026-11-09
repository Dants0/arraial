package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.rede.MapaDados;
import java.util.HashMap;
import java.util.Map;

/**
 * Biomes whose names alone mislead the keyword rules (Terralith's "sakura_grove" is not snowy,
 * "emerald_peaks" is not a glacier, "steppe" says nothing): each one with its kind of land on the
 * region map. Used before the keyword rules by the map ({@link Chancelaria}) and by the region a
 * culture is born in ({@link Cultura}).
 */
final class Biomas {
	private static final Map<String, Integer> TIPO = new HashMap<>();

	static {
		tipo(MapaDados.CAMPO, "alpha_islands", "blooming_plateau", "blooming_valley", "lavender_valley", "lush_valley", "moonlight_valley",
				"sakura_valley", "shrubland", "steppe", "valley_clearing", "yellowstone");
		tipo(MapaDados.FLORESTA, "forested_highlands", "lavender_forest", "moonlight_grove", "sakura_grove", "skylands_autumn", "skylands_spring",
				"skylands_summer", "temperate_highlands", "yosemite_lowlands");
		tipo(MapaDados.SELVA, "amethyst_rainforest", "cloud_forest", "jungle_mountains", "rocky_jungle", "tropical_jungle");
		tipo(MapaDados.DESERTO, "ancient_sands", "desert_canyon", "desert_oasis", "desert_spires", "gravel_desert", "lush_desert", "red_oasis",
				"sandstone_valley");
		tipo(MapaDados.SAVANA, "arid_highlands", "ashen_savanna", "brushland", "fractured_savanna", "hot_shrubland", "savanna_badlands",
				"savanna_slopes");
		tipo(MapaDados.BADLANDS, "bryce_canyon", "painted_mountains", "warped_mesa", "white_mesa");
		tipo(MapaDados.MONTANHA, "alpine_highlands", "amethyst_canyon", "basalt_cliffs", "caldera", "emerald_peaks", "granite_cliffs",
				"haze_mountain", "highlands", "rocky_mountains", "rocky_shrubland", "scarlet_mountains", "stony_spires", "volcanic_crater",
				"volcanic_peaks", "white_cliffs", "windswept_spires", "yosemite_cliffs");
		tipo(MapaDados.TAIGA, "birch_taiga", "cold_shrubland", "shield", "shield_clearing", "siberian_taiga");
		tipo(MapaDados.NEVE, "alpha_islands_winter", "alpine_grove", "frozen_cliffs", "glacial_chasm", "ice_marsh", "siberian_grove",
				"skylands_winter", "snowy_badlands", "snowy_cherry_grove", "snowy_maple_forest", "snowy_shield", "wintry_forest", "wintry_lowlands");
		tipo(MapaDados.PANTANO, "orchid_swamp");
		tipo(MapaDados.PRAIA, "gravel_beach");
		tipo(MapaDados.OCEANO, "deep_warm_ocean", "mirage_isles");
		tipo(MapaDados.RIO, "warm_river");
	}

	private static void tipo(int t, String... biomas) {
		for (String b : biomas) {
			TIPO.put(b, t);
		}
	}

	/** @return the kind of land ({@link MapaDados} constants), or -1 if the keyword rules should decide */
	static int conhecido(String bioma) {
		return TIPO.getOrDefault(bioma, -1);
	}

	/** The region of a culture for a kind of land. */
	static String regiao(int tipo) {
		return switch (tipo) {
			case MapaDados.NEVE, MapaDados.TAIGA -> "frio";
			case MapaDados.DESERTO, MapaDados.SAVANA, MapaDados.BADLANDS -> "sertao";
			case MapaDados.SELVA -> "mata";
			case MapaDados.PANTANO, MapaDados.RIO, MapaDados.PRAIA, MapaDados.OCEANO -> "ribeirinho";
			case MapaDados.MONTANHA -> "serra";
			default -> "campo";
		};
	}

	private Biomas() {
	}
}
