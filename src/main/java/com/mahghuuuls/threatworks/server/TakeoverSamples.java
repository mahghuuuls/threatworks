package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.api.ThreatQueries.Takeover;
import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import com.mahghuuuls.threatworks.threat.Encounter;
import com.mahghuuuls.threatworks.threat.Encounter.TargetObservation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.mahghuuuls.threatworks.api.ThreatQueries.Confirmation.YES;

/** Takeover evidence from ordinary decisions that retained their live incumbent. */
final class TakeoverSamples {
    static final int MAX_AGE = 25;
    private static final int OBSERVATIONS_PER_DECISION = 2;
    private final Map<UUID, Candidate> candidates = new HashMap<>();
    private final long version;
    private final Position mobPosition;
    private final Position incumbentPosition;
    private final Object incumbentIdentity;
    private final TargetObservation incumbentReach;
    private final double followRange;
    private final Item weapon;
    private final int movementState;
    private int cursor;
    private int lastSupplementTick = Integer.MIN_VALUE;

    private TakeoverSamples(WorldEncounters state, EntityLiving mob, EntityLivingBase incumbent,
                            Encounter encounter, TargetObservation reach) {
        version = encounter.decisionVersion();
        mobPosition = new Position(mob);
        incumbentPosition = new Position(incumbent);
        incumbentIdentity = state.identity(incumbent.getUniqueID());
        incumbentReach = reach;
        followRange = mob.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue();
        weapon = mob.getHeldItemMainhand().getItem();
        movementState = movementState(mob);
    }

    static boolean supportsSampling(EntityLiving mob, MobType type) {
        return (type == MobType.ZOMBIE || type == MobType.SKELETON) && mob.getClass() == MobAdmission.vanillaClass(type);
    }

    static Pending record(WorldServer world, WorldEncounters state, EntityLiving mob, MobType type, Encounter encounter,
                          EntityLivingBase incumbent, WorldEncounters.ObservationFrame observations, WorkSample work) {
        UUID id = mob.getUniqueID();
        UUID incumbentId = incumbent.getUniqueID();
        TargetObservation reach = observations.get(incumbent);
        if (reach == null || !encounter.isEligible(incumbentId, observations.tick, reach)
                || encounter.remainingTauntTicks(observations.tick) > 0 || encounter.participantCount() < 2) {
            state.takeovers.remove(id);
            return null;
        }
        TakeoverSamples history = state.takeovers.get(id);
        if (history == null || !history.incumbentReach.sameReach(reach)
                || !history.matches(state, mob, incumbent, encounter)) {
            int nextCursor = history == null ? 0 : history.cursor;
            int lastSupplement = history == null ? Integer.MIN_VALUE : history.lastSupplementTick;
            history = new TakeoverSamples(state, mob, incumbent, encounter, reach);
            history.cursor = nextCursor;
            history.lastSupplementTick = lastSupplement;
            state.takeovers.put(id, history);
        }

        List<EntityLivingBase> unobserved = new ArrayList<>();
        for (UUID participantId : encounter.scoredParticipants()) {
            if (participantId.equals(incumbentId)) {
                continue;
            }
            Entity participant = world.getEntityFromUuid(participantId);
            if (participant instanceof EntityLivingBase && ParticipantQuery.live(world, (EntityLivingBase) participant)) {
                EntityLivingBase living = (EntityLivingBase) participant;
                TargetObservation known = observations.get(living);
                if (known != null) {
                    history.save(state.identity(participantId), living, encounter, known, observations.tick);
                } else {
                    unobserved.add(living);
                }
            } else {
                history.candidates.remove(participantId);
            }
        }
        return unobserved.isEmpty() ? null : new Pending(history, world, state, mob, type, encounter, incumbent,
                observations, unobserved, work);
    }

    private void save(Object identity, EntityLivingBase participant, Encounter encounter, TargetObservation reach, int tick) {
        UUID id = participant.getUniqueID();
        double boundary = reach == null ? Double.NaN : encounter.takeoverAgainstRetainedTarget(id, incumbentReach, reach);
        if (identity == null || !Double.isFinite(boundary) || encounter.decisionVersion() != version) {
            candidates.remove(id);
        } else {
            candidates.put(id, new Candidate(identity, new Position(participant),
                    new Takeover(boundary, false, tick, encounter.score(id))));
        }
    }

    Takeover read(WorldEncounters state, EntityLiving mob, EntityLivingBase incumbent, Encounter encounter,
                  EntityLivingBase participant, int now) {
        Candidate record = candidates.get(participant.getUniqueID());
        if (record == null || participant == incumbent) {
            return null;
        }
        int age = now - record.value.observationTick();
        return age >= 0 && age <= MAX_AGE && matches(state, mob, incumbent, encounter)
                && record.identity == state.identity(participant.getUniqueID()) && record.position.matches(participant)
                && loadedPosition(mob) && loadedPosition(incumbent) && loadedPosition(participant)
                && encounter.isEligible(incumbent.getUniqueID(), now, incumbentReach) ? record.value : null;
    }

    private boolean matches(WorldEncounters state, EntityLiving mob, EntityLivingBase incumbent, Encounter encounter) {
        return version == encounter.decisionVersion() && incumbentIdentity != null
                && incumbentIdentity == state.identity(incumbent.getUniqueID())
                && mobPosition.matches(mob) && incumbentPosition.matches(incumbent)
                && followRange == mob.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue()
                && weapon == mob.getHeldItemMainhand().getItem() && movementState == movementState(mob);
    }

    private static boolean loadedPosition(EntityLivingBase entity) {
        BlockPos pos = new BlockPos(entity);
        return entity.world.isBlockLoaded(pos) && entity.world.getWorldBorder().contains(pos);
    }

    private static int movementState(EntityLiving mob) {
        int bits = mob.world.isDaytime() ? 1 : 0;
        if (!mob.getItemStackFromSlot(EntityEquipmentSlot.HEAD).isEmpty()) bits |= 2;
        if (mob.isRiding() || mob.isBeingRidden()) bits |= 4;
        if (mob.isInWater() || mob.isInLava()) bits |= 8;
        if (mob instanceof EntityZombie && ((EntityZombie) mob).isBreakDoorsTaskSet()) bits |= 16;
        if (mob.getNavigator() instanceof PathNavigateGround) {
            PathNavigateGround path = (PathNavigateGround) mob.getNavigator();
            if (path.getEnterDoors()) bits |= 32;
            if (path.getCanSwim()) bits |= 64;
        }
        return bits;
    }

    /** Same-tick work only. The published history retains scalar inputs and detached identity tokens. */
    static final class Pending {
        private final TakeoverSamples history;
        private final WorldServer world;
        private final WorldEncounters state;
        private final EntityLiving mob;
        private final MobType type;
        private final Encounter encounter;
        private final EntityLivingBase incumbent;
        private final WorldEncounters.ObservationFrame observations;
        private final List<EntityLivingBase> unobserved;
        private final WorkSample work;
        private int served;

        Pending(TakeoverSamples history, WorldServer world, WorldEncounters state, EntityLiving mob, MobType type,
                Encounter encounter, EntityLivingBase incumbent, WorldEncounters.ObservationFrame observations,
                List<EntityLivingBase> unobserved, WorkSample work) {
            this.history = history;
            this.world = world;
            this.state = state;
            this.mob = mob;
            this.type = type;
            this.encounter = encounter;
            this.incumbent = incumbent;
            this.observations = observations;
            this.unobserved = unobserved;
            this.work = work;
        }

        int lastSupplementTick() {
            return history.lastSupplementTick;
        }

        void supplement(int now, Budget budget) {
            int count = unobserved.size();
            if (now != observations.tick || served >= Math.min(count, OBSERVATIONS_PER_DECISION) || !stillMatches()
                    || !budget.observation(now, work)) return;
            served++;
            history.lastSupplementTick = now;
            EntityLivingBase participant = unobserved.get(history.cursor % count);
            history.cursor = (history.cursor + 1) % count;
            if (!ParticipantQuery.live(world, participant)) return;
            Object identity = state.recordIdentity(participant.getUniqueID());
            Position position = new Position(participant);
            TargetObservation result = MobTargets.observeForTakeover(world, (EntityCreature) mob, participant, type);
            if (stillMatches() && ParticipantQuery.live(world, participant) && position.matches(participant)) {
                history.save(identity, participant, encounter, result, now);
            }
        }

        private boolean stillMatches() {
            UUID id = mob.getUniqueID();
            return state.encounters.get(id) == encounter && state.takeovers.get(id) == history
                    && !state.rejectedWrites.contains(id) && mob.getAttackTarget() == incumbent
                    && ParticipantQuery.live(world, mob) && ParticipantQuery.live(world, incumbent)
                    && ParticipantQuery.ordinaryMode(state, mob, type) == YES
                    && history.matches(state, mob, incumbent, encounter) && observations.positionsUnchanged(world);
        }
    }

    private static final class Candidate {
        final Object identity;
        final Position position;
        final Takeover value;

        Candidate(Object identity, Position position, Takeover value) {
            this.identity = identity;
            this.position = position;
            this.value = value;
        }
    }

    static final class Position {
        final double x;
        final double y;
        final double z;
        final float width;
        final float height;
        final float eyeHeight;

        Position(EntityLivingBase entity) {
            x = entity.posX;
            y = entity.posY;
            z = entity.posZ;
            width = entity.width;
            height = entity.height;
            eyeHeight = entity.getEyeHeight();
        }

        boolean matches(EntityLivingBase entity) {
            return x == entity.posX && y == entity.posY && z == entity.posZ && width == entity.width
                    && height == entity.height && eyeHeight == entity.getEyeHeight();
        }
    }

    static final class Budget {
        private static final int MAX_OBSERVATIONS = 8;
        private int tick;
        private int observations;

        boolean exhausted(int now) {
            return now == tick && observations >= MAX_OBSERVATIONS;
        }

        boolean observation(int now, WorkSample sample) {
            if (now != tick) {
                tick = now;
                observations = 0;
            }
            if (exhausted(now)) return false;
            observations++;
            if (sample != null) sample.supplement(now);
            return true;
        }
    }
}
