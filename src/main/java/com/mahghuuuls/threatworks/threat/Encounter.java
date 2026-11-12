package com.mahghuuuls.threatworks.threat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class Encounter {
    private static final double OPENING_THREAT = 10.0;
    // Vanilla target tasks consider new goals every three mob updates in 1.12.2.
    private static final int OPENING_HOLD_TICKS = 3;
    private static final int SELECTION_INTERVAL = 5;
    private static final int SIGHT_GRACE_TICKS = 40;
    private static final double ORDINARY_LEAD = 0.2;
    private static final double MAXIMUM_CHASE_LEAD = 0.4;
    private static final double CHASE_START = 8.0;
    private static final double CHASE_END = 16.0;

    private final Map<UUID, Double> scores = new LinkedHashMap<>();
    private final int openedAt;
    private UUID target;
    private boolean opening;
    private boolean selectionPending;
    private Integer sightLostAt;

    private Encounter(UUID incumbent, int mobTick) {
        openedAt = mobTick;
        target = incumbent;
        opening = incumbent != null;
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
        updated.selectionPending = true;
        return updated;
    }

    public static double healthLost(float before, float after) {
        return Math.max(0.0, (double) before - Math.max(0.0, after));
    }

    public UUID getTarget() {
        return target;
    }

    public boolean holdsOpeningTarget(int mobTick) {
        return opening && mobTick - openedAt <= OPENING_HOLD_TICKS;
    }

    public boolean shouldReevaluate(int mobTick, int mobId) {
        return selectionPending || ((long) mobTick + mobId) % SELECTION_INTERVAL == 0;
    }

    public UUID selectTarget(int mobTick, Function<UUID, TargetObservation> observe) {
        opening = false;
        selectionPending = false;
        TargetObservation incumbent = target == null ? TargetObservation.INELIGIBLE : observe.apply(target);
        boolean incumbentEligible = retainsIncumbent(mobTick, incumbent);
        double incumbentScore = incumbentEligible ? scores.get(target) : 0.0;
        double ordinaryThreshold = threshold(incumbentScore, ORDINARY_LEAD);
        double minimumThreshold = Math.min(ordinaryThreshold, threshold(incumbentScore, MAXIMUM_CHASE_LEAD));
        UUID selected = incumbentEligible ? target : null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Map.Entry<UUID, Double> entry : scores.entrySet()) {
            UUID challenger = entry.getKey();
            double score = entry.getValue();
            if (challenger.equals(target) || score <= bestScore
                    || (incumbentEligible && score <= minimumThreshold)) {
                continue;
            }
            TargetObservation candidate = observe.apply(challenger);
            if (candidate.isEligible() && (!incumbentEligible
                    || score > (incumbent.canAttackImmediately && !candidate.canAttackImmediately
                    ? threshold(incumbentScore, chaseLead(candidate.distance)) : ordinaryThreshold))) {
                selected = challenger;
                bestScore = score;
            }
        }
        if (target == null || !target.equals(selected)) {
            sightLostAt = null;
        }
        target = selected;
        return selected;
    }

    private boolean retainsIncumbent(int tick, TargetObservation observation) {
        if (!observation.sightLost) {
            sightLostAt = null;
            return observation.isEligible();
        }
        if (sightLostAt == null) {
            sightLostAt = tick;
        }
        return tick - sightLostAt < SIGHT_GRACE_TICKS;
    }

    private static double threshold(double score, double lead) {
        return score * (1.0 + lead);
    }

    private static double chaseLead(double distance) {
        double progress = Math.max(0.0, Math.min(1.0, (distance - CHASE_START) / (CHASE_END - CHASE_START)));
        return ORDINARY_LEAD + progress * (MAXIMUM_CHASE_LEAD - ORDINARY_LEAD);
    }

    public Map<UUID, Double> copyScores() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(scores));
    }

    public static final class TargetObservation {
        public static final TargetObservation INELIGIBLE = new TargetObservation(false, false, Double.NaN);
        public static final TargetObservation IN_REACH = new TargetObservation(true, false, 0.0);
        private static final TargetObservation OBSCURED_PURSUIT = new TargetObservation(false, true, Double.NaN);
        private static final TargetObservation OBSCURED_IN_REACH = new TargetObservation(true, true, 0.0);

        private final boolean canAttackImmediately;
        private final boolean sightLost;
        private final double distance;

        private TargetObservation(boolean canAttackImmediately, boolean sightLost, double distance) {
            this.canAttackImmediately = canAttackImmediately;
            this.sightLost = sightLost;
            this.distance = distance;
        }

        public static TargetObservation pursuit(double distance) {
            return new TargetObservation(false, false, distance);
        }

        public static TargetObservation obscured(boolean canAttackImmediately) {
            return canAttackImmediately ? OBSCURED_IN_REACH : OBSCURED_PURSUIT;
        }

        public boolean isEligible() {
            return this != INELIGIBLE && !sightLost;
        }
    }
}
