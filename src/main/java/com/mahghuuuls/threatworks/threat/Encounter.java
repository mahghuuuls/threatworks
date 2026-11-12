package com.mahghuuuls.threatworks.threat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class Encounter {
    private static final double OPENING_THREAT = 10.0;
    // Vanilla target tasks consider new goals every three mob updates in 1.12.2.
    private static final int OPENING_HOLD_TICKS = 3;

    private final Map<UUID, Double> scores = new LinkedHashMap<>();
    private final int openedAt;
    private UUID heldTarget;

    private Encounter(UUID incumbent, int mobTick) {
        openedAt = mobTick;
        heldTarget = incumbent;
        if (incumbent != null) {
            scores.put(incumbent, OPENING_THREAT);
        }
    }

    public static Encounter recordPlayerHit(Encounter encounter, UUID player, UUID incumbent, int mobTick,
                                            double healthLost) {
        if (!(healthLost > 0.0) || !Double.isFinite(healthLost)) {
            return encounter;
        }
        Encounter updated = encounter == null ? new Encounter(incumbent, mobTick) : encounter;
        updated.scores.merge(player, healthLost, Double::sum);
        return updated;
    }

    public static double healthLost(float before, float after) {
        return Math.max(0.0, (double) before - Math.max(0.0, after));
    }

    public UUID getHeldTarget() {
        return heldTarget;
    }

    public boolean holdsOpeningTarget(int mobTick) {
        return heldTarget != null && mobTick - openedAt <= OPENING_HOLD_TICKS;
    }

    public void releaseTarget() {
        heldTarget = null;
    }

    public Map<UUID, Double> copyScores() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(scores));
    }
}
