package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
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
        return entityClass(type) == null ? -1 : 2;
    }

    private static String entityClass(MobType type) {
        switch (type) {
            case DUNGEON_GHOUL:
                return "EntityGhoul";
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
