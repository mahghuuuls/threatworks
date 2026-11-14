package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAIAttackRanged;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.ai.EntityAIFleeSun;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.init.Items;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
            case GAIA_ORC:
                return 2;
            case GAIA_BONE_KNIGHT:
            case GAIA_VAMPIRE:
                return 3;
            default:
                return entitySuffix(type) == null ? -1 : 1;
        }
    }

    public enum RangedAttack {
        NONE, ARROW, FIREBALL, MAGIC, POTION
    }

    public static boolean hasRangedContract(MobType type) {
        return projectileStyle(type) != RangedAttack.NONE;
    }

    private static RangedAttack projectileStyle(MobType type) {
        switch (type) {
            case GAIA_GOBLIN_FERAL:
            case GAIA_KOBOLD:
            case GAIA_MINOTAURUS:
            case GAIA_SELKIE:
            case GAIA_SIREN:
                return RangedAttack.ARROW;
            case GAIA_BAPHOMET:
            case GAIA_FLESH_LICH:
            case GAIA_ILLAGER_FIRE:
            case GAIA_NINE_TAILS:
                return RangedAttack.FIREBALL;
            case GAIA_SHAMAN:
            case GAIA_WITCH:
                return RangedAttack.POTION;
            case GAIA_ANT_RANGER:
            case GAIA_ANUBIS:
            case GAIA_ARACHNE:
            case GAIA_BEHOLDER:
                return RangedAttack.MAGIC;
            default:
                return RangedAttack.NONE;
        }
    }

    public static RangedAttack currentRangedAttack(MobType type, EntityCreature mob) {
        RangedAttack style = projectileStyle(type);
        if (style != RangedAttack.NONE) {
            for (EntityAITaskEntry entry : mob.tasks.taskEntries) {
                if (isRangedGoal(type, mob, entry)) {
                    return style;
                }
            }
        }
        return RangedAttack.NONE;
    }

    public static boolean isRangedGoal(MobType type, EntityCreature mob, EntityAITaskEntry entry) {
        RangedAttack style = projectileStyle(type);
        int priority = type == MobType.GAIA_ANUBIS || type == MobType.GAIA_ARACHNE ? 2 : 1;
        if (style == RangedAttack.NONE || entry.priority != priority || entry.action.getMutexBits() != 3) {
            return false;
        }
        Class<?> goal = entry.action.getClass();
        return style == RangedAttack.ARROW ? mob.getHeldItemMainhand().getItem() == Items.BOW
                && goal.getName().equals("gaia.entity.ai.EntityAIGaiaAttackRangedBow")
                : goal == EntityAIAttackRanged.class;
    }

    public static boolean hasUnexpectedRangedGoal(MobType type, EntityCreature mob, EntityAITaskEntry entry) {
        if (!hasRangedContract(type) && type != MobType.GAIA_CECAELIA && type != MobType.GAIA_ORC) {
            return false;
        }
        Class<?> action = entry.action.getClass();
        return (EntityAIAttackRanged.class.isAssignableFrom(action)
                || action.getName().equals("gaia.entity.ai.EntityAIGaiaAttackRangedBow"))
                && !isRangedGoal(type, mob, entry);
    }

    public static boolean isRangedVariantMelee(MobType type, EntityCreature mob, Class<?> goal) {
        if (!hasRangedContract(type)) {
            return false;
        }
        switch (type) {
            case GAIA_ANT_RANGER:
            case GAIA_BEHOLDER:
            case GAIA_FLESH_LICH:
            case GAIA_WITCH:
            case GAIA_GOBLIN_FERAL:
                return false;
            default:
                if (projectileStyle(type) == RangedAttack.ARROW) {
                    return mob.getHeldItemMainhand().getItem() != Items.BOW && goal.getSuperclass() == EntityAIAttackMelee.class
                            && goal.getName().equals(mob.getClass().getName() + "$1");
                }
                return goal == EntityAIAttackMelee.class;
        }
    }

    public static boolean isRangedVariantUtility(MobType type, EntityAITaskEntry entry) {
        int mutex = entry.action.getMutexBits();
        return mutex == 1 && entry.priority == 2
                && (type == MobType.GAIA_ANUBIS || type == MobType.GAIA_ARACHNE) && entry.action.getClass() == EntityAIWander.class
                || isOrcDoorGoal(type, entry);
    }

    public static boolean isOrcDoorGoal(MobType type, EntityAITaskEntry entry) {
        return type == MobType.GAIA_ORC && entry.priority == 1 && entry.action.getMutexBits() == 0
                && entry.action.getClass().getName().equals("gaia.entity.ai.EntityAIGaiaBreakDoor");
    }

    public static boolean isNativePhase(MobType type, EntityCreature mob) {
        return (hasRangedContract(type) || type == MobType.GAIA_CECAELIA || type == MobType.GAIA_ORC)
                && (mob.isRiding() || mob.isBeingRidden() || mob.isInWater() || mob.isInLava()
                || type == MobType.GAIA_WITCH && nativeFlag(Installed.DRINKING, mob)
                || type == MobType.GAIA_ARACHNE && mob.isOnLadder()
                || isAntRangerHidden(type, mob));
    }

    public static boolean isAntRangerHidden(MobType type, EntityCreature mob) {
        return type == MobType.GAIA_ANT_RANGER && nativeFlag(Installed.HIDDEN, mob);
    }

    private static boolean nativeFlag(MethodHandle flag, EntityCreature mob) {
        try {
            return flag == null || (boolean) flag.invokeExact(mob);
        } catch (Throwable failure) {
            return true;
        }
    }

    public static boolean isAntWander(MobType type, EntityAITaskEntry entry) {
        return type == MobType.GAIA_ANT && entry.priority == 2 && entry.action.getClass() == EntityAIWander.class
                && entry.action.getMutexBits() == 1;
    }

    public static boolean isFleeSun(MobType type, EntityAITaskEntry entry) {
        return (type == MobType.GAIA_BONE_KNIGHT || type == MobType.GAIA_VAMPIRE)
                && entry.priority == 2 && entry.action.getClass() == EntityAIFleeSun.class
                && entry.action.getMutexBits() == 1;
    }

    public static boolean isAquaticPhase(MobType type, EntityCreature mob) {
        return (type == MobType.GAIA_NAGA || type == MobType.GAIA_SHARKO)
                && (mob.isWet() || mob.isInLava());
    }

    public static boolean matches(MobType type, EntityCreature mob) {
        String suffix = entitySuffix(type);
        return suffix != null && Installed.NEUTRAL != null
                && (type != MobType.GAIA_ANT_RANGER || Installed.HIDDEN != null)
                && (type != MobType.GAIA_WITCH || Installed.DRINKING != null)
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
            case GAIA_BONE_KNIGHT:
                return "BoneKnight";
            case GAIA_VAMPIRE:
                return "Vampire";
            case GAIA_ANT_RANGER:
                return "AntRanger";
            case GAIA_ANUBIS:
                return "Anubis";
            case GAIA_ARACHNE:
                return "Arachne";
            case GAIA_BAPHOMET:
                return "Baphomet";
            case GAIA_BEHOLDER:
                return "Beholder";
            case GAIA_CECAELIA:
                return "Cecaelia";
            case GAIA_FLESH_LICH:
                return "FleshLich";
            case GAIA_GOBLIN_FERAL:
                return "GoblinFeral";
            case GAIA_ILLAGER_FIRE:
                return "IllagerFire";
            case GAIA_KOBOLD:
                return "Kobold";
            case GAIA_MINOTAURUS:
                return "Minotaurus";
            case GAIA_NINE_TAILS:
                return "NineTails";
            case GAIA_ORC:
                return "Orc";
            case GAIA_SELKIE:
                return "Selkie";
            case GAIA_SHAMAN:
                return "Shaman";
            case GAIA_SIREN:
                return "Siren";
            case GAIA_WITCH:
                return "Witch";
            default:
                return null;
        }
    }

    private static final class Installed {
        private static final ModContainer MOD = Loader.instance().getIndexedModList().get(MOD_ID);
        private static final boolean SUPPORTED = MOD != null
                && new ComparableVersion(MOD.getVersion()).compareTo(new ComparableVersion(MINIMUM)) >= 0;
        private static final Method NEUTRAL = SUPPORTED ? findNeutral() : null;
        private static final MethodHandle DRINKING = SUPPORTED ? findFlag("Witch", "isDrinkingPotion") : null;
        private static final MethodHandle HIDDEN = SUPPORTED ? findFlag("AntRanger", "getHidding") : null;

        private static MethodHandle findFlag(String suffix, String method) {
            try {
                Class<?> entity = Class.forName(ENTITY_PACKAGE + suffix, false, Loader.instance().getModClassLoader());
                return MethodHandles.publicLookup().findVirtual(entity, method, MethodType.methodType(boolean.class))
                        .asType(MethodType.methodType(boolean.class, EntityCreature.class));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                return null;
            }
        }

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
