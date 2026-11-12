package com.mahghuuuls.threatworks.server;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.DamageSource;

import java.util.function.DoubleSupplier;

/** Identifies the actual attacker and determines which points may be used for a hit. */
public final class DamageAttribution {
    final EntityLivingBase attacker;
    final EntityLivingBase summonOwner;
    // Only direct hits read equipment at impact. An arrow without a snapshot uses the base multiplier.
    private final Double launchMultiplier;

    private DamageAttribution(EntityLivingBase attacker, EntityLivingBase summonOwner, Double launchMultiplier) {
        this.attacker = attacker;
        this.summonOwner = summonOwner;
        this.launchMultiplier = launchMultiplier;
    }

    static DamageAttribution resolve(DamageSource source, Object worldSession) {
        return resolve(source, worldSession, null);
    }

    static DamageAttribution resolve(DamageSource source, Object worldSession, SummonSource summons) {
        if (source == null) {
            return null;
        }
        if (summons != null && summons.handles(source)) {
            return summons.resolve(source, worldSession);
        }
        if ("player".equals(source.getDamageType())) {
            Entity actor = source.getTrueSource();
            return actor instanceof EntityPlayerMP && actor == source.getImmediateSource()
                    ? new DamageAttribution((EntityPlayerMP) actor, null, null) : null;
        }
        if (!"arrow".equals(source.getDamageType()) || !source.isProjectile()) {
            return null;
        }
        Entity actor = source.getTrueSource();
        Entity immediate = source.getImmediateSource();
        if (!(immediate instanceof EntityArrow) || !(actor instanceof EntityLivingBase)) {
            return null;
        }
        return arrow((EntityArrow) immediate, (EntityLivingBase) actor, null, worldSession);
    }

    public interface SummonSource {
        boolean handles(DamageSource source);
        DamageAttribution resolve(DamageSource source, Object worldSession);
    }

    public static DamageAttribution summonMelee(EntityLivingBase summon, EntityLivingBase owner) {
        return new DamageAttribution(summon, owner, null);
    }

    public static DamageAttribution summonArrow(EntityLivingBase summon, EntityLivingBase owner, EntityArrow arrow,
                                                Object worldSession) {
        return arrow(arrow, summon, owner, worldSession);
    }

    private static DamageAttribution arrow(EntityArrow arrow, EntityLivingBase actor, EntityLivingBase owner,
                                           Object worldSession) {
        if (arrow.shootingEntity != actor || arrow.world != actor.world) {
            return null;
        }
        ArrowLaunch.Snapshot snapshot = ((ArrowLaunch) arrow).threatworks$getLaunch();
        if (snapshot == null || snapshot.worldSession != worldSession) {
            return new DamageAttribution(actor, owner, 1.0);
        }
        return snapshot.shooter.equals(actor.getUniqueID())
                ? new DamageAttribution(actor, owner, snapshot.multiplier) : null;
    }

    boolean mayOpenEncounter() {
        return summonOwner == null && attacker instanceof EntityPlayerMP;
    }

    double multiplier(DoubleSupplier currentMultiplier) {
        return launchMultiplier == null ? currentMultiplier.getAsDouble() : launchMultiplier;
    }
}
