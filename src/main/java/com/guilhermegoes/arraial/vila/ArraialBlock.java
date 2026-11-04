package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import com.guilhermegoes.arraial.obra.PlanoDeObra;
import com.guilhermegoes.arraial.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Cruzeiro: placing it founds an arraial. Right-click it to see the village:
 * name, population, free beds, who lives there and what is being built.
 */
public class ArraialBlock extends Block implements EntityBlock {
	private static final VoxelShape FORMA = Block.box(6, 0, 6, 10, 16, 10);

	public ArraialBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return FORMA;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ArraialBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != ModBlockEntities.ARRAIAL
				? null
				: (BlockEntityTicker<T>) (BlockEntityTicker<ArraialBlockEntity>) ArraialBlockEntity::tick;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
		super.setPlacedBy(level, pos, state, by, stack);
		if (!(level instanceof ServerLevel server) || !(level.getBlockEntity(pos) instanceof ArraialBlockEntity arraial)) {
			return;
		}
		if (Vilas.temVizinha(server, pos)) {
			level.destroyBlock(pos, true);
			if (by instanceof Player p) {
				p.sendSystemMessage(Component.translatable("arraial.vila.muito_perto", Vilas.DISTANCIA_MINIMA).withStyle(ChatFormatting.RED));
			}
			return;
		}
		arraial.fundar(server);
		if (by instanceof Player p) {
			p.sendSystemMessage(Component.translatable("arraial.vila.fundado", arraial.nome()).withStyle(ChatFormatting.GOLD));
			p.sendSystemMessage(Component.translatable("arraial.vila.dica").withStyle(ChatFormatting.GRAY));
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof ArraialBlockEntity arraial) {
			relatorio(server, player, arraial);
		}
		return InteractionResult.SUCCESS;
	}

	private static void relatorio(ServerLevel level, Player player, ArraialBlockEntity arraial) {
		List<MoradorEntity> moradores = arraial.moradores(level);
		long camas = arraial.camas(level);
		player.sendSystemMessage(Component.literal("✝ " + arraial.nome()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		player.sendSystemMessage(Component.translatable("arraial.vila.populacao", moradores.size(), camas).withStyle(ChatFormatting.YELLOW));
		if (camas <= moradores.size()) {
			player.sendSystemMessage(Component.translatable("arraial.vila.sem_vaga").withStyle(ChatFormatting.GRAY));
		}
		for (MoradorEntity m : moradores) {
			Component casa = m.casa() != null ? Component.translatable("arraial.morador.tem_casa") : Component.translatable("arraial.morador.sem_casa");
			player.sendSystemMessage(Component.literal(" • ")
					.append(Component.translatable("arraial.vila.linha_morador", m.nome(), m.titulo(), casa))
					.withStyle(ChatFormatting.WHITE));
		}
		BlockPos centro = arraial.getBlockPos();
		for (BlockPos p : CanteiroBlockEntity.ativos(level)) {
			if (p.closerThan(centro, Vilas.RAIO) && level.getBlockEntity(p) instanceof CanteiroBlockEntity c && c.temTrabalho()) {
				int total = c.plano(level).map(PlanoDeObra::etapas).map(List::size).orElse(0);
				int pct = total == 0 ? 0 : c.progresso() * 100 / total;
				player.sendSystemMessage(Component.literal(" ⚒ ")
						.append(Component.translatable("arraial.vila.linha_obra", c.projeto().get().nome(), pct))
						.withStyle(ChatFormatting.AQUA));
			}
		}
	}
}
