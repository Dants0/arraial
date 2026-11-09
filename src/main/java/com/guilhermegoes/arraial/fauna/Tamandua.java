package com.guilhermegoes.arraial.fauna;

import com.guilhermegoes.arraial.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import org.jspecify.annotations.Nullable;

/**
 * Tamanduá-bandeira: the giant anteater, slow and solitary, nosing the ground for ants and termites.
 * Harmless unless cornered; it walks with its great flag of a tail and eats berries from the hand.
 */
public class Tamandua extends Animal {
	public Tamandua(EntityType<? extends Tamandua> type, Level level) {
		super(type, level);
		setPathfindingMalus(PathType.WATER, 0.0F);
	}

	public static AttributeSupplier.Builder atributos() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 16.0).add(Attributes.MOVEMENT_SPEED, 0.18);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new PanicGoal(this, 1.5));
		goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		goalSelector.addGoal(3, new TemptGoal(this, 1.1, this::isFood, false));
		goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
		goalSelector.addGoal(5, new RandomStrollGoal(this, 0.9));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob outro) {
		return ModEntities.TAMANDUA.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.FOX_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.FOX_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.FOX_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.6F;
	}
}
