package com.guilhermegoes.arraial.item;

import com.guilhermegoes.arraial.vila.Chancelaria;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Mapa da Região: a hand-drawn chart of the arraiais around, their lands, roads, wars and ruins. */
public class MapaRegiaoItem extends Item {
	public MapaRegiaoItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			Chancelaria.abrir(sp, null, Component.empty());
		}
		return InteractionResult.SUCCESS;
	}
}
