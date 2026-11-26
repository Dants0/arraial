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
	// ---- The fazendas: coffee, cheese and jerked beef
	/** Coffee cherry: picked from the cafeeiro; planted on dirt it grows a new bush. */
	public static final Item CAFE_CEREJA = register("cafe_cereja",
			p -> new net.minecraft.world.item.BlockItem(ModBlocks.CAFEEIRO, p), new Item.Properties().useItemDescriptionPrefix());
	/** Roasted coffee beans: the cafeicultor dries and roasts the cherries on the terreiro. */
	public static final Item CAFE_TORRADO = register("cafe_torrado", Item::new, new Item.Properties());
	/** Cafezinho in a cuia: strong and sweet; you work faster for a while. */
	public static final Item CAFEZINHO = register("cafezinho", Item::new, new Item.Properties().stacksTo(1)
			.food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.2F).alwaysEdible().build(),
					Consumables.defaultDrink().onConsume(new ApplyStatusEffectsConsumeEffect(
							new MobEffectInstance(MobEffects.HASTE, 20 * 90, 0))).build())
			.usingConvertsTo(CUIA));
	/** Queijo coalho: the cheese of the backlands, from the curral's milk. */
	public static final Item QUEIJO_COALHO = register("queijo_coalho", Item::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.6F).build()));
	/** Charque: salted, sun-dried beef; keeps on long journeys. */
	public static final Item CHARQUE = register("charque", Item::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.8F).build()));
	/** Sal grosso: coarse salt brought by the ships; cures meat into charque. */
	public static final Item SAL = register("sal", Item::new, new Item.Properties());
	/** Chita: printed cotton from overseas, the cloth of the festa junina. */
	public static final Item CHITA = register("chita", Item::new, new Item.Properties());
	/** Farinha de trigo: wheat ground at the moinho d'água; bakes into bread, two for one wheat. */
	public static final Item FARINHA_TRIGO = register("farinha_trigo", Item::new, new Item.Properties());
	/** Ferradura: the ferreiro's horseshoes; with them in the armazém the troop travels quicker. */
	public static final Item FERRADURA = register("ferradura", Item::new, new Item.Properties());
	/** Pão de queijo: the padeiro's cheese bread (cassava flour, queijo coalho and egg). */
	public static final Item PAO_DE_QUEIJO = register("pao_de_queijo", Item::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.6F).build()));
	/** Algodão: a cotton boll; planted, it grows into an algodoeiro. */
	public static final Item ALGODAO = register("algodao",
			p -> new net.minecraft.world.item.BlockItem(ModBlocks.ALGODOEIRO, p), new Item.Properties().useItemDescriptionPrefix());
	/** Pano: cotton cloth woven by the tecelão. */
	public static final Item PANO = register("pano", Item::new, new Item.Properties());
	/** Roupa de festa: a chita dress made by the alfaiate; a well-dressed arraial enjoys its festas more. */
	public static final Item ROUPA_FESTA = register("roupa_festa", Item::new, new Item.Properties().stacksTo(16));
	/** Couro de jacaré: the caiman's scaled hide, a luxury the ships pay well for. */
	public static final Item COURO_JACARE = register("couro_jacare", Item::new, new Item.Properties());
	/** Pena de arara: a macaw feather (they drop now and then); three make festa bunting. */
	public static final Item PENA_ARARA = register("pena_arara", Item::new, new Item.Properties());
	/** Pele de onça: the jaguar's coat; laid on the floor it is a rug. */
	public static final Item PELE_ONCA = register("pele_onca",
			p -> new net.minecraft.world.item.BlockItem(ModBlocks.TAPETE_ONCA, p), new Item.Properties().useItemDescriptionPrefix());
	// ---- The sertanejo's tools (see item.Ferramentas)
	public static final Item BATEIA = register("bateia", com.guilhermegoes.arraial.item.Ferramentas.Bateia::new, new Item.Properties().durability(160));
	public static final Item ENXADAO = register("enxadao", com.guilhermegoes.arraial.item.Ferramentas.Enxadao::new, new Item.Properties().durability(400));
	public static final Item LACO = register("laco", com.guilhermegoes.arraial.item.Ferramentas.Laco::new, new Item.Properties().durability(96));
	public static final Item TARRAFA = register("tarrafa", com.guilhermegoes.arraial.item.Ferramentas.Tarrafa::new, new Item.Properties().durability(80));
	public static final Item BERRANTE = register("berrante", com.guilhermegoes.arraial.item.Ferramentas.Berrante::new, new Item.Properties().durability(200));
	/** Seixo: a river pebble, the estilingue's shot (sifted from gravel, or knapped from cobblestone). */
	public static final Item SEIXO = register("seixo", Item::new, new Item.Properties());
	public static final Item ESTILINGUE = register("estilingue", com.guilhermegoes.arraial.item.Ferramentas.Estilingue::new, new Item.Properties().durability(250));
	public static final Item PENEIRA = register("peneira", com.guilhermegoes.arraial.item.Ferramentas.Peneira::new, new Item.Properties().durability(200));
	public static final Item CANDEEIRO = register("candeeiro", com.guilhermegoes.arraial.item.Ferramentas.Candeeiro::new,
			new Item.Properties().durability(720).repairable(net.minecraft.world.item.Items.COAL));
	/** Cabaça: a dried gourd, empty; filled with cachaça it is four swigs. */
	public static final Item CABACA = register("cabaca", Item::new, new Item.Properties().stacksTo(16));
	public static final Item CABACA_CACHACA = register("cabaca_cachaca", com.guilhermegoes.arraial.item.Ferramentas.Cabaca::new, new Item.Properties().durability(4));
	public static final Item CHICOTE = register("chicote", com.guilhermegoes.arraial.item.Ferramentas.Chicote::new, new Item.Properties().durability(240));
	/** Pé-de-moleque: rapadura with nuts, the sweet of the festas. */
	public static final Item PE_DE_MOLEQUE = register("pe_de_moleque", Item::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.5F).build()));
	// ---- Dishes of the cooking pot (Farmer's Delight): their recipes only load with that mod
	/** Baião de dois: rice with queijo coalho, charque and onion. */
	public static final Item BAIAO_DE_DOIS = prato("baiao_de_dois", 14, 0.75F);
	/** Moqueca: fish stewed with tomato and onion, thickened with farinha (pirão). */
	public static final Item MOQUECA = prato("moqueca", 12, 0.8F);
	/** Vaca atolada: beef "stuck in the mud" of mandioca. */
	public static final Item VACA_ATOLADA = prato("vaca_atolada", 14, 0.75F);
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
	public static final Item FACAO = register("facao", com.guilhermegoes.arraial.item.Ferramentas.Facao::new, new Item.Properties().sword(FERRO_SERTANEJO, 3.5F, -2.3F));
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
	/** Mapa da Região: opens the map of the arraiais around (lands, roads, wars, ruins). */
	public static final Item MAPA_REGIAO = register("mapa_regiao", com.guilhermegoes.arraial.item.MapaRegiaoItem::new, new Item.Properties().stacksTo(1));
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

	// ---- Spawn eggs of the Brazilian fauna
	public static final Item CAPIVARA_SPAWN_EGG = register("capivara_spawn_egg", net.minecraft.world.item.SpawnEggItem::new, new Item.Properties().spawnEgg(ModEntities.CAPIVARA));
	public static final Item TAMANDUA_SPAWN_EGG = register("tamandua_spawn_egg", net.minecraft.world.item.SpawnEggItem::new, new Item.Properties().spawnEgg(ModEntities.TAMANDUA));
	public static final Item JACARE_SPAWN_EGG = register("jacare_spawn_egg", net.minecraft.world.item.SpawnEggItem::new, new Item.Properties().spawnEgg(ModEntities.JACARE));
	public static final Item ONCA_SPAWN_EGG = register("onca_spawn_egg", net.minecraft.world.item.SpawnEggItem::new, new Item.Properties().spawnEgg(ModEntities.ONCA));
	public static final Item ARARA_SPAWN_EGG = register("arara_spawn_egg", net.minecraft.world.item.SpawnEggItem::new, new Item.Properties().spawnEgg(ModEntities.ARARA));

	private static Item prato(String id, int nutricao, float saturacao) {
		return register(id, Item::new, new Item.Properties().stacksTo(16).usingConvertsTo(net.minecraft.world.item.Items.BOWL)
				.food(new FoodProperties.Builder().nutrition(nutricao).saturationModifier(saturacao).build()));
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Arraial.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
	}

	public static void init() {
	}

	private ModItems() {
	}
}
