package com.guilhermegoes.arraial.npc;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * "Contrato de Pedreiro". Used on a block it brings a new pedreiro to that spot;
 * used on an existing morador it makes them a pedreiro (see MoradorEntity#mobInteract).
 */
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
		MoradorEntity pedreiro = MoradorEntity.criar(level, pos, Profissao.PEDREIRO);
		if (pedreiro == null) {
			return InteractionResult.FAIL;
		}
		Player player = context.getPlayer();
		if (player != null) {
			player.sendSystemMessage(Component.translatable("arraial.pedreiro.contratado", pedreiro.nome(), pedreiro.titulo())
					.withStyle(ChatFormatting.GOLD));
			if (!player.getAbilities().instabuild) {
				context.getItemInHand().shrink(1);
			}
		}
		return InteractionResult.CONSUME;
	}
}
