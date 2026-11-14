package com.mahghuuuls.threatworks.threat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.mahghuuuls.threatworks.TestRules.DEFAULT_RULES;
import static com.mahghuuuls.threatworks.threat.Encounter.TargetObservation.INELIGIBLE;
import static com.mahghuuuls.threatworks.threat.Encounter.TargetObservation.IN_REACH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncounterLifecycleTest {
    private final UUID player = new UUID(0, 1);
    private final UUID npc = new UUID(0, 2);

    @Test
    void nativeYieldDropsForcedFocusAndKeepsTheContinuousDeadlineAndScores() {
        Encounter encounter = Encounter.recordHit(null, player, npc, 5, 18, DEFAULT_RULES);
        Encounter.tryTaunt(encounter, player, npc, 10, id -> IN_REACH, DEFAULT_RULES);
        encounter.yieldToNative(11);
        encounter.yieldToNative(12);
        assertNull(encounter.getTaunter());
        assertNull(encounter.getTarget());
        assertEquals(Integer.valueOf(199), encounter.remainingResetTicks(12));
        encounter.resumeFromNative(npc, id -> IN_REACH);
        assertEquals(npc, encounter.getTarget());
        assertEquals(npc, encounter.selectTarget(20, id -> id.equals(player) ? INELIGIBLE : IN_REACH));
        assertEquals(10.0, encounter.copyScores().get(npc), 0.0);
        assertEquals(18.0, encounter.copyScores().get(player), 0.0);
        assertNull(encounter.remainingResetTicks(20));
    }

    @Test
    void nativeAttackKeepsValidTauntAndScoresWithoutStartingReset() {
        Encounter encounter = Encounter.recordHit(null, player, npc, 5, 18, DEFAULT_RULES);
        Encounter.tryTaunt(encounter, player, npc, 10, id -> IN_REACH, DEFAULT_RULES);

        encounter.observeNativeAttack(11, player, id -> IN_REACH);
        assertEquals(player, encounter.getTaunter());
        assertNull(encounter.remainingResetTicks(11));

        encounter.observeNativeAttack(12, npc, id -> id.equals(player) ? INELIGIBLE : IN_REACH);
        assertNull(encounter.getTaunter());
        assertNull(encounter.remainingResetTicks(12));
        assertEquals(18.0, encounter.copyScores().get(player), 0.0);

        encounter.observeNativeAttack(13, null, id -> INELIGIBLE);
        encounter.observeNativeAttack(14, null, id -> INELIGIBLE);
        assertEquals(Integer.valueOf(199), encounter.remainingResetTicks(14));
    }

    @Test
    void preparingATauntLeavesExistingScoresAndFocusUntouchedUntilApplied() {
        Encounter encounter = Encounter.recordHit(null, player, npc, 5, 18, DEFAULT_RULES);
        Encounter.Taunt pending = Encounter.prepareTaunt(encounter, npc, npc, 10, id -> IN_REACH, DEFAULT_RULES);
        assertEquals(10.0, encounter.copyScores().get(npc), 0.0);
        assertNull(encounter.getTaunter());
        pending.apply();
        assertEquals(18.0, encounter.copyScores().get(npc), 0.0);
        assertEquals(npc, encounter.getTaunter());
    }

    @Test
    void npcKeepsEncounterOpenWhenPlayerLeavesAndReturningPlayerUsesRetainedScore() {
        Encounter encounter = Encounter.recordHit(null, player, npc, 10, 17, DEFAULT_RULES);
        assertEquals(player, encounter.selectTarget(20, id -> IN_REACH));
        encounter.participantChanged(player);
        assertTrue(encounter.shouldReevaluate(21, 0));
        assertEquals(npc, encounter.selectTarget(21, id -> id.equals(player) ? INELIGIBLE : IN_REACH));
        assertNull(encounter.remainingResetTicks(250));
        assertFalse(encounter.resetDue(250));
        encounter.participantChanged(player);
        assertEquals(player, encounter.selectTarget(251, id -> IN_REACH));
        assertEquals(17.0, encounter.copyScores().get(player), 0.0);
        assertEquals(10.0, encounter.copyScores().get(npc), 0.0);
    }

    @Test
    void continuouslyIneligibleFightReleasesTargetAndExpiresAtItsOriginalDeadline() {
        Encounter encounter = Encounter.tryTaunt(null, player, null, 100, id -> IN_REACH, DEFAULT_RULES);
        encounter.grant(player, 12.0);
        assertNull(encounter.selectTarget(110, id -> INELIGIBLE));
        assertNull(encounter.getTaunter());
        assertEquals(Integer.valueOf(200), encounter.remainingResetTicks(110));
        assertNull(encounter.selectTarget(200, id -> INELIGIBLE));
        assertEquals(12.0, encounter.copyScores().get(player), 0.0);
        assertFalse(encounter.resetDue(309));
        assertTrue(encounter.shouldReevaluate(310, 1));
        assertNull(encounter.selectTarget(310, id -> INELIGIBLE));
        assertTrue(encounter.resetDue(310));
    }

    @Test
    void returningBeforeDeadlineCancelsResetAndNextLossStartsANewWindow() {
        Encounter encounter = Encounter.tryTaunt(null, player, null, 100, id -> IN_REACH, DEFAULT_RULES);
        encounter.grant(player, 12.0);
        assertNull(encounter.selectTarget(110, id -> INELIGIBLE));
        assertEquals(player, encounter.selectTarget(309, id -> IN_REACH));
        assertNull(encounter.remainingResetTicks(309));
        assertFalse(encounter.resetDue(310));
        assertEquals(12.0, encounter.copyScores().get(player), 0.0);
        assertNull(encounter.selectTarget(320, id -> INELIGIBLE));
        assertEquals(Integer.valueOf(200), encounter.remainingResetTicks(320));
    }
}
