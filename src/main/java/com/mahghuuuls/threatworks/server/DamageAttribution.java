package com.mahghuuuls.threatworks.server;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.DamageSource;

import java.util.function.DoubleSupplier;

/** Identifies the actual attacker and determines which points may be used for a hit. */
final class DamageAttribution {
    final EntityLivingBase attacker;
    // Only direct hits read equipment at impact. An arrow without a snapshot uses an explicit zero.
    private final Double launchPoints;

    private DamageAttribution(EntityLivingBase attacker, Double launchPoints) {
        this.attacker = attacker;
        this.launchPoints = launchPoints;
    }

    static DamageAttribution resolve(DamageSource source, Object worldSession) {
        if (source == null) {
            return null;
        }
        if ("player".equals(source.getDamageType())) {
            Entity actor = source.getTrueSource();
            return actor instanceof EntityPlayerMP && actor == source.getImmediateSource()
                    ? new DamageAttribution((EntityPlayerMP) actor, null) : null;
        }
        if (!"arrow".equals(source.getDamageType()) || !source.isProjectile()) {
            return null;
        }
        Entity actor = source.getTrueSource();
        Entity immediate = source.getImmediateSource();
        if (!(immediate instanceof EntityArrow) || !(actor instanceof EntityLivingBase)) {
            return null;
        }
        EntityArrow arrow = (EntityArrow) immediate;
        if (arrow.shootingEntity != actor || arrow.world != actor.world) {
            return null;
        }
        ArrowLaunch.Snapshot snapshot = ((ArrowLaunch) arrow).threatworks$getLaunch();
        if (snapshot == null || snapshot.worldSession != worldSession) {
            return new DamageAttribution((EntityLivingBase) actor, 0.0);
        }
        return snapshot.shooter.equals(actor.getUniqueID())
                ? new DamageAttribution((EntityLivingBase) actor, snapshot.points) : null;
    }

    double points(DoubleSupplier currentPoints) {
        return launchPoints == null ? currentPoints.getAsDouble() : launchPoints;
    }
}
