package com.mahghuuuls.threatworks.mixin;

import com.mahghuuuls.threatworks.server.NativeTargetGoals;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EntityAINearestAttackableTarget.class)
abstract class EntityAINearestAttackableTargetMixin implements NativeTargetGoals.Nearest {
    @Shadow @Final protected Class<?> targetClass;

    @Override
    public Class<?> threatworks$targetClass() {
        return targetClass;
    }
}
