package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import com.guilhermegoes.arraial.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * The arraial itself, stored in its Cruzeiro: its name and when the next newcomer arrives.
 * Every few seconds it counts beds and moradores and, if a bed is free, welcomes someone new.
 */
public class ArraialBlockEntity extends BlockEntity {
	private String nome = "";
	private long proximaChegada = 0;
	private final java.util.ArrayList<Cronica.Entrada> cronica = new java.util.ArrayList<>();
	/** The elected prefeito (a morador's UUID), managed by {@link Prefeitura}. */
	java.util.@org.jspecify.annotations.Nullable UUID prefeito;
	/** Last day the prefeito made the morning rounds. */
	long ultimoDespacho = 0;
	/** What the prefeito wants but cannot do (shown at the Cruzeiro); empty when all is well. */
	Component pedido = Component.empty();
	/** The crisis the arraial is going through, if any (see {@link Crise}). */
	final Crise crise = new Crise();
	/** The festa of the day, if any (see {@link Festa}). */
	final Festa festa = new Festa();
	/** Council, proposals, taxes and the regime (see {@link Politica}). */
	final Politica politica = new Politica();
	/** Supply orders signed by players (see {@link Comercio}) and money owed to them. */
	final java.util.Map<java.util.UUID, String> encomendas = new java.util.HashMap<>();
	final java.util.Map<java.util.UUID, Integer> pagamentos = new java.util.HashMap<>();
	/** Each player's cultural recognition here (see {@link Cultura}). */
	final java.util.Map<java.util.UUID, Integer> cultura = new java.util.HashMap<>();

	public Politica politica() {
		return politica;
	}

	public Festa festa() {
		return festa;
	}
	/** Population at the last count (for the per-second crisis tick). */
	int populacao;

	public Crise crise() {
		return crise;
	}

	/** How content the moradores are (0..100), see {@link Satisfacao}. */
	int satisfacao = 60;
	/** How much the moradores trust the prefeito (0..100); too low and they elect another. */
	int legitimidade = 60;
	int diasLegitimidadeBaixa;
	/** Réis in the arraial's coffers (earned at the feira and by the tropeiro). */
	int tesouro = 20;
	/** Current stage (Povoado, Arraial, Vila...), see {@link Estagio}. */
	Estagio estagio = Estagio.POVOADO;
	/** Local prices at the feira, in réis, smoothed day by day (see {@link Feira}). */
	final java.util.Map<String, Double> precos = new java.util.HashMap<>();
	/** Reputation of each player with this arraial (-100..100). */
	final java.util.Map<java.util.UUID, Integer> reputacao = new java.util.HashMap<>();
	/** Founded by the world (VilaNova), not by a player. */
	boolean autonoma;
	/** Nobody lives here any more (see {@link Mundo}); it waits for someone to bring it back. */
	boolean abandonada;
	/** Last day the ruins were looked after (see {@link Ruinas}); empty checks of a hideout (not saved). */
	long diaRuina;
	int esconderijoVazio;

	public boolean abandonada() {
		return abandonada;
	}

	/** Last game time this Cruzeiro ticked (to catch up after being unloaded). */
	long ultimoTick;

	public int satisfacao() {
		return satisfacao;
	}

	public int legitimidade() {
		return legitimidade;
	}

	public int tesouro() {
		return tesouro;
	}

	public Estagio estagio() {
		return estagio;
	}

	public int reputacao(java.util.UUID jogador) {
		return reputacao.getOrDefault(jogador, 0);
	}

	public void mudarReputacao(java.util.UUID jogador, int delta) {
		// Customs: the hospitable warm up to strangers faster, the wary slower.
		if (delta > 0 && level instanceof ServerLevel s) {
			if (Cultura.costume(s, this, "hospitaleiros")) {
				delta = (int) Math.ceil(delta * 1.5);
			} else if (Cultura.costume(s, this, "desconfiados")) {
				delta = Math.max(1, (int) Math.floor(delta * 0.6));
			}
		}
		reputacao.merge(jogador, delta, (a, b) -> Math.max(-100, Math.min(100, a + b)));
		setChanged();
	}

	public void mudarTesouro(int delta) {
		tesouro = Math.max(0, tesouro + delta);
		setChanged();
	}

	/** A market stall (banca de feira) inside the arraial. */
	public boolean temFeira(ServerLevel level) {
		return level.getPoiManager().getCountInRange(h -> h.value() == com.guilhermegoes.arraial.registry.ModPois.BANCA_FEIRA,
				worldPosition, Vilas.RAIO, PoiManager.Occupancy.ANY) > 0;
	}

	/** The name with the stage in front: "Vila de Santa Luzia". */
	public String nomeCompleto() {
		int i = nome.indexOf(' ');
		return i < 0 ? nome : estagio.prefixo() + nome.substring(i);
	}

	public java.util.@org.jspecify.annotations.Nullable UUID prefeito() {
		return prefeito;
	}

	public Component pedido() {
		return pedido;
	}

	public ArraialBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.ARRAIAL, pos, state);
	}

	public String nome() {
		return nome;
	}

	public List<Cronica.Entrada> cronica() {
		return java.util.Collections.unmodifiableList(cronica);
	}

	void anotar(Cronica.Entrada entrada) {
		cronica.add(entrada);
		while (cronica.size() > Cronica.MAXIMO) {
			cronica.remove(0);
		}
		setChanged();
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (!level.isClientSide()) {
			Vilas.registrar(level, worldPosition);
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (level != null) {
			Vilas.remover(level, worldPosition);
		}
	}

	public List<MoradorEntity> moradores(ServerLevel level) {
		return level.getEntitiesOfClass(MoradorEntity.class, new AABB(worldPosition).inflate(Vilas.RAIO),
				m -> worldPosition.equals(m.vila()));
	}

	public long camas(ServerLevel level) {
		return level.getPoiManager().getCountInRange(h -> h.is(PoiTypes.HOME), worldPosition, Vilas.RAIO, PoiManager.Occupancy.ANY);
	}

	/** Gives the arraial its name; called when the Cruzeiro is placed (or on first tick as a fallback). */
	void fundar(ServerLevel level) {
		if (nome.isEmpty()) {
			nome = Vilas.nomeAleatorio(level.getRandom());
			proximaChegada = level.getGameTime() + Vilas.INTERVALO_CHEGADA;
			anotar(new Cronica.Entrada(Vilas.dia(level), Component.translatable("arraial.cronica.fundacao", nomeCompleto())));
			setChanged();
		}
	}

	/** Workstations (cepo, balaio, forno) inside the arraial that nobody has claimed yet. */
	public long postosLivres(ServerLevel level) {
		return level.getPoiManager().getCountInRange(com.guilhermegoes.arraial.registry.ModPois::ePosto,
				worldPosition, Vilas.RAIO, PoiManager.Occupancy.HAS_SPACE);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, ArraialBlockEntity arraial) {
		if (!(level instanceof ServerLevel server) || server.getGameTime() % 20 != 0) {
			return;
		}
		Crise.tick(server, arraial, arraial.populacao);
		Festa.tick(server, arraial, arraial.populacao);
		if (server.getGameTime() % 100 != 0) {
			return;
		}
		arraial.fundar(server);

		List<MoradorEntity> moradores = arraial.moradores(server);
		arraial.populacao = moradores.size();
		Simulacao.recuperar(server, arraial, moradores);
		arraial.ultimoTick = server.getGameTime();
		arraial.diario(server, moradores);
		Prefeitura.cuidar(server, arraial, moradores);
		if (arraial.abandonada && !arraial.repovoar(server)) {
			Ruinas.tick(server, arraial);
			return;
		}
		long camasLivres = arraial.camas(server) - moradores.size();
		// Free work also draws people in, even with nowhere to sleep (they arrive homeless,
		// which pushes the player to build houses), but only while there are few homeless.
		long semCasa = moradores.stream().filter(m -> m.casa() == null).count();
		boolean atraiPorTrabalho = arraial.postosLivres(server) > 0 && semCasa < Vilas.MAX_SEM_CASA
				&& moradores.stream().noneMatch(m -> m.profissao() == Profissao.NENHUMA && m.gabinete() == null);
		if (camasLivres <= 0 && !atraiPorTrabalho) {
			// Nobody comes while there is nowhere to live or work; the clock starts when that changes.
			arraial.proximaChegada = Math.max(arraial.proximaChegada, server.getGameTime() + Vilas.INTERVALO_CHEGADA);
			return;
		}
		if (server.getGameTime() >= arraial.proximaChegada) {
			arraial.receberMorador(server);
			// A happy arraial is talked about: newcomers come twice as fast.
			arraial.proximaChegada = server.getGameTime() + (arraial.satisfacao > 75 ? Vilas.INTERVALO_CHEGADA / 2 : Vilas.INTERVALO_CHEGADA);
			arraial.setChanged();
		}
	}

	/** Last day of the morning routine (satisfaction, stage, prices, repairs, the tropeiro). */
	long ultimoDiario;
	/** A prefeito voted out; not eligible in the next election. */
	java.util.@org.jspecify.annotations.Nullable UUID deposto;

	private void diario(ServerLevel level, List<MoradorEntity> moradores) {
		long hoje = Vilas.dia(level);
		if (hoje < ultimoDiario) {
			ultimoDiario = hoje - 1; // clock set back
		}
		if (hoje <= ultimoDiario || !level.isBrightOutside()) {
			return;
		}
		ultimoDiario = hoje;
		setChanged();
		if (abandonada) {
			return; // nobody left to run the arraial
		}
		Politica.diario(level, this, moradores);
		if (Satisfacao.diario(level, this, moradores)) {
			Prefeitura.depor(level, this);
		}
		Estagio.avaliar(level, this, moradores.size());
		Feira.atualizarPrecos(level, this, moradores.size());
		Reparos.verificar(level, this);
		Caravana.diario(level, this, moradores);
		Diplomacia.diario(level, this, moradores);
		Comercio.diario(level, this, moradores);
	}

	/** A newcomer walks into town: spawned on the surface a few blocks from the Cruzeiro. */
	private @org.jspecify.annotations.Nullable MoradorEntity receberMorador(ServerLevel level) {
		double angulo = level.getRandom().nextDouble() * Math.PI * 2;
		int dist = 6 + level.getRandom().nextInt(6);
		BlockPos alvo = worldPosition.offset((int) (Math.cos(angulo) * dist), 0, (int) (Math.sin(angulo) * dist));
		BlockPos chao = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, alvo);
		MoradorEntity novo = MoradorEntity.criar(level, chao, Profissao.NENHUMA);
		if (novo == null) {
			return null;
		}
		novo.definirVila(worldPosition);
		anotar(new Cronica.Entrada(Vilas.dia(level), Component.translatable("arraial.cronica.chegada", novo.nome())));
		Component aviso = Component.translatable("arraial.vila.chegou", novo.nome(), nomeCompleto()).withStyle(ChatFormatting.AQUA);
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().closerThan(worldPosition, Vilas.RAIO * 2)) {
				p.sendSystemMessage(aviso);
			}
		}
		return novo;
	}

	/** Someone who left another arraial (hunger, unhappiness) settles here. */
	void receberMigrante(ServerLevel level, String origem, String motivo) {
		MoradorEntity m = receberMorador(level);
		if (m != null && !origem.isEmpty()) {
			Component texto = Component.translatable("arraial.cronica.migrante", m.nome(), origem, Component.translatable(motivo));
			anotar(new Cronica.Entrada(Vilas.dia(level), texto));
			Prefeitura.avisar(level, this, texto.copy().withStyle(ChatFormatting.AQUA));
		}
	}

	/** Everybody is gone: the moradores leave and the arraial waits, empty. */
	void abandonar(ServerLevel level, List<MoradorEntity> moradores) {
		moradores.forEach(m -> m.discard());
		abandonada = true;
		prefeito = null;
		anotar(new Cronica.Entrada(Vilas.dia(level), Component.translatable("arraial.cronica.abandonada")));
		setChanged();
	}

	/** A player came back to an abandoned arraial with food in the armazém: people return. */
	boolean repovoar(ServerLevel level) {
		Registro.Info dono = Registro.get(Registro.id(level.dimension().identifier().toString(), worldPosition));
		if (dono != null && "bandidos".equals(dono.ocupadaPor)) {
			return false; // first the bandits must go
		}
		boolean alguem = level.players().stream().anyMatch(p -> p.blockPosition().closerThan(worldPosition, 32));
		int comida = Vilas.contar(Vilas.armazens(level, worldPosition), s -> s.has(net.minecraft.core.component.DataComponents.FOOD));
		if (!alguem || comida < 8 || camas(level) == 0) {
			return false;
		}
		abandonada = false;
		Registro.Info info = Registro.get(Registro.id(level.dimension().identifier().toString(), worldPosition));
		if (info != null) {
			info.abandonada = false;
			info.fome = 0;
		}
		Component texto = Component.translatable("arraial.cronica.repovoado", nomeCompleto());
		anotar(new Cronica.Entrada(Vilas.dia(level), texto));
		Prefeitura.avisar(level, this, texto.copy().withStyle(ChatFormatting.GREEN));
		proximaChegada = level.getGameTime();
		setChanged();
		return true;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		nome = input.getStringOr("Nome", "");
		proximaChegada = input.getLongOr("ProximaChegada", 0L);
		cronica.clear();
		input.read("Cronica", Cronica.LISTA).ifPresent(cronica::addAll);
		prefeito = input.read("Prefeito", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
		ultimoDespacho = input.getLongOr("UltimoDespacho", 0L);
		pedido = input.read("Pedido", net.minecraft.network.chat.ComponentSerialization.CODEC).orElse(Component.empty());
		crise.ler(input);
		festa.ler(input);
		politica.ler(input);
		encomendas.clear();
		input.read("Encomendas", com.mojang.serialization.Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, com.mojang.serialization.Codec.STRING)).ifPresent(encomendas::putAll);
		cultura.clear();
		input.read("CulturaJogador", com.mojang.serialization.Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, com.mojang.serialization.Codec.INT)).ifPresent(cultura::putAll);
		pagamentos.clear();
		input.read("Pagamentos", com.mojang.serialization.Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, com.mojang.serialization.Codec.INT)).ifPresent(pagamentos::putAll);
		satisfacao = input.getIntOr("Satisfacao", 60);
		ultimoDiario = input.getLongOr("UltimoDiario", 0L);
		autonoma = input.getBooleanOr("Autonoma", false);
		abandonada = input.getBooleanOr("Abandonada", false);
		diaRuina = input.getLongOr("DiaRuina", 0L);
		deposto = input.read("Deposto", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
		legitimidade = input.getIntOr("Legitimidade", 60);
		diasLegitimidadeBaixa = input.getIntOr("DiasLegitimidadeBaixa", 0);
		tesouro = input.getIntOr("Tesouro", 20);
		estagio = Estagio.de(input.getIntOr("Estagio", 0));
		ultimoTick = input.getLongOr("UltimoTick", 0L);
		precos.clear();
		input.read("Precos", com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING, com.mojang.serialization.Codec.DOUBLE)).ifPresent(precos::putAll);
		reputacao.clear();
		input.read("Reputacao", com.mojang.serialization.Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, com.mojang.serialization.Codec.INT)).ifPresent(reputacao::putAll);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("Nome", nome);
		output.putLong("ProximaChegada", proximaChegada);
		output.store("Cronica", Cronica.LISTA, List.copyOf(cronica));
		if (prefeito != null) {
			output.store("Prefeito", net.minecraft.core.UUIDUtil.CODEC, prefeito);
		}
		output.putLong("UltimoDespacho", ultimoDespacho);
		output.store("Pedido", net.minecraft.network.chat.ComponentSerialization.CODEC, pedido);
		crise.gravar(output);
		festa.gravar(output);
		politica.gravar(output);
		output.store("Encomendas", com.mojang.serialization.Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, com.mojang.serialization.Codec.STRING), java.util.Map.copyOf(encomendas));
		output.store("CulturaJogador", com.mojang.serialization.Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, com.mojang.serialization.Codec.INT), java.util.Map.copyOf(cultura));
		output.store("Pagamentos", com.mojang.serialization.Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, com.mojang.serialization.Codec.INT), java.util.Map.copyOf(pagamentos));
		output.putInt("Satisfacao", satisfacao);
		output.putLong("UltimoDiario", ultimoDiario);
		output.putBoolean("Autonoma", autonoma);
		output.putBoolean("Abandonada", abandonada);
		output.putLong("DiaRuina", diaRuina);
		if (deposto != null) {
			output.store("Deposto", net.minecraft.core.UUIDUtil.CODEC, deposto);
		}
		output.putInt("Legitimidade", legitimidade);
		output.putInt("DiasLegitimidadeBaixa", diasLegitimidadeBaixa);
		output.putInt("Tesouro", tesouro);
		output.putInt("Estagio", estagio.ordinal());
		output.putLong("UltimoTick", ultimoTick);
		output.store("Precos", com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING, com.mojang.serialization.Codec.DOUBLE), java.util.Map.copyOf(precos));
		output.store("Reputacao", com.mojang.serialization.Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, com.mojang.serialization.Codec.INT), java.util.Map.copyOf(reputacao));
	}
}
