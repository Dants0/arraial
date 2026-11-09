package com.guilhermegoes.arraial.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu: the "Configure" button opens the Arraial settings. */
public class ArraialModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return TelaConfig::new;
	}
}
