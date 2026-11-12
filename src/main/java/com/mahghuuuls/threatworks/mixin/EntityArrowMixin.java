package com.mahghuuuls.threatworks.mixin;

import com.mahghuuuls.threatworks.server.ArrowLaunch;
import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import net.minecraft.entity.projectile.EntityArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityArrow.class)
abstract class EntityArrowMixin implements ArrowLaunch {
    @Unique
    private Snapshot threatworks$launch;

    @Inject(method = "shoot(DDDFF)V", at = @At("RETURN"), require = 1)
    private void threatworks$observeLaunch(double x, double y, double z, float velocity, float inaccuracy,
                                           CallbackInfo callback) {
        if (threatworks$launch == null) {
            threatworks$launch = EncounterCoordinator.INSTANCE.observeArrowLaunch((EntityArrow) (Object) this);
        }
    }

    @Override
    public Snapshot threatworks$getLaunch() {
        return threatworks$launch;
    }
}
