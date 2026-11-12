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
    private static final int TAUNT_TICKS = 60;
    private static final int RESET_DELAY_TICKS = 200;
    private static final double ORDINARY_LEAD = 0.2;
    private static final double MAXIMUM_CHASE_LEAD = 0.4;
    private static final double CHASE_START = 8.0;
    private static final double CHASE_END = 16.0;
    // Leave room for multiplying scores by the switch lead without overflowing.
    private static final double MAX_SCORE = Double.MAX_VALUE / 2.0;

    private final Map<UUID, Double> scores = new LinkedHashMap<>();
    private int openedAtMobTick;
    private UUID target;
    private boolean opening;
    private boolean selectionPending;
    private Integer sightLostAt;
    private UUID taunter;
    private int tauntedAt;
    private Integer resetStartedAt;

    private Encounter(UUID incumbent) {
        target = incumbent;
        if (incumbent != null) {
            scores.put(incumbent, OPENING_THREAT);
        }
    }

    public static Encounter recordPlayerHit(Encounter encounter, UUID player, UUID incumbent, int mobTick,
                                            double healthLost) {
        if (!(healthLost > 0.0) || !Double.isFinite(healthLost)) {
            return encounter;
        }
        Encounter updated = encounter;
        if (updated == null) {
            updated = new Encounter(incumbent);
            updated.opening = incumbent != null;
            updated.openedAtMobTick = mobTick;
        }
        updated.scores.merge(player, Math.min(healthLost, MAX_SCORE),
                (score, gain) -> Math.min(score + gain, MAX_SCORE));
        updated.selectionPending = true;
        return updated;
    }

    public GrantResult grant(UUID participant, double amount) {
        if (!(amount > 0.0) || !Double.isFinite(amount)) {
            return GrantResult.INVALID_AMOUNT;
        }
        double previous = scores.getOrDefault(participant, 0.0);
        double updated = previous + amount;
        if (updated > MAX_SCORE || updated <= previous) {
            return GrantResult.SCORE_LIMIT;
        }
        scores.put(participant, updated);
        selectionPending = true;
        return GrantResult.APPLIED;
    }

    /** Returns null on rejection, leaving any existing encounter untouched. */
    public static Encounter tryTaunt(Encounter encounter, UUID participant, UUID incumbent, int tick,
                                     Function<UUID, TargetObservation> observe) {
        TargetObservation taunterObservation = observe.apply(participant);
        if (encounter == null) {
            if (!taunterObservation.isEligible()) {
                return null;
            }
            UUID seed = incumbent != null && (incumbent.equals(participant) || observe.apply(incumbent).isEligible())
                    ? incumbent : null;
            encounter = new Encounter(seed);
        } else if (!encounter.isEligible(participant, tick, taunterObservation)) {
            return null;
        }

        double matchedScore = encounter.scores.getOrDefault(participant, 0.0);
        for (Map.Entry<UUID, Double> entry : encounter.scores.entrySet()) {
            if (entry.getValue() > matchedScore
                    && encounter.isEligible(entry.getKey(), tick, observe.apply(entry.getKey()))) {
                matchedScore = entry.getValue();
            }
        }
        encounter.scores.put(participant, matchedScore);
        encounter.opening = false;
        encounter.setTarget(participant);
        encounter.taunter = participant;
        encounter.tauntedAt = tick;
        encounter.resetStartedAt = null;
        return encounter;
    }

    public enum GrantResult {
        APPLIED, INVALID_AMOUNT, SCORE_LIMIT
    }

    public static double healthLost(float before, float after) {
        return Math.max(0.0, (double) before - Math.max(0.0, after));
    }

    public UUID getTarget() {
        return target;
    }

    public boolean holdsOpeningTarget(int mobTick) {
        return opening && mobTick - openedAtMobTick <= OPENING_HOLD_TICKS;
    }

    public boolean shouldReevaluate(int serverTick, int mobId) {
        return selectionPending || resetDue(serverTick) || (taunter != null && remainingTauntTicks(serverTick) == 0)
                || ((long) serverTick + mobId) % SELECTION_INTERVAL == 0;
    }

    public void participantChanged(UUID participant) {
        if (scores.containsKey(participant)) {
            markParticipantChanged(participant);
        }
    }

    public void removeParticipant(UUID participant) {
        if (scores.remove(participant) != null) {
            markParticipantChanged(participant);
            if (participant.equals(taunter)) {
                taunter = null;
            }
        }
    }

    private void markParticipantChanged(UUID participant) {
        selectionPending = true;
        if (participant.equals(target)) {
            opening = false;
        }
    }

    public UUID selectTarget(int serverTick, Function<UUID, TargetObservation> observe) {
        opening = false;
        selectionPending = false;
        TargetObservation incumbent = target == null ? TargetObservation.INELIGIBLE : observe.apply(target);
        boolean incumbentEligible = retainsIncumbent(serverTick, incumbent);
        if (taunter != null) {
            if (incumbentEligible && remainingTauntTicks(serverTick) > 0) {
                return taunter;
            }
            taunter = null;
        }
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
        setTarget(selected);
        if (selected != null) {
            resetStartedAt = null;
        } else if (resetStartedAt == null) {
            resetStartedAt = serverTick;
        }
        return selected;
    }

    public Integer remainingResetTicks(int serverTick) {
        return resetStartedAt == null ? null : Math.max(0, RESET_DELAY_TICKS - (serverTick - resetStartedAt));
    }

    public boolean resetDue(int serverTick) {
        return resetStartedAt != null && serverTick - resetStartedAt >= RESET_DELAY_TICKS;
    }

    public UUID getTaunter() {
        return taunter;
    }

    public int remainingTauntTicks(int tick) {
        return taunter == null ? 0 : Math.max(0, TAUNT_TICKS - (tick - tauntedAt));
    }

    public boolean isEligible(UUID participant, int tick, TargetObservation observation) {
        return observation.isEligible() || (participant.equals(target) && observation.sightLost
                && (sightLostAt == null || tick - sightLostAt < SIGHT_GRACE_TICKS));
    }

    private boolean retainsIncumbent(int tick, TargetObservation observation) {
        if (!observation.sightLost) {
            sightLostAt = null;
        } else if (sightLostAt == null) {
            sightLostAt = tick;
        }
        return target != null && scores.containsKey(target) && isEligible(target, tick, observation);
    }

    private void setTarget(UUID selected) {
        if (target == null || !target.equals(selected)) {
            sightLostAt = null;
        }
        target = selected;
    }

    private static double threshold(double score, double lead) {
        return score * (1.0 + lead);
    }

    private static double chaseLead(double distance) {
        double progress = Math.max(0.0, Math.min(1.0, (distance - CHASE_START) / (CHASE_END - CHASE_START)));
        return ORDINARY_LEAD + progress * (MAXIMUM_CHASE_LEAD - ORDINARY_LEAD);
    }

    public Map<UUID, Double> copyScores() {
        return copyScores(scores.size());
    }

    public Map<UUID, Double> copyScores(int limit) {
        Map<UUID, Double> shown = new LinkedHashMap<>();
        for (Map.Entry<UUID, Double> entry : scores.entrySet()) {
            if (shown.size() == limit) {
                break;
            }
            shown.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(shown);
    }

    public int participantCount() {
        return scores.size();
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

        public boolean hasLostSight() {
            return sightLost;
        }
    }
}
