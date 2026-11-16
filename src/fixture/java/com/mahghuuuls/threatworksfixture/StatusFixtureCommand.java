package com.mahghuuuls.threatworksfixture;

import com.mojang.authlib.GameProfile;
import com.mahghuuuls.statusworks.api.RegistrationResult;
import com.mahghuuuls.statusworks.api.StackChange;
import com.mahghuuuls.statusworks.api.StatusService;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.api.contribution.ContributionGroup;
import com.mahghuuuls.statusworks.api.contribution.ContributionType;
import com.mahghuuuls.statusworks.api.definition.Contribution;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.api.definition.ValueForm;
import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

final class StatusFixtureCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");
    private static final String POINTS = "threatworks:threat_points";
    private static final String JAVA = "tw_u:java";
    private static final String QUEUE = "tw_u:queue";
    private static final String QUEUED = "tw_u:queued_value";
    private static final UUID TARGET = UUID.fromString("00000000-0000-0001-0000-0000075bcd15");
    private static final int Y = 70;
    private static final int LANES = 16;

    private EntityPlayerMP actor;
    private EntityZombie mob;
    private boolean syntheticActor;
    private boolean awaitingCallback;
    private final EntityPlayerMP[] loadActors = new EntityPlayerMP[LANES];
    private final EntityZombie[] loadMobs = new EntityZombie[LANES];
    private int loadTicks;
    private int loadHits;
    private double loadHealthLost;

    void registerDefinitions() {
        require(StatusworksApi.registerStatus(definition(JAVA, POINTS, 50)));
        require(StatusworksApi.registerStatus(definition(QUEUED, POINTS, 25)));
        require(StatusworksApi.registerStatus(definition(QUEUE, QUEUE, 1)));
        require(StatusworksApi.registerContributionType(new ContributionType() {
            @Override
            public String id() {
                return QUEUE;
            }

            @Override
            public boolean appliesTo(EntityLivingBase entity) {
                return entity instanceof EntityPlayerMP;
            }

            @Override
            public void onValue(EntityLivingBase entity, String key, double value) {
                if (entity == actor && awaitingCallback) {
                    awaitingCallback = false;
                    StatusworksApi.statuses().apply(entity, QUEUED, 1);
                    report("pending");
                    hit("pending");
                }
            }

            @Override
            public void onEnd(EntityLivingBase entity, String key) {}
        }));
    }

    private static StatusDefinition definition(String id, String type, double points) {
        return StatusDefinition.builder(id).defaultDuration(2400)
                .contribute(Contribution.type(type, ValueForm.FIXED, points)).build();
    }

    private static void require(RegistrationResult result) {
        if (!result.isAccepted()) {
            throw new IllegalStateException(result.error());
        }
    }

    @Override
    public String getName() {
        return "twstatus";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/twstatus seed [player] | apply <id> <count> [ticks] | clear <id> | hit | state"
                + " | pending | stick | empty | arrow | load <id> | cleanup";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0 || !server.isDedicatedServer() || !server.getFolderName().startsWith("TW-Campaign-U")) {
            throw new WrongUsageException(getUsage(sender));
        }
        WorldServer world = server.getWorld(0);
        if ("seed".equals(args[0])) {
            cleanup();
            arena(world, 8, 8);
            syntheticActor = args.length != 2;
            actor = syntheticActor ? player(world, "StatusAttacker", 8, 8) : getPlayer(server, sender, args[1]);
            actor.interactionManager.setGameType(GameType.SURVIVAL);
            actor.connection.setPlayerLocation(8.5, Y, 8.5, 0.0F, 0.0F);
            mob = zombie(world, 8, 10);
            mob.setUniqueId(TARGET);
            if (!world.spawnEntity(mob)) {
                throw new CommandException("Could not add the status fixture target.");
            }
            report("seed");
            return;
        }
        if ("cleanup".equals(args[0])) {
            cleanup();
            return;
        }
        if ("load".equals(args[0]) && args.length == 2) {
            cleanup();
            for (int lane = 0; lane < LANES; ++lane) {
                int x = 8 + 8 * (lane % 4);
                int z = 8 + 8 * (lane / 4);
                arena(world, x, z);
                loadActors[lane] = player(world, "StatusLoad" + lane, x, z);
                loadMobs[lane] = zombie(world, x, z + 2);
                world.spawnEntity(loadMobs[lane]);
                apply(loadActors[lane], args[1], 1, 2400);
            }
            LOGGER.info("status_load started lanes={} id={}", LANES, args[1]);
            return;
        }
        if (actor == null || mob == null) {
            throw new WrongUsageException(getUsage(sender));
        }
        switch (args[0]) {
            case "apply":
                if (args.length < 3) {
                    throw new WrongUsageException(getUsage(sender));
                }
                apply(actor, args[1], parseInt(args[2], 1), args.length == 4 ? parseInt(args[3], 1) : 2400);
                break;
            case "clear":
                if (args.length != 2) {
                    throw new WrongUsageException(getUsage(sender));
                }
                LOGGER.info("status_clear id={} result={}", args[1], StatusworksApi.statuses().clear(actor, args[1]));
                break;
            case "stick":
                actor.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.STICK));
                break;
            case "empty":
                actor.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
                break;
            case "hit":
                hit("direct");
                return;
            case "pending":
                awaitingCallback = true;
                try {
                    apply(actor, QUEUE, 1, 2400);
                    if (awaitingCallback) {
                        throw new CommandException("Statusworks did not run the queued-change probe.");
                    }
                } finally {
                    awaitingCallback = false;
                }
                break;
            case "arrow":
                shoot();
                return;
            case "state":
                break;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
        report(args[0]);
    }

    private void shoot() {
        EntityArrow arrow = new EntityTippedArrow(actor.world, actor);
        arrow.shoot(actor, 0, 180, 0, 1, 0);
        arrow.setPosition(mob.posX + 5, mob.posY + 0.8, mob.posZ);
        arrow.setNoGravity(true);
        arrow.setIsCritical(false);
        arrow.setDamage(2);
        arrow.motionX = 0;
        arrow.motionY = 0;
        arrow.motionZ = 0;
        actor.world.spawnEntity(arrow);
        mob.hurtResistantTime = 0;
        LOGGER.info("status_arrow uuid={} shooter={} beforeHp={} beforeScore={}", arrow.getUniqueID(),
                actor.getUniqueID(), mob.getHealth(), score());
    }

    @SubscribeEvent
    public void arrowImpact(ProjectileImpactEvent.Arrow event) {
        if (mob != null && event.getRayTraceResult().entityHit == mob && event.getArrow().shootingEntity == actor) {
            LOGGER.info("status_arrow_impact uuid={} victim={}", event.getArrow().getUniqueID(), mob.getUniqueID());
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || loadMobs[0] == null || loadMobs[0].world != event.world
                || loadTicks >= 600) {
            return;
        }
        ++loadTicks;
        if (loadTicks % 10 == 0) {
            for (int lane = 0; lane < LANES; ++lane) {
                EntityZombie target = loadMobs[lane];
                target.setHealth(200.0F);
                target.hurtResistantTime = 0;
                target.attackEntityFrom(DamageSource.causePlayerDamage(loadActors[lane]), 4.0F);
                float lost = 200.0F - target.getHealth();
                if (lost > 0) {
                    ++loadHits;
                    loadHealthLost += lost;
                }
            }
        }
        if (loadTicks % 125 == 100 && loadTicks >= 225) {
            int tracked = 0;
            int groupEntries = 0;
            double scoreSum = 0;
            for (int lane = 0; lane < LANES; ++lane) {
                EncounterCoordinator.Inspection encounter = EncounterCoordinator.INSTANCE.inspect(loadMobs[lane], 8);
                if (encounter.tracked) {
                    ++tracked;
                }
                EntityPlayerMP attacker = loadActors[lane];
                groupEntries += StatusworksApi.contributions().group(attacker, POINTS).values().size();
                scoreSum += score(encounter, attacker);
            }
            LOGGER.info("status_load sample tick={} hits={} hpLost={} tracked={} entries={} scoreSum={}",
                    loadTicks, loadHits, loadHealthLost, tracked, groupEntries, scoreSum);
            MinecraftServer server = event.world.getMinecraftServer();
            server.getCommandManager().executeCommand(server, "forge tps");
        }
    }

    private void report(String label) {
        ContributionGroup group = StatusworksApi.contributions().group(actor, POINTS);
        EncounterCoordinator.Inspection encounter = EncounterCoordinator.INSTANCE.inspect(mob, 8);
        StatusService statuses = StatusworksApi.statuses();
        LOGGER.info("status_state phase={} actor={} mob={} hp={} tracked={} score={} group={} values={} "
                        + "fixed={} stacks={} negative={} java={} timed={}",
                label, actor.getUniqueID(), mob.getUniqueID(), mob.getHealth(), encounter.tracked,
                score(), group.availability(), group.values().values(), statuses.count(actor, "tw_u:fixed"),
                statuses.count(actor, "tw_u:stacked"), statuses.count(actor, "tw_u:negative"),
                statuses.count(actor, JAVA), statuses.count(actor, "tw_u:timed"));
    }

    private void hit(String kind) {
        float before = mob.getHealth();
        double prior = score();
        mob.hurtResistantTime = 0;
        boolean accepted = mob.attackEntityFrom(DamageSource.causePlayerDamage(actor), 4.0F);
        LOGGER.info("status_hit kind={} actor={} mob={} accepted={} beforeHp={} afterHp={} beforeScore={} afterScore={}",
                kind, actor.getUniqueID(), mob.getUniqueID(), accepted, before, mob.getHealth(), prior, score());
    }

    private double score() {
        return score(EncounterCoordinator.INSTANCE.inspect(mob, 8), actor);
    }

    private static double score(EncounterCoordinator.Inspection encounter, EntityPlayerMP attacker) {
        return encounter.participants.stream()
                .filter(entry -> attacker.getUniqueID().equals(entry.id))
                .mapToDouble(entry -> entry.score).findFirst().orElse(0);
    }

    private static void apply(EntityLivingBase entity, String id, int count, int duration) throws CommandException {
        StackChange result = StatusworksApi.statuses().apply(entity, id, count, null, duration);
        if (!result.succeeded()) {
            throw new CommandException("Statusworks could not apply " + id + ": " + result);
        }
        LOGGER.info("status_apply id={} actor={} count={} duration={} result={}",
                id, entity.getUniqueID(), count, duration, result);
    }

    private static void arena(WorldServer world, int x, int z) {
        for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(x - 2, Y - 1, z - 2), new BlockPos(x + 2, Y - 1, z + 4))) {
            world.setBlockState(pos, Blocks.STONE.getDefaultState(), 2);
        }
    }

    private static EntityZombie zombie(WorldServer world, int x, int z) {
        EntityZombie zombie = new EntityZombie(world);
        zombie.setPosition(x + 0.5, Y, z + 0.5);
        zombie.enablePersistence();
        zombie.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.0);
        zombie.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(0.0);
        zombie.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        zombie.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(200.0);
        zombie.setHealth(200.0F);
        return zombie;
    }

    private static EntityPlayerMP player(WorldServer world, String name, int x, int z) {
        MinecraftServer server = world.getMinecraftServer();
        // Statusworks excludes Forge FakePlayer objects. A server player entity with a local packet sink
        // exercises real status capabilities while the multiplayer case uses a network connection.
        EntityPlayerMP player = new EntityPlayerMP(server, world, new GameProfile(UUID.randomUUID(), name),
                new PlayerInteractionManager(world));
        player.connection = new NetHandlerPlayServer(server, new NetworkManager(EnumPacketDirection.SERVERBOUND),
                player) {
            @Override
            public void sendPacket(Packet<?> packet) {}
        };
        player.setPosition(x + 0.5, Y, z + 0.5);
        player.interactionManager.setGameType(GameType.SURVIVAL);
        world.spawnEntity(player);
        return player;
    }

    private void cleanup() {
        if (actor != null && syntheticActor) {
            actor.setDead();
        }
        if (mob != null) {
            mob.setDead();
        }
        actor = null;
        mob = null;
        for (int lane = 0; lane < LANES; ++lane) {
            if (loadActors[lane] != null) {
                loadActors[lane].setDead();
                loadMobs[lane].setDead();
                loadActors[lane] = null;
                loadMobs[lane] = null;
            }
        }
        loadTicks = 0;
        loadHits = 0;
        loadHealthLost = 0;
    }
}
