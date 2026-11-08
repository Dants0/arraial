package com.guilhermegoes.arraial.trabalho;

import com.guilhermegoes.arraial.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cafeeiro: the coffee bush of the Imperial-era fazendas. Planted from a coffee cherry on dirt,
 * grass or farmland, it grows in four stages; ripe (red) it is picked by hand or by the
 * cafeicultor and goes back to flowering, so a cafezal gives for years.
 */
public class CafeeiroBlock extends VegetationBlock implements BonemealableBlock {
	public static final int MADURO = 3;
	public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
	private static final VoxelShape MUDA = Block.column(8.0, 0.0, 9.0);
	private static final VoxelShape PE = Block.column(14.0, 0.0, 16.0);

	public CafeeiroBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AGE, 0));
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(net.minecraft.world.level.block.Blocks.FARMLAND);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(AGE) == 0 ? MUDA : PE;
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return state.getValue(AGE) < MADURO;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// Coffee is slower than berries: one chance in seven, and it wants light.
		int age = state.getValue(AGE);
		if (age < MADURO && random.nextInt(7) == 0 && level.getRawBrightness(pos.above(), 0) >= 9) {
			level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
		}
	}

	public static boolean maduro(BlockState state) {
		return state.getBlock() instanceof CafeeiroBlock && state.getValue(AGE) == MADURO;
	}

	/** Picks a ripe bush: 2 to 4 cherries, and the bush goes back to flowering. Returns how many. */
	public static int colher(ServerLevel level, BlockPos pos, BlockState state) {
		if (!maduro(state)) {
			return 0;
		}
		int n = 2 + level.getRandom().nextInt(3);
		level.setBlock(pos, state.setValue(AGE, 1), Block.UPDATE_CLIENTS);
		level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.3F);
		return n;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!maduro(state)) {
			return super.useWithoutItem(state, level, pos, player, hit);
		}
		if (level instanceof ServerLevel server) {
			Block.popResource(server, pos, new ItemStack(ModItems.CAFE_CEREJA, colher(server, pos, state)));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE);
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, net.minecraft.world.level.block.BonemealSource source) {
		return state.getValue(AGE) < MADURO;
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, net.minecraft.world.level.block.BonemealSource source) {
		return true;
	}

	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, net.minecraft.world.level.block.BonemealSource source) {
		level.setBlock(pos, state.setValue(AGE, Math.min(MADURO, state.getValue(AGE) + 1)), Block.UPDATE_CLIENTS);
	}
}
