package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import com.guilhermegoes.arraial.obra.PlanoDeObra;
import com.guilhermegoes.arraial.registry.ModBlocks;
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

	/**
	 * How an arraial builds: its era, its people and its trade.
	 * <ul>
	 * <li>Portuguese: taipa → brick and tiles (Imperial) → lime with an azulejo band (Republicana).</li>
	 * <li>Spanish mission ({@code hispano}): adobe under clay tiles from the start → whitewashed.</li>
	 * <li>Mining town ({@code mineiro}), the Baroque of Minas: from the Imperial era whitewashed walls on
	 * a soapstone base, with soapstone paving.</li>
	 * </ul>
	 */
	public record Estilo(Era era, boolean hispano, boolean mineiro) {
		/** The block a planned (colonial) block becomes in this style; the same block if unchanged. */
		public Block troca(Block b) {
			boolean parede = b == Blocks.MUD_BRICKS || b == Blocks.PACKED_MUD || b == ModBlocks.TAIPA || b == ModBlocks.ADOBE;
			boolean moderna = era != COLONIAL;
			if (parede || b == Blocks.BRICKS && era == REPUBLICANA) {
				if (!moderna) {
					return hispano ? ModBlocks.ADOBE : ModBlocks.TAIPA;
				}
				return hispano || mineiro || era == REPUBLICANA ? ModBlocks.CAL : Blocks.BRICKS;
			}
			if (b == Blocks.COBBLESTONE && moderna) {
				if (mineiro) {
					return ModBlocks.PEDRA_SABAO;
				}
				if (era == REPUBLICANA) {
					return Blocks.STONE_BRICKS;
				}
			}
			if (b == Blocks.TORCH && moderna) {
				return Blocks.LANTERN;
			}
			// Roofs (oak, mud brick or brick) become clay tiles; missions had tiles from the start.
			if (moderna || hispano) {
				if (b == Blocks.OAK_STAIRS || b == Blocks.BRICK_STAIRS || b == Blocks.MUD_BRICK_STAIRS) {
					return ModBlocks.TELHA_ESCADA;
				}
				if (b == Blocks.OAK_SLAB || b == Blocks.BRICK_SLAB || b == Blocks.MUD_BRICK_SLAB) {
					return ModBlocks.TELHA_LAJE;
				}
			}
			return b;
		}

		/** What should stand where the plan has {@code plano}; {@code barra}: the lowest course of the walls. */
		public Block alvo(Block plano, boolean barra) {
			Block b = troca(plano);
			if (!barra || b != ModBlocks.CAL || hispano) {
				return b;
			}
			if (mineiro) {
				return ModBlocks.PEDRA_SABAO;   // the soapstone base of the Baroque houses
			}
			return era == REPUBLICANA ? ModBlocks.AZULEJO : b;
		}

		/** A planned state in this style (keeps facing, half, shape...). */
		public BlockState aplicar(BlockState plano) {
			Block b = troca(plano.getBlock());
			return b == plano.getBlock() ? plano : b.withPropertiesOf(plano);
		}
	}

	/** The style of a Portuguese, non-mining arraial in this era. */
	public Estilo comum() {
		return new Estilo(this, false, false);
	}

	/** The style an arraial builds in now (its era, people and specialty). */
	public static Estilo estilo(ServerLevel level, ArraialBlockEntity a) {
		Registro.Info v = Registro.get(Registro.id(level.dimension().identifier().toString(), a.getBlockPos()));
		boolean mineiro = v != null && Economia.Especialidade.de(v.especialidade) == Economia.Especialidade.MINERADORA;
		return new Estilo(a.era, a.hispano(), mineiro);
	}

	public Block troca(Block b) {
		return comum().troca(b);
	}

	public Block alvo(Block plano, boolean barra) {
		return comum().alvo(plano, barra);
	}

	/** The block in the world is the planned one in some era's style (not something a player put there). */
	public static boolean equivale(Block plano, Block atual) {
		if (atual == plano) {
			return true;
		}
		for (Era e : values()) {
			for (int k = 0; k < 4; k++) {
				Estilo s = new Estilo(e, (k & 1) != 0, (k & 2) != 0);
				if (s.alvo(plano, false) == atual || s.alvo(plano, true) == atual) {
					return true;
				}
			}
		}
		return false;
	}

	/** A planned state in this era's (Portuguese) style. */
	public BlockState estilo(BlockState plano) {
		return comum().aplicar(plano);
	}

	/** The item the new block costs from the armazém (null: free, e.g. a lantern from the torch). */
	static Item custo(Block novo) {
		if (novo == Blocks.BRICKS || novo == ModBlocks.TELHA_ESCADA || novo == ModBlocks.TELHA_LAJE) {
			return Items.BRICK;
		}
		if (novo == ModBlocks.CAL || novo == ModBlocks.AZULEJO) {
			return Items.CLAY_BALL;
		}
		if (novo == Blocks.STONE_BRICKS || novo == ModBlocks.PEDRA_SABAO) {
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
				if (Prefeitura.obras(level, v, "capela") + Prefeitura.obras(level, v, "matriz") + Prefeitura.obras(level, v, "missao") == 0) {
					f.add(Component.translatable(a.hispano() ? "projeto.arraial.missao" : "projeto.arraial.capela"));
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
		Estilo estilo = estilo(level, a);
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
			// The lowest course of the walls (the azulejo barra of Republican façades).
			int barra = Integer.MAX_VALUE;
			for (PlanoDeObra.Etapa e : etapas) {
				if (!e.limpeza() && REPUBLICANA.troca(e.state().getBlock()) == ModBlocks.CAL) {
					barra = Math.min(barra, e.pos().getY());
				}
			}
			int aqui = 0;
			for (PlanoDeObra.Etapa e : etapas) {
				if (e.limpeza()) {
					continue;
				}
				BlockState atual = level.getBlockState(e.pos());
				Block plano = e.state().getBlock();
				Block novo = estilo.alvo(plano, e.pos().getY() == barra);
				if (novo == atual.getBlock() || atual.isAir() || !equivale(plano, atual.getBlock())) {
					continue; // already in style, missing (Reparos' job), or changed by a player
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
		for (int dx = -r; dx <= r && feitas < ruas && era != COLONIAL; dx++) {
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
