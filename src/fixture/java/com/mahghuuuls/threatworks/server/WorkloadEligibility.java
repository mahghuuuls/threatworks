package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.WorldServer;

/** Samples live guard geometry outside the measured coordinator tick window. */
public final class WorkloadEligibility {
    private WorkloadEligibility() {
    }

    public static boolean observesEligible(WorldServer world, EntityCreature mob, EntityLivingBase guard, MobType type) {
        return MobTargets.observe(world, mob, guard, null, type).isEligible();
    }
}
