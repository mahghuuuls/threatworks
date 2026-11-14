package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;

import java.lang.reflect.Method;
import java.lang.reflect.Field;

/** Shared ground melee and native spell phases of the TestPACK Fish's Undead Rising mobs. */
public final class FishUndeadRisingCompatibility {
    private static final String MINIMUM = "1.6.2";
    private static final String ENTITY_PACKAGE = "com.Fishmod.mod_LavaCow.entities.";

    private FishUndeadRisingCompatibility() {
    }

    public static boolean matches(MobType type, EntityCreature mob) {
        String entityClass = entityClass(type);
        return entityClass != null && Installed.SUPPORTED && mob.getClass().getName().equals(ENTITY_PACKAGE + entityClass)
                && (!usesSpells(type) || Installed.SPELL_TICKS != null
                && Installed.SPELL_TICKS.getDeclaringClass().isInstance(mob))
                && (!canClimb(type) || Installed.HANGING != null && Installed.CLIMBING != null);
    }

    public static int meleePriority(MobType type) {
        return type == MobType.FISH_PINGU ? 2 : usesSpells(type) ? 4 : -1;
    }

    public static boolean canUseGroundMelee(MobType type, EntityCreature mob) {
        return meleePriority(type) < 0 || !mob.isRiding() && !mob.isBeingRidden() && !mob.isInWater();
    }

    public static int nativeGoal(MobType type, EntityAITaskEntry entry) {
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
        return !usesSpells(type) || (goals & 3) == 3;
    }

    public static boolean nativePhase(MobType type, EntityCreature mob) {
        if (!usesSpells(type)) {
            return false;
        }
        try {
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
            default:
                return null;
        }
    }

    private static final class Installed {
        private static final ModContainer MOD = Loader.instance().getIndexedModList().get("mod_lavacow");
        private static final boolean SUPPORTED = MOD != null
                && new ComparableVersion(MOD.getVersion()).compareTo(new ComparableVersion(MINIMUM)) >= 0;
        private static final Class<?> FOGLET = SUPPORTED ? findFoglet() : null;
        private static final Field SPELL_TICKS = FOGLET == null ? null : findSpellTicks();
        private static final Method HANGING = FOGLET == null ? null : findFlag("getIsHanging");
        private static final Method CLIMBING = FOGLET == null ? null : findFlag("getIsClimbing");

        private static Class<?> findFoglet() {
            try {
                return Class.forName(ENTITY_PACKAGE + "EntityFoglet", false, Loader.instance().getModClassLoader());
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

        private static Method findFlag(String name) {
            try {
                Method flag = FOGLET.getMethod(name);
                return flag.getReturnType() == boolean.class ? flag : null;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                return null;
            }
        }
    }
}
