package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.registry.ModBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * What a morador does for the arraial. Professions with a workstation ("posto") are taken
 * automatically by an unemployed morador who finds a free one inside the arraial.
 * New values go at the END: saves store the ordinal.
 */
public enum Profissao {
	NENHUMA("morador"),
	PEDREIRO("pedreiro"),
	LENHADOR("lenhador"),
	ROCEIRO("roceiro"),
	OLEIRO("oleiro"),
	SOLDADO("soldado"),
	PASTOR("pastor"),
	BARREIRO("barreiro"),
	CAVOUQUEIRO("cavouqueiro"),
	FARINHEIRO("farinheiro"),
	FEIRANTE("feirante"),
	TROPEIRO("tropeiro"),
	BOTICARIO("boticario"),
	PESCADOR("pescador"),
	CACADOR("cacador"),
	ENGENHEIRO("engenheiro"),
	RELACOES("relacoes"),
	/** A travelling peddler from a neighbouring arraial (not a resident; see Comercio). */
	MASCATE("mascate"),
	/** The engenho: cuts cane and grinds it at the moenda into sugar and rapadura. */
	MESTRE_ENGENHO("mestre_engenho"),
	/** The cafezal: picks the coffee and dries and roasts it on the terreiro. */
	CAFEICULTOR("cafeicultor"),
	/** The Baroque carver: gilded talha and soapstone for the Matriz. */
	ENTALHADOR("entalhador"),
	/** The river trader: takes the canoe from the trapiche to other arraiais with a trapiche. */
	BARQUEIRO("barqueiro"),
	ESTIVADOR("estivador"),
	MOLEIRO("moleiro");

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
			case SOLDADO -> ModBlocks.POSTO_GUARDA;
			case PASTOR -> ModBlocks.COCHO;
			case BARREIRO -> ModBlocks.GAMELA;
			case CAVOUQUEIRO -> ModBlocks.BANCADA_CANTEIRO;
			case FARINHEIRO -> ModBlocks.FORNO_FARINHA;
			case FEIRANTE -> ModBlocks.BANCA_FEIRA;
			case TROPEIRO -> ModBlocks.CANGALHA;
			case BOTICARIO -> ModBlocks.BALCAO_BOTICA;
			case PESCADOR -> ModBlocks.COVO;
			case CACADOR -> ModBlocks.VARAL_CACA;
			case ENGENHEIRO -> ModBlocks.PRANCHETA;
			case RELACOES -> ModBlocks.ESCRIVANINHA;
			case MESTRE_ENGENHO -> ModBlocks.MOENDA;
			case CAFEICULTOR -> ModBlocks.TERREIRO;
			case ENTALHADOR -> ModBlocks.BANCADA_ENTALHE;
			case BARQUEIRO -> ModBlocks.AMARRA;
			case ESTIVADOR -> ModBlocks.GUINDASTE;
			case MOLEIRO -> ModBlocks.MO;
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
