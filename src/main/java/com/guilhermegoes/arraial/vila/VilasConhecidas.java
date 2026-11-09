package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.rede.Rede;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Which villages each player knows: a village becomes known when the player first sets foot on its
 * land. The list goes to the player's client (for the minimap waypoints, see the client's
 * IntegracaoXaero) whenever it changes.
 */
public final class VilasConhecidas {
	/** The last list sent to each player (to send only changes). */
	private static final Map<UUID, Integer> ENVIADO = new HashMap<>();

	/** The player is on the land of {@code donos}: those villages are now known to them. */
	static void pisou(ServerPlayer p, List<String> donos) {
		String eu = p.getUUID().toString();
		for (String id : donos) {
			Registro.Info v = Registro.get(id);
			if (v != null && !v.conhecida.contains(eu)) {
				v.conhecida.add(eu);
			}
		}
	}

	static List<Rede.VilaMapa> lista(ServerPlayer p) {
		String eu = p.getUUID().toString();
		List<Rede.VilaMapa> l = new ArrayList<>();
		for (Map.Entry<String, Registro.Info> e : Registro.todas().entrySet()) {
			Registro.Info v = e.getValue();
			if (v.conhecida.contains(eu)) {
				BlockPos c = v.posicao();
				l.add(new Rede.VilaMapa(e.getKey(), v.nome, c.getX(), c.getY(), c.getZ(), v.dimensao, v.cor1, "hispano".equals(v.povo),
						v.abandonada));
			}
		}
		return l;
	}

	/** Sends the player's list if it changed since the last time (or if forced). */
	static void enviar(ServerPlayer p) {
		if (!ServerPlayNetworking.canSend(p, Rede.Vilas.TYPE)) {
			return;
		}
		List<Rede.VilaMapa> l = lista(p);
		int h = l.hashCode();
		Integer antes = ENVIADO.put(p.getUUID(), h);
		if (antes == null || antes != h) {
			ServerPlayNetworking.send(p, new Rede.Vilas(l));
		}
	}

	/** A player (re)joined: send the list again. */
	public static void esquecer(UUID jogador) {
		ENVIADO.remove(jogador);
	}

	private VilasConhecidas() {
	}
}
