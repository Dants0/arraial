package com.guilhermegoes.arraial.fauna;

import com.guilhermegoes.arraial.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Onça-pintada: the jaguar, lord of the forest. Shy with people (it slips away unless attacked,
 * then it fights hard), it hunts now and then: capivaras, chickens, rabbits, pigs and sheep,
 * corral animals included, so a village near the forest wants its soldados.
 */
public class Onca extends Animal {
	/** Ticks until it is hungry again after a kill. */
	private int saciada;

	public Onca(EntityType<? extends Onca> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder atributos() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 6.0).add(Attributes.FOLLOW_RANGE, 24.0);
	}

	private static boolean presa(LivingEntity e) {
		return e instanceof Capivara || e instanceof Chicken || e instanceof Rabbit || e instanceof Pig || e instanceof Sheep;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new LeapAtTargetGoal(this, 0.4F));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.4, true));
		goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, 12.0F, 1.0, 1.3, e -> getTarget() == null));
		goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 60, true, false,
				(alvo, level) -> saciada <= 0 && !isBaby() && presa(alvo)));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (saciada > 0) {
			saciada--;
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity alvo) {
		boolean feriu = super.doHurtTarget(level, alvo);
		if (feriu && alvo instanceof LivingEntity l && !l.isAlive() && presa(l)) {
			saciada = 6000 + getRandom().nextInt(6000);   // fed for a good while
			setTarget(null);
		}
		return feriu;
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return false;
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob outro) {
		return ModEntities.ONCA.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.OCELOT_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.OCELOT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.OCELOT_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.45F;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 400;
	}
}
