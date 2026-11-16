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
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.dragon.phase.IPhase;
import net.minecraft.entity.boss.dragon.phase.PhaseList;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.stats.StatList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.feature.WorldGenEndPodium;
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
    private final DragonTargeting dragonTargeting = new DragonTargeting();
    private ServerTuning tuning;
    private ThreatPoints points;
    private DamageAttribution.SummonSource summons;
    private FoliaathCompatibility foliaath;
    private TargetWrite targetWrite;

    private EncounterCoordinator() {
        for (MobType type : MobType.values()) {
            if (type == MobType.ENDER_DRAGON) {
                policies.put(type, dragonTargeting);
            } else if (type != MobType.FOLIAATH) {
                policies.put(type, MobPolicy.forType(type));
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
                && (admission.controls() || admission.usesStrafeChoice() || admission.yieldsToNative()
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
                                float before, float after, DamageAttribution observed) {
        MobAdmission admission = admission(victim);
        MobType type = admission.type;
        if (type == null) {
            return;
        }

        EntityLiving mob = (EntityLiving) victim;
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
            if (!policies.get(type).keepsEncounterDuringNativePhase()) {
                yieldToNative(state, mobId, existing, tick);
            }
        }

        DamageAttribution hit = observed == null ? DamageAttribution.resolve(source, state.launchSession, summons) : observed;
        if (hit == null || world.getEntityFromUuid(hit.attacker.getUniqueID()) != hit.attacker
                || !validParticipant(mob, type, hit.attacker)) {
            return;
        }
        state.recordHit(mobId, () -> creditRecipient(world, mob, type, hit, source), mob.ticksExisted,
                (admission.controls() || admission.usesStrafeChoice()) && hit.mayOpenEncounter(),
                before, after, tuning.rulesFor(type), () -> hit.multiplier(() -> points.multiplier(hit.attacker, tuning)),
                () -> incumbent != null && world.getEntityFromUuid(incumbent.getUniqueID()) == incumbent
                        && observe(world, mob, type, incumbent, null).isEligible()
                        ? incumbent.getUniqueID() : null,
                () -> !admission.usesStrafeChoice() || (dragonWorld((EntityDragon) mob) == state
                        && admission(mob).usesStrafeChoice()));
        if (!admission.usesStrafeChoice() && state.encounters.containsKey(mobId)) {
            state.coverage.putIfAbsent(mobId, type);
        }
    }

    public void finishDragonHit(EntityDragon dragon, DamageSource source, EntityLivingBase incumbent,
                                float before, float after) {
        WorldEncounters state = dragonWorld(dragon);
        if (state == null) {
            return;
        }
        if (!DragonTargeting.isActive(dragon)) {
            state.discard(dragon.getUniqueID());
        } else if (before > after && admission(dragon).usesStrafeChoice()) {
            onHealthWritten(dragon, source, incumbent, before, after, null);
        }
    }

    public EntityLivingBase dragonOpeningTarget(EntityDragon dragon) {
        DragonStrafe strafe = activeStrafe(dragon);
        return strafe == null || !strafe.threatworks$isOrdinaryStrafe() ? null : strafe.threatworks$strafeTarget();
    }

    public EntityPlayer chooseDragonStrafe(EntityDragon dragon, EntityPlayer vanilla) {
        WorldEncounters state = dragonWorld(dragon);
        if (state == null || !admission(dragon).usesStrafeChoice()) {
            return vanilla;
        }
        WorldServer world = (WorldServer) dragon.world;
        int tick = world.getMinecraftServer().getTickCounter();
        Encounter encounter = state.encounterForAction(dragon.getUniqueID(), tick);
        if (encounter == null) {
            return vanilla;
        }
        BlockPos podium = world.getTopSolidOrLiquidBlock(WorldGenEndPodium.END_PODIUM_LOCATION);
        UUID selected = encounter.chooseForNativeAttack(id ->
                dragonTargeting.observe(world, dragon, world.getEntityFromUuid(id), podium));
        if (dragonWorld(dragon) != state || !admission(dragon).usesStrafeChoice()
                || state.encounters.get(dragon.getUniqueID()) != encounter) {
            return vanilla;
        }
        EntityPlayer actual = selected == null ? null : dragonTargeting.resolve(world, dragon,
                world.getEntityFromUuid(selected));
        return actual == null ? vanilla : actual;
    }

    public void confirmDragonStrafe(EntityDragon dragon, EntityLivingBase target) {
        WorldEncounters state = dragonWorld(dragon);
        if (state == null || target == null || !admission(dragon).usesStrafeChoice()) {
            return;
        }
        Encounter encounter = state.encounters.get(dragon.getUniqueID());
        if (encounter != null) {
            encounter.confirmNativeTarget(target.getUniqueID());
        }
    }

    private WorldEncounters dragonWorld(EntityDragon dragon) {
        if (!(dragon.world instanceof WorldServer)) {
            return null;
        }
        WorldServer world = (WorldServer) dragon.world;
        return world.getMinecraftServer().isCallingFromMinecraftThread()
                && world.getEntityFromUuid(dragon.getUniqueID()) == dragon ? worlds.get(world) : null;
    }

    private static DragonStrafe activeStrafe(EntityDragon dragon) {
        IPhase phase = dragon.getPhaseManager().getCurrentPhase();
        return phase.getType() == PhaseList.STRAFE_PLAYER ? (DragonStrafe) phase : null;
    }

    private UUID creditRecipient(WorldServer world, EntityLiving victim, MobType type, DamageAttribution hit,
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

    private boolean validParticipant(EntityLiving mob, MobType type, EntityLivingBase target) {
        return MobTargets.isAttackable(mob, target) && policies.get(type).accepts(mob, target);
    }

    private EntityLivingBase resolve(WorldServer world, EntityLiving mob, MobType type, Entity candidate) {
        return policies.get(type).resolve(world, mob, candidate);
    }

    private TargetObservation observe(WorldServer world, EntityLiving mob, MobType type, Entity candidate,
                                      WorkSample sample) {
        return policies.get(type).observe(world, mob, candidate, sample);
    }

    public boolean blocksTargetWrite(EntityLiving mob, EntityLivingBase requestedTarget) {
        if (!(mob.world instanceof WorldServer)) {
            return false;
        }
        TargetWrite write = activeWrite(mob);
        if (write != null) {
            return !write.consume(requestedTarget);
        }
        if (mob.getAttackTarget() == requestedTarget) {
            return false;
        }
        WorldServer world = (WorldServer) mob.world;
        WorldEncounters state = worlds.get(world);
        UUID id = mob.getUniqueID();
        MobType binding = state == null ? null : state.coverage.get(id);
        if (binding == null || !mob.isEntityAlive() || state.rejectedWrites.contains(id)) {
            return false;
        }
        MobPolicy policy = policies.get(binding);
        if (policy.protectsCommittedTarget(mob)) {
            return policy.blocksCommittedWrite(world, mob, requestedTarget);
        }
        return !state.yielded.contains(id) && policy.controlsImmediately(mob)
                && !policy.releasesNativeWrite(world, mob, mob.getAttackTarget())
                && state.blocksTargetWrite(id, true,
                target -> resolve(world, mob, binding, world.getEntityFromUuid(target)) != null);
    }

    public void clearGuardianBeamTarget(EntityGuardian guardian) {
        WorldEncounters state = worlds.get(guardian.world);
        if (state != null && GuardianMobPolicy.matchesType(state.coverage.get(guardian.getUniqueID()))) {
            performTargetWrite(guardian, null);
        } else {
            guardian.setAttackTarget(null);
        }
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
        if (activeWrite((EntityLiving) victim) != null) {
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
        EntityLiving mob = (EntityLiving) victim;
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
                || !validParticipant((EntityLiving) mob, admission.type, participant)) {
            return Result.INVALID_PARTICIPANT;
        }
        return null;
    }

    public Inspection inspect(EntityLivingBase victim, int limit) {
        ResourceLocation registryId = EntityList.getKey(victim);
        String typeId = registryId == null ? "unregistered" : registryId.toString();
        EntityLivingBase actualTarget = victim instanceof EntityLiving ? ((EntityLiving) victim).getAttackTarget() : null;
        if (victim instanceof EntityDragon) {
            DragonStrafe strafe = activeStrafe((EntityDragon) victim);
            actualTarget = strafe == null ? null : strafe.threatworks$strafeTarget();
        }
        UUID target = actualTarget == null ? null : actualTarget.getUniqueID();
        MobAdmission admission = MobAdmission.resolve(tuning, foliaath, victim, registryId);
        if (GuardianMobPolicy.matchesType(admission.type)) {
            actualTarget = policies.get(admission.type).effectiveTarget((EntityLiving) victim);
            target = actualTarget == null ? null : actualTarget.getUniqueID();
        }
        WorldEncounters state = worlds.get(victim.world);
        UUID mobId = victim.getUniqueID();
        boolean assignmentRejected = state != null && state.rejectedWrites.contains(mobId);
        if (admission.type == null) {
            return new Inspection(typeId, admission.status(), false, target, null, 0, null,
                    Collections.emptyList(), 0);
        }
        EntityLiving mob = (EntityLiving) victim;
        WorldServer world = (WorldServer) mob.world;
        Encounter encounter = state == null || !mob.isEntityAlive() ? null : state.encounters.get(mob.getUniqueID());
        String phase = assignmentRejected ? "assignment_rejected" : admission.usesStrafeChoice()
                ? "strafe_choice" : "native_phase";
        if (encounter == null) {
            String status = (admission.controls() || admission.usesStrafeChoice()) && !assignmentRejected
                    ? "supported_inactive" : phase;
            return new Inspection(typeId, status, false, target, null, 0, null, Collections.emptyList(), 0);
        }

        boolean controls = admission.controls() && !assignmentRejected && !state.yielded.contains(mobId);
        int tick = world.getMinecraftServer().getTickCounter();
        Map<UUID, TargetObservation> observations = state.observations.get(mobId);
        List<Participant> participants = new ArrayList<>();
        for (Map.Entry<UUID, Double> score : encounter.copyScores(limit).entrySet()) {
            UUID id = score.getKey();
            String participantStatus = controls || admission.usesStrafeChoice()
                    ? eligibility(world, mob, admission.type, encounter, id, tick, observations) : phase;
            participants.add(new Participant(id, score.getValue(), participantStatus));
        }
        int remaining = controls || (!assignmentRejected && policies.get(admission.type).keepsEncounterDuringNativePhase())
                ? encounter.remainingTauntTicks(tick) : 0;
        UUID taunter = remaining == 0 ? null : encounter.getTaunter();
        if (taunter != null && ((!controls && !taunter.equals(target))
                || resolve(world, mob, admission.type, world.getEntityFromUuid(taunter)) == null)) {
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

    private String eligibility(WorldServer world, EntityLiving mob, MobType type, Encounter encounter,
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
            if (!(entity instanceof EntityLiving) || !entity.isEntityAlive()) {
                return true;
            }
            EntityLiving mob = (EntityLiving) entity;
            MobAdmission admission = admission(mob);
            if (admission.usesStrafeChoice()) {
                if (encounter.resetDue(serverTick) || encounter.shouldReevaluate(serverTick, mob.getEntityId())) {
                    targetUpdates.add(() -> observeDragonAvailability(world, state, mob, encounter, serverTick));
                }
                return false;
            }
            if (state.rejectedWrites.contains(id)) {
                yieldToNative(state, id, encounter, serverTick);
                return encounter.resetDue(serverTick);
            }
            if (admission.yieldsToNative()) {
                if (policies.get(admission.type).keepsEncounterDuringNativePhase()) {
                    observeNativeAttack(world, state, mob, admission.type, encounter, serverTick, sample);
                } else {
                    yieldToNative(state, id, encounter, serverTick);
                }
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

    private void observeDragonAvailability(WorldServer world, WorldEncounters state, EntityLiving dragon,
                                           Encounter encounter, int tick) {
        UUID id = dragon.getUniqueID();
        if (state.encounters.get(id) != encounter) {
            return;
        }
        BlockPos podium = DragonTargeting.loadedPodium(world);
        Map<UUID, TargetObservation> observations = new HashMap<>();
        for (UUID candidate : encounter.copyScores().keySet()) {
            boolean eligible = dragonTargeting.eligiblePlayer(world, dragon, world.getEntityFromUuid(candidate), podium)
                    != null;
            observations.put(candidate, eligible ? TargetObservation.IN_REACH : TargetObservation.INELIGIBLE);
        }
        if (state.encounters.get(id) != encounter) {
            return;
        }
        encounter.observeParticipantAvailability(tick, candidate -> observations.get(candidate) == TargetObservation.IN_REACH);
        if (encounter.resetDue(tick)) {
            state.discard(id);
        } else {
            state.observations.put(id, observations);
        }
    }

    private void observeNativeAttack(WorldServer world, WorldEncounters state, EntityLiving mob, MobType type,
                                     Encounter encounter, int serverTick, WorkSample sample) {
        UUID id = mob.getUniqueID();
        boolean entering = state.yielded.add(id);
        EntityLivingBase current = policies.get(type).effectiveTarget(mob);
        UUID currentId = current == null ? null : current.getUniqueID();
        if (!entering && (encounter.getTarget() == null || Objects.equals(encounter.getTarget(), currentId))
                && !encounter.shouldReevaluate(serverTick, mob.getEntityId())) {
            return;
        }
        Map<UUID, TargetObservation> observations = new HashMap<>();
        encounter.observeNativeAttack(serverTick, currentId, participant -> observations.computeIfAbsent(participant,
                key -> observe(world, mob, type, world.getEntityFromUuid(key), sample)));
        state.observations.put(id, observations);
    }

    private void reconcileTarget(WorldServer world, WorldEncounters state, EntityLiving mob, MobType type,
                                 Encounter encounter, int serverTick, List<Runnable> updates, WorkSample sample) {
        boolean returning = state.yielded.remove(mob.getUniqueID());
        UUID previous = encounter.getTarget();
        EntityLivingBase previousTarget = previous == null ? null
                : resolve(world, mob, type, world.getEntityFromUuid(previous));
        boolean lostTarget = previous != null && previousTarget == null;
        if (!returning && !lostTarget && (encounter.holdsOpeningTarget(mob.ticksExisted)
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

    private void applyTarget(WorldServer world, WorldEncounters state, EntityLiving mob, MobType type,
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

    private boolean currentEncounter(WorldServer world, WorldEncounters state, EntityLiving mob, Encounter expected) {
        UUID id = mob.getUniqueID();
        return worlds.get(world) == state && state.encounters.get(id) == expected && mob.isEntityAlive()
                && world.getEntityFromUuid(id) == mob;
    }

    private void rejectWrite(WorldServer world, WorldEncounters state, EntityLiving mob, Encounter encounter) {
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

    private boolean writeTarget(EntityLiving mob, EntityLivingBase target) {
        if (mob.getAttackTarget() == target) {
            return true;
        }
        performTargetWrite(mob, target);
        return mob.getAttackTarget() == target;
    }

    private void performTargetWrite(EntityLiving mob, EntityLivingBase target) {
        TargetWrite saved = targetWrite;
        targetWrite = new TargetWrite(mob, target, saved);
        try {
            mob.setAttackTarget(target);
        } finally {
            targetWrite = saved;
        }
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
