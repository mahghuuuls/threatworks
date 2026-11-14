package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.compat.FoliaathCompatibility;
import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import com.mahghuuuls.threatworks.threat.Encounter.TargetObservation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.WorldServer;

/** Native combat observations used by the coordinator's shared encounter rules. */
interface MobPolicy {
    boolean controlsImmediately(EntityLiving mob);

    boolean accepts(EntityLiving mob, EntityLivingBase participant);

    EntityLivingBase resolve(WorldServer world, EntityLiving mob, Entity candidate);

    TargetObservation observe(WorldServer world, EntityLiving mob, Entity candidate, WorkSample sample);

    /** Lets native combat keep an otherwise legal target when there is no eligible replacement. */
    default boolean preservesNativeTarget(WorldServer world, EntityLiving mob, EntityLivingBase target) {
        return false;
    }

    /** Checks state needed by the setter without recomputing a path or projectile lane. */
    default boolean releasesNativeWrite(WorldServer world, EntityLiving mob, EntityLivingBase target) {
        return preservesNativeTarget(world, mob, target);
    }

    static MobPolicy forType(MobType type) {
        if (GhastMobPolicy.matchesType(type)) {
            return new GhastMobPolicy(type);
        }
        if (SlimeMobPolicy.matchesType(type)) {
            return new SlimeMobPolicy(type);
        }
        return builtIn(type);
    }

    static MobPolicy builtIn(MobType type) {
        return new MobPolicy() {
            @Override
            public boolean controlsImmediately(EntityLiving mob) {
                return MobTargets.canControlImmediately((EntityCreature) mob, type);
            }

            @Override
            public boolean accepts(EntityLiving mob, EntityLivingBase participant) {
                return MobTargets.providerAccepts(type, (EntityCreature) mob, participant);
            }

            @Override
            public EntityLivingBase resolve(WorldServer world, EntityLiving mob, Entity candidate) {
                return MobTargets.resolve(world, (EntityCreature) mob, candidate, type);
            }

            @Override
            public TargetObservation observe(WorldServer world, EntityLiving mob, Entity candidate, WorkSample sample) {
                return MobTargets.observe(world, (EntityCreature) mob, candidate, sample, type);
            }

            @Override
            public boolean preservesNativeTarget(WorldServer world, EntityLiving mob, EntityLivingBase target) {
                return MobTargets.preservesNativeTarget(world, (EntityCreature) mob, target, type);
            }
        };
    }

    static MobPolicy stationary(FoliaathCompatibility adapter) {
        return new MobPolicy() {
            @Override
            public boolean controlsImmediately(EntityLiving mob) {
                return adapter.phase((EntityCreature) mob) == FoliaathCompatibility.Phase.CONTROL;
            }

            @Override
            public boolean accepts(EntityLiving mob, EntityLivingBase participant) {
                return adapter.accepts(participant);
            }

            @Override
            public EntityLivingBase resolve(WorldServer world, EntityLiving mob, Entity candidate) {
                return MobTargets.resolveStationary(world, (EntityCreature) mob, candidate, adapter);
            }

            @Override
            public TargetObservation observe(WorldServer world, EntityLiving mob, Entity candidate, WorkSample sample) {
                return MobTargets.observeStationary(world, (EntityCreature) mob, candidate, adapter);
            }
        };
    }
}
