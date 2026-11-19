package com.mahghuuuls.threatworks.mixin;

import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import com.mahghuuuls.threatworks.server.NativeTargetGoals;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityLiving.class)
abstract class EntityLivingMixin implements NativeTargetGoals {
    @Shadow @Final public EntityAITasks targetTasks;
    @Unique private EntityAITaskEntry[] threatworks$initialGoals;

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void threatworks$rememberTargetGoals(World world, CallbackInfo callback) {
        Class<?> type = getClass();
        if (world != null && !world.isRemote && EncounterCoordinator.INSTANCE.hasAwarenessProvider()
                && (type == EntityZombie.class || type == EntitySkeleton.class)) {
            threatworks$initialGoals = targetTasks.taskEntries.toArray(new EntityAITaskEntry[0]);
        }
    }

    @Override
    public boolean threatworks$hasOriginalTargetGoals() {
        if (threatworks$initialGoals == null || threatworks$initialGoals.length != targetTasks.taskEntries.size()) {
            return false;
        }
        int index = 0;
        for (EntityAITaskEntry entry : targetTasks.taskEntries) {
            if (entry != threatworks$initialGoals[index++] || entry.action.getMutexBits() != 1) {
                return false;
            }
        }
        return true;
    }

    @Inject(method = "setAttackTarget", at = @At("HEAD"), cancellable = true, require = 1)
    private void threatworks$guardTargetWrite(EntityLivingBase target, CallbackInfo callback) {
        if (EncounterCoordinator.INSTANCE.blocksTargetWrite((EntityLiving) (Object) this, target)) {
            callback.cancel();
        }
    }

    @Inject(method = "updateEntityActionState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/ai/EntityAITasks;onUpdateTasks()V", ordinal = 0), require = 1)
    private void threatworks$releaseClearedFocus(CallbackInfo callback) {
        EncounterCoordinator.INSTANCE.releaseClearedFocus((EntityLiving) (Object) this);
    }

    @Inject(method = "updateEntityActionState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/ai/EntityAITasks;onUpdateTasks()V", ordinal = 1), require = 1)
    private void threatworks$releaseExcludedFocus(CallbackInfo callback) {
        EncounterCoordinator.INSTANCE.releaseExcludedFocus((EntityLiving) (Object) this);
    }
}
