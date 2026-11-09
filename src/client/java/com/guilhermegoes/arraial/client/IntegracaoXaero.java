package com.guilhermegoes.arraial.client;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.rede.Rede;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The villages the player knows, as waypoints on Xaero's Minimap (and World Map). The list comes
 * from the server; the Xaero calls live in {@link PonteXaero}, loaded only when Xaero's is installed.
 * The minimap session may not exist yet when the list arrives, so it is applied on a later tick.
 */
public final class IntegracaoXaero {
	private static List<Rede.VilaMapa> vilas = List.of();
	private static boolean pendente;
	private static final boolean XAERO = FabricLoader.getInstance().isModLoaded("xaerominimap");

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(Rede.Vilas.TYPE, (p, ctx) -> {
			vilas = List.copyOf(p.vilas());
			pendente = true;
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (pendente && XAERO && mc.level != null && mc.player != null && mc.player.tickCount % 20 == 0) {
				try {
					pendente = !PonteXaero.aplicar(vilas);
				} catch (LinkageError | RuntimeException e) {
					Arraial.LOGGER.warn("Arraial: integração com o Xaero's Minimap falhou (versão diferente?)", e);
					pendente = false;
				}
			}
		});
	}

	/** The settings changed (waypoints on or off): apply again. */
	public static void configMudou() {
		pendente = true;
	}

	/** For the test script. */
	public static String estado() {
		return "conhecidas=" + vilas.size() + " xaero=" + XAERO + " no_mapa=" + (XAERO ? PonteXaero.aplicadas : 0) + " pendente=" + pendente;
	}

	private IntegracaoXaero() {
	}
}
