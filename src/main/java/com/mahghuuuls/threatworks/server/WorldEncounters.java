package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.threat.Encounter;
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
import java.util.function.Predicate;
import java.util.function.Supplier;

final class WorldEncounters implements IWorldEventListener {
    final Map<UUID, Encounter> encounters = new HashMap<>();

    void recordPlayerHit(UUID mob, UUID player, int mobTick, float before, float after, Supplier<UUID> openingTarget) {
        double lost = Encounter.healthLost(before, after);
        if (!(lost > 0.0) || !Double.isFinite(lost)) {
            return;
        }
        Encounter previous = encounters.get(mob);
        UUID incumbent = previous == null ? openingTarget.get() : null;
        encounters.put(mob, Encounter.recordPlayerHit(previous, player, incumbent, mobTick, lost));
    }

    boolean blocksTargetWrite(UUID mob, boolean changesTarget, Predicate<UUID> targetStillValid) {
        Encounter encounter = encounters.get(mob);
        return changesTarget && encounter != null && encounter.getTarget() != null
                && targetStillValid.test(encounter.getTarget());
    }

    @Override
    public void onEntityRemoved(Entity entity) {
        encounters.remove(entity.getUniqueID());
    }

    @Override
    public void onEntityAdded(Entity entity) {
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
