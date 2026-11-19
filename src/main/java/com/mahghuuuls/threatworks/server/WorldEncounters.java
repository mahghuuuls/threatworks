package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.api.TargetAwareness.Transition;
import com.mahghuuuls.threatworks.points.ThreatPoints;
import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import com.mahghuuuls.threatworks.threat.Encounter;
import com.mahghuuuls.threatworks.threat.Encounter.Rules;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class WorldEncounters implements IWorldEventListener {
    final Map<UUID, Encounter> encounters = new HashMap<>();
    final Map<UUID, MobType> coverage = new HashMap<>();
    final Map<UUID, ObservationFrame> observations = new HashMap<>();
    final Map<UUID, TakeoverSamples> takeovers = new HashMap<>();
    private final Map<UUID, Object> identities = new HashMap<>();
    private final Map<UUID, Object> tauntIdentities = new HashMap<>();
    final Set<UUID> yielded = new HashSet<>();
    final Set<UUID> rejectedWrites = new HashSet<>();
    final Map<UUID, AwarenessQualification> awarenessQualification = new HashMap<>();
    private final Map<UUID, Set<UUID>> failedAwareness = new HashMap<>();
    private final Map<UUID, Long> encounterIds = new HashMap<>();
    private final int dimension;
    private final AwarenessIntegration awareness;
    private final Predicate<UUID> observeEncounter;
    // A snapshot can compare world sessions without retaining a world or its encounter table.
    final Object launchSession = new Object();
    WorkSample workSample;

    WorldEncounters() {
        this(0, null, null);
    }

    WorldEncounters(int dimension, AwarenessIntegration awareness, Predicate<UUID> observeEncounter) {
        this.dimension = dimension;
        this.awareness = awareness;
        this.observeEncounter = observeEncounter;
    }

    WorkSample activeSample() {
        return workSample == null || workSample.complete() ? null : workSample;
    }

    void tick(int serverTick, BiFunction<UUID, Encounter, Transition> remove) {
        WorkSample sample = activeSample();
        if (!encounters.isEmpty()) {
            Iterator<Map.Entry<UUID, Encounter>> entries = encounters.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<UUID, Encounter> entry = entries.next();
                if (sample != null) {
                    sample.visit();
                }
                Transition reason = remove.apply(entry.getKey(), entry.getValue());
                if (reason != null) {
                    UUID id = entry.getKey();
                    entries.remove();
                    forgetControl(id);
                    ended(id, reason);
                }
            }
        }
        if (sample != null) {
            sample.endTick(serverTick, encounters.size());
        }
    }

    Encounter encounterForAction(UUID id, int tick) {
        Encounter encounter = encounters.get(id);
        if (encounter != null && encounter.resetDue(tick)) {
            reset(id);
            return null;
        }
        return encounter;
    }

    Encounter encounterForRead(UUID id, int tick) {
        Encounter encounter = encounters.get(id);
        return encounter == null || encounter.resetDue(tick) ? null : encounter;
    }

    final class ObservationFrame {
        final int tick;
        private final Map<UUID, ObservedParticipant> byParticipant = new HashMap<>();
        private final boolean capturePositions;

        ObservationFrame(int tick) {
            this(tick, false);
        }

        ObservationFrame(int tick, boolean capturePositions) {
            this.tick = tick;
            this.capturePositions = capturePositions;
        }

        Encounter.TargetObservation observe(WorldServer world, UUID participant,
                                            Function<Entity, Encounter.TargetObservation> inspect) {
            ObservedParticipant current = byParticipant.get(participant);
            if (current != null) return current.value;
            Entity candidate = world.getEntityFromUuid(participant);
            Object identity = candidate instanceof EntityLivingBase ? recordIdentity(participant) : null;
            TakeoverSamples.Position position = capturePositions && identity != null
                    ? new TakeoverSamples.Position((EntityLivingBase) candidate) : null;
            Encounter.TargetObservation value = inspect.apply(candidate);
            byParticipant.put(participant, new ObservedParticipant(identity, value, position));
            return value;
        }

        Encounter.TargetObservation get(UUID participant) {
            ObservedParticipant observation = byParticipant.get(participant);
            return observation == null ? null : observation.value;
        }

        Encounter.TargetObservation get(EntityLivingBase participant) {
            ObservedParticipant observation = byParticipant.get(participant.getUniqueID());
            return observation == null || observation.identity == null
                    || observation.identity != identities.get(participant.getUniqueID()) ? null : observation.value;
        }

        boolean positionsUnchanged(WorldServer world) {
            for (Map.Entry<UUID, ObservedParticipant> entry : byParticipant.entrySet()) {
                Entity entity = world.getEntityFromUuid(entry.getKey());
                TakeoverSamples.Position recorded = entry.getValue().position;
                if (recorded == null ? entity instanceof EntityLivingBase
                        : !(entity instanceof EntityLivingBase) || get((EntityLivingBase) entity) == null
                        || !recorded.matches((EntityLivingBase) entity)) return false;
            }
            return true;
        }
    }

    private static final class ObservedParticipant {
        final Object identity;
        final Encounter.TargetObservation value;
        final TakeoverSamples.Position position;

        ObservedParticipant(Object identity, Encounter.TargetObservation value, TakeoverSamples.Position position) {
            this.identity = identity;
            this.value = value;
            this.position = position;
        }
    }

    private void forgetControl(UUID id) {
        coverage.remove(id);
        observations.remove(id);
        takeovers.remove(id);
        clearTauntIdentity(id);
        yielded.remove(id);
    }

    void putEncounter(UUID id, Encounter encounter) {
        if (encounters.put(id, encounter) == null && awareness != null && observeEncounter.test(id)) {
            encounterIds.put(id, awareness.started(dimension, id));
        }
    }

    private void ended(UUID mob, Transition reason) {
        Long encounter = encounterIds.remove(mob);
        if (encounter != null) {
            awareness.ended(encounter, dimension, mob, reason);
        }
    }

    void endWorld(Transition reason) {
        encounterIds.forEach((mob, id) -> awareness.ended(id, dimension, mob, reason));
        encounterIds.clear();
    }

    static final class AwarenessQualification {
        int tick = Integer.MIN_VALUE;
        boolean qualifies;
        UUID focusToRelease;
    }

    boolean awarenessFailed(UUID mob, UUID player) {
        Set<UUID> failedMobs = failedAwareness.get(player);
        return failedMobs != null && failedMobs.contains(mob);
    }

    void failAwareness(UUID mob, UUID player) {
        failedAwareness.computeIfAbsent(player, ignored -> new HashSet<>()).add(mob);
    }

    boolean matchesTaunter(UUID mob, Entity entity) {
        Object identity = entity == null ? null : identities.get(entity.getUniqueID());
        return identity != null && tauntIdentities.get(mob) == identity;
    }

    Object identity(UUID participant) {
        return identities.get(participant);
    }

    Object recordIdentity(UUID participant) {
        // Respawn can reuse both the UUID and the numeric entity ID.
        return identities.computeIfAbsent(participant, ignored -> new Object());
    }

    void recordTaunt(UUID mob, EntityLivingBase participant) {
        tauntIdentities.put(mob, recordIdentity(participant.getUniqueID()));
    }

    void clearTauntIdentity(UUID mob) {
        tauntIdentities.remove(mob);
    }

    void clearMissingTaunt(WorldServer world, UUID mob, Encounter encounter) {
        UUID participant = encounter.getTaunter();
        if (participant == null) {
            clearTauntIdentity(mob);
        } else if (!matchesTaunter(mob, world.getEntityFromUuid(participant))) {
            encounter.clearTaunt();
            clearTauntIdentity(mob);
        }
    }

    void discard(UUID id) {
        encounters.remove(id);
        forgetControl(id);
        rejectedWrites.remove(id);
        awarenessQualification.remove(id);
        failedAwareness.values().removeIf(mobs -> mobs.remove(id) && mobs.isEmpty());
        ended(id, Transition.MOB_REMOVED);
    }

    void reset(UUID id) {
        encounters.remove(id);
        forgetControl(id);
        ended(id, Transition.RESET);
    }

    UUID select(Encounter encounter, int serverTick, WorkSample sample,
                Function<UUID, Encounter.TargetObservation> observe) {
        if (sample != null) {
            sample.selection();
        }
        return encounter.selectTarget(serverTick, id -> {
            if (sample != null) {
                sample.observation();
            }
            return observe.apply(id);
        });
    }

    void recordHit(UUID mob, Supplier<UUID> recipient, int mobTick, boolean mayOpen, float before, float after, Rules rules,
                   DoubleSupplier multiplier, Supplier<UUID> openingTarget) {
        recordHit(mob, recipient, mobTick, mayOpen, before, after, rules, multiplier, openingTarget, () -> true);
    }

    void recordHit(UUID mob, Supplier<UUID> recipient, int mobTick, boolean mayOpen, float before, float after, Rules rules,
                   DoubleSupplier multiplier, Supplier<UUID> openingTarget, BooleanSupplier canCommit) {
        double lost = Encounter.healthLost(before, after);
        if (!(lost > 0.0) || !Double.isFinite(lost)) {
            return;
        }
        Encounter beforeCredit = encounters.get(mob);
        if (!mayOpen && beforeCredit == null) {
            return;
        }
        double gain = ThreatPoints.damageThreat(lost, multiplier.getAsDouble());
        UUID credit = recipient.get();
        Encounter previous = encounters.get(mob);
        if (!mayOpen && previous != beforeCredit) {
            return;
        }
        UUID incumbent = previous == null ? openingTarget.get() : null;
        if (!canCommit.getAsBoolean()) {
            return;
        }
        // An opening eligibility callback may itself open an encounter.
        previous = encounters.get(mob);
        Encounter updated = Encounter.recordHit(previous, credit, incumbent, mobTick, gain, rules);
        if (updated != null) {
            putEncounter(mob, updated);
        }
    }

    boolean blocksTargetWrite(UUID mob, boolean changesTarget, Predicate<UUID> targetStillValid) {
        Encounter encounter = encounters.get(mob);
        return changesTarget && encounter != null && encounter.getTarget() != null
                && targetStillValid.test(encounter.getTarget());
    }

    @Override
    public void onEntityRemoved(Entity entity) {
        if (entity instanceof EntityLivingBase) {
            identities.remove(entity.getUniqueID());
        }
        if (entity instanceof EntityPlayer) {
            playerAvailabilityChanged(entity.getUniqueID());
        } else {
            discard(entity.getUniqueID());
        }
    }

    @Override
    public void onEntityAdded(Entity entity) {
        if (entity instanceof EntityLivingBase) {
            identities.remove(entity.getUniqueID());
            awarenessQualification.remove(entity.getUniqueID());
        }
        if (entity instanceof EntityPlayer) {
            playerAvailabilityChanged(entity.getUniqueID());
        }
    }

    private void playerAvailabilityChanged(UUID player) {
        failedAwareness.remove(player);
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
