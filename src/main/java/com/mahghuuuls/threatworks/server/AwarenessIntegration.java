package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.api.TargetAwareness;
import com.mahghuuuls.threatworks.api.TargetAwareness.Decision;
import com.mahghuuuls.threatworks.api.TargetAwareness.EncounterChange;
import com.mahghuuuls.threatworks.api.TargetAwareness.Transition;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Consumer;

/** Keeps optional callbacks and committed lifecycle delivery outside encounter state. */
final class AwarenessIntegration {
    private static final Logger LOGGER = LogManager.getLogger("threatworks");
    private final Queue<Consumer<TargetAwareness>> changes = new ArrayDeque<>();
    private TargetAwareness provider;
    private boolean registrationClosed;
    private boolean reportedFailure;
    private long nextEncounter;

    boolean register(TargetAwareness candidate) {
        if (provider != null || registrationClosed || candidate == null) {
            return false;
        }
        provider = candidate;
        return true;
    }

    void closeRegistration() {
        registrationClosed = true;
    }

    boolean active() {
        return provider != null;
    }

    Decision decide(EntityLiving mob, EntityPlayerMP player) {
        try {
            Decision decision = provider.decide(mob, player);
            if (decision != null) {
                return decision;
            }
            fail(null);
        } catch (RuntimeException | LinkageError failure) {
            fail(failure);
        }
        return null;
    }

    boolean tauntCommitted(EntityLiving mob, EntityPlayerMP player) {
        try {
            provider.tauntCommitted(mob, player);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            fail(failure);
            return false;
        }
    }

    long started(int dimension, UUID mobId) {
        long id = ++nextEncounter;
        encounterChanged(new EncounterChange(id, dimension, mobId, Transition.STARTED));
        return id;
    }

    void ended(long id, int dimension, UUID mobId, Transition reason) {
        encounterChanged(new EncounterChange(id, dimension, mobId, reason));
    }

    private void encounterChanged(EncounterChange change) {
        changes.add(target -> target.encounterChanged(change));
    }

    void playerDied(UUID playerId) {
        if (active()) {
            changes.add(target -> target.playerDied(playerId));
        }
    }

    void deliverChanges() {
        // Changes made by a listener are delivered on the next dispatch.
        int pending = changes.size();
        for (int i = 0; i < pending; i++) {
            Consumer<TargetAwareness> change = changes.remove();
            try {
                change.accept(provider);
            } catch (RuntimeException | LinkageError failure) {
                fail(failure);
            }
        }
    }

    void serverStopped() {
        deliverChanges();
        reportedFailure = false;
    }

    private void fail(Throwable failure) {
        if (!reportedFailure) {
            reportedFailure = true;
            LOGGER.warn("Target awareness provider failed. A failed pair uses ordinary targeting until it leaves the world.",
                    failure);
        }
    }
}
