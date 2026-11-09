package com.guilhermegoes.arraial.client;

import com.guilhermegoes.arraial.Arraial;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

/**
 * JEI integration (optional: only loaded when JEI is installed). Crafting recipes are plain data
 * packs, so JEI already shows them; this adds information pages for what is not crafted (crops,
 * money, loot) and for each workstation: which trade it gives a morador and what that trade does.
 * Texts live in the lang files under "arraial.jei.<item>".
 */
@JeiPlugin
public class ArraialJei implements IModPlugin {
	@Override
	public Identifier getPluginUid() {
		return Arraial.id("jei");
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		for (ItemLike i : InfoItens.COM_INFO) {
			String id = BuiltInRegistries.ITEM.getKey(i.asItem()).getPath();
			registration.addIngredientInfo(i, Component.translatable("arraial.jei." + id));
		}
	}
}
