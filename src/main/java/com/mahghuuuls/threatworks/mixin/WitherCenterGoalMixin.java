package com.mahghuuuls.threatworks.mixin;

import com.mahghuuuls.threatworks.server.WitherCenterGoal;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.boss.EntityWither;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityWither.class)
abstract class WitherCenterGoalMixin implements WitherCenterGoal {
    @Unique private EntityAITaskEntry threatworks$centerGoal;

    @Override
    public EntityAITaskEntry threatworks$centerGoal() { return threatworks$centerGoal; }

    @Override
    public void threatworks$centerGoal(EntityAITaskEntry goal) { threatworks$centerGoal = goal; }
}
