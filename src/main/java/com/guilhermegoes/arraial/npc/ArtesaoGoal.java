package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.trabalho.Demanda;
import com.guilhermegoes.arraial.trabalho.Receitas;
import com.guilhermegoes.arraial.vila.ArmazemBlockEntity;
import com.guilhermegoes.arraial.vila.Vilas;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Crafting on demand at the workstation: when the arraial is short of something this trade
 * can make (doors for a house, bricks for a roof, bread for the table) and the armazém has
 * the ingredients, the artisan fetches them, works at the posto, and brings the goods back.
 */
public class ArtesaoGoal extends OficioGoal {
	private enum Fase { BUSCAR, TRABALHAR, ENTREGAR }

	private static final int MAX_LOTES = 4;
	private static final int TICKS_POR_LOTE = 50;

	private Fase fase = Fase.BUSCAR;
	private Receitas.@Nullable Receita receita;
	private int lotes;
	private int trabalho;
	/** Ticks per batch this time (the monjolo halves the farinheiro's). */
	private int ticksPorLote = TICKS_POR_LOTE;

	public ArtesaoGoal(MoradorEntity morador) {
		super(morador, Profissao.NENHUMA);
	}

	@Override
	protected boolean podeTrabalhar() {
		Profissao p = morador.profissao();
		return p.posto() != null && !morador.comFome() && !morador.doente() && !morador.viajando() && morador.posto() != null && level().isBrightOutside();
	}

	@Override
	public boolean canUse() {
		if (!hora() || !podeTrabalhar() || morador.vila() == null) {
			return false;
		}
		List<ArmazemBlockEntity> armazens = armazens();
		if (armazens.isEmpty()) {
			return false;
		}
		// Most urgent shortage first; ingredients claimed by construction sites stay in the armazém.
		Demanda.Resultado demanda = Demanda.calcular(level(), morador.vila());
		List<Receitas.Receita> sabe = Receitas.de(morador.profissao());
		for (Map.Entry<Item, Integer> pedido : demanda.pedidos()) {
			for (Receitas.Receita r : sabe) {
				if (r.saida() != pedido.getKey()) {
					continue;
				}
				int possivel = MAX_LOTES;
				for (Map.Entry<Item, Integer> e : r.entrada().entrySet()) {
					possivel = Math.min(possivel, demanda.livre(e.getKey()) / e.getValue());
				}
				int necessario = (pedido.getValue() + r.quantidade() - 1) / r.quantidade();
				if (possivel > 0) {
					receita = r;
					lotes = Math.min(possivel, necessario);
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return receita != null && podeTrabalhar();
	}

	@Override
	public void start() {
		fase = Fase.BUSCAR;
		trabalho = 0;
		ticksPorLote = morador.profissao() == Profissao.FARINHEIRO && morador.vila() != null
				&& Vilas.obraPronta(level(), morador.vila(), "monjolo") != null ? TICKS_POR_LOTE / 2 : TICKS_POR_LOTE;
	}

	@Override
	public void stop() {
		super.stop();
		receita = null;
	}

	@Override
	public void tick() {
		if (receita == null) {
			return;
		}
		Component nome = receita.saida().getDefaultInstance().getHoverName();
		switch (fase) {
			case BUSCAR -> {
				ArmazemBlockEntity a = armazemMaisProximo();
				if (a == null) {
					receita = null;
					return;
				}
				morador.setStatus(Component.translatable("arraial.oficio.buscando", nome));
				if (!perto(a.getBlockPos(), 2.5)) {
					andarAte(a.getBlockPos());
					return;
				}
				// Take the ingredients for the batches; give up if someone else took them first.
				for (Map.Entry<Item, Integer> e : receita.entrada().entrySet()) {
					int quer = e.getValue() * lotes;
					int pegou = Vilas.retirar(armazens(), e.getKey(), quer);
					morador.guardar(e.getKey(), pegou);
				}
				for (Map.Entry<Item, Integer> e : receita.entrada().entrySet()) {
					lotes = Math.min(lotes, morador.carregando(e.getKey()) / e.getValue());
				}
				bater();
				fase = lotes > 0 ? Fase.TRABALHAR : Fase.ENTREGAR;
			}
			case TRABALHAR -> {
				BlockPos posto = morador.posto();
				if (posto == null) {
					fase = Fase.ENTREGAR;
					return;
				}
				morador.setStatus(Component.translatable("arraial.oficio.fazendo", nome));
				if (!perto(posto, 2.5)) {
					andarAte(posto);
					return;
				}
				morador.getNavigation().stop();
				morador.getLookControl().setLookAt(Vec3.atCenterOf(posto));
				morador.segurar(new ItemStack(receita.entrada().keySet().iterator().next()));
				if (++trabalho % 10 == 0) {
					bater();
					level().playSound(null, posto, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.6F, 1.0F);
				}
				if (trabalho >= ticksPorLote * lotes) {
					for (Map.Entry<Item, Integer> e : receita.entrada().entrySet()) {
						morador.gastar(e.getKey(), e.getValue() * lotes);
					}
					morador.guardar(receita.saida(), receita.quantidade() * lotes);
					morador.segurar(new ItemStack(receita.saida()));
					fase = Fase.ENTREGAR;
				}
			}
			case ENTREGAR -> {
				ArmazemBlockEntity a = armazemMaisProximo();
				if (a == null) {
					receita = null;
					return;
				}
				morador.setStatus(Component.translatable("arraial.oficio.entregando", nome));
				if (!perto(a.getBlockPos(), 2.5)) {
					andarAte(a.getBlockPos());
					return;
				}
				entregar(Map.of());
				receita = null; // done; canUse decides what's next
			}
		}
	}
}
