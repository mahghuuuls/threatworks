package com.mahghuuuls.threatworks.threat;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static com.mahghuuuls.threatworks.threat.Encounter.GrantResult.APPLIED;
import static com.mahghuuuls.threatworks.threat.Encounter.GrantResult.INVALID_AMOUNT;
import static com.mahghuuuls.threatworks.threat.Encounter.GrantResult.SCORE_LIMIT;
import static com.mahghuuuls.threatworks.threat.Encounter.TargetObservation.INELIGIBLE;
import static com.mahghuuuls.threatworks.threat.Encounter.TargetObservation.IN_REACH;
import static com.mahghuuuls.threatworks.threat.Encounter.TargetObservation.obscured;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncounterActionsTest {
    private final UUID player = new UUID(0, 1);
    private final UUID incumbent = new UUID(0, 2);
    private final UUID secondPlayer = new UUID(0, 3);

    @Test
    void aGrantChangesOnlyTheNamedScoreAndSchedulesSelection() {
        Encounter encounter = Encounter.recordPlayerHit(null, player, incumbent, 100, 5.0);
        assertEquals(incumbent, encounter.selectTarget(105, id -> IN_REACH));
        assertFalse(encounter.shouldReevaluate(106, 0));

        assertEquals(APPLIED, encounter.grant(secondPlayer, 13.0));
        assertEquals(10.0, encounter.copyScores().get(incumbent), 0.0);
        assertEquals(5.0, encounter.copyScores().get(player), 0.0);
        assertEquals(13.0, encounter.copyScores().get(secondPlayer), 0.0);
        assertTrue(encounter.shouldReevaluate(106, 0));
        assertEquals(secondPlayer, encounter.selectTarget(106, id -> IN_REACH));
    }

    @Test
    void invalidAndUnrepresentableGrantsLeaveScoresAndSelectionUntouched() {
        Encounter encounter = Encounter.recordPlayerHit(null, player, incumbent, 100, 5.0);
        encounter.selectTarget(105, id -> IN_REACH);
        Map<UUID, Double> before = encounter.copyScores();

        for (double amount : new double[]{0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY}) {
            assertEquals(INVALID_AMOUNT, encounter.grant(secondPlayer, amount));
        }
        assertEquals(SCORE_LIMIT, encounter.grant(secondPlayer, Double.MAX_VALUE));
        assertEquals(SCORE_LIMIT, encounter.grant(player, Double.MIN_VALUE));
        assertEquals(before, encounter.copyScores());
        assertFalse(encounter.shouldReevaluate(106, 0));
    }

    @Test
    void damageAtTheScoreLimitCannotOverflow() {
        Encounter encounter = Encounter.recordPlayerHit(null, player, incumbent, 100, 1.0);
        double limit = Double.MAX_VALUE / 2.0;
        assertEquals(APPLIED, encounter.grant(secondPlayer, limit));
        assertEquals(SCORE_LIMIT, encounter.grant(secondPlayer, limit));
        Encounter.recordPlayerHit(encounter, secondPlayer, null, 105, limit);

        assertEquals(limit, encounter.copyScores().get(secondPlayer), 0.0);
        assertEquals(secondPlayer, encounter.selectTarget(105, id -> IN_REACH));
        assertEquals(secondPlayer, encounter.selectTarget(110, id -> IN_REACH));
    }

    @Test
    void openingTauntSeedsOnlyAnEligibleIncumbentAndTakesControlImmediately() {
        assertNull(Encounter.tryTaunt(null, player, incumbent, 100, id -> INELIGIBLE));
        Encounter encounter = Encounter.tryTaunt(null, player, incumbent, 100, id -> IN_REACH);
        assertEquals(player, encounter.getTarget());
        assertEquals(player, encounter.getTaunter());
        assertFalse(encounter.holdsOpeningTarget(100));
        assertEquals(60, encounter.remainingTauntTicks(100));
        assertEquals(10.0, encounter.copyScores().get(incumbent), 0.0);
        assertEquals(10.0, encounter.copyScores().get(player), 0.0);

        Encounter noSeed = Encounter.tryTaunt(null, player, incumbent, 100,
                id -> id.equals(player) ? IN_REACH : INELIGIBLE);
        assertEquals(1, noSeed.participantCount());
        assertEquals(0.0, noSeed.copyScores().get(player), 0.0);
    }

    @Test
    void tauntMatchesTheHighestEligibleScoreAndPreservesAHigherTaunterScore() {
        Encounter encounter = Encounter.recordPlayerHit(null, player, incumbent, 100, 3.0);
        assertEquals(APPLIED, encounter.grant(secondPlayer, 30.0));
        assertSame(encounter, Encounter.tryTaunt(encounter, player, incumbent, 101,
                id -> id.equals(secondPlayer) ? INELIGIBLE : IN_REACH));
        assertEquals(10.0, encounter.copyScores().get(player), 0.0);
        assertEquals(30.0, encounter.copyScores().get(secondPlayer), 0.0);

        assertSame(encounter, Encounter.tryTaunt(encounter, secondPlayer, player, 102,
                id -> id.equals(player) ? INELIGIBLE : IN_REACH));
        assertEquals(30.0, encounter.copyScores().get(secondPlayer), 0.0);
        assertEquals(secondPlayer, encounter.getTarget());
        assertEquals(10.0, encounter.copyScores(1).get(incumbent), 0.0);
        assertEquals(1, encounter.copyScores(1).size());
    }

    @Test
    void latestValidTauntReplacesTheTimerAndInvalidTauntKeepsItAndAllScores() {
        Encounter encounter = Encounter.tryTaunt(null, player, incumbent, 100, id -> IN_REACH);
        assertSame(encounter, Encounter.tryTaunt(encounter, secondPlayer, player, 120, id -> IN_REACH));
        Map<UUID, Double> before = encounter.copyScores();
        assertNull(Encounter.tryTaunt(encounter, incumbent, secondPlayer, 130,
                id -> id.equals(incumbent) ? INELIGIBLE : IN_REACH));
        assertEquals(before, encounter.copyScores());
        assertEquals(secondPlayer, encounter.getTaunter());
        assertEquals(50, encounter.remainingTauntTicks(130));
        assertEquals(APPLIED, encounter.grant(incumbent, 5.0));
        assertEquals(secondPlayer, encounter.selectTarget(160, id -> IN_REACH));
        assertEquals(secondPlayer, encounter.selectTarget(179, id -> IN_REACH));
        assertTrue(encounter.shouldReevaluate(180, 1));
        assertEquals(incumbent, encounter.selectTarget(180, id -> IN_REACH));
        assertNull(encounter.getTaunter());
    }

    @Test
    void expiredFocusKeepsItsCurrentTargetWhenNoChallengerQualifies() {
        Encounter encounter = Encounter.tryTaunt(null, player, incumbent, 100, id -> IN_REACH);

        assertEquals(player, encounter.selectTarget(160, id -> IN_REACH));
        assertNull(encounter.getTaunter());
        assertEquals(0, encounter.remainingTauntTicks(160));
    }

    @Test
    void tauntUsesExistingSightGraceAndCannotRenewItAfterSightLoss() {
        Encounter encounter = Encounter.tryTaunt(null, player, incumbent, 100, id -> IN_REACH);
        assertEquals(player, encounter.selectTarget(105, id -> id.equals(player) ? obscured(true) : IN_REACH));
        assertSame(encounter, Encounter.tryTaunt(encounter, player, player, 130,
                id -> id.equals(player) ? obscured(true) : IN_REACH));
        assertEquals(player, encounter.selectTarget(144, id -> id.equals(player) ? obscured(true) : IN_REACH));
        assertNull(Encounter.tryTaunt(encounter, player, player, 145,
                id -> id.equals(player) ? obscured(true) : IN_REACH));
        assertEquals(incumbent, encounter.selectTarget(145, id -> id.equals(player) ? obscured(true) : IN_REACH));
        assertNull(encounter.getTaunter());
    }

    @Test
    void anInvalidTaunterLosesFocusBeforeTheTimerExpires() {
        Encounter encounter = Encounter.tryTaunt(null, player, incumbent, 100, id -> IN_REACH);

        assertEquals(incumbent, encounter.selectTarget(101, id -> id.equals(player) ? INELIGIBLE : IN_REACH));
        assertNull(encounter.getTaunter());
        assertEquals(10.0, encounter.copyScores().get(player), 0.0);
    }
}
