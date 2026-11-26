package com.guilhermegoes.arraial.item;

import com.guilhermegoes.arraial.registry.ModEntities;
import com.guilhermegoes.arraial.registry.ModItems;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** A pebble shot from the estilingue: it hurts (4 points) and knocks back a little, then it is gone. */
public class Pedrada extends ThrowableItemProjectile {
	public Pedrada(EntityType<? extends Pedrada> type, Level level) {
		super(type, level);
	}

	public Pedrada(ServerLevel level, LivingEntity dono, ItemStack stack) {
		super(ModEntities.PEDRADA, dono, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.SEIXO;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (level() instanceof ServerLevel server) {
			hit.getEntity().hurtServer(server, damageSources().thrown(this, getOwner()), 4.0F);
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (level() instanceof ServerLevel server) {
			server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, getItem().getItem()), getX(), getY(), getZ(), 6, 0.05, 0.05, 0.05, 0.08);
			discard();
		}
	}
}
