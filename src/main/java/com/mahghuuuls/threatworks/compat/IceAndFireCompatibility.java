package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;

/** Ground melee for wild, on-foot Dread mobs with inspected native goals. */
public final class IceAndFireCompatibility {
    private static final String MINIMUM = "1.9.1-1.2.1";
    private static final String ENTITY_PACKAGE = "com.github.alexthe666.iceandfire.entity.";
    private static final Logger LOGGER = LogManager.getLogger("threatworks");
    private static boolean nativeReadFailed;

    private IceAndFireCompatibility() {
    }

    public static void reportVersion(Logger logger) {
        if (Installed.MOD == null) {
            return;
        }
        if (!Installed.SUPPORTED) {
            logger.warn("Ice and Fire Dread targeting unavailable for version {}. Update to at least {}.",
                    Installed.MOD.getVersion(), MINIMUM);
        } else if (Installed.COMMANDER == null || Installed.TARGET == null) {
            logger.warn("Ice and Fire Dread targeting unavailable because the native commander or target method could not be bound.");
        }
    }

    public static boolean matches(MobType type, EntityCreature mob) {
        return meleePriority(type) >= 0 && Installed.COMMANDER != null && Installed.TARGET != null
                && Installed.COMMANDER.getDeclaringClass().isInstance(mob)
                && mob.getClass().getName().equals(ENTITY_PACKAGE
                + (type == MobType.DREAD_THRALL ? "EntityDreadThrall" : "EntityDreadKnight"));
    }

    public static int meleePriority(MobType type) {
        return type == MobType.DREAD_THRALL || type == MobType.DREAD_KNIGHT ? 2 : -1;
    }

    public static boolean isHorseGoal(MobType type, EntityAITaskEntry entry) {
        return type == MobType.DREAD_KNIGHT && entry.priority == 0 && entry.action.getMutexBits() == 1
                && entry.action.getClass().getName().equals(ENTITY_PACKAGE + "ai.DreadAIRideHorse");
    }

    public static boolean isUncommandedOnFoot(EntityCreature mob) {
        if (mob.isRiding() || mob.isBeingRidden() || nativeReadFailed || Installed.COMMANDER == null) {
            return false;
        }
        try {
            return Installed.COMMANDER.invoke(mob) == null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            nativeReadFailed = true;
            LOGGER.warn("Disabling Ice and Fire Dread targeting because the commander read failed.", failure);
            return false;
        }
    }

    public static boolean acceptsTarget(MobType type, Entity participant) {
        if (meleePriority(type) < 0) {
            return true;
        }
        if (nativeReadFailed || Installed.TARGET == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(Installed.TARGET.invoke(null, participant));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            nativeReadFailed = true;
            LOGGER.warn("Disabling Ice and Fire Dread targeting because the native target check failed.", failure);
            return false;
        }
    }

    private static final class Installed {
        private static final ModContainer MOD = Loader.instance().getIndexedModList().get("iceandfire");
        private static final boolean SUPPORTED = MOD != null
                && new ComparableVersion(MOD.getVersion()).compareTo(new ComparableVersion(MINIMUM)) >= 0;
        private static final Method COMMANDER = SUPPORTED ? findCommander() : null;
        private static final Method TARGET = SUPPORTED ? findTargetPredicate() : null;

        private static Method findCommander() {
            try {
                Method method = Class.forName(ENTITY_PACKAGE + "EntityDreadMob", false, Loader.instance().getModClassLoader())
                        .asSubclass(EntityCreature.class).getMethod("getCommanderId");
                return method.getReturnType() == UUID.class && !Modifier.isStatic(method.getModifiers()) ? method : null;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                return null;
            }
        }

        private static Method findTargetPredicate() {
            try {
                Method method = Class.forName(ENTITY_PACKAGE + "DragonUtils", false, Loader.instance().getModClassLoader())
                        .getMethod("canHostilesTarget", Entity.class);
                return method.getReturnType() == boolean.class && Modifier.isStatic(method.getModifiers()) ? method : null;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                return null;
            }
        }
    }
}
