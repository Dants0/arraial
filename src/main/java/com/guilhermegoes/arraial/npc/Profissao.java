package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.registry.ModBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * What a morador does for the arraial. Professions with a workstation ("posto") are taken
 * automatically by an unemployed morador who finds a free one inside the arraial.
 */
public enum Profissao {
	NENHUMA("morador"),
	PEDREIRO("pedreiro"),
	LENHADOR("lenhador"),
	ROCEIRO("roceiro"),
	OLEIRO("oleiro");

	private final String id;

	Profissao(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	/** "Pedreiro" / "Pedreira", "Morador" / "Moradora". */
	public Component titulo(boolean feminino) {
		return Component.translatable("arraial.profissao." + id + (feminino ? ".f" : ".m"));
	}

	/** The workstation block that grants this profession, or null (pedreiro comes by contract). */
	public @Nullable Block posto() {
		return switch (this) {
			case LENHADOR -> ModBlocks.CEPO;
			case ROCEIRO -> ModBlocks.BALAIO;
			case OLEIRO -> ModBlocks.FORNO_OLARIA;
			default -> null;
		};
	}

	public static @Nullable Profissao doPosto(Block bloco) {
		for (Profissao p : values()) {
			if (p.posto() == bloco) {
				return p;
			}
		}
		return null;
	}

	public static Profissao porIndice(int i) {
		Profissao[] todas = values();
		return i >= 0 && i < todas.length ? todas[i] : NENHUMA;
	}
}
