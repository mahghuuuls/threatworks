package com.mahghuuuls.threatworks.server;

import java.util.UUID;

/** Transient launch observation stored on the live arrow by the Mixin. */
public interface ArrowLaunch {
    Snapshot threatworks$getLaunch();

    final class Snapshot {
        final UUID shooter;
        final Object worldSession;
        final double multiplier;

        Snapshot(UUID shooter, Object worldSession, double multiplier) {
            this.shooter = shooter;
            this.worldSession = worldSession;
            this.multiplier = multiplier;
        }
    }
}
