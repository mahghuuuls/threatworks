package com.mahghuuuls.threatworks.server;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.math.BlockPos;

final class ZombieTargets {
    private ZombieTargets() {
    }

    static boolean canEngage(EntityZombie zombie, EntityLivingBase target) {
        if (!isAttackable(zombie, target) || !withinPursuitBounds(zombie, target)) {
            return false;
        }

        double distanceSquared = zombie.getDistanceSq(target);
        BlockPos targetPosition = new BlockPos(target);

        float reach = zombie.width * 2.0F;
        if (distanceSquared <= reach * reach + target.width && zombie.getEntitySenses().canSee(target)) {
            return true;
        }

        if (!(zombie.getNavigator() instanceof PathNavigateGround)) {
            return false;
        }
        PathNavigateGround current = (PathNavigateGround) zombie.getNavigator();
        // A path request updates the navigator's destination, so probe on a separate navigator.
        PathNavigateGround probe = new PathNavigateGround(zombie, zombie.world);
        probe.setEnterDoors(current.getEnterDoors());
        probe.setCanSwim(current.getCanSwim());
        probe.setBreakDoors(zombie.isBreakDoorsTaskSet());
        Path path = probe.getPathToEntityLiving(target);
        PathPoint end = path == null ? null : path.getFinalPathPoint();
        return end != null && targetPosition.distanceSq(end.x, end.y, end.z) <= 2.25;
    }

    static boolean withinPursuitBounds(EntityZombie zombie, EntityLivingBase target) {
        double followRange = zombie.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue();
        return zombie.getDistanceSq(target) <= followRange * followRange
                && zombie.isWithinHomeDistanceFromPosition(new BlockPos(target));
    }

    static boolean isAttackable(EntityZombie zombie, EntityLivingBase target) {
        if (target == null || target == zombie || !target.isEntityAlive() || target.world != zombie.world
                || target.getIsInvulnerable() || zombie.isOnSameTeam(target) || !zombie.canAttackClass(target.getClass())) {
            return false;
        }
        if (target instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) target;
            return !player.isSpectator() && !player.capabilities.disableDamage;
        }
        return true;
    }
}
