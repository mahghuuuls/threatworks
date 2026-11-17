package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.ThreatworksMod;
import com.mahghuuuls.threatworks.api.ThreatActions.Result;
import com.mahghuuuls.threatworks.api.ThreatQueries.Confirmation;
import com.mahghuuuls.threatworks.api.ThreatQueries.State;
import com.mahghuuuls.threatworks.api.ThreatQueries.Takeover;
import com.mahghuuuls.threatworks.api.ThreatQueries.View;
import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import com.mahghuuuls.threatworks.server.EncounterCoordinator.Inspection;
import com.sun.management.ThreadMXBean;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Operator-only fixed load, usable with the published baseline and candidate. */
public final class HudWorkloadCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");
    private static final int MOBS = 16;
    private static final int VIEWERS = 8;
    private static final int PAIRS = MOBS * VIEWERS;
    private static final int WINDOW = 600;
    private static final int WARMUP = 100;
    private static final String MOB_TAG = "tw_h2_mob";
    private static final String PLAYER_TAG = "tw_h2_viewer";
    private static Run active;

    @Override
    public String getName() { return "twhud"; }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public String getUsage(ICommandSender sender) { return "/twhud <baseline|candidate|smoke|overload|transitions>"; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!server.isDedicatedServer() || active != null || args.length != 1) {
            throw new WrongUsageException(getUsage(sender));
        }
        String scenario = args[0];
        if (scenario.equals("transitions")) {
            TakeoverTransitionProbe.start(server.getWorld(0));
            return;
        }
        if (!scenario.equals("baseline") && !scenario.equals("candidate") && !scenario.equals("smoke")
                && !scenario.equals("overload")) {
            throw new WrongUsageException(getUsage(sender));
        }
        Run run = new Run(server.getWorld(0), scenario);
        try {
            run.seed();
            active = run;
            MinecraftForge.EVENT_BUS.register(run);
        } catch (CommandException | RuntimeException e) {
            run.stop();
            throw e;
        }
    }

    private enum Mode { NONE, ALIGNED, STAGGERED }

    public static final class Run {
        private final WorldServer world;
        private final String scenario;
        private final Mode[] sequence;
        private final int duration;
        private final EntityLiving[] mobs = new EntityLiving[MOBS];
        private final EntityPlayerMP[] viewers = new EntityPlayerMP[VIEWERS];
        private final Vec3d[] mobPositions = new Vec3d[MOBS];
        private final Vec3d[] viewerPositions = new Vec3d[VIEWERS];
        private final double[][] scores = new double[MOBS][VIEWERS];
        private final int[] coverage = new int[PAIRS];
        private final long[] ticks = new long[WINDOW];
        private final long[] batches = new long[WINDOW];
        private final long[] controls = new long[WINDOW];
        private final long[] allocations = new long[WINDOW];
        private final ThreadMXBean allocationMeter;
        private int started;
        private int window;
        private int elapsed;
        private int batchCount;
        private int queries;
        private int numeric;
        private int missing;
        private int maxAge;
        private int minBatch;
        private int maxBatch;
        private int sink;
        private long tickStart;
        private Reader reader;

        private Run(WorldServer world, String scenario) {
            this.world = world;
            this.scenario = scenario;
            duration = scenario.equals("smoke") ? 75 : WINDOW;
            sequence = scenario.equals("baseline") ? new Mode[]{Mode.NONE, Mode.NONE, Mode.NONE}
                    : scenario.equals("smoke") || scenario.equals("overload") ? new Mode[]{Mode.ALIGNED}
                    : new Mode[]{Mode.NONE, Mode.STAGGERED, Mode.ALIGNED, Mode.ALIGNED, Mode.NONE,
                    Mode.STAGGERED, Mode.STAGGERED, Mode.ALIGNED, Mode.NONE};
            java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            allocationMeter = bean instanceof ThreadMXBean && ((ThreadMXBean) bean).isThreadAllocatedMemoryEnabled()
                    ? (ThreadMXBean) bean : null;
        }

        private void seed() throws CommandException {
            world.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
            world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
            world.getWorldInfo().setDifficulty(EnumDifficulty.HARD);
            world.setWorldTime(18000);
            BlockPos spawn = world.getSpawnPoint();
            int x = spawn.getX();
            int y = Math.max(4, world.getHeight(spawn).getY());
            int z = spawn.getZ();
            for (int dx = -3; dx <= 14; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    world.setBlockState(new BlockPos(x + dx, y - 1, z + dz), Blocks.STONE.getDefaultState(), 2);
                }
            }
            for (int index = 0; index < VIEWERS; index++) {
                double angle = 2 * Math.PI * index / VIEWERS;
                EntityPlayerMP player = CoverageFixtureCommand.fixturePlayer(world, "", "HudView" + index);
                player.capabilities.disableDamage = false;
                player.noClip = true;
                player.setNoGravity(true);
                player.addTag(PLAYER_TAG);
                player.setPosition(x + 0.5 + 0.85 * Math.cos(angle), y, z + 0.5 + 0.85 * Math.sin(angle));
                require(world.spawnEntity(player), "Could not load HUD viewer");
                viewers[index] = player;
                viewerPositions[index] = player.getPositionVector();
            }
            for (int index = 0; index < MOBS; index++) {
                boolean bow = index >= MOBS / 2;
                EntityLiving mob = bow ? new EntitySkeleton(world) : new EntityZombie(world);
                if (scenario.equals("overload")) mob.setEntityId(2000000 + index * 5);
                if (bow) {
                    mob.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.BOW));
                } else {
                    ((EntityZombie) mob).setChild(false);
                }
                mob.addTag(MOB_TAG);
                mob.enablePersistence();
                mob.noClip = true;
                mob.setNoGravity(true);
                mob.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0);
                mob.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
                mob.setPosition(x + (bow ? 9.5 : 0.5), y, z + 0.5);
                require(world.spawnEntity(mob), "Could not load HUD mob");
                mobs[index] = mob;
                mobPositions[index] = mob.getPositionVector();
                mob.setAttackTarget(viewers[0]);
                float before = mob.getHealth();
                mob.attackEntityFrom(DamageSource.causePlayerDamage(viewers[0]), 2);
                require(mob.getHealth() < before, "HUD load did not open through applied damage");
                require(ThreatworksMod.threatActions().grant(mob, viewers[0], 100) == Result.APPLIED,
                        "Could not raise incumbent score");
                for (int view = 1; view < VIEWERS; view++) {
                    require(ThreatworksMod.threatActions().grant(mob, viewers[view], 10 + view) == Result.APPLIED,
                            "Could not score challenger");
                }
                Inspection setup = EncounterCoordinator.INSTANCE.inspect(mob, VIEWERS);
                for (int view = 0; view < VIEWERS; view++) {
                    EntityPlayerMP player = viewers[view];
                    scores[index][view] = setup.participants.stream().filter(entry -> player.getUniqueID().equals(entry.id))
                            .mapToDouble(entry -> entry.score).findFirst().orElseThrow(IllegalStateException::new);
                }
            }
            if (!scenario.equals("baseline")) reader = new Reader();
            started = world.getMinecraftServer().getTickCounter();
            LOGGER.info("h2_workload_started scenario={} mobs={} viewers={} window={} tick={}", scenario, MOBS,
                    VIEWERS, duration, started);
        }

        @SubscribeEvent
        public void attack(LivingAttackEvent event) {
            if (event.getEntity().world == world && event.getEntity().getTags().contains(PLAYER_TAG)) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public void projectile(EntityJoinWorldEvent event) {
            if (event.getWorld() == world && event.getEntity() instanceof EntityArrow) {
                EntityArrow arrow = (EntityArrow) event.getEntity();
                if (arrow.shootingEntity != null && arrow.shootingEntity.getTags().contains(MOB_TAG)) event.setCanceled(true);
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void holdPositions(TickEvent.WorldTickEvent event) {
            if (event.world == world && event.phase == TickEvent.Phase.END) {
                for (int i = 0; i < mobs.length; i++) hold(mobs[i], mobPositions[i]);
                for (int i = 0; i < viewers.length; i++) hold(viewers[i], viewerPositions[i]);
            }
        }

        private static void hold(net.minecraft.entity.Entity entity, Vec3d position) {
            entity.setPosition(position.x, position.y, position.z);
            entity.motionX = entity.motionY = entity.motionZ = 0;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void tick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                tickStart = System.nanoTime();
                return;
            }
            int tick = world.getMinecraftServer().getTickCounter();
            try {
                if (tick - started < WARMUP) {
                    if (reader != null && (tick - started) % 5 == 0) reader.poll(mobs, viewers, 0, 1);
                    if (tick - started == WARMUP - 1) begin();
                    return;
                }
                measure(tick);
                ticks[elapsed] = System.nanoTime() - tickStart;
                if (++elapsed == duration) finish();
            } catch (RuntimeException | CommandException e) {
                LOGGER.error("h2_workload_failed", e);
                stop();
            }
        }

        private void begin() throws CommandException {
            batchCount = queries = numeric = missing = maxAge = maxBatch = 0;
            minBatch = Integer.MAX_VALUE;
            Arrays.fill(coverage, 0);
            verifyGameplay();
            require(EncounterCoordinator.INSTANCE.beginWorkSample(world, duration), "Cannot start workload counters");
        }

        private void measure(int tick) throws CommandException {
            for (EntityLiving mob : mobs) require(mob.isEntityAlive() && mob.getAttackTarget() == viewers[0], "Native target changed");
            Mode mode = sequence[window];
            int phase = elapsed % 5;
            if (mode != Mode.STAGGERED && phase != 0) return;
            int first = mode == Mode.STAGGERED ? phase : 0;
            int stride = mode == Mode.STAGGERED ? 5 : 1;
            long initial = System.nanoTime();
            for (int pair = first; pair < PAIRS; pair += stride) sink ^= mobs[pair / VIEWERS].getAttackTarget().getEntityId();
            controls[batchCount] = System.nanoTime() - initial;
            if (mode != Mode.NONE) {
                long beforeAlloc = allocated();
                long before = System.nanoTime();
                int count = reader.poll(mobs, viewers, first, stride);
                batches[batchCount] = System.nanoTime() - before;
                long afterAlloc = allocated();
                allocations[batchCount] = beforeAlloc < 0 ? -1 : afterAlloc - beforeAlloc;
                queries += count;
                minBatch = Math.min(minBatch, count);
                maxBatch = Math.max(maxBatch, count);
                inspectReads(tick, first, stride);
            }
            batchCount++;
        }

        private long allocated() {
            return allocationMeter == null ? -1 : allocationMeter.getThreadAllocatedBytes(Thread.currentThread().getId());
        }

        private void inspectReads(int tick, int first, int stride) throws CommandException {
            for (int pair = first; pair < PAIRS; pair += stride) {
                int view = pair % VIEWERS;
                View result = reader.values[pair];
                double expected = scores[pair / VIEWERS][view];
                require(result.state() == State.TRACKED && result.threat().orElse(-1) == expected
                        && result.aggro() == (view == 0 ? Confirmation.YES : Confirmation.NO)
                        && result.tauntActive() == Confirmation.NO, "Read disagreed with independently observed combat");
                Takeover takeover = result.takeover().orElse(null);
                if (view == 0) {
                    require(takeover == null, "Incumbent received takeover score");
                } else if (takeover == null) {
                    missing++;
                } else {
                    int age = tick - takeover.observationTick();
                    require(takeover.observedThreat() == expected && takeover.score() > expected
                            && !takeover.equalitySuffices() && age >= 0 && age <= 25, "Invalid stable takeover score");
                    maxAge = Math.max(maxAge, age);
                    numeric++;
                    coverage[pair]++;
                }
            }
        }

        private void verifyGameplay() throws CommandException {
            for (int index = 0; index < MOBS; index++) {
                EntityLiving mob = mobs[index];
                Inspection state = EncounterCoordinator.INSTANCE.inspect(mob, VIEWERS);
                require(state.tracked && state.participants.size() == VIEWERS && mob.getAttackTarget() == viewers[0],
                        "Expected one settled ordinary fight per HUD mob");
                for (int view = 0; view < VIEWERS; view++) {
                    EntityPlayerMP player = viewers[view];
                    double actual = state.participants.stream().filter(entry -> player.getUniqueID().equals(entry.id))
                            .mapToDouble(entry -> entry.score).findFirst().orElse(-1);
                    require(actual == scores[index][view], "HUD load changed an accumulated score");
                }
            }
        }

        private void finish() throws CommandException {
            verifyGameplay();
            String work = EncounterCoordinator.INSTANCE.takeWorkSample(world);
            require(work != null && work.contains("activeMin=16 activeMax=16"), "HUD workload lost a tracked mob");
            Mode mode = sequence[window];
            int minCoverage = Integer.MAX_VALUE;
            int meleeReads = 0;
            int bowReads = 0;
            for (int pair = 0; pair < PAIRS; pair++) {
                if (pair % VIEWERS != 0) minCoverage = Math.min(minCoverage, coverage[pair]);
                if (pair < PAIRS / 2) meleeReads += coverage[pair];
                else bowReads += coverage[pair];
            }
            LOGGER.info("h2_window scenario={} window={} mode={} ticks={} batches={} queries={} minBatch={} maxBatch={} "
                            + "numeric={} missing={} minCoverage={} maxAge={} p50Ns={} p95Ns={} controlP95Ns={} allocP95Bytes={} "
                            + "tickP50Ns={} tickP95Ns={} meleeNumeric={} bowNumeric={} work={} sink={}", scenario, window, mode, duration, batchCount, queries,
                    queries == 0 ? 0 : minBatch, maxBatch, numeric, missing, minCoverage, maxAge,
                    queries > 0 ? percentile(batches, batchCount, 0.50) : 0,
                    queries > 0 ? percentile(batches, batchCount, 0.95) : 0, percentile(controls, batchCount, 0.95),
                    queries > 0 ? percentile(allocations, batchCount, 0.95) : -1, percentile(ticks, duration, 0.50),
                    percentile(ticks, duration, 0.95), meleeReads, bowReads, work, sink);
            if (mode != Mode.NONE) {
                require(scenario.equals("overload") ? minCoverage > 0 : minCoverage >= duration / 5 - 1 && missing == 0,
                        "Numeric takeover coverage is missing for a stable designated pair");
            }
            if (++window == sequence.length) {
                LOGGER.info("h2_workload_passed scenario={}", scenario);
                stop();
            } else {
                elapsed = 0;
                begin();
            }
        }

        private void stop() {
            MinecraftForge.EVENT_BUS.unregister(this);
            active = null;
            for (EntityLiving mob : mobs) if (mob != null) mob.setDead();
            for (EntityPlayerMP player : viewers) if (player != null) world.removeEntityDangerously(player);
        }

        private static long percentile(long[] values, int size, double fraction) {
            long[] sorted = Arrays.copyOf(values, size);
            Arrays.sort(sorted);
            return sorted[Math.max(0, (int) Math.ceil(size * fraction) - 1)];
        }
    }

    private static final class Reader {
        private final ParticipantQueryConsumer consumer = new ParticipantQueryConsumer();
        private final View[] values = new View[PAIRS];

        int poll(EntityLiving[] mobs, EntityPlayerMP[] viewers, int first, int stride) {
            int reads = 0;
            for (int pair = first; pair < PAIRS; pair += stride) {
                values[pair] = consumer.read(mobs[pair / VIEWERS], viewers[pair % VIEWERS]);
                reads++;
            }
            return reads;
        }
    }

    private static void require(boolean condition, String detail) throws CommandException {
        if (!condition) throw new CommandException(detail);
    }
}
