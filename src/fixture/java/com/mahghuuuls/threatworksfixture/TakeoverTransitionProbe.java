package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.ThreatworksMod;
import com.mahghuuuls.threatworks.api.ThreatActions.Result;
import com.mahghuuuls.threatworks.api.ThreatQueries.Confirmation;
import com.mahghuuuls.threatworks.api.ThreatQueries.State;
import com.mahghuuuls.threatworks.api.ThreatQueries.Takeover;
import com.mahghuuuls.threatworks.api.ThreatQueries.View;
import net.minecraft.command.CommandException;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Exercises ordinary pursuit, movement and score invalidation with real server entities. */
public final class TakeoverTransitionProbe {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");
    private final ParticipantQueryConsumer consumer = new ParticipantQueryConsumer();
    private final WorldServer world;
    private final BlockPos origin;
    private final EntityZombie mob;
    private EntityPlayerMP incumbent;
    private EntityPlayerMP challenger;
    private EntityPlayerMP rival;
    private double incumbentScore;
    private double challengerScore;
    private int movedAt;
    private int switchedAt;
    private int started;

    private TakeoverTransitionProbe(WorldServer world) {
        this.world = world;
        origin = world.getHeight(world.getSpawnPoint());
        mob = new EntityZombie(world);
    }

    static void start(WorldServer world) throws CommandException {
        TakeoverTransitionProbe fixture = new TakeoverTransitionProbe(world);
        try {
            fixture.seed();
            MinecraftForge.EVENT_BUS.register(fixture);
        } catch (CommandException | RuntimeException failure) {
            fixture.stop();
            throw failure;
        }
    }

    private void seed() throws CommandException {
        world.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
        world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
        world.getWorldInfo().setDifficulty(EnumDifficulty.HARD);
        world.setWorldTime(18000);
        for (int dx = -3; dx <= 17; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                world.setBlockState(origin.add(dx, -1, dz), Blocks.STONE.getDefaultState(), 2);
            }
        }
        incumbent = fixedPlayer("TakeoverOwner", 0.85, 0);
        challenger = fixedPlayer("TakeoverChallenger", 12, 0);
        rival = fixedPlayer("TakeoverRival", 0, -0.8);
        mob.setChild(false);
        mob.noClip = true;
        mob.setNoGravity(true);
        mob.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0);
        mob.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(0);
        mob.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        mob.setPosition(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        require(world.spawnEntity(mob), "Could not spawn takeover transition mob");
        mob.setAttackTarget(incumbent);
        float before = mob.getHealth();
        mob.attackEntityFrom(DamageSource.causePlayerDamage(incumbent), 2);
        require(mob.getHealth() < before && !consumer.read(mob, challenger).takeover().isPresent(),
                "Opening hold should have applied health loss and no takeover score");
        grant(incumbent, 100);
        incumbentScore = consumer.read(mob, incumbent).threat().getAsDouble();
        grant(challenger, incumbentScore * 1.25);
        challengerScore = consumer.read(mob, challenger).threat().getAsDouble();
        grant(rival, 10);
        started = world.getMinecraftServer().getTickCounter();
        LOGGER.info("h2_transitions_started tick={} healthBefore={} healthAfter={} incumbentThreat={} challengerThreat={}",
                started, before, mob.getHealth(), incumbentScore, challengerScore);
    }

    private EntityPlayerMP fixedPlayer(String name, double x, double z) throws CommandException {
        EntityPlayerMP player = ParticipantQueryFixture.fake(world, name, origin.getX() + 0.5 + x, origin.getY(),
                origin.getZ() + 0.5 + z);
        player.noClip = true;
        player.setNoGravity(true);
        return player;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        int tick = world.getMinecraftServer().getTickCounter();
        int elapsed = tick - started;
        try {
            if (movedAt != 0 && switchedAt == 0 && mob.getAttackTarget() == challenger) switchedAt = tick;
            switch (elapsed) {
                case 25: {
                    View far = consumer.read(mob, challenger);
                    Takeover boundary = far.takeover().orElseThrow(() -> new CommandException("Far pursuit boundary is absent"));
                    require(mob.getAttackTarget() == incumbent && near(boundary.score(), incumbentScore * 1.3D)
                            && boundary.observedThreat() == challengerScore && !boundary.equalitySuffices(),
                            "Pursuit threshold disagreed with the default chase lead at 12 blocks");
                    challenger.setPosition(origin.getX() + 0.5, origin.getY(), origin.getZ() + 1.4);
                    require(!consumer.read(mob, challenger).takeover().isPresent(), "Movement retained the old pursuit score");
                    movedAt = tick;
                    LOGGER.info("h2_pursuit boundary={} observedThreat={} distance=12 movementTick={} immediateAbsence=true",
                            boundary.score(), boundary.observedThreat(), movedAt);
                    break;
                }
                case 36:
                    require(switchedAt - movedAt > 0 && switchedAt - movedAt <= 5 && mob.getAttackTarget() == challenger
                            && consumer.read(mob, challenger).threat().getAsDouble() == challengerScore,
                            "Movement alone did not switch the ordinary target on cadence");
                    require(consumer.read(mob, incumbent).takeover().isPresent(), "New settled incumbent lacked a fresh sample");
                    grant(rival, 500);
                    rival.capabilities.disableDamage = true;
                    require(!consumer.read(mob, incumbent).takeover().isPresent(), "Rival score mutation retained the old sample");
                    LOGGER.info("h2_move switchedAfter={} sameThreat={} invalidatedOnRivalGrant=true", switchedAt - movedAt,
                            challengerScore);
                    break;
                case 45: {
                    View settled = consumer.read(mob, incumbent);
                    View invalid = consumer.read(mob, rival);
                    require(settled.takeover().isPresent() && near(settled.takeover().get().score(), challengerScore * 1.2D)
                            && invalid.eligibility() == Confirmation.NO && !invalid.takeover().isPresent()
                            && invalid.threat().getAsDouble() > challengerScore, "Ineligible higher score changed the boundary");
                    require(ThreatworksMod.threatActions().taunt(mob, incumbent) == Result.APPLIED
                            && mob.getAttackTarget() == incumbent, "Transition taunt was not applied by the native setter");
                    View during = consumer.read(mob, challenger);
                    require(during.tauntForcingFocus() == Confirmation.YES && !during.takeover().isPresent(),
                            "Active taunt kept a takeover score");
                    LOGGER.info("h2_rival ignoredScore={} immediateBoundary={} tauntTicks={} pausedTakeover=true",
                            invalid.threat().getAsDouble(), settled.takeover().get().score(), during.tauntTicks().orElse(0));
                    break;
                }
                case 113: {
                    View after = consumer.read(mob, challenger);
                    require(after.tauntActive() == Confirmation.NO && after.takeover().isPresent(),
                            "Ordinary takeover did not return after taunt expiry");
                    incumbent.capabilities.disableDamage = true;
                    challenger.capabilities.disableDamage = true;
                    require(!consumer.read(mob, challenger).takeover().isPresent(), "Ineligible challenger retained score boundary");
                    LOGGER.info("h2_taunt_expired boundary={} tick={}", after.takeover().get().score(), tick);
                    break;
                }
                case 365:
                    require(consumer.read(mob, challenger).state() == State.INACTIVE,
                            "Encounter did not reset after all participants became ineligible");
                    LOGGER.info("h2_transitions_passed reset=true tick={}", tick);
                    stop();
                    break;
                default:
                    break;
            }
        } catch (CommandException | RuntimeException failure) {
            LOGGER.error("h2_transitions_failed", failure);
            stop();
        }
    }

    private void grant(EntityPlayerMP player, double amount) throws CommandException {
        require(ThreatworksMod.threatActions().grant(mob, player, amount) == Result.APPLIED, "Threat grant failed");
    }

    private void stop() {
        MinecraftForge.EVENT_BUS.unregister(this);
        mob.setDead();
        if (incumbent != null) world.removeEntityDangerously(incumbent);
        if (challenger != null) world.removeEntityDangerously(challenger);
        if (rival != null) world.removeEntityDangerously(rival);
    }

    private static boolean near(double actual, double expected) { return Math.abs(actual - expected) < 1e-10; }

    private static void require(boolean condition, String detail) throws CommandException {
        if (!condition) throw new CommandException(detail);
    }
}
