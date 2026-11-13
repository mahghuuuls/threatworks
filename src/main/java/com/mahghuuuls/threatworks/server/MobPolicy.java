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

    boolean accepts(EntityLivingBase participant);

    EntityLivingBase resolve(WorldServer world, EntityCreature mob, Entity candidate);

    TargetObservation observe(WorldServer world, EntityCreature mob, Entity candidate, WorkSample sample);

    static MobPolicy builtIn(MobType type) {
        return new MobPolicy() {
            @Override
            public boolean controlsImmediately(EntityCreature mob) {
                return MobTargets.canControlImmediately(mob, type);
            }

            @Override
            public boolean accepts(EntityLivingBase participant) {
                return true;
            }

            @Override
            public EntityLivingBase resolve(WorldServer world, EntityCreature mob, Entity candidate) {
                return MobTargets.resolve(world, mob, candidate);
            }

            @Override
            public TargetObservation observe(WorldServer world, EntityCreature mob, Entity candidate, WorkSample sample) {
                return MobTargets.observe(world, mob, candidate, sample);
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
            public boolean accepts(EntityLivingBase participant) {
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
