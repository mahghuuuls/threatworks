package com.mahghuuuls.threatworks.threat;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

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
    private final UUID player = new UUID(0, 1);
    private final UUID incumbent = new UUID(0, 2);
    private final UUID secondPlayer = new UUID(0, 3);

    @Test
    void positiveHealthLossSeedsAndHoldsTheOpeningTarget() {
        double loss = Encounter.healthLost(20.0F, 7.0F);
        Encounter encounter = Encounter.recordPlayerHit(null, player, incumbent, 100, loss);

        assertEquals(incumbent, encounter.getTarget());
        assertTrue(encounter.holdsOpeningTarget(103));
        assertFalse(encounter.holdsOpeningTarget(104));
        assertEquals(10.0, encounter.copyScores().get(incumbent), 0.0);
        assertEquals(13.0, encounter.copyScores().get(player), 0.0);
    }

    @Test
    void subsequentHitsDoNotReseedOrReplaceTheHeldTarget() {
        Encounter encounter = Encounter.recordPlayerHit(null, player, incumbent, 100, 13.0);
        Encounter.recordPlayerHit(encounter, secondPlayer, player, 100, 4.0);
        Encounter.recordPlayerHit(encounter, player, secondPlayer, 100, 1.5);

        assertEquals(incumbent, encounter.getTarget());
        assertEquals(10.0, encounter.copyScores().get(incumbent), 0.0);
        assertEquals(14.5, encounter.copyScores().get(player), 0.0);
        assertEquals(4.0, encounter.copyScores().get(secondPlayer), 0.0);
    }

    @Test
    void zeroOrInvalidLossLeavesStateUnchanged() {
        assertNull(Encounter.recordPlayerHit(null, player, incumbent, 100, Encounter.healthLost(20.0F, 20.0F)));
        assertNull(Encounter.recordPlayerHit(null, player, incumbent, 100, Double.POSITIVE_INFINITY));

        Encounter encounter = Encounter.recordPlayerHit(null, player, incumbent, 100, 2.0);
        assertSame(encounter, Encounter.recordPlayerHit(encounter, secondPlayer, incumbent, 100, 0.0));
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
        Encounter encounter = Encounter.recordPlayerHit(null, player, null, 100, 5.0);

        assertNull(encounter.getTarget());
        assertEquals(1, encounter.copyScores().size());
        assertEquals(5.0, encounter.copyScores().get(player), 0.0);
    }

    @Test
    void releasingTheTargetPreservesTheScoresAndTheInspectionIsASnapshot() {
        Encounter encounter = Encounter.recordPlayerHit(null, player, incumbent, 100, 3.0);
        Map<UUID, Double> snapshot = encounter.copyScores();

        encounter.selectTarget(105, id -> INELIGIBLE);
        Encounter.recordPlayerHit(encounter, player, incumbent, 100, 1.0);

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
            Encounter exact = Encounter.recordPlayerHit(null, player, incumbent, 100, threshold);
            Encounter above = Encounter.recordPlayerHit(null, player, incumbent, 100, Math.nextUp(threshold));
            assertEquals(incumbent, exact.selectTarget(105, id -> id.equals(incumbent) ? IN_REACH : pursuit(distance)));
            assertEquals(player, above.selectTarget(105, id -> id.equals(incumbent) ? IN_REACH : pursuit(distance)));
        }
        Encounter ordinary = Encounter.recordPlayerHit(null, player, incumbent, 100, 12.0);
        assertEquals(incumbent, ordinary.selectTarget(105, id -> IN_REACH));
    }

    @Test
    void winningChallengerQualifiesAgainstItsOwnLeadAndTiesStayInRecordOrder() {
        UUID thirdPlayer = new UUID(0, 4);
        Encounter encounter = Encounter.recordPlayerHit(null, player, incumbent, 100, 13.9);
        Encounter.recordPlayerHit(encounter, secondPlayer, null, 100, 13.0);
        Encounter.recordPlayerHit(encounter, thirdPlayer, null, 100, 13.0);

        assertEquals(secondPlayer, encounter.selectTarget(105, id -> id.equals(player) ? pursuit(16.0) : IN_REACH));
    }

    @Test
    void twoPursuitsUseTheOrdinaryLead() {
        Encounter encounter = Encounter.recordPlayerHit(null, player, incumbent, 100, 12.1);

        assertEquals(player, encounter.selectTarget(105, id -> pursuit(16.0)));
    }

    @Test
    void motionAloneCanQualifyOnTheNextScheduledCheck() {
        Encounter encounter = Encounter.recordPlayerHit(null, player, incumbent, 100, 13.0);
        assertEquals(incumbent, encounter.selectTarget(105, id -> id.equals(player) ? pursuit(16.0) : IN_REACH));

        assertFalse(encounter.shouldReevaluate(106, 0));
        assertTrue(encounter.shouldReevaluate(110, 0));
        assertEquals(player, encounter.selectTarget(110, id -> IN_REACH));
        assertEquals(13.0, encounter.copyScores().get(player), 0.0);
    }

    @Test
    void unreachableHighScoreCannotWinAndInvalidIncumbentNeedsNoLead() {
        Encounter encounter = Encounter.recordPlayerHit(null, player, incumbent, 100, 20.0);
        Encounter.recordPlayerHit(encounter, secondPlayer, null, 100, 1.0);

        assertEquals(incumbent, encounter.selectTarget(105, id -> id.equals(player) ? INELIGIBLE : IN_REACH));
        assertEquals(secondPlayer, encounter.selectTarget(106, id -> id.equals(secondPlayer) ? IN_REACH : INELIGIBLE));
    }

    @Test
    void onlyAnIncumbentCanUseSightGraceAndItExpiresAfterFortyTicks() {
        Encounter encounter = Encounter.recordPlayerHit(null, player, incumbent, 100, 1.0);

        assertEquals(incumbent, encounter.selectTarget(105, id -> id.equals(incumbent) ? obscured(false) : IN_REACH));
        assertEquals(incumbent, encounter.selectTarget(144, id -> id.equals(incumbent) ? obscured(false) : IN_REACH));
        assertEquals(player, encounter.selectTarget(145, id -> id.equals(incumbent) ? obscured(false) : IN_REACH));

        Encounter challenger = Encounter.recordPlayerHit(null, player, incumbent, 100, 20.0);
        assertEquals(incumbent, challenger.selectTarget(105, id -> id.equals(player) ? obscured(false) : IN_REACH));
        assertEquals(player, challenger.selectTarget(106, id -> id.equals(incumbent) ? obscured(false) : IN_REACH));
    }

    @Test
    void anObscuredIncumbentStillAbleToStrikeKeepsTheChaseLead() {
        Encounter encounter = Encounter.recordPlayerHit(null, player, incumbent, 100, 13.0);

        assertEquals(incumbent, encounter.selectTarget(105, id -> id.equals(incumbent) ? obscured(true) : pursuit(16.0)));
        assertEquals(player, encounter.selectTarget(106, id -> id.equals(incumbent) ? obscured(false) : pursuit(16.0)));
    }
}
