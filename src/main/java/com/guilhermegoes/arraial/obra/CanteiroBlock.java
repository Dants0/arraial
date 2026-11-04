package com.guilhermegoes.arraial.obra;

import com.guilhermegoes.arraial.registry.ModItems;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Marco da Obra. Place it facing where the building should go, right-click it
 * with the Livro de Projetos to pick a project, and put a chest next to it.
 * Right-click with an empty hand to see progress and missing materials.
 */
public class CanteiroBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public CanteiroBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// The building goes in the direction the player is looking.
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CanteiroBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(ModItems.LIVRO_DE_PROJETOS)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!(level instanceof ServerLevel server) || !(level.getBlockEntity(pos) instanceof CanteiroBlockEntity canteiro)) {
			return InteractionResult.SUCCESS;
		}
		if (canteiro.iniciada() && !canteiro.concluida()) {
			player.sendOverlayMessage(Component.translatable("arraial.canteiro.obra_em_andamento").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}

		// The book carries the choice (picked by right-clicking in the air); the marker only receives it.
		Catalogo.Projeto novo = LivroDeProjetosItem.selecionado(stack).orElse(null);
		if (novo == null) {
			player.sendOverlayMessage(Component.translatable("arraial.livro.escolha_primeiro").withStyle(ChatFormatting.YELLOW));
			return InteractionResult.SUCCESS;
		}
		if (canteiro.projeto().map(novo::equals).orElse(false)) {
			relatorio(server, player, canteiro);
			return InteractionResult.SUCCESS;
		}
		canteiro.escolherProjeto(novo);

		MutableComponent msg = Component.translatable("arraial.canteiro.projeto", novo.nome().copy().withStyle(ChatFormatting.GOLD));
		canteiro.plano(server).ifPresentOrElse(
				plano -> msg.append(Component.translatable("arraial.canteiro.tamanho",
						plano.area().getXSpan(), plano.area().getYSpan(), plano.area().getZSpan(),
						plano.materiais(0).values().stream().mapToInt(Integer::intValue).sum())),
				() -> msg.append(Component.translatable("arraial.canteiro.sem_arquivo").withStyle(ChatFormatting.RED)));
		player.sendOverlayMessage(msg);
		relatorio(server, player, canteiro);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof CanteiroBlockEntity canteiro) {
			relatorio(server, player, canteiro);
		}
		return InteractionResult.SUCCESS;
	}

	/** Chat report: project, progress, and every material still missing from the chest. */
	private static void relatorio(ServerLevel server, Player player, CanteiroBlockEntity canteiro) {
		if (canteiro.projeto().isEmpty()) {
			player.sendSystemMessage(Component.translatable("arraial.canteiro.vazio").withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (canteiro.concluida()) {
			player.sendSystemMessage(Component.translatable("arraial.canteiro.concluida", canteiro.projeto().get().nome())
					.withStyle(ChatFormatting.GREEN));
			return;
		}
		PlanoDeObra plano = canteiro.plano(server).orElse(null);
		if (plano == null) {
			player.sendSystemMessage(Component.translatable("arraial.canteiro.sem_arquivo").withStyle(ChatFormatting.RED));
			return;
		}

		int pct = plano.etapas().isEmpty() ? 100 : canteiro.progresso() * 100 / plano.etapas().size();
		player.sendSystemMessage(Component.translatable("arraial.canteiro.status", canteiro.projeto().get().nome(), pct)
				.withStyle(ChatFormatting.GOLD));

		Container bau = canteiro.bau(server);
		if (bau == null) {
			player.sendSystemMessage(Component.translatable("arraial.canteiro.sem_bau").withStyle(ChatFormatting.RED));
		}
		boolean faltaAlgo = false;
		for (Map.Entry<Item, Integer> e : plano.materiais(canteiro.progresso()).entrySet()) {
			int noBau = bau == null ? 0 : contar(bau, e.getKey());
			int falta = e.getValue() - noBau;
			if (falta > 0) {
				faltaAlgo = true;
				player.sendSystemMessage(Component.literal(" • ")
						.append(Component.translatable("arraial.canteiro.falta", falta, e.getKey().getDefaultInstance().getHoverName()))
						.withStyle(ChatFormatting.GRAY));
			}
		}
		if (!faltaAlgo && bau != null) {
			player.sendSystemMessage(Component.translatable("arraial.canteiro.tudo_pronto").withStyle(ChatFormatting.GREEN));
		}
	}

	public static int contar(Container bau, Item item) {
		int total = 0;
		for (int i = 0; i < bau.getContainerSize(); i++) {
			ItemStack s = bau.getItem(i);
			if (s.is(item)) {
				total += s.getCount();
			}
		}
		return total;
	}
}
