package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** The exact faction roles and live native attack reach of Ancient Warfare NPCs. */
public final class AncientWarfareCompatibility {
    private static final String NPC = "net.shadowmage.ancientwarfare.npc.";
    private static final String FACTION = NPC + "entity.faction.NpcFaction";
    private static final String AI = NPC + "ai.";
    private static final String MELEE = AI + "NpcAIAttackMeleeLongRange";
    private static final String ARCHERY = AI + "faction.NpcAIFactionRangedAttack";
    private static final String MINIMUM = "1.12.2-2.9.20";

    private AncientWarfareCompatibility() {
    }

    public static void reportVersion(Logger logger) {
        if (Installed.MOD == null) {
            return;
        }
        if (!Installed.SUPPORTED) {
            logger.warn("Ancient Warfare targeting unavailable for version {}. Update to at least {}.",
                    Installed.MOD.getVersion(), MINIMUM);
        } else if (Installed.HOSTILE == null || Installed.CAN_TARGET == null || Installed.SLEEPING == null
                || Installed.FOLLOWING == null || Installed.MELEE_REACH == null || Installed.ARROW_RANGE == null) {
            logger.warn("Some Ancient Warfare targeting is unavailable because its native combat contract could not be bound.");
        }
    }

    private static String role(MobType type) {
        switch (type) {
            case ANCIENT_WARFARE_SOLDIER:
                return "Soldier";
            case ANCIENT_WARFARE_SOLDIER_ELITE:
                return "SoldierElite";
            case ANCIENT_WARFARE_LEADER:
                return "Leader";
            case ANCIENT_WARFARE_LEADER_ELITE:
                return "LeaderElite";
            case ANCIENT_WARFARE_ARCHER:
                return "Archer";
            case ANCIENT_WARFARE_ARCHER_ELITE:
                return "ArcherElite";
            default:
                return null;
        }
    }

    public static boolean isSupportedType(MobType type) {
        return type != null && role(type) != null;
    }

    public static boolean isArcher(MobType type) {
        return type == MobType.ANCIENT_WARFARE_ARCHER || type == MobType.ANCIENT_WARFARE_ARCHER_ELITE;
    }

    public static boolean matches(MobType type, EntityCreature mob) {
        return isSupportedType(type) && Installed.HOSTILE != null && Installed.CAN_TARGET != null
                && Installed.SLEEPING != null && Installed.FOLLOWING != null
                && (isArcher(type) ? Installed.ARROW_RANGE != null : Installed.MELEE_REACH != null)
                && mob.getClass().getName().equals(FACTION + role(type));
    }

    public static boolean hasNavigator(EntityCreature mob) {
        return mob.getNavigator().getClass().getName().equals(AI + "NpcNavigator");
    }

    public static boolean isAttackGoal(MobType type, EntityAITaskEntry entry) {
        boolean archer = isArcher(type);
        return entry.priority == 3 && entry.action.getMutexBits() == (archer ? 0 : 3)
                && entry.action.getClass().getName().equals(archer ? ARCHERY : MELEE);
    }

    public static boolean isSelector(EntityAITaskEntry entry) {
        String name = entry.action.getClass().getName();
        return entry.action.getMutexBits() == 1 && (entry.priority == 1 && name.equals(AI + "faction.NpcAIFactionHurt")
                || entry.priority == 2 && name.equals(AI + "NpcAIAttackNearest"));
    }

    public static boolean isNativeTask(EntityAITaskEntry entry) {
        String goal = entry.action.getClass().getName();
        int mutex = entry.action.getMutexBits();
        return mutex == 0 && (entry.priority == 0 && goal.equals(AI + "NpcAIDoor")
                || entry.priority == 1 && goal.equals(AI + "faction.NpcAIFactionCommander")
                || entry.priority == 2 && goal.equals(AI + "faction.NpcAIFactionRestrictSun")
                || entry.priority == 3 && goal.equals(AI + "faction.NpcAIFactionFleeSun"))
                || mutex == 1 && entry.priority == 1 && goal.equals(AI + "NpcAIFollowPlayer")
                || mutex == 3 && ((entry.priority == 2 || entry.priority == 4) && goal.equals(AI + "NpcAIMoveHome")
                || entry.priority == 2 && goal.equals(AI + "faction.NpcAIFactionArcherStayAtHome"));
    }

    public static boolean nativeControl(EntityCreature mob) {
        try {
            return (boolean) Installed.SLEEPING.invokeExact(mob)
                    || (EntityLivingBase) Installed.FOLLOWING.invokeExact(mob) != null;
        } catch (Throwable failure) {
            return true;
        }
    }

    public static boolean hostileToPlayer(EntityCreature mob, EntityLivingBase target) {
        return target instanceof EntityPlayer && test(Installed.HOSTILE, mob, target);
    }

    public static boolean canTarget(EntityCreature mob, EntityLivingBase target) {
        return test(Installed.CAN_TARGET, mob, target);
    }

    private static boolean test(MethodHandle method, EntityCreature mob, Entity target) {
        try {
            return method != null && (boolean) method.invokeExact(mob, target);
        } catch (Throwable failure) {
            return false;
        }
    }

    public static boolean withinNativeReach(MobType type, EntityCreature mob, EntityLivingBase player) {
        BlockPos home = mob.getHomePosition();
        double homeDistance = Math.sqrt(mob.getDistanceSq(home));
        double followRange = mob.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue();
        double adjustedRange = Math.max(followRange / (homeDistance / 20.0), 3.0);
        if (mob.getDistance(player) >= adjustedRange || isArcher(type) && mob.hasHome()
                && player.getDistanceSq(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > 900.0) {
            return false;
        }
        for (EntityAITaskEntry entry : mob.tasks.taskEntries) {
            if (!isAttackGoal(type, entry)) {
                continue;
            }
            try {
                EntityAIBase goal = entry.action;
                double distance = mob.getDistanceSq(player.posX, player.getEntityBoundingBox().minY, player.posZ);
                if (isArcher(type)) {
                    int rangeSquared = (int) Installed.ARROW_RANGE.invokeExact(goal);
                    return distance <= (rangeSquared == -1 ? (int) (followRange * followRange) : rangeSquared);
                }
                float weaponReach = (float) Installed.MELEE_REACH.invokeExact(goal);
                double reach = weaponReach + (mob.width + player.width) / 2.0;
                return reach > 0.0 && distance <= reach * reach;
            } catch (Throwable failure) {
                return false;
            }
        }
        return false;
    }

    private static final class Installed {
        private static final ModContainer MOD = Loader.instance().getIndexedModList().get("ancientwarfarenpc");
        private static final boolean SUPPORTED = MOD != null
                && new ComparableVersion(MOD.getVersion()).compareTo(new ComparableVersion(MINIMUM)) >= 0;
        private static final MethodHandle HOSTILE = method("isHostileTowards", boolean.class, Entity.class);
        private static final MethodHandle CAN_TARGET = method("canTarget", boolean.class, Entity.class);
        private static final MethodHandle SLEEPING = method("isSleeping", boolean.class);
        private static final MethodHandle FOLLOWING = method("getFollowingEntity", EntityLivingBase.class);
        private static final MethodHandle MELEE_REACH = field(MELEE, "attackReach", float.class);
        private static final MethodHandle ARROW_RANGE = field(ARCHERY, "attackDistanceSq", int.class);

        private static MethodHandle method(String name, Class<?> result, Class<?>... arguments) {
            if (!SUPPORTED) {
                return null;
            }
            try {
                Class<?> owner = Class.forName(FACTION, false, Loader.instance().getModClassLoader());
                Method method = owner.getMethod(name, arguments);
                return MethodHandles.publicLookup().unreflect(method)
                        .asType(MethodType.methodType(result, EntityCreature.class, arguments));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                return null;
            }
        }

        private static MethodHandle field(String ownerName, String name, Class<?> value) {
            if (!SUPPORTED) {
                return null;
            }
            try {
                Class<?> owner = Class.forName(ownerName, false, Loader.instance().getModClassLoader());
                Field field = owner.getDeclaredField(name);
                if (field.getType() == value) {
                    field.setAccessible(true);
                    return MethodHandles.lookup().unreflectGetter(field).asType(MethodType.methodType(value, EntityAIBase.class));
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                return null;
            }
            return null;
        }
    }
}
