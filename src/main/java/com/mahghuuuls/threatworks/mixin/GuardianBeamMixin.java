package com.mahghuuuls.threatworks.mixin;

import com.mahghuuuls.threatworks.server.GuardianBeam;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.network.datasync.DataParameter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;

@Mixin(EntityGuardian.class)
abstract class GuardianBeamMixin implements GuardianBeam {
    @Shadow @Final private static DataParameter<Integer> TARGET_ENTITY;

    @Override
    public int threatworks$beamTargetId() {
        return ((EntityGuardian) (Object) this).getDataManager().get(TARGET_ENTITY);
    }
}
