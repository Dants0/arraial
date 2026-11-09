package com.guilhermegoes.arraial;

import com.guilhermegoes.arraial.registry.ModBlockEntities;
import com.guilhermegoes.arraial.registry.ModComponents;
import com.guilhermegoes.arraial.registry.ModBlocks;
import com.guilhermegoes.arraial.registry.ModCreativeTabs;
import com.guilhermegoes.arraial.registry.ModEntities;
import com.guilhermegoes.arraial.registry.ModItems;
import com.guilhermegoes.arraial.registry.ModPois;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Arraial implements ModInitializer {
	public static final String MOD_ID = "arraial";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		com.guilhermegoes.arraial.config.ArraialConfig.carregar();
		ModComponents.init();
		ModBlocks.init();
		ModBlockEntities.init();
		ModPois.init();
		ModEntities.init();
		ModItems.init();
		ModCreativeTabs.init();
		com.guilhermegoes.arraial.npc.Ameacas.init();
		com.guilhermegoes.arraial.vila.ComandoArraial.init();
		com.guilhermegoes.arraial.trabalho.Mato.init();
		com.guilhermegoes.arraial.vila.Registro.init();
		com.guilhermegoes.arraial.vila.VilaNova.init();
		com.guilhermegoes.arraial.vila.Mundo.init();
		com.guilhermegoes.arraial.vila.Territorio.init();
		com.guilhermegoes.arraial.rede.Rede.init();
		com.guilhermegoes.arraial.vila.Bases.init();
		com.guilhermegoes.arraial.vila.Conquistas.init();
		LOGGER.info("Arraial: o pedreiro chegou.");
	}
}
