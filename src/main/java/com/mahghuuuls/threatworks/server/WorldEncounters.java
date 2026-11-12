package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.points.ThreatPoints;
import com.mahghuuuls.threatworks.threat.Encounter;
import com.mahghuuuls.threatworks.threat.Encounter.Rules;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class WorldEncounters implements IWorldEventListener {
    final Map<UUID, Encounter> encounters = new HashMap<>();
    // A snapshot can compare world sessions without retaining a world or its encounter table.
    final Object launchSession = new Object();

    void recordHit(UUID mob, Supplier<UUID> recipient, int mobTick, boolean mayOpen, float before, float after, Rules rules,
                   DoubleSupplier points, Supplier<UUID> openingTarget) {
        double lost = Encounter.healthLost(before, after);
        if (!(lost > 0.0) || !Double.isFinite(lost)) {
            return;
        }
        Encounter beforeCredit = encounters.get(mob);
        if (!mayOpen && beforeCredit == null) {
            return;
        }
        double gain = ThreatPoints.damageThreat(lost, points.getAsDouble());
        UUID credit = recipient.get();
        Encounter previous = encounters.get(mob);
        if (!mayOpen && previous != beforeCredit) {
            return;
        }
        UUID incumbent = previous == null ? openingTarget.get() : null;
        Encounter updated = Encounter.recordHit(previous, credit, incumbent, mobTick, gain, rules);
        if (updated != null) {
            encounters.put(mob, updated);
        }
    }

    boolean blocksTargetWrite(UUID mob, boolean changesTarget, Predicate<UUID> targetStillValid) {
        Encounter encounter = encounters.get(mob);
        return changesTarget && encounter != null && encounter.getTarget() != null
                && targetStillValid.test(encounter.getTarget());
    }

    @Override
    public void onEntityRemoved(Entity entity) {
        if (entity instanceof EntityPlayer) {
            playerAvailabilityChanged(entity.getUniqueID());
        } else {
            encounters.remove(entity.getUniqueID());
        }
    }

    @Override
    public void onEntityAdded(Entity entity) {
        if (entity instanceof EntityPlayer) {
            playerAvailabilityChanged(entity.getUniqueID());
        }
    }

    private void playerAvailabilityChanged(UUID player) {
        encounters.values().forEach(encounter -> encounter.participantChanged(player));
    }

    static void removePlayerScores(Iterable<WorldEncounters> partitions, UUID player) {
        for (WorldEncounters partition : partitions) {
            partition.encounters.values().forEach(encounter -> encounter.removeParticipant(player));
        }
    }

    @Override
    public void notifyBlockUpdate(World world, BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
    }

    @Override
    public void notifyLightSet(BlockPos pos) {
    }

    @Override
    public void markBlockRangeForRenderUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    @Override
    public void playSoundToAllNearExcept(EntityPlayer player, SoundEvent sound, SoundCategory category,
                                         double x, double y, double z, float volume, float pitch) {
    }

    @Override
    public void playRecord(SoundEvent sound, BlockPos pos) {
    }

    @Override
    public void spawnParticle(int id, boolean ignoreRange, double x, double y, double z,
                              double xSpeed, double ySpeed, double zSpeed, int... parameters) {
    }

    @Override
    public void spawnParticle(int id, boolean ignoreRange, boolean minimize, double x, double y, double z,
                              double xSpeed, double ySpeed, double zSpeed, int... parameters) {
    }

    @Override
    public void broadcastSound(int sound, BlockPos pos, int data) {
    }

    @Override
    public void playEvent(EntityPlayer player, int type, BlockPos pos, int data) {
    }

    @Override
    public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress) {
    }
}
