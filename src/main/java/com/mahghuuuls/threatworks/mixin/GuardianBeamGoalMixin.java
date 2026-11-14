package com.mahghuuuls.threatworks.mixin;

import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityGuardian;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.entity.monster.EntityGuardian$AIGuardianAttack")
abstract class GuardianBeamGoalMixin {
    @Redirect(method = "resetTask", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/monster/EntityGuardian;setAttackTarget(Lnet/minecraft/entity/EntityLivingBase;)V"),
            require = 1)
    private void threatworks$allowReset(EntityGuardian guardian, EntityLivingBase target) {
        EncounterCoordinator.INSTANCE.clearGuardianBeamTarget(guardian);
    }

    @Redirect(method = "updateTask", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/monster/EntityGuardian;setAttackTarget(Lnet/minecraft/entity/EntityLivingBase;)V"),
            require = 2)
    private void threatworks$allowFinishOrSightLoss(EntityGuardian guardian, EntityLivingBase target) {
        EncounterCoordinator.INSTANCE.clearGuardianBeamTarget(guardian);
    }
}
