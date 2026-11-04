package com.guilhermegoes.arraial.registry;

import com.guilhermegoes.arraial.Arraial;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;

/** Workstations as points of interest, so moradores can claim one each (like beds). */
public final class ModPois {
	public static final PoiType CEPO = PoiHelper.register(Arraial.id("cepo"), 1, 1, ModBlocks.CEPO);
	public static final PoiType BALAIO = PoiHelper.register(Arraial.id("balaio"), 1, 1, ModBlocks.BALAIO);
	public static final PoiType FORNO_OLARIA = PoiHelper.register(Arraial.id("forno_olaria"), 1, 1, ModBlocks.FORNO_OLARIA);

	public static boolean ePosto(Holder<PoiType> tipo) {
		PoiType t = tipo.value();
		return t == CEPO || t == BALAIO || t == FORNO_OLARIA;
	}

	public static void init() {
	}

	private ModPois() {
	}
}
