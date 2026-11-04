package com.guilhermegoes.arraial.registry;

import com.guilhermegoes.arraial.Arraial;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTabs {
	public static final CreativeModeTab ARRAIAL_TAB = Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB,
			Arraial.id("arraial"),
			CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
					.title(Component.translatable("itemGroup.arraial"))
					.icon(() -> new ItemStack(ModItems.LIVRO_DE_PROJETOS))
					.displayItems((params, output) -> {
						output.accept(ModBlocks.CANTEIRO);
						output.accept(ModItems.LIVRO_DE_PROJETOS);
						output.accept(ModItems.CONTRATO_PEDREIRO);
					})
					.build());

	public static void init() {
	}

	private ModCreativeTabs() {
	}
}
