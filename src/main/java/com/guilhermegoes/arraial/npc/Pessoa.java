package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.registry.ModItems;
import com.guilhermegoes.arraial.vila.ArraialBlockEntity;
import com.guilhermegoes.arraial.vila.Cronica;
import com.guilhermegoes.arraial.vila.Vilas;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A morador's personal life (beyond work and sleep): who they are and what they need.
 * <ul>
 * <li><b>Personality</b>: two traits (cheerful, grumpy, hard-working, lazy, shy, quarrelsome,
 * generous, greedy for food, devout, curious), a favourite food and a favourite pastime.</li>
 * <li><b>Needs</b>: food (the armazém), rest (energy drops by day, faster for the lazy, and comes
 * back sleeping; the exhausted stop to rest) and company (it drops alone; chats and festas fill it).</li>
 * <li><b>Routine</b>: at the end of the afternoon, work stops for leisure: a stroll, a prayer at
 * the Cruzeiro, a chat, sitting by the water or the fire.</li>
 * <li><b>Friendships and rivalries</b> among moradores grow chat by chat, by how their traits get on;
 * rivals quarrel instead of chatting.</li>
 * <li><b>A dream</b> of their own (a house, a trade, a friend, a festa, their favourite food); when
 * it comes true, they are happier and the Crônica tells it.</li>
 * <li><b>Friendship with the player</b>: talking, gifts (the favourite above all) and small social
 * missions (bring me something, take this note to someone, make peace for me with my rival).
 * Close friends confide, and give presents.</li>
 * </ul>
 */
public final class Pessoa {
	static final List<String> TRACOS = List.of("alegre", "ranzinza", "trabalhador", "preguicoso", "timido", "brigao", "generoso", "guloso", "devoto", "curioso");
	private static final List<String> OPOSTOS = List.of("alegre:ranzinza", "trabalhador:preguicoso", "timido:brigao");
	private static final List<String> COMIDAS = List.of("minecraft:bread", "arraial:beiju", "arraial:pacoca", "arraial:rapadura",
			"minecraft:cooked_beef", "minecraft:cooked_cod", "arraial:pe_de_moleque", "minecraft:apple", "minecraft:sweet_berries", "minecraft:cooked_chicken");
	static final List<String> LAZERES = List.of("passear", "rezar", "conversar", "agua", "fogueira");
	private static final List<String> SONHOS = List.of("casa", "oficio", "amigo", "festa", "favorita");

	final List<String> tracos = new ArrayList<>();
	String comida = "";
	String lazer = "";
	String sonho = "";
	boolean sonhoRealizado;
	int energia = 80;
	int convivio = 60;
	/** Feelings toward other moradores (-100..100). */
	final Map<UUID, Integer> afinidade = new HashMap<>();
	/** Friendship with each player (0..100) and the last day they talked. */
	final Map<UUID, Integer> amizade = new HashMap<>();
	final Map<UUID, Long> ultimoPapo = new HashMap<>();
	/** The mission given to a player: "tipo;jogador;alvo;qtd;prazo" (empty when none). */
	String missao = "";
	boolean viuFesta;
	boolean comeuFavorita;
	boolean presenteou;

	// ================================================================ who they are

	void gerar(RandomSource r) {
		while (tracos.size() < 2) {
			String t = TRACOS.get(r.nextInt(TRACOS.size()));
			boolean oposto = tracos.stream().anyMatch(o -> OPOSTOS.contains(o + ":" + t) || OPOSTOS.contains(t + ":" + o));
			if (!tracos.contains(t) && !oposto) {
				tracos.add(t);
			}
		}
		comida = COMIDAS.get(r.nextInt(COMIDAS.size()));
		lazer = LAZERES.get(r.nextInt(LAZERES.size()));
		sonho = SONHOS.get(r.nextInt(SONHOS.size()));
	}

	public boolean tem(String traco) {
		return tracos.contains(traco);
	}

	public String lazer() {
		return lazer;
	}

	public @Nullable Item favorita() {
		Identifier id = Identifier.tryParse(comida);
		return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
	}

	public boolean cansado() {
		return energia < 15;
	}

	public int afinidadeCom(MoradorEntity outro) {
		return afinidade.getOrDefault(outro.getUUID(), 0);
	}

	public int amizadeCom(UUID jogador) {
		return amizade.getOrDefault(jogador, 0);
	}

	/** 0..100: rest, company, hunger, health, a home, the dream, temperament. */
	public int humor(MoradorEntity m) {
		double h = 50 + (energia - 50) / 3.0 + (convivio - 50) / 3.0;
		if (m.comFome()) {
			h -= 25;
		}
		if (m.doente()) {
			h -= 15;
		}
		if (m.casa() == null) {
			h -= 10;
		}
		if (sonhoRealizado) {
			h += 10;
		}
		if (tem("alegre")) {
			h += 8;
		}
		if (tem("ranzinza")) {
			h -= 8;
		}
		return (int) Math.max(0, Math.min(100, Math.round(h)));
	}

	// ================================================================ the passing of time

	/** Every 30 s: tiredness, loneliness, the dream. */
	void passar(ServerLevel level, MoradorEntity m) {
		if (m.isSleeping()) {
			energia = Math.min(100, energia + 12);
		} else if (level.isBrightOutside()) {
			energia -= tem("preguicoso") ? 3 : tem("trabalhador") ? 1 : 2;
		}
		convivio -= tem("timido") ? 1 : 2;
		energia = Math.max(0, energia);
		convivio = Math.max(0, Math.min(100, convivio));
		if (!sonhoRealizado && sonhoCumprido(m)) {
			sonhoRealizado = true;
			Cronica.anotar(level, m.vila(), Component.translatable("arraial.cronica.sonho", m.nome(), Component.translatable("arraial.sonho." + sonho)));
			m.dizer(Component.translatable("arraial.fala.sonho"), 80);
		}
	}

	private boolean sonhoCumprido(MoradorEntity m) {
		return switch (sonho) {
			case "casa" -> m.casa() != null;
			case "oficio" -> m.profissao() != Profissao.NENHUMA;
			case "amigo" -> afinidade.values().stream().anyMatch(v -> v >= 40);
			case "festa" -> viuFesta;
			case "favorita" -> comeuFavorita;
			default -> false;
		};
	}

	/** Two moradores chatted: company, and feelings move by how their traits get on. */
	static void conversaram(MoradorEntity a, MoradorEntity b, RandomSource r) {
		int compat = compatibilidade(a.pessoa(), b.pessoa());
		int delta = 3 + compat * 2 + r.nextInt(5) - 2;
		mudarAfinidade(a, b, delta);
		boolean amigos = a.pessoa().afinidadeCom(b) >= 40;
		boolean rivais = a.pessoa().afinidadeCom(b) <= -40;
		for (MoradorEntity m : new MoradorEntity[] {a, b}) {
			m.pessoa().convivio = Math.min(100, m.pessoa().convivio + (rivais ? -5 : amigos ? 25 : 15));
		}
	}

	static void mudarAfinidade(MoradorEntity a, MoradorEntity b, int delta) {
		int antes = a.pessoa().afinidadeCom(b);
		int depois = Math.max(-100, Math.min(100, antes + delta));
		a.pessoa().afinidade.put(b.getUUID(), depois);
		b.pessoa().afinidade.put(a.getUUID(), depois);
		if (a.level() instanceof ServerLevel level) {
			if (antes < 40 && depois >= 40) {
				Cronica.anotar(level, a.vila(), Component.translatable("arraial.cronica.amizade", a.nome(), b.nome()));
			} else if (antes > -40 && depois <= -40) {
				Cronica.anotar(level, a.vila(), Component.translatable("arraial.cronica.rivalidade", a.nome(), b.nome()));
			}
		}
	}

	private static int compatibilidade(Pessoa a, Pessoa b) {
		int c = 0;
		for (String t : a.tracos) {
			if (b.tem(t)) {
				c += 2;
			}
		}
		for (Pessoa p : new Pessoa[] {a, b}) {
			if (p.tem("alegre") || p.tem("generoso")) {
				c += 1;
			}
			if (p.tem("ranzinza")) {
				c -= 1;
			}
			if (p.tem("brigao")) {
				c -= 2;
			}
		}
		if (a.tem("trabalhador") && b.tem("preguicoso") || b.tem("trabalhador") && a.tem("preguicoso")) {
			c -= 2;
		}
		return Math.max(-4, Math.min(4, c));
	}

	// ================================================================ the player

	/** A chat with the player (once a day counts toward friendship). */
	void conversouComJogador(ServerLevel level, MoradorEntity m, Player p) {
		long hoje = Vilas.dia(level);
		convivio = Math.min(100, convivio + 5);
		if (ultimoPapo.getOrDefault(p.getUUID(), -1L) != hoje) {
			ultimoPapo.put(p.getUUID(), hoje);
			amizade(level, m, p, tem("timido") ? 1 : 2);
		}
	}

	/** Friendship with the player: milestones bring confidences and presents. */
	void amizade(ServerLevel level, MoradorEntity m, Player p, int delta) {
		int antes = amizadeCom(p.getUUID());
		int depois = Math.max(0, Math.min(100, antes + delta));
		amizade.put(p.getUUID(), depois);
		if (antes < 30 && depois >= 30) {
			if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
				com.guilhermegoes.arraial.vila.Conquistas.dar(sp, "compadre");
			}
			p.sendSystemMessage(Component.translatable("arraial.pessoa.compadre", m.nome(),
					Component.translatable(m.feminino() ? "arraial.pessoa.comadre_t" : "arraial.pessoa.compadre_t")).withStyle(ChatFormatting.GOLD));
		}
		if (antes < 60 && depois >= 60 && !presenteou) {
			presenteou = true;
			ItemStack presente = presente(m);
			if (!p.getInventory().add(presente)) {
				net.minecraft.world.level.block.Block.popResource(level, p.blockPosition(), presente);
			}
			m.dizer(Component.translatable("arraial.fala.presente_amigo", p.getName()), 80);
			p.sendSystemMessage(Component.translatable("arraial.pessoa.presente", m.nome(), presente.getCount(), presente.getHoverName()).withStyle(ChatFormatting.GOLD));
		}
	}

	private static ItemStack presente(MoradorEntity m) {
		return switch (m.profissao()) {
			case ROCEIRO -> new ItemStack(Items.BREAD, 12);
			case LENHADOR -> new ItemStack(Items.OAK_LOG, 16);
			case OLEIRO -> new ItemStack(Items.BRICKS, 16);
			case PESCADOR -> new ItemStack(Items.COOKED_COD, 8);
			case FARINHEIRO -> new ItemStack(ModItems.BEIJU, 8);
			case BOTICARIO -> new ItemStack(ModItems.REMEDIO, 3);
			case CACADOR -> new ItemStack(Items.LEATHER, 8);
			case PASTOR -> new ItemStack(ModItems.QUEIJO_COALHO, 6);
			case MESTRE_ENGENHO -> new ItemStack(ModItems.RAPADURA, 8);
			case CAFEICULTOR -> new ItemStack(ModItems.CAFE_TORRADO, 6);
			case ENTALHADOR -> new ItemStack(com.guilhermegoes.arraial.registry.ModBlocks.TALHA_DOURADA, 2);
			case BARQUEIRO -> new ItemStack(Items.COOKED_SALMON, 8);
			case ESTIVADOR -> new ItemStack(ModItems.SAL, 6);
			case MOLEIRO -> new ItemStack(ModItems.FARINHA_TRIGO, 12);
			default -> new ItemStack(ModItems.REIS, 12);
		};
	}

	/** The personal lines of the morador's card. */
	List<Component> ficha(MoradorEntity m, Player p) {
		List<Component> l = new ArrayList<>();
		MutableComponent t = Component.translatable("arraial.pessoa.jeito");
		for (int i = 0; i < tracos.size(); i++) {
			t.append(i == 0 ? " " : ", ").append(Component.translatable("arraial.traco." + tracos.get(i) + (m.feminino() ? ".f" : ".m")));
		}
		Item fav = favorita();
		t.append(" · ").append(Component.translatable("arraial.pessoa.gosta", fav == null ? Component.literal("?") : fav.getDefaultInstance().getHoverName(),
				Component.translatable("arraial.lazer." + lazer)));
		l.add(t.withStyle(ChatFormatting.GRAY));
		int h = humor(m);
		l.add(Component.translatable("arraial.pessoa.humor", h, Component.translatable(energia < 30 ? "arraial.pessoa.cansado" : "arraial.pessoa.descansado"),
				Component.translatable(convivio < 30 ? "arraial.pessoa.sozinho" : "arraial.pessoa.acompanhado"))
				.withStyle(h < 35 ? ChatFormatting.RED : h > 65 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		int a = amizadeCom(p.getUUID());
		MutableComponent amz = Component.translatable("arraial.pessoa.amizade", a);
		if (a >= 30) {
			// Close friends confide their dream.
			amz.append(" · ").append(Component.translatable(sonhoRealizado ? "arraial.pessoa.sonho_feito" : "arraial.pessoa.sonho",
					Component.translatable("arraial.sonho." + sonho)));
		}
		l.add(amz.withStyle(ChatFormatting.AQUA));
		return l;
	}

	// ================================================================ social missions

	record Missao(String tipo, UUID jogador, String alvo, int qtd, long prazo) {
		static @Nullable Missao de(String s) {
			String[] p = s.split(";");
			if (p.length != 5) {
				return null;
			}
			try {
				return new Missao(p[0], UUID.fromString(p[1]), p[2], Integer.parseInt(p[3]), Long.parseLong(p[4]));
			} catch (IllegalArgumentException e) {
				return null;
			}
		}

		String texto() {
			return tipo + ";" + jogador + ";" + alvo + ";" + qtd + ";" + prazo;
		}
	}

	/** For the automated test: asks the player for 3 apples. */
	public void pedirFavorTeste(UUID jogador, long hoje) {
		missao = new Missao("trazer", jogador, "minecraft:apple", 3, hoje + 3).texto();
	}

	@Nullable Missao missao() {
		return Missao.de(missao);
	}

	/** Maybe asks the player for a small favour. */
	void oferecer(ServerLevel level, MoradorEntity m, Player p) {
		long hoje = Vilas.dia(level);
		Missao atual = missao();
		if (atual != null && hoje > atual.prazo()) {
			missao = "";
			atual = null;
		}
		if (atual != null || level.getRandom().nextFloat() > 0.35F) {
			return;
		}
		RandomSource r = level.getRandom();
		ArraialBlockEntity a = m.arraial();
		List<MoradorEntity> outros = a == null ? List.of() : a.moradores(level).stream().filter(o -> o != m).toList();
		MoradorEntity rival = outros.stream().filter(o -> afinidadeCom(o) <= -40).findFirst().orElse(null);
		Missao nova;
		if (rival != null && r.nextBoolean()) {
			nova = new Missao("pazes", p.getUUID(), rival.getUUID().toString(), 1, hoje + 3);
			m.dizer(Component.translatable("arraial.fala.missao.pazes", p.getName(), rival.nome()), 120);
		} else if (!outros.isEmpty() && r.nextBoolean()) {
			MoradorEntity dest = outros.stream().max(java.util.Comparator.comparingInt(this::afinidadeCom)).orElse(outros.get(0));
			nova = new Missao("recado", p.getUUID(), dest.getUUID().toString(), 1, hoje + 2);
			ItemStack bilhete = new ItemStack(Items.PAPER);
			bilhete.set(DataComponents.CUSTOM_NAME, Component.translatable("arraial.pessoa.bilhete", m.nome(), dest.nome()));
			if (!p.getInventory().add(bilhete)) {
				net.minecraft.world.level.block.Block.popResource(level, p.blockPosition(), bilhete);
			}
			m.dizer(Component.translatable("arraial.fala.missao.recado", p.getName(), dest.nome()), 120);
		} else {
			Item quer = r.nextBoolean() && favorita() != null ? favorita()
					: List.of(Items.TORCH, Items.POPPY, Items.APPLE, Items.HONEY_BOTTLE, Items.WOOL.white(), Items.LEATHER).get(r.nextInt(6));
			int qtd = quer == Items.TORCH ? 8 : quer == Items.HONEY_BOTTLE ? 1 : 3;
			nova = new Missao("trazer", p.getUUID(), BuiltInRegistries.ITEM.getKey(quer).toString(), qtd, hoje + 3);
			m.dizer(Component.translatable("arraial.fala.missao.trazer", p.getName(), qtd, quer.getDefaultInstance().getHoverName()), 120);
		}
		missao = nova.texto();
		p.sendSystemMessage(Component.literal("✦ ").append(Component.translatable("arraial.pessoa.missao_nova", m.nome())).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	/**
	 * The player clicked {@code m} holding {@code stack}: does that finish a mission (theirs, or
	 * someone else's note or peace offering)? Returns true if it did.
	 */
	static boolean cumprir(ServerLevel level, MoradorEntity m, Player p, ItemStack stack) {
		// Their own "bring me" mission.
		Missao minha = m.pessoa().missao();
		if (minha != null && minha.jogador().equals(p.getUUID()) && minha.tipo().equals("trazer")
				&& BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(minha.alvo()) && stack.getCount() >= minha.qtd()) {
			stack.shrink(minha.qtd());
			concluir(level, m, p);
			return true;
		}
		ArraialBlockEntity a = m.arraial();
		if (a == null) {
			return false;
		}
		for (MoradorEntity o : a.moradores(level)) {
			Missao mo = o.pessoa().missao();
			if (o == m || mo == null || !mo.jogador().equals(p.getUUID()) || !mo.alvo().equals(m.getUUID().toString())) {
				continue;
			}
			if (mo.tipo().equals("recado") && stack.is(Items.PAPER) && stack.has(DataComponents.CUSTOM_NAME)) {
				stack.shrink(1);
				m.dizer(Component.translatable("arraial.fala.recado_recebido", o.nome()), 80);
				mudarAfinidade(m, o, 10);
				concluir(level, o, p);
				return true;
			}
			if (mo.tipo().equals("pazes") && stack.has(DataComponents.FOOD)) {
				stack.shrink(1);
				m.dizer(Component.translatable("arraial.fala.pazes_feitas", o.nome()), 80);
				mudarAfinidade(m, o, 45);
				concluir(level, o, p);
				return true;
			}
		}
		return false;
	}

	private static void concluir(ServerLevel level, MoradorEntity m, Player p) {
		Pessoa ps = m.pessoa();
		ps.missao = "";
		ps.convivio = Math.min(100, ps.convivio + 10);
		ps.amizade(level, m, p, 20);
		int reis = 3 + level.getRandom().nextInt(4);
		ItemStack paga = new ItemStack(ModItems.REIS, reis);
		if (!p.getInventory().add(paga)) {
			net.minecraft.world.level.block.Block.popResource(level, p.blockPosition(), paga);
		}
		ArraialBlockEntity a = m.arraial();
		if (a != null) {
			a.mudarReputacao(p.getUUID(), 3);
		}
		m.dizer(Component.translatable("arraial.fala.missao.obrigado", p.getName()), 80);
		p.sendSystemMessage(Component.literal("✦ ").append(Component.translatable("arraial.pessoa.missao_feita", m.nome(), reis)).withStyle(ChatFormatting.GREEN));
		if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
			com.guilhermegoes.arraial.vila.Conquistas.dar(sp, "favor");
		}
		Cronica.anotar(level, m.vila(), Component.translatable("arraial.cronica.favor", p.getName().getString(), m.nome()));
	}

	/** The favourite food, as a gift: much friendship. */
	boolean favoritaDada(ServerLevel level, MoradorEntity m, Player p, ItemStack stack) {
		Item fav = favorita();
		if (fav == null || !stack.is(fav)) {
			return false;
		}
		stack.shrink(1);
		comeuFavorita = true;
		amizade(level, m, p, tem("guloso") ? 18 : 12);
		m.dizer(Component.translatable("arraial.fala.favorita", p.getName()), 80);
		return true;
	}

	// ================================================================ saving

	private static final Codec<Map<UUID, Integer>> MAPA = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT);
	private static final Codec<Map<UUID, Long>> MAPA_DIA = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG);

	void ler(ValueInput in) {
		tracos.clear();
		String t = in.getStringOr("Tracos", "");
		if (!t.isEmpty()) {
			tracos.addAll(List.of(t.split(",")));
		}
		comida = in.getStringOr("ComidaFavorita", "");
		lazer = in.getStringOr("Lazer", "");
		sonho = in.getStringOr("Sonho", "");
		sonhoRealizado = in.getBooleanOr("SonhoRealizado", false);
		energia = in.getIntOr("Energia", 80);
		convivio = in.getIntOr("Convivio", 60);
		afinidade.clear();
		in.read("Afinidade", MAPA).ifPresent(afinidade::putAll);
		amizade.clear();
		in.read("Amizade", MAPA).ifPresent(amizade::putAll);
		ultimoPapo.clear();
		in.read("UltimoPapo", MAPA_DIA).ifPresent(ultimoPapo::putAll);
		missao = in.getStringOr("Missao", "");
		viuFesta = in.getBooleanOr("ViuFesta", false);
		comeuFavorita = in.getBooleanOr("ComeuFavorita", false);
		presenteou = in.getBooleanOr("Presenteou", false);
	}

	void gravar(ValueOutput out) {
		out.putString("Tracos", String.join(",", tracos));
		out.putString("ComidaFavorita", comida);
		out.putString("Lazer", lazer);
		out.putString("Sonho", sonho);
		out.putBoolean("SonhoRealizado", sonhoRealizado);
		out.putInt("Energia", energia);
		out.putInt("Convivio", convivio);
		out.store("Afinidade", MAPA, Map.copyOf(afinidade));
		out.store("Amizade", MAPA, Map.copyOf(amizade));
		out.store("UltimoPapo", MAPA_DIA, Map.copyOf(ultimoPapo));
		out.putString("Missao", missao);
		out.putBoolean("ViuFesta", viuFesta);
		out.putBoolean("ComeuFavorita", comeuFavorita);
		out.putBoolean("Presenteou", presenteou);
	}
}
