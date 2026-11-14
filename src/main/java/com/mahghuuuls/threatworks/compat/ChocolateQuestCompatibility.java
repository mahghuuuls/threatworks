package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAILeapAtTarget;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.item.ItemSword;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.UUID;

public final class ChocolateQuestCompatibility {
    private static final String ROOT = "team.cqr.cqrepoured.entity.";
    private static final String AI = ROOT + "ai.";
    private static final String MINIMUM = "2.8.0B";
    private static final Logger LOGGER = LogManager.getLogger("threatworks");
    private static final EnumMap<MobType, String> GROUND_MELEE = new EnumMap<>(MobType.class);
    private static Binding installed;
    private static boolean invocationFailed;

    static {
        GROUND_MELEE.put(MobType.CQR_BOARMAN, "Boarman");
        GROUND_MELEE.put(MobType.CQR_GREMLIN, "Gremlin");
        GROUND_MELEE.put(MobType.CQR_ILLAGER, "Illager");
        GROUND_MELEE.put(MobType.CQR_MANDRIL, "Mandril");
        GROUND_MELEE.put(MobType.CQR_MINOTAUR, "Minotaur");
        GROUND_MELEE.put(MobType.CQR_MUMMY, "Mummy");
        GROUND_MELEE.put(MobType.CQR_OGRE, "Ogre");
        GROUND_MELEE.put(MobType.CQR_ORC, "Orc");
        GROUND_MELEE.put(MobType.CQR_PIRATE, "Pirate");
        GROUND_MELEE.put(MobType.CQR_SKELETON, "Skeleton");
        GROUND_MELEE.put(MobType.CQR_SPECTRE, "Spectre");
        GROUND_MELEE.put(MobType.CQR_WALKER, "Walker");
        GROUND_MELEE.put(MobType.CQR_ZOMBIE, "Zombie");
    }

    private ChocolateQuestCompatibility() {
    }

    public static void initialize() {
        installed = bind();
    }

    public static boolean matches(MobType type, EntityCreature mob) {
        String name = GROUND_MELEE.get(type);
        return name != null && installed != null && !invocationFailed && installed.base.isInstance(mob)
                && mob.getClass().getName().equals(ROOT + "mobs.EntityCQR" + name);
    }

    public static boolean isGroundMelee(MobType type) {
        return GROUND_MELEE.containsKey(type);
    }

    public static boolean hasGroundContract(MobType type, EntityCreature mob) {
        if (!mob.getNavigator().getClass().getName().equals(ROOT + "pathfinding.PathNavigateGroundCQR")) {
            return false;
        }
        if (type != MobType.CQR_MANDRIL) {
            return true;
        }
        boolean foundLeap = false;
        for (EntityAITaskEntry task : mob.tasks.taskEntries) {
            if (task.action instanceof EntityAILeapAtTarget) {
                if (foundLeap || !isMandrilLeap(task)) {
                    return false;
                }
                foundLeap = true;
            }
        }
        return foundLeap;
    }

    public static boolean isAttackGoal(Class<?> goal) {
        return goal.getName().equals(AI + "attack.EntityAIAttack");
    }

    public static boolean isNativeGoal(MobType type, EntityAITaskEntry task) {
        String name = task.action.getClass().getName();
        switch (task.priority) {
            case 0: return name.equals("net.minecraft.entity.ai.EntityAISwimming");
            case 1: return name.equals(AI + "EntityAIOpenCloseDoor");
            case 8: return name.equals(AI + "EntityAIRideHorse");
            case 9: return name.equals(AI + "item.EntityAIHealingPotion")
                    || type == MobType.CQR_MANDRIL && isMandrilLeap(task);
            case 11: return name.equals(AI + "spells.EntityAISpellHandler")
                    || name.equals("team.cqr.cqrepoured.integration.ebwizardry.EntityAICastSpell");
            case 12: return name.equals(AI + "attack.special.EntityAIAttackSpecial");
            case 13: return name.equals(AI + "attack.EntityAIAttackRanged");
            case 14: return name.equals(AI + "item.EntityAIPotionThrower");
            case 15: return name.equals(AI + "item.EntityAIFireball");
            case 16: return name.equals(AI + "attack.special.EntityAIHooker");
            case 17: return name.equals(AI + "attack.EntityAIBackstab");
            case 19: return name.equals(AI + "item.EntityAICursedBoneSummoner");
            case 20: return name.equals(AI + "EntityAIFollowAttackTarget");
            case 22: return name.equals(AI + "EntityAIFireFighter");
            case 23: return name.equals(AI + "EntityAITorchIgniter");
            case 24: return name.equals(AI + "attack.special.EntityAILooter");
            case 25: return name.equals(AI + "EntityAITameAndLeashPet");
            case 26: return name.equals(AI + "EntityAISearchMount");
            case 30: return name.equals(AI + "EntityAIMoveToLeader");
            case 31: return name.equals(AI + "EntityAIFollowPath");
            case 32: return name.equals(AI + "EntityAIMoveToHome");
            case 33: return name.equals(AI + "EntityAIIdleSit");
            default: return false;
        }
    }

    private static boolean isMandrilLeap(EntityAITaskEntry task) {
        return task.priority == 9 && task.action.getClass() == EntityAILeapAtTarget.class
                && task.action.getMutexBits() == 5;
    }

    public static boolean usesNativeGoal(MobType type, EntityAITaskEntry task) {
        return isNativeGoal(type, task) || (task.action.getMutexBits() & 3) != 0 && !isAttackGoal(task.action.getClass());
    }

    public static boolean yieldsToNative(EntityCreature mob) {
        if (mob.isRiding() || mob.isBeingRidden() || mob.isInWater() || mob.isInLava()
                || !mob.getHeldItemOffhand().isEmpty()
                || !(mob.getHeldItemMainhand().isEmpty() || mob.getHeldItemMainhand().getItem().getClass() == ItemSword.class)) {
            return true;
        }
        Binding binding = installed;
        try {
            return binding == null || invocationFailed || (UUID) binding.leader.invokeExact(mob) != null
                    || (boolean) binding.sitting.invokeExact(mob) || (boolean) binding.chatting.invokeExact(mob)
                    || (boolean) binding.charging.invokeExact(mob) || (boolean) binding.casting.invokeExact(mob)
                    || (boolean) binding.potion.invokeExact(mob);
        } catch (Throwable failure) {
            return failClosed(failure, true);
        }
    }

    public static boolean acceptsTarget(EntityCreature mob, EntityLivingBase target) {
        Binding binding = installed;
        try {
            return binding != null && !invocationFailed && (boolean) binding.enemy.invokeExact((EntityLivingBase) mob, target);
        } catch (Throwable failure) {
            return failClosed(failure, false);
        }
    }

    public static boolean inNativeReach(EntityCreature mob, EntityLivingBase target) {
        Binding binding = installed;
        try {
            return binding != null && !invocationFailed && (boolean) binding.reach.invokeExact(mob, target);
        } catch (Throwable failure) {
            return failClosed(failure, false);
        }
    }

    private static boolean failClosed(Throwable failure, boolean fallback) {
        if (failure instanceof Error && !(failure instanceof LinkageError)) {
            throw (Error) failure;
        }
        if (!invocationFailed) {
            invocationFailed = true;
            LOGGER.warn("Disabling Chocolate Quest Repoured targeting because its native melee contract failed.", failure);
        }
        return fallback;
    }

    private static Binding bind() {
        ModContainer provider = Loader.instance().getIndexedModList().get("cqrepoured");
        if (provider == null) {
            return null;
        }
        if (new ComparableVersion(provider.getVersion()).compareTo(new ComparableVersion(MINIMUM)) < 0) {
            LOGGER.warn("Chocolate Quest Repoured targeting unavailable for version {}. Update to at least {}.",
                    provider.getVersion(), MINIMUM);
            return null;
        }
        try {
            ClassLoader loader = Loader.instance().getModClassLoader();
            Class<?> base = Class.forName(ROOT + "bases.AbstractEntityCQR", false, loader).asSubclass(EntityCreature.class);
            Class<?> targets = Class.forName(AI + "target.TargetUtil", false, loader);
            return new Binding(base, targets);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            LOGGER.warn("Chocolate Quest Repoured melee contract could not be bound.", failure);
            return null;
        }
    }

    private static final class Binding {
        private final Class<?> base;
        private final MethodHandle leader;
        private final MethodHandle sitting;
        private final MethodHandle chatting;
        private final MethodHandle charging;
        private final MethodHandle casting;
        private final MethodHandle potion;
        private final MethodHandle enemy;
        private final MethodHandle reach;

        private Binding(Class<?> base, Class<?> targets) throws ReflectiveOperationException {
            this.base = base;
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Field leaderId = base.getDeclaredField("leaderUUID");
            leaderId.setAccessible(true);
            leader = lookup.unreflectGetter(leaderId).asType(MethodType.methodType(UUID.class, EntityCreature.class));
            sitting = state(lookup, base, "isSitting");
            chatting = state(lookup, base, "isChatting");
            charging = state(lookup, base, "isSpellCharging");
            casting = state(lookup, base, "isSpellCasting");
            potion = state(lookup, base, "isHoldingPotion");
            enemy = lookup.findStatic(targets, "isEnemyCheckingLeaders",
                    MethodType.methodType(boolean.class, EntityLivingBase.class, EntityLivingBase.class));
            reach = lookup.findVirtual(base, "isInAttackReach", MethodType.methodType(boolean.class, EntityLivingBase.class))
                    .asType(MethodType.methodType(boolean.class, EntityCreature.class, EntityLivingBase.class));
        }

        private static MethodHandle state(MethodHandles.Lookup lookup, Class<?> type, String name) throws ReflectiveOperationException {
            return lookup.findVirtual(type, name, MethodType.methodType(boolean.class))
                    .asType(MethodType.methodType(boolean.class, EntityCreature.class));
        }
    }
}
