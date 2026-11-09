package com.guilhermegoes.arraial.client;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.config.ArraialConfig;
import com.guilhermegoes.arraial.rede.Rede;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;

/**
 * Talks to Xaero's Minimap (only loaded when it is installed), the same way it integrates
 * Waystones: third-party waypoints of the origin "arraial:vilas" in each dimension's container.
 */
final class PonteXaero {
	private static final Identifier ORIGEM = Arraial.id("vilas");
	/** Dimensions that got waypoints last time (to clear them when the list changes). */
	private static final Set<String> DIMENSOES = new HashSet<>();
	/** Waypoints added last time (for the test script). */
	static int aplicadas;

	/** @return false if the minimap is not ready yet (try again later) */
	static boolean aplicar(List<Rede.VilaMapa> vilas) {
		MinimapSession sessao = BuiltInHudModules.MINIMAP.getCurrentSession();
		if (sessao == null || sessao.getWorldManager() == null) {
			return false;
		}
		MinimapWorldRootContainer raiz = sessao.getWorldManager().getAutoRootContainer();
		if (raiz == null) {
			return false;
		}
		Set<String> dims = new HashSet<>(DIMENSOES);
		vilas.forEach(v -> dims.add(v.dimensao()));
		for (String dim : dims) {
			pontos(raiz, dim).clear();
		}
		DIMENSOES.clear();
		aplicadas = 0;
		if (!ArraialConfig.get().waypointsNoMapa) {
			return true;
		}
		for (Rede.VilaMapa v : vilas) {
			String lugar = v.nome().replaceFirst("^(Povoado|Arraial|Vila próspera|Vila|Cidade|Misión|Pueblo|Villa Real|Villa|Ciudad) (de|do|da|dos|das|del) ", "");
			String sigla = lugar.isEmpty() ? "A" : lugar.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
			Waypoint w = new Waypoint(v.x(), v.y() + 1, v.z(), v.abandonada() ? v.nome() + " ☠" : v.nome(), sigla,
					v.abandonada() ? WaypointColor.DARK_RED : cor(v.cor()), WaypointPurpose.NORMAL);
			pontos(raiz, v.dimensao()).add(v.id(), w);
			DIMENSOES.add(v.dimensao());
			aplicadas++;
		}
		return true;
	}

	private static ThirdPartyWaypoints pontos(MinimapWorldRootContainer raiz, String dimensao) {
		ResourceKey<net.minecraft.world.level.Level> chave = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimensao));
		String pasta = raiz.getSession().getDimensionHelper().getDimensionDirectoryName(chave);
		MinimapWorldContainer c = raiz.addSubContainer(raiz.getPath().resolve(pasta));
		ThirdPartyWaypoints t = c.getThirdPartyWaypointManager().get(ORIGEM);
		if (!t.hasEnabledStateGetter()) {
			t.setEnabledStateGetter(() -> true);
		}
		return t;
	}

	/** The flag colour of the village as the nearest waypoint colour. */
	private static WaypointColor cor(String corDaBandeira) {
		return switch (corDaBandeira) {
			case "red" -> WaypointColor.RED;
			case "orange", "brown" -> WaypointColor.GOLD;
			case "yellow" -> WaypointColor.YELLOW;
			case "lime" -> WaypointColor.GREEN;
			case "green" -> WaypointColor.DARK_GREEN;
			case "cyan" -> WaypointColor.DARK_AQUA;
			case "light_blue" -> WaypointColor.AQUA;
			case "blue" -> WaypointColor.BLUE;
			case "purple" -> WaypointColor.DARK_PURPLE;
			case "magenta" -> WaypointColor.MAGENTA;
			case "pink" -> WaypointColor.PINK;
			case "gray", "light_gray" -> WaypointColor.GRAY;
			case "black" -> WaypointColor.DARK_GRAY;
			default -> WaypointColor.WHITE;
		};
	}

	private PonteXaero() {
	}
}
