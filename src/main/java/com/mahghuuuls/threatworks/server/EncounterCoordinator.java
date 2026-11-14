package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.api.ThreatActions;
import com.mahghuuuls.threatworks.api.SummonThreatCreditEvent;
import com.mahghuuuls.threatworks.config.ServerTuning;
import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import com.mahghuuuls.threatworks.compat.FoliaathCompatibility;
import com.mahghuuuls.threatworks.points.ThreatPoints;
import com.mahghuuuls.threatworks.threat.Encounter;
import com.mahghuuuls.threatworks.threat.Encounter.TargetObservation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.stats.StatList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class EncounterCoordinator implements ThreatActions {
    public static final EncounterCoordinator INSTANCE = new EncounterCoordinator();

    private final Map<WorldServer, WorldEncounters> worlds = new IdentityHashMap<>();
    private final Map<EntityPlayerMP, Integer> pendingPlayerDeaths = new IdentityHashMap<>();
    private final Map<MobType, MobPolicy> policies = new EnumMap<>(MobType.class);
    private ServerTuning tuning;
    private ThreatPoints points;
    private DamageAttribution.SummonSource summons;
    private FoliaathCompatibility foliaath;
    private TargetWrite targetWrite;

    private EncounterCoordinator() {
        for (MobType type : MobType.values()) {
            if (type != MobType.FOLIAATH) {
                policies.put(type, MobPolicy.builtIn(type));
            }
        }
    }

    public static boolean supports(EntityLivingBase mob) {
        return mob.world instanceof WorldServer && ((WorldServer) mob.world).getMinecraftServer().isCallingFromMinecraftThread()
                && INSTANCE.canCredit(mob);
    }

    private boolean canCredit(EntityLivingBase mob) {
        MobAdmission admission = admission(mob);
        WorldEncounters state = worlds.get(mob.world);
        return admission.type != null && state != null && activeWrite((EntityLiving) mob) == null
                && !state.rejectedWrites.contains(mob.getUniqueID())
                && (admission.controls() || admission.yieldsToNative()
                && state.encounters.containsKey(mob.getUniqueID()));
    }

    private MobAdmission admission(EntityLivingBase mob) {
        return MobAdmission.resolve(tuning, foliaath, mob);
    }

    public void setFoliaathCompatibility(FoliaathCompatibility compatibility) {
        this.foliaath = compatibility;
        if (compatibility == null) {
            policies.remove(MobType.FOLIAATH);
        } else {
            policies.put(MobType.FOLIAATH, MobPolicy.stationary(compatibility));
        }
    }

    public void startServer(ServerTuning tuning, ThreatPoints points) {
        this.tuning = tuning;
        this.points = points;
    }

    public void setSummonSource(DamageAttribution.SummonSource summons) {
        this.summons = summons;
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world instanceof WorldServer && !worlds.containsKey(world)) {
            WorldEncounters state = new WorldEncounters();
            worlds.put((WorldServer) world, state);
            world.addEventListener(state);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        if (world instanceof WorldServer) {
            WorldEncounters state = worlds.remove(world);
            if (state != null) {
                world.removeEventListener(state);
            }
        }
    }

    public void serverStopped() {
        worlds.forEach(World::removeEventListener);
        worlds.clear();
        pendingPlayerDeaths.clear();
        if (points != null) {
            points.clearSetBonuses();
        }
        tuning = null;
        points = null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onPlayerDeathAttempt(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
            pendingPlayerDeaths.putIfAbsent(player, player.getStatFile().readStat(StatList.DEATHS));
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof EntityPlayerMP && event.getOriginal() != event.getEntityPlayer()) {
            EntityPlayerMP original = (EntityPlayerMP) event.getOriginal();
            if (points != null) {
                points.discardSetBonuses(original);
            }
            if (event.isWasDeath()) {
                Integer before = pendingPlayerDeaths.remove(original);
                if (before != null) {
                    confirmPlayerDeath(original, before);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerLogout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        if (points != null && event.player instanceof EntityPlayerMP) {
            points.discardSetBonuses(event.player);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pendingPlayerDeaths.isEmpty()) {
            return;
        }
        pendingPlayerDeaths.forEach(this::confirmPlayerDeath);
        pendingPlayerDeaths.clear();
    }

    private void confirmPlayerDeath(EntityPlayerMP player, int previousDeaths) {
        // Vanilla increments the death statistic after Forge accepts the death event.
        if (player.getStatFile().readStat(StatList.DEATHS) != previousDeaths) {
            if (points != null) {
                points.discardSetBonuses(player);
            }
            WorldEncounters.removePlayerScores(worlds.values(), player.getUniqueID());
        }
    }

    public ArrowLaunch.Snapshot observeArrowLaunch(EntityArrow arrow) {
        if (!(arrow.world instanceof WorldServer) || arrow.isDead
                || !Double.isFinite(arrow.motionX) || !Double.isFinite(arrow.motionY) || !Double.isFinite(arrow.motionZ)
                || (arrow.motionX == 0 && arrow.motionY == 0 && arrow.motionZ == 0)) {
            return null;
        }
        WorldServer world = (WorldServer) arrow.world;
        if (!world.getMinecraftServer().isCallingFromMinecraftThread()) {
            return null;
        }
        WorldEncounters state = worlds.get(world);
        Entity shooter = arrow.shootingEntity;
        if (state == null || !(shooter instanceof EntityLivingBase) || shooter.world != world
                || !shooter.isEntityAlive() || world.getEntityFromUuid(shooter.getUniqueID()) != shooter) {
            return null;
        }
        return new ArrowLaunch.Snapshot(shooter.getUniqueID(), state.launchSession,
                points.multiplier((EntityLivingBase) shooter, tuning));
    }

    public void onHealthWritten(EntityLivingBase victim, DamageSource source, EntityLivingBase incumbent,
                                float before, float after) {
        MobAdmission admission = admission(victim);
        MobType type = admission.type;
        if (type == null) {
            return;
        }

        EntityCreature mob = (EntityCreature) victim;
        WorldServer world = (WorldServer) mob.world;
        WorldEncounters state = worlds.get(world);
        UUID mobId = mob.getUniqueID();
        if (state == null || world.getEntityFromUuid(mobId) != mob || activeWrite(mob) != null
                || state.rejectedWrites.contains(mobId)) {
            return;
        }
        int tick = world.getMinecraftServer().getTickCounter();
        Encounter existing = state.encounterForAction(mobId, tick);
        if (admission.yieldsToNative()) {
            if (existing == null) {
                return;
            }
            yieldToNative(state, mobId, existing, tick);
        }

        DamageAttribution hit = DamageAttribution.resolve(source, state.launchSession, summons);
        if (hit == null || world.getEntityFromUuid(hit.attacker.getUniqueID()) != hit.attacker
                || !validParticipant(mob, type, hit.attacker)) {
            return;
        }
        state.recordHit(mobId, () -> creditRecipient(world, mob, type, hit, source), mob.ticksExisted,
                admission.controls() && hit.mayOpenEncounter(),
                before, after, tuning.rulesFor(type), () -> hit.multiplier(() -> points.multiplier(hit.attacker, tuning)),
                () -> incumbent != null && world.getEntityFromUuid(incumbent.getUniqueID()) == incumbent
                        && observe(world, mob, type, incumbent, null).isEligible()
                        ? incumbent.getUniqueID() : null);
        if (state.encounters.containsKey(mobId)) {
            state.coverage.putIfAbsent(mobId, type);
        }
    }

    private UUID creditRecipient(WorldServer world, EntityCreature victim, MobType type, DamageAttribution hit,
                                 DamageSource source) {
        EntityLivingBase owner = hit.summonOwner;
        if (owner != null && world.getEntityFromUuid(owner.getUniqueID()) == owner && validParticipant(victim, type, owner)) {
            SummonThreatCreditEvent decision = new SummonThreatCreditEvent(victim, hit.attacker, owner, source);
            MinecraftForge.EVENT_BUS.post(decision);
            if (decision.isOwnerCreditRequested() && world.getEntityFromUuid(owner.getUniqueID()) == owner
                    && validParticipant(victim, type, owner)) {
                return owner.getUniqueID();
            }
        }
        return hit.attacker.getUniqueID();
    }

    private boolean validParticipant(EntityCreature mob, MobType type, EntityLivingBase target) {
        return MobTargets.isAttackable(mob, target) && policies.get(type).accepts(mob, target);
    }

    private EntityLivingBase resolve(WorldServer world, EntityCreature mob, MobType type, Entity candidate) {
        return policies.get(type).resolve(world, mob, candidate);
    }

    private TargetObservation observe(WorldServer world, EntityCreature mob, MobType type, Entity candidate,
                                      WorkSample sample) {
        return policies.get(type).observe(world, mob, candidate, sample);
    }

    public boolean blocksTargetWrite(EntityLiving mob, EntityLivingBase requestedTarget) {
        if (!(mob instanceof EntityCreature) || !(mob.world instanceof WorldServer)) {
            return false;
        }
        TargetWrite write = activeWrite(mob);
        if (write != null) {
            return !write.consume(requestedTarget);
        }
        WorldServer world = (WorldServer) mob.world;
        WorldEncounters state = worlds.get(world);
        MobType binding = state == null ? null : state.coverage.get(mob.getUniqueID());
        return binding != null && mob.isEntityAlive()
                && !state.yielded.contains(mob.getUniqueID()) && !state.rejectedWrites.contains(mob.getUniqueID())
                && policies.get(binding).controlsImmediately((EntityCreature) mob)
                && !policies.get(binding).preservesNativeTarget(world, (EntityCreature) mob, mob.getAttackTarget())
                && state.blocksTargetWrite(mob.getUniqueID(),
                mob.getAttackTarget() != requestedTarget,
                target -> resolve(world, (EntityCreature) mob, binding, world.getEntityFromUuid(target)) != null);
    }

    @Override
    public Result grant(EntityLivingBase victim, EntityLivingBase participant, double amount) {
        Result rejected = checkActionContext(victim);
        if (rejected != null) {
            return rejected;
        }
        rejected = checkParticipants(victim, participant, admission(victim));
        if (rejected != null) {
            return rejected;
        }
        if (activeWrite((EntityCreature) victim) != null) {
            return Result.TARGET_WRITE_IN_PROGRESS;
        }
        WorldServer world = (WorldServer) victim.world;
        Encounter encounter = worlds.get(world).encounterForAction(victim.getUniqueID(),
                world.getMinecraftServer().getTickCounter());
        if (encounter == null) {
            return Result.UNTRACKED;
        }
        switch (encounter.grant(participant.getUniqueID(), amount)) {
            case APPLIED:
                return Result.APPLIED;
            case INVALID_AMOUNT:
                return Result.INVALID_AMOUNT;
            case SCORE_LIMIT:
                return Result.SCORE_LIMIT;
            default:
                throw new AssertionError("Unhandled threat grant outcome");
        }
    }

    @Override
    public Result taunt(EntityLivingBase victim, EntityLivingBase participant) {
        Result rejected = checkActionContext(victim);
        if (rejected != null) {
            return rejected;
        }
        MobAdmission admission = admission(victim);
        if (!admission.controls()) {
            return Result.UNSUPPORTED_MOB;
        }
        rejected = checkParticipants(victim, participant, admission);
        if (rejected != null) {
            return rejected;
        }
        EntityCreature mob = (EntityCreature) victim;
        if (activeWrite(mob) != null) {
            return Result.TARGET_WRITE_IN_PROGRESS;
        }
        WorldServer world = (WorldServer) mob.world;
        WorldEncounters state = worlds.get(world);
        UUID mobId = mob.getUniqueID();
        int tick = world.getMinecraftServer().getTickCounter();
        Encounter encounter = state.encounterForAction(mobId, tick);
        if (encounter == null && !(participant instanceof EntityPlayerMP)) {
            return Result.UNTRACKED;
        }
        EntityLivingBase previous = mob.getAttackTarget();
        UUID incumbent = previous == null || world.getEntityFromUuid(previous.getUniqueID()) != previous
                ? null : previous.getUniqueID();
        UUID taunter = participant.getUniqueID();
        Map<UUID, TargetObservation> observations = new HashMap<>();
        Encounter.Taunt pending = Encounter.prepareTaunt(encounter, taunter, incumbent, tick,
                id -> observations.computeIfAbsent(id, key -> observe(world, mob, admission.type,
                        world.getEntityFromUuid(key), null)),
                tuning.rulesFor(admission.type));
        if (pending == null) {
            return Result.INELIGIBLE_TAUNTER;
        }
        if (!writeTarget(mob, participant) || !currentEncounter(world, state, mob, encounter)
                || world.getEntityFromUuid(taunter) != participant
                || !admission(mob).controls() || resolve(world, mob, admission.type, participant) == null) {
            rejectWrite(world, state, mob, encounter);
            return Result.TARGET_ASSIGNMENT_REJECTED;
        }
        state.encounters.put(mobId, pending.apply());
        state.coverage.put(mobId, admission.type);
        state.observations.put(mobId, observations);
        state.yielded.remove(mobId);
        return Result.APPLIED;
    }

    private Result checkActionContext(EntityLivingBase mob) {
        if (mob == null) {
            return Result.UNSUPPORTED_MOB;
        }
        if (!(mob.world instanceof WorldServer)) {
            return Result.SERVER_WORLD_UNAVAILABLE;
        }
        WorldServer world = (WorldServer) mob.world;
        if (!world.getMinecraftServer().isCallingFromMinecraftThread()) {
            return Result.WRONG_THREAD;
        }
        if (!worlds.containsKey(world)) {
            return Result.SERVER_WORLD_UNAVAILABLE;
        }
        return null;
    }

    private Result checkParticipants(EntityLivingBase mob, EntityLivingBase participant, MobAdmission admission) {
        if (admission.type == null || worlds.get(mob.world).rejectedWrites.contains(mob.getUniqueID())) {
            return Result.UNSUPPORTED_MOB;
        }
        WorldServer world = (WorldServer) mob.world;
        if (!mob.isEntityAlive() || world.getEntityFromUuid(mob.getUniqueID()) != mob) {
            return Result.MOB_UNAVAILABLE;
        }
        if (participant == null || world.getEntityFromUuid(participant.getUniqueID()) != participant
                || !validParticipant((EntityCreature) mob, admission.type, participant)) {
            return Result.INVALID_PARTICIPANT;
        }
        return null;
    }

    public Inspection inspect(EntityLivingBase victim, int limit) {
        ResourceLocation registryId = EntityList.getKey(victim);
        String typeId = registryId == null ? "unregistered" : registryId.toString();
        EntityLivingBase actualTarget = victim instanceof EntityLiving ? ((EntityLiving) victim).getAttackTarget() : null;
        UUID target = actualTarget == null ? null : actualTarget.getUniqueID();
        MobAdmission admission = MobAdmission.resolve(tuning, foliaath, victim, registryId);
        WorldEncounters state = worlds.get(victim.world);
        UUID mobId = victim.getUniqueID();
        boolean assignmentRejected = state != null && state.rejectedWrites.contains(mobId);
        if (admission.type == null) {
            return new Inspection(typeId, admission.status(), false, target, null, 0, null,
                    Collections.emptyList(), 0);
        }
        EntityCreature mob = (EntityCreature) victim;
        WorldServer world = (WorldServer) mob.world;
        Encounter encounter = state == null || !mob.isEntityAlive() ? null : state.encounters.get(mob.getUniqueID());
        String phase = assignmentRejected ? "assignment_rejected" : "native_phase";
        if (encounter == null) {
            String status = admission.controls() && !assignmentRejected ? "supported_inactive" : phase;
            return new Inspection(typeId, status, false, target, null, 0, null, Collections.emptyList(), 0);
        }

        boolean controls = admission.controls() && !assignmentRejected && !state.yielded.contains(mobId);
        int tick = world.getMinecraftServer().getTickCounter();
        List<Participant> participants = new ArrayList<>();
        for (Map.Entry<UUID, Double> score : encounter.copyScores(limit).entrySet()) {
            UUID id = score.getKey();
            participants.add(new Participant(id, score.getValue(), controls
                    ? eligibility(world, mob, admission.type, encounter, id, tick, state.observations.get(mobId))
                    : phase));
        }
        int remaining = controls ? encounter.remainingTauntTicks(tick) : 0;
        UUID taunter = remaining == 0 ? null : encounter.getTaunter();
        if (taunter != null && resolve(world, mob, admission.type, world.getEntityFromUuid(taunter)) == null) {
            taunter = null;
            remaining = 0;
        }
        return new Inspection(typeId, controls ? "tracked" : phase, true, target, taunter, remaining,
                encounter.remainingResetTicks(tick),
                Collections.unmodifiableList(participants),
                encounter.participantCount() - participants.size());
    }

    public boolean beginWorkSample(World world, int ticks) {
        WorldEncounters state = worlds.get(world);
        if (state == null || state.workSample != null) {
            return false;
        }
        state.workSample = new WorkSample(ticks);
        return true;
    }

    public String takeWorkSample(World world) {
        WorldEncounters state = worlds.get(world);
        if (state == null || state.workSample == null || !state.workSample.complete()) {
            return null;
        }
        String report = state.workSample.report();
        state.workSample = null;
        return report;
    }

    private String eligibility(WorldServer world, EntityCreature mob, MobType type, Encounter encounter,
                               UUID participant, int tick, Map<UUID, TargetObservation> observations) {
        EntityLivingBase candidate = resolve(world, mob, type, world.getEntityFromUuid(participant));
        if (candidate == null) {
            return "unavailable_or_outside_engagement";
        }
        TargetObservation observation = observations == null ? null : observations.get(participant);
        if (observation == null) {
            return "observation_unavailable";
        }
        if (encounter.isEligible(participant, tick, observation)) {
            return observation.hasLostSight() ? "sight_grace" : "eligible";
        }
        return observation.hasLostSight() ? "no_line_of_sight" : "cannot_attack_or_pursue";
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)) {
            return;
        }
        WorldServer world = (WorldServer) event.world;
        WorldEncounters state = worlds.get(world);
        if (state == null || (state.encounters.isEmpty() && state.activeSample() == null)) {
            return;
        }

        int serverTick = world.getMinecraftServer().getTickCounter();
        if (state.encounters.isEmpty()) {
            state.tick(serverTick, null);
            return;
        }
        List<Runnable> targetUpdates = new ArrayList<>();
        WorkSample sample = state.activeSample();
        state.tick(serverTick, (id, encounter) -> {
            Entity entity = world.getEntityFromUuid(id);
            if (!(entity instanceof EntityCreature) || !entity.isEntityAlive()) {
                return true;
            }
            EntityCreature mob = (EntityCreature) entity;
            MobAdmission admission = admission(mob);
            if (state.rejectedWrites.contains(id) || admission.yieldsToNative()) {
                yieldToNative(state, id, encounter, serverTick);
                return encounter.resetDue(serverTick);
            }
            if (!admission.controls()) {
                return true;
            }
            if (state.yielded.contains(id) && encounter.resetDue(serverTick)) {
                return true;
            }
            reconcileTarget(world, state, mob, admission.type, encounter, serverTick, targetUpdates, sample);
            return encounter.resetDue(serverTick);
        });
        // The vanilla setter notifies Forge listeners that can change encounter membership.
        targetUpdates.forEach(Runnable::run);
    }

    private void reconcileTarget(WorldServer world, WorldEncounters state, EntityCreature mob, MobType type,
                                 Encounter encounter, int serverTick, List<Runnable> updates, WorkSample sample) {
        boolean returning = state.yielded.remove(mob.getUniqueID());
        UUID previous = encounter.getTarget();
        EntityLivingBase previousTarget = previous == null ? null
                : resolve(world, mob, type, world.getEntityFromUuid(previous));
        boolean lostTarget = previous != null && previousTarget == null;
        if (!lostTarget && (encounter.holdsOpeningTarget(mob.ticksExisted)
                || !encounter.shouldReevaluate(serverTick, mob.getEntityId()))) {
            return;
        }
        Map<UUID, TargetObservation> observations = new HashMap<>();
        Function<UUID, TargetObservation> observe = id -> observations.computeIfAbsent(id,
                key -> observe(world, mob, type, world.getEntityFromUuid(key), sample));
        EntityLivingBase current = mob.getAttackTarget();
        if (returning && current != null && world.getEntityFromUuid(current.getUniqueID()) == current) {
            encounter.resumeFromNative(current.getUniqueID(), observe);
        }
        UUID selected = state.select(encounter, serverTick, sample, observe);
        state.observations.put(mob.getUniqueID(), observations);
        if (selected == null && (current == null || !current.getUniqueID().equals(previous)
                || policies.get(type).preservesNativeTarget(world, mob, current))) {
            return;
        }
        EntityLivingBase target = selected == null ? null
                : resolve(world, mob, type, world.getEntityFromUuid(selected));
        if (target != current && (target != null || selected == null)) {
            updates.add(() -> applyTarget(world, state, mob, type, encounter, selected, target, current));
        }
    }

    private void applyTarget(WorldServer world, WorldEncounters state, EntityCreature mob, MobType type,
                             Encounter encounter, UUID selected, EntityLivingBase target, EntityLivingBase previous) {
        UUID mobId = mob.getUniqueID();
        if (!currentEncounter(world, state, mob, encounter) || state.yielded.contains(mobId)
                || state.rejectedWrites.contains(mobId) || !Objects.equals(encounter.getTarget(), selected)
                || !admission(mob).controls()
                || mob.getAttackTarget() == target) {
            return;
        }
        if (target == null ? mob.getAttackTarget() != previous || policies.get(type).preservesNativeTarget(world, mob, previous)
                : world.getEntityFromUuid(selected) != target || resolve(world, mob, type, target) == null) {
            return;
        }
        if (!writeTarget(mob, target)) {
            rejectWrite(world, state, mob, encounter);
        }
    }

    private boolean currentEncounter(WorldServer world, WorldEncounters state, EntityCreature mob, Encounter expected) {
        UUID id = mob.getUniqueID();
        return worlds.get(world) == state && state.encounters.get(id) == expected && mob.isEntityAlive()
                && world.getEntityFromUuid(id) == mob;
    }

    private void rejectWrite(WorldServer world, WorldEncounters state, EntityCreature mob, Encounter encounter) {
        if (currentEncounter(world, state, mob, encounter)) {
            UUID id = mob.getUniqueID();
            state.rejectedWrites.add(id);
            if (encounter != null) {
                yieldToNative(state, id, encounter, world.getMinecraftServer().getTickCounter());
            }
        }
    }

    private static void yieldToNative(WorldEncounters state, UUID id, Encounter encounter, int tick) {
        state.yielded.add(id);
        state.observations.remove(id);
        encounter.yieldToNative(tick);
    }

    private boolean writeTarget(EntityCreature mob, EntityLivingBase target) {
        if (mob.getAttackTarget() == target) {
            return true;
        }
        TargetWrite saved = targetWrite;
        targetWrite = new TargetWrite(mob, target, saved);
        try {
            mob.setAttackTarget(target);
        } finally {
            targetWrite = saved;
        }
        return mob.getAttackTarget() == target;
    }

    private TargetWrite activeWrite(EntityLiving mob) {
        for (TargetWrite write = targetWrite; write != null; write = write.previous) {
            if (write.mob == mob) {
                return write;
            }
        }
        return null;
    }

    static final class TargetWrite {
        private final EntityLiving mob;
        private final EntityLivingBase target;
        private final TargetWrite previous;
        private boolean consumed;

        TargetWrite(EntityLiving mob, EntityLivingBase target, TargetWrite previous) {
            this.mob = mob;
            this.target = target;
            this.previous = previous;
        }

        boolean consume(EntityLivingBase requested) {
            if (consumed || requested != target) {
                return false;
            }
            consumed = true;
            return true;
        }
    }

    public static final class Inspection {
        public final String typeId;
        public final String status;
        public final boolean tracked;
        public final UUID target;
        public final UUID taunter;
        public final int tauntTicks;
        public final Integer resetTicks;
        public final List<Participant> participants;
        public final int omitted;

        private Inspection(String typeId, String status, boolean tracked, UUID target, UUID taunter, int tauntTicks,
                           Integer resetTicks,
                           List<Participant> participants, int omitted) {
            this.typeId = typeId;
            this.status = status;
            this.tracked = tracked;
            this.target = target;
            this.taunter = taunter;
            this.tauntTicks = tauntTicks;
            this.resetTicks = resetTicks;
            this.participants = participants;
            this.omitted = omitted;
        }
    }

    public static final class Participant {
        public final UUID id;
        public final double score;
        public final String eligibility;

        private Participant(UUID id, double score, String eligibility) {
            this.id = id;
            this.score = score;
            this.eligibility = eligibility;
        }
    }
}
