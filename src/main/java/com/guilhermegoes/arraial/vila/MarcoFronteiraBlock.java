package com.guilhermegoes.arraial.vila;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Marco de Fronteira: a carved stone post that extends an arraial's territory over the 3x3 chunks
 * around it (see {@link Territorio}). It also counts as a border post in a conflict.
 */
public class MarcoFronteiraBlock extends Block {
	private static final VoxelShape FORMA = Block.box(5, 0, 5, 11, 15, 11);

	public MarcoFronteiraBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return FORMA;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity quem, ItemStack stack) {
		super.setPlacedBy(level, pos, state, quem, stack);
		if (level instanceof ServerLevel server) {
			Territorio.marcoColocado(server, pos, quem instanceof Player p ? p : null);
		}
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server) {
			Territorio.marcoRemovido(server, pos);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
