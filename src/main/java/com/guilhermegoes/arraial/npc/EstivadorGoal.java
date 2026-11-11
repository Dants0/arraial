package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.vila.ArmazemBlockEntity;
import com.guilhermegoes.arraial.vila.ArraialBlockEntity;
import com.guilhermegoes.arraial.vila.Navio;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The estivador: while a ship is in port, he carries lots between the armazém and the guindaste.
 * At the armazém he picks up the surplus the ship buys; at the guindaste it goes aboard (the réis go
 * to the treasury) and the salt, iron and cloth the arraial bought come down; back at the armazém
 * he stores them. A few round trips per visit (see {@link Navio#LOTES}).
 */
public class EstivadorGoal extends OficioGoal {
	/** false: going to the armazém to load; true: carrying the lot to the guindaste. */
	private boolean levando;

	public EstivadorGoal(MoradorEntity morador) {
		super(morador, Profissao.ESTIVADOR);
	}

	@Override
	public boolean canUse() {
		if (!hora() || !podeTrabalhar()) {
			return false;
		}
		ArraialBlockEntity a = morador.arraial();
		return a != null && Navio.atracado(a) && (Navio.lotes(a) < Navio.LOTES || morador.cargaTotal() > 0);
	}

	@Override
	public boolean canContinueToUse() {
		ArraialBlockEntity a = morador.arraial();
		return podeTrabalhar() && a != null && (Navio.atracado(a) && Navio.lotes(a) < Navio.LOTES || morador.cargaTotal() > 0);
	}

	@Override
	public void start() {
		levando = morador.cargaTotal() > 0 && !temImportados();
		morador.segurar(new ItemStack(Items.BARREL));
	}

	@Override
	public void tick() {
		ArraialBlockEntity a = morador.arraial();
		BlockPos guindaste = morador.posto();
		ArmazemBlockEntity arm = armazemMaisProximo();
		if (a == null || guindaste == null || arm == null) {
			return;
		}
		if (!levando) {
			morador.setStatus(Component.translatable("arraial.estivador.carregando"));
			if (!perto(arm.getBlockPos(), 2.5)) {
				andarAte(arm.getBlockPos());
				return;
			}
			// Store what came off the ship, then pick up the next lot.
			if (morador.cargaTotal() > 0) {
				entregar(Map.of());
			}
			if (!Navio.atracado(a) || Navio.lotes(a) >= Navio.LOTES) {
				return;
			}
			int pop = a.moradores(level()).size();
			for (Map.Entry<Item, Integer> e : Navio.lote(level(), a, pop).entrySet()) {
				pegar(e.getKey(), e.getValue());
			}
			bater();
			levando = true;
			return;
		}
		morador.setStatus(Component.translatable("arraial.estivador.embarcando"));
		if (!perto(guindaste, 3.0)) {
			andarAte(guindaste);
			return;
		}
		// At the guindaste: the lot goes up, the ship's goods come down.
		Map<Item, Integer> levou = morador.cargaCopia();
		Navio.embarcar(level(), a, levou);
		levou.forEach(morador::gastar);
		Navio.desembarcar(level(), a).forEach(morador::guardar);
		bater();
		level().playSound(null, guindaste, SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1.0F, 0.8F);
		levando = false;
	}

	private boolean temImportados() {
		return morador.cargaCopia().keySet().stream().anyMatch(Navio.IMPORTA::contains);
	}
}
