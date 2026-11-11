package com.guilhermegoes.arraial.client;

import com.guilhermegoes.arraial.registry.ModBlocks;
import com.guilhermegoes.arraial.registry.ModItems;
import net.minecraft.world.level.ItemLike;

/**
 * Blocks and items with an information page in JEI and REI (text: "arraial.jei.<item>"). Kept apart
 * from both plugins so that neither loads the other mod's classes.
 */
final class InfoItens {
	static final ItemLike[] COM_INFO = {
			ModBlocks.CRUZEIRO, ModBlocks.ARMAZEM, ModBlocks.CANTEIRO, ModItems.LIVRO_DE_PROJETOS, ModItems.CONTRATO_PEDREIRO,
			ModBlocks.CEPO, ModBlocks.BALAIO, ModBlocks.FORNO_OLARIA, ModBlocks.GAMELA, ModBlocks.BANCADA_CANTEIRO, ModBlocks.COCHO,
			ModBlocks.POSTO_GUARDA, ModBlocks.FORNO_FARINHA, ModBlocks.BANCA_FEIRA, ModBlocks.CANGALHA, ModBlocks.BALCAO_BOTICA,
			ModBlocks.COVO, ModBlocks.VARAL_CACA, ModBlocks.PRANCHETA, ModBlocks.ESCRIVANINHA, ModBlocks.URNA, ModBlocks.MARCO_FRONTEIRA,
			ModBlocks.BANDEIRINHAS, ModBlocks.LAMPIAO, ModItems.REIS, ModItems.MANIVA, ModItems.MANDIOCA, ModItems.FARINHA,
			ModItems.REMEDIO, ModItems.GUIA, ModItems.QUENTAO, ModItems.CUIA_GARAPA, ModItems.CHAPEU_COURO,
			ModItems.FACAO, ModItems.PEIXEIRA, ModItems.FOICE, ModItems.GIBAO, ModItems.CANTIL, ModItems.SACO_SAQUE, ModItems.COMENDA,
			ModItems.MAPA_REGIAO, ModBlocks.TAIPA, ModBlocks.TELHA, ModBlocks.CAL, ModBlocks.AZULEJO,
			ModBlocks.MOENDA, ModBlocks.TERREIRO, ModBlocks.BANCADA_ENTALHE, ModBlocks.AMARRA, ModBlocks.GUINDASTE, ModItems.SAL, ModItems.CHITA, ModBlocks.MO, ModBlocks.RODA_DAGUA, ModBlocks.MONJOLO, ModItems.FARINHA_TRIGO, ModBlocks.BILHETERIA, ModItems.CAFE_CEREJA, ModItems.CAFEZINHO, ModItems.QUEIJO_COALHO,
			ModItems.CHARQUE, ModBlocks.PEDRA_SABAO, ModBlocks.TALHA_DOURADA, ModBlocks.ADOBE,
			ModItems.CAPIVARA_SPAWN_EGG, ModItems.TAMANDUA_SPAWN_EGG, ModItems.JACARE_SPAWN_EGG, ModItems.ONCA_SPAWN_EGG, ModItems.ARARA_SPAWN_EGG};

	private InfoItens() {
	}
}
