package com.guilhermegoes.arraial.client;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * Roughly Enough Items: the same information pages as the JEI plugin (what each block and item of
 * the arraial is for). Recipes need nothing here: REI reads the game's recipes by itself.
 */
public class ArraialRei implements REIClientPlugin {
	@Override
	public void registerDisplays(DisplayRegistry registry) {
		for (ItemLike i : InfoItens.COM_INFO) {
			ItemStack pilha = new ItemStack(i);
			String id = BuiltInRegistries.ITEM.getKey(pilha.getItem()).getPath();
			registry.add(DefaultInformationDisplay.createFromEntry(EntryStacks.of(pilha), pilha.getHoverName())
					.line(Component.translatable("arraial.jei." + id)));
		}
	}
}
