package com.mahghuuuls.threatworks.mixin;

import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityLiving.class)
abstract class EntityLivingMixin {
    @Inject(method = "setAttackTarget", at = @At("HEAD"), cancellable = true, require = 1)
    private void threatworks$guardTargetWrite(EntityLivingBase target, CallbackInfo callback) {
        if (EncounterCoordinator.INSTANCE.blocksTargetWrite((EntityLiving) (Object) this, target)) {
            callback.cancel();
        }
    }
}
