package com.guilhermegoes.arraial.vila;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

/**
 * Guia do Arraial: opened (right-click) it turns into a written book explaining how the
 * arraial works: founding, trades, the prefeito, food, safety, crises, the feira, the other
 * villages, the stages and satisfaction. Pages live in the lang files ("arraial.guia.N"),
 * so the book reads in the player's language.
 */
public class GuiaItem extends Item {
	public static final int PAGINAS = 23;

	public GuiaItem(Properties properties) {
		super(properties);
	}

	public static ItemStack livro() {
		List<Filterable<Component>> paginas = new ArrayList<>();
		for (int i = 0; i < PAGINAS; i++) {
			paginas.add(Filterable.passThrough(Component.translatable("arraial.guia." + i)));
		}
		ItemStack livro = new ItemStack(Items.WRITTEN_BOOK);
		livro.set(DataComponents.WRITTEN_BOOK_CONTENT,
				new WrittenBookContent(Filterable.passThrough("Guia do Arraial"), "Arraial", 0, paginas, true));
		return livro;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide()) {
			player.setItemInHand(hand, livro());
		}
		return InteractionResult.SUCCESS;
	}
}
