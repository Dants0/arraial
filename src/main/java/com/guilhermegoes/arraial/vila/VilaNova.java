package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.npc.Profissao;
import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import com.guilhermegoes.arraial.obra.Catalogo;
import com.guilhermegoes.arraial.obra.PlanoDeObra;
import com.guilhermegoes.arraial.obra.Prioridade;
import com.guilhermegoes.arraial.obra.Terreno;
import com.guilhermegoes.arraial.registry.ModBlocks;
import com.guilhermegoes.arraial.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Autonomous villages (GDD 7, "mundo vivo"): while a player explores far from every arraial, a
 * povoado may be found ahead: a Cruzeiro, an armazém with some provisions, a few houses, a roça,
 * a woodshed and a well, and its first moradores. From there it runs itself like any arraial
 * (elects a prefeito, orders buildings, grows) and joins the world's trade.
 */
public final class VilaNova {
	private static final int MAX_AUTONOMAS = 10;
	/** Neighbours: at least this far from any arraial, within {@link #VIZINHA_MAX} of the player's. */
	private static final int VIZINHA_MIN = 150;
	private static final int VIZINHA_MAX = 420;
	private static final int VIZINHAS = 2;
	private static final int LONGE = 350;
	private static final int INTERVALO = 6000;
	private static final List<String> COMECO = List.of("casebre", "rancho", "roca", "serraria", "poco");
	/** The mission's church comes first. */
	private static final List<String> COMECO_MISSAO = List.of("missao", "casebre", "rancho", "roca", "poco");
	/** What a caravan builds first, by itself: a roof, food and water; the prefeito orders the rest. */
	private static final List<String> AO_VIVO = List.of("casebre", "roca", "poco");
	// The mission church is too big for the mules (it filled the armazém and left the houses short):
	// the prefeito orders it once everyone has a roof (see Prefeitura).
	private static final List<String> AO_VIVO_MISSAO = List.of("casebre", "roca", "poco");
	/** Share of autonomous villages that are Spanish missions. */
	static final float CHANCE_MISSAO = 0.3F;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % INTERVALO != 0) {
				return;
			}
			ServerLevel level = server.overworld();
			for (ServerPlayer p : level.players()) {
				if (level.getRandom().nextFloat() < 0.35F) {
					tentar(level, p);
				}
			}
		});
	}

	private static void tentar(ServerLevel level, ServerPlayer p) {
		String dim = level.dimension().identifier().toString();
		long autonomas = Registro.todas().values().stream().filter(i -> i.autonoma).count();
		if (autonomas >= com.guilhermegoes.arraial.config.ArraialConfig.get().maxAutonomas) {
			return;
		}
		// Far from everything (a new region), or a neighbour for an arraial that has too few.
		double perto = distanciaMinima(level, dim, p.blockPosition());
		if (perto < LONGE && !(perto >= VIZINHA_MIN && precisaVizinha(level, dim, p.blockPosition()))) {
			return;
		}
		RandomSource r = level.getRandom();
		boolean aoVivo = com.guilhermegoes.arraial.config.ArraialConfig.get().fundacaoAoVivo;
		if (aoVivo && Colonos.aCaminho(level)) {
			return; // one caravan at a time
		}
		for (int tentativa = 0; tentativa < 6; tentativa++) {
			double a = r.nextDouble() * Math.PI * 2;
			// A caravan settles closer, where the player can watch it arrive and build.
			int dist = aoVivo ? 40 + r.nextInt(24) : 56 + r.nextInt(28);
			BlockPos alvo = p.blockPosition().offset((int) (Math.cos(a) * dist), 0, (int) (Math.sin(a) * dist));
			BlockPos centro = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, alvo);
			if (distanciaMinima(level, dim, centro) < VIZINHA_MIN) {
				continue;
			}
			if (plano(level, centro)) {
				if (!aoVivo) {
					fundar(level, centro, p);
					return;
				}
				if (Colonos.partir(level, p, centro, sorteiaMissao(level))) {
					return;
				}
			}
		}
	}

	/** Distance to the nearest arraial, loaded or not. */
	private static double distanciaMinima(ServerLevel level, String dim, BlockPos pos) {
		double min = Double.MAX_VALUE;
		for (Registro.Info i : Registro.todas().values()) {
			if (i.dimensao.equals(dim)) {
				min = Math.min(min, Math.sqrt(i.posicao().distSqr(pos)));
			}
		}
		for (BlockPos v : Vilas.ativas(level)) {
			min = Math.min(min, Math.sqrt(v.distSqr(pos)));
		}
		return min;
	}

	/**
	 * A player's arraial (founded by a player) near {@code pos} with fewer than {@link #VIZINHAS}
	 * neighbours: the world sends it one, so trade, diplomacy and rivalry have someone to happen with.
	 */
	private static boolean precisaVizinha(ServerLevel level, String dim, BlockPos pos) {
		for (Registro.Info i : Registro.todas().values()) {
			if (!i.dimensao.equals(dim) || i.autonoma || i.abandonada || i.posicao().distSqr(pos) > (double) VIZINHA_MAX * VIZINHA_MAX) {
				continue;
			}
			long vizinhas = Registro.todas().values().stream().filter(o -> o != i && o.dimensao.equals(dim)
					&& o.posicao().distSqr(i.posicao()) < (double) VIZINHA_MAX * VIZINHA_MAX).count();
			if (vizinhas < VIZINHAS && com.guilhermegoes.arraial.config.ArraialConfig.get().vizinhasPerto) {
				return true;
			}
		}
		return false;
	}

	/** Loaded, dry and flat enough around {@code centro}. */
	static boolean plano(ServerLevel level, BlockPos centro) {
		int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
		for (int dx = -14; dx <= 14; dx += 2) {
			for (int dz = -14; dz <= 14; dz += 2) {
				BlockPos c = centro.offset(dx, 0, dz);
				if (!level.isLoaded(c)) {
					return false;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
				BlockState topo = level.getBlockState(new BlockPos(c.getX(), y - 1, c.getZ()));
				if (!topo.getFluidState().isEmpty() || topo.is(BlockTags.LOGS)) {
					return false;
				}
				min = Math.min(min, y);
				max = Math.max(max, y);
			}
		}
		return max - min <= 3;
	}

	/** Lays out a new povoado at {@code centro} and brings its first moradores. */
	public static void fundar(ServerLevel level, BlockPos centro, @org.jspecify.annotations.Nullable ServerPlayer quem) {
		fundar(level, centro, quem, null);
	}

	/** @param missao true: a Spanish mission; false: Portuguese; null: chance ({@link #CHANCE_MISSAO}). */
	public static void fundar(ServerLevel level, BlockPos centro, @org.jspecify.annotations.Nullable ServerPlayer quem, @org.jspecify.annotations.Nullable Boolean missao) {
		ArraialBlockEntity arraial = cruzeiro(level, centro, missao != null ? missao : sorteiaMissao(level));
		if (arraial == null) {
			return;
		}
		armazem(level, centro);

		// The first buildings, already standing.
		for (String id : arraial.hispano() ? COMECO_MISSAO : COMECO) {
			Catalogo.porId(id).ifPresent(projeto -> {
				Optional<Terreno.Lugar> lugar = Terreno.procurar(level, centro, projeto);
				if (lugar.isPresent() && Terreno.marcar(level, lugar.get(), projeto, Prioridade.NORMAL)
						&& level.getBlockEntity(lugar.get().marco()) instanceof CanteiroBlockEntity c) {
					erguer(level, c);
				}
			});
		}

		// Its first moradores.
		for (int i = 0; i < 5; i++) {
			BlockPos onde = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					centro.offset(level.getRandom().nextInt(9) - 4, 0, level.getRandom().nextInt(9) - 4));
			MoradorEntity m = MoradorEntity.criar(level, onde, Profissao.NENHUMA);
			if (m != null) {
				m.definirVila(centro);
			}
		}
		Cronica.anotar(level, centro, Component.translatable("arraial.cronica.autonoma"));
		if (quem != null) quem.sendSystemMessage(Component.translatable("arraial.vila.avistou", arraial.nomeCompleto()).withStyle(ChatFormatting.AQUA));
	}

	/** Along the frontier, some of the neighbours are Spanish missions (Jesuit reductions and pueblos). */
	static boolean sorteiaMissao(ServerLevel level) {
		return level.getRandom().nextInt(100) < com.guilhermegoes.arraial.config.ArraialConfig.get().chanceMissao;
	}

	/**
	 * Founded live by a caravan of settlers: the Cruzeiro, the armazém with provisions and the
	 * materials for the first works, a campfire, and the sites marked; the settlers build them.
	 */
	static @org.jspecify.annotations.Nullable ArraialBlockEntity fundarAoVivo(ServerLevel level, BlockPos centro, boolean missao) {
		ArraialBlockEntity arraial = cruzeiro(level, centro, missao);
		if (arraial == null) {
			return null;
		}
		ArmazemBlockEntity a = armazem(level, centro);
		BlockPos fogo = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centro.offset(-2, 0, 2));
		if (level.getBlockState(fogo).canBeReplaced() && level.getBlockState(fogo.below()).isFaceSturdy(level, fogo.below(), net.minecraft.core.Direction.UP)) {
			level.setBlock(fogo, Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
		}
		for (String id : arraial.hispano() ? AO_VIVO_MISSAO : AO_VIVO) {
			Catalogo.porId(id).ifPresent(projeto -> {
				Optional<Terreno.Lugar> lugar = Terreno.procurar(level, centro, projeto);
				if (lugar.isPresent() && Terreno.marcar(level, lugar.get(), projeto, Prioridade.NORMAL)
						&& level.getBlockEntity(lugar.get().marco()) instanceof CanteiroBlockEntity c && a != null) {
					// What the settlers brought on the mules: exactly what the work needs.
					c.plano(level).ifPresent(plano -> plano.materiais(0).forEach((item, n) -> Vilas.depositar(List.of(a), item, n)));
				}
			});
		}
		Cronica.anotar(level, centro, Component.translatable("arraial.cronica.colonos"));
		return arraial;
	}

	/** The Cruzeiro of a new autonomous povoado (null if it could not be placed). */
	private static @org.jspecify.annotations.Nullable ArraialBlockEntity cruzeiro(ServerLevel level, BlockPos centro, boolean missao) {
		level.setBlock(centro, ModBlocks.CRUZEIRO.defaultBlockState(), Block.UPDATE_ALL);
		if (!(level.getBlockEntity(centro) instanceof ArraialBlockEntity arraial)) {
			return null;
		}
		arraial.autonoma = true;
		if (missao) {
			arraial.povo = "hispano";
		}
		// Some povoados of the backlands are ruled by a coronel.
		if (level.getRandom().nextFloat() < 0.35F) {
			arraial.politica.regime = Politica.Regime.HEREDITARIO;
		}
		arraial.fundar(level);
		Vilas.registrar(level, centro);
		return arraial;
	}

	/** The armazém, with provisions for the first days. */
	private static @org.jspecify.annotations.Nullable ArmazemBlockEntity armazem(ServerLevel level, BlockPos centro) {
		BlockPos arm = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centro.offset(3, 0, 0));
		level.setBlock(arm, ModBlocks.ARMAZEM.defaultBlockState(), Block.UPDATE_ALL);
		if (!(level.getBlockEntity(arm) instanceof ArmazemBlockEntity a)) {
			return null;
		}
		Item tora = madeiraLocal(level, centro);
		List<ArmazemBlockEntity> l = List.of(a);
		Vilas.depositar(l, Items.BREAD, 24);
		Vilas.depositar(l, ModItems.FARINHA, 16);
		Vilas.depositar(l, ModItems.MANIVA, 12);
		Vilas.depositar(l, Items.WHEAT_SEEDS, 12);
		Vilas.depositar(l, tora, 48);
		Vilas.depositar(l, Items.COBBLESTONE, 32);
		Vilas.depositar(l, Items.TORCH, 8);
		return a;
	}

	/** /arraial obra_pronta: a finished building of {@code id} at the first free spot of the arraial. */
	public static boolean construirPronto(ServerLevel level, BlockPos vila, String id) {
		return Catalogo.porId(id).map(projeto -> {
			Optional<Terreno.Lugar> lugar = Terreno.procurar(level, vila, projeto);
			if (lugar.isPresent() && Terreno.marcar(level, lugar.get(), projeto, Prioridade.NORMAL)
					&& level.getBlockEntity(lugar.get().marco()) instanceof CanteiroBlockEntity c) {
				erguer(level, c);
				return true;
			}
			return false;
		}).orElse(false);
	}

	/** Builds the whole plan at once (no pedreiro needed for the founders' houses). */
	private static void erguer(ServerLevel level, CanteiroBlockEntity c) {
		PlanoDeObra plano = c.plano(level).orElse(null);
		if (plano == null) {
			return;
		}
		BlockPos v = Vilas.maisProxima(level, c.getBlockPos());
		Era.Estilo estilo = v != null && level.getBlockEntity(v) instanceof ArraialBlockEntity a ? Era.estilo(level, a) : Era.COLONIAL.comum();
		for (PlanoDeObra.Etapa e : plano.etapas()) {
			level.setBlock(e.pos(), estilo.aplicar(e.state()), Block.UPDATE_CLIENTS);
		}
		for (PlanoDeObra.Etapa e : plano.etapas()) {
			c.avancar();
		}
		c.concluir();
	}

	/** The wood of the nearest tree, so the povoado builds with what grows around it. */
	private static Item madeiraLocal(Level level, BlockPos centro) {
		for (BlockPos p : BlockPos.betweenClosed(centro.offset(-20, -2, -20), centro.offset(20, 12, 20))) {
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.LOGS)) {
				return s.getBlock().asItem();
			}
		}
		return Items.OAK_LOG;
	}

	private VilaNova() {
	}
}
