package com.guilhermegoes.arraial.npc;

import com.guilhermegoes.arraial.registry.ModPois;
import com.guilhermegoes.arraial.vila.Vilas;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The caçador hunts wild animals for meat, hides and feathers, by day, around the arraial.
 * He never touches: the herd of the curral (anything near a cocho), animals on a lead, tame
 * animals (dogs, cats...), horses and their kin (the arraial's mounts, later on), named animals
 * and young ones. And he leaves at least {@link #MINIMO} of each kind around so they breed back.
 */
public class CacadorGoal extends OficioGoal {
	private static final int RAIO = 40;
	private static final int MINIMO = 3;
	private static final int CARGA = 16;

	private @Nullable Animal presa;
	private boolean entregando;
	private int tempo;

	public CacadorGoal(MoradorEntity morador) {
		super(morador, Profissao.CACADOR);
	}

	@Override
	public boolean canUse() {
		if (!hora() || !podeTrabalhar()) {
			return false;
		}
		if (morador.cargaTotal() >= CARGA && !armazens().isEmpty()) {
			entregando = true;
			return true;
		}
		entregando = false;
		presa = escolherPresa();
		if (presa == null) {
			if (morador.cargaTotal() > 0 && !armazens().isEmpty()) {
				entregando = true;
				return true;
			}
			morador.setStatus(Component.translatable("arraial.cacador.sem_caca"));
			return false;
		}
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return podeTrabalhar() && (entregando || (presa != null && presa.isAlive() && tempo < 900));
	}

	@Override
	public void start() {
		tempo = 0;
		morador.segurar(new ItemStack(entregando ? Items.LEATHER : Items.IRON_SWORD));
	}

	@Override
	public void stop() {
		super.stop();
		presa = null;
	}

	/** May this animal be hunted? */
	public static boolean cacavel(Animal a, @Nullable BlockPos vila, net.minecraft.server.level.ServerLevel level) {
		if (!a.isAlive() || a.isBaby() || a.isLeashed() || a.hasCustomName() || a instanceof AbstractHorse
				|| (a instanceof TamableAnimal t && t.isTame())) {
			return false;
		}
		// The curral's herd: anything grazing near a cocho.
		if (vila != null && level.getPoiManager().findAll(h -> h.value() == ModPois.COCHO, p -> true, a.blockPosition(), 14,
				PoiManager.Occupancy.ANY).findAny().isPresent()) {
			return false;
		}
		return a.getType() == net.minecraft.world.entity.EntityTypes.COW || a.getType() == net.minecraft.world.entity.EntityTypes.PIG
				|| a.getType() == net.minecraft.world.entity.EntityTypes.SHEEP || a.getType() == net.minecraft.world.entity.EntityTypes.CHICKEN
				|| a.getType() == net.minecraft.world.entity.EntityTypes.RABBIT || a.getType() == net.minecraft.world.entity.EntityTypes.GOAT;
	}

	private @Nullable Animal escolherPresa() {
		BlockPos centro = morador.posto() != null ? morador.posto() : morador.blockPosition();
		List<Animal> bichos = level().getEntitiesOfClass(Animal.class, new AABB(centro).inflate(RAIO),
				a -> cacavel(a, morador.vila(), level()));
		Animal melhor = null;
		double d = Double.MAX_VALUE;
		for (Animal a : bichos) {
			// Leave enough of the kind to breed back.
			long iguais = level().getEntitiesOfClass(Animal.class, a.getBoundingBox().inflate(RAIO), b -> b.getType() == a.getType()).size();
			if (iguais <= MINIMO) {
				continue;
			}
			double x = a.distanceToSqr(morador);
			if (x < d) {
				d = x;
				melhor = a;
			}
		}
		return melhor;
	}

	@Override
	public void tick() {
		if (entregando) {
			var a = armazemMaisProximo();
			if (a == null) {
				entregando = false;
				return;
			}
			morador.setStatus(Component.translatable("arraial.oficio.entregando", Component.translatable("arraial.cacador.caca")));
			if (!perto(a.getBlockPos(), 2.5)) {
				andarAte(a.getBlockPos());
				return;
			}
			entregar(Map.of());
			entregando = false;
			return;
		}
		Animal alvo = presa;
		if (alvo == null) {
			return;
		}
		tempo++;
		morador.setStatus(Component.translatable("arraial.cacador.cacando", alvo.getType().getDescription()));
		if (morador.distanceTo(alvo) > 2.2) {
			if (tempo % 10 == 1) {
				morador.getNavigation().moveTo(alvo, 1.1);
			}
			return;
		}
		morador.getLookControl().setLookAt(alvo);
		if (tempo % 12 == 0) {
			bater();
			alvo.hurtServer(level(), level().damageSources().mobAttack(morador), 6.0F);
			if (!alvo.isAlive()) {
				recolherItens(alvo.position(), 3.0);
				presa = null;
			}
		}
	}

	/** Used by others to know the caçador's rules. */
	public static boolean protegido(Entity e) {
		return e instanceof LivingEntity l && l instanceof Animal a && (a.isLeashed() || a.hasCustomName() || a instanceof AbstractHorse
				|| (a instanceof TamableAnimal t && t.isTame()));
	}
}
