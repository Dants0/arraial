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
	/** Chapéu de couro: the sertanejo leather hat, worn on the head (3D model). */
	public static final Item CHAPEU_COURO = register("chapeu_couro", Item::new, new Item.Properties().stacksTo(1)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).build()));
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
