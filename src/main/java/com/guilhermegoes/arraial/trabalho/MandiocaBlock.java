package com.guilhermegoes.arraial.trabalho;

import com.guilhermegoes.arraial.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mandioca (cassava): planted from a maniva (stem cutting), grows slower than wheat but gives
 * several roots, which the farinheiro turns into farinha and beiju. The staple of the arraial.
 */
public class MandiocaBlock extends CropBlock {
	public MandiocaBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected ItemLike getBaseSeedId() {
		return ModItems.MANIVA;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// Cassava takes its time: half the growth chances of wheat.
		if (random.nextBoolean()) {
			super.randomTick(state, level, pos, random);
		}
	}
}
