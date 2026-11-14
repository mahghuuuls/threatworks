package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Ordinary ground melee shares Gaia's native neutral state. */
public final class GaiaCompatibility {
    private static final String MOD_ID = "grimoireofgaia";
    private static final String MINIMUM = "1.7.2";
    private static final String BASE = "gaia.entity.EntityMobHostileBase";
    private static final String ENTITY_PACKAGE = "gaia.entity.monster.EntityGaia";
    private static final Logger LOGGER = LogManager.getLogger("threatworks");
    private static boolean neutralReadFailed;

    private GaiaCompatibility() {
    }

    public static void reportVersion(Logger logger) {
        if (Installed.MOD == null) {
            return;
        }
        if (!Installed.SUPPORTED) {
            logger.warn("Grimoire of Gaia targeting unavailable for version {}. Update to at least {}.",
                    Installed.MOD.getVersion(), MINIMUM);
        } else if (Installed.NEUTRAL == null) {
            logger.warn("Grimoire of Gaia targeting unavailable because its neutral-state method could not be bound.");
        }
    }

    public static int meleePriority(MobType type) {
        switch (type) {
            case GAIA_COBBLESTONE_GOLEM:
            case GAIA_GELATINOUS_SLIME:
            case GAIA_MATANGO:
                return 0;
            case GAIA_ANT:
            case GAIA_SPORELING:
                return 2;
            default:
                return entitySuffix(type) == null ? -1 : 1;
        }
    }

    public static boolean isAntWander(MobType type, EntityAITaskEntry entry) {
        return type == MobType.GAIA_ANT && entry.priority == 2 && entry.action.getClass() == EntityAIWander.class
                && entry.action.getMutexBits() == 1;
    }

    public static boolean isAquaticPhase(MobType type, EntityCreature mob) {
        return (type == MobType.GAIA_NAGA || type == MobType.GAIA_SHARKO)
                && (mob.isWet() || mob.isInLava());
    }

    public static boolean matches(MobType type, EntityCreature mob) {
        String suffix = entitySuffix(type);
        return suffix != null && Installed.NEUTRAL != null
                && Installed.NEUTRAL.getDeclaringClass().isInstance(mob)
                && mob.getClass().getName().equals(ENTITY_PACKAGE + suffix);
    }

    public static boolean isHostile(EntityCreature mob) {
        if (neutralReadFailed) {
            return false;
        }
        try {
            return Boolean.FALSE.equals(Installed.NEUTRAL.invoke(mob));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            neutralReadFailed = true;
            LOGGER.warn("Disabling Grimoire of Gaia targeting because its neutral-state read failed.", failure);
            return false;
        }
    }

    private static String entitySuffix(MobType type) {
        switch (type) {
            case GAIA_MUMMY:
                return "Mummy";
            case GAIA_SLUDGE_GIRL:
                return "SludgeGirl";
            case GAIA_SUCCUBUS:
                return "Succubus";
            case GAIA_WITHER_COW:
                return "WitherCow";
            case GAIA_ANT:
                return "Ant";
            case GAIA_COBBLESTONE_GOLEM:
                return "CobblestoneGolem";
            case GAIA_DULLAHAN:
                return "Dullahan";
            case GAIA_GELATINOUS_SLIME:
                return "GelatinousSlime";
            case GAIA_ILLAGER_BUTLER:
                return "IllagerButler";
            case GAIA_ILLAGER_INQUISITOR:
                return "IllagerInquisitor";
            case GAIA_SPORELING:
                return "Sporeling";
            case GAIA_YETI:
                return "Yeti";
            case GAIA_MANDRAGORA:
                return "Mandragora";
            case GAIA_MATANGO:
                return "Matango";
            case GAIA_MIMIC:
                return "Mimic";
            case GAIA_SPHINX:
                return "Sphinx";
            case GAIA_SPRIGGAN:
                return "Spriggan";
            case GAIA_DEATHWORD:
                return "Deathword";
            case GAIA_DHAMPIR:
                return "Dhampir";
            case GAIA_MINOTAUR:
                return "Minotaur";
            case GAIA_NAGA:
                return "Naga";
            case GAIA_ONI:
                return "Oni";
            case GAIA_SHARKO:
                return "Sharko";
            default:
                return null;
        }
    }

    private static final class Installed {
        private static final ModContainer MOD = Loader.instance().getIndexedModList().get(MOD_ID);
        private static final boolean SUPPORTED = MOD != null
                && new ComparableVersion(MOD.getVersion()).compareTo(new ComparableVersion(MINIMUM)) >= 0;
        private static final Method NEUTRAL = SUPPORTED ? findNeutral() : null;

        private static Method findNeutral() {
            try {
                Method method = Class.forName(BASE, false, Loader.instance().getModClassLoader())
                        .asSubclass(EntityCreature.class).getMethod("isNeutral");
                return method.getReturnType() == boolean.class && !Modifier.isStatic(method.getModifiers()) ? method : null;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                return null;
            }
        }
    }
}
