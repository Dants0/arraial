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
	public static final int VISUAIS = 4;

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
		goalSelector.addGoal(2, new ConstruirGoal(this));
		goalSelector.addGoal(2, new ArtesaoGoal(this));
		goalSelector.addGoal(2, new SoldadoGoal.Escoltar(this));
		goalSelector.addGoal(3, new LenhadorGoal(this));
		goalSelector.addGoal(3, new RoceiroGoal(this));
		goalSelector.addGoal(3, new PastorGoal(this));
		goalSelector.addGoal(3, new CavarGoal(this, Profissao.BARREIRO));
		goalSelector.addGoal(3, new CavarGoal(this, Profissao.CAVOUQUEIRO));
		goalSelector.addGoal(3, new PrefeitoGoal(this));
		goalSelector.addGoal(3, new OpenDoorGoal(this, true));
		goalSelector.addGoal(4, new SoldadoGoal(this));
		targetSelector.addGoal(1, new SoldadoGoal.Revidar(this));
		targetSelector.addGoal(2, new SoldadoGoal.Cacar(this));
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
		if (!status.getString().isEmpty()) {
			placa.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(status.copy().withStyle(ChatFormatting.WHITE));
		}
		setCustomName(placa);
		setCustomNameVisible(true);
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
		if (!portoes.isEmpty() && tickCount % 10 == 0) {
			portoes.entrySet().removeIf(e -> {
				if (level.getGameTime() - e.getValue() < 30 || !Porteira.podeFechar(level, e.getKey(), this)) {
					return false;
				}
				Porteira.fechar(level, e.getKey());
				return true;
			});
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
		if (vila == null) {
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
		if (p == Profissao.NENHUMA && vila != null && gabinete == null) {
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
		if (hoje <= ultimoDia || vila == null) {
			return;
		}
		ultimoDia = hoje;
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
		if (stack.is(ModItems.CONTRATO_PEDREIRO)) {
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
		if (stack.isEmpty()) {
			ArraialBlockEntity a = arraial();
			Component onde = a != null ? Component.literal(a.nome()) : Component.translatable("arraial.morador.sem_vila");
			Component lar = casa != null ? Component.translatable("arraial.morador.tem_casa") : Component.translatable("arraial.morador.sem_casa");
			player.sendSystemMessage(Component.translatable("arraial.morador.ficha", nome(), titulo(), onde, lar).withStyle(ChatFormatting.YELLOW));
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
		output.putInt("Visual", visual());
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
