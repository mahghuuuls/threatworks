package com.mahghuuuls.threatworks.mixin;

import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import com.mahghuuuls.threatworks.server.NativeTargetGoals;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(EntityAITarget.class)
abstract class EntityAITargetMixin implements NativeTargetGoals.CachedTarget {
    @Shadow @Final protected EntityCreature taskOwner;
    @Shadow protected EntityLivingBase target;

    @Override
    public void threatworks$forgetTarget(UUID playerId) {
        if (target != null && playerId.equals(target.getUniqueID())) {
            target = null;
        }
    }

    @Inject(method = "isSuitableTarget(Lnet/minecraft/entity/EntityLiving;Lnet/minecraft/entity/EntityLivingBase;ZZ)Z",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void threatworks$excludeCandidate(EntityLiving mob, EntityLivingBase candidate,
                                                     boolean includeInvincibles, boolean checkSight,
                                                     CallbackInfoReturnable<Boolean> callback) {
        if (EncounterCoordinator.INSTANCE.isAwarenessExcluded(mob, candidate)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "shouldContinueExecuting", at = @At("HEAD"), cancellable = true, require = 1)
    private void threatworks$releaseCachedChoice(CallbackInfoReturnable<Boolean> callback) {
        if (!EncounterCoordinator.INSTANCE.hasAwarenessProvider()) {
            return;
        }
        EntityLivingBase current = taskOwner.getAttackTarget();
        if (EncounterCoordinator.INSTANCE.isAwarenessExcluded(taskOwner, current == null ? target : current)) {
            callback.setReturnValue(false);
        }
    }
}
