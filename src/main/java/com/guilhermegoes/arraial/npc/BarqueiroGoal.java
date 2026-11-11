package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.vila.ArmazemBlockEntity;
import com.guilhermegoes.arraial.vila.ArraialBlockEntity;
import com.guilhermegoes.arraial.vila.Caravana;
import com.guilhermegoes.arraial.vila.Registro;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The barqueiro: the tropeiro of the river. Loads the canoe at the armazém, walks down to the
 * trapiche, paddles off (a real boat, for a few seconds) and the rest of the trip is simulated
 * like the troop's (see {@link Caravana}): bigger loads, faster, no robbers on the water. Back
 * home he steps off at the trapiche and unloads at the armazém.
 */
public class BarqueiroGoal extends OficioGoal {
	/** Ticks paddling in sight before the trip goes "off screen". */
	private static final int REMANDO = 80;

	private int andando;
	private int remando;
	private @Nullable Entity canoa;

	public BarqueiroGoal(MoradorEntity morador) {
		super(morador, Profissao.BARQUEIRO);
	}

	@Override
	public boolean canUse() {
		int f = morador.viagemFase();
		return hora() && (f == 1 || f == 2 || f == 4) && podeTrabalhar() && morador.vila() != null;
	}

	@Override
	public boolean canContinueToUse() {
		int f = morador.viagemFase();
		return (f == 1 || f == 2 || f == 4) && (podeTrabalhar() || canoa != null);
	}

	@Override
	public void start() {
		andando = 0;
		remando = 0;
		morador.segurar(new ItemStack(Items.OAK_BOAT));
	}

	@Override
	public void stop() {
		super.stop();
		desembarcar();
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
				morador.setStatus(Component.translatable("arraial.barqueiro.carregando", nome));
				if (!perto(arm.getBlockPos(), 2.5)) {
					andarAte(arm.getBlockPos());
					return;
				}
				int pop = a.moradores(level()).size();
				for (Map.Entry<Item, Integer> e : Caravana.carga(level(), a, pop, Caravana.CARGA_CANOA).entrySet()) {
					pegar(e.getKey(), e.getValue());
				}
				bater();
				if (morador.cargaCopia().isEmpty() && a.tesouro() < 10) {
					morador.setViagem(0, "", 0);
					return;
				}
				morador.setViagem(2, morador.viagemDestino(), 0);
			}
			case 2 -> {
				if (canoa != null) {
					remar();
					return;
				}
				BlockPos amarra = morador.posto();
				if (amarra == null) {
					morador.setViagem(0, "", 0);
					return;
				}
				morador.setStatus(Component.translatable("arraial.barqueiro.partindo", nome));
				BlockPos agua = Caravana.aguaPerto(level(), amarra, 10);
				if (agua == null || ++andando > 1200) {
					Caravana.partir(level(), morador); // no water in sight: the canoe leaves "off screen"
					return;
				}
				if (!perto(agua, 3.5)) {
					andarAte(agua);
					return;
				}
				embarcar(agua);
			}
			case 4 -> {
				ArmazemBlockEntity arm = armazemMaisProximo();
				morador.setStatus(Component.translatable("arraial.barqueiro.chegando", nome));
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

	/** Puts a canoe on the water and the barqueiro in it. */
	private void embarcar(BlockPos agua) {
		Entity barco = EntityTypes.OAK_BOAT.create(level(), EntitySpawnReason.EVENT);
		if (barco == null) {
			Caravana.partir(level(), morador);
			return;
		}
		barco.snapTo(agua.getX() + 0.5, agua.getY() + 0.9, agua.getZ() + 0.5, morador.getYRot(), 0);
		level().addFreshEntity(barco);
		morador.getNavigation().stop();
		morador.startRiding(barco, true, true);
		canoa = barco;
		remando = 0;
		level().playSound(null, agua, SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 1.0F, 1.0F);
	}

	/** A few seconds of paddling away from the trapiche, then the trip is on. */
	private void remar() {
		Entity barco = canoa;
		if (barco == null || !barco.isAlive()) {
			desembarcar();
			Caravana.partir(level(), morador);
			return;
		}
		BlockPos amarra = morador.posto();
		Vec3 longe = amarra == null ? barco.getLookAngle() : barco.position().subtract(Vec3.atCenterOf(amarra)).multiply(1, 0, 1);
		if (longe.lengthSqr() < 0.01) {
			longe = barco.getLookAngle();
		}
		Vec3 v = longe.normalize().scale(0.18);
		if (level().getFluidState(barco.blockPosition()).is(FluidTags.WATER) || level().getFluidState(barco.blockPosition().below()).is(FluidTags.WATER)) {
			barco.setDeltaMovement(v.x, barco.getDeltaMovement().y, v.z);
			barco.setYRot((float) (Math.atan2(-v.x, v.z) * 180 / Math.PI));
		}
		if (remando % 10 == 0) {
			level().sendParticles(ParticleTypes.SPLASH, barco.getX(), barco.getY() + 0.3, barco.getZ(), 6, 0.6, 0.0, 0.6, 0.0);
			level().playSound(null, barco.blockPosition(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 0.6F, 1.0F);
		}
		if (++remando >= REMANDO) {
			desembarcar();
			Caravana.partir(level(), morador);
		}
	}

	private void desembarcar() {
		if (canoa != null) {
			if (morador.isPassenger()) {
				morador.stopRiding();
			}
			canoa.discard();
			canoa = null;
		}
	}
}
