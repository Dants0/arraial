package com.guilhermegoes.arraial.obra;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Rede de dormir: a striped hammock hung between two ropes, made by the tecelão (decorative). */
public class RedeBlock extends PostoDirecionalBlock {
	private static final VoxelShape FORMA = Block.box(0, 6, 0, 16, 11, 16);

	public RedeBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return FORMA;
	}
}
