package com.guilhermegoes.arraial.mixin;

import com.guilhermegoes.arraial.vila.Vilas;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Monsters do not spawn naturally inside an arraial: a cave or ravine next to the houses would
 * otherwise breed skeletons that shoot the roceiros one after another. Deep caves (more than 20
 * blocks above or below the Cruzeiro) stay as they are, and raids, patrols, spawners and the
 * bandits of the mod are not touched.
 */
@Mixin(Mob.class)
public abstract class MobSpawnMixin {
	@Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
	private void arraial$semMonstroNaVila(LevelAccessor level, EntitySpawnReason reason, CallbackInfoReturnable<Boolean> cir) {
		if (reason == EntitySpawnReason.NATURAL && this instanceof Enemy && level instanceof ServerLevel server
				&& Vilas.protegido(server, ((Mob) (Object) this).blockPosition())) {
			cir.setReturnValue(false);
		}
	}
}
