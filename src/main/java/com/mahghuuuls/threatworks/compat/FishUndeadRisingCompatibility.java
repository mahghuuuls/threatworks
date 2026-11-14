package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.ai.EntityAIFleeSun;
import net.minecraft.entity.ai.EntityAILeapAtTarget;
import net.minecraft.entity.ai.EntityAIMate;
import net.minecraft.entity.ai.EntityAITempt;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Shared ground melee and native phases of the TestPACK Fish's Undead Rising mobs. */
public final class FishUndeadRisingCompatibility {
    private static final String MINIMUM = "1.6.2";
    private static final String ENTITY_PACKAGE = "com.Fishmod.mod_LavaCow.entities.";
    private static final String FLOATING_BASE = ENTITY_PACKAGE + "floating.EntityFloatingMob";
    private static final int LEAP = 16;
    private static final int MATE = 32;
    private static final int TEMPT = 64;
    private static final int DESTROY_CROPS = 128;
    private static final int FLEE_SUN = 256;

    private FishUndeadRisingCompatibility() {
    }

    public static void reportVersion(Logger logger) {
        if (Installed.MOD != null && !Installed.SUPPORTED) {
            logger.warn("Fish's Undead Rising targeting unavailable for version {}. Update to at least {}.",
                    Installed.MOD.getVersion(), MINIMUM);
        }
    }

    public static boolean matches(MobType type, EntityCreature mob) {
        String entityClass = entityClass(type);
        return entityClass != null && Installed.SUPPORTED && mob.getClass().getName().equals(ENTITY_PACKAGE + entityClass)
                && (!usesSpells(type) || Installed.SPELL_TICKS != null
                && Installed.SPELL_TICKS.getDeclaringClass().isInstance(mob))
                && (!canClimb(type) || Installed.HANGING != null && Installed.CLIMBING != null)
                && (type != MobType.FISH_GHOUL || Installed.GHOUL_ATTACK_TIMER != null && Installed.GHOUL_HEALTH_THRESHOLD != null)
                && (!isWildZombie(type) || Installed.RISING != null && Installed.RISING.getDeclaringClass().isInstance(mob));
    }

    public static int meleePriority(MobType type) {
        if (type == MobType.FISH_WETA) {
            return 4;
        }
        if (type == MobType.FISH_GHOUL) {
            return 4;
        }
        if (usesWildAnimalMelee(type)) {
            return 5;
        }
        return type == MobType.FISH_PINGU || isWildZombie(type) ? 2 : usesSpells(type) ? 4 : -1;
    }

    public static boolean canUseGroundMelee(MobType type, EntityCreature mob) {
        return meleePriority(type) < 0 || !mob.isRiding() && !mob.isBeingRidden() && !mob.isInWater()
                && (!(isWildZombie(type) || usesWildAnimalMelee(type)) || isUnownedAndStanding(mob));
    }

    private static boolean isUnownedAndStanding(EntityCreature mob) {
        if (!(mob instanceof EntityTameable)) {
            return false;
        }
        EntityTameable tameable = (EntityTameable) mob;
        return !tameable.isTamed() && tameable.getOwnerId() == null && !tameable.isSitting();
    }

    public static int nativeGoal(MobType type, EntityAITaskEntry entry) {
        if (type == MobType.FISH_GHOUL) {
            Class<?> goal = entry.action.getClass();
            if (entry.priority == 3 && entry.action.getMutexBits() == 5 && goal == EntityAILeapAtTarget.class) {
                return LEAP;
            }
            if (entry.priority == 5 && entry.action.getMutexBits() == 1 && goal == EntityAIFleeSun.class) {
                return FLEE_SUN;
            }
        }
        if (usesWildAnimalMelee(type)) {
            Class<?> goal = entry.action.getClass();
            int mutex = entry.action.getMutexBits();
            if (goal == EntityAILeapAtTarget.class && entry.priority == meleePriority(type) - 1 && mutex == 5) {
                return LEAP;
            }
            if (type == MobType.FISH_WETA) {
                if (goal == EntityAIMate.class && entry.priority == 2 && mutex == 3) {
                    return MATE;
                }
                if (goal == EntityAITempt.class && entry.priority == 3 && mutex == 3) {
                    return TEMPT;
                }
                if (goal.getName().equals(ENTITY_PACKAGE + "ai.EntityAIDestroyCrops") && entry.priority == 5 && mutex == 5) {
                    return DESTROY_CROPS;
                }
            }
        }
        if ((isWildZombie(type) || type == MobType.FISH_GHOUL) && entry.priority == 1 && entry.action.getMutexBits() == 0
                && entry.action.getClass().getName().equals(ENTITY_PACKAGE + "ai.EntityFishAIBreakDoor")) {
            return 8;
        }
        if (!usesSpells(type)) {
            return 0;
        }
        String owner = ENTITY_PACKAGE + (type == MobType.FISH_IMP ? "EntityImp" : "EntityFoglet");
        String goal = entry.action.getClass().getName();
        if (entry.priority == 2 && entry.action.getMutexBits() == 3 && goal.equals(owner + "$AICastingApell")) {
            return 1;
        }
        if (entry.priority == 3 && entry.action.getMutexBits() == 0 && goal.equals(owner + "$AIUseSpell")) {
            return 2;
        }
        if (entry.priority == 1 && entry.action.getMutexBits() == 3 && canClimb(type)
                && goal.equals(owner + "$AIClimbimgTree")) {
            return 4;
        }
        return 0;
    }

    public static boolean hasNativeGoals(MobType type, int goals) {
        if (type == MobType.FISH_GHOUL) {
            return (goals & (LEAP | 8)) == (LEAP | 8);
        }
        if (usesWildAnimalMelee(type)) {
            int expected = type == MobType.FISH_WETA ? LEAP | MATE | TEMPT | DESTROY_CROPS : LEAP;
            return goals == expected;
        }
        return !usesSpells(type) || (goals & 3) == 3;
    }

    public static boolean nativePhase(MobType type, EntityCreature mob) {
        try {
            if (type == MobType.FISH_GHOUL) {
                return mob.isBurning() || Installed.GHOUL_ATTACK_TIMER == null
                        || (Integer) Installed.GHOUL_ATTACK_TIMER.invoke(mob) > 0;
            }
            if (isWildZombie(type)) {
                return Installed.RISING == null || (Boolean) Installed.RISING.invoke(mob);
            }
            if (!usesSpells(type)) {
                return false;
            }
            return Installed.SPELL_TICKS == null || Installed.SPELL_TICKS.getInt(mob) > 0
                    || canClimb(type) && (Installed.HANGING == null || Installed.CLIMBING == null
                    || (Boolean) Installed.HANGING.invoke(mob) || (Boolean) Installed.CLIMBING.invoke(mob));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return true;
        }
    }

    private static boolean usesSpells(MobType type) {
        return type == MobType.FISH_FOGLET || type == MobType.FISH_IMP || type == MobType.FISH_ISNACHI;
    }

    private static boolean canClimb(MobType type) {
        return type == MobType.FISH_FOGLET || type == MobType.FISH_ISNACHI;
    }

    private static boolean isWildZombie(MobType type) {
        return type == MobType.FISH_MUMMY || type == MobType.FISH_UNBURIED
                || type == MobType.FISH_FROZEN_ZOMBIE || type == MobType.FISH_MUSHROOM_ZOMBIE;
    }

    public static boolean usesFlyingCharge(MobType type) {
        return type == MobType.FISH_AVATON || type == MobType.FISH_BANSHEE || type == MobType.FISH_GRAVE_ROBBER_GHOST
                || type == MobType.FISH_SEA_HAG || type == MobType.FISH_WRAITH;
    }

    public static boolean isGhoulMelee(Class<?> goal) {
        return goal.getName().equals(ENTITY_PACKAGE + "EntityGhoul$AIGhoulAttack");
    }

    public static boolean isGhoulPickup(EntityAITaskEntry entry) {
        return entry.priority == 4 && entry.action.getMutexBits() == 1
                && entry.action.getClass().getName().equals(ENTITY_PACKAGE + "ai.EntityAIPickupMeat");
    }

    public static boolean ghoulAccepts(EntityCreature mob, EntityLivingBase target) {
        if (target == mob.getRevengeTarget() || target instanceof EntityVillager || target instanceof EntityIronGolem) {
            return true;
        }
        if (!target.attackable() || target instanceof EntityCreeper || target.isEntityUndead()) {
            return false;
        }
        try {
            float fraction = (float) Installed.GHOUL_HEALTH_THRESHOLD.getDouble(null) / 100.0F;
            return target.getHealth() <= target.getMaxHealth() * fraction;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    public static boolean matchesFlyingCharge(MobType type, EntityLiving mob) {
        return usesFlyingCharge(type) && Installed.FLOATING != null && Installed.FLOATING.isInstance(mob)
                && Installed.CHARGING != null && Installed.CASTING != null && Installed.FLYING_SPELL_TICKS != null
                && mob.getClass().getName().equals(ENTITY_PACKAGE + entityClass(type));
    }

    public static boolean hasFlyingMoveHelper(EntityLiving mob) {
        return mob.getMoveHelper().getClass().getName().equals(FLOATING_BASE + "$AIMoveControl");
    }

    public static boolean isChargeGoal(MobType type, EntityAITaskEntry entry) {
        return entry.priority == (type == MobType.FISH_AVATON ? 2 : 3) && entry.action.getMutexBits() == 1
                && entry.action.getClass().getName().equals(FLOATING_BASE + "$AIChargeAttack");
    }

    public static boolean isCastingGoal(EntityAITaskEntry entry) {
        return entry.priority == 1 && entry.action.getMutexBits() == 3
                && entry.action.getClass().getName().equals(FLOATING_BASE + "$AICastingApell");
    }

    public static boolean isFlyingSpellGoal(MobType type, EntityAITaskEntry entry) {
        int priority = type == MobType.FISH_WRAITH || type == MobType.FISH_BANSHEE ? 2 : 3;
        return entry.priority == priority && entry.action.getMutexBits() == 0
                && entry.action.getClass().getName().equals(ENTITY_PACKAGE + entityClass(type) + "$AIUseSpell");
    }

    public static boolean isFlyingWanderGoal(EntityAITaskEntry entry) {
        return entry.priority == 7 && entry.action.getMutexBits() == 1
                && entry.action.getClass().getName().equals(FLOATING_BASE + "$AIMoveRandom");
    }

    public static boolean flyingAttackCommitted(EntityLiving mob) {
        try {
            return Installed.CHARGING == null || Installed.CASTING == null || Installed.FLYING_SPELL_TICKS == null
                    || (Boolean) Installed.CHARGING.invoke(mob) || (Boolean) Installed.CASTING.invoke(mob)
                    || (Integer) Installed.FLYING_SPELL_TICKS.invoke(mob) > 0;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return true;
        }
    }

    public static boolean usesWildAnimalMelee(MobType type) {
        return type == MobType.FISH_SCARAB || type == MobType.FISH_LITTLE_SLUDGE
                || type == MobType.FISH_WETA || type == MobType.FISH_CACTOID;
    }

    private static String entityClass(MobType type) {
        switch (type) {
            case FISH_FOGLET:
                return "EntityFoglet";
            case FISH_IMP:
                return "EntityImp";
            case FISH_ISNACHI:
                return "EntityIsnachi";
            case FISH_PINGU:
                return "EntityPingu";
            case FISH_MUMMY:
                return "EntityMummy";
            case FISH_UNBURIED:
                return "tameable.EntityUnburied";
            case FISH_FROZEN_ZOMBIE:
                return "EntityZombieFrozen";
            case FISH_MUSHROOM_ZOMBIE:
                return "EntityZombieMushroom";
            case FISH_SCARAB:
                return "tameable.EntityScarab";
            case FISH_LITTLE_SLUDGE:
                return "tameable.EntityLilSludge";
            case FISH_WETA:
                return "tameable.EntityWeta";
            case FISH_CACTOID:
                return "tameable.EntityCactoid";
            case FISH_AVATON:
                return "EntityAvaton";
            case FISH_BANSHEE:
                return "EntityBanshee";
            case FISH_GRAVE_ROBBER_GHOST:
                return "floating.EntityGraveRobberGhost";
            case FISH_SEA_HAG:
                return "floating.EntitySeaHag";
            case FISH_WRAITH:
                return "floating.EntityWraith";
            case FISH_GHOUL:
                return "EntityGhoul";
            default:
                return null;
        }
    }

    private static final class Installed {
        private static final ModContainer MOD = Loader.instance().getIndexedModList().get("mod_lavacow");
        private static final boolean SUPPORTED = MOD != null
                && new ComparableVersion(MOD.getVersion()).compareTo(new ComparableVersion(MINIMUM)) >= 0;
        private static final Class<?> FOGLET = findEntityClass("EntityFoglet");
        private static final Field SPELL_TICKS = FOGLET == null ? null : findSpellTicks();
        private static final Method HANGING = findFlag(FOGLET, "getIsHanging");
        private static final Method CLIMBING = findFlag(FOGLET, "getIsClimbing");
        private static final Method RISING = findFlag(findEntityClass("tameable.EntitySummonedZombie"), "isRising");
        private static final Class<?> FLOATING = findEntityClass("floating.EntityFloatingMob");
        private static final Method CHARGING = findFlag(FLOATING, "isCharging");
        private static final Method CASTING = findFlag(FLOATING, "isSpellcasting");
        private static final Method FLYING_SPELL_TICKS = findMethod(FLOATING, "getSpellTicks", int.class);
        private static final Method GHOUL_ATTACK_TIMER = findMethod(findEntityClass("EntityGhoul"), "getAttackTimer", int.class);
        private static final Field GHOUL_HEALTH_THRESHOLD = findGhoulHealthThreshold();

        private static Field findGhoulHealthThreshold() {
            if (!SUPPORTED) {
                return null;
            }
            try {
                Class<?> config = Class.forName("com.Fishmod.mod_LavaCow.client.Modconfig", false, Loader.instance().getModClassLoader());
                Field threshold = config.getField("Ghoul_Target_Health_Threshold");
                return threshold.getType() == double.class ? threshold : null;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
                return null;
            }
        }

        private static Class<?> findEntityClass(String name) {
            if (!SUPPORTED) {
                return null;
            }
            try {
                return Class.forName(ENTITY_PACKAGE + name, false, Loader.instance().getModClassLoader());
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                return null;
            }
        }

        private static Field findSpellTicks() {
            try {
                Field field = FOGLET.getDeclaredField("spellTicks");
                if (field.getType() == int.class) {
                    field.setAccessible(true);
                    return field;
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                // A missing provider contract leaves its spellcasting mobs under native control.
            }
            return null;
        }

        private static Method findFlag(Class<?> entityClass, String name) {
            return findMethod(entityClass, name, boolean.class);
        }

        private static Method findMethod(Class<?> entityClass, String name, Class<?> result) {
            if (entityClass == null) {
                return null;
            }
            try {
                Method flag = entityClass.getMethod(name);
                return flag.getReturnType() == result ? flag : null;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                return null;
            }
        }
    }
}
