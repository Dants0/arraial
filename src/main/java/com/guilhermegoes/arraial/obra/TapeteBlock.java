package com.guilhermegoes.arraial.obra;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A rug lying on the floor (the pele de onça): one sixteenth thick, pointing where it was laid. */
public class TapeteBlock extends PostoDirecionalBlock {
	private static final VoxelShape FORMA = Block.box(0, 0, 0, 16, 1, 16);

	public TapeteBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return FORMA;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return !level.isEmptyBlock(pos.below());
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos,
			Direction direcao, BlockPos vizinho, BlockState estadoVizinho, net.minecraft.util.RandomSource random) {
		return !state.canSurvive(level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
				: super.updateShape(state, level, ticks, pos, direcao, vizinho, estadoVizinho, random);
	}
}
