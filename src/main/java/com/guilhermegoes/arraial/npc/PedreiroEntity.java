package com.guilhermegoes.arraial.npc;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** A builder NPC. He finds a nearby Marco da Obra, fetches materials from its chest and builds the project. */
public class PedreiroEntity extends PathfinderMob {
	private static final List<String> NOMES = List.of(
			"Zé", "Tião", "Chico", "Bento", "Severino", "Raimundo", "Damião", "Joaquim",
			"Benedito", "Sebastião", "Manoel", "Expedito", "Lourenço", "Januário", "Firmino", "Quinzinho");

	private static final Codec<Map<Item, Integer>> CARGA_CODEC =
			Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.INT);

	private String nome = "";
	private Component status = Component.empty();
	/** Materials he is carrying, taken from the site chest. */
	private final Map<Item, Integer> carga = new HashMap<>();
	private @Nullable BlockPos obra;

	public PedreiroEntity(EntityType<? extends PedreiroEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new ConstruirGoal(this));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	public String nome() {
		if (nome.isEmpty()) {
			nome = NOMES.get(getRandom().nextInt(NOMES.size()));
			atualizarPlaca();
		}
		return nome;
	}

	public void setStatus(Component novo) {
		if (!novo.equals(status)) {
			status = novo;
			atualizarPlaca();
		}
	}

	/** The floating name tag: "Zé, o Pedreiro · status". */
	private void atualizarPlaca() {
		MutableComponent placa = Component.translatable("arraial.pedreiro.titulo", nome()).withStyle(ChatFormatting.GOLD);
		if (!status.getString().isEmpty()) {
			placa.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(status.copy().withStyle(ChatFormatting.WHITE));
		}
		setCustomName(placa);
		setCustomNameVisible(true);
	}

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

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("Nome", nome());
		output.store("Carga", CARGA_CODEC, Map.copyOf(carga));
		if (obra != null) {
			output.store("Obra", BlockPos.CODEC, obra);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		nome = input.getStringOr("Nome", "");
		carga.clear();
		input.read("Carga", CARGA_CODEC).ifPresent(carga::putAll);
		obra = input.read("Obra", BlockPos.CODEC).orElse(null);
		atualizarPlaca();
	}
}
