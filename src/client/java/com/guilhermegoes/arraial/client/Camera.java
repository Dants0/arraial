package com.guilhermegoes.arraial.client;

import java.util.function.DoubleFunction;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A film camera for the recording scripts (dev only, like {@link Roteiro}): the player, in spectator
 * mode, is moved every tick along a shot (a dolly between two points, an orbit, or a chase behind
 * an entity), always looking at a target, with eased start and stop. Moving the client player
 * itself keeps the picture smooth (the game interpolates between ticks).
 */
final class Camera {
	/** A shot: where the camera is at progress s (0..1, already eased) and what it looks at. */
	private record Plano(DoubleFunction<Vec3> posicao, Supplier<Vec3> alvo, int ticks) {
	}

	private static @Nullable Plano atual;
	private static int t;
	private static boolean registrada;

	private Camera() {
	}

	static void ligar() {
		if (!registrada) {
			registrada = true;
			ClientTickEvents.END_CLIENT_TICK.register(Camera::tick);
		}
	}

	static boolean rodando() {
		return atual != null;
	}

	private static void tocar(DoubleFunction<Vec3> posicao, Supplier<Vec3> alvo, int ticks) {
		ligar();
		atual = new Plano(posicao, alvo, Math.max(1, ticks));
		t = 0;
	}

	/** A straight move from one point to another, looking at a fixed point. */
	static void travelling(Vec3 de, Vec3 ate, Vec3 alvo, int ticks) {
		tocar(s -> de.lerp(ate, s), () -> alvo, ticks);
	}

	/** A move from one point to another, looking at something that moves (an entity). */
	static void travelling(Vec3 de, Vec3 ate, Supplier<Vec3> alvo, int ticks) {
		tocar(s -> de.lerp(ate, s), alvo, ticks);
	}

	/** Around a point, at a radius and height above it, from one angle to another (degrees). */
	static void orbita(Vec3 centro, double raio, double altura, double de, double ate, int ticks) {
		tocar(s -> {
			double a = Math.toRadians(de + (ate - de) * s);
			return centro.add(Math.cos(a) * raio, altura, Math.sin(a) * raio);
		}, () -> centro, ticks);
	}

	/** Behind and above an entity, keeping the same offset from it as it walks. */
	static void seguir(Supplier<@Nullable Entity> quem, double atras, double altura, int ticks) {
		final Vec3[] ultimo = {null};
		tocar(s -> {
			Entity e = quem.get();
			if (e == null) {
				return ultimo[0] == null ? Vec3.ZERO : ultimo[0];
			}
			Vec3 olhar = Vec3.directionFromRotation(0, e.getYRot());
			Vec3 p = e.position().subtract(olhar.scale(atras)).add(0, altura, 0);
			ultimo[0] = ultimo[0] == null ? p : ultimo[0].lerp(p, 0.15); // a little lag: no jerks
			return ultimo[0];
		}, () -> {
			Entity e = quem.get();
			return e == null ? (ultimo[0] == null ? Vec3.ZERO : ultimo[0]) : e.getEyePosition();
		}, ticks);
	}

	/** Alongside an entity at a fixed offset from it (a tracking shot), looking at it. */
	static void acompanhar(Supplier<@Nullable Entity> quem, Vec3 deslocamento, int ticks) {
		final Vec3[] ultimo = {null};
		tocar(s -> {
			Entity e = quem.get();
			if (e != null) {
				Vec3 p = e.position().add(deslocamento);
				ultimo[0] = ultimo[0] == null ? p : ultimo[0].lerp(p, 0.3);
			}
			return ultimo[0] == null ? Vec3.ZERO : ultimo[0];
		}, () -> {
			Entity e = quem.get();
			return e == null ? (ultimo[0] == null ? Vec3.ZERO : ultimo[0].subtract(deslocamento)) : e.position().add(0, 0.8, 0);
		}, ticks);
	}

	private static void tick(Minecraft mc) {
		Plano p = atual;
		LocalPlayer jogador = mc.player;
		if (p == null || jogador == null) {
			return;
		}
		t++;
		double k = Math.min(1.0, (double) t / p.ticks());
		double s = k * k * (3 - 2 * k); // ease in, ease out
		Vec3 pos = p.posicao().apply(s);
		jogador.setPos(pos.x, pos.y, pos.z);
		jogador.setDeltaMovement(Vec3.ZERO);
		Vec3 olho = pos.add(0, jogador.getEyeHeight(), 0);
		Vec3 a = p.alvo().get();
		double dx = a.x - olho.x, dy = a.y - olho.y, dz = a.z - olho.z;
		float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
		float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI));
		// Keep the yaw continuous (no spin when it crosses ±180).
		yaw = jogador.getYRot() + Mth.wrapDegrees(yaw - jogador.getYRot());
		jogador.setYRot(yaw);
		jogador.setXRot(pitch);
		jogador.setYHeadRot(yaw);
		if (t >= p.ticks()) {
			atual = null;
		}
	}
}
