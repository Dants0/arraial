package com.guilhermegoes.arraial.obra;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * How urgent a construction site is. Higher-priority sites get the pedreiros first and keep
 * their materials in the armazém reserved; artisans make their goods first.
 * Saved by ordinal: append new values at the end only.
 */
public enum Prioridade {
	BAIXA(ChatFormatting.GRAY),
	NORMAL(ChatFormatting.WHITE),
	ALTA(ChatFormatting.YELLOW),
	URGENTE(ChatFormatting.RED);

	private final ChatFormatting cor;

	Prioridade(ChatFormatting cor) {
		this.cor = cor;
	}

	public Component nome() {
		return Component.translatable("arraial.prioridade." + name().toLowerCase(java.util.Locale.ROOT)).withStyle(cor);
	}

	public Prioridade proxima() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static Prioridade de(int ordinal) {
		return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : NORMAL;
	}
}
