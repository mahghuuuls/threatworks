package com.mahghuuuls.threatworks.threat;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static com.mahghuuuls.threatworks.TestRules.DEFAULT_RULES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.mahghuuuls.threatworks.threat.Encounter.TargetObservation.INELIGIBLE;
import static com.mahghuuuls.threatworks.threat.Encounter.TargetObservation.IN_REACH;
import static com.mahghuuuls.threatworks.threat.Encounter.TargetObservation.obscured;
import static com.mahghuuuls.threatworks.threat.Encounter.TargetObservation.pursuit;

class EncounterTest {
    @Test
    void finiteLeadAtTheFarBandAllowsChallengingAZeroSeed() {
        Encounter.Rules rules = new Encounter.Rules(0, 2.9937604643020797e292, Double.MAX_VALUE,
                8, 16, 60, 200, 40);
        Encounter encounter = Encounter.recordHit(null, player, incumbent, 100, 1, rules);
        assertEquals(player, encounter.selectTarget(104, id -> id.equals(incumbent) ? IN_REACH : pursuit(16)));
    }

    private final UUID player = new UUID(0, 1);
    private final UUID incumbent = new UUID(0, 2);
    private final UUID secondPlayer = new UUID(0, 3);

    @Test
    void positiveHealthLossSeedsAndHoldsTheOpeningTarget() {
        double loss = Encounter.healthLost(20.0F, 7.0F);
        Encounter encounter = Encounter.recordHit(null, player, incumbent, 100, loss, DEFAULT_RULES);

        assertEquals(incumbent, encounter.getTarget());
        assertTrue(encounter.holdsOpeningTarget(103));
        assertFalse(encounter.holdsOpeningTarget(104));
        assertEquals(10.0, encounter.copyScores().get(incumbent), 0.0);
        assertEquals(13.0, encounter.copyScores().get(player), 0.0);
    }

    @Test
    void subsequentHitsDoNotReseedOrReplaceTheHeldTarget() {
        Encounter encounter = Encounter.recordHit(null, player, incumbent, 100, 13.0, DEFAULT_RULES);
        Encounter.recordHit(encounter, secondPlayer, player, 100, 4.0, DEFAULT_RULES);
        Encounter.recordHit(encounter, player, secondPlayer, 100, 1.5, DEFAULT_RULES);

        assertEquals(incumbent, encounter.getTarget());
        assertEquals(10.0, encounter.copyScores().get(incumbent), 0.0);
        assertEquals(14.5, encounter.copyScores().get(player), 0.0);
        assertEquals(4.0, encounter.copyScores().get(secondPlayer), 0.0);
    }

    @Test
    void zeroOrInvalidLossLeavesStateUnchanged() {
        assertNull(Encounter.recordHit(null, player, incumbent, 100, Encounter.healthLost(20.0F, 20.0F), DEFAULT_RULES));
        assertNull(Encounter.recordHit(null, player, incumbent, 100, Double.POSITIVE_INFINITY, DEFAULT_RULES));

        Encounter encounter = Encounter.recordHit(null, player, incumbent, 100, 2.0, DEFAULT_RULES);
        assertSame(encounter, Encounter.recordHit(encounter, secondPlayer, incumbent, 100, 0.0, DEFAULT_RULES));
        assertFalse(encounter.copyScores().containsKey(secondPlayer));
    }

    @Test
    void measuresRegularHealthLossAndCapsOverkill() {
        assertEquals(2.0, Encounter.healthLost(20.0F, 18.0F), 0.0);
        assertEquals(3.0, Encounter.healthLost(3.0F, -7.0F), 0.0);
        assertEquals(0.0, Encounter.healthLost(5.0F, 10.0F), 0.0);
    }

    @Test
    void absentIncumbentDoesNotSeedAnyOtherActor() {
        Encounter encounter = Encounter.recordHit(null, player, null, 100, 5.0, DEFAULT_RULES);

        assertNull(encounter.getTarget());
        assertEquals(1, encounter.copyScores().size());
        assertEquals(5.0, encounter.copyScores().get(player), 0.0);
    }

    @Test
    void releasingTheTargetPreservesTheScoresAndTheInspectionIsASnapshot() {
        Encounter encounter = Encounter.recordHit(null, player, incumbent, 100, 3.0, DEFAULT_RULES);
        Map<UUID, Double> snapshot = encounter.copyScores();

        encounter.selectTarget(105, id -> INELIGIBLE);
        Encounter.recordHit(encounter, player, incumbent, 100, 1.0, DEFAULT_RULES);

        assertNull(encounter.getTarget());
        assertEquals(10.0, encounter.copyScores().get(incumbent), 0.0);
        assertEquals(3.0, snapshot.get(player), 0.0);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
    }

    @Test
    void equalThresholdHoldsAtBothBandEndsAndTheMidpoint() {
        double[] distances = {4.0, 8.0, 12.0, 16.0, 20.0};
        double[] thresholds = {12.0, 12.0, 13.0, 14.0, 14.0};
        for (int i = 0; i < distances.length; i++) {
            double distance = distances[i];
            double threshold = thresholds[i];
            Encounter exact = Encounter.recordHit(null, player, incumbent, 100, threshold, DEFAULT_RULES);
            Encounter above = Encounter.recordHit(null, player, incumbent, 100, Math.nextUp(threshold), DEFAULT_RULES);
            assertEquals(incumbent, exact.selectTarget(105, id -> id.equals(incumbent) ? IN_REACH : pursuit(distance)));
            assertEquals(player, above.selectTarget(105, id -> id.equals(incumbent) ? IN_REACH : pursuit(distance)));
        }
        Encounter ordinary = Encounter.recordHit(null, player, incumbent, 100, 12.0, DEFAULT_RULES);
        assertEquals(incumbent, ordinary.selectTarget(105, id -> IN_REACH));
    }

    @Test
    void winningChallengerQualifiesAgainstItsOwnLeadAndTiesStayInRecordOrder() {
        UUID thirdPlayer = new UUID(0, 4);
        Encounter encounter = Encounter.recordHit(null, player, incumbent, 100, 13.9, DEFAULT_RULES);
        Encounter.recordHit(encounter, secondPlayer, null, 100, 13.0, DEFAULT_RULES);
        Encounter.recordHit(encounter, thirdPlayer, null, 100, 13.0, DEFAULT_RULES);

        assertEquals(secondPlayer, encounter.selectTarget(105, id -> id.equals(player) ? pursuit(16.0) : IN_REACH));
    }

    @Test
    void twoPursuitsUseTheOrdinaryLead() {
        Encounter encounter = Encounter.recordHit(null, player, incumbent, 100, 12.1, DEFAULT_RULES);

        assertEquals(player, encounter.selectTarget(105, id -> pursuit(16.0)));
    }

    @Test
    void motionAloneCanQualifyOnTheNextScheduledCheck() {
        Encounter encounter = Encounter.recordHit(null, player, incumbent, 100, 13.0, DEFAULT_RULES);
        assertEquals(incumbent, encounter.selectTarget(105, id -> id.equals(player) ? pursuit(16.0) : IN_REACH));

        assertFalse(encounter.shouldReevaluate(106, 0));
        assertTrue(encounter.shouldReevaluate(110, 0));
        assertEquals(player, encounter.selectTarget(110, id -> IN_REACH));
        assertEquals(13.0, encounter.copyScores().get(player), 0.0);
    }

    @Test
    void unreachableHighScoreCannotWinAndInvalidIncumbentNeedsNoLead() {
        Encounter encounter = Encounter.recordHit(null, player, incumbent, 100, 20.0, DEFAULT_RULES);
        Encounter.recordHit(encounter, secondPlayer, null, 100, 1.0, DEFAULT_RULES);

        assertEquals(incumbent, encounter.selectTarget(105, id -> id.equals(player) ? INELIGIBLE : IN_REACH));
        assertEquals(secondPlayer, encounter.selectTarget(106, id -> id.equals(secondPlayer) ? IN_REACH : INELIGIBLE));
    }

    @Test
    void onlyAnIncumbentCanUseSightGraceAndItExpiresAfterFortyTicks() {
        Encounter encounter = Encounter.recordHit(null, player, incumbent, 100, 1.0, DEFAULT_RULES);

        assertEquals(incumbent, encounter.selectTarget(105, id -> id.equals(incumbent) ? obscured(false) : IN_REACH));
        assertEquals(incumbent, encounter.selectTarget(144, id -> id.equals(incumbent) ? obscured(false) : IN_REACH));
        assertEquals(player, encounter.selectTarget(145, id -> id.equals(incumbent) ? obscured(false) : IN_REACH));

        Encounter challenger = Encounter.recordHit(null, player, incumbent, 100, 20.0, DEFAULT_RULES);
        assertEquals(incumbent, challenger.selectTarget(105, id -> id.equals(player) ? obscured(false) : IN_REACH));
        assertEquals(player, challenger.selectTarget(106, id -> id.equals(incumbent) ? obscured(false) : IN_REACH));
    }

    @Test
    void anObscuredIncumbentStillAbleToStrikeKeepsTheChaseLead() {
        Encounter encounter = Encounter.recordHit(null, player, incumbent, 100, 13.0, DEFAULT_RULES);

        assertEquals(incumbent, encounter.selectTarget(105, id -> id.equals(incumbent) ? obscured(true) : pursuit(16.0)));
        assertEquals(player, encounter.selectTarget(106, id -> id.equals(incumbent) ? obscured(false) : pursuit(16.0)));
    }
}
