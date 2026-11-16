package com.mahghuuuls.threatworks.server;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;

import java.util.function.DoubleSupplier;

/** Identifies the actual attacker and determines which points may be used for a hit. */
public final class DamageAttribution {
    final EntityLivingBase attacker;
    final EntityLivingBase summonOwner;
    private final boolean summon;
    // Only direct hits read equipment at impact. An attributable projectile without a snapshot uses base threat.
    private final Double launchMultiplier;

    private DamageAttribution(EntityLivingBase attacker, EntityLivingBase summonOwner, Double launchMultiplier,
                              boolean summon) {
        this.attacker = attacker;
        this.summonOwner = summonOwner;
        this.launchMultiplier = launchMultiplier;
        this.summon = summon;
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
                    ? new DamageAttribution((EntityPlayerMP) actor, null, null, false) : null;
        }
        if ("mob".equals(source.getDamageType()) && source.getClass() == EntityDamageSource.class) {
            Entity actor = source.getTrueSource();
            return actor instanceof EntityLiving && actor == source.getImmediateSource()
                    ? new DamageAttribution((EntityLiving) actor, null, null, false) : null;
        }
        if (!"arrow".equals(source.getDamageType()) || !source.isProjectile()) {
            return null;
        }
        Entity actor = source.getTrueSource();
        Entity immediate = source.getImmediateSource();
        if (!(immediate instanceof EntityArrow) || !(actor instanceof EntityLivingBase)) {
            return null;
        }
        Double multiplier = arrowMultiplier((EntityArrow) immediate, (EntityLivingBase) actor, worldSession);
        return multiplier == null ? null : new DamageAttribution((EntityLivingBase) actor, null, multiplier, false);
    }

    public interface SummonSource {
        boolean handles(DamageSource source);
        DamageAttribution resolve(DamageSource source, Object worldSession);
    }

    public static DamageAttribution summonDirect(EntityLivingBase summon, EntityLivingBase owner) {
        return new DamageAttribution(summon, owner, null, true);
    }

    public static DamageAttribution summonIndirect(EntityLivingBase summon, EntityLivingBase owner, Entity projectile,
                                                    Object worldSession) {
        Double multiplier = projectile instanceof EntityArrow
                ? arrowMultiplier((EntityArrow) projectile, summon, worldSession) : Double.valueOf(1.0);
        return multiplier == null ? null : new DamageAttribution(summon, owner, multiplier, true);
    }

    private static Double arrowMultiplier(EntityArrow arrow, EntityLivingBase actor, Object worldSession) {
        if (arrow.shootingEntity != actor || arrow.world != actor.world) {
            return null;
        }
        ArrowLaunch.Snapshot snapshot = ((ArrowLaunch) arrow).threatworks$getLaunch();
        if (snapshot == null || snapshot.worldSession != worldSession) {
            return 1.0;
        }
        return snapshot.shooter.equals(actor.getUniqueID())
                ? snapshot.multiplier : null;
    }

    boolean mayOpenEncounter() {
        return !summon && attacker instanceof EntityPlayerMP;
    }

    double multiplier(DoubleSupplier currentMultiplier) {
        return launchMultiplier == null ? currentMultiplier.getAsDouble() : launchMultiplier;
    }
}
