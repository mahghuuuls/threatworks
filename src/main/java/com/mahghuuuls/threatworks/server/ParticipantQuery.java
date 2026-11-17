package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.api.ThreatQueries.Confirmation;
import com.mahghuuuls.threatworks.api.ThreatQueries.Focus;
import com.mahghuuuls.threatworks.api.ThreatQueries.State;
import com.mahghuuuls.threatworks.api.ThreatQueries.Takeover;
import com.mahghuuuls.threatworks.api.ThreatQueries.View;
import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import com.mahghuuuls.threatworks.threat.Encounter;
import com.mahghuuuls.threatworks.threat.Encounter.TargetObservation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.boss.dragon.phase.IPhase;
import net.minecraft.entity.boss.dragon.phase.PhaseList;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEvoker;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityVex;
import net.minecraft.entity.monster.EntityZombieVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.UUID;

import static com.mahghuuuls.threatworks.api.ThreatQueries.Confirmation.NO;
import static com.mahghuuuls.threatworks.api.ThreatQueries.Confirmation.UNKNOWN;
import static com.mahghuuuls.threatworks.api.ThreatQueries.Confirmation.YES;

/** Projects settled state without invoking a combat provider or choosing a target. */
final class ParticipantQuery {
    private static final int MAX_NATIVE_TASKS = 24;
    private static final View[] EMPTY = new View[State.values().length];
    static {
        for (State state : State.values()) {
            EMPTY[state.ordinal()] = new View(state, UNKNOWN, null, UNKNOWN, Focus.UNKNOWN, UNKNOWN, UNKNOWN,
                    UNKNOWN, null, null);
        }
    }

    private ParticipantQuery() {}

    static View empty(State state) {
        return EMPTY[state.ordinal()];
    }

    static View read(WorldServer world, WorldEncounters state, MobType type, EntityLiving mob,
                     EntityLivingBase participant, boolean writing, int now) {
        UUID mobId = mob.getUniqueID();
        Class<?> vanilla = MobAdmission.vanillaClass(type);
        if (type == null || vanilla != null && vanilla != mob.getClass()) {
            return empty(State.UNSUPPORTED);
        }
        if (participant == mob || participant == null || !live(world, participant)) {
            return empty(State.INVALID_PARTICIPANT);
        }
        Encounter encounter = state.encounterForRead(mobId, now);
        if (encounter == null) {
            return empty(vanilla == null ? State.SUPPORT_UNKNOWN : State.INACTIVE);
        }

        UUID participantId = participant.getUniqueID();
        double score = encounter.score(participantId);
        if (writing || state.rejectedWrites.contains(mobId)) {
            return new View(State.TRACKED, UNKNOWN, score, UNKNOWN, Focus.UNKNOWN, UNKNOWN, UNKNOWN,
                    UNKNOWN, null, null);
        }

        Confirmation ordinary = ordinaryMode(state, mob, type);
        ObservedFocus focus = focus(world, mob, ordinary, encounter);
        Confirmation eligibility = knownEligibility(state, mob, encounter, participant, ordinary, focus.scope, now);
        int remaining = encounter.remainingTauntTicks(now);
        Entity taunter = remaining == 0 ? null : world.getEntityFromUuid(encounter.getTaunter());
        boolean tauntActive = taunter instanceof EntityLivingBase && state.matchesTaunter(mobId, taunter)
                && live(world, (EntityLivingBase) taunter) && !targetExcluded(mob, (EntityLivingBase) taunter);
        Confirmation forcing = tauntActive && ordinary != NO
                ? focus.scope != Focus.UNKNOWN && ordinary == YES ? confirmation(focus.target == taunter) : UNKNOWN
                : NO;
        TakeoverSamples samples = state.takeovers.get(mobId);
        Takeover takeover = samples != null && focus.scope == Focus.ORDINARY && focus.target != null
                && ordinary == YES && remaining == 0 && !encounter.holdsOpeningTarget(mob.ticksExisted)
                && eligibility != NO && focus.target != participant
                ? samples.read(state, mob, (EntityLivingBase) focus.target, encounter, participant, now) : null;
        boolean recentTakeover = takeover != null && now - takeover.observationTick() <= Encounter.SELECTION_INTERVAL;
        return new View(State.TRACKED, ordinary, score, recentTakeover ? YES : eligibility, focus.scope,
                focus.scope == Focus.UNKNOWN ? UNKNOWN : confirmation(focus.target == participant),
                confirmation(tauntActive), forcing, tauntActive ? remaining : 0, takeover);
    }

    static boolean live(WorldServer world, EntityLivingBase entity) {
        return entity.world == world && entity.isEntityAlive() && world.getEntityFromUuid(entity.getUniqueID()) == entity;
    }

    private static Confirmation knownEligibility(WorldEncounters state, EntityLiving mob, Encounter encounter,
                                                 EntityLivingBase participant, Confirmation ordinary,
                                                 Focus focus, int now) {
        if (targetExcluded(mob, participant)) {
            return NO;
        }
        if (ordinary != YES && focus != Focus.DRAGON_STRAFE && focus != Focus.GUARDIAN_BEAM) {
            return UNKNOWN;
        }
        WorldEncounters.ObservationFrame frame = state.observations.get(mob.getUniqueID());
        if (frame == null || now - frame.tick < 0 || now - frame.tick > Encounter.SELECTION_INTERVAL) {
            return UNKNOWN;
        }
        UUID participantId = participant.getUniqueID();
        TargetObservation value = frame.get(participant);
        return value == null ? UNKNOWN : confirmation(encounter.isEligible(participantId, now, value));
    }

    static boolean definitelyIneligible(EntityLivingBase entity) {
        if (entity.getIsInvulnerable()) {
            return true;
        }
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            return player.isSpectator() || player.capabilities.disableDamage;
        }
        return false;
    }

    private static boolean targetExcluded(EntityLiving mob, EntityLivingBase entity) {
        return definitelyIneligible(entity) || mob.isOnSameTeam(entity) || !mob.canAttackClass(entity.getClass());
    }

    static Confirmation ordinaryMode(WorldEncounters state, EntityLiving mob, MobType type) {
        if (mob instanceof EntityDragon || mob.isAIDisabled() || mob.tasks.isControlFlagDisabled(3)
                || state.yielded.contains(mob.getUniqueID())) {
            return NO;
        }
        if (MobAdmission.vanillaClass(type) == null || state.coverage.get(mob.getUniqueID()) != type) {
            return UNKNOWN;
        }
        if (mob instanceof EntitySpider
                && !mob.world.isAreaLoaded(new BlockPos(mob.posX, mob.posY + mob.getEyeHeight(), mob.posZ), 1)
                || mob instanceof EntityCreeper && !mob.world.isBlockLoaded(mob.getPosition())
                || (mob instanceof EntityCreeper || mob instanceof EntityEvoker)
                && mob.tasks.taskEntries.size() > MAX_NATIVE_TASKS) {
            return UNKNOWN;
        }
        if (mob instanceof EntityGuardian && GuardianMobPolicy.isNativePhase((EntityGuardian) mob)
                || mob instanceof EntityWither && WitherMobPolicy.isNativePhase((EntityWither) mob)
                || IllagerMobPolicy.matchesType(type) && IllagerMobPolicy.hasVanillaNativeState(mob)
                || mob instanceof EntityEvoker && IllagerMobPolicy.hasActiveRetreat(mob)
                || mob instanceof EntityVex && !IllagerMobPolicy.isIndependent((EntityVex) mob)
                || type == MobType.GHAST && GhastMobPolicy.isNativePhase(mob)
                || mob instanceof EntitySlime && SlimeMobPolicy.matchesType(type) && SlimeMobPolicy.isNativePhase(mob)
                || mob instanceof EntityZombieVillager && ((EntityZombieVillager) mob).isConverting()
                || mob instanceof EntityCreeper && MobTargets.hasActiveCreeperGoal((EntityCreeper) mob)
                || mob instanceof EntityCreature && MobTargets.hasVanillaNativeState((EntityCreature) mob, type)) {
            return NO;
        }
        return YES;
    }

    private static ObservedFocus focus(WorldServer world, EntityLiving mob, Confirmation ordinary, Encounter encounter) {
        if (mob instanceof EntityDragon) {
            EntityDragon dragon = (EntityDragon) mob;
            IPhase phase = dragon.getPhaseManager().getCurrentPhase();
            if (DragonTargeting.isActive(dragon) && phase.getType() == PhaseList.STRAFE_PLAYER
                    && ((DragonStrafe) phase).threatworks$isOrdinaryStrafe()) {
                return checkedFocus(world, mob, Focus.DRAGON_STRAFE,
                        ((DragonStrafe) phase).threatworks$strafeTarget(), false);
            }
            return ObservedFocus.UNKNOWN;
        }
        if (mob instanceof EntityGuardian && ((EntityGuardian) mob).hasTargetedEntity()) {
            int id = ((GuardianBeam) mob).threatworks$beamTargetId();
            Entity beamTarget = world.getEntityByID(id);
            return beamTarget == mob.getAttackTarget()
                    ? checkedFocus(world, mob, Focus.GUARDIAN_BEAM, beamTarget, false) : ObservedFocus.UNKNOWN;
        }
        if (ordinary != YES) {
            return ObservedFocus.UNKNOWN;
        }
        if (mob instanceof EntityWither) {
            EntityLivingBase center = mob.getAttackTarget();
            return WitherMobPolicy.confirmsCenter((EntityWither) mob, center)
                    ? checkedFocus(world, mob, Focus.WITHER_CENTER, center, true) : ObservedFocus.UNKNOWN;
        }
        EntityLivingBase target = mob.getAttackTarget();
        UUID decided = encounter.getTarget();
        if (decided != null && (target == null || !decided.equals(target.getUniqueID()))) {
            return ObservedFocus.UNKNOWN;
        }
        return checkedFocus(world, mob, Focus.ORDINARY, target, true);
    }

    private static ObservedFocus checkedFocus(WorldServer world, EntityLiving mob, Focus scope, Entity target,
                                              boolean allowEmpty) {
        if (target == null && allowEmpty || target instanceof EntityLivingBase
                && live(world, (EntityLivingBase) target) && !targetExcluded(mob, (EntityLivingBase) target)) {
            return new ObservedFocus(scope, target);
        }
        return ObservedFocus.UNKNOWN;
    }

    private static Confirmation confirmation(boolean value) {
        return value ? YES : NO;
    }

    private static final class ObservedFocus {
        static final ObservedFocus UNKNOWN = new ObservedFocus(Focus.UNKNOWN, null);
        final Focus scope;
        final Entity target;

        ObservedFocus(Focus scope, Entity target) {
            this.scope = scope;
            this.target = target;
        }
    }
}
