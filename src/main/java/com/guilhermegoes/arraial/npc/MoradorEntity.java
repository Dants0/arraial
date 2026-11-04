package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.registry.ModEntities;
import com.guilhermegoes.arraial.registry.ModItems;
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
	public static final int VISUAIS = 3;

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
				.add(Attributes.FOLLOW_RANGE, 32.0);
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
		goalSelector.addGoal(1, new DormirGoal(this));
		goalSelector.addGoal(2, new ConstruirGoal(this));
		goalSelector.addGoal(3, new OpenDoorGoal(this, true));
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
		atualizarPlaca();
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
		MutableComponent placa = Component.translatable("arraial.morador.placa", nome(), titulo()).withStyle(ChatFormatting.GOLD);
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
		if ((tickCount + getId()) % 40 == 0) {
			cuidarDaVida(level);
		}
	}

	/** Every couple of seconds: join an arraial, claim a bed, and drop a home that no longer exists. */
	private void cuidarDaVida(ServerLevel level) {
		if (vila != null && arraial() == null && level.isLoaded(vila)) {
			vila = null; // the Cruzeiro was broken
			soltarCasa(level);
		}
		if (vila == null) {
			BlockPos encontrada = Vilas.maisProxima(level, blockPosition());
			if (encontrada != null) {
				vila = encontrada;
			}
		}
		if (vila != null) {
			setHomeTo(vila, Vilas.RAIO);
		} else {
			clearHome();
		}

		if (casa != null && level.isLoaded(casa) && !level.getBlockState(casa).is(BlockTags.BEDS)) {
			soltarCasa(level); // the bed was broken
		}
		if (casa == null && vila != null) {
			level.getPoiManager()
					.take(h -> h.is(PoiTypes.HOME), (h, pos) -> true, vila, Vilas.RAIO)
					.ifPresent(cama -> casa = cama.immutable());
		}
		if (status.getString().isEmpty() || status.equals(Component.translatable("arraial.morador.sem_casa"))) {
			setStatus(casa == null && vila != null ? Component.translatable("arraial.morador.sem_casa") : Component.empty());
		}
	}

	private void soltarCasa(ServerLevel level) {
		if (casa != null) {
			PoiManager poi = level.getPoiManager();
			if (poi.exists(casa, h -> h.is(PoiTypes.HOME))) {
				poi.release(casa);
			}
			casa = null;
		}
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel server) {
			soltarCasa(server);
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
			setProfissao(Profissao.PEDREIRO);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			player.sendSystemMessage(Component.translatable("arraial.morador.virou", nome(), titulo()).withStyle(ChatFormatting.GOLD));
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
		atualizarPlaca();
	}
}
