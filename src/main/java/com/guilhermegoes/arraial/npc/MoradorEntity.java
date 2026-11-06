package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.registry.ModEntities;
import com.guilhermegoes.arraial.registry.ModItems;
import com.guilhermegoes.arraial.registry.ModPois;
import com.guilhermegoes.arraial.vila.Cronica;
import net.minecraft.core.component.DataComponents;
import com.guilhermegoes.arraial.vila.ArraialBlockEntity;
import com.guilhermegoes.arraial.vila.Vilas;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * An inhabitant of an arraial. Has a name, a home (a bed claimed inside the village),
 * a daily routine, and optionally a profession (e.g. pedreiro).
 */
public class MoradorEntity extends PathfinderMob {
	/** Looks (skin tone, hair, face); the last one is an elder with grey hair. */
	public static final int VISUAIS = 6;

	private static final List<String> NOMES_M = List.of(
			"Zé", "Tião", "Chico", "Bento", "Severino", "Raimundo", "Damião", "Joaquim",
			"Benedito", "Sebastião", "Manoel", "Expedito", "Lourenço", "Januário", "Firmino", "Quinzinho");
	private static final List<String> NOMES_F = List.of(
			"Maria", "Rosa", "Benedita", "Joana", "Luzia", "Francisca", "Conceição", "Severina",
			"Ana", "Rita", "Tereza", "Aparecida", "Josefa", "Antônia", "Zefinha", "Dorinha");

	private static final EntityDataAccessor<Integer> PROFISSAO = SynchedEntityData.defineId(MoradorEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> VISUAL = SynchedEntityData.defineId(MoradorEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> FEMININO = SynchedEntityData.defineId(MoradorEntity.class, EntityDataSerializers.BOOLEAN);

	private static final Codec<Map<Item, Integer>> CARGA_CODEC =
			Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.INT);

	private String nome = "";
	private Component status = Component.empty();
	/** Materials a pedreiro is carrying, taken from the site chest. */
	private final Map<Item, Integer> carga = new HashMap<>();
	private @Nullable BlockPos obra;
	/** The bed this morador claimed (its head block). */
	private @Nullable BlockPos casa;
	/** The arraial's Cruzeiro. */
	private @Nullable BlockPos vila;
	/** The workstation (cepo, balaio, forno) this morador works at. */
	private @Nullable BlockPos posto;
	/** Hungry days in a row; at {@link #DIAS_ATE_IR_EMBORA} the morador leaves. */
	private int diasSemComer = 0;
	/** Days of food a newcomer brings with them. */
	private int provisoes = 2;
	private long ultimoDia = 0;
	private long diaChegada = 0;
	private boolean prefeito = false;
	/** Fence gates this morador opened and must close behind him (not saved). */
	private final java.util.Map<BlockPos, Long> portoes = new java.util.HashMap<>();
	/** Pastor: out in the countryside fetching animals. Soldado: the pastor he escorts. Not saved. */
	private boolean expedicao = false;
	private @Nullable MoradorEntity escoltado;
	public static final int DIAS_ATE_IR_EMBORA = 3;

	// ---- Family (see vila/Familias)
	private static final List<String> SOBRENOMES = List.of("Silva", "Souza", "Oliveira", "Ferreira", "Alves", "Lima", "Gomes", "Ribeiro",
			"Carvalho", "Rocha", "Barbosa", "Cavalcanti", "Albuquerque", "Bezerra", "Nogueira", "Santana", "Macedo", "Teixeira");
	private String sobrenome = "";
	private java.util.@Nullable UUID conjuge;
	private java.util.@Nullable UUID pai;
	private java.util.@Nullable UUID mae;
	private boolean crianca;
	private long nascimento;
	/** Children grow up after this many days. */
	public static final int DIAS_INFANCIA = 3;

	public String sobrenome() {
		if (sobrenome.isEmpty()) {
			sobrenome = SOBRENOMES.get(getRandom().nextInt(SOBRENOMES.size()));
		}
		return sobrenome;
	}

	public void setSobrenome(String s) {
		sobrenome = s;
	}

	public java.util.@Nullable UUID conjuge() {
		return conjuge;
	}

	public void setConjuge(java.util.@Nullable UUID c) {
		conjuge = c;
	}

	public boolean filhoDe(java.util.UUID p) {
		return p.equals(pai) || p.equals(mae);
	}

	public java.util.@Nullable UUID pai() {
		return pai;
	}

	public java.util.@Nullable UUID mae() {
		return mae;
	}

	public boolean crianca() {
		return crianca;
	}

	public long nascimento() {
		return nascimento;
	}

	/** Born in the arraial: small, no trade, until they grow up. */
	public void nascer(java.util.UUID pai, java.util.UUID mae, String sobrenome, long dia) {
		this.pai = pai;
		this.mae = mae;
		this.sobrenome = sobrenome;
		this.crianca = true;
		this.nascimento = dia;
		this.diaChegada = dia;
		getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE).setBaseValue(0.55);
	}

	/** A newborn takes after one of the parents (but not the grey hair of an elder). */
	public void puxar(MoradorEntity parente) {
		int v = parente.visual();
		entityData.set(VISUAL, v >= VISUAIS - 1 ? VISUAIS - 2 : v);
	}

	public void crescer() {
		crianca = false;
		getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE).setBaseValue(1.0);
	}

	/** Personal life: personality, needs, friendships, a dream, missions (see {@link Pessoa}). */
	private final Pessoa pessoa = new Pessoa();

	public Pessoa pessoa() {
		return pessoa;
	}

	// ---- Mascate (a peddler from another arraial; see Comercio) and caravan escort
	private String mascateOrigem = "";
	private @Nullable BlockPos mascateVila;
	private long mascateFim;
	private int bolsa;
	private java.util.@Nullable UUID escolta;
	private boolean escoltada;

	public void virarMascate(String origem, BlockPos vila, long fim, int bolsa) {
		mascateOrigem = origem;
		mascateVila = vila.immutable();
		mascateFim = fim;
		this.bolsa = bolsa;
		setProfissao(Profissao.MASCATE);
	}

	public String mascateOrigem() {
		return mascateOrigem;
	}

	public @Nullable BlockPos mascateVila() {
		return mascateVila;
	}

	public long mascateFim() {
		return mascateFim;
	}

	public int bolsa() {
		return bolsa;
	}

	public void mudarBolsa(int delta) {
		bolsa = Math.max(0, bolsa + delta);
	}

	public java.util.@Nullable UUID escolta() {
		return escolta;
	}

	public boolean escoltada() {
		return escoltada;
	}

	public void setEscolta(java.util.@Nullable UUID jogador, boolean confirmada) {
		escolta = jogador;
		escoltada = confirmada;
	}

	public MoradorEntity(EntityType<? extends MoradorEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		getNavigation().setCanOpenDoors(true);
	}

	/** Creates a new morador with a random look and name at {@code pos}. */
	public static @Nullable MoradorEntity criar(ServerLevel level, BlockPos pos, Profissao profissao) {
		MoradorEntity m = ModEntities.MORADOR.spawn(level, pos, EntitySpawnReason.MOB_SUMMONED);
		if (m != null) {
			m.entityData.set(FEMININO, m.getRandom().nextBoolean());
			m.entityData.set(VISUAL, m.getRandom().nextInt(VISUAIS));
			m.setProfissao(profissao);
			m.nome = "";
			m.pessoa.gerar(m.getRandom());
			m.sobrenome();
			m.nome();
		}
		return m;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 32.0)
				.add(Attributes.ATTACK_DAMAGE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(PROFISSAO, Profissao.NENHUMA.ordinal());
		builder.define(VISUAL, 0);
		builder.define(FEMININO, false);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new SoldadoGoal.Atacar(this));
		goalSelector.addGoal(1, new FugirGoal(this));
		goalSelector.addGoal(1, new DormirGoal(this));
		goalSelector.addGoal(1, new ApagarFogoGoal(this));
		goalSelector.addGoal(1, new FestejarGoal(this));
		goalSelector.addGoal(1, new ProtestarGoal(this));
		goalSelector.addGoal(2, new MascateGoal(this));
		goalSelector.addGoal(2, new ConstruirGoal(this));
		goalSelector.addGoal(2, new BoticarioGoal(this));
		goalSelector.addGoal(2, new ArtesaoGoal(this));
		goalSelector.addGoal(2, new SoldadoGoal.Escoltar(this));
		goalSelector.addGoal(3, new LenhadorGoal(this));
		goalSelector.addGoal(3, new RoceiroGoal(this));
		goalSelector.addGoal(3, new PastorGoal(this));
		goalSelector.addGoal(3, new CavarGoal(this, Profissao.BARREIRO));
		goalSelector.addGoal(3, new CavarGoal(this, Profissao.CAVOUQUEIRO));
		goalSelector.addGoal(3, new PrefeitoGoal(this));
		goalSelector.addGoal(3, new TropeiroGoal(this));
		goalSelector.addGoal(3, new FeiranteGoal(this));
		goalSelector.addGoal(3, new PescadorGoal(this));
		goalSelector.addGoal(3, new CacadorGoal(this));
		goalSelector.addGoal(3, new EngenheiroGoal(this));
		goalSelector.addGoal(3, new EmissarioGoal(this));
		goalSelector.addGoal(3, new OpenDoorGoal(this, true));
		goalSelector.addGoal(4, new SoldadoGoal(this));
		goalSelector.addGoal(4, new LazerGoal(this));
		targetSelector.addGoal(1, new SoldadoGoal.Revidar(this));
		targetSelector.addGoal(2, new SoldadoGoal.Cacar(this));
		goalSelector.addGoal(5, new ConversarGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, MoradorEntity.class, 5.0F));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	// ---------------------------------------------------------------- identity

	public Profissao profissao() {
		return Profissao.porIndice(entityData.get(PROFISSAO));
	}

	public void setProfissao(Profissao p) {
		entityData.set(PROFISSAO, p.ordinal());
		armar(p == Profissao.SOLDADO);
		atualizarPlaca();
	}

	/** Soldados wear an iron helmet over their uniform and carry a sword (none of it drops). */
	private void armar(boolean soldado) {
		if (level().isClientSide()) {
			return;
		}
		setItemSlot(EquipmentSlot.HEAD, soldado ? new ItemStack(net.minecraft.world.item.Items.IRON_HELMET) : ItemStack.EMPTY);
		setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		setItemSlot(EquipmentSlot.MAINHAND, soldado ? new ItemStack(net.minecraft.world.item.Items.IRON_SWORD) : ItemStack.EMPTY);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			setDropChance(slot, 0.0F);
		}
		if (!soldado) {
			setTarget(null);
		}
	}

	public boolean prefeito() {
		return prefeito;
	}

	/** Set by the arraial after an election. */
	void abriuPortao(BlockPos pos, long quando) {
		portoes.put(pos.immutable(), quando);
	}

	public boolean expedicao() {
		return expedicao;
	}

	public void setExpedicao(boolean sim) {
		expedicao = sim;
	}

	public @Nullable MoradorEntity escoltado() {
		return escoltado != null && escoltado.isAlive() && escoltado.expedicao() ? escoltado : null;
	}

	public void setEscoltado(@Nullable MoradorEntity pastor) {
		escoltado = pastor;
	}

	/** The prefeito's lectern at the Prefeitura, once there is one (set by the arraial; not saved). */
	private @Nullable BlockPos gabinete;

	public @Nullable BlockPos gabinete() {
		return gabinete;
	}

	public void setGabinete(@Nullable BlockPos pos) {
		gabinete = pos;
	}

	/** Leaves the current trade (and its workstation) for good, e.g. to govern full time. */
	public void largarOficio() {
		if (level() instanceof ServerLevel server) {
			soltar(server, posto);
		}
		posto = null;
		obra = null;
		setProfissao(Profissao.NENHUMA);
	}

	public void setPrefeito(boolean sim) {
		if (!sim) {
			gabinete = null;
		}
		if (prefeito != sim) {
			prefeito = sim;
			atualizarPlaca();
		}
	}

	/** In-game day this morador settled in the arraial (seniority counts in elections). */
	public long diaChegada() {
		return diaChegada;
	}

	public boolean feminino() {
		return entityData.get(FEMININO);
	}

	public int visual() {
		return entityData.get(VISUAL);
	}

	public String nome() {
		if (nome.isEmpty()) {
			List<String> lista = feminino() ? NOMES_F : NOMES_M;
			nome = lista.get(getRandom().nextInt(lista.size()));
			atualizarPlaca();
		}
		return nome;
	}

	public Component titulo() {
		return profissao().titulo(feminino());
	}

	public void setStatus(Component novo) {
		if (!novo.equals(status)) {
			status = novo;
			atualizarPlaca();
		}
	}

	/** The floating name tag: "Maria, a Pedreira · status". */
	private void atualizarPlaca() {
		if (level().isClientSide()) {
			return;
		}
		MutableComponent placa = prefeito
				? Component.translatable("arraial.morador.placa_prefeito", nome(), titulo(),
						Component.translatable(feminino() ? "arraial.prefeito.f" : "arraial.prefeito.m")).withStyle(ChatFormatting.GOLD)
				: Component.translatable("arraial.morador.placa", nome(), titulo()).withStyle(ChatFormatting.GOLD);
		if (!fala.getString().isEmpty()) {
			placa.append(Component.literal(" 💬 ").withStyle(ChatFormatting.WHITE)).append(fala.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
		} else if (doenteDias > 0) {
			placa.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(Component.translatable("arraial.morador.doente").withStyle(ChatFormatting.GREEN));
		} else if (!status.getString().isEmpty()) {
			placa.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(status.copy().withStyle(ChatFormatting.WHITE));
		}
		setCustomName(placa);
		setCustomNameVisible(viagemFase != 3);
	}

	// ---------------------------------------------------------------- tropeiro's trips

	/** 0 at home, 1 loading, 2 leaving, 3 away (invisible at the cangalha), 4 back and unloading. */
	private int viagemFase;
	private String viagemDestino = "";
	private long viagemVolta;

	public int viagemFase() {
		return viagemFase;
	}

	public String viagemDestino() {
		return viagemDestino;
	}

	public long viagemVolta() {
		return viagemVolta;
	}

	public void setViagem(int fase, String destino, long volta) {
		viagemFase = fase;
		viagemDestino = destino;
		viagemVolta = volta;
		boolean fora = fase == 3;
		setInvisible(fora);
		setPermanentlyInvulnerable(fora);
		atualizarPlaca();
	}

	public boolean viajando() {
		return viagemFase == 3;
	}

	// ---------------------------------------------------------------- talk

	/** Who this morador is chatting with right now (not saved). */
	private @Nullable MoradorEntity interlocutor;
	private long proximaConversa;
	/** A line being said: shown in the name tag as a speech bubble until {@link #falaAte}. */
	private Component fala = Component.empty();
	private long falaAte;
	/** Days left of illness (epidemia); a sick morador rests instead of working. */
	private int doenteDias;

	public @Nullable MoradorEntity interlocutor() {
		return interlocutor;
	}

	public void setInterlocutor(@Nullable MoradorEntity m) {
		interlocutor = m;
	}

	public long proximaConversa() {
		return proximaConversa;
	}

	public void setProximaConversa(long quando) {
		proximaConversa = quando;
	}

	/** Idle, awake, not already chatting and not chatted too recently. */
	public boolean livreParaConversar(long agora) {
		return interlocutor == null && viagemFase == 0 && !isSleeping() && status.getString().isEmpty() && agora >= proximaConversa && !comFome()
				&& getNavigation().isDone();
	}

	/** Says a line out loud: a bubble over the head and a chat line for players close by. */
	public void dizer(Component texto, int ticks) {
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		fala = texto;
		falaAte = server.getGameTime() + ticks;
		atualizarPlaca();
		Component linha = Component.literal(nome() + ": ").withStyle(ChatFormatting.GOLD)
				.append(texto.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
		for (net.minecraft.server.level.ServerPlayer p : server.players()) {
			if (p.distanceToSqr(this) < 12 * 12) {
				p.sendSystemMessage(linha);
			}
		}
	}

	public boolean doente() {
		return doenteDias > 0;
	}

	public void adoecer(int dias) {
		doenteDias = Math.max(doenteDias, dias);
		atualizarPlaca();
	}

	public void curar() {
		doenteDias = 0;
		atualizarPlaca();
	}

	/** Called once a day: illness wears off by itself, slowly. */
	void passarDiaDoente() {
		if (doenteDias > 0 && --doenteDias == 0) {
			atualizarPlaca();
		}
	}

	// ---------------------------------------------------------------- village & home

	public @Nullable BlockPos vila() {
		return vila;
	}

	public @Nullable BlockPos casa() {
		return casa;
	}

	public @Nullable ArraialBlockEntity arraial() {
		if (vila == null || !(level() instanceof ServerLevel server) || !server.isLoaded(vila)) {
			return null;
		}
		return server.getBlockEntity(vila) instanceof ArraialBlockEntity a ? a : null;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (tickCount % 600 == 0 && vila != null) {
			pessoa.passar(level, this);
			if (crianca && com.guilhermegoes.arraial.vila.Vilas.dia(level) - nascimento >= DIAS_INFANCIA) {
				crescer();
				Cronica.anotar(level, vila, Component.translatable("arraial.cronica.cresceu", nome(), sobrenome()));
			}
		}
		if (viagemFase == 3) {
			// Away with the troop: stays put, out of sight, until the day he comes back.
			getNavigation().stop();
			setDeltaMovement(0, getDeltaMovement().y, 0);
			if (level.getGameTime() >= viagemVolta) {
				if (profissao() == Profissao.RELACOES) {
					com.guilhermegoes.arraial.vila.Diplomacia.voltar(level, this);
				} else {
					com.guilhermegoes.arraial.vila.Caravana.voltar(level, this);
				}
			}
			return;
		}
		if (!portoes.isEmpty() && tickCount % 10 == 0) {
			portoes.entrySet().removeIf(e -> {
				if (level.getGameTime() - e.getValue() < 30 || !Porteira.podeFechar(level, e.getKey(), this)) {
					return false;
				}
				Porteira.fechar(level, e.getKey());
				return true;
			});
		}
		if (!fala.getString().isEmpty() && level.getGameTime() >= falaAte) {
			fala = Component.empty();
			atualizarPlaca();
		}
		if ((tickCount + getId()) % 40 == 0) {
			cuidarDaVida(level);
		}
	}

	/** Used by the arraial when it welcomes a newcomer. */
	public void definirVila(BlockPos cruzeiro) {
		if (!cruzeiro.equals(vila) && level() instanceof ServerLevel server) {
			diaChegada = Vilas.dia(server);
		}
		vila = cruzeiro.immutable();
	}

	/** When a morador is attacked, the arraial's soldados come running. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		// The militia coat is padded: soldiers take a quarter less damage (no chestplate hides the uniform).
		if (profissao() == Profissao.SOLDADO) {
			damage *= 0.75F;
		}
		boolean ferido = super.hurtServer(level, source, damage);
		if (ferido && source.getEntity() instanceof net.minecraft.world.entity.LivingEntity agressor
				&& !(agressor instanceof MoradorEntity) && !(agressor instanceof Player)) {
			for (MoradorEntity s : level.getEntitiesOfClass(MoradorEntity.class, getBoundingBox().inflate(32),
					m -> m.profissao() == Profissao.SOLDADO && m.getTarget() == null)) {
				s.setTarget(agressor);
			}
		}
		return ferido;
	}

	/** Hungry moradores don't work. */
	public boolean comFome() {
		return diasSemComer > 0;
	}

	public @Nullable BlockPos posto() {
		return posto;
	}

	/**
	 * Every couple of seconds: join an arraial, claim a bed and a workstation,
	 * drop what no longer exists, and eat once a day.
	 */
	private void cuidarDaVida(ServerLevel level) {
		if (vila != null && arraial() == null && level.isLoaded(vila)) {
			vila = null; // the Cruzeiro was broken
			soltar(level, casa);
			casa = null;
		}
		if (vila == null && profissao() != Profissao.MASCATE) { // a mascate is only passing through
			BlockPos encontrada = Vilas.maisProxima(level, blockPosition());
			if (encontrada != null) {
				definirVila(encontrada);
			}
		}
		if (vila == null && prefeito) {
			setPrefeito(false);
		}
		// Fed moradores slowly heal their wounds.
		if (!comFome() && getHealth() < getMaxHealth()) {
			heal(1.0F);
		}
		if (profissao() == Profissao.SOLDADO && (getMainHandItem().isEmpty() || !getItemBySlot(EquipmentSlot.CHEST).isEmpty())) {
			armar(true);
		}
		if (vila != null) {
			setHomeTo(vila, Vilas.RAIO);
		} else {
			clearHome();
		}

		// Home
		if (casa != null && level.isLoaded(casa) && !level.getBlockState(casa).is(BlockTags.BEDS)) {
			soltar(level, casa); // the bed was broken
			casa = null;
		}
		if (casa == null && vila != null) {
			level.getPoiManager()
					.take(h -> h.is(PoiTypes.HOME), (h, pos) -> true, vila, Vilas.RAIO)
					.ifPresent(cama -> casa = cama.immutable());
		}

		// Workstation: lose the job if the posto is gone; the unemployed take a free one.
		Profissao p = profissao();
		if (posto != null && level.isLoaded(posto) && p.posto() != null && !level.getBlockState(posto).is(p.posto())) {
			soltar(level, posto);
			posto = null;
			setProfissao(Profissao.NENHUMA);
		}
		if (p == Profissao.NENHUMA && vila != null && gabinete == null && !crianca) {
			level.getPoiManager()
					.take(ModPois::ePosto, (h, pos) -> true, vila, Vilas.RAIO)
					.ifPresent(pos -> {
						Profissao nova = Profissao.doPosto(level.getBlockState(pos).getBlock());
						if (nova != null) {
							posto = pos.immutable();
							setProfissao(nova);
							Cronica.anotar(level, vila, Component.translatable("arraial.cronica.profissao", nome(), titulo()));
						} else {
							level.getPoiManager().release(pos);
						}
					});
		}

		comer(level);

		if (status.getString().isEmpty() || status.equals(Component.translatable("arraial.morador.sem_casa"))
				|| status.equals(Component.translatable("arraial.morador.com_fome"))) {
			Component s = Component.empty();
			if (comFome()) {
				s = Component.translatable("arraial.morador.com_fome");
			} else if (casa == null && vila != null) {
				s = Component.translatable("arraial.morador.sem_casa");
			}
			setStatus(s);
		}
	}

	/**
	 * Once per in-game day a morador eats one food item from the armazém. Newcomers bring a
	 * couple of days of provisions. After {@link #DIAS_ATE_IR_EMBORA} hungry days they leave.
	 */
	private void comer(ServerLevel level) {
		long hoje = Vilas.dia(level);
		if (ultimoDia == 0) {
			ultimoDia = hoje;
			return;
		}
		if (hoje < ultimoDia) {
			ultimoDia = hoje; // the clock was set back (/time set): start counting from today
		}
		if (vila == null) {
			return;
		}
		if (hoje == ultimoDia) {
			// Already counted today; but someone hungry eats as soon as food reaches the armazém.
			if (diasSemComer > 0 && Vilas.retirar(Vilas.armazens(level, vila), s -> s.has(DataComponents.FOOD), 1).size() > 0) {
				diasSemComer = 0;
				Cronica.anotar(level, vila, Component.translatable("arraial.cronica.matou_fome", nome()));
				setStatus(Component.empty());
			}
			return;
		}
		ultimoDia = hoje;
		passarDiaDoente();
		if (provisoes > 0) {
			provisoes--;
			return;
		}
		int comeu = Vilas.retirar(Vilas.armazens(level, vila), s -> s.has(DataComponents.FOOD), 1).size();
		if (comeu > 0) {
			if (diasSemComer > 0) {
				Cronica.anotar(level, vila, Component.translatable("arraial.cronica.matou_fome", nome()));
			}
			diasSemComer = 0;
			return;
		}
		diasSemComer++;
		Cronica.anotar(level, vila, Component.translatable("arraial.cronica.fome", nome(), diasSemComer));
		if (diasSemComer >= DIAS_ATE_IR_EMBORA) {
			irEmbora(level);
		}
	}

	/** Leaves the arraial for good (hunger). */
	/** Leaves the arraial for good for another reason (the chronicle line is written by the caller). */
	public void irEmboraDaVila(ServerLevel level) {
		soltar(level, casa);
		soltar(level, posto);
		discard();
	}

	private void irEmbora(ServerLevel level) {
		Component aviso = Component.translatable("arraial.morador.foi_embora", nome()).withStyle(ChatFormatting.RED);
		Cronica.anotar(level, vila, Component.translatable("arraial.cronica.foi_embora", nome()));
		for (net.minecraft.server.level.ServerPlayer pl : level.players()) {
			if (pl.distanceToSqr(this) < 96 * 96) {
				pl.sendSystemMessage(aviso);
			}
		}
		soltar(level, casa);
		soltar(level, posto);
		discard();
	}

	private static void soltar(ServerLevel level, @Nullable BlockPos pos) {
		if (pos != null) {
			PoiManager poi = level.getPoiManager();
			if (poi.getType(pos).isPresent()) {
				poi.release(pos);
			}
		}
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel server) {
			soltar(server, casa);
			soltar(server, posto);
			Cronica.anotar(server, vila, Component.translatable("arraial.cronica.morte", nome()));
			// Mourning: the arraial grieves its dead.
			if (vila != null && profissao() != Profissao.MASCATE) {
				com.guilhermegoes.arraial.vila.Satisfacao.agradar(server, vila, -3);
				Cronica.anotar(server, vila, Component.translatable("arraial.cronica.luto", nome()));
			}
			// Killed by a player: the whole arraial remembers.
			if (source.getEntity() instanceof net.minecraft.world.entity.player.Player p) {
				ArraialBlockEntity a = arraial();
				if (a != null) {
					a.mudarReputacao(p.getUUID(), -30);
				}
				com.guilhermegoes.arraial.vila.Satisfacao.agradar(server, vila, -10);
			}
		}
		super.die(source);
	}

	// ---------------------------------------------------------------- work

	public @Nullable BlockPos obra() {
		return obra;
	}

	public void setObra(@Nullable BlockPos obra) {
		this.obra = obra;
	}

	/** Drops everything carried (the load was sold, or stolen on the road). */
	public void esvaziarCarga() {
		carga.clear();
	}

	public Map<Item, Integer> cargaCopia() {
		return Map.copyOf(carga);
	}

	public int cargaTotal() {
		return carga.values().stream().mapToInt(Integer::intValue).sum();
	}

	public int carregando(Item item) {
		return carga.getOrDefault(item, 0);
	}

	public void guardar(Item item, int quantidade) {
		carga.merge(item, quantidade, Integer::sum);
	}

	public boolean gastar(Item item, int quantidade) {
		int tem = carregando(item);
		if (tem < quantidade) {
			return false;
		}
		if (tem == quantidade) {
			carga.remove(item);
		} else {
			carga.put(item, tem - quantidade);
		}
		return true;
	}

	public void segurar(ItemStack stack) {
		setItemSlot(EquipmentSlot.MAINHAND, stack);
	}

	// ---------------------------------------------------------------- interaction

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (profissao() == Profissao.MASCATE && level() instanceof ServerLevel s) {
			getNavigation().stop();
			getLookControl().setLookAt(player);
			com.guilhermegoes.arraial.vila.Comercio.negociar(s, this, player, stack);
			return InteractionResult.SUCCESS;
		}
		// A sword in hand: "I will ride with the troop" (the escort cuts the risk of robbery).
		if (profissao() == Profissao.TROPEIRO && (viagemFase == 1 || viagemFase == 2) && stack.is(net.minecraft.tags.ItemTags.SWORDS)) {
			escolta = player.getUUID();
			escoltada = false;
			dizer(Component.translatable("arraial.fala.escolta", player.getName()), 80);
			player.sendSystemMessage(Component.translatable("arraial.comercio.escolta_aceita", nome()).withStyle(ChatFormatting.GOLD));
			return InteractionResult.SUCCESS;
		}
		if (stack.is(ModItems.CONTRATO_PEDREIRO)) {
			if (crianca) {
				player.sendOverlayMessage(Component.translatable("arraial.familia.crianca_nao", nome()));
				return InteractionResult.SUCCESS;
			}
			if (profissao() == Profissao.PEDREIRO) {
				player.sendOverlayMessage(Component.translatable("arraial.morador.ja_e", nome(), titulo()));
				return InteractionResult.SUCCESS;
			}
			if (level() instanceof ServerLevel server) {
				soltar(server, posto);
				posto = null;
				Cronica.anotar(server, vila, Component.translatable("arraial.cronica.profissao", nome(), Profissao.PEDREIRO.titulo(feminino())));
			}
			setProfissao(Profissao.PEDREIRO);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			player.sendSystemMessage(Component.translatable("arraial.morador.virou", nome(), titulo()).withStyle(ChatFormatting.GOLD));
			return InteractionResult.SUCCESS;
		}
		if (stack.isEmpty() && player.isShiftKeyDown() && profissao() != Profissao.NENHUMA) {
			// Dismiss: back to plain morador, freeing the workstation; they'll pick up any free one.
			Component antigo = titulo();
			if (level() instanceof ServerLevel server) {
				soltar(server, posto);
				posto = null;
				obra = null;
				setProfissao(Profissao.NENHUMA);
				Cronica.anotar(server, vila, Component.translatable("arraial.cronica.dispensado", nome(), antigo));
			}
			player.sendSystemMessage(Component.translatable("arraial.morador.dispensado", nome(), antigo).withStyle(ChatFormatting.YELLOW));
			return InteractionResult.SUCCESS;
		}
		// Missions: the thing asked for, a note to deliver, a peace offering for a rival.
		if (level() instanceof ServerLevel s && !stack.isEmpty() && Pessoa.cumprir(s, this, player, stack)) {
			return InteractionResult.SUCCESS;
		}
		// Sweets for the festa: the village remembers who brought them.
		if (level() instanceof ServerLevel server && arraial() != null && arraial().festa().agora() != null
				&& (stack.is(ModItems.QUENTAO) || stack.is(ModItems.PE_DE_MOLEQUE) || stack.is(ModItems.PACOCA) || stack.is(ModItems.RAPADURA) || stack.is(ModItems.BEIJU))) {
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			diasSemComer = 0;
			dizer(Component.translatable("arraial.fala.festa_presente", player.getName()), 80);
			com.guilhermegoes.arraial.vila.Satisfacao.agradar(server, vila, 1);
			com.guilhermegoes.arraial.vila.Cultura.reconhecer(server, arraial(), player, 3);
			return InteractionResult.SUCCESS;
		}
		// The favourite food: a gift that wins a friend.
		if (level() instanceof ServerLevel s && !comFome() && pessoa.favoritaDada(s, this, player, stack)) {
			getLookControl().setLookAt(player);
			return InteractionResult.SUCCESS;
		}
		// A gift of food for someone hungry (or a remedy for someone sick).
		if (level() instanceof ServerLevel server && (comFome() && stack.has(DataComponents.FOOD)
				|| doente() && stack.is(ModItems.REMEDIO))) {
			if (doente() && stack.is(ModItems.REMEDIO)) {
				curar();
			} else {
				diasSemComer = 0;
				Cronica.anotar(server, vila, Component.translatable("arraial.cronica.presente", player.getName(), nome()));
			}
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			getLookControl().setLookAt(player);
			dizer(Falas.presente(server, player.getName().getString()), 80);
			com.guilhermegoes.arraial.vila.Satisfacao.agradar(server, vila, 5);
			return InteractionResult.SUCCESS;
		}
		if (stack.isEmpty()) {
			ArraialBlockEntity a = arraial();
			Component onde = a != null ? Component.literal(a.nomeCompleto()) : Component.translatable("arraial.morador.sem_vila");
			Component lar = casa != null ? Component.translatable("arraial.morador.tem_casa") : Component.translatable("arraial.morador.sem_casa");
			player.sendSystemMessage(Component.translatable("arraial.morador.ficha", nome(), titulo(), onde, lar).withStyle(ChatFormatting.YELLOW));
			if (level() instanceof ServerLevel server) {
				getNavigation().stop();
				getLookControl().setLookAt(player);
				dizer(Falas.paraJogador(server, this, player.getName().getString()), 100);
				pessoa.ficha(this, player).forEach(player::sendSystemMessage);
				player.sendSystemMessage(com.guilhermegoes.arraial.vila.Familias.ficha(server, this));
				pessoa.conversouComJogador(server, this, player);
				pessoa.oferecer(server, this, player);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	// ---------------------------------------------------------------- save

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("Nome", nome());
		output.putInt("Profissao", profissao().ordinal());
		output.putInt("Doente", doenteDias);
		output.putInt("ViagemFase", viagemFase);
		output.putString("ViagemDestino", viagemDestino);
		output.putLong("ViagemVolta", viagemVolta);
		output.putString("MascateOrigem", mascateOrigem);
		if (mascateVila != null) {
			output.putLong("MascateVila", mascateVila.asLong());
		}
		output.putLong("MascateFim", mascateFim);
		output.putInt("Bolsa", bolsa);
		if (escolta != null) {
			output.store("Escolta", net.minecraft.core.UUIDUtil.CODEC, escolta);
		}
		output.putBoolean("Escoltada", escoltada);
		output.putInt("Visual", visual());
		pessoa.gravar(output);
		output.putString("Sobrenome", sobrenome);
		if (conjuge != null) {
			output.store("Conjuge", net.minecraft.core.UUIDUtil.CODEC, conjuge);
		}
		if (pai != null) {
			output.store("Pai", net.minecraft.core.UUIDUtil.CODEC, pai);
		}
		if (mae != null) {
			output.store("Mae", net.minecraft.core.UUIDUtil.CODEC, mae);
		}
		output.putBoolean("Crianca", crianca);
		output.putLong("Nascimento", nascimento);
		output.putBoolean("Feminino", feminino());
		output.store("Carga", CARGA_CODEC, Map.copyOf(carga));
		if (obra != null) {
			output.store("Obra", BlockPos.CODEC, obra);
		}
		if (casa != null) {
			output.store("Casa", BlockPos.CODEC, casa);
		}
		if (vila != null) {
			output.store("Vila", BlockPos.CODEC, vila);
		}
		if (posto != null) {
			output.store("Posto", BlockPos.CODEC, posto);
		}
		output.putInt("DiasSemComer", diasSemComer);
		output.putInt("Provisoes", provisoes);
		output.putLong("UltimoDia", ultimoDia);
		output.putLong("DiaChegada", diaChegada);
		output.putBoolean("Prefeito", prefeito);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		entityData.set(PROFISSAO, input.getIntOr("Profissao", 0));
		pessoa.ler(input);
		sobrenome = input.getStringOr("Sobrenome", "");
		conjuge = input.read("Conjuge", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
		pai = input.read("Pai", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
		mae = input.read("Mae", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
		crianca = input.getBooleanOr("Crianca", false);
		nascimento = input.getLongOr("Nascimento", 0L);
		if (pessoa.tracos.isEmpty()) {
			pessoa.gerar(getRandom()); // moradores from before personal life
		}
		doenteDias = input.getIntOr("Doente", 0);
		viagemFase = input.getIntOr("ViagemFase", 0);
		viagemDestino = input.getStringOr("ViagemDestino", "");
		viagemVolta = input.getLongOr("ViagemVolta", 0L);
		mascateOrigem = input.getStringOr("MascateOrigem", "");
		long mv = input.getLongOr("MascateVila", Long.MIN_VALUE);
		mascateVila = mv == Long.MIN_VALUE ? null : BlockPos.of(mv);
		mascateFim = input.getLongOr("MascateFim", 0L);
		bolsa = input.getIntOr("Bolsa", 0);
		escolta = input.read("Escolta", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
		escoltada = input.getBooleanOr("Escoltada", false);
		if (viagemFase == 3) {
			setInvisible(true);
			setPermanentlyInvulnerable(true);
		}
		entityData.set(VISUAL, input.getIntOr("Visual", 0));
		entityData.set(FEMININO, input.getBooleanOr("Feminino", false));
		nome = input.getStringOr("Nome", "");
		carga.clear();
		input.read("Carga", CARGA_CODEC).ifPresent(carga::putAll);
		obra = input.read("Obra", BlockPos.CODEC).orElse(null);
		casa = input.read("Casa", BlockPos.CODEC).orElse(null);
		vila = input.read("Vila", BlockPos.CODEC).orElse(null);
		posto = input.read("Posto", BlockPos.CODEC).orElse(null);
		diasSemComer = input.getIntOr("DiasSemComer", 0);
		provisoes = input.getIntOr("Provisoes", 2);
		ultimoDia = input.getLongOr("UltimoDia", 0L);
		diaChegada = input.getLongOr("DiaChegada", 0L);
		prefeito = input.getBooleanOr("Prefeito", false);
		atualizarPlaca();
	}
}
