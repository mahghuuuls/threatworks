package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import com.mahghuuuls.threatworks.threat.Encounter.TargetObservation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.monster.EntityElderGuardian;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.pathfinding.PathNavigateSwimmer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

/** Eligibility and commitment for the native synchronized beam. */
final class GuardianMobPolicy implements MobPolicy {
    static boolean matchesType(MobType type) {
        return type == MobType.GUARDIAN || type == MobType.ELDER_GUARDIAN;
    }

    static boolean hasNativeContract(EntityGuardian guardian) {
        if (!beamAiEnabled(guardian) || guardian.getNavigator().getClass() != PathNavigateSwimmer.class) {
            return false;
        }
        boolean beam = false;
        for (EntityAITaskEntry task : guardian.tasks.taskEntries) {
            Class<?> goal = task.action.getClass();
            if (goal.getEnclosingClass() == EntityGuardian.class && goal.getSimpleName().equals("AIGuardianAttack")) {
                if (beam || task.priority != 4 || task.action.getMutexBits() != 3) {
                    return false;
                }
                beam = true;
            } else if (task.priority <= 4 && (task.action.getMutexBits() & 3) != 0) {
                return false;
            }
        }
        return beam;
    }

    private static boolean beamAiEnabled(EntityLiving guardian) {
        return guardian.isEntityAlive() && !guardian.isAIDisabled() && !guardian.tasks.isControlFlagDisabled(3);
    }

    static boolean isNativePhase(EntityGuardian guardian) {
        return guardian.hasTargetedEntity() || guardian.isRiding() || guardian.isBeingRidden();
    }

    @Override
    public boolean controlsImmediately(EntityLiving mob) {
        return beamAiEnabled(mob) && !isNativePhase((EntityGuardian) mob);
    }

    @Override
    public boolean keepsEncounterDuringNativePhase() {
        return true;
    }

    @Override
    public boolean protectsCommittedTarget(EntityLiving mob) {
        return ((EntityGuardian) mob).hasTargetedEntity();
    }

    @Override
    public boolean blocksCommittedWrite(WorldServer world, EntityLiving mob, EntityLivingBase requested) {
        // Allow native invalidation, while keeping every replacement out until the beam ID clears.
        return requested != null || resolve(world, mob, effectiveTarget(mob)) != null;
    }

    @Override
    public EntityLivingBase effectiveTarget(EntityLiving mob) {
        int id = ((GuardianBeam) mob).threatworks$beamTargetId();
        if (id == 0) {
            return mob.getAttackTarget();
        }
        Entity target = mob.world.getEntityByID(id);
        return target instanceof EntityLivingBase ? (EntityLivingBase) target : null;
    }

    @Override
    public boolean accepts(EntityLiving mob, EntityLivingBase participant) {
        return participant.attackable() && !(participant instanceof EntityGuardian);
    }

    @Override
    public EntityLivingBase resolve(WorldServer world, EntityLiving mob, Entity candidate) {
        if (!(candidate instanceof EntityLivingBase)) {
            return null;
        }
        EntityLivingBase target = (EntityLivingBase) candidate;
        BlockPos position = new BlockPos(target);
        double range = mob.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue();
        return MobTargets.isAttackable(mob, target) && accepts(mob, target)
                && world.isBlockLoaded(position) && world.getWorldBorder().contains(position)
                && mob.getDistanceSq(target) <= range * range ? target : null;
    }

    @Override
    public TargetObservation observe(WorldServer world, EntityLiving mob, Entity candidate, WorkSample sample) {
        EntityLivingBase target = resolve(world, mob, candidate);
        if (target == null) {
            return TargetObservation.INELIGIBLE;
        }
        double range = mob.getDistanceSq(target);
        boolean continuing = protectsCommittedTarget(mob) && effectiveTarget(mob) == target;
        if (range <= 9 && (!(mob instanceof EntityElderGuardian) || !continuing)) {
            return TargetObservation.INELIGIBLE;
        }
        if (!continuing && !((EntityGuardian) mob).isWithinHomeDistanceFromPosition(new BlockPos(target))) {
            return TargetObservation.INELIGIBLE;
        }
        // The beam goal uses a visible lane and clears its swimmer path during the attack.
        Vec3d from = new Vec3d(mob.posX, mob.posY + mob.getEyeHeight(), mob.posZ);
        Vec3d to = new Vec3d(target.posX, target.posY + target.getEyeHeight(), target.posZ);
        LoadedPathTerrain terrain = new LoadedPathTerrain(world);
        if (!terrain.lineLoaded(from, to)) {
            return TargetObservation.INELIGIBLE;
        }
        return world.rayTraceBlocks(from, to, false, true, false) == null
                ? TargetObservation.IN_REACH : TargetObservation.obscured(true);
    }
}
