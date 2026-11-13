package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.threat.Encounter;
import com.mahghuuuls.threatworks.threat.Encounter.TargetObservation;
import net.minecraft.entity.Entity;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static com.mahghuuuls.threatworks.TestRules.DEFAULT_RULES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class WorldEncountersTest {
    @Test
    void actionsCannotReviveAnEncounterAtTheNativeResetDeadline() {
        UUID id = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        Encounter retained = Encounter.recordHit(null, attacker, null, 1, 4.0, DEFAULT_RULES);
        world.encounters.put(id, retained);
        retained.yieldToNative(10);
        assertSame(retained, world.encounterForAction(id, 209));
        assertNull(world.encounterForAction(id, 210));
        assertFalse(world.encounters.containsKey(id));
    }

    private final UUID mob = new UUID(0, 1);
    private final UUID player = new UUID(0, 2);
    private final UUID incumbent = new UUID(0, 3);
    private final WorldEncounters world = new WorldEncounters();

    @Test
    void samplesOnlyTrackedEntriesInTheWorldTickLoop() {
        WorkSample sample = new WorkSample(2);
        world.workSample = sample;
        world.tick(40, (id, encounter) -> {
            fail("Idle world must not visit a mob");
            return false;
        });
        world.recordHit(mob, () -> player, 20, true, 20, 19, DEFAULT_RULES, () -> 1, () -> null);
        world.tick(41, (id, encounter) -> {
            assertEquals(mob, id);
            return true;
        });
        assertTrue(sample.complete());
        assertTrue(sample.report().contains("ticks=2 activeMin=0 activeMax=0 visited=1"));
        world.tick(42, (id, encounter) -> {
            fail("Removed encounter must not be visited again");
            return false;
        });
        assertTrue(sample.report().contains("lastTick=41"));
    }

    @Test
    void selectionSamplesOnlyScoreQualifiedChallengersAndRetainsTheOthers() {
        UUID belowThreshold = new UUID(0, 4);
        UUID aboveThreshold = new UUID(0, 5);
        world.recordHit(mob, () -> player, 20, true, 20, 19, DEFAULT_RULES, () -> 1, () -> incumbent);
        Encounter encounter = world.encounters.get(mob);
        encounter.grant(belowThreshold, 11);
        encounter.grant(aboveThreshold, 30);
        WorkSample sample = new WorkSample(1);
        assertEquals(incumbent, world.select(encounter, 50, sample, candidate -> {
            if (incumbent.equals(candidate)) {
                return TargetObservation.IN_REACH;
            }
            assertEquals(aboveThreshold, candidate, "Lower score must not even reach the mob policy");
            return TargetObservation.INELIGIBLE;
        }));
        sample.endTick(50, 1);
        assertTrue(sample.report().contains("selections=1 observations=2"));
        assertEquals(4, encounter.participantCount());
    }

    @Test
    void nonPlayerDamageCreditsAnExistingFightWithoutOpeningOne() {
        world.recordHit(mob, () -> incumbent, 20, false, 20, 19, DEFAULT_RULES, () -> 1, this::unexpectedOpeningLookup);
        assertFalse(world.encounters.containsKey(mob));
        world.recordHit(mob, () -> player, 21, true, 19, 18, DEFAULT_RULES, () -> 1, () -> null);
        world.recordHit(mob, () -> incumbent, 22, false, 18, 17, DEFAULT_RULES, () -> 2, this::unexpectedOpeningLookup);
        assertEquals(1, world.encounters.get(mob).copyScores().get(player), 0);
        assertEquals(2, world.encounters.get(mob).copyScores().get(incumbent), 0);
    }

    @Test
    void nonPlayerHitCannotCarryCreditIntoAReplacementEncounter() {
        world.recordHit(mob, () -> player, 20, true, 20, 19, DEFAULT_RULES, () -> 1, () -> null);
        world.recordHit(mob, () -> {
            world.encounters.remove(mob);
            world.recordHit(mob, () -> player, 21, true, 19, 18, DEFAULT_RULES, () -> 1, () -> null);
            return incumbent;
        }, 21, false, 18, 17, DEFAULT_RULES, () -> 1, this::unexpectedOpeningLookup);
        assertEquals(1, world.encounters.get(mob).participantCount());
    }

    @Test
    void extremeAcceptedPointsStillCreditHealthLossAtTheScoreLimit() {
        // Division rounds the theoretical safe boundary up to this exact float-derived loss.
        world.recordHit(mob, () -> player, 20, true, 1024.0F, 129.685302734375F, DEFAULT_RULES,
                () -> 2.0101348444331488e305, () -> null);
        Encounter encounter = world.encounters.get(mob);
        assertNotNull(encounter, "A qualifying hit must still open an encounter at the score limit");
        assertEquals(Encounter.Rules.MAX_SCORE, encounter.copyScores().get(player), 0.0);
    }

    @Test
    void onlyAppliedHealthLossCreatesAndUpdatesTheWorldEncounter() {
        world.recordHit(mob, () -> player, 20, true, 20.0F, 20.0F, DEFAULT_RULES, () -> {
            throw new AssertionError("No health loss must skip equipment collection");
        }, this::unexpectedOpeningLookup);
        assertFalse(world.encounters.containsKey(mob));

        world.recordHit(mob, () -> player, 22, true, 20.0F, 7.0F, DEFAULT_RULES, () -> 1, () -> incumbent);
        world.recordHit(mob, () -> player, 23, true, 7.0F, 7.0F, DEFAULT_RULES, () -> 1, this::unexpectedOpeningLookup);
        world.recordHit(mob, () -> player, 24, true, 7.0F, 4.0F, DEFAULT_RULES, () -> 2, this::unexpectedOpeningLookup);

        assertEquals(10.0, world.encounters.get(mob).copyScores().get(incumbent), 0.0);
        assertEquals(19.0, world.encounters.get(mob).copyScores().get(player), 0.0);
    }

    @Test
    void onlyAChangedTargetIsBlockedWhileAnEligibleTargetIsControlled() {
        assertFalse(world.blocksTargetWrite(mob, true, incumbent::equals));
        world.recordHit(mob, () -> player, 22, true, 20.0F, 7.0F, DEFAULT_RULES, () -> 1, () -> incumbent);

        assertTrue(world.blocksTargetWrite(mob, true, incumbent::equals));
        assertFalse(world.blocksTargetWrite(mob, false, incumbent::equals));
        assertFalse(world.blocksTargetWrite(mob, true, held -> false));
        world.encounters.get(mob).selectTarget(26, id -> TargetObservation.IN_REACH);
        assertTrue(world.blocksTargetWrite(mob, true, player::equals));
        world.encounters.get(mob).selectTarget(27, id -> TargetObservation.INELIGIBLE);
        assertFalse(world.blocksTargetWrite(mob, true, id -> true));
    }

    @Test
    void aCoordinatorClearIsAuthorizedOnceEvenForAnIdenticalNestedWrite() {
        EncounterCoordinator.TargetWrite write = new EncounterCoordinator.TargetWrite(null, null, null);

        assertTrue(write.consume(null));
        assertFalse(write.consume(null));
    }

    @Test
    void lethalCreditIsCappedAndEntityRemovalDiscardsItsStoredEncounter() {
        Bootstrap.register();
        Entity entity = new Entity(null) {
            @Override
            protected void entityInit() {
            }

            @Override
            protected void readEntityFromNBT(NBTTagCompound data) {
            }

            @Override
            protected void writeEntityToNBT(NBTTagCompound data) {
            }
        };
        entity.setUniqueId(mob);
        world.recordHit(mob, () -> player, 22, true, 3.0F, -7.0F, DEFAULT_RULES, () -> 1, () -> incumbent);
        assertEquals(3.0, world.encounters.get(mob).copyScores().get(player), 0.0);

        world.onEntityRemoved(entity);

        assertFalse(world.encounters.containsKey(mob));
        assertFalse(world.blocksTargetWrite(mob, true, incumbent::equals));
        world.onEntityAdded(entity);
        assertFalse(world.encounters.containsKey(mob));
        world.recordHit(mob, () -> player, 99, true, 20.0F, 18.0F, DEFAULT_RULES, () -> 1, () -> null);
        assertEquals(2.0, world.encounters.get(mob).copyScores().get(player), 0.0);
    }

    @Test
    void playerDeathRemovesTheirScoresAcrossWorldsAndKeepsNpcCombat() {
        WorldEncounters otherWorld = new WorldEncounters();
        world.recordHit(mob, () -> player, 20, true, 20.0F, 3.0F, DEFAULT_RULES, () -> 1, () -> incumbent);
        otherWorld.recordHit(mob, () -> player, 20, true, 20.0F, 3.0F, DEFAULT_RULES, () -> 1, () -> null);
        world.encounters.get(mob).selectTarget(25, id -> TargetObservation.IN_REACH);
        otherWorld.encounters.get(mob).selectTarget(25, id -> TargetObservation.IN_REACH);

        WorldEncounters.removePlayerScores(Arrays.asList(world, otherWorld), player);

        assertEquals(1, world.encounters.get(mob).participantCount());
        assertEquals(incumbent, world.encounters.get(mob).selectTarget(26, id -> TargetObservation.IN_REACH));
        assertEquals(0, otherWorld.encounters.get(mob).participantCount());
        assertNull(otherWorld.encounters.get(mob).selectTarget(26, id -> TargetObservation.IN_REACH));
    }

    private UUID unexpectedOpeningLookup() {
        return fail("Only a positive opening hit needs the incumbent");
    }
}
