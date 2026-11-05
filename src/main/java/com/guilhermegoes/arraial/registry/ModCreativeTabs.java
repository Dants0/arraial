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
						output.accept(ModBlocks.CRUZEIRO);
						output.accept(ModBlocks.CANTEIRO);
						output.accept(ModBlocks.ARMAZEM);
						output.accept(ModBlocks.CEPO);
						output.accept(ModBlocks.BALAIO);
						output.accept(ModBlocks.FORNO_OLARIA);
						output.accept(ModBlocks.POSTO_GUARDA);
						output.accept(ModBlocks.COCHO);
						output.accept(ModBlocks.GAMELA);
						output.accept(ModBlocks.BANCADA_CANTEIRO);
						output.accept(ModItems.LIVRO_DE_PROJETOS);
						output.accept(ModItems.CONTRATO_PEDREIRO);
						output.accept(ModBlocks.FORNO_FARINHA);
						output.accept(ModBlocks.BANCA_FEIRA);
						output.accept(ModBlocks.CANGALHA);
						output.accept(ModBlocks.BALCAO_BOTICA);
						output.accept(ModBlocks.LAMPIAO);
						output.accept(ModItems.MANIVA);
						output.accept(ModItems.MANDIOCA);
						output.accept(ModItems.FARINHA);
						output.accept(ModItems.BEIJU);
						output.accept(ModItems.PACOCA);
						output.accept(ModItems.RAPADURA);
						output.accept(ModItems.CUIA);
						output.accept(ModItems.CUIA_GARAPA);
						output.accept(ModItems.CHAPEU_COURO);
						output.accept(ModItems.REIS);
						output.accept(ModItems.REMEDIO);
						output.accept(ModBlocks.COVO);
						output.accept(ModBlocks.VARAL_CACA);
						output.accept(ModBlocks.PRANCHETA);
						output.accept(ModBlocks.ESCRIVANINHA);
						output.accept(ModItems.GUIA);
						output.accept(ModItems.QUENTAO);
						output.accept(ModItems.PE_DE_MOLEQUE);
						output.accept(ModBlocks.BANDEIRINHAS);
						output.accept(ModBlocks.URNA);
						output.accept(ModBlocks.MARCO_FRONTEIRA);
						output.accept(ModItems.FACAO);
						output.accept(ModItems.PEIXEIRA);
						output.accept(ModItems.FOICE);
						output.accept(ModItems.GIBAO);
						output.accept(ModItems.PERNEIRA);
						output.accept(ModItems.ALPERCATAS);
						output.accept(ModItems.CANTIL);
						output.accept(ModItems.CANTIL_CHEIO);
						output.accept(ModItems.SACO_SAQUE);
						output.accept(ModItems.COMENDA);
						output.accept(ModItems.MAPA_REGIAO);
						output.accept(ModBlocks.TAIPA);
						output.accept(ModBlocks.TELHA);
						output.accept(ModBlocks.TELHA_ESCADA);
						output.accept(ModBlocks.TELHA_LAJE);
						output.accept(ModBlocks.CAL);
						output.accept(ModBlocks.AZULEJO);
					})
					.build());

	public static void init() {
	}

	private ModCreativeTabs() {
	}
}
