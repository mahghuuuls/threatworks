package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.api.ThreatActions;
import com.mahghuuuls.threatworks.api.ThreatQueries;
import com.mahghuuuls.threatworks.api.SummonThreatCreditEvent;
import com.mahghuuuls.threatworks.api.TargetAwareness;
import com.mahghuuuls.threatworks.api.TargetAwareness.Decision;
import com.mahghuuuls.threatworks.api.TargetAwareness.Transition;
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
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.dragon.phase.IPhase;
import net.minecraft.entity.boss.dragon.phase.PhaseList;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Items;
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
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;

public final class EncounterCoordinator implements ThreatActions, ThreatQueries {
    public static final EncounterCoordinator INSTANCE = new EncounterCoordinator();

    private final Map<WorldServer, WorldEncounters> worlds = new IdentityHashMap<>();
    private final Map<EntityPlayerMP, Integer> pendingPlayerDeaths = new IdentityHashMap<>();
    private final Map<MobType, MobPolicy> policies = new EnumMap<>(MobType.class);
    private final DragonTargeting dragonTargeting = new DragonTargeting();
    private final AwarenessIntegration awareness = new AwarenessIntegration();
    private final BiPredicate<EntityLiving, EntityLivingBase> excludedFromTakeover = this::isAwarenessExcluded;
    private ServerTuning tuning;
    private ThreatPoints points;
    private DamageAttribution.SummonSource summons;
    private FoliaathCompatibility foliaath;
    private TargetWrite targetWrite;
    private final TakeoverSamples.Budget takeoverBudget = new TakeoverSamples.Budget();
    private final List<TakeoverSamples.Pending> pendingTakeovers = new ArrayList<>();

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

    public boolean registerAwareness(TargetAwareness provider) {
        return awareness.register(provider);
    }

    public void closeAwarenessRegistration() {
        awareness.closeRegistration();
    }

    public boolean hasAwarenessProvider() {
        return awareness.active();
    }

    private WorldEncounters awarenessState(EntityLiving mob) {
        return awarenessState(mob, false);
    }

    private WorldEncounters awarenessState(EntityLiving mob, boolean refresh) {
        if (!awareness.active() || mob == null) {
            return null;
        }
        boolean zombie = mob.getClass() == EntityZombie.class;
        if ((!zombie && (mob.getClass() != EntitySkeleton.class || mob.getHeldItemMainhand().getItem() != Items.BOW))
                || !(mob.world instanceof WorldServer) || mob.isAIDisabled() || mob.isRiding() || mob.isBeingRidden()
                || mob.targetTasks.isControlFlagDisabled(1)
                || !((NativeTargetGoals) mob).threatworks$hasOriginalTargetGoals()) {
            return null;
        }
        WorldServer world = (WorldServer) mob.world;
        if (!world.getMinecraftServer().isCallingFromMinecraftThread() || !ParticipantQuery.live(world, mob)) {
            return null;
        }
        WorldEncounters state = worlds.get(world);
        if (state == null) {
            return null;
        }
        UUID id = mob.getUniqueID();
        int tick = world.getMinecraftServer().getTickCounter();
        WorldEncounters.AwarenessQualification check = state.awarenessQualification.get(id);
        if (check == null) {
            check = new WorldEncounters.AwarenessQualification();
            state.awarenessQualification.put(id, check);
        }
        if (refresh || check.tick != tick) {
            check.tick = tick;
            MobType type = zombie ? MobType.ZOMBIE : MobType.SKELETON;
            check.qualifies = MobAdmission.configuredType(tuning, mob) == type
                    && MobTargets.matchesBehavior((EntityCreature) mob, type) && hasStockTargetGoals(mob, zombie);
        }
        return check.qualifies ? state : null;
    }

    private static boolean hasStockTargetGoals(EntityLiving mob, boolean zombie) {
        if (mob.targetTasks.taskEntries.size() != (zombie ? 4 : 3)) {
            return false;
        }
        boolean revenge = false;
        boolean player = false;
        boolean golem = false;
        boolean villager = false;
        for (EntityAITaskEntry entry : mob.targetTasks.taskEntries) {
            if (entry.priority == 1 && entry.action.getClass() == EntityAIHurtByTarget.class) {
                revenge = true;
                continue;
            }
            if (entry.action.getClass() != EntityAINearestAttackableTarget.class) {
                return false;
            }
            Class<?> target = ((NativeTargetGoals.Nearest) entry.action).threatworks$targetClass();
            player |= entry.priority == 2 && target == EntityPlayer.class;
            golem |= entry.priority == 3 && target == EntityIronGolem.class;
            villager |= entry.priority == 3 && target == EntityVillager.class;
        }
        return revenge && player && golem && (!zombie || villager);
    }

    private Decision awarenessDecision(EntityLiving mob, Entity candidate) {
        if (!(candidate instanceof EntityPlayerMP)) {
            return Decision.ALLOW;
        }
        WorldEncounters state = awarenessState(mob);
        EntityPlayerMP player = (EntityPlayerMP) candidate;
        if (state == null) {
            return Decision.ALLOW;
        }
        if (!ParticipantQuery.live((WorldServer) mob.world, player)) {
            return Decision.BLOCKED;
        }
        if (state.awarenessFailed(mob.getUniqueID(), player.getUniqueID())) {
            return Decision.ALLOW;
        }
        Decision decision = awareness.decide(mob, player);
        if (decision == null) {
            state.failAwareness(mob.getUniqueID(), player.getUniqueID());
            invalidateAwareness(state, mob.getUniqueID(), player.getUniqueID());
            return Decision.ALLOW;
        }
        return decision == Decision.ALLOW && player.getUniqueID().equals(
                state.awarenessQualification.get(mob.getUniqueID()).focusToRelease) ? Decision.UNAWARE : decision;
    }

    public boolean isAwarenessExcluded(EntityLiving mob, EntityLivingBase candidate) {
        return awarenessDecision(mob, candidate) != Decision.ALLOW;
    }

    public boolean awarenessChanged(EntityLiving mob, EntityPlayerMP player, boolean cleared) {
        WorldEncounters state = awarenessState(mob, true);
        if (state == null || player == null || !ParticipantQuery.live((WorldServer) mob.world, player)) {
            return false;
        }
        UUID mobId = mob.getUniqueID();
        UUID playerId = player.getUniqueID();
        if (state.awarenessFailed(mobId, playerId)) {
            return false;
        }
        Encounter encounter = invalidateAwareness(state, mobId, playerId);
        if (cleared) {
            forgetCachedTargets(mob, playerId);
            if (mob.getAttackTarget() == player) {
                state.awarenessQualification.get(mobId).focusToRelease = playerId;
            }
        }
        if (encounter != null) {
            if (cleared && playerId.equals(encounter.getTarget())) {
                encounter.confirmNativeTarget(null);
            }
            if (playerId.equals(encounter.getTaunter()) && (cleared || isAwarenessExcluded(mob, player))) {
                clearTaunt(state, mobId, encounter);
            }
        }
        return !state.awarenessFailed(mobId, playerId);
    }

    private Encounter invalidateAwareness(WorldEncounters state, UUID mob, UUID player) {
        state.observations.remove(mob);
        state.takeovers.remove(mob);
        Encounter encounter = state.encounters.get(mob);
        if (encounter != null) {
            encounter.participantChanged(player);
        }
        return encounter;
    }

    private static void forgetCachedTargets(EntityLiving mob, UUID playerId) {
        for (EntityAITaskEntry entry : mob.targetTasks.taskEntries) {
            if (entry.action instanceof NativeTargetGoals.CachedTarget) {
                ((NativeTargetGoals.CachedTarget) entry.action).threatworks$forgetTarget(playerId);
            }
        }
    }

    public void releaseClearedFocus(EntityLiving mob) {
        if (!awareness.active()) {
            return;
        }
        WorldEncounters state = worlds.get(mob.world);
        WorldEncounters.AwarenessQualification check = state == null ? null
                : state.awarenessQualification.get(mob.getUniqueID());
        if (check == null || check.focusToRelease == null || activeWrite(mob) != null) {
            return;
        }
        UUID playerId = check.focusToRelease;
        check.focusToRelease = null;
        EntityLivingBase current = mob.getAttackTarget();
        if (awarenessState(mob) != null) {
            // A synchronous setter listener may have requested the clear before a goal cached its target.
            forgetCachedTargets(mob, playerId);
            if (current != null && playerId.equals(current.getUniqueID())) {
                performTargetWrite(mob, null);
            }
        }
    }

    public void releaseExcludedFocus(EntityLiving mob) {
        if (!awareness.active() || activeWrite(mob) != null) {
            return;
        }
        releaseClearedFocus(mob);
        EntityLivingBase target = mob.getAttackTarget();
        if (isAwarenessExcluded(mob, target)) {
            WorldEncounters state = worlds.get(mob.world);
            UUID mobId = mob.getUniqueID();
            UUID playerId = target.getUniqueID();
            Encounter encounter = invalidateAwareness(state, mobId, playerId);
            if (encounter != null && playerId.equals(encounter.getTaunter())) {
                clearTaunt(state, mobId, encounter);
            }
            performTargetWrite(mob, null);
        }
    }

    private static void clearTaunt(WorldEncounters state, UUID mob, Encounter encounter) {
        encounter.clearTaunt();
        state.clearTauntIdentity(mob);
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
            WorldServer serverWorld = (WorldServer) world;
            WorldEncounters state = awareness.active()
                    ? new WorldEncounters(world.provider.getDimension(), awareness, id -> {
                        Entity mob = serverWorld.getEntityFromUuid(id);
                        return mob instanceof EntityLiving && (mob.getClass() == EntityZombie.class
                                || mob.getClass() == EntitySkeleton.class)
                                && MobAdmission.configuredType(tuning, (EntityLiving) mob) != null;
                    })
                    : new WorldEncounters();
            worlds.put(serverWorld, state);
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
                state.endWorld(Transition.WORLD_UNLOADED);
            }
        }
    }

    public void serverStopped() {
        worlds.forEach((world, state) -> {
            world.removeEventListener(state);
            state.endWorld(Transition.SERVER_STOPPED);
        });
        worlds.clear();
        pendingPlayerDeaths.clear();
        pendingTakeovers.clear();
        if (points != null) {
            points.clearSetBonuses();
        }
        tuning = null;
        points = null;
        awareness.serverStopped();
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
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        pendingPlayerDeaths.forEach(this::confirmPlayerDeath);
        pendingPlayerDeaths.clear();
        publishSupplementalTakeovers();
        awareness.deliverChanges();
    }

    private void confirmPlayerDeath(EntityPlayerMP player, int previousDeaths) {
        // Vanilla increments the death statistic after Forge accepts the death event.
        if (player.getStatFile().readStat(StatList.DEATHS) != previousDeaths) {
            if (points != null) {
                points.discardSetBonuses(player);
            }
            WorldEncounters.removePlayerScores(worlds.values(), player.getUniqueID());
            awareness.playerDied(player.getUniqueID());
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
        if (state == null || world.getEntityFromUuid(mobId) != mob) {
            return;
        }
        if (activeWrite(mob) != null || state.rejectedWrites.contains(mobId)) {
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

    private void notifyTauntCommitted(EntityLiving mob, EntityPlayerMP player) {
        WorldEncounters state = awarenessState(mob);
        UUID mobId = mob.getUniqueID();
        UUID playerId = player.getUniqueID();
        if (state != null && !state.awarenessFailed(mobId, playerId)) {
            if (awareness.tauntCommitted(mob, player)) {
                awarenessChanged(mob, player, false);
            } else if (ParticipantQuery.live((WorldServer) mob.world, player)) {
                state.failAwareness(mobId, playerId);
            }
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
        return awarenessDecision(mob, candidate) == Decision.ALLOW ? policies.get(type).resolve(world, mob, candidate) : null;
    }

    private TargetObservation observe(WorldServer world, EntityLiving mob, MobType type, Entity candidate,
                                      WorkSample sample) {
        return awarenessDecision(mob, candidate) == Decision.ALLOW
                ? policies.get(type).observe(world, mob, candidate, sample) : TargetObservation.INELIGIBLE;
    }

    public boolean blocksTargetWrite(EntityLiving mob, EntityLivingBase requestedTarget) {
        if (!(mob.world instanceof WorldServer)) {
            return false;
        }
        TargetWrite write = activeWrite(mob);
        if (write != null) {
            return !write.consume(requestedTarget);
        }
        if (isAwarenessExcluded(mob, requestedTarget)) {
            return true;
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
        boolean awarenessApplies = awarenessState(mob, true) != null;
        boolean revealing = awarenessApplies && participant instanceof EntityPlayerMP;
        if (revealing && awarenessDecision(mob, participant) == Decision.BLOCKED) {
            return Result.INELIGIBLE_TAUNTER;
        }
        WorldEncounters.AwarenessQualification qualified = awarenessApplies
                ? state.awarenessQualification.get(mobId) : null;
        WorldEncounters.ObservationFrame observations = state.new ObservationFrame(tick);
        Encounter.Taunt pending = prepareTaunt(world, mob, admission.type, encounter, taunter, incumbent,
                revealing, observations);
        if (pending == null) {
            return Result.INELIGIBLE_TAUNTER;
        }
        if (!writeTarget(mob, participant) || !currentEncounter(world, state, mob, encounter)) {
            if (!awarenessApplies) {
                rejectWrite(world, state, mob, encounter);
            }
            return Result.TARGET_ASSIGNMENT_REJECTED;
        }
        boolean awarenessAppliesNow = awarenessState(mob, true) != null;
        if (!ParticipantQuery.live(world, participant) || !admission(mob).controls()
                || policies.get(admission.type).resolve(world, mob, participant) == null
                || awarenessApplies != awarenessAppliesNow
                || (revealing && awarenessDecision(mob, participant) == Decision.BLOCKED)) {
            if (awarenessApplies || awarenessAppliesNow) {
                restoreBeforeTaunt(world, mob, admission.type, previous, participant);
            } else {
                rejectWrite(world, state, mob, encounter);
            }
            return Result.TARGET_ASSIGNMENT_REJECTED;
        }
        if (qualified != null) {
            // Target notifications are synchronous and can change eligibility without changing awareness.
            observations = state.new ObservationFrame(tick);
            pending = prepareTaunt(world, mob, admission.type, encounter, taunter, incumbent,
                    revealing, observations);
            if (pending == null || !currentEncounter(world, state, mob, encounter)
                    || mob.getAttackTarget() != participant
                    || (revealing && awarenessDecision(mob, participant) == Decision.BLOCKED)) {
                if (currentEncounter(world, state, mob, encounter)) {
                    restoreBeforeTaunt(world, mob, admission.type, previous, participant);
                }
                return Result.TARGET_ASSIGNMENT_REJECTED;
            }
        }
        state.putEncounter(mobId, pending.apply());
        state.coverage.put(mobId, admission.type);
        if (qualified == null) {
            state.observations.put(mobId, observations);
        } else {
            state.observations.remove(mobId);
            if (taunter.equals(qualified.focusToRelease)) {
                qualified.focusToRelease = null;
            }
        }
        state.takeovers.remove(mobId);
        state.recordTaunt(mobId, participant);
        state.yielded.remove(mobId);
        if (revealing) {
            notifyTauntCommitted(mob, (EntityPlayerMP) participant);
        }
        return Result.APPLIED;
    }

    private Encounter.Taunt prepareTaunt(WorldServer world, EntityLiving mob, MobType type, Encounter encounter,
                                         UUID taunter, UUID incumbent, boolean revealing,
                                         WorldEncounters.ObservationFrame observations) {
        return Encounter.prepareTaunt(encounter, taunter, incumbent, observations.tick,
                id -> observations.observe(world, id, candidate -> revealing && id.equals(taunter)
                        ? policies.get(type).observe(world, mob, candidate, null)
                        : observe(world, mob, type, candidate, null)),
                tuning.rulesFor(type));
    }

    private void restoreBeforeTaunt(WorldServer world, EntityLiving mob, MobType type,
                                    EntityLivingBase previous, EntityLivingBase attempted) {
        if (mob.getAttackTarget() == attempted && ParticipantQuery.live(world, mob)) {
            writeTarget(mob, resolve(world, mob, type, previous));
        }
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

    @Override
    public View query(EntityLivingBase mob, EntityLivingBase participant) {
        if (mob == null) {
            return ParticipantQuery.empty(ThreatQueries.State.MOB_UNAVAILABLE);
        }
        if (!(mob.world instanceof WorldServer)) {
            return ParticipantQuery.empty(ThreatQueries.State.SERVER_WORLD_UNAVAILABLE);
        }
        WorldServer world = (WorldServer) mob.world;
        if (!world.getMinecraftServer().isCallingFromMinecraftThread()) {
            return ParticipantQuery.empty(ThreatQueries.State.WRONG_THREAD);
        }
        WorldEncounters state = worlds.get(world);
        if (state == null) {
            return ParticipantQuery.empty(ThreatQueries.State.SERVER_WORLD_UNAVAILABLE);
        }
        if (!ParticipantQuery.live(world, mob)) {
            return ParticipantQuery.empty(ThreatQueries.State.MOB_UNAVAILABLE);
        }
        if (!(mob instanceof EntityLiving)) {
            return ParticipantQuery.empty(ThreatQueries.State.UNSUPPORTED);
        }
        MobType type = MobAdmission.configuredType(tuning, mob);
        return ParticipantQuery.read(world, state, type, (EntityLiving) mob, participant, targetWrite != null,
                world.getMinecraftServer().getTickCounter());
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
        WorldEncounters.ObservationFrame observations = state.observations.get(mobId);
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
                               UUID participant, int tick, WorldEncounters.ObservationFrame observations) {
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
                return Transition.MOB_REMOVED;
            }
            EntityLiving mob = (EntityLiving) entity;
            state.clearMissingTaunt(world, id, encounter);
            MobAdmission admission = admission(mob);
            if (admission.usesStrafeChoice()) {
                if (encounter.resetDue(serverTick) || encounter.shouldReevaluate(serverTick, mob.getEntityId())) {
                    targetUpdates.add(() -> observeDragonAvailability(world, state, mob, encounter, serverTick));
                }
                return null;
            }
            if (state.rejectedWrites.contains(id)) {
                yieldToNative(state, id, encounter, serverTick);
                return encounter.resetDue(serverTick) ? Transition.RESET : null;
            }
            if (admission.yieldsToNative()) {
                state.takeovers.remove(id);
                if (policies.get(admission.type).keepsEncounterDuringNativePhase()) {
                    observeNativeAttack(world, state, mob, admission.type, encounter, serverTick, sample);
                } else {
                    yieldToNative(state, id, encounter, serverTick);
                }
                return encounter.resetDue(serverTick) ? Transition.RESET : null;
            }
            if (!admission.controls()) {
                return Transition.UNSUPPORTED;
            }
            if (state.yielded.contains(id) && encounter.resetDue(serverTick)) {
                return Transition.RESET;
            }
            reconcileTarget(world, state, mob, admission.type, encounter, serverTick, targetUpdates, sample);
            return encounter.resetDue(serverTick) ? Transition.RESET : null;
        });
        // The vanilla setter notifies Forge listeners that can change encounter membership.
        targetUpdates.forEach(Runnable::run);
    }

    private void publishSupplementalTakeovers() {
        if (pendingTakeovers.isEmpty()) return;
        int tick = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance().getTickCounter();
        pendingTakeovers.sort(Comparator.comparingInt(TakeoverSamples.Pending::lastSupplementTick));
        try {
            for (int pass = 0; pass < 2; pass++) {
                for (TakeoverSamples.Pending pending : pendingTakeovers) {
                    if (takeoverBudget.exhausted(tick)) return;
                    pending.supplement(tick, takeoverBudget, excludedFromTakeover);
                }
            }
        } finally {
            pendingTakeovers.clear();
        }
    }

    private void observeDragonAvailability(WorldServer world, WorldEncounters state, EntityLiving dragon,
                                           Encounter encounter, int tick) {
        UUID id = dragon.getUniqueID();
        if (state.encounters.get(id) != encounter) {
            return;
        }
        BlockPos podium = DragonTargeting.loadedPodium(world);
        WorldEncounters.ObservationFrame observations = state.new ObservationFrame(tick);
        for (UUID candidate : encounter.copyScores().keySet()) {
            observations.observe(world, candidate, entity -> dragonTargeting.eligiblePlayer(world, dragon, entity, podium)
                    != null ? TargetObservation.IN_REACH : TargetObservation.INELIGIBLE);
        }
        if (state.encounters.get(id) != encounter) {
            return;
        }
        encounter.observeParticipantAvailability(tick, candidate -> observations.get(candidate) == TargetObservation.IN_REACH);
        if (encounter.resetDue(tick)) {
            state.reset(id);
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
        WorldEncounters.ObservationFrame observations = state.new ObservationFrame(serverTick);
        encounter.observeNativeAttack(serverTick, currentId, participant -> observations.observe(world, participant,
                candidate -> observe(world, mob, type, candidate, sample)));
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
        EntityLivingBase current = mob.getAttackTarget();
        boolean canPublish = !returning && current != null && encounter.participantCount() > 1
                && TakeoverSamples.supportsSampling(mob, type);
        TakeoverSamples.Position selectionPosition = canPublish ? new TakeoverSamples.Position(mob) : null;
        WorldEncounters.ObservationFrame observations = state.new ObservationFrame(serverTick, canPublish);
        Function<UUID, TargetObservation> observe = id -> observations.observe(world, id,
                candidate -> observe(world, mob, type, candidate, sample));
        if (returning && current != null && world.getEntityFromUuid(current.getUniqueID()) == current) {
            encounter.resumeFromNative(current.getUniqueID(), observe);
        }
        long versionBeforeSelection = encounter.decisionVersion();
        UUID selected = state.select(encounter, serverTick, sample, observe);
        UUID mobId = mob.getUniqueID();
        state.observations.put(mobId, observations);
        if (canPublish && selected != null && selected.equals(previous)
                && selected.equals(current.getUniqueID()) && world.getEntityFromUuid(selected) == current
                && currentEncounter(world, state, mob, encounter) && mob.getAttackTarget() == current
                && versionBeforeSelection == encounter.decisionVersion() && selectionPosition.matches(mob)
                && observations.positionsUnchanged(world)) {
            TakeoverSamples.Pending pending = TakeoverSamples.record(world, state, mob, type, encounter, current,
                    observations, sample);
            if (pending != null) pendingTakeovers.add(pending);
        } else {
            state.takeovers.remove(mobId);
        }
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
        state.takeovers.remove(id);
        state.clearTauntIdentity(id);
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
