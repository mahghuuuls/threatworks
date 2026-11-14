package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import com.mahghuuuls.threatworks.server.EncounterCoordinator.Inspection;
import com.mahghuuuls.threatworks.server.GuardianBeam;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityElderGuardian;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.stream.Collectors;

final class GuardianFixtureCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");
    private final ThreatworksFixture fixture;
    private WorldServer world;
    private EntityGuardian guardian;
    private EntityPlayerMP left;
    private EntityPlayerMP right;
    private BlockPos wall;
    private int lastBeam;

    GuardianFixtureCommand(ThreatworksFixture fixture) {
        this.fixture = fixture;
    }

    @Override
    public String getName() {
        return "twbeam";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/twbeam <setup|read|hit|activate|taunt|grant|close|open|obstruct|unblock|compete> [actor or type] [amount]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0 || !server.isDedicatedServer() || !server.getFolderName().startsWith("TW-Vanilla-Guardian")) {
            throw new CommandException(getUsage(sender));
        }
        if ("setup".equals(args[0]) && guardian == null) {
            setup(server.getWorld(0), args.length > 1 && "elder".equals(args[1]));
            report("state");
            return;
        }
        if (guardian == null) {
            throw new CommandException("Set up a beam lane first.");
        }
        switch (args[0]) {
            case "read":
                break;
            case "activate":
                guardian.setAttackTarget(left);
                place(right);
                // Keep the native incumbent in place while the first supported hit opens tracking.
            case "hit":
                EntityPlayerMP player = actor(args);
                float before = guardian.getHealth();
                guardian.attackEntityFrom(DamageSource.causePlayerDamage(player), (float) parseDouble(args[2], 0, 10));
                LOGGER.info("beam input actor={} before={} after={}", player.getName(), before, guardian.getHealth());
                break;
            case "taunt":
                LOGGER.info("beam taunt={}", EncounterCoordinator.INSTANCE.taunt(guardian, actor(args)));
                break;
            case "grant":
                LOGGER.info("beam grant={}", EncounterCoordinator.INSTANCE.grant(guardian, actor(args),
                        parseDouble(args[2], 0, 500)));
                break;
            case "close":
                actor(args).setPositionAndUpdate(guardian.posX + 2.0, guardian.posY, guardian.posZ);
                break;
            case "open":
                place(actor(args));
                break;
            case "obstruct":
                wall(actor(args), true);
                break;
            case "unblock":
                wall(left, false);
                break;
            case "compete":
                guardian.setAttackTarget(actor(args));
                break;
            case "invalidCompete":
                left.setPositionAndUpdate(guardian.posX - 24, guardian.posY, guardian.posZ);
                guardian.setAttackTarget(right);
                report("invalidCompete");
                place(left);
                break;
            default:
                throw new CommandException(getUsage(sender));
        }
        report("state");
    }

    private void setup(WorldServer nextWorld, boolean elder) throws CommandException {
        world = nextWorld;
        ForgeChunkManager.Ticket ticket = ForgeChunkManager.requestTicket(fixture, world, ForgeChunkManager.Type.NORMAL);
        if (ticket == null) {
            throw new CommandException("Unable to keep the aquatic lane loaded.");
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getChunkProvider().provideChunk(x, z);
                ForgeChunkManager.forceChunk(ticket, new ChunkPos(x, z));
            }
        }
        world.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
        for (int x = -14; x <= 14; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = 4; y <= 10; y++) {
                    world.setBlockState(new BlockPos(x, y, z),
                            Math.abs(x) == 14 || Math.abs(z) == 3 || y == 4 || y == 10
                                    ? Blocks.GLASS.getDefaultState() : Blocks.WATER.getDefaultState());
                }
            }
        }
        for (int x = -4; x <= 4; x++) {
            for (int z = -3; z <= 3; z++) {
                world.getChunkProvider().provideChunk(x, z);
            }
        }
        guardian = elder ? new EntityElderGuardian(world) : new EntityGuardian(world);
        guardian.setPosition(0.5, 7, 0.5);
        guardian.setNoGravity(true);
        guardian.enablePersistence();
        world.spawnEntity(guardian);
        // The beam protocol reserves zero as the absent target ID.
        left = player("BeamLeft");
        right = player("BeamRight");
        right.capabilities.disableDamage = true;
        LOGGER.info("beam actors mob={} left={} right={} elder={}", guardian.getUniqueID(),
                left.getUniqueID(), right.getUniqueID(), elder);
    }

    private EntityPlayerMP player(String name) {
        EntityPlayerMP player = CoverageFixtureCommand.fixturePlayer(world, "guardian", name);
        player.interactionManager.setGameType(GameType.SURVIVAL);
        player.addTag("tw_l_tick_hurt_window");
        player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(250);
        player.setHealth(250);
        player.setPosition("BeamLeft".equals(name) ? -8.5 : 8.5, 7, 0.5);
        world.spawnEntity(player);
        return player;
    }

    private void place(EntityPlayerMP player) {
        player.capabilities.disableDamage = false;
        player.setPositionAndUpdate(player == left ? -8.5 : 8.5, 7, 0.5);
    }

    private EntityPlayerMP actor(String[] args) throws CommandException {
        if (args.length > 1 && "left".equals(args[1])) {
            return left;
        }
        if (args.length > 1 && "right".equals(args[1])) {
            return right;
        }
        throw new CommandException("Expected left or right.");
    }

    private void wall(EntityPlayerMP player, boolean block) {
        if (block) {
            wall = new BlockPos((guardian.posX + player.posX) * 0.5, 5, 0);
        }
        if (wall == null) {
            return;
        }
        for (int y = 0; y < 5; y++) {
            for (int z = -2; z <= 2; z++) {
                world.setBlockState(wall.add(0, y, z),
                        block ? Blocks.STONE.getDefaultState() : Blocks.WATER.getDefaultState());
            }
        }
        if (!block) {
            wall = null;
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (guardian == null || event.world != world || event.phase != TickEvent.Phase.END) {
            return;
        }
        int beam = ((GuardianBeam) guardian).threatworks$beamTargetId();
        if (beam != lastBeam) {
            lastBeam = beam;
            report("transition");
        }
    }

    @SubscribeEvent
    public void damage(LivingDamageEvent event) {
        if (guardian != null && event.getSource().getTrueSource() == guardian
                && (event.getEntityLiving() == left || event.getEntityLiving() == right)) {
            EntityLivingBase victim = event.getEntityLiving();
            LOGGER.info("beam damage source={} victim={} victimId={} beam={} before={} amount={} distance={}",
                    event.getSource().getDamageType(), victim.getName(), victim.getEntityId(), beamName(),
                    victim.getHealth(), event.getAmount(), guardian.getDistance(victim));
        }
    }

    private String beamName() {
        int id = ((GuardianBeam) guardian).threatworks$beamTargetId();
        return name(id == 0 ? null : world.getEntityByID(id));
    }

    private static String name(Entity entity) {
        return entity == null ? "none" : entity.getName();
    }

    private void report(String kind) {
        Inspection state = EncounterCoordinator.INSTANCE.inspect(guardian, 8);
        LOGGER.info("beam {} beam={} target={} tracked={} status={} leftHp={} rightHp={} leftDist={} rightDist={} fatigue={} tick={} loaded={} survival={} score={}",
                kind, beamName(), name(guardian.getAttackTarget()), state.tracked, state.status,
                left.getHealth(), right.getHealth(), guardian.getDistance(left), guardian.getDistance(right),
                left.isPotionActive(MobEffects.MINING_FATIGUE), guardian.ticksExisted,
                world.getEntityFromUuid(guardian.getUniqueID()) == guardian, left.interactionManager.survivalOrAdventure(),
                state.participants.stream()
                        .map(it -> it.id + ":" + it.score).collect(Collectors.joining(",")));
    }
}
