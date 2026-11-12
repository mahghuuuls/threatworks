package com.mahghuuuls.threatworks.server;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

final class SkeletonBow {
    private SkeletonBow() {
    }

    static boolean canReach(Vec3d origin, AxisAlignedBB target, float targetHeight,
                            BiPredicate<Vec3d, Vec3d> clearLine, Predicate<Vec3d> inWater) {
        double dx = (target.minX + target.maxX) * 0.5 - origin.x;
        double dz = (target.minZ + target.maxZ) * 0.5 - origin.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        // AbstractSkeleton aims above the target. Follow the center shot without random spread.
        Vec3d velocity = new Vec3d(dx, target.minY + targetHeight / 3.0F - origin.y + horizontal * 0.2F, dz)
                .normalize().scale(1.6F);
        AxisAlignedBB hitbox = target.grow(0.3F);
        Vec3d position = origin;
        double halfWidth = Math.max(hitbox.maxX - hitbox.minX, hitbox.maxZ - hitbox.minZ) * 0.5;
        double pastTarget = horizontal * (horizontal + 2.0 * halfWidth);
        // A straight horizontal heading, gravity, and drag keep the probe bounded.
        for (int tick = 0; tick < 128; tick++) {
            if (hitbox.contains(position)) {
                return true;
            }
            Vec3d next = position.add(velocity);
            RayTraceResult hit = hitbox.calculateIntercept(position, next);
            if (!clearLine.test(position, hit == null ? next : hit.hitVec)) {
                return false;
            }
            if (hit != null) {
                return true;
            }
            if (next.y < 0.0 && velocity.y < 0.0
                    || (next.x - origin.x) * dx + (next.z - origin.z) * dz > pastTarget) {
                return false;
            }
            velocity = velocity.scale(inWater.test(position) ? 0.6F : 0.99F).add(0.0, -0.05F, 0.0);
            position = next;
        }
        return false;
    }
}
