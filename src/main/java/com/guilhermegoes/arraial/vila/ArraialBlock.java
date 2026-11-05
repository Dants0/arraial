package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.npc.MoradorEntity;
import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import com.guilhermegoes.arraial.obra.PlanoDeObra;
import com.guilhermegoes.arraial.registry.ModBlockEntities;
import com.guilhermegoes.arraial.npc.Profissao;
import com.guilhermegoes.arraial.trabalho.Estoque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
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
			if (player.isShiftKeyDown()) {
				ultimasNoticias(player, arraial);
			} else {
				relatorio(server, player, arraial);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(net.minecraft.world.item.Items.BOOK)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof ArraialBlockEntity arraial) {
			// A plain book becomes a copy of the chronicle.
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			ItemStack copia = Cronica.livro(arraial);
			if (!player.getInventory().add(copia)) {
				Block.popResource(level, player.blockPosition(), copia);
			}
			player.sendSystemMessage(Component.translatable("arraial.cronica.copiada", arraial.nome()).withStyle(ChatFormatting.GOLD));
		}
		return InteractionResult.SUCCESS;
	}

	private static void ultimasNoticias(Player player, ArraialBlockEntity arraial) {
		List<Cronica.Entrada> todas = arraial.cronica();
		player.sendSystemMessage(Component.translatable("arraial.cronica.titulo", arraial.nome()).withStyle(ChatFormatting.GOLD));
		for (Cronica.Entrada e : todas.subList(Math.max(0, todas.size() - 8), todas.size())) {
			player.sendSystemMessage(e.linha());
		}
		player.sendSystemMessage(Component.translatable("arraial.cronica.dica").withStyle(ChatFormatting.GRAY));
	}

	private static void relatorio(ServerLevel level, Player player, ArraialBlockEntity arraial) {
		List<MoradorEntity> moradores = arraial.moradores(level);
		long camas = arraial.camas(level);
		player.sendSystemMessage(Component.literal("✝ " + arraial.nome()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		player.sendSystemMessage(Component.translatable("arraial.vila.populacao", moradores.size(), camas).withStyle(ChatFormatting.YELLOW));
		MoradorEntity prefeito = moradores.stream().filter(m -> m.getUUID().equals(arraial.prefeito())).findFirst().orElse(null);
		if (prefeito != null) {
			player.sendSystemMessage(Component.translatable("arraial.vila.prefeito", Prefeitura.cargo(prefeito), prefeito.nome())
					.withStyle(ChatFormatting.GOLD));
		} else if (arraial.prefeito() == null) {
			player.sendSystemMessage(Component.translatable("arraial.vila.sem_prefeito", Prefeitura.MIN_ELEITORES).withStyle(ChatFormatting.GRAY));
		}
		Component crise = arraial.crise().descricao(level);
		if (crise != null) {
			player.sendSystemMessage(Component.literal("⚠ ").append(crise).withStyle(ChatFormatting.RED));
		}
		if (!arraial.pedido().getString().isEmpty()) {
			player.sendSystemMessage(Component.literal("📜 ").append(arraial.pedido()).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		if (camas <= moradores.size()) {
			player.sendSystemMessage(Component.translatable("arraial.vila.sem_vaga").withStyle(ChatFormatting.GRAY));
		}
		long postos = arraial.postosLivres(level);
		if (postos > 0) {
			player.sendSystemMessage(Component.translatable("arraial.vila.postos_livres", postos).withStyle(ChatFormatting.YELLOW));
		}
		List<ArmazemBlockEntity> armazens = Vilas.armazens(level, arraial.getBlockPos());
		if (armazens.isEmpty()) {
			player.sendSystemMessage(Component.translatable("arraial.vila.sem_armazem").withStyle(ChatFormatting.RED));
		} else {
			int comida = Vilas.contar(armazens, s -> s.has(net.minecraft.core.component.DataComponents.FOOD));
			int dias = moradores.isEmpty() ? 0 : comida / moradores.size();
			player.sendSystemMessage(Component.translatable("arraial.vila.comida", comida, dias)
					.withStyle(dias < 2 ? ChatFormatting.RED : ChatFormatting.GREEN));
		}
		for (MoradorEntity m : moradores) {
			Component casa = m.casa() != null ? Component.translatable("arraial.morador.tem_casa") : Component.translatable("arraial.morador.sem_casa");
			player.sendSystemMessage(Component.literal(" • ")
					.append(Component.translatable("arraial.vila.linha_morador", m.nome(), m.titulo(), casa))
					.withStyle(ChatFormatting.WHITE));
		}
		BlockPos centro = arraial.getBlockPos();
		// Sites in the order the pedreiros take them.
		for (CanteiroBlockEntity c : CanteiroBlockEntity.daVila(level, centro)) {
			int total = c.plano(level).map(PlanoDeObra::etapas).map(List::size).orElse(0);
			int pct = total == 0 ? 0 : c.progresso() * 100 / total;
			player.sendSystemMessage(Component.literal(" ⚒ ")
					.append(Component.translatable("arraial.vila.linha_obra", c.projeto().get().nome(), pct).withStyle(ChatFormatting.AQUA))
					.append(Component.literal(" · "))
					.append(c.prioridade().nome()));
		}
		// The prefeito's minimum stock and how far along it is.
		Map<Item, Integer> inv = Vilas.inventario(armazens);
		Set<Profissao> oficios = EnumSet.noneOf(Profissao.class);
		moradores.forEach(m -> oficios.add(m.profissao()));
		Map<Item, Integer> metas = Estoque.metas(level, centro, moradores.size(), oficios, inv);
		if (!metas.isEmpty()) {
			MutableComponent linha = Component.translatable("arraial.vila.metas").withStyle(ChatFormatting.GOLD);
			boolean primeiro = true;
			for (Map.Entry<Item, Integer> e : metas.entrySet()) {
				int tem = inv.getOrDefault(e.getKey(), 0);
				linha.append(Component.literal(primeiro ? " " : " · ").withStyle(ChatFormatting.DARK_GRAY));
				linha.append(Component.empty().append(e.getKey().getDefaultInstance().getHoverName())
						.append(" " + Math.min(tem, e.getValue()) + "/" + e.getValue())
						.withStyle(tem >= e.getValue() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
				primeiro = false;
			}
			player.sendSystemMessage(linha);
		}
	}
}
