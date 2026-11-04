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

	private static final Set<PoiType> POSTOS = Set.of(CEPO, BALAIO, FORNO_OLARIA, POSTO_GUARDA, COCHO, GAMELA, BANCADA_CANTEIRO);

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
			default -> null;
		};
	}

	public static void init() {
	}

	private ModPois() {
	}
}
