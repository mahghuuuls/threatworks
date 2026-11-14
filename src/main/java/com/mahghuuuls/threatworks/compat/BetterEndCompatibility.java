package com.mahghuuuls.threatworks.compat;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.Logger;

/** Exact End Slime movement contract. */
public final class BetterEndCompatibility {
    private static final String END_SLIME = "mod.beethoven92.betterendforge.common.entity.EndSlimeEntity";
    private static final String MINIMUM = "1.1.1";

    private BetterEndCompatibility() {
    }

    public static void reportVersion(Logger logger) {
        if (Installed.MOD != null && !Installed.SUPPORTED) {
            logger.warn("BetterEnd Forge targeting unavailable for version {}. Update to at least {}.",
                    Installed.MOD.getVersion(), MINIMUM);
        }
    }

    public static boolean matchesSlime(EntityLiving mob) {
        return Installed.SUPPORTED && mob.getClass().getName().equals(END_SLIME);
    }

    public static boolean hasSlimeMovement(EntityLiving mob) {
        if (!mob.getMoveHelper().getClass().getName().equals(END_SLIME + "$EndSlimeMoveHelper")) {
            return false;
        }
        int goals = 0;
        for (EntityAITaskEntry entry : mob.tasks.taskEntries) {
            String name = entry.action.getClass().getName();
            int mutex = entry.action.getMutexBits();
            if (entry.priority == 1 && mutex == 2 && name.equals(END_SLIME + "$SwimmingGoal")) {
                goals |= 1;
            } else if (entry.priority == 2 && mutex == 1 && name.equals(END_SLIME + "$FaceTowardTargetGoal")) {
                goals |= 2;
            } else if (entry.priority == 3 && mutex == 1 && name.equals(END_SLIME + "$RandomLookGoal")) {
                goals |= 4;
            } else if (entry.priority == 5 && mutex == 2 && name.equals(END_SLIME + "$MoveGoal")) {
                goals |= 8;
            }
        }
        return goals == 15;
    }

    private static final class Installed {
        private static final ModContainer MOD = Loader.instance().getIndexedModList().get("betterendforge");
        private static final boolean SUPPORTED = MOD != null
                && new ComparableVersion(MOD.getVersion()).compareTo(new ComparableVersion(MINIMUM)) >= 0;
    }
}
