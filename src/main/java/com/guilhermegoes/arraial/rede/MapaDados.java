package com.guilhermegoes.arraial.rede;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * What the region map and the diplomacy screen show, sent from the server: a coarse biome grid of
 * the region, every arraial in it (with its flag colours and its relations with the player's own
 * arraial), the land each one claims, the roads between them, and the player's base.
 */
public final class MapaDados {
	/** Biome kinds of the grid (the client picks the colours). */
	public static final int OCEANO = 0, RIO = 1, PRAIA = 2, DESERTO = 3, BADLANDS = 4, SAVANA = 5, SELVA = 6, PANTANO = 7, NEVE = 8,
			TAIGA = 9, FLORESTA = 10, MONTANHA = 11, CAMPO = 12;

	public static final class Vila {
		public String id = "", nome = "", era = "", especialidade = "", simbolo = "";
		public int x, z, cor, cor2, estagio, populacao, forca;
		public boolean abandonada, bandidos;
		/** Relations with the player's arraial (the one of the escrivaninha, or where the player stands). */
		public int rep, tensao;
		/** 0: calm, 1: dispute, 2: war. */
		public int estado;
		public String tratado = "";
		public int dias, tributo;
		public boolean embargamos, embargados, batida;
		/** "" (Portuguese) or "hispano" (a Spanish mission). */
		public String povo = "";

		void escrever(RegistryFriendlyByteBuf b) {
			b.writeUtf(id);
			b.writeUtf(nome);
			b.writeUtf(era);
			b.writeUtf(especialidade);
			b.writeUtf(simbolo);
			b.writeVarInt(x);
			b.writeVarInt(z);
			b.writeInt(cor);
			b.writeInt(cor2);
			b.writeVarInt(estagio);
			b.writeVarInt(populacao);
			b.writeVarInt(forca);
			b.writeBoolean(abandonada);
			b.writeBoolean(bandidos);
			b.writeVarInt(rep);
			b.writeVarInt(tensao);
			b.writeVarInt(estado);
			b.writeUtf(tratado);
			b.writeVarInt(dias);
			b.writeVarInt(tributo);
			b.writeBoolean(embargamos);
			b.writeBoolean(embargados);
			b.writeBoolean(batida);
			b.writeUtf(povo);
		}

		static Vila ler(RegistryFriendlyByteBuf b) {
			Vila v = new Vila();
			v.id = b.readUtf();
			v.nome = b.readUtf();
			v.era = b.readUtf();
			v.especialidade = b.readUtf();
			v.simbolo = b.readUtf();
			v.x = b.readVarInt();
			v.z = b.readVarInt();
			v.cor = b.readInt();
			v.cor2 = b.readInt();
			v.estagio = b.readVarInt();
			v.populacao = b.readVarInt();
			v.forca = b.readVarInt();
			v.abandonada = b.readBoolean();
			v.bandidos = b.readBoolean();
			v.rep = b.readVarInt();
			v.tensao = b.readVarInt();
			v.estado = b.readVarInt();
			v.tratado = b.readUtf();
			v.dias = b.readVarInt();
			v.tributo = b.readVarInt();
			v.embargamos = b.readBoolean();
			v.embargados = b.readBoolean();
			v.batida = b.readBoolean();
			v.povo = b.readUtf();
			return v;
		}
	}

	/** A claimed chunk: whose (index into {@link #vilas}), and whether another arraial claims it too. */
	public record Terra(int cx, int cz, int dono, boolean disputada) {
	}

	/** A road between two arraiais (indices); estado 0: used, 1: beaten road, 2: closed, 3: war, 4: embargo. */
	public record Rota(int a, int b, int estado) {
	}

	/** The open escrivaninha (diplomacy allowed) or 0 for the map item. */
	public boolean mesa;
	public long escrivaninha;
	/** Index of the player's arraial in {@link #vilas}, or -1. */
	public int meu = -1;
	/** The grid: its north-west corner, the size of a cell in blocks, and its width and height in cells. */
	public int x0, z0, celula = 16, w, h;
	public byte[] bioma = new byte[0];
	public int jx, jz;
	public boolean temBase, baseOcupada;
	public int bx, bz;
	public boolean podeHoje;
	public int papel;
	public List<Vila> vilas = new ArrayList<>();
	public List<Terra> terras = new ArrayList<>();
	public List<Rota> rotas = new ArrayList<>();
	public Component mensagem = Component.empty();

	public void escrever(RegistryFriendlyByteBuf b) {
		b.writeBoolean(mesa);
		b.writeLong(escrivaninha);
		b.writeVarInt(meu);
		b.writeInt(x0);
		b.writeInt(z0);
		b.writeVarInt(celula);
		b.writeVarInt(w);
		b.writeVarInt(h);
		b.writeByteArray(bioma);
		b.writeInt(jx);
		b.writeInt(jz);
		b.writeBoolean(temBase);
		b.writeBoolean(baseOcupada);
		b.writeInt(bx);
		b.writeInt(bz);
		b.writeBoolean(podeHoje);
		b.writeVarInt(papel);
		b.writeVarInt(vilas.size());
		vilas.forEach(v -> v.escrever(b));
		b.writeVarInt(terras.size());
		for (Terra t : terras) {
			b.writeVarInt(t.cx());
			b.writeVarInt(t.cz());
			b.writeVarInt(t.dono());
			b.writeBoolean(t.disputada());
		}
		b.writeVarInt(rotas.size());
		for (Rota r : rotas) {
			b.writeVarInt(r.a());
			b.writeVarInt(r.b());
			b.writeVarInt(r.estado());
		}
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(b, mensagem);
	}

	public static MapaDados ler(RegistryFriendlyByteBuf b) {
		MapaDados d = new MapaDados();
		d.mesa = b.readBoolean();
		d.escrivaninha = b.readLong();
		d.meu = b.readVarInt();
		d.x0 = b.readInt();
		d.z0 = b.readInt();
		d.celula = b.readVarInt();
		d.w = b.readVarInt();
		d.h = b.readVarInt();
		d.bioma = b.readByteArray();
		d.jx = b.readInt();
		d.jz = b.readInt();
		d.temBase = b.readBoolean();
		d.baseOcupada = b.readBoolean();
		d.bx = b.readInt();
		d.bz = b.readInt();
		d.podeHoje = b.readBoolean();
		d.papel = b.readVarInt();
		int n = b.readVarInt();
		for (int i = 0; i < n; i++) {
			d.vilas.add(Vila.ler(b));
		}
		n = b.readVarInt();
		for (int i = 0; i < n; i++) {
			d.terras.add(new Terra(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean()));
		}
		n = b.readVarInt();
		for (int i = 0; i < n; i++) {
			d.rotas.add(new Rota(b.readVarInt(), b.readVarInt(), b.readVarInt()));
		}
		d.mensagem = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(b);
		return d;
	}

	/** Can the player's arraial propose this treaty to that neighbour? (same rules on both sides) */
	public static boolean podePropor(Vila v, String tipo, int forcaMinha) {
		if (v.abandonada) {
			return false;
		}
		boolean guerra = v.estado == 2;
		return switch (tipo) {
			case "paz" -> guerra;
			case "fronteira" -> v.estado == 1 && !v.tratado.equals("fronteira");
			case "comercio" -> !guerra && !v.tratado.equals("comercio") && !v.tratado.equals("alianca") && !v.embargamos;
			case "alianca" -> !guerra && v.rep >= 20 && !v.tratado.equals("alianca") && !v.embargamos;
			case "tributo" -> !guerra && v.tratado.isEmpty() && forcaMinha * 2 >= v.forca * 3;
			default -> false;
		};
	}
}
