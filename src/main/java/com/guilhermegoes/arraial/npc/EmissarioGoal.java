package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.vila.Caravana;
import com.guilhermegoes.arraial.vila.Diplomacia;
import com.guilhermegoes.arraial.vila.Registro;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Relações públicas on foot: takes a small gift from the armazém, walks out of the arraial
 * towards the neighbour (the visit itself is simulated, see {@link Diplomacia}), and back home
 * reports at the Cruzeiro.
 */
public class EmissarioGoal extends OficioGoal {
	private int andando;
	private boolean presente;

	public EmissarioGoal(MoradorEntity morador) {
		super(morador, Profissao.RELACOES);
	}

	@Override
	public boolean canUse() {
		int f = morador.viagemFase();
		return hora() && (f == 2 || f == 4) && podeTrabalhar() && morador.vila() != null;
	}

	@Override
	public boolean canContinueToUse() {
		int f = morador.viagemFase();
		return (f == 2 || f == 4) && podeTrabalhar();
	}

	@Override
	public void start() {
		andando = 0;
		if (morador.viagemFase() == 2 && !presente) {
			Diplomacia.presente(level(), morador);
			presente = true;
		}
		morador.segurar(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITABLE_BOOK));
	}

	@Override
	public void tick() {
		Registro.Info destino = Registro.get(morador.viagemDestino());
		String nome = destino == null ? "?" : destino.nome;
		BlockPos vila = morador.vila();
		if (vila == null) {
			return;
		}
		if (morador.viagemFase() == 2) {
			BlockPos borda = Caravana.borda(level(), vila, morador.viagemDestino());
			morador.setStatus(Component.translatable("arraial.relacoes.partindo", nome));
			if (perto(borda, 4.0) || ++andando > 1200) {
				presente = false;
				Caravana.partir(level(), morador);
				return;
			}
			andarAte(borda);
			return;
		}
		morador.setStatus(Component.translatable("arraial.relacoes.voltando", nome));
		if (!perto(vila, 4.0)) {
			andarAte(vila);
			return;
		}
		morador.dizer(Component.translatable("arraial.fala.relacoes." + morador.getRandom().nextInt(2), nome), 80);
		morador.setViagem(0, "", 0);
	}
}
