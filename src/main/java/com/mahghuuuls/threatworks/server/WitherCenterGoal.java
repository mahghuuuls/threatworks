package com.mahghuuuls.threatworks.server;

import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;

/** The center ranged goal verified by normal Wither admission. */
public interface WitherCenterGoal {
    EntityAITaskEntry threatworks$centerGoal();

    void threatworks$centerGoal(EntityAITaskEntry goal);
}
