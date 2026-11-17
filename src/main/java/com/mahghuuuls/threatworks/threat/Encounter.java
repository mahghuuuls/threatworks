package com.mahghuuuls.threatworks.threat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Encounter {
    // Vanilla target tasks consider new goals every three mob updates in 1.12.2.
    private static final int OPENING_HOLD_TICKS = 3;
    public static final int SELECTION_INTERVAL = 5;
    private final Rules rules;
    private final Map<UUID, Double> scores = new LinkedHashMap<>();
    private final Iterable<UUID> participants = Collections.unmodifiableSet(scores.keySet());
    private long decisionVersion;
    private int openedAtMobTick;
    private UUID target;
    private boolean opening;
    private boolean selectionPending;
    private Integer sightLostAt;
    private UUID taunter;
    private int tauntedAt;
    private Integer resetStartedAt;

    private Encounter(UUID incumbent, Rules rules) {
        this.rules = rules;
        target = incumbent;
        if (incumbent != null) {
            scores.put(incumbent, rules.openingThreat);
        }
    }

    public static Encounter recordHit(Encounter encounter, UUID attacker, UUID incumbent, int mobTick,
                                      double gain, Rules rules) {
        if (!(gain > 0.0) || !Double.isFinite(gain)) {
            return encounter;
        }
        Encounter updated = encounter;
        if (updated == null) {
            updated = new Encounter(incumbent, rules);
            updated.opening = incumbent != null;
            updated.openedAtMobTick = mobTick;
        }
        updated.scores.merge(attacker, Math.min(gain, Rules.MAX_SCORE),
                (score, amount) -> Math.min(score + amount, Rules.MAX_SCORE));
        updated.decisionVersion++;
        updated.selectionPending = true;
        return updated;
    }

    public GrantResult grant(UUID participant, double amount) {
        if (!(amount > 0.0) || !Double.isFinite(amount)) {
            return GrantResult.INVALID_AMOUNT;
        }
        double previous = scores.getOrDefault(participant, 0.0);
        double updated = previous + amount;
        if (updated > Rules.MAX_SCORE || updated <= previous) {
            return GrantResult.SCORE_LIMIT;
        }
        scores.put(participant, updated);
        decisionVersion++;
        selectionPending = true;
        return GrantResult.APPLIED;
    }

    /** Returns null on rejection, leaving any existing encounter untouched. */
    public static Encounter tryTaunt(Encounter encounter, UUID participant, UUID incumbent, int tick,
                                     Function<UUID, TargetObservation> observe, Rules rules) {
        Taunt pending = prepareTaunt(encounter, participant, incumbent, tick, observe, rules);
        return pending == null ? null : pending.apply();
    }

    public static Taunt prepareTaunt(Encounter encounter, UUID participant, UUID incumbent, int tick,
                                     Function<UUID, TargetObservation> observe, Rules rules) {
        TargetObservation taunterObservation = observe.apply(participant);
        if (encounter == null) {
            if (!taunterObservation.isEligible()) {
                return null;
            }
            UUID seed = incumbent != null && (incumbent.equals(participant) || observe.apply(incumbent).isEligible())
                    ? incumbent : null;
            encounter = new Encounter(seed, rules);
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
        return new Taunt(encounter, participant, matchedScore, tick);
    }

    public static final class Taunt {
        private final Encounter encounter;
        private final UUID participant;
        private final double matchedScore;
        private final int tick;

        private Taunt(Encounter encounter, UUID participant, double matchedScore, int tick) {
            this.encounter = encounter;
            this.participant = participant;
            this.matchedScore = matchedScore;
            this.tick = tick;
        }

        public Encounter apply() {
            encounter.scores.merge(participant, matchedScore, Math::max);
            encounter.decisionVersion++;
            encounter.opening = false;
            encounter.setTarget(participant);
            encounter.taunter = participant;
            encounter.tauntedAt = tick;
            encounter.resetStartedAt = null;
            return encounter;
        }
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

    public double score(UUID participant) {
        return scores.getOrDefault(participant, 0.0);
    }

    public void yieldToNative(int tick) {
        target = null;
        taunter = null;
        sightLostAt = null;
        opening = false;
        selectionPending = true;
        if (resetStartedAt == null) {
            resetStartedAt = tick;
        }
    }

    public void resumeFromNative(UUID incumbent, Function<UUID, TargetObservation> observe) {
        if (incumbent != null && scores.containsKey(incumbent) && observe.apply(incumbent).isEligible()) {
            setTarget(incumbent);
        }
    }

    /** A native attack may own the target while the participants remain eligible for the encounter. */
    public void observeNativeAttack(int tick, UUID nativeTarget, Function<UUID, TargetObservation> observe) {
        opening = false;
        selectionPending = false;
        if (!Objects.equals(target, nativeTarget)) {
            setTarget(null);
        }
        boolean incumbentEligible = target != null && retainsIncumbent(tick, observe.apply(target));
        if (taunter != null && (!taunter.equals(nativeTarget) || !incumbentEligible || remainingTauntTicks(tick) == 0)) {
            taunter = null;
        }
        if (incumbentEligible || scores.keySet().stream().anyMatch(id -> observe.apply(id).isEligible())) {
            resetStartedAt = null;
        } else if (resetStartedAt == null) {
            resetStartedAt = tick;
        }
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

    public void clearTaunt() {
        if (taunter != null) {
            taunter = null;
            decisionVersion++;
            selectionPending = true;
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
        decisionVersion++;
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
        UUID selected = bestTarget(incumbentEligible ? target : null, incumbent, scores, observe);
        setTarget(selected);
        updateReset(serverTick, selected != null);
        return selected;
    }

    /** Propose a target without changing state until the native attack commits. */
    public UUID chooseForNativeAttack(Function<UUID, TargetObservation> observe) {
        UUID incumbentId = target;
        Map<UUID, Double> currentScores = copyScores();
        TargetObservation incumbent = incumbentId == null ? TargetObservation.INELIGIBLE : observe.apply(incumbentId);
        return bestTarget(incumbent.isEligible() ? incumbentId : null, incumbent, currentScores, observe);
    }

    public void confirmNativeTarget(UUID actualTarget) {
        setTarget(actualTarget);
        opening = false;
    }

    public void observeParticipantAvailability(int tick, Predicate<UUID> isEligible) {
        selectionPending = false;
        updateReset(tick, scores.keySet().stream().anyMatch(isEligible));
    }

    private UUID bestTarget(UUID incumbentId, TargetObservation incumbent, Map<UUID, Double> currentScores,
                            Function<UUID, TargetObservation> observe) {
        boolean incumbentEligible = incumbentId != null;
        double incumbentScore = incumbentEligible ? currentScores.getOrDefault(incumbentId, 0.0) : 0.0;
        double ordinaryThreshold = threshold(incumbentScore, rules.ordinaryLead);
        UUID selected = incumbentId;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Map.Entry<UUID, Double> entry : currentScores.entrySet()) {
            UUID challenger = entry.getKey();
            double score = entry.getValue();
            if (challenger.equals(incumbentId) || score <= bestScore
                    || (incumbentEligible && score <= ordinaryThreshold)) {
                continue;
            }
            TargetObservation candidate = observe.apply(challenger);
            if (candidate.isEligible() && (!incumbentEligible
                    || score > cutoff(incumbentScore, ordinaryThreshold, incumbent, candidate))) {
                selected = challenger;
                bestScore = score;
            }
        }
        return selected;
    }

    /** A retained incumbent proves that no other challenger cleared its own lead in this decision. */
    public double takeoverAgainstRetainedTarget(UUID participant, TargetObservation incumbent,
                                                TargetObservation challenger) {
        if (target == null || target.equals(participant) || !scores.containsKey(participant)
                || !challenger.isEligible()) {
            return Double.NaN;
        }
        double incumbentScore = score(target);
        double boundary = cutoff(incumbentScore, threshold(incumbentScore, rules.ordinaryLead), incumbent, challenger);
        return boundary < Rules.MAX_SCORE ? boundary : Double.NaN;
    }

    public long decisionVersion() {
        return decisionVersion;
    }

    public Iterable<UUID> scoredParticipants() {
        return participants;
    }

    private void updateReset(int tick, boolean eligible) {
        if (eligible) {
            resetStartedAt = null;
        } else if (resetStartedAt == null) {
            resetStartedAt = tick;
        }
    }

    public Integer remainingResetTicks(int serverTick) {
        return resetStartedAt == null ? null : Math.max(0, rules.resetTicks - (serverTick - resetStartedAt));
    }

    public boolean resetDue(int serverTick) {
        return resetStartedAt != null && serverTick - resetStartedAt >= rules.resetTicks;
    }

    public UUID getTaunter() {
        return taunter;
    }

    public int remainingTauntTicks(int tick) {
        return taunter == null ? 0 : Math.max(0, rules.tauntTicks - (tick - tauntedAt));
    }

    public boolean isEligible(UUID participant, int tick, TargetObservation observation) {
        return observation.isEligible() || (participant.equals(target) && observation.sightLost
                && rules.sightTicks > 0 && (sightLostAt == null || tick - sightLostAt < rules.sightTicks));
    }

    private boolean retainsIncumbent(int tick, TargetObservation observation) {
        Integer previous = sightLostAt;
        if (!observation.sightLost) {
            sightLostAt = null;
        } else if (sightLostAt == null) {
            sightLostAt = tick;
        }
        if (!Objects.equals(previous, sightLostAt)) {
            decisionVersion++;
        }
        return target != null && scores.containsKey(target) && isEligible(target, tick, observation);
    }

    private void setTarget(UUID selected) {
        if (!Objects.equals(target, selected)) {
            sightLostAt = null;
            decisionVersion++;
        }
        target = selected;
    }

    private double cutoff(double incumbentScore, double ordinaryThreshold, TargetObservation incumbent,
                          TargetObservation candidate) {
        return incumbent.canAttackImmediately && !candidate.canAttackImmediately
                ? threshold(incumbentScore, chaseLead(candidate.distance)) : ordinaryThreshold;
    }

    private static double threshold(double score, double lead) {
        return Math.min(Rules.MAX_SCORE, score * (1.0 + lead));
    }

    private double chaseLead(double distance) {
        double progress = Math.max(0.0, Math.min(1.0, (distance - rules.chaseStart)
                / (rules.chaseEnd - rules.chaseStart)));
        // Rounding near the upper double limit can overshoot the configured endpoint.
        return Math.min(rules.maximumChaseLead,
                rules.ordinaryLead + progress * (rules.maximumChaseLead - rules.ordinaryLead));
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

    /** Validated rules supplied by the server when an encounter opens. */
    public static final class Rules {
        public static final double MAX_SCORE = Double.MAX_VALUE / 2.0;

        public final double openingThreat;
        public final double ordinaryLead;
        public final double maximumChaseLead;
        public final double chaseStart;
        public final double chaseEnd;
        public final int tauntTicks;
        public final int resetTicks;
        public final int sightTicks;

        public Rules(double openingThreat, double ordinaryLead, double maximumChaseLead, double chaseStart,
                     double chaseEnd, int tauntTicks, int resetTicks, int sightTicks) {
            this.openingThreat = openingThreat;
            this.ordinaryLead = ordinaryLead;
            this.maximumChaseLead = maximumChaseLead;
            this.chaseStart = chaseStart;
            this.chaseEnd = chaseEnd;
            this.tauntTicks = tauntTicks;
            this.resetTicks = resetTicks;
            this.sightTicks = sightTicks;
        }
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

        public boolean sameReach(TargetObservation other) {
            return other != null && isEligible() == other.isEligible() && sightLost == other.sightLost
                    && canAttackImmediately == other.canAttackImmediately && Double.compare(distance, other.distance) == 0;
        }
    }
}
