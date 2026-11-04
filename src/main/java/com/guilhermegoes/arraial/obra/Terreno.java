package com.guilhermegoes.arraial.obra;

import com.guilhermegoes.arraial.registry.ModBlocks;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Finding a place to build: the prefeito walks rings around the Cruzeiro, from the centre
 * outwards, looking for flat natural ground big enough for the project, clear of trees,
 * water and anything people made, and not too close to the other buildings. The marker goes
 * on the side facing the Cruzeiro, so every door looks towards the village centre.
 */
public final class Terreno {
	public record Lugar(BlockPos marco, Direction frente) {
	}

	private static final int RAIO_MINIMO = 7;
	private static final int FOLGA = 2;

	public static Optional<Lugar> procurar(ServerLevel level, BlockPos vila, Catalogo.Projeto projeto) {
		List<BoundingBox> ocupadas = new ArrayList<>();
		for (BlockPos p : CanteiroBlockEntity.ativos(level)) {
			if (p.closerThan(vila, Vilas.RAIO * 2) && level.getBlockEntity(p) instanceof CanteiroBlockEntity c) {
				c.plano(level).ifPresent(pl -> ocupadas.add(pl.area().inflatedBy(FOLGA)));
				ocupadas.add(BoundingBox.fromCorners(p, p).inflatedBy(1));
			}
		}
		ocupadas.add(BoundingBox.fromCorners(vila, vila).inflatedBy(3));

		double inicio = level.getRandom().nextDouble() * Math.PI * 2;
		for (int raio = RAIO_MINIMO; raio <= Vilas.RAIO - 10; raio += 3) {
			int passos = Math.max(8, raio);
			for (int i = 0; i < passos; i++) {
				double a = inicio + i * Math.PI * 2 / passos;
				int dx = (int) Math.round(Math.cos(a) * raio);
				int dz = (int) Math.round(Math.sin(a) * raio);
				BlockPos marco = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, vila.offset(dx, 0, dz));
				Direction frente = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST)
						: (dz > 0 ? Direction.SOUTH : Direction.NORTH);
				if (serve(level, vila, projeto, marco, frente, ocupadas)) {
					return Optional.of(new Lugar(marco, frente));
				}
			}
		}
		return Optional.empty();
	}

	private static boolean serve(ServerLevel level, BlockPos vila, Catalogo.Projeto projeto, BlockPos marco, Direction frente,
			List<BoundingBox> ocupadas) {
		if (!level.isLoaded(marco) || !livre(level.getBlockState(marco)) || !chao(level.getBlockState(marco.below()))) {
			return false;
		}
		PlanoDeObra plano = PlanoDeObra.criar(level, projeto, marco, frente).orElse(null);
		if (plano == null) {
			return false;
		}
		BoundingBox area = plano.area();
		for (BoundingBox o : ocupadas) {
			if (o.intersects(area)) {
				return false;
			}
		}
		int chaoY = marco.getY() - 1;
		for (int x = area.minX() - 1; x <= area.maxX() + 1; x++) {
			for (int z = area.minZ() - 1; z <= area.maxZ() + 1; z++) {
				BlockPos coluna = new BlockPos(x, chaoY, z);
				if (!coluna.closerThan(vila, Vilas.RAIO - 2) || !level.isLoaded(coluna)) {
					return false;
				}
				int topo = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				if (Math.abs(topo - chaoY) > 1 || !chao(level.getBlockState(new BlockPos(x, topo, z)))) {
					return false; // too steep, a hole, water, or something built
				}
			}
		}
		for (BlockPos p : BlockPos.betweenClosed(area.minX(), chaoY + 1, area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
			BlockState s = level.getBlockState(p);
			if (!livre(s) && !chao(s)) {
				return false;
			}
		}
		return true;
	}

	/** Natural ground the pedreiro may dig into or build on. */
	private static boolean chao(BlockState s) {
		return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL)
				|| s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.CLAY);
	}

	/** Open air, plants, snow layers or leaves: nothing anyone would miss. */
	private static boolean livre(BlockState s) {
		return s.isAir() || (s.canBeReplaced() && s.getFluidState().isEmpty()) || s.is(BlockTags.LEAVES)
				|| s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS);
	}

	/** Puts down a Marco da Obra holding {@code projeto}; the pedreiros will find it. */
	public static boolean marcar(ServerLevel level, Lugar lugar, Catalogo.Projeto projeto) {
		BlockState marco = ModBlocks.CANTEIRO.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, lugar.frente());
		if (!level.setBlock(lugar.marco(), marco, Block.UPDATE_ALL)) {
			return false;
		}
		if (level.getBlockEntity(lugar.marco()) instanceof CanteiroBlockEntity c) {
			c.escolherProjeto(projeto);
			return true;
		}
		return false;
	}

	private Terreno() {
	}
}
