package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIFleeSun;
import net.minecraft.entity.ai.EntityAILeapAtTarget;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.Logger;

/** Ground combat from the Dungeon Mobs build pinned by TestPACK. */
public final class DungeonMobsCompatibility {
    private static final String MINIMUM = "5.8.9";
    private static final String ENTITY_PACKAGE = "com.gw.dm.entity.";

    private DungeonMobsCompatibility() {
    }

    public static void reportVersion(Logger logger) {
        if (Installed.MOD != null && !Installed.SUPPORTED) {
            logger.warn("Dungeon Mobs targeting unavailable for version {}. Update to at least {}.",
                    Installed.MOD.getVersion(), MINIMUM);
        }
    }

    public static boolean matches(MobType type, EntityCreature mob) {
        String entityClass = entityClass(type);
        return entityClass != null && Installed.SUPPORTED && mob.getClass().getName().equals(ENTITY_PACKAGE + entityClass);
    }

    public static int meleePriority(MobType type) {
        return entityClass(type) == null ? -1 : type == MobType.DUNGEON_REVENANT ? 3 : 2;
    }

    public static boolean isRevenantGoal(MobType type, EntityAITaskEntry entry) {
        if (type != MobType.DUNGEON_REVENANT) {
            return false;
        }
        Class<?> goal = entry.action.getClass();
        int mutex = entry.action.getMutexBits();
        return entry.priority == 2 && mutex == 5 && goal == EntityAILeapAtTarget.class
                || entry.priority == 4 && mutex == 1 && goal == EntityAIFleeSun.class;
    }

    public static boolean hasUnknownRevenantGoal(MobType type, EntityAITaskEntry entry) {
        Class<?> goal = entry.action.getClass();
        return type == MobType.DUNGEON_REVENANT && (goal == EntityAILeapAtTarget.class || goal == EntityAIFleeSun.class)
                && !isRevenantGoal(type, entry);
    }

    private static String entityClass(MobType type) {
        switch (type) {
            case DUNGEON_GHOUL:
                return "EntityGhoul";
            case DUNGEON_GHOST:
                return "EntityGhost";
            case DUNGEON_REVENANT:
                return "EntityRevenant";
            case DUNGEON_HOOK_HORROR:
                return "EntityHookHorror";
            case DUNGEON_UMBER_HULK:
                return "EntityUmberHulk";
            default:
                return null;
        }
    }

    private static final class Installed {
        private static final ModContainer MOD = Loader.instance().getIndexedModList().get("dungeonmobs");
        private static final boolean SUPPORTED = MOD != null
                && new ComparableVersion(MOD.getVersion()).compareTo(new ComparableVersion(MINIMUM)) >= 0;
    }
}
