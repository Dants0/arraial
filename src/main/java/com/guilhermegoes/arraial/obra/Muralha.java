package com.guilhermegoes.arraial.obra;

import com.guilhermegoes.arraial.trabalho.Receitas;
import com.guilhermegoes.arraial.vila.Vilas;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * The paliçada: a log stockade (one log topped by a fence, too tall for a zombie to jump) all
 * around the arraial, with a double gate in the middle of each side. Unlike the catalog
 * buildings it has no structure file: the plan follows the lie of the land, goes around the
 * buildings that already stand, and its size is fixed the first time it is drawn (stored in the
 * Marco) so it does not shift while it goes up.
 */
public final class Muralha {
	public static final String ID = "muralha";
	/** Room left between the outermost building and the wall. */
	private static final int FOLGA = 4;
	private static final int MINIMO = 12;

	public static boolean e(Catalogo.Projeto p) {
		return p.id().equals(ID);
	}

	/** Half the side of the square the wall encloses, wide enough for every building of the arraial. */
	public static int raioPara(ServerLevel level, BlockPos vila) {
		int r = MINIMO;
		for (BoundingBox b : construcoes(level, vila)) {
			r = Math.max(r, Math.max(Math.max(Math.abs(b.minX() - vila.getX()), Math.abs(b.maxX() - vila.getX())),
					Math.max(Math.abs(b.minZ() - vila.getZ()), Math.abs(b.maxZ() - vila.getZ()))) + FOLGA);
		}
		return Math.min(r, Vilas.RAIO - 3);
	}

	private static List<BoundingBox> construcoes(ServerLevel level, BlockPos vila) {
		List<BoundingBox> l = new ArrayList<>();
		for (BlockPos p : CanteiroBlockEntity.ativos(level)) {
			if (p.closerThan(vila, Vilas.RAIO) && level.getBlockEntity(p) instanceof CanteiroBlockEntity c
					&& c.projeto().map(pr -> !e(pr)).orElse(false)) {
				c.plano(level).ifPresent(pl -> l.add(pl.area()));
			}
		}
		return l;
	}

	/** Draws the wall around the arraial nearest to {@code marco}. */
	public static Optional<PlanoDeObra> planejar(ServerLevel level, BlockPos marco, int raio, int madeira) {
		BlockPos vila = Vilas.maisProxima(level, marco);
		if (vila == null) {
			vila = marco;
		}
		if (raio <= 0) {
			raio = raioPara(level, vila);
		}
		Receitas.Madeira m = Receitas.MADEIRAS.get(Math.floorMod(madeira, Receitas.MADEIRAS.size()));
		BlockState tora = Block.byItem(m.tora()).defaultBlockState();
		if (tora.hasProperty(RotatedPillarBlock.AXIS)) {
			tora = tora.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		}
		BlockState cerca = Block.byItem(m.cerca()).defaultBlockState();
		BlockState portao = Block.byItem(m.portao()).defaultBlockState();
		List<BoundingBox> desviar = construcoes(level, vila).stream().map(b -> b.inflatedBy(1)).toList();

		List<PlanoDeObra.Etapa> etapas = new ArrayList<>();
		BoundingBox area = null;
		// Walk the square: north side west→east, east side north→south, south east→west, west south→north.
		int cx = vila.getX(), cz = vila.getZ();
		for (int lado = 0; lado < 4; lado++) {
			for (int i = -raio; i < raio; i++) {
				int x, z;
				Direction frente;
				switch (lado) {
					case 0 -> { x = cx + i; z = cz - raio; frente = Direction.NORTH; }
					case 1 -> { x = cx + raio; z = cz + i; frente = Direction.EAST; }
					case 2 -> { x = cx - i; z = cz + raio; frente = Direction.SOUTH; }
					default -> { x = cx - raio; z = cz - i; frente = Direction.WEST; }
				}
				BlockPos chao = chao(level, x, z);
				if (chao == null) {
					continue; // water, a ravine, or unloaded: leave a gap
				}
				BlockPos base = chao.above();
				boolean bloqueado = false;
				for (BoundingBox b : desviar) {
					if (b.isInside(base)) {
						bloqueado = true;
					}
				}
				if (bloqueado) {
					continue;
				}
				boolean ePortao = i == 0 || i == -1;
				if (ePortao) {
					etapas.add(new PlanoDeObra.Etapa(base, portao.setValue(FenceGateBlock.FACING, frente)));
					etapas.add(new PlanoDeObra.Etapa(base.above(), Blocks.AIR.defaultBlockState()));
				} else {
					etapas.add(new PlanoDeObra.Etapa(base, tora));
					etapas.add(new PlanoDeObra.Etapa(base.above(), cerca));
				}
				area = area == null ? BoundingBox.fromCorners(base, base.above()) : area.encapsulate(base).encapsulate(base.above());
			}
		}
		if (etapas.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(PlanoDeObra.de(etapas, area));
	}

	/** The natural ground of a column, under any trees, plants or an earlier stretch of the wall. */
	private static @Nullable BlockPos chao(ServerLevel level, int x, int z) {
		BlockPos topo = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
		if (!level.isLoaded(topo)) {
			return null;
		}
		BlockPos p = topo;
		for (int i = 0; i < 24; i++) {
			BlockState s = level.getBlockState(p);
			if (!s.getFluidState().isEmpty()) {
				return null;
			}
			boolean passa = s.isAir() || s.canBeReplaced() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES)
					|| s.is(BlockTags.FENCES) || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS);
			if (!passa && s.isSolidRender()) {
				return p.immutable();
			}
			if (!passa) {
				return null; // something built (a path slab, a chest...): leave it alone
			}
			p = p.below();
		}
		return null;
	}

	/** Closed gates of the arraial's wall (so moradores can find a way out). */
	public static List<BlockPos> portoes(ServerLevel level, BlockPos vila) {
		List<BlockPos> l = new ArrayList<>();
		for (BlockPos p : CanteiroBlockEntity.ativos(level)) {
			if (p.closerThan(vila, Vilas.RAIO) && level.getBlockEntity(p) instanceof CanteiroBlockEntity c
					&& c.projeto().map(Muralha::e).orElse(false)) {
				c.plano(level).ifPresent(pl -> pl.etapas().stream()
						.filter(e -> e.state().getBlock() instanceof FenceGateBlock)
						.forEach(e -> l.add(e.pos())));
			}
		}
		return l;
	}

	private Muralha() {
	}
}
