package com.guilhermegoes.arraial.rede;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.vila.Chancelaria;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The mod's packets: the map/diplomacy screen (server → client) and the player's diplomatic acts (client → server). */
public final class Rede {
	/** Opens (or refreshes) the region map; with an escrivaninha, the diplomacy panel too. */
	public record AbrirTela(MapaDados dados) implements CustomPacketPayload {
		public static final Type<AbrirTela> TYPE = new Type<>(Arraial.id("tela_mapa"));
		public static final StreamCodec<RegistryFriendlyByteBuf, AbrirTela> CODEC = StreamCodec.of((b, p) -> p.dados().escrever(b),
				b -> new AbrirTela(MapaDados.ler(b)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** At the escrivaninha {@code mesa}: propose a treaty, break one, or start/lift an embargo on {@code outro}. */
	public record Acao(long mesa, String outro, String acao) implements CustomPacketPayload {
		public static final Type<Acao> TYPE = new Type<>(Arraial.id("diplomacia"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Acao> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, Acao::mesa, ByteBufCodecs.STRING_UTF8, Acao::outro, ByteBufCodecs.STRING_UTF8, Acao::acao, Acao::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A village the player knows, for the minimap (see VilasConhecidas). */
	public record VilaMapa(String id, String nome, int x, int y, int z, String dimensao, String cor, boolean hispano, boolean abandonada) {
	}

	/** The villages this player knows (sent when the list changes). */
	public record Vilas(java.util.List<VilaMapa> vilas) implements CustomPacketPayload {
		public static final Type<Vilas> TYPE = new Type<>(Arraial.id("vilas_conhecidas"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Vilas> CODEC = StreamCodec.of((b, p) -> {
			b.writeVarInt(p.vilas().size());
			for (VilaMapa v : p.vilas()) {
				b.writeUtf(v.id());
				b.writeUtf(v.nome());
				b.writeVarInt(v.x());
				b.writeVarInt(v.y());
				b.writeVarInt(v.z());
				b.writeUtf(v.dimensao());
				b.writeUtf(v.cor());
				b.writeBoolean(v.hispano());
				b.writeBoolean(v.abandonada());
			}
		}, b -> {
			int n = b.readVarInt();
			java.util.List<VilaMapa> l = new java.util.ArrayList<>();
			for (int i = 0; i < n; i++) {
				l.add(new VilaMapa(b.readUtf(), b.readUtf(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf(), b.readUtf(), b.readBoolean(), b.readBoolean()));
			}
			return new Vilas(l);
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Vilas.TYPE, Vilas.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AbrirTela.TYPE, AbrirTela.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Acao.TYPE, Acao.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Acao.TYPE, (p, ctx) -> Chancelaria.agir(ctx.player(), p));
	}

	private Rede() {
	}
}
