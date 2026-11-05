package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.obra.PostoDirecionalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bandeirinhas: a string of little paper flags for the festas juninas. Purely decorative and
 * walk-through; a festa with bandeirinhas near the Cruzeiro makes the moradores happier.
 */
public class BandeirinhasBlock extends PostoDirecionalBlock {
	private static final VoxelShape NS = Block.box(0, 0, 7, 16, 16, 9);
	private static final VoxelShape LO = Block.box(7, 0, 0, 9, 16, 16);

	public BandeirinhasBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : LO;
	}
}
