package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.vila.ArraialBlockEntity;
import com.guilhermegoes.arraial.vila.Feira;
import com.guilhermegoes.arraial.vila.Vilas;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/** The feirante keeps the stall by day and cries out the goods and their prices now and then. */
public class FeiranteGoal extends OficioGoal {
	private int pregao;

	public FeiranteGoal(MoradorEntity morador) {
		super(morador, Profissao.FEIRANTE);
	}

	@Override
	public boolean canUse() {
		return hora() && podeTrabalhar();
	}

	@Override
	public boolean canContinueToUse() {
		return podeTrabalhar();
	}

	@Override
	public void start() {
		pregao = 100 + morador.getRandom().nextInt(200);
	}

	@Override
	public void tick() {
		BlockPos banca = morador.posto();
		if (banca == null) {
			return;
		}
		morador.setStatus(Component.translatable("arraial.feirante.na_banca"));
		if (!perto(banca, 2.5)) {
			andarAte(banca);
			return;
		}
		morador.getNavigation().stop();
		morador.getLookControl().setLookAt(Vec3.atCenterOf(banca).add(0, 1, 0));
		if (--pregao > 0) {
			return;
		}
		pregao = 600 + morador.getRandom().nextInt(600);
		ArraialBlockEntity a = morador.arraial();
		if (a == null) {
			return;
		}
		// Cry out one of the goods in stock, with today's price.
		Map<Item, Integer> inv = Vilas.inventario(armazens());
		java.util.List<Feira.Mercadoria> tem = Feira.TABELA.stream().filter(m -> inv.getOrDefault(m.item(), 0) > 0).toList();
		if (tem.isEmpty()) {
			return;
		}
		Feira.Mercadoria m = tem.get(morador.getRandom().nextInt(tem.size()));
		String preco = String.format(java.util.Locale.ROOT, "%.1f", Feira.preco(a, m.item()));
		morador.dizer(Component.translatable("arraial.feirante.pregao." + morador.getRandom().nextInt(3),
				m.item().getDefaultInstance().getHoverName(), preco), 80);
		bater();
	}
}
