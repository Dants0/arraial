package com.guilhermegoes.arraial.obra;

import com.guilhermegoes.arraial.registry.ModComponents;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Livro de Projetos. Right-click in the air to flip through the catalog (sneak to go back);
 * the chosen project shows in the book's name. Right-click a Marco da Obra to hand it that project.
 */
public class LivroDeProjetosItem extends Item {
	public LivroDeProjetosItem(Item.Properties properties) {
		super(properties);
	}

	public static Optional<Catalogo.Projeto> selecionado(ItemStack stack) {
		String id = stack.get(ModComponents.PROJETO);
		return id == null ? Optional.empty() : Catalogo.porId(id);
	}

	@Override
	public Component getName(ItemStack stack) {
		Component base = super.getName(stack);
		return selecionado(stack)
				.<Component>map(p -> Component.translatable("item.arraial.livro_de_projetos.com", base, p.nome()))
				.orElse(base);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		String atual = selecionado(stack).map(Catalogo.Projeto::id).orElse("");
		Catalogo.Projeto novo = Catalogo.vizinho(atual, player.isShiftKeyDown());
		stack.set(ModComponents.PROJETO, novo.id());

		if (level instanceof ServerLevel server) {
			player.sendOverlayMessage(Component.translatable("arraial.livro.selecionado", novo.nome().copy().withStyle(ChatFormatting.GOLD)));
			// Show the shopping list right away, so the player can gather materials before picking a spot.
			PlanoDeObra.criar(server, novo, player.blockPosition(), player.getDirection()).ifPresent(plano -> {
				player.sendSystemMessage(Component.translatable("arraial.livro.materiais", novo.nome()).withStyle(ChatFormatting.GOLD));
				for (Map.Entry<Item, Integer> e : plano.materiais(0).entrySet()) {
					player.sendSystemMessage(Component.literal(" • " + e.getValue() + "× ")
							.append(e.getKey().getDefaultInstance().getHoverName())
							.withStyle(ChatFormatting.GRAY));
				}
			});
		}
		return InteractionResult.SUCCESS;
	}
}
