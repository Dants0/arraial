package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.obra.PostoDirecionalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The urna: players read the council, propose and campaign here (see {@link Politica}). */
public class UrnaBlock extends PostoDirecionalBlock {
	public UrnaBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			Politica.usar(server, pos, player, stack);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server) {
			Politica.usar(server, pos, player, ItemStack.EMPTY);
		}
		return InteractionResult.SUCCESS;
	}
}
