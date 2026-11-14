package com.mahghuuuls.threatworks.mixin;

import com.mahghuuuls.threatworks.server.DragonStrafe;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.dragon.phase.PhaseStrafePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PhaseStrafePlayer.class)
abstract class PhaseStrafePlayerMixin implements DragonStrafe {
    @Shadow private EntityLivingBase attackTarget;
    @Unique private boolean threatworks$ordinaryStrafe;

    @Inject(method = "setTarget", at = @At("HEAD"), require = 1)
    private void threatworks$clearStrafeOrigin(EntityLivingBase target, CallbackInfo callback) {
        threatworks$ordinaryStrafe = false;
    }

    @Override
    public EntityLivingBase threatworks$strafeTarget() {
        return attackTarget;
    }

    @Override
    public boolean threatworks$isOrdinaryStrafe() {
        return threatworks$ordinaryStrafe && attackTarget != null;
    }

    @Override
    public void threatworks$markOrdinaryStrafe() {
        threatworks$ordinaryStrafe = true;
    }
}
