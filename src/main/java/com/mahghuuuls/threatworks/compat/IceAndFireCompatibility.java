package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Field;
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
        String entityClass = entityClass(type);
        return entityClass != null && Installed.COMMANDER != null && Installed.TARGET != null
                && Installed.COMMANDER.getDeclaringClass().isInstance(mob)
                && mob.getClass().getName().equals(ENTITY_PACKAGE + entityClass);
    }

    public static int meleePriority(MobType type) {
        return entityClass(type) == null ? -1 : 2;
    }

    private static String entityClass(MobType type) {
        switch (type) {
            case DREAD_THRALL:
                return "EntityDreadThrall";
            case DREAD_KNIGHT:
                return "EntityDreadKnight";
            case DREAD_BEAST:
                return "EntityDreadBeast";
            default:
                return null;
        }
    }

    public static boolean beastBiteReaches(Vec3d from, EntityLivingBase target) {
        float x = (float) (from.x - target.posX);
        float y = (float) (from.y - target.posY);
        float z = (float) (from.z - target.posZ);
        return MathHelper.sqrt(x * x + y * y + z * z) < 4.0F;
    }

    public static boolean beastUnavailable(EntityCreature mob) {
        if (Beast.ANIMATION == null || Beast.SPAWN == null || Beast.readFailed) {
            return true;
        }
        try {
            return Beast.ANIMATION.invoke(mob) == Beast.SPAWN;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            Beast.readFailed = true;
            LOGGER.warn("Disabling Ice and Fire Dread Beast targeting because its animation read failed.", failure);
            return true;
        }
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

    private static final class Beast {
        private static final Method ANIMATION;
        private static final Object SPAWN;
        private static boolean readFailed;

        static {
            Method animation = null;
            Object spawn = null;
            try {
                Class<?> entity = Class.forName(ENTITY_PACKAGE + "EntityDreadBeast", false, Loader.instance().getModClassLoader())
                        .asSubclass(EntityCreature.class);
                Method getter = entity.getMethod("getAnimation");
                Field field = entity.getField("ANIMATION_SPAWN");
                if (!Modifier.isStatic(getter.getModifiers()) && Modifier.isStatic(field.getModifiers())
                        && getter.getReturnType() == field.getType()) {
                    spawn = field.get(null);
                    animation = getter;
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                LOGGER.warn("Ice and Fire Dread Beast targeting unavailable because its animation could not be bound.", failure);
            }
            ANIMATION = animation;
            SPAWN = spawn;
        }
    }
}
