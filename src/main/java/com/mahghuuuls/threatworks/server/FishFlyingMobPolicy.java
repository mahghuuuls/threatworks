package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.compat.FishUndeadRisingCompatibility;
import com.mahghuuuls.threatworks.compat.SpecialAiCompatibility;
import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import com.mahghuuuls.threatworks.threat.Encounter.TargetObservation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAIFleeSun;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIMoveThroughVillage;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

/** Short, open native flight between committed charges and spells. */
final class FishFlyingMobPolicy implements MobPolicy {
    private static final double MAX_DISTANCE_SQUARED = 144.0;
    private static final int CHARGE = 1;
    private static final int CASTING = 2;
    private static final int SPELL = 4;
    private static final int REQUIRED_GOALS = CHARGE | CASTING | SPELL;
    private static final Field NEAREST_CLASS = nearestClass();
    private final MobType type;

    FishFlyingMobPolicy(MobType type) {
        this.type = type;
    }

    static boolean hasNativeContract(MobType type, EntityCreature mob) {
        if (mob.isAIDisabled() || mob.tasks.isControlFlagDisabled(1) || mob.tasks.isControlFlagDisabled(2)
                || mob.getNavigator().getClass() != PathNavigateGround.class
                || !FishUndeadRisingCompatibility.hasFlyingMoveHelper(mob)) {
            return false;
        }
        int goals = 0;
        for (EntityAITaskEntry entry : mob.tasks.taskEntries) {
            int attack = attackGoal(type, entry);
            if (attack != 0) {
                if ((goals & attack) != 0) {
                    return false;
                }
                goals |= attack;
            } else if (!isUtility(type, entry)) {
                return false;
            }
        }
        int retaliation = 0;
        int nearest = 0;
        for (EntityAITaskEntry entry : mob.targetTasks.taskEntries) {
            if (entry.action.getMutexBits() != 1) {
                return false;
            }
            if (entry.priority == 1 && entry.action.getClass() == EntityAIHurtByTarget.class) {
                retaliation++;
            } else if (entry.priority == 2 && entry.action.getClass() == EntityAINearestAttackableTarget.class
                    && selectsPlayers(entry)) {
                nearest++;
            } else {
                return false;
            }
        }
        // Banshee registers its native target pair from both the base and subclass initialization.
        int expectedPairs = type == MobType.FISH_BANSHEE ? 2 : 1;
        return goals == REQUIRED_GOALS && retaliation == expectedPairs && nearest == expectedPairs;
    }

    private static Field nearestClass() {
        try {
            return ReflectionHelper.findField(EntityAINearestAttackableTarget.class, "targetClass", "field_75307_b");
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private static boolean selectsPlayers(EntityAITaskEntry entry) {
        try {
            return NEAREST_CLASS != null && NEAREST_CLASS.get(entry.action) == EntityPlayer.class;
        } catch (IllegalAccessException unavailable) {
            return false;
        }
    }

    private static int attackGoal(MobType type, EntityAITaskEntry entry) {
        if (FishUndeadRisingCompatibility.isChargeGoal(type, entry)) {
            return CHARGE;
        }
        if (FishUndeadRisingCompatibility.isCastingGoal(entry)) {
            return CASTING;
        }
        return FishUndeadRisingCompatibility.isFlyingSpellGoal(type, entry) ? SPELL : 0;
    }

    private static boolean isUtility(MobType type, EntityAITaskEntry entry) {
        Class<?> goal = entry.action.getClass();
        int priority = entry.priority;
        int mutex = entry.action.getMutexBits();
        return FishUndeadRisingCompatibility.isFlyingWanderGoal(entry)
                || SpecialAiCompatibility.isCombatReaction(entry)
                || priority == 0 && mutex == 4 && goal == EntityAISwimming.class
                || priority == 4 && mutex == 3 && goal == EntityAIAttackMelee.class
                || priority == 5 && mutex == 1 && goal == EntityAIFleeSun.class
                || priority == 6 && mutex == 1 && type == MobType.FISH_AVATON && goal == EntityAIMoveThroughVillage.class
                || priority == 8 && (mutex == 2 && goal == EntityAIWatchClosest.class
                || mutex == 3 && goal == EntityAILookIdle.class);
    }

    static boolean isNativePhase(EntityCreature mob) {
        if (GhastMobPolicy.isNativePhase(mob) || mob.isBurning() || mob.isPotionActive(MobEffects.LEVITATION)
                || FishUndeadRisingCompatibility.flyingAttackCommitted(mob)) {
            return true;
        }
        for (EntityAITaskEntry entry : mob.tasks.taskEntries) {
            if (entry.using && (entry.action.getClass() == EntityAIFleeSun.class
                    || entry.action.getClass() == EntityAIMoveThroughVillage.class
                    || SpecialAiCompatibility.isCombatReaction(entry))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean controlsImmediately(EntityLiving mob) {
        EntityCreature flyer = (EntityCreature) mob;
        return hasNativeContract(type, flyer) && !isNativePhase(flyer);
    }

    @Override
    public boolean accepts(EntityLiving mob, EntityLivingBase participant) {
        return participant instanceof EntityPlayer;
    }

    @Override
    public EntityLivingBase resolve(WorldServer world, EntityLiving mob, Entity candidate) {
        return candidate instanceof EntityPlayer ? MobTargets.resolve(world, (EntityCreature) mob, candidate, type) : null;
    }

    @Override
    public TargetObservation observe(WorldServer world, EntityLiving mob, Entity candidate, WorkSample sample) {
        EntityLivingBase target = resolve(world, mob, candidate);
        if (target == null || mob.getDistanceSq(target) > MAX_DISTANCE_SQUARED) {
            return TargetObservation.INELIGIBLE;
        }
        if (mob.getEntityBoundingBox().intersects(target.getEntityBoundingBox())) {
            return TargetObservation.IN_REACH;
        }
        // The airborne charge starts farther than two blocks. Grounded native melee can still connect.
        if (mob.getDistanceSq(target) <= 4.0) {
            double reach = mob.width * 2.0;
            return mob.onGround && target.onGround && mob.getDistanceSq(target) <= reach * reach + target.width
                    ? TargetObservation.IN_REACH : TargetObservation.INELIGIBLE;
        }
        LoadedPathTerrain terrain = new LoadedPathTerrain(world);
        Vec3d aim = target.getPositionEyes(1.0F);
        if (!terrain.clearLine(mob.getPositionEyes(1.0F), aim)) {
            return TargetObservation.obscured(false);
        }
        if (sample != null) {
            sample.pathQuery();
        }
        Vec3d travel = aim.subtract(mob.getPositionVector());
        return terrain.clearFlight(mob.getEntityBoundingBox(), travel)
                ? TargetObservation.pursuit(mob.getDistance(target)) : TargetObservation.INELIGIBLE;
    }

    @Override
    public boolean preservesNativeTarget(WorldServer world, EntityLiving mob, EntityLivingBase target) {
        return target != null && world.getEntityFromUuid(target.getUniqueID()) == target
                && resolve(world, mob, target) != null && !observe(world, mob, target, null).isEligible();
    }

    @Override
    public boolean releasesNativeWrite(WorldServer world, EntityLiving mob, EntityLivingBase target) {
        return target != null && world.getEntityFromUuid(target.getUniqueID()) == target
                && resolve(world, mob, target) != null && mob.getDistanceSq(target) > MAX_DISTANCE_SQUARED;
    }
}
