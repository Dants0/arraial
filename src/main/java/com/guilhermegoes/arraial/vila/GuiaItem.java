package com.guilhermegoes.arraial.vila;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
 * Guia do Arraial: opened (right-click) it turns into a written book. It is laid out like a game
 * manual: a cover, a clickable table of contents, then the early game (founding, first works,
 * food), the mid game (mayor, stages, feira, crises, festas, neighbours, politics), the late game
 * (eras, territory, diplomacy, culture, families, ruins, the road to Cidade), and reference
 * chapters on the trades, commerce, items and blocks. Pages live in the lang files
 * ("arraial.guia.&lt;page&gt;"), so the book reads in the player's language.
 */
public class GuiaItem extends Item {
	private record Capitulo(String id, List<String> paginas) {
	}

	private static final List<Capitulo> CAPITULOS = List.of(
			new Capitulo("inicio", List.of("inicio.1", "inicio.2", "inicio.3", "inicio.4", "inicio.5", "inicio.6")),
			new Capitulo("meio", List.of("meio.1", "meio.2", "meio.3", "meio.4", "meio.5", "meio.6", "meio.7", "meio.8", "meio.9",
					"meio.10", "meio.11")),
			new Capitulo("fim", List.of("fim.1", "fim.2", "fim.3", "fim.4", "fim.5", "fim.6", "fim.7", "fim.8", "fim.9", "fim.10", "fim.11", "fim.12", "fim.13")),
			new Capitulo("oficios", List.of("oficios.1", "oficios.2", "oficios.3", "oficios.4", "oficios.5", "oficios.6", "oficios.7", "oficios.8")),
			new Capitulo("comercio", List.of("comercio.1", "comercio.2", "comercio.3", "comercio.4", "comercio.5")),
			new Capitulo("itens", List.of("itens.1", "itens.2", "itens.3", "itens.4", "itens.5", "itens.6", "itens.7")),
			new Capitulo("fauna", List.of("fauna.1", "fauna.2")),
			new Capitulo("rios", List.of("rios.1")),
			new Capitulo("dicas", List.of("dicas.1", "dicas.2")));

	public GuiaItem(Properties properties) {
		super(properties);
	}

	public static ItemStack livro() {
		List<Filterable<Component>> paginas = new ArrayList<>();
		paginas.add(Filterable.passThrough(pagina("capa")));
		paginas.add(Filterable.passThrough(sumario()));
		for (Capitulo c : CAPITULOS) {
			for (String p : c.paginas()) {
				paginas.add(Filterable.passThrough(pagina(p)));
			}
		}
		ItemStack livro = new ItemStack(Items.WRITTEN_BOOK);
		livro.set(DataComponents.WRITTEN_BOOK_CONTENT,
				new WrittenBookContent(Filterable.passThrough("Guia do Arraial"), "Arraial", 0, paginas, true));
		return livro;
	}

	/** Dark ink on the paper, whatever a resource pack or another mod sets as the default text colour. */
	private static final int TINTA = 0x1E1408;

	private static Component pagina(String chave) {
		return Component.translatable("arraial.guia." + chave).withColor(TINTA);
	}

	/** The table of contents: each chapter is a link to its first page. */
	private static Component sumario() {
		MutableComponent s = Component.empty().withColor(TINTA).append(Component.translatable("arraial.guia.sumario").withStyle(ChatFormatting.BOLD)).append("\n");
		int pagina = 3; // 1: cover, 2: contents
		for (Capitulo c : CAPITULOS) {
			final int alvo = pagina;
			s.append("\n").append(Component.literal("› ").append(Component.translatable("arraial.guia.cap." + c.id()))
					.append(Component.literal(" " + pagina).withStyle(ChatFormatting.DARK_GRAY))
					.withStyle(st -> st.withColor(ChatFormatting.DARK_BLUE).withUnderlined(true).withClickEvent(new ClickEvent.ChangePage(alvo))));
			pagina += c.paginas().size();
		}
		return s.append("\n\n").append(Component.translatable("arraial.guia.sumario_dica").withStyle(ChatFormatting.DARK_GRAY));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide()) {
			player.setItemInHand(hand, livro());
		}
		return InteractionResult.SUCCESS;
	}
}
