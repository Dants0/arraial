package com.guilhermegoes.arraial.obra;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A building turned into an ordered list of steps ("etapas"), already rotated
 * and positioned in the world. Air steps clear the footprint; the rest place blocks.
 */
public final class PlanoDeObra {
	public record Etapa(BlockPos pos, BlockState state) {
		public boolean limpeza() {
			return state.isAir();
		}
	}

	/** What a step costs: an item and how many of it. Null means free. */
	public record Custo(Item item, int quantidade) {
	}

	private final List<Etapa> etapas;
	private final BoundingBox area;

	private PlanoDeObra(List<Etapa> etapas, BoundingBox area) {
		this.etapas = etapas;
		this.area = area;
	}

	public List<Etapa> etapas() {
		return etapas;
	}

	public BoundingBox area() {
		return area;
	}

	/**
	 * Loads the structure and lays it out in front of the marker: the building
	 * starts one block ahead of the marker in the direction it faces, centred on it,
	 * with its floor (layer 0) replacing the ground the marker stands on.
	 */
	/** A plan from ready-made steps (used by procedural projects like the {@link Muralha}). */
	static PlanoDeObra de(List<Etapa> etapas, BoundingBox area) {
		return new PlanoDeObra(List.copyOf(etapas), area);
	}

	public static Optional<PlanoDeObra> criar(ServerLevel level, Catalogo.Projeto projeto, BlockPos marco, Direction frente) {
		return criar(level, projeto, marco, frente, 0);
	}

	/**
	 * As above, with the arraial's wood ({@code madeira}: index in Receitas.MADEIRAS): the oak logs,
	 * planks, doors, fences, slabs and stairs of the drawing are built in that wood, so a savanna
	 * village raises acacia houses with the acacia its lenhador cuts.
	 */
	public static Optional<PlanoDeObra> criar(ServerLevel level, Catalogo.Projeto projeto, BlockPos marco, Direction frente, int madeira) {
		if (Muralha.e(projeto)) {
			return Muralha.planejar(level, marco, 0, madeiraDaVila(level, marco));
		}
		Optional<StructureTemplate> template = level.getStructureTemplateManager().get(projeto.estrutura());
		if (template.isEmpty()) {
			return Optional.empty();
		}

		CompoundTag tag = template.get().save(new CompoundTag());
		ListTag size = tag.getListOrEmpty("size");
		int largura = size.getIntOr(0, 1);

		List<BlockState> paleta = new ArrayList<>();
		ListTag paletaTag = tag.getListOrEmpty("palette");
		for (int i = 0; i < paletaTag.size(); i++) {
			paleta.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, paletaTag.getCompoundOrEmpty(i)));
		}

		Rotation rotacao = rotacaoPara(frente);
		BlockPos base = marco.below();
		List<Etapa> limpeza = new ArrayList<>();
		List<Etapa> construcao = new ArrayList<>();
		BoundingBox area = null;

		ListTag blocos = tag.getListOrEmpty("blocks");
		for (int i = 0; i < blocos.size(); i++) {
			CompoundTag bloco = blocos.getCompoundOrEmpty(i);
			ListTag p = bloco.getListOrEmpty("pos");
			int indice = bloco.getIntOr("state", 0);
			if (indice < 0 || indice >= paleta.size()) {
				continue;
			}
			BlockState state = trocarMadeira(paleta.get(indice), madeira);
			if (state.is(net.minecraft.world.level.block.Blocks.STRUCTURE_VOID)) {
				continue;
			}
			BlockPos local = new BlockPos(p.getIntOr(0, 0) - largura / 2, p.getIntOr(1, 0), p.getIntOr(2, 0) + 1);
			BlockPos mundo = base.offset(local.rotate(rotacao));
			Etapa etapa = new Etapa(mundo, state.rotate(rotacao));
			(etapa.limpeza() ? limpeza : construcao).add(etapa);
			area = area == null ? BoundingBox.fromCorners(mundo, mundo) : area.encapsulate(mundo);
		}

		if (construcao.isEmpty()) {
			// A blueprint with nothing to place means the file could not be read (e.g. wrong NBT format).
			return Optional.empty();
		}

		// Clear from the top down, then build from the bottom up.
		// Within a layer, full blocks go first so torches, doors and stairs have support.
		limpeza.sort(Comparator.comparingInt((Etapa e) -> -e.pos().getY()));
		construcao.sort(Comparator.comparingInt((Etapa e) -> e.pos().getY())
				.thenComparingInt(e -> e.state().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) ? 0 : 1));

		List<Etapa> todas = new ArrayList<>(limpeza);
		todas.addAll(construcao);
		return Optional.of(new PlanoDeObra(List.copyOf(todas), area == null ? BoundingBox.fromCorners(marco, marco) : area));
	}

	/** The building's front (z = 0 in the file) faces back towards the marker. */
	private static Rotation rotacaoPara(Direction frente) {
		return switch (frente) {
			case WEST -> Rotation.CLOCKWISE_90;
			case NORTH -> Rotation.CLOCKWISE_180;
			case EAST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/**
	 * Blocks the pedreiro makes on the spot from raw materials instead of fetching them ready:
	 * ground (farmland, paths, grass) is just earth, and the workstations are built from wood or
	 * stone, so the arraial never needs a lenhador to build the lenhador's own shed.
	 */
	private static final Map<net.minecraft.world.level.block.Block, Custo> CUSTOS_ESPECIAIS = Map.ofEntries(
			// Earth is dug on the site itself: soil, paths and tilled ground cost nothing from the armazém.
			Map.entry(net.minecraft.world.level.block.Blocks.DIRT, new Custo(Items.DIRT, 0)),
			Map.entry(net.minecraft.world.level.block.Blocks.FARMLAND, new Custo(Items.DIRT, 0)),
			// The era buildings: the capela's bell, glass and altar, the fountain's dressed stone.
			Map.entry(net.minecraft.world.level.block.Blocks.BELL, new Custo(Items.IRON_INGOT, 4)),
			Map.entry(net.minecraft.world.level.block.Blocks.GLASS_PANE, new Custo(Items.SAND, 1)),
			Map.entry(net.minecraft.world.level.block.Blocks.CHISELED_STONE_BRICKS, new Custo(Items.COBBLESTONE, 1)),
			Map.entry(net.minecraft.world.level.block.Blocks.STONE_BRICKS, new Custo(Items.COBBLESTONE, 1)),
			Map.entry(net.minecraft.world.level.block.Blocks.STONE_BRICK_WALL, new Custo(Items.COBBLESTONE, 1)),
			Map.entry(net.minecraft.world.level.block.Blocks.CANDLE, new Custo(Items.HONEYCOMB, 0)),
			Map.entry(net.minecraft.world.level.block.Blocks.DIRT_PATH, new Custo(Items.DIRT, 0)),
			// The fazendas and the new era buildings: cane and coffee seedlings come with the plan; lime from clay,
			// tiles from bricks, soapstone from the quarry, adobe from mud bricks, pews and altars from any planks.
			Map.entry(net.minecraft.world.level.block.Blocks.SUGAR_CANE, new Custo(Items.SUGAR_CANE, 0)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.CAFEEIRO, new Custo(Items.STICK, 0)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.CAL, new Custo(Items.CLAY_BALL, 1)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.TELHA_ESCADA, new Custo(Items.BRICK, 1)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.TELHA_LAJE, new Custo(Items.BRICK, 1)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.PEDRA_SABAO, new Custo(Items.COBBLESTONE, 1)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.ADOBE, new Custo(Items.MUD_BRICKS, 1)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.MOENDA, new Custo(Items.OAK_LOG, 3)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.TERREIRO, new Custo(Items.BRICK, 4)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.BANCADA_ENTALHE, new Custo(Items.OAK_PLANKS, 4)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.AMARRA, new Custo(Items.OAK_LOG, 1)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.GUINDASTE, new Custo(Items.OAK_LOG, 3)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.MO, new Custo(Items.COBBLESTONE, 4)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.RODA_DAGUA, new Custo(Items.OAK_LOG, 2)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.MONJOLO, new Custo(Items.OAK_LOG, 2)),
			Map.entry(net.minecraft.world.level.block.Blocks.SPRUCE_STAIRS, new Custo(Items.OAK_PLANKS, 1)),
			Map.entry(net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS, new Custo(Items.OAK_PLANKS, 1)),
			Map.entry(net.minecraft.world.level.block.Blocks.BARREL, new Custo(Items.OAK_PLANKS, 4)),
			Map.entry(net.minecraft.world.level.block.Blocks.DARK_OAK_DOOR, new Custo(Items.OAK_DOOR, 1)),
			Map.entry(net.minecraft.world.level.block.Blocks.GRASS_BLOCK, new Custo(Items.DIRT, 0)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.CEPO, new Custo(Items.OAK_LOG, 2)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.BALAIO, new Custo(Items.OAK_PLANKS, 4)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.POSTO_GUARDA, new Custo(Items.OAK_PLANKS, 4)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.COCHO, new Custo(Items.OAK_PLANKS, 5)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.GAMELA, new Custo(Items.OAK_PLANKS, 3)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.BANCADA_CANTEIRO, new Custo(Items.OAK_LOG, 2)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.FORNO_OLARIA, new Custo(Items.COBBLESTONE, 8)),
			Map.entry(net.minecraft.world.level.block.Blocks.LECTERN, new Custo(Items.OAK_PLANKS, 4)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.FORNO_FARINHA, new Custo(Items.MUD_BRICKS, 4)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.BANCA_FEIRA, new Custo(Items.OAK_PLANKS, 5)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.CANGALHA, new Custo(Items.OAK_PLANKS, 3)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.BALCAO_BOTICA, new Custo(Items.OAK_PLANKS, 5)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.LAMPIAO, new Custo(Items.TORCH, 1)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.COVO, new Custo(Items.STICK, 4)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.VARAL_CACA, new Custo(Items.STICK, 5)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.PRANCHETA, new Custo(Items.OAK_PLANKS, 4)),
			Map.entry(com.guilhermegoes.arraial.registry.ModBlocks.ESCRIVANINHA, new Custo(Items.OAK_PLANKS, 5)));

	private static BlockState trocarMadeira(BlockState state, int madeira) {
		var lista = com.guilhermegoes.arraial.trabalho.Receitas.MADEIRAS;
		if (madeira <= 0 || madeira >= lista.size()) {
			return state;
		}
		var carvalho = lista.get(0);
		var nova = lista.get(madeira);
		Item[][] pares = {{carvalho.tora(), nova.tora()}, {carvalho.tabua(), nova.tabua()}, {carvalho.porta(), nova.porta()},
				{carvalho.cerca(), nova.cerca()}, {carvalho.laje(), nova.laje()}, {carvalho.escada(), nova.escada()}, {carvalho.portao(), nova.portao()}};
		for (Item[] par : pares) {
			if (state.is(net.minecraft.world.level.block.Block.byItem(par[0]))) {
				return net.minecraft.world.level.block.Block.byItem(par[1]).withPropertiesOf(state);
			}
		}
		return state;
	}

	/** Index in {@link com.guilhermegoes.arraial.trabalho.Receitas#MADEIRAS} of the wood the arraial near {@code pos} uses most. */
	static int madeiraDaVila(ServerLevel level, BlockPos pos) {
		BlockPos vila = com.guilhermegoes.arraial.vila.Vilas.maisProxima(level, pos);
		var inv = com.guilhermegoes.arraial.vila.Vilas.inventario(com.guilhermegoes.arraial.vila.Vilas.armazens(level, vila));
		return com.guilhermegoes.arraial.trabalho.Receitas.MADEIRAS.indexOf(com.guilhermegoes.arraial.trabalho.Estoque.madeiraDaVila(inv));
	}

	public static Custo custo(BlockState state) {
		if (state.isAir()) {
			return null;
		}
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
				&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			return null; // the lower half already paid for the door / tall plant
		}
		if (state.hasProperty(BlockStateProperties.BED_PART)
				&& state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
			return null;
		}
		Custo especial = CUSTOS_ESPECIAIS.get(state.getBlock());
		if (especial != null) {
			return especial.quantidade() == 0 ? null : especial;
		}
		Item item = state.getBlock().asItem();
		if (item == Items.AIR) {
			return null; // water, fire and other blocks without an item are free
		}
		int quantidade = state.hasProperty(BlockStateProperties.SLAB_TYPE)
				&& state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE ? 2 : 1;
		return new Custo(item, quantidade);
	}

	/** Total materials for the steps from {@code inicio} to the end. */
	public Map<Item, Integer> materiais(int inicio) {
		Map<Item, Integer> total = new LinkedHashMap<>();
		for (int i = Math.max(0, inicio); i < etapas.size(); i++) {
			Custo c = custo(etapas.get(i).state());
			if (c != null) {
				total.merge(c.item(), c.quantidade(), Integer::sum);
			}
		}
		return total;
	}

	/** True when the world already matches this step. */
	public static boolean pronta(ServerLevel level, Etapa etapa) {
		BlockState atual = level.getBlockState(etapa.pos());
		if (etapa.limpeza()) {
			return atual.isAir() || !atual.getFluidState().isEmpty();
		}
		return com.guilhermegoes.arraial.vila.Era.equivale(etapa.state().getBlock(), atual.getBlock());
	}
}
