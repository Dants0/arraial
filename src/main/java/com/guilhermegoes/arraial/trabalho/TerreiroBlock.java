package com.guilhermegoes.arraial.trabalho;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Terreiro de café: the low brick drying yard with coffee spread out on it (the cafeicultor's posto). */
public class TerreiroBlock extends Block {
	private static final VoxelShape FORMA = Block.box(0, 0, 0, 16, 4, 16);

	public TerreiroBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return FORMA;
	}
}
