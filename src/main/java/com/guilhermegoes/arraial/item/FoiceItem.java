package com.guilhermegoes.arraial.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Foice: the sickle of the roça. Right-click a ripe crop and it reaps the ripe ones in a 3x3
 * around it, dropping the harvest and leaving each plant replanted (back to its first stage).
 */
public class FoiceItem extends Item {
	public FoiceItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		if (!(ctx.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos centro = ctx.getClickedPos();
		if (!(level.getBlockState(centro).getBlock() instanceof CropBlock)) {
			return InteractionResult.PASS;
		}
		int colheu = 0;
		for (BlockPos p : BlockPos.betweenClosed(centro.offset(-1, 0, -1), centro.offset(1, 0, 1))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof CropBlock crop && crop.getAge(s) >= crop.getMaxAge()) {
				Block.dropResources(s, level, p);
				level.setBlock(p, crop.getStateForAge(0), Block.UPDATE_ALL);
				colheu++;
			}
		}
		if (colheu == 0) {
			return InteractionResult.PASS;
		}
		level.playSound(null, centro, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
		if (ctx.getPlayer() != null) {
			ctx.getItemInHand().hurtAndBreak(colheu, ctx.getPlayer(), EquipmentSlot.MAINHAND);
		}
		return InteractionResult.SUCCESS;
	}
}
