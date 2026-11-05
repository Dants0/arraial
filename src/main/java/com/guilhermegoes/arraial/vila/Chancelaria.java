package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.rede.MapaDados;
import com.guilhermegoes.arraial.rede.Rede;
import com.guilhermegoes.arraial.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * The chancery: builds what the region map shows and carries out what the player decides at the
 * Escrivaninha's diplomacy screen (treaties, breaking them, embargoes).
 */
public final class Chancelaria {
	/** How far around the player's arraial (or the player) the map reaches. */
	private static final int ALCANCE = 1400;
	/** The grid is at most this many cells across. */
	private static final int CELULAS = 128;

	/** Opens the screen: at an escrivaninha (diplomacy) or from the map item ({@code mesa} null). */
	public static void abrir(ServerPlayer p, @Nullable BlockPos mesa, Component mensagem) {
		MapaDados d = dados((ServerLevel) p.level(), p, mesa);
		d.mensagem = mensagem;
		ServerPlayNetworking.send(p, new Rede.AbrirTela(d));
	}

	// ================================================================ what the map shows

	private static @Nullable String meuArraial(ServerLevel level, ServerPlayer p, @Nullable BlockPos mesa) {
		if (mesa != null) {
			BlockPos v = Vilas.maisProxima(level, mesa);
			return v == null ? null : Economia.id(level, v);
		}
		ChunkPos c = p.chunkPosition();
		List<String> donos = Territorio.donos(level.dimension().identifier().toString(), c.x(), c.z());
		if (!donos.isEmpty()) {
			return donos.get(0);
		}
		BlockPos v = Vilas.maisProxima(level, p.blockPosition());
		return v == null || v.distSqr(p.blockPosition()) > 300 * 300 ? null : Economia.id(level, v);
	}

	static MapaDados dados(ServerLevel level, ServerPlayer p, @Nullable BlockPos mesa) {
		MapaDados d = new MapaDados();
		String dim = level.dimension().identifier().toString();
		long hoje = Vilas.dia(level);
		String meu = meuArraial(level, p, mesa);
		Registro.Info eu = meu == null ? null : Registro.get(meu);
		BlockPos centro = eu != null ? eu.posicao() : p.blockPosition();
		d.mesa = mesa != null && eu != null;
		d.escrivaninha = mesa == null ? 0 : mesa.asLong();
		d.jx = p.getBlockX();
		d.jz = p.getBlockZ();

		// The arraiais of the region.
		List<String> ids = new ArrayList<>();
		Map<String, Integer> indice = new HashMap<>();
		int minX = Math.min(centro.getX(), d.jx), maxX = Math.max(centro.getX(), d.jx);
		int minZ = Math.min(centro.getZ(), d.jz), maxZ = Math.max(centro.getZ(), d.jz);
		for (Map.Entry<String, Registro.Info> e : Registro.todas().entrySet()) {
			Registro.Info v = e.getValue();
			if (!v.dimensao.equals(dim) || v.posicao().distSqr(centro) > (double) ALCANCE * ALCANCE) {
				continue;
			}
			indice.put(e.getKey(), ids.size());
			ids.add(e.getKey());
			minX = Math.min(minX, v.posicao().getX());
			maxX = Math.max(maxX, v.posicao().getX());
			minZ = Math.min(minZ, v.posicao().getZ());
			maxZ = Math.max(maxZ, v.posicao().getZ());
		}
		Bases.Base base = Bases.de(p.getUUID());
		if (base != null && base.dimensao.equals(dim)) {
			BlockPos b = BlockPos.of(base.pos);
			if (b.distSqr(centro) < (double) ALCANCE * ALCANCE) {
				d.temBase = true;
				d.baseOcupada = base.ocupada;
				d.bx = b.getX();
				d.bz = b.getZ();
				minX = Math.min(minX, d.bx);
				maxX = Math.max(maxX, d.bx);
				minZ = Math.min(minZ, d.bz);
				maxZ = Math.max(maxZ, d.bz);
			}
		}
		d.meu = meu == null ? -1 : indice.getOrDefault(meu, -1);

		// A square grid around all of it, with room for the land around the edges.
		int lado = Math.max(512, Math.max(maxX - minX, maxZ - minZ) + 2 * 176);
		d.celula = Math.max(4, (lado + CELULAS - 1) / CELULAS);
		d.w = d.h = (lado + d.celula - 1) / d.celula;
		d.x0 = (minX + maxX) / 2 - d.w * d.celula / 2;
		d.z0 = (minZ + maxZ) / 2 - d.h * d.celula / 2;
		d.bioma = biomas(level, d);

		for (String id : ids) {
			d.vilas.add(vila(id, Registro.get(id), meu, eu, hoje));
		}
		terras(dim, ids, indice, d);
		rotas(ids, indice, hoje, d);
		if (eu != null) {
			d.podeHoje = eu.ultimaProposta != hoje;
		}
		d.papel = p.getInventory().countItem(Items.PAPER) + (p.isCreative() ? 64 : 0);
		return d;
	}

	private static MapaDados.Vila vila(String id, Registro.Info v, @Nullable String meu, Registro.@Nullable Info eu, long hoje) {
		MapaDados.Vila m = new MapaDados.Vila();
		m.id = id;
		m.nome = v.nome;
		m.x = v.posicao().getX();
		m.z = v.posicao().getZ();
		m.cor = 0xFF000000 | DyeColor.byName(v.cor1, DyeColor.WHITE).getTextureDiffuseColor();
		m.cor2 = 0xFF000000 | DyeColor.byName(v.cor2, DyeColor.RED).getTextureDiffuseColor();
		m.simbolo = v.simbolo;
		m.estagio = v.estagio;
		m.populacao = v.populacao;
		m.forca = Territorio.forca(v);
		m.abandonada = v.abandonada;
		m.bandidos = v.ocupadaPor.equals("bandidos") || v.bandidos > 0;
		m.especialidade = v.especialidade;
		m.era = eraDe(v);
		if (eu != null && meu != null && !id.equals(meu)) {
			m.rep = eu.reputacaoCom(id);
			m.tensao = eu.tensao.getOrDefault(id, 0);
			m.estado = eu.guerras.containsKey(id) ? 2 : eu.disputas.contains(id) ? 1 : 0;
			Registro.Tratado t = Territorio.tratado(meu, id, hoje);
			if (t != null) {
				m.tratado = t.tipo;
				m.dias = (int) (t.ate - hoje);
				m.tributo = t.tributo;
			}
			m.embargamos = eu.embargos.contains(id);
			m.embargados = v.embargos.contains(meu);
			m.batida = Economia.batida(meu, id);
		}
		return m;
	}

	/** The era is kept on the arraial's block entity; the register only knows it by its stage. */
	private static String eraDe(Registro.Info v) {
		return v.era.isEmpty() ? Era.COLONIAL.id() : v.era;
	}

	private static byte[] biomas(ServerLevel level, MapaDados d) {
		var fonte = level.getChunkSource().getGenerator().getBiomeSource();
		var resolvedor = fonte.createCachingResolver(level.getChunkSource().randomState());
		byte[] b = new byte[d.w * d.h];
		int y = QuartPos.fromBlock(level.getSeaLevel());
		for (int j = 0; j < d.h; j++) {
			for (int i = 0; i < d.w; i++) {
				int x = d.x0 + i * d.celula + d.celula / 2, z = d.z0 + j * d.celula + d.celula / 2;
				var bioma = resolvedor.getNoiseBiome(QuartPos.fromBlock(x), y, QuartPos.fromBlock(z));
				b[j * d.w + i] = (byte) tipo(bioma.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains"));
			}
		}
		return b;
	}

	private static int tipo(String b) {
		if (b.contains("river")) {
			return MapaDados.RIO;
		}
		if (b.contains("ocean")) {
			return MapaDados.OCEANO;
		}
		if (b.contains("beach") || b.contains("shore")) {
			return MapaDados.PRAIA;
		}
		if (b.contains("desert")) {
			return MapaDados.DESERTO;
		}
		if (b.contains("badlands")) {
			return MapaDados.BADLANDS;
		}
		if (b.contains("savanna")) {
			return MapaDados.SAVANA;
		}
		if (b.contains("jungle") || b.contains("bamboo")) {
			return MapaDados.SELVA;
		}
		if (b.contains("swamp") || b.contains("mangrove")) {
			return MapaDados.PANTANO;
		}
		if (b.contains("snow") || b.contains("frozen") || b.contains("ice") || b.contains("grove") || b.contains("peaks")) {
			return MapaDados.NEVE;
		}
		if (b.contains("taiga")) {
			return MapaDados.TAIGA;
		}
		if (b.contains("forest") || b.contains("woodland") || b.contains("garden")) {
			return MapaDados.FLORESTA;
		}
		if (b.contains("windswept") || b.contains("hills") || b.contains("stony") || b.contains("mountain")) {
			return MapaDados.MONTANHA;
		}
		return MapaDados.CAMPO;
	}

	private static void terras(String dim, List<String> ids, Map<String, Integer> indice, MapaDados d) {
		Set<Long> vistas = new HashSet<>();
		for (String id : ids) {
			Registro.Info v = Registro.get(id);
			if (v == null || v.abandonada) {
				continue;
			}
			List<Long> candidatas = new ArrayList<>();
			int r = Territorio.raio(v), cx = v.posicao().getX() >> 4, cz = v.posicao().getZ() >> 4;
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					candidatas.add(ChunkPos.pack(cx + x, cz + z));
				}
			}
			for (long m : v.marcos) {
				for (int x = -1; x <= 1; x++) {
					for (int z = -1; z <= 1; z++) {
						candidatas.add(ChunkPos.pack(ChunkPos.getX(m) + x, ChunkPos.getZ(m) + z));
					}
				}
			}
			candidatas.addAll(v.anexos);
			for (long k : candidatas) {
				if (!vistas.add(k)) {
					continue;
				}
				List<String> donos = Territorio.donos(dim, ChunkPos.getX(k), ChunkPos.getZ(k));
				Integer dono = donos.isEmpty() ? null : indice.get(donos.get(0));
				if (dono != null) {
					d.terras.add(new MapaDados.Terra(ChunkPos.getX(k), ChunkPos.getZ(k), dono, donos.size() > 1));
				}
			}
		}
	}

	private static void rotas(List<String> ids, Map<String, Integer> indice, long hoje, MapaDados d) {
		Set<String> feitas = new HashSet<>();
		for (String a : ids) {
			Registro.Info va = Registro.get(a);
			Set<String> outros = new HashSet<>(va.rotas.keySet());
			outros.addAll(va.guerras.keySet());
			outros.addAll(va.embargos);
			outros.addAll(va.tratados.keySet());
			for (String b : outros) {
				Integer ib = indice.get(b);
				if (ib == null || !feitas.add(a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a)) {
					continue;
				}
				Registro.Rota r = va.rotas.get(b);
				int estado;
				if (va.guerras.containsKey(b)) {
					estado = 3;
				} else if (Economia.embargo(a, b)) {
					estado = 4;
				} else if (Economia.bloqueada(a, b, hoje)) {
					estado = 2;
				} else if (Economia.batida(a, b)) {
					estado = 1;
				} else if (r != null && r.viagens > 0 || Territorio.tratado(a, b, hoje) != null) {
					estado = 0;
				} else {
					continue;
				}
				d.rotas.add(new MapaDados.Rota(indice.get(a), ib, estado));
			}
		}
	}

	// ================================================================ what the player decides

	public static void agir(ServerPlayer p, Rede.Acao acao) {
		ServerLevel level = (ServerLevel) p.level();
		BlockPos mesa = BlockPos.of(acao.mesa());
		if (!level.getBlockState(mesa).is(ModBlocks.ESCRIVANINHA) || p.blockPosition().distSqr(mesa) > 16 * 16) {
			return;
		}
		BlockPos vila = Vilas.maisProxima(level, mesa);
		if (vila == null || !(level.getBlockEntity(vila) instanceof ArraialBlockEntity a)) {
			return;
		}
		String meu = Economia.id(level, vila), outro = acao.outro();
		Registro.Info eu = meu == null ? null : Registro.get(meu), o = Registro.get(outro);
		if (eu == null || o == null || outro.equals(meu) || o.abandonada) {
			return;
		}
		long hoje = Vilas.dia(level);
		Component msg;
		switch (acao.acao()) {
			case "embargo" -> msg = Territorio.embargar(level, vila, meu, eu, outro, o, true);
			case "romper" -> msg = Territorio.romper(level, vila, meu, eu, outro, o);
			default -> {
				MapaDados.Vila v = vila(outro, o, meu, eu, hoje);
				if (!MapaDados.podePropor(v, acao.acao(), Territorio.forca(eu))) {
					msg = Component.translatable("arraial.diplomacia.nao_cabe", Component.translatable("arraial.tratado." + acao.acao()), o.nome)
							.withStyle(ChatFormatting.GRAY);
				} else if (eu.ultimaProposta == hoje) {
					msg = Component.translatable("arraial.territorio.ja_hoje").withStyle(ChatFormatting.GRAY);
				} else if (!p.isCreative() && p.getInventory().countItem(Items.PAPER) == 0) {
					msg = Component.translatable("arraial.diplomacia.sem_papel").withStyle(ChatFormatting.GRAY);
				} else {
					if (!p.isCreative()) {
						tirarPapel(p);
					}
					eu.ultimaProposta = hoje;
					msg = Territorio.propor(level, a, p, meu, eu, outro, o, acao.acao());
				}
			}
		}
		Registro.salvar();
		p.sendSystemMessage(msg);
		abrir(p, mesa, msg);
	}

	private static void tirarPapel(ServerPlayer p) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.PAPER)) {
				s.shrink(1);
				return;
			}
		}
	}

	private Chancelaria() {
	}
}
