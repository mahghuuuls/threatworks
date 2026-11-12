package com.mahghuuuls.threatworks.threat;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncounterTest {
    private final UUID player = new UUID(0, 1);
    private final UUID incumbent = new UUID(0, 2);
    private final UUID secondPlayer = new UUID(0, 3);

    @Test
    void positiveHealthLossSeedsAndHoldsTheOpeningTarget() {
        double loss = Encounter.healthLost(20.0F, 7.0F);
        Encounter encounter = Encounter.recordPlayerHit(null, player, incumbent, 100, loss);

        assertEquals(incumbent, encounter.getHeldTarget());
        assertEquals(10.0, encounter.copyScores().get(incumbent), 0.0);
        assertEquals(13.0, encounter.copyScores().get(player), 0.0);
    }

    @Test
    void subsequentHitsDoNotReseedOrReplaceTheHeldTarget() {
        Encounter encounter = Encounter.recordPlayerHit(null, player, incumbent, 100, 13.0);
        Encounter.recordPlayerHit(encounter, secondPlayer, player, 100, 4.0);
        Encounter.recordPlayerHit(encounter, player, secondPlayer, 100, 1.5);

        assertEquals(incumbent, encounter.getHeldTarget());
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

        assertNull(encounter.getHeldTarget());
        assertEquals(1, encounter.copyScores().size());
        assertEquals(5.0, encounter.copyScores().get(player), 0.0);
    }

    @Test
    void releasingTheTargetPreservesTheScoresAndTheInspectionIsASnapshot() {
        Encounter encounter = Encounter.recordPlayerHit(null, player, incumbent, 100, 3.0);
        Map<UUID, Double> snapshot = encounter.copyScores();

        encounter.releaseTarget();
        Encounter.recordPlayerHit(encounter, player, incumbent, 100, 1.0);

        assertNull(encounter.getHeldTarget());
        assertEquals(10.0, encounter.copyScores().get(incumbent), 0.0);
        assertEquals(3.0, snapshot.get(player), 0.0);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
    }
}
