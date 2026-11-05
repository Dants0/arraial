package com.guilhermegoes.arraial.vila;

import com.guilhermegoes.arraial.obra.CanteiroBlockEntity;
import com.guilhermegoes.arraial.obra.PlanoDeObra;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Every morning the prefeito walks past the finished buildings. Where blocks are gone (a fire, a
 * creeper, bandits) the Marco reopens from the first missing block and the pedreiros rebuild it.
 * Only holes count: blocks the player swapped for something else are left alone.
 */
public final class Reparos {
	static void verificar(ServerLevel level, ArraialBlockEntity arraial) {
		BlockPos vila = arraial.getBlockPos();
		for (BlockPos p : List.copyOf(CanteiroBlockEntity.ativos(level))) {
			if (!p.closerThan(vila, Vilas.RAIO) || !level.isLoaded(p) || !(level.getBlockEntity(p) instanceof CanteiroBlockEntity c)
					|| !c.concluida()) {
				continue;
			}
			PlanoDeObra plano = c.plano(level).orElse(null);
			if (plano == null) {
				continue;
			}
			List<PlanoDeObra.Etapa> etapas = plano.etapas();
			for (int i = 0; i < etapas.size(); i++) {
				PlanoDeObra.Etapa e = etapas.get(i);
				if (!e.limpeza() && PlanoDeObra.custo(e.state()) != null && level.getBlockState(e.pos()).isAir()) {
					c.reabrir(i);
					Cronica.anotar(level, vila, Component.translatable("arraial.cronica.reparo", c.projeto().get().nome()));
					break;
				}
			}
		}
	}

	private Reparos() {
	}
}
