package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.threat.Encounter.TargetObservation;
import net.minecraft.entity.Entity;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class WorldEncountersTest {
    private final UUID mob = new UUID(0, 1);
    private final UUID player = new UUID(0, 2);
    private final UUID incumbent = new UUID(0, 3);
    private final WorldEncounters world = new WorldEncounters();

    @Test
    void onlyAppliedHealthLossCreatesAndUpdatesTheWorldEncounter() {
        world.recordPlayerHit(mob, player, 20, 20.0F, 20.0F, this::unexpectedOpeningLookup);
        assertFalse(world.encounters.containsKey(mob));

        world.recordPlayerHit(mob, player, 22, 20.0F, 7.0F, () -> incumbent);
        world.recordPlayerHit(mob, player, 23, 7.0F, 7.0F, this::unexpectedOpeningLookup);
        world.recordPlayerHit(mob, player, 24, 7.0F, 4.0F, this::unexpectedOpeningLookup);

        assertEquals(10.0, world.encounters.get(mob).copyScores().get(incumbent), 0.0);
        assertEquals(16.0, world.encounters.get(mob).copyScores().get(player), 0.0);
    }

    @Test
    void onlyAChangedTargetIsBlockedWhileAnEligibleTargetIsControlled() {
        assertFalse(world.blocksTargetWrite(mob, true, incumbent::equals));
        world.recordPlayerHit(mob, player, 22, 20.0F, 7.0F, () -> incumbent);

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
        EncounterCoordinator.TargetWrite write = new EncounterCoordinator.TargetWrite(null, null);

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
        world.recordPlayerHit(mob, player, 22, 3.0F, -7.0F, () -> incumbent);
        assertEquals(3.0, world.encounters.get(mob).copyScores().get(player), 0.0);

        world.onEntityRemoved(entity);

        assertFalse(world.encounters.containsKey(mob));
        assertFalse(world.blocksTargetWrite(mob, true, incumbent::equals));
    }

    private UUID unexpectedOpeningLookup() {
        return fail("Only a positive opening hit needs the incumbent");
    }
}
