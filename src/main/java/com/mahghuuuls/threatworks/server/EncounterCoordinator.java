package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.api.ThreatActions;
import com.mahghuuuls.threatworks.api.SummonThreatCreditEvent;
import com.mahghuuuls.threatworks.config.ServerTuning;
import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import com.mahghuuuls.threatworks.points.ThreatPoints;
import com.mahghuuuls.threatworks.threat.Encounter;
import com.mahghuuuls.threatworks.threat.Encounter.TargetObservation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.stats.StatList;
import net.minecraft.util.DamageSource;
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class EncounterCoordinator implements ThreatActions {
    public static final EncounterCoordinator INSTANCE = new EncounterCoordinator();

    private final Map<WorldServer, WorldEncounters> worlds = new IdentityHashMap<>();
    private final Map<EntityPlayerMP, Integer> pendingPlayerDeaths = new IdentityHashMap<>();
    private ServerTuning tuning;
    private ThreatPoints points;
    private DamageAttribution.SummonSource summons;
    private TargetWrite targetWrite;

    private EncounterCoordinator() {
    }

    public static boolean supports(EntityLivingBase mob) {
        return INSTANCE.enabledType(mob) != null;
    }

    private MobType enabledType(EntityLivingBase mob) {
        MobType type = MobTargets.supportedType(mob);
        return type != null && tuning != null && tuning.isEnabled(type) ? type : null;
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
        MobType type = enabledType(victim);
        if (type == null) {
            return;
        }

        EntityCreature mob = (EntityCreature) victim;
        WorldServer world = (WorldServer) mob.world;
        WorldEncounters state = worlds.get(world);
        UUID mobId = mob.getUniqueID();
        if (state == null || world.getEntityFromUuid(mobId) != mob) {
            return;
        }

        DamageAttribution hit = DamageAttribution.resolve(source, state.launchSession, summons);
        if (hit == null || world.getEntityFromUuid(hit.attacker.getUniqueID()) != hit.attacker
                || !MobTargets.isAttackable(mob, hit.attacker)) {
            return;
        }
        state.recordHit(mobId, () -> creditRecipient(world, mob, hit, source), mob.ticksExisted, hit.mayOpenEncounter(),
                before, after, tuning.rulesFor(type), () -> hit.multiplier(() -> points.multiplier(hit.attacker, tuning)),
                () -> incumbent != null && world.getEntityFromUuid(incumbent.getUniqueID()) == incumbent
                        && MobTargets.observe(world, mob, incumbent).isEligible()
                        ? incumbent.getUniqueID() : null);
    }

    private static UUID creditRecipient(WorldServer world, EntityCreature victim, DamageAttribution hit,
                                        DamageSource source) {
        EntityLivingBase owner = hit.summonOwner;
        if (owner != null && validOwner(world, victim, owner)) {
            SummonThreatCreditEvent decision = new SummonThreatCreditEvent(victim, hit.attacker, owner, source);
            MinecraftForge.EVENT_BUS.post(decision);
            if (decision.isOwnerCreditRequested() && validOwner(world, victim, owner)) {
                return owner.getUniqueID();
            }
        }
        return hit.attacker.getUniqueID();
    }

    private static boolean validOwner(WorldServer world, EntityCreature victim, EntityLivingBase owner) {
        return world.getEntityFromUuid(owner.getUniqueID()) == owner && MobTargets.isAttackable(victim, owner);
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
        return state != null && mob.isEntityAlive() && state.blocksTargetWrite(mob.getUniqueID(),
                mob.getAttackTarget() != requestedTarget,
                target -> MobTargets.resolve(world, (EntityCreature) mob, world.getEntityFromUuid(target)) != null);
    }

    @Override
    public Result grant(EntityLivingBase victim, EntityLivingBase participant, double amount) {
        Result rejected = checkAction(victim, participant);
        if (rejected != null) {
            return rejected;
        }
        Encounter encounter = worlds.get(victim.world).encounters.get(victim.getUniqueID());
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
        Result rejected = checkAction(victim, participant);
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
        Encounter encounter = state.encounters.get(mobId);
        if (encounter == null && !(participant instanceof EntityPlayerMP)) {
            return Result.UNTRACKED;
        }
        EntityLivingBase previous = mob.getAttackTarget();
        UUID incumbent = previous == null || world.getEntityFromUuid(previous.getUniqueID()) != previous
                ? null : previous.getUniqueID();
        UUID taunter = participant.getUniqueID();
        Encounter taunted = Encounter.tryTaunt(encounter, taunter, incumbent, world.getMinecraftServer().getTickCounter(),
                id -> MobTargets.observe(world, mob, world.getEntityFromUuid(id)),
                tuning.rulesFor(MobTargets.supportedType(mob)));
        if (taunted == null) {
            return Result.INELIGIBLE_TAUNTER;
        }
        state.encounters.put(mobId, taunted);
        applyTarget(world, state, mob, taunted, taunter, participant, previous);
        return Result.APPLIED;
    }

    private Result checkAction(EntityLivingBase mob, EntityLivingBase participant) {
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
        if (!supports(mob)) {
            return Result.UNSUPPORTED_MOB;
        }
        if (!mob.isEntityAlive() || world.getEntityFromUuid(mob.getUniqueID()) != mob) {
            return Result.MOB_UNAVAILABLE;
        }
        if (participant == null || world.getEntityFromUuid(participant.getUniqueID()) != participant
                || !MobTargets.isAttackable((EntityCreature) mob, participant)) {
            return Result.INVALID_PARTICIPANT;
        }
        return null;
    }

    public Inspection inspect(EntityLivingBase victim, int limit) {
        EntityCreature mob = (EntityCreature) victim;
        WorldServer world = (WorldServer) mob.world;
        WorldEncounters state = worlds.get(world);
        Encounter encounter = state == null || !mob.isEntityAlive() ? null : state.encounters.get(mob.getUniqueID());
        EntityLivingBase actualTarget = mob.getAttackTarget();
        UUID target = actualTarget == null ? null : actualTarget.getUniqueID();
        if (encounter == null) {
            return new Inspection(false, target, null, 0, null, Collections.emptyList(), 0);
        }

        int tick = world.getMinecraftServer().getTickCounter();
        List<Participant> participants = new ArrayList<>();
        for (Map.Entry<UUID, Double> score : encounter.copyScores(limit).entrySet()) {
            UUID id = score.getKey();
            participants.add(new Participant(id, score.getValue(), eligibility(world, mob, encounter, id, tick)));
        }
        int remaining = encounter.remainingTauntTicks(tick);
        UUID taunter = remaining == 0 ? null : encounter.getTaunter();
        if (taunter != null && !encounter.isEligible(taunter, tick,
                MobTargets.observe(world, mob, world.getEntityFromUuid(taunter)))) {
            taunter = null;
            remaining = 0;
        }
        return new Inspection(true, target, taunter, remaining, encounter.remainingResetTicks(tick),
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

    private String eligibility(WorldServer world, EntityCreature mob, Encounter encounter, UUID participant, int tick) {
        EntityLivingBase candidate = MobTargets.resolve(world, mob, world.getEntityFromUuid(participant));
        if (candidate == null) {
            return "unavailable_or_outside_engagement";
        }
        TargetObservation observation = MobTargets.observe(world, mob, candidate);
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
            reconcileTarget(world, state, (EntityCreature) entity, encounter, serverTick, targetUpdates, sample);
            return encounter.resetDue(serverTick);
        });
        // The vanilla setter notifies Forge listeners that can change encounter membership.
        targetUpdates.forEach(Runnable::run);
    }

    private void reconcileTarget(WorldServer world, WorldEncounters state, EntityCreature mob, Encounter encounter,
                                 int serverTick, List<Runnable> updates, WorkSample sample) {
        UUID previous = encounter.getTarget();
        EntityLivingBase previousTarget = previous == null ? null
                : MobTargets.resolve(world, mob, world.getEntityFromUuid(previous));
        boolean lostTarget = previous != null && previousTarget == null;
        if (!lostTarget && (encounter.holdsOpeningTarget(mob.ticksExisted)
                || !encounter.shouldReevaluate(serverTick, mob.getEntityId()))) {
            return;
        }
        UUID selected = state.select(encounter, serverTick, sample,
                id -> MobTargets.observe(world, mob, world.getEntityFromUuid(id), sample));
        EntityLivingBase current = mob.getAttackTarget();
        if (selected == null && (current == null || !current.getUniqueID().equals(previous))) {
            return;
        }
        EntityLivingBase target = selected == null ? null
                : MobTargets.resolve(world, mob, world.getEntityFromUuid(selected));
        if (target != current && (target != null || selected == null)) {
            updates.add(() -> applyTarget(world, state, mob, encounter, selected, target, current));
        }
    }

    private void applyTarget(WorldServer world, WorldEncounters state, EntityCreature mob, Encounter encounter,
                             UUID selected, EntityLivingBase target, EntityLivingBase previous) {
        UUID mobId = mob.getUniqueID();
        if (worlds.get(world) != state || state.encounters.get(mobId) != encounter || !mob.isEntityAlive()
                || world.getEntityFromUuid(mobId) != mob || !Objects.equals(encounter.getTarget(), selected)
                || mob.getAttackTarget() == target) {
            return;
        }
        if (target == null ? mob.getAttackTarget() != previous
                : world.getEntityFromUuid(selected) != target || MobTargets.resolve(world, mob, target) == null) {
            return;
        }
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
        public final boolean tracked;
        public final UUID target;
        public final UUID taunter;
        public final int tauntTicks;
        public final Integer resetTicks;
        public final List<Participant> participants;
        public final int omitted;

        private Inspection(boolean tracked, UUID target, UUID taunter, int tauntTicks, Integer resetTicks,
                           List<Participant> participants, int omitted) {
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
