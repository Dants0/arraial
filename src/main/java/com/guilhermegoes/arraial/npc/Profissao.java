package com.guilhermegoes.arraial.npc;

import net.minecraft.network.chat.Component;

/** What a morador does for the arraial. New jobs (lenhador, oleiro, roceiro...) get added here. */
public enum Profissao {
	NENHUMA("morador"),
	PEDREIRO("pedreiro");

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

	public static Profissao porIndice(int i) {
		Profissao[] todas = values();
		return i >= 0 && i < todas.length ? todas[i] : NENHUMA;
	}
}
