package com.guilhermegoes.arraial.obra;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The roda d'água of the moinho: a paddle wheel two blocks across that turns (four frames) while
 * there is water under or beside it. A wheel out of the water stands still.
 */
public class RodaDaguaBlock extends PostoDirecionalBlock {
	public static final IntegerProperty FASE = IntegerProperty.create("fase", 0, 3);
	private static final int GIRO = 5;
	private static final int PARADA = 40;

	public RodaDaguaBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FASE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(FASE);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState antigo, boolean piston) {
		super.onPlace(state, level, pos, antigo, piston);
		if (!level.isClientSide() && !antigo.is(this)) {
			level.scheduleTick(pos, this, GIRO);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!naAgua(level, pos)) {
			level.scheduleTick(pos, this, PARADA);
			return;
		}
		int fase = (state.getValue(FASE) + 1) % 4;
		level.setBlock(pos, state.setValue(FASE, fase), Block.UPDATE_CLIENTS);
		if (fase == 0 && random.nextInt(3) == 0) {
			level.playSound(null, pos, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.4F, 1.2F);
		}
		level.scheduleTick(pos, this, GIRO);
	}

	/** Water right under the wheel or beside its lower half. */
	public static boolean naAgua(LevelReader level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -2, -1), pos.offset(1, 0, 1))) {
			if (level.getFluidState(p).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}
}
