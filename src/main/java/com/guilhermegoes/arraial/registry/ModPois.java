package com.guilhermegoes.arraial.registry;

import com.guilhermegoes.arraial.Arraial;
import java.util.Set;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;

/** Workstations as points of interest, so moradores can claim one each (like beds). */
public final class ModPois {
	public static final PoiType CEPO = PoiHelper.register(Arraial.id("cepo"), 1, 1, ModBlocks.CEPO);
	public static final PoiType BALAIO = PoiHelper.register(Arraial.id("balaio"), 1, 1, ModBlocks.BALAIO);
	public static final PoiType FORNO_OLARIA = PoiHelper.register(Arraial.id("forno_olaria"), 1, 1, ModBlocks.FORNO_OLARIA);
	public static final PoiType POSTO_GUARDA = PoiHelper.register(Arraial.id("posto_guarda"), 1, 1, ModBlocks.POSTO_GUARDA);
	public static final PoiType COCHO = PoiHelper.register(Arraial.id("cocho"), 1, 1, ModBlocks.COCHO);
	public static final PoiType GAMELA = PoiHelper.register(Arraial.id("gamela"), 1, 1, ModBlocks.GAMELA);
	public static final PoiType BANCADA_CANTEIRO = PoiHelper.register(Arraial.id("bancada_canteiro"), 1, 1, ModBlocks.BANCADA_CANTEIRO);
	public static final PoiType FORNO_FARINHA = PoiHelper.register(Arraial.id("forno_farinha"), 1, 1, ModBlocks.FORNO_FARINHA);
	public static final PoiType BANCA_FEIRA = PoiHelper.register(Arraial.id("banca_feira"), 1, 1, ModBlocks.BANCA_FEIRA);
	public static final PoiType CANGALHA = PoiHelper.register(Arraial.id("cangalha"), 1, 1, ModBlocks.CANGALHA);
	public static final PoiType BALCAO_BOTICA = PoiHelper.register(Arraial.id("balcao_botica"), 1, 1, ModBlocks.BALCAO_BOTICA);
	public static final PoiType COVO = PoiHelper.register(Arraial.id("covo"), 1, 1, ModBlocks.COVO);
	public static final PoiType VARAL_CACA = PoiHelper.register(Arraial.id("varal_caca"), 1, 1, ModBlocks.VARAL_CACA);
	public static final PoiType PRANCHETA = PoiHelper.register(Arraial.id("prancheta"), 1, 1, ModBlocks.PRANCHETA);
	public static final PoiType ESCRIVANINHA = PoiHelper.register(Arraial.id("escrivaninha"), 1, 1, ModBlocks.ESCRIVANINHA);
	public static final PoiType MOENDA = PoiHelper.register(Arraial.id("moenda"), 1, 1, ModBlocks.MOENDA);
	public static final PoiType TERREIRO = PoiHelper.register(Arraial.id("terreiro"), 1, 1, ModBlocks.TERREIRO);
	public static final PoiType BANCADA_ENTALHE = PoiHelper.register(Arraial.id("bancada_entalhe"), 1, 1, ModBlocks.BANCADA_ENTALHE);

	public static final PoiType AMARRA = PoiHelper.register(Arraial.id("amarra"), 1, 1, ModBlocks.AMARRA);
	public static final PoiType GUINDASTE = PoiHelper.register(Arraial.id("guindaste"), 1, 1, ModBlocks.GUINDASTE);
	public static final PoiType MO = PoiHelper.register(Arraial.id("mo"), 1, 1, ModBlocks.MO);
	public static final PoiType BILHETERIA = PoiHelper.register(Arraial.id("bilheteria"), 1, 1, ModBlocks.BILHETERIA);

	private static final Set<PoiType> POSTOS = Set.of(CEPO, BALAIO, FORNO_OLARIA, POSTO_GUARDA, COCHO, GAMELA, BANCADA_CANTEIRO,
			FORNO_FARINHA, BANCA_FEIRA, CANGALHA, BALCAO_BOTICA,
			COVO, VARAL_CACA, PRANCHETA, ESCRIVANINHA, MOENDA, TERREIRO, BANCADA_ENTALHE, AMARRA, GUINDASTE, MO, BILHETERIA);

	public static boolean ePosto(Holder<PoiType> tipo) {
		return POSTOS.contains(tipo.value());
	}

	/** The workstation type of a trade, or null (pedreiro). */
	public static @org.jspecify.annotations.Nullable PoiType de(com.guilhermegoes.arraial.npc.Profissao p) {
		return switch (p) {
			case LENHADOR -> CEPO;
			case ROCEIRO -> BALAIO;
			case OLEIRO -> FORNO_OLARIA;
			case SOLDADO -> POSTO_GUARDA;
			case PASTOR -> COCHO;
			case BARREIRO -> GAMELA;
			case CAVOUQUEIRO -> BANCADA_CANTEIRO;
			case FARINHEIRO -> FORNO_FARINHA;
			case FEIRANTE -> BANCA_FEIRA;
			case TROPEIRO -> CANGALHA;
			case BOTICARIO -> BALCAO_BOTICA;
			case PESCADOR -> COVO;
			case CACADOR -> VARAL_CACA;
			case ENGENHEIRO -> PRANCHETA;
			case RELACOES -> ESCRIVANINHA;
			case MESTRE_ENGENHO -> MOENDA;
			case CAFEICULTOR -> TERREIRO;
			case ENTALHADOR -> BANCADA_ENTALHE;
			case BARQUEIRO -> AMARRA;
			case ESTIVADOR -> GUINDASTE;
			case MOLEIRO -> MO;
			case FERROVIARIO -> BILHETERIA;
			default -> null;
		};
	}

	public static void init() {
	}

	private ModPois() {
	}
}
