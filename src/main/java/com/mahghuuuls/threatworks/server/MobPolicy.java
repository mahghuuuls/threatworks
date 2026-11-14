package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.compat.FoliaathCompatibility;
import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import com.mahghuuuls.threatworks.threat.Encounter.TargetObservation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.WorldServer;

/** Native combat observations used by the coordinator's shared encounter rules. */
interface MobPolicy {
    boolean controlsImmediately(EntityCreature mob);

    boolean accepts(EntityCreature mob, EntityLivingBase participant);

    EntityLivingBase resolve(WorldServer world, EntityCreature mob, Entity candidate);

    TargetObservation observe(WorldServer world, EntityCreature mob, Entity candidate, WorkSample sample);

    /** Lets native combat keep an otherwise legal target when there is no eligible replacement. */
    default boolean preservesNativeTarget(WorldServer world, EntityCreature mob, EntityLivingBase target) {
        return false;
    }

    static MobPolicy builtIn(MobType type) {
        return new MobPolicy() {
            @Override
            public boolean controlsImmediately(EntityCreature mob) {
                return MobTargets.canControlImmediately(mob, type);
            }

            @Override
            public boolean accepts(EntityCreature mob, EntityLivingBase participant) {
                return MobTargets.providerAccepts(type, mob, participant);
            }

            @Override
            public EntityLivingBase resolve(WorldServer world, EntityCreature mob, Entity candidate) {
                return MobTargets.resolve(world, mob, candidate, type);
            }

            @Override
            public TargetObservation observe(WorldServer world, EntityCreature mob, Entity candidate, WorkSample sample) {
                return MobTargets.observe(world, mob, candidate, sample, type);
            }

            @Override
            public boolean preservesNativeTarget(WorldServer world, EntityCreature mob, EntityLivingBase target) {
                return MobTargets.preservesNativeTarget(world, mob, target, type);
            }
        };
    }

    static MobPolicy stationary(FoliaathCompatibility adapter) {
        return new MobPolicy() {
            @Override
            public boolean controlsImmediately(EntityCreature mob) {
                return adapter.phase(mob) == FoliaathCompatibility.Phase.CONTROL;
            }

            @Override
            public boolean accepts(EntityCreature mob, EntityLivingBase participant) {
                return adapter.accepts(participant);
            }

            @Override
            public EntityLivingBase resolve(WorldServer world, EntityCreature mob, Entity candidate) {
                return MobTargets.resolveStationary(world, mob, candidate, adapter);
            }

            @Override
            public TargetObservation observe(WorldServer world, EntityCreature mob, Entity candidate, WorkSample sample) {
                return MobTargets.observeStationary(world, mob, candidate, adapter);
            }
        };
    }
}
