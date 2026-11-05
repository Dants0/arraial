package com.guilhermegoes.arraial.vila;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * Territory and its conflicts. Each arraial claims the chunks around its Cruzeiro (more as it grows)
 * plus those around its Marcos de Fronteira; chunks taken by treaty belong to it alone. Where two
 * claims meet, the land is disputed.
 * <p>Conflicts do not start at random: every day the <b>tension</b> between two neighbours moves with
 * the land they both claim, competition for the same resources (two mining or logging villages close
 * together), bad blood (low reputation) and robberies on the road between them; treaties calm it.
 * High tension opens a <b>dispute</b>, which sours relations day by day; when relations hit
 * {@link Diplomacia#INIMIGO} it is <b>war</b>: roads close and there are skirmishes (the stronger
 * side, by people, soldiers and border posts, usually wins; a loaded arraial that loses is raided by
 * the enemy's militia). After a few days the weaker side sues for peace: a treaty with tribute, and
 * the winner annexes the disputed land.
 * <p>Treaties: peace, trade (cheaper freight), alliance, border (the disputed land is split) and
 * tribute. Players negotiate them at the Escrivaninha, and the other side may say no.
 */
public final class Territorio {
	private static final int TENSAO_DISPUTA = 40;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					mostrarFronteira(p);
				}
			}
		});
	}

	// ================================================================ claims

	public static int raio(Registro.Info v) {
		return Math.max(2, Math.min(8, 2 + v.estagio + v.populacao / 6));
	}

	private static boolean reivindica(Registro.Info v, int cx, int cz) {
		if (v.abandonada) {
			return false;
		}
		BlockPos c = v.posicao();
		if (Math.max(Math.abs((c.getX() >> 4) - cx), Math.abs((c.getZ() >> 4) - cz)) <= raio(v)) {
			return true;
		}
		for (long m : v.marcos) {
			if (Math.max(Math.abs(ChunkPos.getX(m) - cx), Math.abs(ChunkPos.getZ(m) - cz)) <= 1) {
				return true;
			}
		}
		return v.anexos.contains(ChunkPos.pack(cx, cz));
	}

	/** Who claims this chunk (annexed land belongs only to whoever annexed it). */
	public static List<String> donos(String dim, int cx, int cz) {
		List<String> todos = new ArrayList<>();
		List<String> anexado = new ArrayList<>();
		long k = ChunkPos.pack(cx, cz);
		for (Map.Entry<String, Registro.Info> e : Registro.todas().entrySet()) {
			Registro.Info v = e.getValue();
			if (v.dimensao.equals(dim) && reivindica(v, cx, cz)) {
				todos.add(e.getKey());
				if (v.anexos.contains(k)) {
					anexado.add(e.getKey());
				}
			}
		}
		return anexado.isEmpty() ? todos : anexado;
	}

	/** The chunks both claim (and neither has annexed alone). */
	private static List<Long> sobreposicao(String dim, String a, Registro.Info va, String b, Registro.Info vb) {
		List<Long> l = new ArrayList<>();
		int r = raio(va) + 1;
		int cx = va.posicao().getX() >> 4, cz = va.posicao().getZ() >> 4;
		java.util.Set<Long> candidatos = new java.util.HashSet<>();
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				candidatos.add(ChunkPos.pack(cx + x, cz + z));
			}
		}
		for (long m : va.marcos) {
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					candidatos.add(ChunkPos.pack(ChunkPos.getX(m) + x, ChunkPos.getZ(m) + z));
				}
			}
		}
		for (long k : candidatos) {
			List<String> d = donos(dim, ChunkPos.getX(k), ChunkPos.getZ(k));
			if (d.contains(a) && d.contains(b)) {
				l.add(k);
			}
		}
		return l;
	}

	// ================================================================ the border, as you walk

	private static final Map<UUID, String> ULTIMO = new HashMap<>();

	private static void mostrarFronteira(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		ChunkPos c = p.chunkPosition();
		List<String> d = donos(level.dimension().identifier().toString(), c.x(), c.z());
		String chave = String.join("|", d);
		String antes = ULTIMO.put(p.getUUID(), chave);
		if (chave.equals(antes) || antes == null && d.isEmpty()) {
			return;
		}
		Component msg;
		if (d.isEmpty()) {
			msg = Component.translatable("arraial.territorio.ninguem").withStyle(ChatFormatting.GRAY);
		} else if (d.size() == 1) {
			Registro.Info v = Registro.get(d.get(0));
			msg = Component.literal("⚑ ").append(Component.translatable("arraial.territorio.de", v == null ? "?" : v.nome))
					.withStyle(Cultura.cor(v));
		} else {
			Registro.Info a = Registro.get(d.get(0)), b = Registro.get(d.get(1));
			msg = Component.literal("⚔ ").append(Component.translatable("arraial.territorio.disputada", a == null ? "?" : a.nome, b == null ? "?" : b.nome))
					.withStyle(ChatFormatting.RED);
		}
		p.sendOverlayMessage(msg);
	}

	// ================================================================ the day

	public static Registro.@Nullable Tratado tratado(String a, String b, long hoje) {
		Registro.Info v = Registro.get(a);
		Registro.Tratado t = v == null ? null : v.tratados.get(b);
		return t != null && t.ate >= hoje ? t : null;
	}

	public static boolean tem(String a, String b, String tipo, long hoje) {
		Registro.Tratado t = tratado(a, b, hoje);
		return t != null && t.tipo.equals(tipo);
	}

	/** Strength in a conflict: people, soldiers, border posts and the stage. */
	static int forca(Registro.Info v) {
		return v.populacao + 3 * v.oficios.getOrDefault("soldado", 0) + 2 * Math.min(3, v.marcos.size()) + 2 * v.estagio;
	}

	/** Once a day (from {@link Comercio#diaRegional}): tension, disputes, wars, skirmishes, peace and treaties. */
	static void diario(ServerLevel level, long hoje) {
		String dim = level.dimension().identifier().toString();
		List<String> ids = new ArrayList<>();
		Registro.todas().forEach((k, v) -> {
			if (v.dimensao.equals(dim) && !v.abandonada) {
				ids.add(k);
			}
		});
		RandomSource r = level.getRandom();
		for (int i = 0; i < ids.size(); i++) {
			for (int j = i + 1; j < ids.size(); j++) {
				par(level, ids.get(i), ids.get(j), hoje, r);
			}
		}
		for (String id : ids) {
			cumprirTratados(level, id, hoje);
		}
	}

	private static void par(ServerLevel level, String a, String b, long hoje, RandomSource r) {
		Registro.Info va = Registro.get(a), vb = Registro.get(b);
		if (va == null || vb == null) {
			return;
		}
		double dist = Math.sqrt(va.posicao().distSqr(vb.posicao()));
		if (dist > 600) {
			return;
		}
		String dim = va.dimensao;
		List<Long> comum = sobreposicao(dim, a, va, b, vb);
		int rep = va.reputacaoCom(b);
		int t = va.tensao.getOrDefault(b, 0);
		int delta = -1;
		String motivo = "";
		if (!comum.isEmpty()) {
			delta += 1 + comum.size() / 6;
			motivo = "fronteira";
		}
		Economia.Especialidade ea = Economia.Especialidade.de(va.especialidade), eb = Economia.Especialidade.de(vb.especialidade);
		if (ea == eb && dist < 400 && (ea == Economia.Especialidade.MINERADORA || ea == Economia.Especialidade.MADEIREIRA
				|| ea == Economia.Especialidade.PESQUEIRA)) {
			delta += 1;
			if (motivo.isEmpty()) {
				motivo = "recursos";
			}
		}
		if (rep < 0) {
			delta += rep < -30 ? 2 : 1;
			if (motivo.isEmpty()) {
				motivo = "rixa";
			}
		}
		Registro.Rota rota = va.rotas.get(b);
		if (rota != null && hoje - rota.ultimoRoubo < 5) {
			delta += 1;
		}
		if (tratado(a, b, hoje) != null) {
			delta -= 3;
		}
		boolean embargo = va.embargos.contains(b) || vb.embargos.contains(a);
		if (embargo) {
			delta += 1;
			Registro.mudarReputacao(a, b, -1);
		}
		t = Math.max(0, Math.min(100, t + delta));
		va.tensao.put(b, t);
		vb.tensao.put(a, t);

		boolean disputa = va.disputas.contains(b);
		if (!disputa && t >= TENSAO_DISPUTA && tratado(a, b, hoje) == null && !motivo.isEmpty()) {
			va.disputas.add(b);
			vb.disputas.add(a);
			Mundo.historia(a, hoje, "arraial.historia.disputa." + motivo, vb.nome, String.valueOf(comum.size()));
			Mundo.historia(b, hoje, "arraial.historia.disputa." + motivo, va.nome, String.valueOf(comum.size()));
			avisarCarregadas(level, a, b, Component.translatable("arraial.territorio.aviso_disputa", va.nome, vb.nome));
		} else if (disputa) {
			Registro.mudarReputacao(a, b, -2);
			if (t < 15) {
				va.disputas.remove(b);
				vb.disputas.remove(a);
				Mundo.historia(a, hoje, "arraial.historia.disputa_fim", vb.nome);
				Mundo.historia(b, hoje, "arraial.historia.disputa_fim", va.nome);
			}
		}

		embargos(level, a, va, b, vb, t, hoje, r);

		// War.
		rep = va.reputacaoCom(b);
		boolean guerra = va.guerras.containsKey(b);
		if (!guerra && rep <= Diplomacia.INIMIGO) {
			va.guerras.put(b, hoje);
			vb.guerras.put(a, hoje);
			Mundo.historia(a, hoje, "arraial.historia.guerra", vb.nome);
			Mundo.historia(b, hoje, "arraial.historia.guerra", va.nome);
			avisarCarregadas(level, a, b, Component.literal("⚔ ").append(Component.translatable("arraial.territorio.aviso_guerra", va.nome, vb.nome)));
		} else if (guerra && rep > Diplomacia.INIMIGO) {
			va.guerras.remove(b);
			vb.guerras.remove(a);
		} else if (guerra) {
			if (r.nextFloat() < 0.4F) {
				escaramuca(level, a, va, b, vb, hoje, r);
			}
			if (hoje - va.guerras.get(b) >= 3 && r.nextFloat() < 0.3F) {
				int fa = forca(va), fb = forca(vb);
				paz(level, fa >= fb ? a : b, fa >= fb ? b : a, hoje, comum);
			}
		}
	}

	/**
	 * Autonomous arraiais use embargoes too: in a dispute the stronger side may cut trade; when tempers
	 * cool, they lift it. (The player's own arraial only embargoes or lifts by the player's hand.)
	 */
	private static void embargos(ServerLevel level, String a, Registro.Info va, String b, Registro.Info vb, int t, long hoje, RandomSource r) {
		boolean disputa = va.disputas.contains(b);
		for (int lado = 0; lado < 2; lado++) {
			String eu = lado == 0 ? a : b, outro = lado == 0 ? b : a;
			Registro.Info ve = lado == 0 ? va : vb, vo = lado == 0 ? vb : va;
			if (!ve.autonoma) {
				continue;
			}
			if (!ve.embargos.contains(outro) && disputa && forca(ve) >= forca(vo) && r.nextFloat() < 0.08F) {
				Component msg = embargar(level, ve.posicao(), eu, ve, outro, vo, false);
				Mundo.historia(eu, hoje, "arraial.historia.embargamos", vo.nome);
				avisarCarregadas(level, eu, outro, msg);
			} else if (ve.embargos.contains(outro) && t < 20 && r.nextFloat() < 0.15F) {
				Component msg = embargar(level, ve.posicao(), eu, ve, outro, vo, false);
				avisarCarregadas(level, eu, outro, msg);
			}
		}
	}

	private static void escaramuca(ServerLevel level, String a, Registro.Info va, String b, Registro.Info vb, long hoje, RandomSource r) {
		int fa = forca(va), fb = forca(vb);
		boolean aVence = r.nextInt(fa + fb + 1) < fa;
		String perdedor = aVence ? b : a, vencedor = aVence ? a : b;
		Registro.Info vp = aVence ? vb : va, vv = aVence ? va : vb;
		Mundo.historia(perdedor, hoje, "arraial.historia.escaramuca_perdeu", vv.nome);
		Mundo.historia(vencedor, hoje, "arraial.historia.escaramuca_venceu", vp.nome);
		if (Mundo.carregada(level, vp) && level.getBlockEntity(vp.posicao()) instanceof ArraialBlockEntity arraial) {
			// The enemy's militia comes at night like bandits.
			if (arraial.crise.tipo() == null) {
				Crise.comecar(level, arraial, Crise.Tipo.SALTEADORES);
			}
			Prefeitura.avisar(level, arraial, Component.literal("⚔ ").append(Component.translatable("arraial.territorio.milicia", vv.nome))
					.withStyle(ChatFormatting.RED));
		} else {
			if (r.nextFloat() < 0.25F && vp.populacao > 1) {
				vp.populacao--;
				vp.saldoMoradores--;
			}
			for (Map.Entry<String, Integer> e : new ArrayList<>(vp.estoque.entrySet())) {
				Mundo.somar(vp, e.getKey(), -e.getValue() / 10);
			}
			int perda = vp.tesouro / 10;
			vp.tesouro -= perda;
			vp.tesouroPendente -= perda;
		}
	}

	/** The weaker side sues for peace: tribute, and the winner keeps the disputed land. */
	private static void paz(ServerLevel level, String vencedor, String perdedor, long hoje, List<Long> comum) {
		Registro.Info vv = Registro.get(vencedor), vp = Registro.get(perdedor);
		if (vv == null || vp == null) {
			return;
		}
		int tributo = 2 + vp.populacao / 3;
		assinar(vencedor, perdedor, "paz", hoje + 12, tributo);
		for (long k : comum) {
			if (!vv.anexos.contains(k)) {
				vv.anexos.add(k);
			}
			vp.anexos.remove(k);
		}
		Mundo.historia(vencedor, hoje, "arraial.historia.paz_venceu", vp.nome, String.valueOf(tributo), String.valueOf(comum.size()));
		Mundo.historia(perdedor, hoje, "arraial.historia.paz_perdeu", vv.nome, String.valueOf(tributo), String.valueOf(comum.size()));
		avisarCarregadas(level, vencedor, perdedor, Component.literal("🕊 ").append(Component.translatable("arraial.territorio.aviso_paz", vv.nome, vp.nome)));
	}

	/** Signs a treaty for both sides; tribute > 0 means {@code b} pays {@code a}. */
	static void assinar(String a, String b, String tipo, long ate, int tributo) {
		Registro.Info va = Registro.get(a), vb = Registro.get(b);
		if (va == null || vb == null) {
			return;
		}
		Registro.Tratado ta = new Registro.Tratado(), tb = new Registro.Tratado();
		ta.tipo = tb.tipo = tipo;
		ta.ate = tb.ate = ate;
		ta.tributo = tributo;
		tb.tributo = -tributo;
		va.tratados.put(b, ta);
		vb.tratados.put(a, tb);
		va.guerras.remove(b);
		vb.guerras.remove(a);
		va.disputas.remove(b);
		vb.disputas.remove(a);
		va.tensao.put(b, 10);
		vb.tensao.put(a, 10);
		va.embargos.remove(b);
		vb.embargos.remove(a);
		int rep = va.reputacaoCom(b);
		int alvo = switch (tipo) {
			case "paz" -> Math.max(rep, -20);
			case "tributo" -> rep - 5;
			case "alianca" -> Math.max(rep, Diplomacia.ALIADO);
			default -> Math.max(rep, 0);
		};
		Registro.mudarReputacao(a, b, alvo - rep);
	}

	private static void cumprirTratados(ServerLevel level, String id, long hoje) {
		Registro.Info v = Registro.get(id);
		v.tratados.entrySet().removeIf(e -> {
			Registro.Tratado t = e.getValue();
			Registro.Info o = Registro.get(e.getKey());
			if (o == null) {
				return true;
			}
			if (hoje > t.ate) {
				Mundo.historia(id, hoje, "arraial.historia.tratado_fim", Component.translatable("arraial.tratado." + t.tipo).getString(), o.nome);
				return true;
			}
			if (t.tributo < 0) {
				// We pay: from the armazém's coffers when loaded.
				int paga = -t.tributo;
				if (Mundo.carregada(level, v) && level.getBlockEntity(v.posicao()) instanceof ArraialBlockEntity a) {
					paga = Math.min(paga, a.tesouro);
					a.mudarTesouro(-paga);
				} else {
					paga = Math.min(paga, Math.max(0, v.tesouro));
					v.tesouro -= paga;
					v.tesouroPendente -= paga;
				}
				if (Mundo.carregada(level, o) && level.getBlockEntity(o.posicao()) instanceof ArraialBlockEntity b) {
					b.mudarTesouro(paga);
				} else {
					o.tesouro += paga;
					o.tesouroPendente += paga;
				}
			}
			if (t.tipo.equals("comercio") || t.tipo.equals("alianca")) {
				v.reputacao.merge(e.getKey(), 1, (x, y) -> Math.min(100, x + y));
			}
			return false;
		});
	}

	private static void avisarCarregadas(ServerLevel level, String a, String b, Component msg) {
		for (String id : new String[] {a, b}) {
			Registro.Info v = Registro.get(id);
			if (v != null && Mundo.carregada(level, v) && level.getBlockEntity(v.posicao()) instanceof ArraialBlockEntity arraial) {
				Prefeitura.avisar(level, arraial, msg.copy().withStyle(ChatFormatting.GOLD));
			}
		}
	}

	// ================================================================ the Marco de Fronteira

	/** A Marco placed: it extends the territory of the arraial whose land is closest. */
	public static void marcoColocado(ServerLevel level, BlockPos pos, @Nullable Player p) {
		String dim = level.dimension().identifier().toString();
		int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
		String melhor = null;
		int menor = Integer.MAX_VALUE;
		for (Map.Entry<String, Registro.Info> e : Registro.todas().entrySet()) {
			Registro.Info v = e.getValue();
			if (!v.dimensao.equals(dim) || v.abandonada) {
				continue;
			}
			// Distance in chunks to its land (must touch it, within 2 chunks).
			int d = Math.max(Math.abs((v.posicao().getX() >> 4) - cx), Math.abs((v.posicao().getZ() >> 4) - cz)) - raio(v);
			for (long m : v.marcos) {
				d = Math.min(d, Math.max(Math.abs(ChunkPos.getX(m) - cx), Math.abs(ChunkPos.getZ(m) - cz)) - 1);
			}
			if (d <= 2 && d < menor) {
				menor = d;
				melhor = e.getKey();
			}
		}
		if (melhor == null) {
			if (p != null) {
				p.sendSystemMessage(Component.translatable("arraial.territorio.marco_longe").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		Registro.Info v = Registro.get(melhor);
		long k = ChunkPos.pack(cx, cz);
		if (!v.marcos.contains(k)) {
			v.marcos.add(k);
		}
		level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
		if (p != null) {
			p.sendSystemMessage(Component.literal("⚑ ").append(Component.translatable("arraial.territorio.marco", v.nome)).withStyle(ChatFormatting.GOLD));
		}
		Mundo.historia(melhor, Vilas.dia(level), "arraial.historia.marco");
	}

	public static void marcoRemovido(ServerLevel level, BlockPos pos) {
		long k = ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
		Registro.todas().values().forEach(v -> v.marcos.remove(k));
	}

	// ================================================================ diplomacy (the Escrivaninha's screen, see Chancelaria)

	/**
	 * The player's arraial proposes a treaty to a neighbour. The other side weighs relations, the
	 * balance of strength, how long a war has gone on and the player's standing; it may say no.
	 * Tribute is demanded rather than offered: only a much stronger arraial gets it, and a refusal
	 * hurts.
	 */
	public static Component propor(ServerLevel level, ArraialBlockEntity a, Player p, String meu, Registro.Info eu, String outro,
			Registro.Info o, String tipo) {
		long hoje = Vilas.dia(level);
		BlockPos vila = a.getBlockPos();
		int rep = eu.reputacaoCom(outro);
		double razao = forca(eu) / (double) Math.max(1, forca(o));
		double chance = 0.3 + rep / 150.0 + (razao - 1) * 0.3 + a.reputacao(p.getUUID()) / 200.0;
		switch (tipo) {
			case "paz" -> chance += (hoje - eu.guerras.getOrDefault(outro, hoje)) * 0.05;
			case "alianca" -> chance -= 0.2;
			case "tributo" -> chance = 0.05 + (razao - 1.5) * 0.4 + Math.min(0, rep) / 200.0;
			default -> {
			}
		}
		if (Cultura.costume(o, "desconfiados")) {
			chance -= 0.1;
		} else if (Cultura.costume(o, "hospitaleiros") || Cultura.costume(o, "comerciantes") && tipo.equals("comercio")) {
			chance += 0.1;
		}
		Component nomeTratado = Component.translatable("arraial.tratado." + tipo);
		if (level.getRandom().nextDouble() >= chance) {
			if (tipo.equals("tributo")) {
				// An insult: tempers rise.
				Registro.mudarReputacao(meu, outro, -6);
				eu.tensao.merge(outro, 15, (x, y) -> Math.min(100, x + y));
				o.tensao.merge(meu, 15, (x, y) -> Math.min(100, x + y));
			} else {
				Registro.mudarReputacao(meu, outro, -2);
			}
			level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 1.0F, 1.0F);
			return Component.translatable("arraial.territorio.recusou", o.nome, nomeTratado).withStyle(ChatFormatting.RED);
		}
		int tributo = 0;
		if (tipo.equals("fronteira")) {
			dividir(level, meu, eu, outro, o);
		} else if (tipo.equals("tributo")) {
			tributo = 1 + o.populacao / 4;
		}
		assinar(meu, outro, tipo, hoje + (tipo.equals("alianca") ? 24 : tipo.equals("tributo") ? 12 : 16), tributo);
		Component texto = Component.translatable("arraial.cronica.tratado", nomeTratado, o.nome, p.getName().getString());
		Cronica.anotar(level, vila, texto);
		Mundo.historia(outro, hoje, "arraial.historia.tratado", nomeTratado.getString(), eu.nome);
		if (p instanceof ServerPlayer sp) {
			Conquistas.dar(sp, "pacificador");
		}
		a.mudarReputacao(p.getUUID(), tipo.equals("tributo") ? 2 : 5);
		level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 1.0F, 1.0F);
		return Component.literal("🕊 ").append(texto).withStyle(ChatFormatting.GREEN);
	}

	/** Breaks the treaty with a neighbour: they will not forget it. */
	public static Component romper(ServerLevel level, BlockPos vila, String meu, Registro.Info eu, String outro, Registro.Info o) {
		long hoje = Vilas.dia(level);
		Registro.Tratado t = tratado(meu, outro, hoje);
		if (t == null) {
			return Component.translatable("arraial.diplomacia.sem_tratado", o.nome).withStyle(ChatFormatting.GRAY);
		}
		Component nome = Component.translatable("arraial.tratado." + t.tipo);
		eu.tratados.remove(outro);
		o.tratados.remove(meu);
		Registro.mudarReputacao(meu, outro, -15);
		eu.tensao.merge(outro, 20, (x, y) -> Math.min(100, x + y));
		o.tensao.merge(meu, 20, (x, y) -> Math.min(100, x + y));
		Component texto = Component.translatable("arraial.cronica.rompeu", nome, o.nome);
		Cronica.anotar(level, vila, texto);
		Mundo.historia(outro, hoje, "arraial.historia.rompeu", nome.getString(), eu.nome);
		return Component.literal("✂ ").append(texto).withStyle(ChatFormatting.GOLD);
	}

	/** Starts or lifts an embargo: no merchants, tropeiros or contracts between the two (prices feel it). */
	public static Component embargar(ServerLevel level, BlockPos vila, String meu, Registro.Info eu, String outro, Registro.Info o, boolean anunciar) {
		long hoje = Vilas.dia(level);
		Component texto;
		if (eu.embargos.remove(outro)) {
			Registro.mudarReputacao(meu, outro, 3);
			texto = Component.translatable("arraial.cronica.embargo_fim", eu.nome, o.nome);
			Mundo.historia(outro, hoje, "arraial.historia.embargo_fim", eu.nome);
		} else {
			eu.embargos.add(outro);
			Registro.Tratado t = tratado(meu, outro, hoje);
			if (t != null && (t.tipo.equals("comercio") || t.tipo.equals("alianca"))) {
				eu.tratados.remove(outro); // an embargo ends any trade between them
				o.tratados.remove(meu);
			}
			eu.contratos.removeIf(c -> c.fornecedor.equals(outro));
			o.contratos.removeIf(c -> c.fornecedor.equals(meu));
			Registro.mudarReputacao(meu, outro, -8);
			eu.tensao.merge(outro, 10, (x, y) -> Math.min(100, x + y));
			o.tensao.merge(meu, 10, (x, y) -> Math.min(100, x + y));
			texto = Component.translatable("arraial.cronica.embargo", eu.nome, o.nome);
			Mundo.historia(outro, hoje, "arraial.historia.embargo", eu.nome);
		}
		if (anunciar) {
			Cronica.anotar(level, vila, texto);
		}
		return Component.literal("⛔ ").append(texto).withStyle(ChatFormatting.GOLD);
	}

	public static MutableComponent linhaVizinho(String meu, Registro.Info eu, String k, Registro.Info o, long hoje, boolean escolhido) {
		int rep = eu.reputacaoCom(k);
		MutableComponent l = Component.literal(escolhido ? " ▶ " : " • ").append(o.nome)
				.append(Component.translatable("arraial.territorio.linha", rep, eu.tensao.getOrDefault(k, 0), forca(o)));
		if (eu.guerras.containsKey(k)) {
			l.append(Component.translatable("arraial.territorio.em_guerra"));
		} else if (eu.disputas.contains(k)) {
			l.append(Component.translatable("arraial.territorio.em_disputa"));
		}
		Registro.Tratado t = tratado(meu, k, hoje);
		if (t != null) {
			l.append(Component.translatable("arraial.territorio.com_tratado", Component.translatable("arraial.tratado." + t.tipo), t.ate - hoje));
			if (t.tributo != 0) {
				l.append(Component.translatable(t.tributo > 0 ? "arraial.territorio.recebe_tributo" : "arraial.territorio.paga_tributo", Math.abs(t.tributo)));
			}
		}
		return l.withStyle(eu.guerras.containsKey(k) ? ChatFormatting.RED : escolhido ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
	}

	/** Border treaty: each side keeps the disputed chunks closer to its Cruzeiro. */
	private static void dividir(ServerLevel level, String a, Registro.Info va, String b, Registro.Info vb) {
		for (long k : sobreposicao(va.dimensao, a, va, b, vb)) {
			BlockPos centro = new BlockPos((ChunkPos.getX(k) << 4) + 8, 64, (ChunkPos.getZ(k) << 4) + 8);
			boolean deA = centro.distSqr(va.posicao().atY(64)) <= centro.distSqr(vb.posicao().atY(64));
			(deA ? va : vb).anexos.add(k);
		}
	}

	private Territorio() {
	}
}
