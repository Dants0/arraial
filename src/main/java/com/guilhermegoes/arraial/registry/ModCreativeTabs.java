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
						output.accept(ModItems.CAPIVARA_SPAWN_EGG);
						output.accept(ModItems.TAMANDUA_SPAWN_EGG);
						output.accept(ModItems.JACARE_SPAWN_EGG);
						output.accept(ModItems.ONCA_SPAWN_EGG);
						output.accept(ModItems.ARARA_SPAWN_EGG);
						output.accept(ModBlocks.TAIPA);
						output.accept(ModBlocks.TELHA);
						output.accept(ModBlocks.TELHA_ESCADA);
						output.accept(ModBlocks.TELHA_LAJE);
						output.accept(ModBlocks.CAL);
						output.accept(ModBlocks.AZULEJO);
						output.accept(ModBlocks.MOENDA);
						output.accept(ModBlocks.TERREIRO);
						output.accept(ModBlocks.BANCADA_ENTALHE);
						output.accept(ModBlocks.AMARRA);
						output.accept(ModBlocks.GUINDASTE);
						output.accept(ModBlocks.MO);
						output.accept(ModBlocks.RODA_DAGUA);
						output.accept(ModBlocks.MONJOLO);
						output.accept(ModItems.CAFE_CEREJA);
						output.accept(ModItems.CAFE_TORRADO);
						output.accept(ModItems.CAFEZINHO);
						output.accept(ModItems.QUEIJO_COALHO);
						output.accept(ModItems.CHARQUE);
						output.accept(ModItems.SAL);
						output.accept(ModItems.CHITA);
						output.accept(ModItems.FARINHA_TRIGO);
						if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("farmersdelight")) {
							output.accept(ModItems.BAIAO_DE_DOIS);
							output.accept(ModItems.MOQUECA);
							output.accept(ModItems.VACA_ATOLADA);
						}
						output.accept(ModBlocks.PEDRA_SABAO);
						output.accept(ModBlocks.TALHA_DOURADA);
						output.accept(ModBlocks.ADOBE);
					})
					.build());

	public static void init() {
	}

	private ModCreativeTabs() {
	}
}
