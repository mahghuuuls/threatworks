package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.threat.Encounter.TargetObservation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackRanged;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

/** Eligibility for the native center-head ranged goal and its direct flight. */
final class WitherMobPolicy implements MobPolicy {
    private static final float RANGED_REACH_SQUARED = 20.0F * 20.0F;
    private static final double CENTER_HEAD_HEIGHT = 3.0;

    static boolean hasNativeContract(EntityWither wither) {
        WitherCenterGoal observed = (WitherCenterGoal) wither;
        observed.threatworks$centerGoal(null);
        if (!rangedAiEnabled(wither)) {
            return false;
        }
        EntityAITaskEntry center = null;
        for (EntityAITaskEntry task : wither.tasks.taskEntries) {
            if (task.action instanceof EntityAIAttackRanged) {
                if (center != null || task.action.getClass() != EntityAIAttackRanged.class || task.priority != 2
                        || task.action.getMutexBits() != 3 || !RangedGoal.matches(task, wither)) {
                    return false;
                }
                center = task;
            } else if (task.priority <= 2 && (task.action.getMutexBits() & 3) != 0
                    && !(task.priority == 1 && task.action.getClass() == EntityAISwimming.class)
                    && !isSpawnGoal(task)) {
                return false;
            }
        }
        observed.threatworks$centerGoal(center);
        return center != null;
    }

    static boolean confirmsCenter(EntityWither wither, EntityLivingBase target) {
        EntityAITaskEntry goal = ((WitherCenterGoal) wither).threatworks$centerGoal();
        if (goal == null || !wither.tasks.taskEntries.contains(goal)) {
            return false;
        }
        int watched = wither.getWatchedTargetId(0);
        return target == null ? watched == 0 && !goal.using
                : watched == target.getEntityId() && goal.using && RangedGoal.aimsAt(goal, target);
    }

    private static boolean rangedAiEnabled(EntityWither wither) {
        return wither.isEntityAlive() && !wither.isAIDisabled() && !wither.tasks.isControlFlagDisabled(3);
    }

    private static boolean isSpawnGoal(EntityAITaskEntry task) {
        Class<?> goal = task.action.getClass();
        return task.priority == 0 && goal.getEnclosingClass() == EntityWither.class
                && goal.getSimpleName().equals("AIDoNothing");
    }

    private static final class RangedGoal {
        private static final Field HOST = field("rangedAttackEntityHost", "field_82641_b");
        private static final Field DISTANCE_SQUARED = field("maxAttackDistance", "field_82642_h");
        private static final Field ATTACK_TARGET = field("attackTarget", "field_75323_c");

        private static Field field(String mcp, String srg) {
            try {
                return ReflectionHelper.findField(EntityAIAttackRanged.class, mcp, srg);
            } catch (RuntimeException unavailable) {
                return null;
            }
        }

        static boolean matches(EntityAITaskEntry task, EntityWither wither) {
            try {
                return HOST != null && DISTANCE_SQUARED != null && HOST.get(task.action) == wither
                        && DISTANCE_SQUARED.getFloat(task.action) == RANGED_REACH_SQUARED;
            } catch (IllegalAccessException unavailable) {
                return false;
            }
        }

        static boolean aimsAt(EntityAITaskEntry task, EntityLivingBase target) {
            try {
                return ATTACK_TARGET != null && ATTACK_TARGET.get(task.action) == target;
            } catch (IllegalAccessException unavailable) {
                return false;
            }
        }
    }

    static boolean isNativePhase(EntityWither wither) {
        return wither.getInvulTime() > 0 || wither.isRiding() || wither.isBeingRidden()
                || wither.isInWater() || wither.isInLava()
                || wither.world.getDifficulty() == EnumDifficulty.PEACEFUL;
    }

    @Override
    public boolean controlsImmediately(EntityLiving mob) {
        EntityWither wither = (EntityWither) mob;
        return rangedAiEnabled(wither) && !isNativePhase(wither);
    }

    @Override
    public boolean accepts(EntityLiving mob, EntityLivingBase participant) {
        return participant instanceof EntityPlayer
                || (participant.attackable() && participant.getCreatureAttribute() != EnumCreatureAttribute.UNDEAD);
    }

    @Override
    public EntityLivingBase resolve(WorldServer world, EntityLiving mob, Entity candidate) {
        if (!(candidate instanceof EntityLivingBase)) {
            return null;
        }
        EntityLivingBase target = (EntityLivingBase) candidate;
        BlockPos pos = new BlockPos(target);
        double range = mob.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue();
        return MobTargets.isAttackable(mob, target) && accepts(mob, target) && world.isBlockLoaded(pos)
                && world.getWorldBorder().contains(pos) && mob.getDistanceSq(target) <= range * range ? target : null;
    }

    @Override
    public TargetObservation observe(WorldServer world, EntityLiving mob, Entity candidate, WorkSample sample) {
        EntityLivingBase target = resolve(world, mob, candidate);
        if (target == null) {
            return TargetObservation.INELIGIBLE;
        }
        Vec3d origin = mob.getPositionVector();
        Vec3d feet = new Vec3d(target.posX, target.getEntityBoundingBox().minY, target.posZ);
        double distanceSquared = origin.squareDistanceTo(feet);
        boolean inReach = distanceSquared <= RANGED_REACH_SQUARED;
        LoadedPathTerrain terrain = new LoadedPathTerrain(world);
        Vec3d eye = origin.add(0, mob.getEyeHeight(), 0);
        Vec3d targetEye = target.getPositionVector().add(0, target.getEyeHeight(), 0);
        Vec3d aim = target.getPositionVector().add(0, target.getEyeHeight() * 0.5, 0);
        if (!terrain.lineLoaded(eye, targetEye)) {
            return TargetObservation.INELIGIBLE;
        }
        boolean visible = world.rayTraceBlocks(eye, targetEye, false, true, false) == null;
        if (!visible && inReach) {
            return TargetObservation.obscured(true);
        }
        if (inReach && terrain.clearLine(origin.add(0, CENTER_HEAD_HEIGHT, 0), aim)) {
            return TargetObservation.IN_REACH;
        }
        // The watched center target drives native X/Z flight and a health-dependent hover height.
        double hover = ((EntityWither) mob).isArmored() ? 0 : 5;
        Vec3d horizontal = new Vec3d(feet.x - origin.x, 0, feet.z - origin.z);
        double run = Math.max(0, horizontal.length() - 3);
        double rise = Math.max(0, target.posY + hover - origin.y);
        Vec3d approach = horizontal.normalize().scale(run).add(0, rise, 0);
        if (sample != null) {
            sample.pathQuery();
        }
        Vec3d destination = origin.add(approach);
        if (approach.lengthSquared() == 0 || destination.squareDistanceTo(feet) > RANGED_REACH_SQUARED
                || !terrain.clearFlight(mob.getEntityBoundingBox(), approach)
                || !terrain.clearLine(destination.add(0, CENTER_HEAD_HEIGHT, 0), aim)) {
            return TargetObservation.INELIGIBLE;
        }
        return visible ? TargetObservation.pursuit(Math.sqrt(distanceSquared)) : TargetObservation.obscured(false);
    }
}
