package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.registry.ModItems;
import com.guilhermegoes.arraial.vila.ArmazemBlockEntity;
import com.guilhermegoes.arraial.vila.Cronica;
import com.guilhermegoes.arraial.vila.Vilas;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The boticário: treats the sick with a garrafada from the armazém (made at his counter from
 * herbs, see the recipes), and when herbs run low walks around the arraial picking wild flowers
 * for the next batch.
 */
public class BoticarioGoal extends OficioGoal {
	private enum Fase { CURAR, ERVAS, GUARDAR }

	private static final int RAIO_ERVAS = 20;
	private static final int ERVAS_NA_MAO = 8;

	private Fase fase = Fase.CURAR;
	private @Nullable MoradorEntity paciente;
	private @Nullable BlockPos erva;

	public BoticarioGoal(MoradorEntity morador) {
		super(morador, Profissao.BOTICARIO);
	}

	@Override
	public boolean canUse() {
		if (!hora() || !podeTrabalhar() || morador.vila() == null) {
			return false;
		}
		int remedios = Vilas.contar(armazens(), ModItems.REMEDIO) + morador.carregando(ModItems.REMEDIO);
		if (remedios > 0) {
			paciente = level().getEntitiesOfClass(MoradorEntity.class, morador.getBoundingBox().inflate(Vilas.RAIO),
					m -> m.doente() && morador.vila().equals(m.vila())).stream().findFirst().orElse(null);
			if (paciente != null) {
				fase = Fase.CURAR;
				return true;
			}
		}
		if (ervas() >= ERVAS_NA_MAO) {
			fase = Fase.GUARDAR;
			return true;
		}
		if (Vilas.contar(armazens(), s -> s.is(net.minecraft.tags.BlockItemTags.SMALL_FLOWERS.item())) < 6) {
			erva = procurarErva();
			if (erva != null) {
				fase = Fase.ERVAS;
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return podeTrabalhar() && (fase == Fase.GUARDAR || (fase == Fase.CURAR && paciente != null && paciente.doente())
				|| (fase == Fase.ERVAS && erva != null));
	}

	@Override
	public void stop() {
		super.stop();
		paciente = null;
		erva = null;
	}

	private int ervas() {
		int n = 0;
		for (var e : morador.cargaCopia().entrySet()) {
			if (e.getKey().getDefaultInstance().is(net.minecraft.tags.BlockItemTags.SMALL_FLOWERS.item())) {
				n += e.getValue();
			}
		}
		return n;
	}

	private @Nullable BlockPos procurarErva() {
		BlockPos centro = morador.posto() != null ? morador.posto() : morador.blockPosition();
		BlockPos melhor = null;
		double d = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(centro.offset(-RAIO_ERVAS, -4, -RAIO_ERVAS), centro.offset(RAIO_ERVAS, 4, RAIO_ERVAS))) {
			BlockState s = level().getBlockState(p);
			if (s.is(BlockTags.SMALL_FLOWERS)) {
				double x = p.distSqr(morador.blockPosition());
				if (x < d) {
					d = x;
					melhor = p.immutable();
				}
			}
		}
		return melhor;
	}

	@Override
	public void tick() {
		switch (fase) {
			case CURAR -> curar();
			case ERVAS -> colher();
			case GUARDAR -> {
				ArmazemBlockEntity a = armazemMaisProximo();
				morador.setStatus(Component.translatable("arraial.boticario.guardando"));
				if (a == null) {
					fase = Fase.CURAR;
					paciente = null;
					return;
				}
				if (!perto(a.getBlockPos(), 2.5)) {
					andarAte(a.getBlockPos());
					return;
				}
				entregar(Map.of());
				paciente = null;
				fase = Fase.CURAR;
			}
		}
	}

	private void curar() {
		if (paciente == null) {
			return;
		}
		if (morador.carregando(ModItems.REMEDIO) == 0) {
			ArmazemBlockEntity a = armazemMaisProximo();
			if (a == null) {
				paciente = null;
				return;
			}
			morador.setStatus(Component.translatable("arraial.boticario.buscando"));
			if (!perto(a.getBlockPos(), 2.5)) {
				andarAte(a.getBlockPos());
				return;
			}
			if (pegar(ModItems.REMEDIO, 3) == 0) {
				paciente = null;
				return;
			}
		}
		morador.segurar(new ItemStack(ModItems.REMEDIO));
		morador.setStatus(Component.translatable("arraial.boticario.tratando", paciente.nome()));
		if (morador.distanceTo(paciente) > 2.5) {
			andarAte(paciente.blockPosition());
			return;
		}
		morador.getLookControl().setLookAt(paciente);
		morador.gastar(ModItems.REMEDIO, 1);
		paciente.curar();
		bater();
		level().playSound(null, paciente.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.NEUTRAL, 0.8F, 1.0F);
		Cronica.anotar(level(), morador.vila(), Component.translatable("arraial.cronica.curou", morador.nome(), paciente.nome()));
		paciente.dizer(Component.translatable("arraial.fala.curado." + morador.getRandom().nextInt(2), morador.nome()), 80);
		paciente = null;
	}

	private void colher() {
		if (erva == null) {
			return;
		}
		morador.setStatus(Component.translatable("arraial.boticario.ervas"));
		if (!perto(erva, 2.0)) {
			andarAte(erva);
			return;
		}
		BlockState s = level().getBlockState(erva);
		if (s.is(BlockTags.SMALL_FLOWERS)) {
			morador.guardar(s.getBlock().asItem(), 1);
			level().setBlock(erva, Blocks.AIR.defaultBlockState(), 3);
			bater();
		}
		erva = ervas() >= ERVAS_NA_MAO ? null : procurarErva();
		if (erva == null) {
			fase = Fase.GUARDAR;
			erva = null;
		}
	}
}
