package com.mahghuuuls.threatworks.compat;

import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.Logger;

/** The pinned Special AI reactions compete with normal combat only while running. */
public final class SpecialAiCompatibility {
    private static final String REACTIONS = "fathertoast.specialai.ai.react.";
    private static final String AVOID_EXPLOSIONS = REACTIONS + "EntityAIAvoidExplosions";
    private static final String DODGE_ARROWS = REACTIONS + "EntityAIDodgeArrows";
    private static final String GRIEF_BLOCKS = "fathertoast.specialai.ai.grief.EntityAIGriefBlocks";
    private static final String EAT_BREEDING_ITEM = "fathertoast.specialai.ai.grief.EntityAIEatBreedingItem";
    private static final String RIDER = "fathertoast.specialai.ai.EntityAIRider";

    private SpecialAiCompatibility() {
    }

    public static void reportVersion(Logger logger) {
        if (Installed.MOD != null && !Installed.VERSION_MATCHES) {
            logger.warn("Special AI combat reactions are unsupported for Special AI {}. Verified version is {}.",
                    Installed.MOD.getVersion(), Installed.VERSION);
        }
    }

    public static boolean hasUnknownCombatGoal(EntityAITaskEntry entry) {
        String name = entry.action.getClass().getName();
        if (name.equals(AVOID_EXPLOSIONS) || name.equals(DODGE_ARROWS)) {
            return !isCombatReaction(entry);
        }
        return name.equals(GRIEF_BLOCKS) && !isBlockGriefing(entry);
    }

    public static boolean isEliteGoal(EntityAITaskEntry entry) {
        return entry.action.getClass().getName().startsWith("fathertoast.specialai.ai.elite.");
    }

    public static boolean isBreedingItemGoal(EntityAITaskEntry entry) {
        return Installed.VERSION_MATCHES && entry.action.getMutexBits() == 3
                && entry.action.getClass().getName().equals(EAT_BREEDING_ITEM);
    }

    public static boolean hasUnknownBreedingItemGoal(EntityAITaskEntry entry) {
        return entry.action.getClass().getName().equals(EAT_BREEDING_ITEM) && !isBreedingItemGoal(entry);
    }

    public static boolean isCombatReaction(EntityAITaskEntry entry) {
        if (entry.priority != -1) {
            return false;
        }
        String name = entry.action.getClass().getName();
        int mutex = entry.action.getMutexBits();
        return (mutex == 3 && name.equals(AVOID_EXPLOSIONS)
                || mutex == 4 && name.equals(DODGE_ARROWS)) && Installed.VERSION_MATCHES;
    }

    public static boolean isWitchRider(EntityAITaskEntry entry) {
        return entry.priority == 2 && entry.action.getMutexBits() == 3 && Installed.VERSION_MATCHES
                && entry.action.getClass().getName().equals(RIDER) && !entry.action.isInterruptible();
    }

    public static boolean isBlockGriefing(EntityAITaskEntry entry) {
        return entry.action.getMutexBits() == 3 && entry.action.getClass().getName().equals(GRIEF_BLOCKS)
                && Installed.VERSION_MATCHES;
    }

    private static final class Installed {
        private static final String VERSION = "1.1.0_for_mc1.12.2";
        private static final ModContainer MOD = Loader.instance().getIndexedModList().get("specialai");
        private static final boolean VERSION_MATCHES = MOD != null && VERSION.equals(MOD.getVersion());
    }
}
