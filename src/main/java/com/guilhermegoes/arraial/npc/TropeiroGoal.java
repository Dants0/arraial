package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.vila.ArmazemBlockEntity;
import com.guilhermegoes.arraial.vila.ArraialBlockEntity;
import com.guilhermegoes.arraial.vila.Caravana;
import com.guilhermegoes.arraial.vila.Registro;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/**
 * The tropeiro on foot: loads the troop at the armazém, walks out of the arraial towards the
 * destination (then the trip itself is simulated, see {@link Caravana}), and when he is back
 * unloads what he bought at the armazém.
 */
public class TropeiroGoal extends OficioGoal {
	private int andando;

	public TropeiroGoal(MoradorEntity morador) {
		super(morador, Profissao.TROPEIRO);
	}

	@Override
	public boolean canUse() {
		int f = morador.viagemFase();
		return hora() && (f == 1 || f == 2 || f == 4) && podeTrabalhar() && morador.vila() != null;
	}

	@Override
	public boolean canContinueToUse() {
		int f = morador.viagemFase();
		return (f == 1 || f == 2 || f == 4) && podeTrabalhar();
	}

	@Override
	public void start() {
		andando = 0;
		morador.segurar(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEAD));
	}

	@Override
	public void tick() {
		ArraialBlockEntity a = morador.arraial();
		Registro.Info destino = Registro.get(morador.viagemDestino());
		String nome = destino == null ? "?" : destino.nome;
		switch (morador.viagemFase()) {
			case 1 -> {
				ArmazemBlockEntity arm = armazemMaisProximo();
				if (a == null || arm == null) {
					morador.setViagem(0, "", 0);
					return;
				}
				morador.setStatus(Component.translatable("arraial.tropeiro.carregando", nome));
				if (!perto(arm.getBlockPos(), 2.5)) {
					andarAte(arm.getBlockPos());
					return;
				}
				int pop = a.moradores(level()).size();
				for (Map.Entry<Item, Integer> e : Caravana.carga(level(), a, pop).entrySet()) {
					pegar(e.getKey(), e.getValue());
				}
				bater();
				if (morador.cargaCopia().isEmpty() && a.tesouro() < 10) {
					morador.setViagem(0, "", 0); // nothing to sell and nothing to buy with
					return;
				}
				morador.setViagem(2, morador.viagemDestino(), 0);
			}
			case 2 -> {
				if (morador.vila() == null) {
					return;
				}
				BlockPos borda = Caravana.borda(level(), morador.vila(), morador.viagemDestino());
				morador.setStatus(Component.translatable("arraial.tropeiro.partindo", nome));
				if (perto(borda, 4.0) || ++andando > 1200) {
					Caravana.partir(level(), morador);
					return;
				}
				andarAte(borda);
			}
			case 4 -> {
				ArmazemBlockEntity arm = armazemMaisProximo();
				morador.setStatus(Component.translatable("arraial.tropeiro.chegando", nome));
				if (arm == null) {
					morador.setViagem(0, "", 0);
					return;
				}
				if (!perto(arm.getBlockPos(), 2.5)) {
					andarAte(arm.getBlockPos());
					return;
				}
				entregar(Map.of());
				morador.setViagem(0, "", 0);
			}
			default -> {
			}
		}
	}
}
