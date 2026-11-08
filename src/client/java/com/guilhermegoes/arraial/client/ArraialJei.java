package com.guilhermegoes.arraial.client;

import com.guilhermegoes.arraial.Arraial;
import com.guilhermegoes.arraial.registry.ModBlocks;
import com.guilhermegoes.arraial.registry.ModItems;
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
	private static final ItemLike[] COM_INFO = {
			ModBlocks.CRUZEIRO, ModBlocks.ARMAZEM, ModBlocks.CANTEIRO, ModItems.LIVRO_DE_PROJETOS, ModItems.CONTRATO_PEDREIRO,
			ModBlocks.CEPO, ModBlocks.BALAIO, ModBlocks.FORNO_OLARIA, ModBlocks.GAMELA, ModBlocks.BANCADA_CANTEIRO, ModBlocks.COCHO,
			ModBlocks.POSTO_GUARDA, ModBlocks.FORNO_FARINHA, ModBlocks.BANCA_FEIRA, ModBlocks.CANGALHA, ModBlocks.BALCAO_BOTICA,
			ModBlocks.COVO, ModBlocks.VARAL_CACA, ModBlocks.PRANCHETA, ModBlocks.ESCRIVANINHA, ModBlocks.URNA, ModBlocks.MARCO_FRONTEIRA,
			ModBlocks.BANDEIRINHAS, ModBlocks.LAMPIAO, ModItems.REIS, ModItems.MANIVA, ModItems.MANDIOCA, ModItems.FARINHA,
			ModItems.REMEDIO, ModItems.GUIA, ModItems.QUENTAO, ModItems.CUIA_GARAPA, ModItems.CHAPEU_COURO,
			ModItems.FACAO, ModItems.PEIXEIRA, ModItems.FOICE, ModItems.GIBAO, ModItems.CANTIL, ModItems.SACO_SAQUE, ModItems.COMENDA,
			ModItems.MAPA_REGIAO, ModBlocks.TAIPA, ModBlocks.TELHA, ModBlocks.CAL, ModBlocks.AZULEJO,
			ModBlocks.MOENDA, ModBlocks.TERREIRO, ModBlocks.BANCADA_ENTALHE, ModItems.CAFE_CEREJA, ModItems.CAFEZINHO, ModItems.QUEIJO_COALHO,
			ModItems.CHARQUE, ModBlocks.PEDRA_SABAO, ModBlocks.TALHA_DOURADA, ModBlocks.ADOBE};

	@Override
	public Identifier getPluginUid() {
		return Arraial.id("jei");
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		for (ItemLike i : COM_INFO) {
			String id = BuiltInRegistries.ITEM.getKey(i.asItem()).getPath();
			registration.addIngredientInfo(i, Component.translatable("arraial.jei." + id));
		}
	}
}
