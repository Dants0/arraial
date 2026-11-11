package com.guilhermegoes.arraial.registry;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.obra.CanteiroBlock;
import com.guilhermegoes.arraial.vila.ArraialBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	/** "Marco da Obra": the construction-site marker the pedreiro works from. */
	public static final Block CANTEIRO = register("canteiro",
			new CanteiroBlock(props("canteiro")
					.mapColor(MapColor.WOOD)
					.strength(1.5F)
					.sound(SoundType.WOOD)
					.noOcclusion()
					.noCollision()));

	/** "Cruzeiro": the wooden cross that founds an arraial and marks its centre. */
	public static final Block CRUZEIRO = register("cruzeiro",
			new ArraialBlock(props("cruzeiro")
					.mapColor(MapColor.WOOD)
					.strength(2.0F)
					.sound(SoundType.WOOD)
					.noOcclusion()));

	/** "Armazém": the arraial's shared storehouse (54 slots). Workers deposit here; builders and the hungry take from it. */
	public static final Block ARMAZEM = register("armazem",
			new com.guilhermegoes.arraial.vila.ArmazemBlock(props("armazem")
					.mapColor(MapColor.WOOD)
					.strength(2.5F)
					.sound(SoundType.WOOD)));

	/** Workstation of the lenhador (woodcutter). */
	public static final Block CEPO = register("cepo",
			new Block(props("cepo").mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));

	/** Workstation of the roceiro (farmer): a big woven basket. */
	public static final Block BALAIO = register("balaio",
			new Block(props("balaio").mapColor(MapColor.COLOR_YELLOW).strength(1.0F).sound(SoundType.GRASS).noOcclusion()));

	/** Workstation of the oleiro (potter): a brick kiln. */
	public static final Block FORNO_OLARIA = register("forno_olaria",
			new Block(props("forno_olaria").mapColor(MapColor.COLOR_RED).strength(3.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)));


	/** Workstation of the soldado: a rack of weapons by the guard post. */
	public static final Block POSTO_GUARDA = register("posto_guarda",
			new Block(props("posto_guarda").mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));

	/** Workstation of the pastor (herder): a feeding trough. */
	public static final Block COCHO = register("cocho",
			new Block(props("cocho").mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion()));

	/** Workstation of the barreiro (clay digger): a tub of wet clay; the pit opens in front of it. */
	public static final Block GAMELA = register("gamela",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("gamela").mapColor(MapColor.CLAY).strength(1.0F).sound(SoundType.MUD).noOcclusion()));

	/** Workstation of the cavouqueiro (quarryman): a stonecutting bench; the quarry opens in front of it. */
	public static final Block BANCADA_CANTEIRO = register("bancada_canteiro",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("bancada_canteiro").mapColor(MapColor.STONE).strength(2.5F)
					.requiresCorrectToolForDrops().sound(SoundType.STONE)));

	/** Workstation of the pescador: a covo, the woven fish trap. */
	public static final Block COVO = register("covo",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("covo").mapColor(MapColor.COLOR_YELLOW).strength(1.0F)
					.sound(SoundType.BAMBOO_WOOD).noOcclusion()));

	/** Workstation of the caçador: a rack with hides drying. */
	public static final Block VARAL_CACA = register("varal_caca",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("varal_caca").mapColor(MapColor.COLOR_BROWN).strength(1.0F)
					.sound(SoundType.WOOD).noOcclusion()));

	/** Workstation of the engenheiro: the drafting board with the arraial's plans. */
	public static final Block PRANCHETA = register("prancheta",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("prancheta").mapColor(MapColor.WOOD).strength(1.5F)
					.sound(SoundType.WOOD).noOcclusion()));

	/** Workstation of relações públicas: a writing desk for letters to the neighbours. */
	public static final Block ESCRIVANINHA = register("escrivaninha",
			new com.guilhermegoes.arraial.vila.EscrivaninhaBlock(props("escrivaninha").mapColor(MapColor.WOOD).strength(1.5F)
					.sound(SoundType.WOOD).noOcclusion()));

	/** Lampião: an oil lamp of tin and glass, standing or hanging, with a flickering flame. */
	public static final Block LAMPIAO = register("lampiao",
			new net.minecraft.world.level.block.LanternBlock(props("lampiao").mapColor(MapColor.METAL).forceSolidOn().strength(2.0F)
					.sound(SoundType.LANTERN).lightLevel(s -> 15).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)));

	/** Mandioca crop; its item is the maniva (registered in ModItems), so no block item here. */
	public static final Block MANDIOCA = registerSemItem("mandioca",
			new com.guilhermegoes.arraial.trabalho.MandiocaBlock(props("mandioca").mapColor(MapColor.PLANT).noCollision().randomTicks()
					.instabreak().sound(SoundType.CROP).pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)));

	/** Workstation of the farinheiro: the clay oven with its big copper pan where the farinha is toasted. */
	public static final Block FORNO_FARINHA = register("forno_farinha",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("forno_farinha").mapColor(MapColor.TERRACOTTA_ORANGE).strength(2.0F)
					.sound(SoundType.MUD_BRICKS).noOcclusion()));

	/** Workstation of the feirante: a market stall with an awning. */
	public static final Block BANCA_FEIRA = register("banca_feira",
			new com.guilhermegoes.arraial.vila.BancaFeiraBlock(props("banca_feira").mapColor(MapColor.WOOD).strength(1.5F)
					.sound(SoundType.WOOD).noOcclusion()));

	/** Workstation of the tropeiro: the cangalha, the pack saddle of the mule troop. */
	public static final Block CANGALHA = register("cangalha",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("cangalha").mapColor(MapColor.COLOR_BROWN).strength(1.0F)
					.sound(SoundType.WOOD).noOcclusion()));

	/** Workstation of the boticário: the counter with jars of remedies. */
	public static final Block BALCAO_BOTICA = register("balcao_botica",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("balcao_botica").mapColor(MapColor.WOOD).strength(1.5F)
					.sound(SoundType.WOOD).noOcclusion()));

	/** Urna: the ballot box where players deal with the arraial's council. */
	public static final Block URNA = register("urna",
			new com.guilhermegoes.arraial.vila.UrnaBlock(props("urna").mapColor(MapColor.WOOD).strength(1.5F)
					.sound(SoundType.WOOD).noOcclusion()));

	/** Marco de Fronteira: extends the arraial's territory (see Territorio). */
	public static final Block MARCO_FRONTEIRA = register("marco_fronteira",
			new com.guilhermegoes.arraial.vila.MarcoFronteiraBlock(props("marco_fronteira").mapColor(MapColor.STONE).strength(2.0F)
					.requiresCorrectToolForDrops().sound(SoundType.STONE).noOcclusion()));

	/** Bandeirinhas: the string of little flags of the festas juninas (decoration). */
	public static final Block BANDEIRINHAS = register("bandeirinhas",
			new com.guilhermegoes.arraial.vila.BandeirinhasBlock(props("bandeirinhas").mapColor(MapColor.COLOR_RED).noCollision().instabreak()
					.sound(SoundType.WOOL).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)));

	// ---------------------------------------------------------------- colonial building blocks (see vila.Era)

	/** Taipa: wattle and daub, clay packed on a lattice of sticks; the colonial wall. */
	public static final Block TAIPA = register("taipa",
			new Block(props("taipa").mapColor(MapColor.DIRT).strength(1.2F).sound(SoundType.MUD_BRICKS)));

	/** Telha: curved clay roof tiles (the Imperial roof). */
	public static final Block TELHA = register("telha",
			new Block(props("telha").mapColor(MapColor.TERRACOTTA_ORANGE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()
					.sound(SoundType.DECORATED_POT)));

	public static final Block TELHA_ESCADA = register("telha_escada",
			new net.minecraft.world.level.block.StairBlock(TELHA.defaultBlockState(), props("telha_escada")
					.mapColor(MapColor.TERRACOTTA_ORANGE).strength(1.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.DECORATED_POT)));

	public static final Block TELHA_LAJE = register("telha_laje",
			new net.minecraft.world.level.block.SlabBlock(props("telha_laje").mapColor(MapColor.TERRACOTTA_ORANGE).strength(1.5F, 6.0F)
					.requiresCorrectToolForDrops().sound(SoundType.DECORATED_POT)));

	/** Parede caiada: whitewashed lime plaster (the Republican wall). */
	public static final Block CAL = register("cal",
			new Block(props("cal").mapColor(MapColor.SNOW).strength(1.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)));

	/** Azulejo: blue and white glazed tiles, on the lower part of Republican façades. */
	public static final Block AZULEJO = register("azulejo",
			new Block(props("azulejo").mapColor(MapColor.COLOR_BLUE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()
					.sound(SoundType.DECORATED_POT)));

	// ---------------------------------------------------------------- the fazendas (agropecuária)

	/** Cafeeiro: the coffee bush; its item is the coffee cherry (ModItems.CAFE_CEREJA). */
	public static final Block CAFEEIRO = registerSemItem("cafeeiro",
			new com.guilhermegoes.arraial.trabalho.CafeeiroBlock(props("cafeeiro").mapColor(MapColor.PLANT).noCollision().randomTicks()
					.sound(SoundType.SWEET_BERRY_BUSH).pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)));

	/** Moenda: the sugar mill of the engenho, workstation of the mestre de engenho. */
	public static final Block MOENDA = register("moenda",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("moenda").mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));

	/** Terreiro de café: the drying yard, workstation of the cafeicultor. */
	public static final Block TERREIRO = register("terreiro",
			new com.guilhermegoes.arraial.trabalho.TerreiroBlock(props("terreiro").mapColor(MapColor.TERRACOTTA_BROWN).strength(1.5F).sound(SoundType.STONE).noOcclusion()));

	// ---------------------------------------------------------------- the Baroque and the Spanish missions

	/** Bancada de entalhe: the carver's bench, workstation of the entalhador. */
	public static final Block BANCADA_ENTALHE = register("bancada_entalhe",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("bancada_entalhe").mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));

	/** Amarra: the mooring post of the trapiche, workstation of the barqueiro. */
	public static final Block AMARRA = register("amarra",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("amarra").mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));

	/** Guindaste: the wooden crane of the porto, workstation of the estivador. */
	public static final Block GUINDASTE = register("guindaste",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("guindaste").mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));

	/** Mó: the millstones of the moinho d'água, workstation of the moleiro. */
	public static final Block MO = register("mo",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("mo").mapColor(MapColor.STONE).strength(2.5F).sound(SoundType.STONE).noOcclusion()));
	/** Roda d'água: the mill's paddle wheel; it turns while it stands in water. */
	public static final Block RODA_DAGUA = register("roda_dagua",
			new com.guilhermegoes.arraial.obra.RodaDaguaBlock(props("roda_dagua").mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));
	/** Monjolo: the water-driven pestle that pounds farinha and coffee by itself. */
	public static final Block MONJOLO = register("monjolo",
			new com.guilhermegoes.arraial.obra.MonjoloBlock(props("monjolo").mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));

	/** Bilheteria: the ticket window of the estação, workstation of the ferroviário. */
	public static final Block BILHETERIA = register("bilheteria",
			new com.guilhermegoes.arraial.obra.PostoDirecionalBlock(props("bilheteria").mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));

	/** Pedra-sabão: soapstone, the grey-green stone of the Baroque of Minas. */
	public static final Block PEDRA_SABAO = register("pedra_sabao",
			new Block(props("pedra_sabao").mapColor(MapColor.COLOR_GRAY).strength(1.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)));

	/** Talha dourada: gilded wood carving, the gold of the Baroque altars. */
	public static final Block TALHA_DOURADA = register("talha_dourada",
			new Block(props("talha_dourada").mapColor(MapColor.GOLD).strength(2.0F).sound(SoundType.WOOD)));

	/** Adobe: sun-dried mud and straw bricks, the wall of the Spanish missions. */
	public static final Block ADOBE = register("adobe",
			new Block(props("adobe").mapColor(MapColor.TERRACOTTA_ORANGE).strength(1.2F).sound(SoundType.MUD_BRICKS)));

	private static BlockBehaviour.Properties props(String name) {
		return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, Arraial.id(name)));
	}

	private static Block register(String name, Block block) {
		Registry.register(BuiltInRegistries.BLOCK, Arraial.id(name), block);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Arraial.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	private static Block registerSemItem(String name, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK, Arraial.id(name), block);
	}

	public static void init() {
	}

	private ModBlocks() {
	}
}
