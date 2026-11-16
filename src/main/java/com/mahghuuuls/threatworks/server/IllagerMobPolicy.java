package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import com.mahghuuuls.threatworks.threat.Encounter.TargetObservation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.monster.EntityEvoker;
import net.minecraft.entity.monster.EntityVex;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

/** Observes the Evoker's spell and the independent Vex's charge without driving either action. */
final class IllagerMobPolicy implements MobPolicy {
    private final boolean vex;

    IllagerMobPolicy(MobType type) {
        vex = type == MobType.VEX;
    }

    static boolean matchesType(MobType type) {
        return type == MobType.EVOKER || type == MobType.VEX;
    }

    static boolean isIndependent(EntityVex vex) {
        // Native summons save LifeTicks even though vanilla does not save the owner reference.
        return vex.getOwner() == null && !VexLifetime.isLimited(vex);
    }

    private static final class VexLifetime {
        private static final Field LIMITED = findLimitedLife();

        private static Field findLimitedLife() {
            try {
                return ReflectionHelper.findField(EntityVex.class, "limitedLifespan", "field_190667_bw");
            } catch (RuntimeException unavailable) {
                return null;
            }
        }

        static boolean isLimited(EntityVex vex) {
            try {
                return LIMITED == null || LIMITED.getBoolean(vex);
            } catch (IllegalAccessException unavailable) {
                return true;
            }
        }
    }

    static boolean hasNativeContract(EntityLiving mob) {
        if (!aiEnabled(mob)) {
            return false;
        }
        if (mob instanceof EntityVex) {
            return isIndependent((EntityVex) mob) && mob.getMoveHelper().getClass().getEnclosingClass() == EntityVex.class
                    && hasGoal(mob, EntityVex.class, "AIChargeAttack", 4, 1);
        }
        return hasGoal(mob, EntityEvoker.class, "AIAttackSpell", 5, 0)
                && hasGoal(mob, EntityEvoker.class, "AISummonSpell", 4, 0)
                && hasGoal(mob, EntityEvoker.class, "AICastingSpell", 1, 3);
    }

    private static boolean hasGoal(EntityLiving mob, Class<?> owner, String name, int priority, int mutex) {
        int count = 0;
        for (EntityAITaskEntry entry : mob.tasks.taskEntries) {
            Class<?> goal = entry.action.getClass();
            if (goal.getEnclosingClass() == owner && goal.getSimpleName().equals(name)) {
                if (entry.priority != priority || entry.action.getMutexBits() != mutex) {
                    return false;
                }
                count++;
            }
        }
        return count == 1;
    }

    private static boolean aiEnabled(EntityLiving mob) {
        return mob.isEntityAlive() && !mob.isAIDisabled() && !mob.tasks.isControlFlagDisabled(3);
    }

    static boolean isNativePhase(EntityLiving mob) {
        if (hasVanillaNativeState(mob)) {
            return true;
        }
        return mob instanceof EntityEvoker && hasActiveRetreat(mob);
    }

    static boolean hasActiveRetreat(EntityLiving mob) {
        if (!(mob instanceof EntityEvoker)) {
            return false;
        }
        for (EntityAITaskEntry entry : mob.tasks.taskEntries) {
            if (entry.using && entry.action.getClass() == EntityAIAvoidEntity.class && entry.priority == 2) {
                return true;
            }
        }
        return false;
    }

    static boolean hasVanillaNativeState(EntityLiving mob) {
        if (mob.isRiding() || mob.isBeingRidden() || mob.isInWater() || mob.isInLava()) {
            return true;
        }
        if (mob instanceof EntityVex) {
            return ((EntityVex) mob).isCharging();
        }
        return ((EntityEvoker) mob).isSpellcasting();
    }

    @Override
    public boolean controlsImmediately(EntityLiving mob) {
        return aiEnabled(mob) && (!vex || isIndependent((EntityVex) mob)) && !isNativePhase(mob);
    }

    @Override
    public boolean protectsCommittedTarget(EntityLiving mob) {
        return vex ? isIndependent((EntityVex) mob) && ((EntityVex) mob).isCharging()
                : ((EntityEvoker) mob).isSpellcasting();
    }

    @Override
    public boolean blocksCommittedWrite(WorldServer world, EntityLiving mob, EntityLivingBase requested) {
        EntityLivingBase target = mob.getAttackTarget();
        boolean validTarget = resolve(world, mob, target) != null
                && world.getEntityFromUuid(target.getUniqueID()) == target;
        return requested != null || validTarget;
    }

    @Override
    public boolean keepsEncounterDuringNativePhase() {
        return true;
    }

    @Override
    public boolean accepts(EntityLiving mob, EntityLivingBase participant) {
        return participant.attackable();
    }

    @Override
    public EntityLivingBase resolve(WorldServer world, EntityLiving mob, Entity candidate) {
        if (!(candidate instanceof EntityLivingBase)) {
            return null;
        }
        EntityLivingBase target = (EntityLivingBase) candidate;
        BlockPos position = new BlockPos(target);
        double reach = mob.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue();
        return MobTargets.isAttackable(mob, target) && accepts(mob, target) && world.isBlockLoaded(position)
                && world.getWorldBorder().contains(position) && mob.getDistanceSq(target) <= reach * reach
                ? target : null;
    }

    @Override
    public TargetObservation observe(WorldServer world, EntityLiving mob, Entity candidate, WorkSample sample) {
        EntityLivingBase target = resolve(world, mob, candidate);
        if (target == null) {
            return TargetObservation.INELIGIBLE;
        }
        double distance = mob.getDistanceSq(target);
        LoadedPathTerrain terrain = new LoadedPathTerrain(world);
        if (vex) {
            // The native charge phases through blocks but still needs loaded world and a valid target.
            if (!terrain.lineLoaded(mob.getPositionVector(), target.getPositionVector())) {
                return TargetObservation.INELIGIBLE;
            }
            boolean continuing = ((EntityVex) mob).isCharging() && mob.getAttackTarget() == target;
            if (continuing && mob.getEntityBoundingBox().intersects(target.getEntityBoundingBox())) {
                return TargetObservation.IN_REACH;
            }
            return distance > 4 || continuing
                    ? TargetObservation.pursuit(Math.sqrt(distance)) : TargetObservation.INELIGIBLE;
        }
        Vec3d from = new Vec3d(mob.posX, mob.posY + mob.getEyeHeight(), mob.posZ);
        Vec3d to = new Vec3d(target.posX, target.posY + target.getEyeHeight(), target.posZ);
        if (!terrain.lineLoaded(from, to) || !canPlaceFang(terrain, mob, target, distance)) {
            return TargetObservation.INELIGIBLE;
        }
        return world.rayTraceBlocks(from, to, false, true, false) == null
                ? TargetObservation.IN_REACH : TargetObservation.obscured(true);
    }

    private static boolean canPlaceFang(LoadedPathTerrain terrain, EntityLiving mob, EntityLivingBase target,
                                        double distanceSquared) {
        float aim = (float) MathHelper.atan2(target.posZ - mob.posZ, target.posX - mob.posX);
        if (distanceSquared < 9) {
            for (int index = 0; index < 8; index++) {
                if (index < 5 && fangIntersects(terrain, mob, target, aim + index * (float) Math.PI * 0.4F, 1.5)
                        || fangIntersects(terrain, mob, target,
                        aim + index * (float) Math.PI * 0.25F + (float) Math.PI * 0.4F, 2.5)) {
                    return true;
                }
            }
        } else {
            double separation = Math.hypot(target.posX - mob.posX, target.posZ - mob.posZ);
            int nearest = (int) Math.round(separation / 1.25);
            for (int step = Math.max(1, nearest - 1); step <= Math.min(16, nearest + 1); step++) {
                if (fangIntersects(terrain, mob, target, aim, step * 1.25)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean fangIntersects(LoadedPathTerrain terrain, EntityLiving mob, EntityLivingBase target,
                                           float angle, double radius) {
        double x = mob.posX + MathHelper.cos(angle) * radius;
        double z = mob.posZ + MathHelper.sin(angle) * radius;
        int bottom = MathHelper.floor(Math.min(mob.posY, target.posY)) - 1;
        BlockPos cell = new BlockPos(x, Math.max(mob.posY, target.posY) + 1, z);
        // Bound the probe to local combat even if a pack assigns an extremely large follow range.
        for (int step = 0; step < 24 && cell.getY() >= bottom; step++, cell = cell.down()) {
            if (!terrain.isNormalCube(cell, true) && terrain.isNormalCube(cell.down(), true)) {
                IBlockState state = terrain.getBlockState(cell);
                AxisAlignedBB bounds = state.getCollisionBoundingBox(terrain, cell);
                double y = cell.getY() + (bounds == null ? 0 : bounds.maxY);
                return new AxisAlignedBB(x - 0.45, y, z - 0.45, x + 0.45, y + 0.8, z + 0.45)
                        .intersects(target.getEntityBoundingBox());
            }
        }
        return false;
    }
}
