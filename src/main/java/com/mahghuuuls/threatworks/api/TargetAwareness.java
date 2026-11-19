package com.mahghuuuls.threatworks.api;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

/** Optional server-side awareness for qualified mob and player pairs. */
public interface TargetAwareness {
    enum Decision {
        ALLOW,
        UNAWARE,
        BLOCKED
    }

    enum Transition {
        STARTED,
        RESET,
        MOB_REMOVED,
        UNSUPPORTED,
        WORLD_UNLOADED,
        SERVER_STOPPED
    }

    /**
     * Reads the current decision for one live pair. Return ALLOW for a pair the provider
     * does not manage, UNAWARE to exclude except for a revealing taunt, or BLOCKED to
     * also refuse the taunt. Combine independent restrictions before returning.
     * This is a hot-path read. Do not mutate gameplay, call back into Threatworks,
     * search for entities, or evaluate sight and pathfinding here.
     */
    Decision decide(EntityLiving mob, EntityPlayerMP player);

    /**
     * A taunt successfully assigned its target and committed. Reveal this player to
     * this mob before returning, without consuming protection or calling threat actions.
     * Observe damage independently and report any changes through the pair operations.
     * If this callback fails,
     * the affected live pair falls back to ordinary targeting until one entity leaves the world.
     */
    default void tauntCommitted(EntityLiving mob, EntityPlayerMP player) {}

    /** Receives committed transitions in order on the logical server, outside encounter mutation. */
    default void encounterChanged(EncounterChange change) {}

    /** Receives an actual player death after its threat scores have been removed. */
    default void playerDied(UUID playerId) {}

    /** Detached observation. Encounter IDs are stable and unique within the running game process. */
    final class EncounterChange {
        private final long encounterId;
        private final int dimension;
        private final UUID mobId;
        private final Transition transition;

        public EncounterChange(long encounterId, int dimension, UUID mobId, Transition transition) {
            this.encounterId = encounterId;
            this.dimension = dimension;
            this.mobId = mobId;
            this.transition = transition;
        }

        public long encounterId() { return encounterId; }

        public int dimension() { return dimension; }

        public UUID mobId() { return mobId; }

        public Transition transition() { return transition; }
    }
}
