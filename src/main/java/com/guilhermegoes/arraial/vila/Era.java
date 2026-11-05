package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import com.guilhermegoes.arraial.obra.PlanoDeObra;
import com.guilhermegoes.arraial.registry.ModPois;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The arraial's era: how it builds. It modernises as it grows richer and larger:
 * <ul>
 * <li><b>Colonial</b>: taipa (mud bricks), wood, thatch, dirt streets, torch posts.</li>
 * <li><b>Imperial</b> (a Vila with an olaria and money): taipa becomes brick, roofs become clay
 * tiles, the streets get cobbles (calçamento pé-de-moleque), lamp posts get lanterns; the Capela
 * can be built.</li>
 * <li><b>Republicana</b> (a prosperous Vila with a Capela and a Cartório): walls are whitewashed,
 * foundations of dressed stone, streets of stone setts; the Chafariz (fountain) on the square.</li>
 * </ul>
 * Each morning the pedreiros reform a couple of finished buildings and a stretch of street to the
 * current era, with materials from the armazém (bricks, white terracotta, cobblestone, stone bricks).
 */
public enum Era {
	COLONIAL, IMPERIAL, REPUBLICANA;

	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	public Component nome() {
		return Component.translatable("arraial.era." + id());
	}

	public static Era de(int i) {
		return i >= 0 && i < values().length ? values()[i] : COLONIAL;
	}

	public static Era de(String id) {
		for (Era e : values()) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		return COLONIAL;
	}

	// ---------------------------------------------------------------- what each era builds with

	/** The block a planned (colonial) block becomes in this era; the same block if unchanged. */
	public Block troca(Block b) {
		if (this == COLONIAL) {
			return b;
		}
		if (b == Blocks.MUD_BRICKS || b == Blocks.PACKED_MUD) {
			return this == IMPERIAL ? Blocks.BRICKS : Blocks.DYED_TERRACOTTA.white();
		}
		if (b == Blocks.BRICKS && this == REPUBLICANA) {
			return Blocks.DYED_TERRACOTTA.white();
		}
		if (b == Blocks.MUD_BRICK_STAIRS) {
			return Blocks.BRICK_STAIRS;
		}
		if (b == Blocks.MUD_BRICK_SLAB) {
			return Blocks.BRICK_SLAB;
		}
		if (b == Blocks.COBBLESTONE && this == REPUBLICANA) {
			return Blocks.STONE_BRICKS;
		}
		if (b == Blocks.TORCH) {
			return Blocks.LANTERN;
		}
		// Roofs of wood become clay tiles.
		String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
		if (id.endsWith("_stairs") && !id.contains("brick") && !id.contains("stone")) {
			return Blocks.BRICK_STAIRS;
		}
		if (id.endsWith("_slab") && !id.contains("brick") && !id.contains("stone")) {
			return Blocks.BRICK_SLAB;
		}
		return b;
	}

	/** The item the new block costs from the armazém (null: free, e.g. a lantern from the torch). */
	static Item custo(Block novo) {
		if (novo == Blocks.BRICKS || novo == Blocks.BRICK_STAIRS || novo == Blocks.BRICK_SLAB) {
			return Items.BRICK;
		}
		if (novo == Blocks.DYED_TERRACOTTA.white()) {
			return Items.CLAY_BALL;
		}
		if (novo == Blocks.STONE_BRICKS) {
			return Items.COBBLESTONE;
		}
		return null;
	}

	/** The street surface of this era. */
	public Block rua() {
		return switch (this) {
			case COLONIAL -> Blocks.DIRT_PATH;
			case IMPERIAL -> Blocks.COBBLESTONE;
			case REPUBLICANA -> Blocks.STONE_BRICKS;
		};
	}

	/** The light on top of a lamp post. */
	public Block luz() {
		return this == COLONIAL ? Blocks.TORCH : Blocks.LANTERN;
	}

	// ---------------------------------------------------------------- the passing of eras

	/** What the arraial still lacks for the next era (empty when it qualifies). */
	public List<Component> falta(ServerLevel level, ArraialBlockEntity a) {
		List<Component> f = new ArrayList<>();
		BlockPos v = a.getBlockPos();
		switch (this) {
			case IMPERIAL -> {
				if (!a.estagio.pelo(Estagio.VILA)) {
					f.add(Estagio.VILA.nome());
				}
				if (Vilas.postosDe(level, v, ModPois.FORNO_OLARIA) == 0) {
					f.add(Component.translatable("projeto.arraial.olaria"));
				}
				if (a.tesouro < 40) {
					f.add(Component.translatable("arraial.era.falta_reis", 40));
				}
			}
			case REPUBLICANA -> {
				if (!a.estagio.pelo(Estagio.VILA_PROSPERA)) {
					f.add(Estagio.VILA_PROSPERA.nome());
				}
				if (Prefeitura.obras(level, v, "capela") == 0) {
					f.add(Component.translatable("projeto.arraial.capela"));
				}
				if (Vilas.postosDe(level, v, ModPois.ESCRIVANINHA) == 0) {
					f.add(Component.translatable("projeto.arraial.cartorio"));
				}
				if (a.tesouro < 80) {
					f.add(Component.translatable("arraial.era.falta_reis", 80));
				}
			}
			default -> {
			}
		}
		return f;
	}

	/** Morning: a new era if the arraial qualifies, then a little reform work. */
	static void diario(ServerLevel level, ArraialBlockEntity a) {
		if (a.era.ordinal() < values().length - 1) {
			Era proxima = values()[a.era.ordinal() + 1];
			if (proxima.falta(level, a).isEmpty()) {
				mudar(level, a, proxima, true);
			}
		}
		reformar(level, a, 2, 48, false);
	}

	/** The arraial enters a new era (paid from the coffers unless forced by command). */
	public static void mudar(ServerLevel level, ArraialBlockEntity a, Era nova, boolean paga) {
		if (paga) {
			a.mudarTesouro(-(nova == IMPERIAL ? 30 : 60));
		}
		a.era = nova;
		a.setChanged();
		Component texto = Component.translatable("arraial.cronica.era", a.nomeCompleto(), nova.nome());
		Cronica.anotar(level, a.getBlockPos(), texto);
		Prefeitura.avisar(level, a, Component.literal("🏛 ").append(texto).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		Festa.soltarFogos(level, a.getBlockPos(), 4);
		Conquistas.darPerto(level, a.getBlockPos(), Vilas.RAIO * 2, "era_" + nova.id());
	}

	/**
	 * Brings finished buildings and streets up to the current era.
	 * @param predios how many buildings at most
	 * @param ruas how many street blocks at most
	 * @param gratis no materials needed (the command)
	 * @return blocks changed
	 */
	public static int reformar(ServerLevel level, ArraialBlockEntity a, int predios, int ruas, boolean gratis) {
		Era era = a.era;
		if (era == COLONIAL) {
			return 0;
		}
		List<ArmazemBlockEntity> armazens = Vilas.armazens(level, a.getBlockPos());
		Map<Item, Integer> inv = Vilas.inventario(armazens);
		int mudou = 0, reformados = 0;
		boolean faltou = false;
		for (BlockPos p : List.copyOf(CanteiroBlockEntity.ativos(level))) {
			if (reformados >= predios) {
				break;
			}
			if (!p.closerThan(a.getBlockPos(), Vilas.RAIO) || !(level.getBlockEntity(p) instanceof CanteiroBlockEntity c) || !c.concluida()) {
				continue;
			}
			List<PlanoDeObra.Etapa> etapas = c.plano(level).map(PlanoDeObra::etapas).orElse(List.of());
			int aqui = 0;
			for (PlanoDeObra.Etapa e : etapas) {
				BlockState atual = level.getBlockState(e.pos());
				Block novo = era.troca(atual.getBlock());
				if (novo == atual.getBlock() || !atual.is(e.state().getBlock()) && era.troca(e.state().getBlock()) != novo) {
					continue;
				}
				Item custo = custo(novo);
				if (!gratis && custo != null) {
					if (inv.getOrDefault(custo, 0) <= 0) {
						faltou = true;
						continue;
					}
					inv.merge(custo, -1, Integer::sum);
					Vilas.retirar(armazens, custo, 1);
				}
				level.setBlock(e.pos(), novo.withPropertiesOf(atual), Block.UPDATE_CLIENTS);
				aqui++;
			}
			if (aqui > 0) {
				reformados++;
				mudou += aqui;
				level.playSound(null, p, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
				Cronica.anotar(level, a.getBlockPos(), Component.translatable("arraial.cronica.reforma", c.projeto().map(pr -> (Component) pr.nome())
						.orElse(Component.literal("?")), era.nome()));
			}
		}
		// Streets and lamp posts around the Cruzeiro.
		int r = Vilas.RAIO, feitas = 0;
		BlockPos c = a.getBlockPos();
		for (int dx = -r; dx <= r && feitas < ruas; dx++) {
			for (int dz = -r; dz <= r && feitas < ruas; dz++) {
				BlockPos topo = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.offset(dx, 0, dz)).below();
				BlockState s = level.getBlockState(topo);
				if (s.is(Blocks.DIRT_PATH) || era == REPUBLICANA && s.is(Blocks.COBBLESTONE) && level.getBlockState(topo.above()).isAir()
						&& vizinhoDeRua(level, topo)) {
					Item custo = Items.COBBLESTONE;
					if (!gratis) {
						if (inv.getOrDefault(custo, 0) <= 0) {
							faltou = true;
							continue;
						}
						inv.merge(custo, -1, Integer::sum);
						Vilas.retirar(armazens, custo, 1);
					}
					level.setBlock(topo, era.rua().defaultBlockState(), Block.UPDATE_CLIENTS);
					feitas++;
				} else if (s.is(Blocks.TORCH) && level.getBlockState(topo.below()).is(net.minecraft.tags.BlockTags.FENCES)) {
					level.setBlock(topo, Blocks.LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
					feitas++;
				}
			}
		}
		mudou += feitas;
		if (faltou && a.pedido.getString().isEmpty()) {
			a.pedido = Component.translatable("arraial.era.pedido_material", era.nome());
		}
		a.setChanged();
		return mudou;
	}

	/** A cobbled block that is part of a street (has another street block beside it). */
	private static boolean vizinhoDeRua(ServerLevel level, BlockPos p) {
		for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			BlockState s = level.getBlockState(p.relative(d));
			if (s.is(Blocks.COBBLESTONE) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.DIRT_PATH)) {
				return true;
			}
		}
		return false;
	}
}
