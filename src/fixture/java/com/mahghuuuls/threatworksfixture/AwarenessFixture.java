package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.ThreatworksMod;
import com.mahghuuuls.threatworks.api.TargetAwareness;
import com.mahghuuuls.threatworks.api.ThreatActions.Result;
import com.mahghuuuls.threatworks.api.ThreatQueries;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAIAttackRangedBow;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.living.ZombieEvent.SummonAidEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Independent consumer of the optional public contract in disposable servers. */
public final class AwarenessFixture extends CommandBase implements TargetAwareness {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksAwarenessFixture");
    private static final int VANISH = 1;
    private static final int OTHER = 2;

    private final List<EncounterChange> changes = new ArrayList<>();
    private EntityLiving mob;
    private EntityPlayerMP closer;
    private EntityPlayerMP farther;
    private EntityPlayerMP reserve;
    private EntityZombie reinforcementCaller;
    private EntityZombie reinforcement;
    private BlockPos reinforcementPosition;
    private boolean closerAware;
    private boolean closerRemembered;
    private boolean fartherAware;
    private boolean fartherBlocked;
    private boolean reserveAware;
    private boolean failNextReveal;
    private boolean measuring;
    private EntityPlayerMP pendingTaunter;
    private WriteChange writeChange;
    private int closerProtection;
    private int decisions;
    private int damageReveals;
    private int tauntReveals;
    private int focusClears;
    private int forbiddenAssignments;
    private int ticks;
    private int limit;
    private double retained;
    private String scenario;
    private Phase phase;
    private boolean cancelDamage;
    private boolean damageCanceled;
    private float healthBeforeArrow;
    private float mobHealthBeforeArrow;

    private enum Phase {
        ENTER_BOW, NATIVE, REVENGE, CLEARED, DRAW, RELEASE, OVERLAP, EXPIRED, REDISCOVERED, FALLBACK, RESET, IMPACT, REOPENED
    }

    private enum WriteChange {
        BLOCK_FARTHER, INVALIDATE_CLOSER
    }

    @Override
    public String getName() {
        return "twawareness";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "twawareness <zombie|skeleton|disabled-zombie|blacklisted-skeleton>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (mob != null || args.length != 1 || !("zombie".equals(args[0]) || "skeleton".equals(args[0])
                || "disabled-zombie".equals(args[0]) || "blacklisted-skeleton".equals(args[0]))) {
            throw new CommandException(getUsage(sender));
        }
        scenario = args[0];
        WorldServer world = server.getWorld(0);
        BlockPos spawn = world.getSpawnPoint().add(0, 10, 0);
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                world.setBlockState(spawn.add(x, 0, z), Blocks.STONE.getDefaultState());
            }
        }
        world.setWorldTime(14000);
        world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
        world.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
        closer = player(world, "AwarenessNear", spawn, 1.5);
        farther = player(world, "AwarenessFar", spawn, -3);
        reserve = player(world, "AwarenessReserve", spawn, 4);
        closerAware = closerRemembered = false;
        fartherAware = true;
        fartherBlocked = reserveAware = failNextReveal = measuring = false;
        pendingTaunter = null;
        writeChange = null;
        closerProtection = 0;
        decisions = damageReveals = tauntReveals = focusClears = forbiddenAssignments = ticks = 0;
        cancelDamage = damageCanceled = false;
        changes.clear();
        spawnMob(scenario.endsWith("zombie") ? new EntityZombie(world) : new EntitySkeleton(world),
                spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5);
        if (scenario.startsWith("disabled-") || scenario.startsWith("blacklisted-")) {
            outsideCoverage();
            return;
        }
        if ("skeleton".equals(scenario)) {
            fartherAware = false;
            mob.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            require(!ThreatworksMod.targetAwarenessChanged(mob, closer), "sword combat incorrectly qualified");
            mob.setAttackTarget(closer);
            require(mob.getAttackTarget() == closer, "unsupported native target was filtered");
            require(taunt(closer) == Result.APPLIED, "sword mode did not allow baseline tracking");
            mob.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            enter(Phase.ENTER_BOW, 2);
        } else {
            enter(Phase.NATIVE, 90);
        }
        measuring = true;
        LOGGER.info("tw_awareness_started scenario={}", scenario);
    }

    private void outsideCoverage() {
        if (mob instanceof EntitySkeleton) {
            mob.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        }
        require(!ThreatworksMod.targetAwarenessChanged(mob, closer), "excluded mob accepted a pair update");
        mob.setAttackTarget(closer);
        require(mob.getAttackTarget() == closer && decisions == 0, "excluded mob lost native targeting");
        LOGGER.info("tw_awareness_result scenario={} status=PASS decisions={}", scenario, decisions);
        cleanup();
    }

    private void spawnMob(EntityLiving entity, double x, double y, double z) {
        mob = entity;
        mob.enablePersistence();
        mob.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.025);
        mob.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        mob.setPosition(x, y, z);
        if (!mob.world.spawnEntity(mob)) {
            cleanup();
            throw new IllegalStateException("Could not spawn awareness fixture");
        }
    }

    private EntityPlayerMP player(WorldServer world, String name, BlockPos spawn, double x) {
        EntityPlayerMP player = CoverageFixtureCommand.fixturePlayer(world, scenario, name);
        player.setPosition(spawn.getX() + 0.5 + x, spawn.getY() + 1, spawn.getZ() + 0.5);
        player.setNoGravity(true);
        player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(80);
        player.setHealth(80);
        if (!world.spawnEntity(player)) {
            throw new IllegalStateException("Could not spawn fixture participant " + name);
        }
        return player;
    }

    @Override
    public Decision decide(EntityLiving host, EntityPlayerMP participant) {
        if (host == reinforcement && participant == closer) {
            return Decision.UNAWARE;
        }
        if (host != mob) {
            return Decision.ALLOW;
        }
        decisions++;
        return participant == closer ? closerDecision()
                : participant == farther ? fartherBlocked ? Decision.BLOCKED
                : fartherAware ? Decision.ALLOW : Decision.UNAWARE
                : participant == reserve && !reserveAware ? Decision.UNAWARE : Decision.ALLOW;
    }

    private Decision closerDecision() {
        return closerProtection != 0 ? Decision.BLOCKED : closerAware ? Decision.ALLOW : Decision.UNAWARE;
    }

    @Override
    public void tauntCommitted(EntityLiving host, EntityPlayerMP player) {
        if (host != mob) {
            return;
        }
        if (failNextReveal) {
            failNextReveal = false;
            throw new IllegalStateException("Expected reveal callback failure");
        }
        if (mob.getAttackTarget() != player || view(player).tauntForcingFocus() != ThreatQueries.Confirmation.YES) {
            throw new AssertionError("Taunt reveal arrived before authoritative focus");
        }
        tauntReveals++;
        if (player == closer) {
            closerAware = true;
        } else if (player == farther) {
            fartherAware = true;
        }
    }

    @Override
    public void encounterChanged(EncounterChange change) {
        LOGGER.info("tw_awareness_lifecycle encounter={} mob={} dimension={} action={}", change.encounterId(),
                change.mobId(), change.dimension(), change.transition());
        if (mob != null && mob.getUniqueID().equals(change.mobId())) {
            changes.add(change);
        }
    }

    @Override
    public void playerDied(UUID playerId) {
        LOGGER.info("tw_awareness_player_died uuid={}", playerId);
    }

    @SubscribeEvent
    public void lateCancel(LivingDamageEvent event) {
        if (cancelDamage && event.getEntityLiving() == mob && event.getSource().getTrueSource() == closer) {
            event.setCanceled(true);
            damageCanceled = true;
        }
    }

    @SubscribeEvent
    public void arrangeReinforcement(SummonAidEvent event) {
        if (event.getEntity() != reinforcementCaller) {
            return;
        }
        long seed = 23;
        Random positions = new Random(seed);
        reinforcementPosition = new BlockPos(event.getX() + offset(positions), event.getY() + offset(positions),
                event.getZ() + offset(positions));
        WorldServer world = (WorldServer) reinforcementCaller.world;
        world.setBlockState(reinforcementPosition.down(), Blocks.STONE.getDefaultState());
        world.setBlockToAir(reinforcementPosition);
        world.setBlockToAir(reinforcementPosition.up());
        reinforcementCaller.getRNG().setSeed(seed);
        event.setResult(Event.Result.ALLOW);
        event.setCustomSummonedAid(reinforcement);
    }

    private static int offset(Random random) {
        return MathHelper.getInt(random, 7, 40) * MathHelper.getInt(random, -1, 1);
    }

    @SubscribeEvent
    public void targetAssigned(LivingSetAttackTargetEvent event) {
        if (event.getEntityLiving() != mob || !measuring) {
            return;
        }
        if (phase == Phase.CLEARED && event.getTarget() == null) {
            focusClears++;
        }
        if (event.getTarget() == closer && event.getTarget() != pendingTaunter && closerDecision() != Decision.ALLOW) {
            forbiddenAssignments++;
        }
        if (event.getTarget() == farther && writeChange != null) {
            WriteChange change = writeChange;
            writeChange = null;
            if (change == WriteChange.BLOCK_FARTHER) {
                fartherBlocked = true;
                changed(farther);
            } else {
                closer.capabilities.disableDamage = true;
            }
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (mob == null || event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            ticks++;
            require(ticks <= limit, "timed out in " + phase);
            require(forbiddenAssignments == 0, "native AI assigned the excluded player");
            switch (phase) {
                case ENTER_BOW:
                    if (mob.ticksExisted == 0 || changes.isEmpty()) {
                        break;
                    }
                    require(mob.getAttackTarget() != closer, "entering bow combat kept an excluded focus");
                    require(changes.size() == 1 && changes.get(0).transition() == Transition.STARTED
                            && view(closer).state() == ThreatQueries.State.TRACKED,
                            "bow entry lost the encounter started in sword combat");
                    EntityLiving swordProbe = mob;
                    swordProbe.setDead();
                    EntitySkeleton bowFixture = new EntitySkeleton(swordProbe.world);
                    bowFixture.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                    spawnMob(bowFixture, swordProbe.posX, swordProbe.posY, swordProbe.posZ);
                    changes.clear();
                    fartherAware = true;
                    changed(farther);
                    enter(Phase.NATIVE, 90);
                    break;
                case NATIVE:
                    openAndClear();
                    break;
                case REVENGE:
                    clearWhenRevengeRuns();
                    break;
                case CLEARED:
                    vanishAfterClear();
                    break;
                case DRAW:
                    if (mob.isHandActive() && mob.tasks.taskEntries.stream()
                            .anyMatch(entry -> entry.using && entry.action instanceof EntityAIAttackRangedBow)) {
                        LOGGER.info("tw_awareness_bow_draw target={} hand={}", closer.getUniqueID(),
                                mob.getActiveHand());
                        startVanish();
                    }
                    break;
                case RELEASE:
                    release();
                    break;
                case OVERLAP:
                    holdExclusion();
                    if (ticks == limit) {
                        if (mob instanceof EntitySkeleton) {
                            require(closer.getHealth() < healthBeforeArrow, "in-flight arrow never reached vanished player");
                            LOGGER.info("tw_awareness_committed_arrow before={} health={}", healthBeforeArrow, closer.getHealth());
                        }
                        closerProtection = 0;
                        changed(closer);
                        enter(Phase.EXPIRED, 15);
                    }
                    break;
                case EXPIRED:
                    holdExclusion();
                    if (ticks == limit) {
                        closerAware = true;
                        changed(closer);
                        enter(Phase.REDISCOVERED, 65);
                    }
                    break;
                case REDISCOVERED:
                    if (mob.getAttackTarget() == closer) {
                        require(score(closer) == retained, "rediscovery altered retained score");
                        double alternative = score(farther);
                        writeChange = WriteChange.INVALIDATE_CLOSER;
                        require(taunt(farther) == Result.APPLIED && closer.capabilities.disableDamage && tauntReveals == 3,
                                "eligibility change prevented a valid taunt");
                        require(score(farther) == alternative, "taunt matched a score excluded during assignment");
                        require(view(closer).eligibility() != ThreatQueries.Confirmation.YES,
                                "taunt published stale eligibility");
                        closer.capabilities.disableDamage = false;
                        closerAware = fartherAware = false;
                        changed(closer);
                        changed(farther);
                        reserveAware = true;
                        changed(reserve);
                        enter(Phase.FALLBACK, 120);
                    }
                    break;
                case FALLBACK:
                    if (mob.getAttackTarget() == reserve) {
                        require(view(reserve).state() == ThreatQueries.State.TRACKED && score(reserve) == 0,
                                "native fallback required a score or ended the continuing encounter");
                        LOGGER.info("tw_awareness_native_fallback scenario={} score={}", scenario, score(reserve));
                        reserveAware = false;
                        cleared(reserve);
                        enter(Phase.RESET, 260);
                    }
                    break;
                case RESET:
                    reopen();
                    break;
                case IMPACT:
                    observeOpeningArrow();
                    break;
                case REOPENED:
                    finish();
                    break;
                default:
                    throw new AssertionError(phase);
            }
        } catch (IllegalStateException failure) {
            LOGGER.error("tw_awareness_result scenario={} status=FAIL phase={} detail={}", scenario, phase,
                    failure.getMessage());
            cleanup();
        }
    }

    private void openAndClear() {
        if (mob.getAttackTarget() != farther) {
            return;
        }
        require(decisions > 0, "native selection never consulted the provider");
        require(view(closer).state() == ThreatQueries.State.INACTIVE, "opened before qualifying action");
        EntityPlayerMP unloaded = CoverageFixtureCommand.fixturePlayer((WorldServer) mob.world, scenario, "Unloaded");
        mob.setAttackTarget(unloaded);
        require(mob.getAttackTarget() == farther, "native write accepted an unloaded player");
        if (mob instanceof EntityZombie) {
            checkZombieAid();
        }
        absorptionControls();
        hit();
        require(damageReveals == 2 && closerAware && score(closer) > 0, "damage did not reveal and score");
        require(score(farther) > 0, "qualified opening incumbent did not seed");
        require(mob.getAttackTarget() == farther, "opening damage displaced the native incumbent");
        require(ThreatworksMod.threatActions().grant(mob, closer, 80) == Result.APPLIED, "grant failed");
        require(taunt(closer) == Result.APPLIED, "valid taunt failed");
        require(mob.getAttackTarget() == closer && tauntReveals == 1, "taunt did not commit and reveal");
        double fartherBefore = score(farther);
        writeChange = WriteChange.BLOCK_FARTHER;
        require(taunt(farther) == Result.TARGET_ASSIGNMENT_REJECTED && fartherBlocked,
                "mid-write hard block did not reject");
        require(mob.getAttackTarget() == closer && view(closer).tauntForcingFocus() == ThreatQueries.Confirmation.YES
                && score(farther) == fartherBefore && tauntReveals == 1, "rejected write altered prior taunt or reveal");
        fartherBlocked = false;
        changed(farther);
        enter(Phase.REVENGE, 15);
    }

    private void checkZombieAid() {
        WorldServer world = (WorldServer) mob.world;
        EntityZombie caller = new EntityZombie(world);
        caller.setPosition(mob.posX, mob.posY, mob.posZ + 2);
        require(world.spawnEntity(caller), "could not spawn the zombie calling for help");
        try {
            caller.setRevengeTarget(closer);
            EntityAIHurtByTarget revenge = (EntityAIHurtByTarget) caller.targetTasks.taskEntries.stream()
                    .filter(entry -> entry.action instanceof EntityAIHurtByTarget).findFirst().get().action;
            mob.setAttackTarget(null);
            revenge.startExecuting();
            require(caller.getAttackTarget() == closer && mob.getAttackTarget() == null,
                    "receiving zombie accepted an unseen ally target");
            closerAware = true;
            changed(closer);
            revenge.startExecuting();
            require(mob.getAttackTarget() == closer, "ally targeting positive control did not assign the player");
            closerAware = false;
            changed(closer);
            mob.setAttackTarget(farther);

            reinforcementCaller = caller;
            reinforcement = new EntityZombie(world);
            require(caller.attackEntityFrom(DamageSource.causePlayerDamage(closer), 2)
                    && world.getEntityFromUuid(reinforcement.getUniqueID()) == reinforcement
                    && reinforcement.getAttackTarget() == null,
                    "native reinforcement failed to spawn without inheriting its caller's unseen target");
            LOGGER.info("tw_awareness_zombie_aid reinforcement={} position={}", reinforcement.getUniqueID(), reinforcementPosition);
        } finally {
            caller.setDead();
            reinforcementCaller = null;
            if (reinforcement != null) {
                reinforcement.setDead();
                reinforcement = null;
            }
        }
    }

    private boolean revengeRunning() {
        return mob.targetTasks.taskEntries.stream().anyMatch(entry -> entry.using && entry.action instanceof EntityAIHurtByTarget);
    }

    private void clearWhenRevengeRuns() {
        if (!revengeRunning()) {
            return;
        }
        require(mob.getAttackTarget() == closer, "running revenge did not hold the taunter");
        retained = score(closer);
        closerAware = false;
        cleared(closer);
        closerAware = true;
        changed(closer);
        require(view(closer).tauntActive() == ThreatQueries.Confirmation.NO && score(closer) == retained,
                "same-tick rediscovery kept the old taunt or altered its score");
        enter(Phase.CLEARED, 10);
    }

    private void vanishAfterClear() {
        require(focusClears > 0, "same-tick rediscovery kept the old target assignment");
        require(!revengeRunning(), "same-tick rediscovery retained the old cached revenge goal");
        require(taunt(closer) == Result.APPLIED && tauntReveals == 2, "rediscovered player could not taunt again");
        if (mob instanceof EntitySkeleton) {
            enter(Phase.DRAW, 54);
        } else {
            startVanish();
        }
    }

    private void startVanish() {
        if (mob instanceof EntitySkeleton) {
            require(mob.getAttackTarget() == closer, "bow draw lost the taunt before vanish");
            closer.hurtResistantTime = 0;
            healthBeforeArrow = closer.getHealth();
            ((EntitySkeleton) mob).attackEntityWithRangedAttack(closer, 1.0F);
            require(!mob.world.getEntities(EntityArrow.class, arrow -> arrow.shootingEntity == mob).isEmpty(),
                    "skeleton did not launch an arrow before vanish");
        }
        closerAware = false;
        closerProtection = VANISH | OTHER;
        cleared(closer);
        require(view(closer).tauntActive() == ThreatQueries.Confirmation.NO, "vanish left forced focus");
        require(taunt(closer) == Result.INELIGIBLE_TAUNTER,
                "active protection allowed taunt");
        require(closerProtection == (VANISH | OTHER) && tauntReveals == 2, "taunt rejection altered protection");
        LOGGER.info("tw_awareness_open scenario={} damageReveals={} score={} alternativeScore={}",
                scenario, damageReveals, retained, score(farther));
        enter(Phase.RELEASE, 20);
    }

    private void release() {
        require(mob.getAttackTarget() != closer, "focus persisted through the next AI tick");
        if (mob.getAttackTarget() == farther) {
            holdExclusion();
            closerProtection = OTHER;
            changed(closer);
            enter(Phase.OVERLAP, 14);
        }
    }

    private void holdExclusion() {
        require(mob.getAttackTarget() == farther && score(closer) == retained, "alternative or score changed");
        int reads = decisions;
        for (int i = 0; i < 128; i++) {
            view(closer);
        }
        require(decisions == reads, "HUD reads called the provider");
    }

    private void reopen() {
        if (changes.size() < 2) {
            return;
        }
        require(changes.get(0).transition() == Transition.STARTED && changes.get(1).transition() == Transition.RESET,
                "tracked lifetime did not end with a reset");
        require(changes.get(0).encounterId() == changes.get(1).encounterId(), "reset identity differs");
        require(mob.getAttackTarget() == null, "native target returned while both players stayed unaware");
        closerAware = true;
        changed(closer);
        EntityTippedArrow arrow = new EntityTippedArrow(mob.world, closer);
        arrow.setPosition(closer.posX, mob.posY + mob.height / 2, closer.posZ);
        arrow.shoot(mob.posX - arrow.posX, 0, mob.posZ - arrow.posZ, 1.8F, 0.0F);
        mobHealthBeforeArrow = mob.getHealth();
        mob.hurtResistantTime = 0;
        require(mob.world.spawnEntity(arrow), "could not launch the player arrow before vanish");
        closerAware = false;
        closerProtection = VANISH | OTHER;
        cleared(closer);
        enter(Phase.IMPACT, 25);
    }

    private void observeOpeningArrow() {
        if (mob.getHealth() >= mobHealthBeforeArrow) {
            return;
        }
        recordImpact();
        require(damageReveals == 3 && closerRemembered && !closerAware && closerProtection == OTHER,
                "fixture impact bypassed surviving protection");
        require(score(closer) > 0 && score(closer) < retained, "new lifetime inherited the prior score");
        LOGGER.info("tw_awareness_late_arrow scenario={} before={} health={} score={}", scenario,
                mobHealthBeforeArrow, mob.getHealth(), score(closer));
        require(taunt(farther) == Result.APPLIED, "unaware player could not taunt");
        require(fartherAware && tauntReveals == 4 && mob.getAttackTarget() == farther,
                "taunt did not reveal the unaware player");
        enter(Phase.REOPENED, 6);
    }

    private void finish() {
        if (changes.size() < 3) {
            return;
        }
        require(changes.size() == 3 && changes.get(2).transition() == Transition.STARTED
                && changes.get(2).encounterId() != changes.get(0).encounterId(), "reopening reused identity");
        closerProtection = 0;
        closerAware = fartherAware = false;
        changed(closer);
        changed(farther);
        failNextReveal = true;
        require(taunt(closer) == Result.APPLIED
                && view(closer).tauntForcingFocus() == ThreatQueries.Confirmation.YES,
                "failed callback removed the applied taunt");
        require(!ThreatworksMod.targetAwarenessChanged(mob, closer) && !closerAware,
                "failed reveal continued to claim coverage for that pair");
        require(!ThreatworksMod.targetAwarenessCleared(mob, closer) && mob.getAttackTarget() == closer
                && view(closer).tauntForcingFocus() == ThreatQueries.Confirmation.YES,
                "a clear changed a pair already using ordinary fallback");
        require(ThreatworksMod.targetAwarenessChanged(mob, farther) && !fartherAware,
                "failed reveal disabled a different pair");
        replaceNearestPlayerGoal();
        require(!ThreatworksMod.targetAwarenessChanged(mob, farther), "replacement target goal qualified");
        LOGGER.info("tw_awareness_result scenario={} status=PASS decisions={} damage={} taunt={} transitions={}",
                scenario, decisions, damageReveals, tauntReveals, changes.size());
        cleanup();
    }

    private void replaceNearestPlayerGoal() {
        EntityAITaskEntry playerGoal = mob.targetTasks.taskEntries.stream().filter(goal -> goal.priority == 2)
                .findFirst().orElseThrow(() -> new AssertionError("Missing stock player goal"));
        mob.targetTasks.removeTask(playerGoal.action);
        mob.targetTasks.addTask(2, new EntityAINearestAttackableTarget<>((EntityCreature) mob, EntityPlayer.class, true));
    }

    private Result taunt(EntityPlayerMP player) {
        pendingTaunter = player;
        try {
            return ThreatworksMod.threatActions().taunt(mob, player);
        } finally {
            pendingTaunter = null;
        }
    }

    private void hit() {
        float before = mob.getHealth();
        mob.hurtResistantTime = 0;
        require(mob.attackEntityFrom(DamageSource.causePlayerDamage(closer), 2) && mob.getHealth() < before,
                "fixture hit failed to remove health");
        recordImpact();
    }

    private void absorptionControls() {
        absorbedHit(true);
        require(!closerAware && !closerRemembered && damageReveals == 0,
                "late cancellation revealed the attacker");
        absorbedHit(false);
        require(closerAware && closerRemembered && damageReveals == 1,
                "uncanceled absorption failed to reach the fixture-owned observer");
        closerAware = closerRemembered = false;
        cleared(closer);
        mob.setAbsorptionAmount(0);
    }

    private void absorbedHit(boolean canceled) {
        float health = mob.getHealth();
        float absorption = 10;
        mob.setAbsorptionAmount(absorption);
        mob.hurtResistantTime = 0;
        damageCanceled = false;
        cancelDamage = canceled;
        try {
            mob.attackEntityFrom(DamageSource.causePlayerDamage(closer), 2);
        } finally {
            cancelDamage = false;
        }
        require(damageCanceled == canceled && mob.getHealth() == health && mob.getAbsorptionAmount() < absorption,
                "absorption control did not spend absorption with expected cancellation");
        require(view(closer).state() == ThreatQueries.State.INACTIVE && tauntReveals == 0,
                "absorbed hit opened tracking or notified a taunt");
        if (!damageCanceled) {
            recordImpact();
        }
    }

    private void recordImpact() {
        damageReveals++;
        closerRemembered = true;
        closerProtection &= ~VANISH;
        closerAware = closerProtection == 0;
        changed(closer);
    }

    private void changed(EntityPlayerMP player) {
        require(ThreatworksMod.targetAwarenessChanged(mob, player), "stock pair was outside awareness scope");
    }

    private void cleared(EntityPlayerMP player) {
        require(ThreatworksMod.targetAwarenessCleared(mob, player), "stock pair could not clear awareness");
    }

    private ThreatQueries.View view(EntityPlayerMP player) {
        return ThreatworksMod.threatQueries().query(mob, player);
    }

    private double score(EntityPlayerMP player) {
        return view(player).threat().orElse(Double.NaN);
    }

    private void enter(Phase next, int timeout) {
        phase = next;
        ticks = 0;
        limit = timeout;
    }

    private void cleanup() {
        if (mob != null) {
            mob.setDead();
            mob = null;
        }
        closer.setDead();
        farther.setDead();
        reserve.setDead();
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalStateException(message);
        }
    }
}
