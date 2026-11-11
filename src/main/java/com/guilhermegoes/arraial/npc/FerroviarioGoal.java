package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.vila.ArmazemBlockEntity;
import com.guilhermegoes.arraial.vila.ArraialBlockEntity;
import com.guilhermegoes.arraial.vila.Caravana;
import com.guilhermegoes.arraial.vila.Registro;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The ferroviário: the railway's tropeiro (Republican era). He loads the wagons at the armazém,
 * walks to the bilheteria, and the train pulls out along the station's track with him on the
 * locomotive (real minecarts, for a few seconds); the rest of the trip is simulated like the
 * troop's (see {@link Caravana}): the biggest loads, the quickest trips, no robbers. Back home he
 * steps off at the station and unloads at the armazém.
 */
public class FerroviarioGoal extends OficioGoal {
	/** Ticks the train runs in sight before the trip goes "off screen". */
	private static final int APITO = 70;

	private int andando;
	private int correndo;
	private final List<Entity> trem = new ArrayList<>();
	private Vec3 rumo = Vec3.ZERO;

	public FerroviarioGoal(MoradorEntity morador) {
		super(morador, Profissao.FERROVIARIO);
	}

	@Override
	public boolean canUse() {
		int f = morador.viagemFase();
		return hora() && (f == 1 || f == 2 || f == 4) && podeTrabalhar() && morador.vila() != null;
	}

	@Override
	public boolean canContinueToUse() {
		int f = morador.viagemFase();
		return (f == 1 || f == 2 || f == 4) && (podeTrabalhar() || !trem.isEmpty());
	}

	@Override
	public void start() {
		andando = 0;
		correndo = 0;
		morador.segurar(new ItemStack(Items.PAPER));
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
				morador.setStatus(Component.translatable("arraial.ferroviario.carregando", nome));
				if (!perto(arm.getBlockPos(), 2.5)) {
					andarAte(arm.getBlockPos());
					return;
				}
				int pop = a.moradores(level()).size();
				for (Map.Entry<Item, Integer> e : Caravana.carga(level(), a, pop, Caravana.CARGA_TREM).entrySet()) {
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
				if (!trem.isEmpty()) {
					correr();
					return;
				}
				BlockPos bilheteria = morador.posto();
				if (bilheteria == null) {
					morador.setViagem(0, "", 0);
					return;
				}
				morador.setStatus(Component.translatable("arraial.ferroviario.partindo", nome));
				if (!perto(bilheteria, 2.5) && ++andando < 1200) {
					andarAte(bilheteria);
					return;
				}
				if (!embarcar(bilheteria)) {
					Caravana.partir(level(), morador); // no track in sight: the train leaves "off screen"
				}
			}
			case 4 -> {
				ArmazemBlockEntity arm = armazemMaisProximo();
				morador.setStatus(Component.translatable("arraial.ferroviario.chegando", nome));
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

	/** The station's track: the rails within reach of the bilheteria, ordered along their line. */
	private List<BlockPos> trilhos(BlockPos bilheteria) {
		List<BlockPos> r = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(bilheteria.offset(-14, -2, -14), bilheteria.offset(14, 2, 14))) {
			if (level().getBlockState(p).is(BlockTags.RAILS)) {
				r.add(p.immutable());
			}
		}
		if (r.size() < 6) {
			return List.of();
		}
		int dx = r.stream().mapToInt(BlockPos::getX).max().orElse(0) - r.stream().mapToInt(BlockPos::getX).min().orElse(0);
		int dz = r.stream().mapToInt(BlockPos::getZ).max().orElse(0) - r.stream().mapToInt(BlockPos::getZ).min().orElse(0);
		r.sort(java.util.Comparator.comparingInt(p -> dx >= dz ? p.getX() : p.getZ()));
		return r;
	}

	/** The train at the near end of the track: a locomotive and two wagons, the ferroviário aboard. */
	private boolean embarcar(BlockPos bilheteria) {
		List<BlockPos> linha = trilhos(bilheteria);
		if (linha.isEmpty()) {
			return false;
		}
		BlockPos inicio = linha.get(1), fim = linha.get(linha.size() - 1);
		rumo = Vec3.atLowerCornerOf(fim.subtract(inicio)).normalize();
		List<EntityType<?>> composicao = List.of(EntityTypes.FURNACE_MINECART, EntityTypes.CHEST_MINECART, EntityTypes.CHEST_MINECART);
		for (int i = 0; i < composicao.size(); i++) {
			Entity carro = composicao.get(i).create(level(), EntitySpawnReason.EVENT);
			if (carro == null) {
				desembarcar();
				return false;
			}
			Vec3 p = Vec3.atBottomCenterOf(inicio).subtract(rumo.scale(i * 1.3)).add(0, 0.1, 0);
			carro.snapTo(p.x, p.y, p.z, (float) (Math.atan2(-rumo.x, rumo.z) * 180 / Math.PI), 0);
			carro.setPermanentlyInvulnerable(true);
			level().addFreshEntity(carro);
			trem.add(carro);
		}
		morador.getNavigation().stop();
		morador.startRiding(trem.get(0), true, true);
		correndo = 0;
		level().playSound(null, bilheteria, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.0F, 1.3F);
		return true;
	}

	/** The train pulls out along the track, puffing, then the trip is on. */
	private void correr() {
		double v = Math.min(0.45, 0.06 + correndo * 0.01);
		for (Entity carro : trem) {
			if (carro.isAlive()) {
				carro.setDeltaMovement(rumo.x * v, carro.getDeltaMovement().y, rumo.z * v);
			}
		}
		Entity loco = trem.get(0);
		if (correndo % 6 == 0) {
			level().sendParticles(ParticleTypes.LARGE_SMOKE, loco.getX(), loco.getY() + 1.2, loco.getZ(), 3, 0.15, 0.1, 0.15, 0.02);
		}
		if (correndo == 0 || correndo == 30) {
			level().playSound(null, loco.blockPosition(), SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.NEUTRAL, 1.2F, 1.6F);
		}
		if (++correndo >= APITO || !loco.isAlive()) {
			desembarcar();
			Caravana.partir(level(), morador);
		}
	}

	private void desembarcar() {
		if (!trem.isEmpty()) {
			if (morador.isPassenger()) {
				morador.stopRiding();
			}
			trem.forEach(Entity::discard);
			trem.clear();
		}
	}
}
