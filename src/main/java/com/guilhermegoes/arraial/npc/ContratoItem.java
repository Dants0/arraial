package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** "Contrato de Pedreiro": using it on a block hires a pedreiro on that spot. */
public class ContratoItem extends Item {
	public ContratoItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		PedreiroEntity pedreiro = ModEntities.PEDREIRO.spawn(level, pos, EntitySpawnReason.SPAWN_ITEM_USE);
		if (pedreiro == null) {
			return InteractionResult.FAIL;
		}
		Player player = context.getPlayer();
		if (player != null) {
			player.sendSystemMessage(Component.translatable("arraial.pedreiro.contratado", pedreiro.nome())
					.withStyle(ChatFormatting.GOLD));
			if (!player.getAbilities().instabuild) {
				context.getItemInHand().shrink(1);
			}
		}
		return InteractionResult.CONSUME;
	}
}
