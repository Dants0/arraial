package com.guilhermegoes.arraial.fauna;

import com.guilhermegoes.arraial.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import org.jspecify.annotations.Nullable;

/**
 * Jacaré: lies still on the riverbank and in the shallows. It leaves you alone on dry land, but
 * whoever comes close while it is at the edge of the water gets bitten; it also snatches chickens
 * and young capivaras that come to drink. It swims well and breeds when fed with fish.
 */
public class Jacare extends Animal {
	public Jacare(EntityType<? extends Jacare> type, Level level) {
		super(type, level);
		setPathfindingMalus(PathType.WATER, 0.0F);
	}

	public static AttributeSupplier.Builder atributos() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 24.0).add(Attributes.MOVEMENT_SPEED, 0.18)
				.add(Attributes.ATTACK_DAMAGE, 5.0).add(Attributes.ARMOR, 4.0).add(Attributes.FOLLOW_RANGE, 12.0);
	}

	/** At the water: in it, or with water right beside it. */
	public boolean naAgua() {
		if (isInWater()) {
			return true;
		}
		BlockPos p = blockPosition();
		for (BlockPos q : BlockPos.betweenClosed(p.offset(-2, -1, -2), p.offset(2, 0, 2))) {
			if (level().getFluidState(q).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.3, false));
		goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		goalSelector.addGoal(5, new RandomStrollGoal(this, 0.7, 240));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		// Only an ambush at the water, and only close by.
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 20, true, false,
				(alvo, level) -> naAgua() && alvo.distanceToSqr(this) < 25 && !isBaby()));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 40, true, false,
				(alvo, level) -> naAgua() && !isBaby() && alvo.distanceToSqr(this) < 64
						&& (alvo instanceof Chicken || alvo instanceof Capivara && alvo.isBaby())));
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity alvo) {
		boolean mordeu = super.doHurtTarget(level, alvo);
		if (mordeu) {
			level.playSound(null, blockPosition(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 0.8F, 0.7F);
		}
		return mordeu;
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.COD) || stack.is(Items.SALMON);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob outro) {
		return ModEntities.JACARE.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.TURTLE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.TURTLE_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.6F;
	}
}
