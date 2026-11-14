package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.compat.SpecialMobsCompatibility;
import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import com.mahghuuuls.threatworks.threat.Encounter.TargetObservation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIFindEntityNearestPlayer;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;

/** Visible native ghast fireball lanes. Flight and firing remain with the mob. */
final class GhastMobPolicy implements MobPolicy {
    private final MobType type;

    GhastMobPolicy(MobType type) {
        this.type = type;
    }

    static boolean matchesType(MobType type) {
        return type == MobType.GHAST || type == MobType.SPECIAL_GHAST || type == MobType.KING_GHAST
                || type == MobType.QUEEN_GHAST;
    }

    static boolean matches(MobType type, EntityLiving mob) {
        return type == MobType.GHAST ? mob.getClass() == EntityGhast.class : SpecialMobsCompatibility.matchesGhast(type, mob);
    }

    static boolean hasNativeContract(MobType type, EntityLiving mob) {
        if (mob.isAIDisabled() || !(range(type, mob) > 0)
                || mob.tasks.isControlFlagDisabled(1) || mob.tasks.isControlFlagDisabled(2)
                || mob.tasks.taskEntries.size() != 3 || mob.targetTasks.taskEntries.size() != 1) {
            return false;
        }
        boolean vanilla = type == MobType.GHAST;
        String provider = "fathertoast.specialmobs.ai.";
        boolean attack = false;
        boolean look = false;
        boolean wander = false;
        for (EntityAITaskEntry task : mob.tasks.taskEntries) {
            Class<?> goal = task.action.getClass();
            String name = goal.getSimpleName();
            if (vanilla ? goal.getEnclosingClass() == EntityGhast.class && name.equals("AIFireballAttack")
                    : goal.getName().equals(provider + "EntityAIFlyingAttackRanged")) {
                if (attack || task.priority != (vanilla ? 7 : 4) || task.action.getMutexBits() != 0) {
                    return false;
                }
                attack = true;
            } else if (vanilla ? goal.getEnclosingClass() == EntityGhast.class && name.equals("AILookAround")
                    : goal.getName().equals(provider + "EntityAIFlyingLookIdle")) {
                if (look || task.priority != 7 || task.action.getMutexBits() != 2) {
                    return false;
                }
                look = true;
            } else if (vanilla ? goal.getEnclosingClass() == EntityGhast.class && name.equals("AIRandomFly")
                    : goal.getName().equals(provider + "EntityAIFlyingWander")) {
                if (wander || task.priority != 5 || task.action.getMutexBits() != 1) {
                    return false;
                }
                wander = true;
            } else {
                return false;
            }
        }
        if (!attack || !look || !wander) {
            return false;
        }
        EntityAITaskEntry selector = mob.targetTasks.taskEntries.iterator().next();
        return selector.priority == 1 && selector.action.getClass() == EntityAIFindEntityNearestPlayer.class
                && selector.action.getMutexBits() == 0;
    }

    static boolean isNativePhase(EntityLiving mob) {
        return mob.isRiding() || mob.isBeingRidden() || mob.isInWater() || mob.isInLava()
                || mob.world.getDifficulty() == EnumDifficulty.PEACEFUL;
    }

    @Override
    public boolean controlsImmediately(EntityLiving mob) {
        return hasNativeContract(type, mob) && !isNativePhase(mob);
    }

    @Override
    public boolean accepts(EntityLiving mob, EntityLivingBase participant) {
        return participant instanceof EntityPlayer;
    }

    @Override
    public EntityLivingBase resolve(WorldServer world, EntityLiving mob, Entity candidate) {
        if (!(candidate instanceof EntityPlayer)) {
            return null;
        }
        EntityLivingBase player = (EntityLivingBase) candidate;
        double limit = mob.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue();
        BlockPos pos = new BlockPos(player);
        return player.world == world && MobTargets.isAttackable(mob, player)
                && world.isBlockLoaded(pos) && world.getWorldBorder().contains(pos)
                && mob.getDistanceSq(player) <= limit * limit ? player : null;
    }

    @Override
    public TargetObservation observe(WorldServer world, EntityLiving mob, Entity candidate, WorkSample sample) {
        EntityLivingBase target = resolve(world, mob, candidate);
        if (target == null) {
            return TargetObservation.INELIGIBLE;
        }
        double range = range(type, mob);
        if (!withinFireballRange(mob, target, range)) {
            return TargetObservation.INELIGIBLE;
        }
        double launchOffset = launchOffset(mob);
        Vec3d position = mob.getPositionVector();
        Vec3d player = target.getPositionVector();
        Vec3d horizontal = new Vec3d(player.x - position.x, 0.0, player.z - position.z);
        LoadedPathTerrain terrain = new LoadedPathTerrain(world);
        if (!terrain.clearLine(position.add(0.0, mob.getEyeHeight(), 0.0),
                player.add(0.0, target.getEyeHeight(), 0.0))) {
            return TargetObservation.obscured(false);
        }
        // Project the candidate's firing lane. The native goal still aims and chooses when to fire.
        Vec3d origin = position.add(horizontal.normalize().scale(launchOffset)).add(0.0, mob.height / 2.0 + 0.5, 0.0);
        return terrain.clearLine(origin, player.add(0.0, target.height / 2.0, 0.0))
                ? TargetObservation.IN_REACH : TargetObservation.INELIGIBLE;
    }

    @Override
    public boolean preservesNativeTarget(WorldServer world, EntityLiving mob, EntityLivingBase target) {
        return target != null && world.getEntityFromUuid(target.getUniqueID()) == target
                && resolve(world, mob, target) != null && observe(world, mob, target, null) == TargetObservation.INELIGIBLE;
    }

    @Override
    public boolean releasesNativeWrite(WorldServer world, EntityLiving mob, EntityLivingBase target) {
        return target != null && world.getEntityFromUuid(target.getUniqueID()) == target
                && resolve(world, mob, target) != null && !withinFireballRange(mob, target, range(type, mob));
    }

    private boolean withinFireballRange(EntityLiving mob, EntityLivingBase target, double range) {
        double x = target.posX - mob.posX;
        double z = target.posZ - mob.posZ;
        double offset = launchOffset(mob);
        return mob.getDistanceSq(target) < range * range && x * x + z * z > offset * offset;
    }

    private double launchOffset(EntityLiving mob) {
        return type == MobType.GHAST ? 4.0 : mob.width;
    }

    private static double range(MobType type, EntityLiving mob) {
        return type == MobType.GHAST ? 64.0 : SpecialMobsCompatibility.ghastRange(mob);
    }
}
