package com.guilhermegoes.arraial.vila;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.Nullable;

/**
 * Crônica do Arraial: the village's memory. Every notable event (founding, arrivals,
 * new trades, finished buildings, hunger, departures) is written down with its day.
 */
public final class Cronica {
	public record Entrada(long dia, Component texto) {
		public static final Codec<Entrada> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("dia").forGetter(Entrada::dia),
				ComponentSerialization.CODEC.fieldOf("texto").forGetter(Entrada::texto)
		).apply(i, Entrada::new));

		public MutableComponent linha() {
			return Component.translatable("arraial.cronica.dia", dia).withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.literal(" ")).append(texto.copy().withStyle(ChatFormatting.RESET));
		}
	}

	public static final Codec<List<Entrada>> LISTA = Entrada.CODEC.listOf();
	/** Older entries are dropped beyond this, to keep the save small. */
	public static final int MAXIMO = 300;
	private static final int LINHAS_POR_PAGINA = 6;

	/** Writes an event into the chronicle of the arraial at {@code vila} (if it is loaded). */
	public static void anotar(ServerLevel level, @Nullable BlockPos vila, Component texto) {
		if (vila != null && level.isLoaded(vila) && level.getBlockEntity(vila) instanceof ArraialBlockEntity a) {
			a.anotar(new Entrada(Vilas.dia(level), texto));
		}
	}

	/** A written book holding the whole chronicle, oldest first. */
	public static ItemStack livro(ArraialBlockEntity arraial) {
		List<Filterable<Component>> paginas = new ArrayList<>();
		List<Entrada> entradas = arraial.cronica();
		MutableComponent capa = Component.literal(arraial.nomeCompleto()).withStyle(ChatFormatting.BOLD)
				.append(Component.literal("\n\n")).append(Component.translatable("arraial.cronica.capa", entradas.size()).withStyle(ChatFormatting.RESET));
		paginas.add(Filterable.passThrough(capa));
		MutableComponent pagina = Component.empty();
		int linhas = 0;
		for (Entrada e : entradas) {
			if (linhas > 0) {
				pagina.append(Component.literal("\n\n"));
			}
			pagina.append(e.linha());
			if (++linhas == LINHAS_POR_PAGINA) {
				paginas.add(Filterable.passThrough(pagina));
				pagina = Component.empty();
				linhas = 0;
			}
		}
		if (linhas > 0) {
			paginas.add(Filterable.passThrough(pagina));
		}
		ItemStack livro = new ItemStack(Items.WRITTEN_BOOK);
		String titulo = arraial.nomeCompleto().length() > 32 ? arraial.nomeCompleto().substring(0, 32) : arraial.nomeCompleto();
		livro.set(DataComponents.WRITTEN_BOOK_CONTENT,
				new WrittenBookContent(Filterable.passThrough(titulo), "Crônica", 0, paginas, true));
		return livro;
	}

	private Cronica() {
	}
}
