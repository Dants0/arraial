package com.guilhermegoes.arraial.obra;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The monjolo: water fills the trough at the back of the beam, it tips, empties, and the pestle
 * at the front falls into the pilão, thump, all day long, with nobody at it. It needs water
 * close by (a brook, a river); an arraial with a working monjolo pounds farinha and coffee twice
 * as fast (see {@link com.guilhermegoes.arraial.npc.ArtesaoGoal} and the cafezal).
 */
public class MonjoloBlock extends PostoDirecionalBlock {
	public static final BooleanProperty BATENDO = BooleanProperty.create("batendo");
	private static final int ENCHENDO = 30;
	private static final int BATIDA = 8;
	private static final int PARADO = 60;

	public MonjoloBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(BATENDO, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(BATENDO);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState antigo, boolean piston) {
		super.onPlace(state, level, pos, antigo, piston);
		if (!level.isClientSide() && !antigo.is(this)) {
			level.scheduleTick(pos, this, ENCHENDO);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!RodaDaguaBlock.naAgua(level, pos)) {
			if (state.getValue(BATENDO)) {
				level.setBlock(pos, state.setValue(BATENDO, false), Block.UPDATE_CLIENTS);
			}
			level.scheduleTick(pos, this, PARADO);
			return;
		}
		boolean bate = !state.getValue(BATENDO);
		level.setBlock(pos, state.setValue(BATENDO, bate), Block.UPDATE_CLIENTS);
		if (bate) {
			level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.9F, 0.55F);
			BlockPos pilao = pos.relative(state.getValue(FACING));
			level.sendParticles(ParticleTypes.WHITE_ASH, pilao.getX() + 0.5, pos.getY() + 0.4, pilao.getZ() + 0.5, 4, 0.15, 0.05, 0.15, 0.0);
		} else {
			level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.3F, 1.4F);
		}
		level.scheduleTick(pos, this, bate ? BATIDA : ENCHENDO);
	}
}
