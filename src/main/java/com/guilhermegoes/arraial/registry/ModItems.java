package com.guilhermegoes.arraial.registry;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.npc.ContratoItem;
import com.guilhermegoes.arraial.obra.LivroDeProjetosItem;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.Equippable;

public final class ModItems {
	/** Pick a project by right-clicking in the air, then right-click a Marco da Obra to apply it. */
	public static final Item LIVRO_DE_PROJETOS = register("livro_de_projetos", LivroDeProjetosItem::new,
			new Item.Properties().stacksTo(1));

	/** Hires a pedreiro: right-click the ground to make one appear. */
	public static final Item CONTRATO_PEDREIRO = register("contrato_pedreiro", ContratoItem::new,
			new Item.Properties().stacksTo(16));

	// ---- Mandioca and the casa de farinha
	/** Maniva: a cassava stem cutting; plant it on farmland. */
	public static final Item MANIVA = register("maniva",
			p -> new net.minecraft.world.item.BlockItem(ModBlocks.MANDIOCA, p), new Item.Properties().useItemDescriptionPrefix());
	public static final Item MANDIOCA = register("mandioca", Item::new, new Item.Properties());
	public static final Item FARINHA = register("farinha", Item::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.5F).build()));
	public static final Item BEIJU = register("beiju", Item::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.6F).build()));

	// ---- Sertão goods
	public static final Item RAPADURA = register("rapadura", Item::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.3F).build(),
					Consumables.defaultFood().consumeSeconds(0.8F).build()));
	/** Paçoca de pilão: farinha pounded with dried meat; the tropeiro's provision. */
	public static final Item PACOCA = register("pacoca", Item::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.8F).build()));
	public static final Item CUIA = register("cuia", Item::new, new Item.Properties().stacksTo(16));
	/** Garapa (cane juice) in a cuia: a quick drink that puts a spring in your step. */
	public static final Item CUIA_GARAPA = register("cuia_garapa", Item::new, new Item.Properties().stacksTo(1)
			.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.2F).alwaysEdible().build(),
					Consumables.defaultDrink().onConsume(new ApplyStatusEffectsConsumeEffect(
							new MobEffectInstance(MobEffects.SPEED, 20 * 45, 0))).build())
			.usingConvertsTo(CUIA));
	/** Quentão: the hot spiced drink of the festas juninas, in a cuia; warms you up (regeneration). */
	public static final Item QUENTAO = register("quentao", Item::new, new Item.Properties().stacksTo(1)
			.food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.4F).alwaysEdible().build(),
					Consumables.defaultDrink().onConsume(new ApplyStatusEffectsConsumeEffect(
							new MobEffectInstance(MobEffects.REGENERATION, 20 * 12, 0))).build())
			.usingConvertsTo(CUIA));
	/** Pé-de-moleque: rapadura with nuts, the sweet of the festas. */
	public static final Item PE_DE_MOLEQUE = register("pe_de_moleque", Item::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.5F).build()));
	/** Chapéu de couro: the sertanejo leather hat, worn on the head (3D model). */
	public static final Item CHAPEU_COURO = register("chapeu_couro", Item::new, new Item.Properties().stacksTo(1)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
					.setEquipSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER).build())
			.durability(110).repairable(net.minecraft.tags.ItemTags.REPAIRS_LEATHER_ARMOR)
			.attributes(net.minecraft.world.item.component.ItemAttributeModifiers.builder()
					.add(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, new net.minecraft.world.entity.ai.attributes.AttributeModifier(
							Arraial.id("chapeu_couro"), 2.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
							net.minecraft.world.entity.EquipmentSlotGroup.HEAD).build()));

	// ---- The vaqueiro's leather (with the chapéu de couro: a full set gives the vaqueiro's agility)
	public static final net.minecraft.world.item.equipment.ArmorMaterial COURO_VAQUEIRO = new net.minecraft.world.item.equipment.ArmorMaterial(
			12, java.util.Map.of(net.minecraft.world.item.equipment.ArmorType.BOOTS, 1, net.minecraft.world.item.equipment.ArmorType.LEGGINGS, 4,
					net.minecraft.world.item.equipment.ArmorType.CHESTPLATE, 5, net.minecraft.world.item.equipment.ArmorType.HELMET, 2,
					net.minecraft.world.item.equipment.ArmorType.BODY, 5),
			15, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER, 0.5F, 0.05F, net.minecraft.tags.ItemTags.REPAIRS_LEATHER_ARMOR,
			ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, Arraial.id("vaqueiro")));
	public static final Item GIBAO = register("gibao", Item::new, new Item.Properties()
			.humanoidArmor(COURO_VAQUEIRO, net.minecraft.world.item.equipment.ArmorType.CHESTPLATE));
	public static final Item PERNEIRA = register("perneira", Item::new, new Item.Properties()
			.humanoidArmor(COURO_VAQUEIRO, net.minecraft.world.item.equipment.ArmorType.LEGGINGS));
	public static final Item ALPERCATAS = register("alpercatas", Item::new, new Item.Properties()
			.humanoidArmor(COURO_VAQUEIRO, net.minecraft.world.item.equipment.ArmorType.BOOTS));

	// ---- Tools and weapons of the sertão (3D models made in Blockbench, see tools/blockbench)
	public static final net.minecraft.world.item.ToolMaterial FERRO_SERTANEJO = new net.minecraft.world.item.ToolMaterial(
			net.minecraft.tags.BlockTags.INCORRECT_FOR_IRON_TOOL, 300, 6.5F, 2.0F, 14, net.minecraft.tags.ItemTags.IRON_TOOL_MATERIALS);
	/** Facão: the machete; hits hard and clears the way. */
	public static final Item FACAO = register("facao", Item::new, new Item.Properties().sword(FERRO_SERTANEJO, 3.5F, -2.3F));
	/** Peixeira: the long knife; light and very fast. */
	public static final Item PEIXEIRA = register("peixeira", Item::new, new Item.Properties().sword(FERRO_SERTANEJO, 1.5F, -1.4F));
	/** Foice: reaps the ripe crops in a 3x3 and replants them. */
	public static final Item FOICE = register("foice", com.guilhermegoes.arraial.item.FoiceItem::new, new Item.Properties()
			.hoe(FERRO_SERTANEJO, -2.0F, -1.5F));

	// ---- Odds and ends
	/** Cantil: a leather flask; fill it at the water and drink to shake off hunger and poison. */
	public static final Item CANTIL = register("cantil", com.guilhermegoes.arraial.item.CantilItem::new, new Item.Properties().stacksTo(1));
	public static final Item CANTIL_CHEIO = register("cantil_cheio", Item::new, new Item.Properties().stacksTo(1)
			.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.6F).alwaysEdible().build(),
					Consumables.defaultDrink()
							.onConsume(new net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect(MobEffects.HUNGER))
							.onConsume(new net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect(MobEffects.POISON)).build())
			.usingConvertsTo(CANTIL));
	/** Saco de saque: what a bandit carried; open it. */
	public static final Item SACO_SAQUE = register("saco_saque", com.guilhermegoes.arraial.item.SacoDeSaqueItem::new, new Item.Properties().stacksTo(16));
	/** Comenda do Arraial: the honour of a child of the land, or of whoever cleared a hideout. */
	public static final Item COMENDA = register("comenda", Item::new, new Item.Properties().stacksTo(1)
			.rarity(net.minecraft.world.item.Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
	/** Garrafada: the boticário's herbal remedy; cures a sick morador (epidemia). */
	public static final Item REMEDIO = register("remedio", Item::new, new Item.Properties().stacksTo(16));
	/** Guia do Arraial: right-click to open a book explaining the trades, the villages, safety and crises. */
	public static final Item GUIA = register("guia", com.guilhermegoes.arraial.vila.GuiaItem::new, new Item.Properties().stacksTo(1));
	/** Réis: the arraial's money, used at the feira. */
	public static final Item REIS = register("reis", Item::new, new Item.Properties());

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Arraial.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
	}

	public static void init() {
	}

	private ModItems() {
	}
}
