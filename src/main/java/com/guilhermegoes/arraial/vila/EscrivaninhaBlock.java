package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.obra.PostoDirecionalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The relações públicas' desk; players open the diplomacy screen and the region map here (see {@link Chancelaria}). */
public class EscrivaninhaBlock extends PostoDirecionalBlock {
	public EscrivaninhaBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer sp) {
			if (Vilas.maisProxima(sp.level(), pos) == null) {
				sp.sendOverlayMessage(Component.translatable("arraial.feira.sem_vila"));
			} else {
				Chancelaria.abrir(sp, pos, Component.empty());
			}
		}
		return InteractionResult.SUCCESS;
	}
}
