package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.server.DamageAttribution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.UUID;

/** Transient origin of the exact snow golem inserted by the Wizardry spell. */
public interface SnowGolemOrigin {
    UUID threatworks$wizardryCaster();

    void threatworks$observeWizardrySpawn(UUID caster);

    static void observe(World world, Entity entity, EntityPlayer caster) {
        if (!(world instanceof WorldServer) || entity.getClass() != EntitySnowman.class || entity.world != world
                || caster == null || caster.world != world || !caster.isEntityAlive()) {
            return;
        }
        WorldServer serverWorld = (WorldServer) world;
        if (serverWorld.getMinecraftServer().isCallingFromMinecraftThread()
                && serverWorld.getEntityFromUuid(entity.getUniqueID()) == entity
                && serverWorld.getEntityFromUuid(caster.getUniqueID()) == caster) {
            ((SnowGolemOrigin) entity).threatworks$observeWizardrySpawn(caster.getUniqueID());
        }
    }

    static boolean handles(DamageSource source) {
        return source.getClass() == EntityDamageSourceIndirect.class && source.isProjectile()
                && "thrown".equals(source.getDamageType()) && source.getImmediateSource() instanceof EntitySnowball
                && source.getTrueSource() != null && source.getTrueSource().getClass() == EntitySnowman.class;
    }

    static DamageAttribution resolve(DamageSource source, Object worldSession) {
        EntitySnowball snowball = (EntitySnowball) source.getImmediateSource();
        EntityLivingBase golem = snowball.getThrower();
        if (!(golem instanceof SnowGolemOrigin) || golem != source.getTrueSource() || golem.world != snowball.world) {
            return null;
        }
        UUID caster = ((SnowGolemOrigin) golem).threatworks$wizardryCaster();
        return caster == null ? null : DamageAttribution.summonIndirect(golem,
                WizardryCompatibility.liveCaster(golem, caster), snowball, worldSession);
    }
}
