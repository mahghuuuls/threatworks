package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.server.DragonStrafe;
import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import com.mahghuuuls.threatworks.server.EncounterCoordinator.Inspection;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MultiPartEntityPart;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.dragon.phase.IPhase;
import net.minecraft.entity.boss.dragon.phase.PhaseList;
import net.minecraft.entity.boss.dragon.phase.PhaseStrafePlayer;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityDragonFireball;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.feature.WorldGenEndPodium;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.stream.Collectors;

final class DragonFixtureCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");
    private final ThreatworksFixture fixture;
    private WorldServer world;
    private EntityDragon dragon;
    private EntityPlayerMP left;
    private EntityPlayerMP right;
    private EntityPlayerMP visitor;
    private String lastPhase;
    private BlockPos podium;

    DragonFixtureCommand(ThreatworksFixture fixture) {
        this.fixture = fixture;
    }

    @Override
    public String getName() {
        return "twdragon";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/twdragon <setup|read|hit|move|crystal|taunt|grant|visitor|respawn> [actor] [mode] [amount]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0 || !server.isDedicatedServer() || !server.getFolderName().startsWith("TW-Vanilla-Dragon")) {
            throw new CommandException(getUsage(sender));
        }
        if ("setup".equals(args[0])) {
            setup(server);
            return;
        }
        if (world == null || dragon == null) {
            throw new CommandException("The native End fight is still starting.");
        }
        switch (args[0]) {
            case "read":
                break;
            case "hit":
                hit(actor(args), args[2], (float) parseDouble(args[3], 0, 1000));
                break;
            case "move":
                EntityPlayerMP player = actor(args);
                place(player, "near".equals(args[2]) ? (player == left ? -14 : 16) : 75,
                        player == left ? 0 : 6);
                break;
            case "crystal":
                crystal(actor(args));
                break;
            case "visitor":
                if (visitor == null) {
                    visitor = player("DragonVisitor", 2, 9);
                }
                break;
            case "taunt":
                LOGGER.info("dragon taunt={}", EncounterCoordinator.INSTANCE.taunt(dragon, actor(args)));
                break;
            case "grant":
                LOGGER.info("dragon grant={}", EncounterCoordinator.INSTANCE.grant(dragon, actor(args),
                        parseDouble(args[2], 0, 1000)));
                break;
            case "respawn":
                respawn();
                break;
            default:
                throw new CommandException(getUsage(sender));
        }
        report("state");
    }

    private void setup(MinecraftServer server) throws CommandException {
        world = server.getWorld(1);
        for (int startX = -6; startX <= 4; startX += 5) {
            for (int startZ = -6; startZ <= 4; startZ += 5) {
                ForgeChunkManager.Ticket ticket = ForgeChunkManager.requestTicket(fixture, world, ForgeChunkManager.Type.NORMAL);
                if (ticket == null) {
                    throw new CommandException("Could not hold the disposable End arena chunks.");
                }
                for (int x = startX; x < startX + 5; x++) {
                    for (int z = startZ; z < startZ + 5; z++) {
                        world.getChunkProvider().provideChunk(x, z);
                        ForgeChunkManager.forceChunk(ticket, new ChunkPos(x, z));
                    }
                }
            }
        }
        podium = world.getTopSolidOrLiquidBlock(WorldGenEndPodium.END_PODIUM_LOCATION);
        world.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
        world.getGameRules().setOrCreateGameRule("mobGriefing", "false");
        left = player("DragonLeft", -14, 0);
        right = player("DragonRight", 16, 6);
        LOGGER.info("dragon actors left={} right={} podium={}", left.getUniqueID(), right.getUniqueID(), podium);
    }

    private EntityPlayerMP player(String name, int x, int z) {
        EntityPlayerMP player = CoverageFixtureCommand.fixturePlayer(world, "minecraft:ender_dragon", name);
        player.setNoGravity(true);
        place(player, x, z);
        world.spawnEntity(player);
        return player;
    }

    private void place(EntityPlayerMP player, int x, int z) {
        world.getChunkProvider().provideChunk(x >> 4, z >> 4);
        player.setPositionAndUpdate(x + 0.5, podium.getY() + 13.0, z + 0.5);
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

    private void hit(EntityPlayerMP player, String mode, float amount) {
        float before = dragon.getHealth();
        float absorbed = dragon.getAbsorptionAmount();
        MultiPartEntityPart part = "body".equals(mode) ? dragon.dragonPartBody : dragon.dragonPartHead;
        DamageSource source = DamageSource.causePlayerDamage(player);
        if ("launch".equals(mode) || "missing".equals(mode)) {
            EntityTippedArrow arrow = new EntityTippedArrow(world, player);
            if ("launch".equals(mode)) {
                player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.STICK));
                arrow.shoot(player, 0, 0, 0, 1, 0);
                player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            }
            source = DamageSource.causeArrowDamage(arrow, player);
        } else if ("ignored".equals(mode)) {
            source = DamageSource.GENERIC;
        } else if ("absorb".equals(mode)) {
            dragon.setAbsorptionAmount(amount);
            absorbed = amount;
        }
        boolean accepted;
        if ("swing".equals(mode)) {
            Vec3d previous = player.getPositionVector();
            player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(amount);
            ReflectionHelper.setPrivateValue(EntityLivingBase.class, player, 100,
                    "ticksSinceLastSwing", "field_184617_aD");
            player.setPosition(part.posX, part.posY + 1, part.posZ + 2);
            player.onGround = true;
            try {
                player.attackTargetEntityWithCurrentItem(part);
            } finally {
                player.setPosition(previous.x, previous.y, previous.z);
            }
            accepted = dragon.getHealth() < before;
        } else {
            accepted = part.attackEntityFrom(source, amount);
        }
        LOGGER.info("dragon hit actor={} mode={} amount={} before={} after={} absorbedBefore={} absorbedAfter={} accepted={}",
                player.getName(), mode, amount, before, dragon.getHealth(), absorbed, dragon.getAbsorptionAmount(), accepted);
    }

    private void crystal(EntityPlayerMP player) throws CommandException {
        EntityEnderCrystal crystal = world.getEntities(EntityEnderCrystal.class, Entity::isEntityAlive).stream()
                .max(Comparator.comparingDouble(dragon::getDistanceSq)).orElse(null);
        if (crystal == null || dragon.getPhaseManager().getCurrentPhase().getType() != PhaseList.HOLDING_PATTERN) {
            throw new CommandException("A native holding pattern and an intact crystal are required.");
        }
        boolean accepted = crystal.attackEntityFrom(DamageSource.causePlayerDamage(player), 1);
        LOGGER.info("dragon crystal actor={} crystal={} accepted={} destroyed={}",
                player.getName(), crystal.getUniqueID(), accepted, crystal.isDead);
    }

    private void respawn() throws CommandException {
        BlockPos portal = null;
        for (int y = podium.getY() + 8; y > podium.getY() - 18; y--) {
            BlockPos candidate = new BlockPos(2, y, 0);
            if (world.getBlockState(candidate).getBlock() == Blocks.END_PORTAL) {
                portal = new BlockPos(0, y, 0);
                break;
            }
        }
        if (portal == null) {
            throw new CommandException("The native exit portal is missing.");
        }
        LOGGER.info("dragon portal block={} position={} previouslyKilled={}", Blocks.END_PORTAL.getRegistryName(),
                portal, dragon.getFightManager().hasPreviouslyKilledDragon());
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            BlockPos placement = portal.offset(facing, 3);
            left.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.END_CRYSTAL));
            LOGGER.info("dragon respawn crystal={} result={}", placement,
                    Items.END_CRYSTAL.onItemUse(left, world, placement, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1, 0.5F));
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (world == null || event.world != world || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (dragon == null || dragon.isDead) {
            EntityDragon live = world.getEntities(EntityDragon.class, Entity::isEntityAlive).stream().findFirst().orElse(null);
            if (live == null) {
                return;
            }
            dragon = live;
            lastPhase = null;
            LOGGER.info("dragon native_spawn id={} fightManager={}", dragon.getUniqueID(), dragon.getFightManager() != null);
        }
        String phase = phaseState();
        if (!phase.equals(lastPhase)) {
            lastPhase = phase;
            report("phase");
        }
    }

    @SubscribeEvent
    public void fireball(EntityJoinWorldEvent event) {
        if (dragon == null || event.getWorld() != world || !(event.getEntity() instanceof EntityDragonFireball)) {
            return;
        }
        EntityDragonFireball fireball = (EntityDragonFireball) event.getEntity();
        if (fireball.shootingEntity == dragon) {
            Vec3d acceleration = new Vec3d(fireball.accelerationX, fireball.accelerationY, fireball.accelerationZ).normalize();
            LOGGER.info("dragon fireball id={} {} aimLeft={} aimRight={}", fireball.getUniqueID(), phaseState(),
                    aim(fireball, left, acceleration), aim(fireball, right, acceleration));
        }
    }

    private double aim(EntityDragonFireball fireball, EntityPlayerMP player, Vec3d acceleration) {
        return new Vec3d(player.posX - fireball.posX, player.getEntityBoundingBox().minY + player.height / 2.0
                - fireball.posY - dragon.dragonPartHead.height / 2.0, player.posZ - fireball.posZ)
                .normalize().dotProduct(acceleration);
    }

    private String phaseState() {
        IPhase phase = dragon.getPhaseManager().getCurrentPhase();
        String phaseName = phase.getType().toString().split(" ", 2)[0];
        if (phase.getType() == PhaseList.STRAFE_PLAYER) {
            EntityLivingBase target = ReflectionHelper.getPrivateValue(PhaseStrafePlayer.class,
                    (PhaseStrafePlayer) phase, "attackTarget", "field_188693_f");
            return "phase=" + phaseName + " origin="
                    + (((DragonStrafe) phase).threatworks$isOrdinaryStrafe() ? "ordinary" : "crystal")
                    + " nativeTarget=" + (target == null ? "none" : target.getName());
        }
        return "phase=" + phaseName + " origin=none nativeTarget=none";
    }

    private void report(String observation) {
        Inspection result = EncounterCoordinator.INSTANCE.inspect(dragon, 10);
        LOGGER.info("dragon {} {} uuid={} hp={} tracked={} status={} target={} score={} eligibility={} dead={} loaded={}",
                observation, phaseState(), dragon.getUniqueID(), dragon.getHealth(), result.tracked, result.status,
                result.target, result.participants.stream().map(participant ->
                        participant.id + ":" + participant.score).collect(Collectors.joining(",")),
                result.participants.stream().map(participant ->
                        participant.id + ":" + participant.eligibility).collect(Collectors.joining(",")),
                dragon.isDead,
                world.getEntityFromUuid(left.getUniqueID()) == left && world.getEntityFromUuid(right.getUniqueID()) == right);
    }
}
