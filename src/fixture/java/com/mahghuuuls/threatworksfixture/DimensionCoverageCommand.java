package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import com.mahghuuuls.threatworks.server.EncounterCoordinator.Inspection;
import com.mojang.authlib.GameProfile;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityShulker;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityShulkerBullet;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class DimensionCoverageCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");
    static final String TAG = "tw_k_dimension";
    private final ThreatworksFixture fixture;
    private ForgeChunkManager.Ticket ticket;
    private EntityCreature mob;
    private EntityPlayerMP incumbent;
    private EntityPlayerMP challenger;
    private EntityPlayerMP spawnerPlayer;
    private BlockPos spawner;
    private BlockPos obstacle;
    private WorldServer world;
    private int arenaFloor;

    DimensionCoverageCommand(ThreatworksFixture fixture) {
        this.fixture = fixture;
    }

    @Override
    public String getName() {
        return "twdimension";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/twdimension <locate|adopt|read|activate|resist|unresist|block|unblock|cleanup> [dimension]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0 || !server.isDedicatedServer() || !server.getFolderName().startsWith("TW-Campaign-K")) {
            throw new CommandException(getUsage(sender));
        }
        if (mob == null && !"locate".equals(args[0]) && !"adopt".equals(args[0]) && !"cleanup".equals(args[0])) {
            throw new CommandException("No dimension actor is ready.");
        }
        switch (args[0]) {
            case "locate":
                if (args.length != 2) {
                    throw new CommandException(getUsage(sender));
                }
                locate(server.getWorld(parseInt(args[1], -1, 1)));
                return;
            case "adopt":
                adopt();
                break;
            case "read":
                break;
            case "activate":
                if (mob.getAttackTarget() != incumbent) {
                    throw new CommandException("The native incumbent is missing.");
                }
                CoverageFixtureCommand.attack(mob, challenger, "dimension");
                placeChallenger();
                challenger.setInvisible(false);
                break;
            case "resist":
                challenger.addPotionEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE, 1200));
                break;
            case "unresist":
                challenger.removePotionEffect(MobEffects.FIRE_RESISTANCE);
                break;
            case "block":
            case "unblock":
                wall("block".equals(args[0]));
                break;
            case "cleanup":
                cleanup();
                return;
            default:
                throw new CommandException(getUsage(sender));
        }
        if (mob != null) {
            report();
        }
    }

    private void locate(WorldServer next) throws CommandException {
        cleanup();
        world = next;
        boolean nether = world.provider.getDimension() == -1;
        if (!nether && world.provider.getDimension() != 1) {
            throw new CommandException("Expected the Nether or End.");
        }
        String structure = nether ? "Fortress" : "EndCity";
        BlockPos origin = nether ? new BlockPos(0, 64, 0) : new BlockPos(1200, 64, 0);
        if (nether) {
            // The 1.12.2 fortress locator needs its generator initialized by a chunk first.
            world.getChunkProvider().provideChunk(0, 0);
        }
        BlockPos found = world.findNearestStructure(structure, origin, false);
        if (found == null) {
            throw new CommandException("Could not locate " + structure);
        }
        ticket = ForgeChunkManager.requestTicket(fixture, world, ForgeChunkManager.Type.NORMAL);
        if (ticket == null) {
            throw new CommandException("Could not hold structure chunks.");
        }
        ChunkPos center = new ChunkPos(found);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = center.x + dx;
                int z = center.z + dz;
                world.getChunkProvider().provideChunk(x, z);
                ForgeChunkManager.forceChunk(ticket, new ChunkPos(x, z));
            }
        }
        LOGGER.info("dimension structure={} dimension={} seed={} center={} loaded={}",
                structure, world.provider.getDimension(), world.getSeed(), found, ticket.getChunkList().size());
        if (nether) {
            spawner = ticket.getChunkList().stream()
                    .flatMap(chunk -> world.getChunkProvider().provideChunk(chunk.x, chunk.z)
                            .getTileEntityMap().values().stream())
                    .filter(TileEntityMobSpawner.class::isInstance)
                    .filter(tile -> "minecraft:blaze".equals(tile.writeToNBT(new NBTTagCompound())
                            .getCompoundTag("SpawnData").getString("id")))
                    .map(TileEntity::getPos)
                    .min(Comparator.comparingDouble(pos -> pos.distanceSq(found))).orElse(null);
            if (spawner == null) {
                throw new CommandException("No generated fortress spawner in the held chunks.");
            }
            spawnerPlayer = player("KSpawner", spawner.getX() + 0.5, spawner.getY() + 1.0, spawner.getZ() + 0.5);
            spawnerPlayer.setInvisible(true);
            spawnerPlayer.capabilities.disableDamage = true;
            spawnerPlayer.setNoGravity(true);
            LOGGER.info("dimension spawner={} nativeType=minecraft:blaze block={} dimension={}", spawner,
                    world.getBlockState(spawner).getBlock().getRegistryName(), world.provider.getDimension());
        }
    }

    private void adopt() throws CommandException {
        if (world == null || mob != null) {
            throw new CommandException("Locate a structure before adopting an actor.");
        }
        boolean nether = world.provider.getDimension() == -1;
        Class<? extends EntityCreature> type = nether ? EntityBlaze.class : EntityShulker.class;
        List<? extends EntityCreature> found = world.getEntities(type, Entity::isEntityAlive);
        mob = found.stream().filter(entity -> nether ? entity.getDistanceSq(spawner) < 64
                        : ((EntityShulker) entity).getAttachmentFacing() == EnumFacing.DOWN)
                .min(Comparator.comparingInt(entity -> entity.getEntityId())).orElse(null);
        if (mob == null) {
            throw new CommandException("No structure-generated " + type.getSimpleName());
        }
        mob.addTag(TAG);
        mob.enablePersistence();
        if (spawnerPlayer != null) {
            world.removeEntityDangerously(spawnerPlayer);
            spawnerPlayer = null;
        }
        LOGGER.info("dimension nativeMob={} uuid={} dimension={} entityId={} at={} biome={}",
                EntityList.getKey(mob), mob.getUniqueID(), world.provider.getDimension(), mob.getEntityId(), mob.getPosition(),
                world.getBiome(mob.getPosition()).getRegistryName());
        // Isolate the observed native actor after generation, so impacts can be attributed to it.
        world.getEntities(EntityCreature.class, entity -> entity != mob).forEach(Entity::setDead);
        world.getEntities(Entity.class, entity -> entity instanceof EntityShulkerBullet || entity instanceof EntityFireball)
                .forEach(Entity::setDead);
        world.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
        if (nether) {
            world.setBlockToAir(spawner);
        }
        prepareArena(nether);
        incumbent = player("KIncumbent", mob.posX + 4, mob.posY, mob.posZ);
        challenger = player("KChallenger", mob.posX - 6, mob.posY, mob.posZ + 5);
        challenger.setInvisible(true);
        LOGGER.info("dimension actors mob={} incumbent={} challenger={}", mob.getUniqueID(), incumbent.getUniqueID(),
                challenger.getUniqueID());
    }

    private void prepareArena(boolean nether) {
        BlockPos center = mob.getPosition();
        arenaFloor = center.getY() - 1;
        if (nether) {
            for (BlockPos pos : BlockPos.getAllInBox(center.add(-9, -1, -6), center.add(9, 8, 6))) {
                world.setBlockState(pos, pos.getY() == center.getY() - 1
                        ? Blocks.NETHER_BRICK.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
            }
        } else {
            EntityShulker shulker = (EntityShulker) mob;
            BlockPos anchor = shulker.getAttachmentPos();
            if (anchor == null) {
                anchor = center;
            }
            BlockPos support = anchor.offset(shulker.getAttachmentFacing());
            for (BlockPos pos : BlockPos.getAllInBox(anchor.add(-9, -1, -6), anchor.add(9, 5, 6))) {
                if (!pos.equals(support)) {
                    world.setBlockState(pos, pos.getY() == anchor.getY() - 1
                            ? Blocks.PURPUR_BLOCK.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
                }
            }
        }
    }

    private EntityPlayerMP player(String name, double x, double y, double z) throws CommandException {
        FakePlayer player = new FakePlayer(world, new GameProfile(UUID.randomUUID(), name)) {
            @Override
            public Vec3d getPositionVector() {
                return new Vec3d(posX, posY, posZ);
            }

            @Override
            public boolean isEntityInvulnerable(DamageSource source) {
                return false;
            }
        };
        player.connection = new NetHandlerPlayServer(world.getMinecraftServer(),
                new NetworkManager(EnumPacketDirection.SERVERBOUND), player) {
            @Override
            public void sendPacket(Packet<?> packet) {
                // This player exists only in the disposable server fixture.
            }
        };
        player.dimension = world.provider.getDimension();
        player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(100);
        player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(8);
        player.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        player.setHealth(100);
        ReflectionHelper.setPrivateValue(EntityPlayerMP.class, player, 0, "respawnInvulnerabilityTicks", "field_147101_bU");
        player.setPosition(x, y, z);
        player.setNoGravity(true);
        player.addTag(TAG);
        if (!world.spawnEntity(player)) {
            throw new CommandException("Could not spawn " + name);
        }
        return player;
    }

    private void placeChallenger() {
        double y = mob instanceof EntityBlaze ? mob.posY + 3 : mob.posY;
        challenger.setPosition(mob.posX - 4, y, mob.posZ);
        challenger.setNoGravity(true);
    }

    private void wall(boolean add) {
        if (add) {
            obstacle = mob.getPosition().west(2);
        }
        for (BlockPos pos : BlockPos.getAllInBox(obstacle.add(0, 0, -3), obstacle.add(0, 6, 3))) {
            world.setBlockState(pos, add ? Blocks.OBSIDIAN.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
        }
        LOGGER.info("dimension obstacle={} pos={}", add, obstacle);
    }

    private void report() {
        Inspection view = EncounterCoordinator.INSTANCE.inspect(mob, 16);
        LOGGER.info("dimension state tick={} id={} world={} status={} target={} incumbentHp={} challengerHp={} "
                        + "x={} y={} z={} onGround={} motionY={} challengerY={} floorY={}",
                mob.getServer().getTickCounter(), view.typeId, world.provider.getDimension(), view.status, view.target,
                incumbent.getHealth(), challenger.getHealth(), mob.posX, mob.posY, mob.posZ,
                mob.onGround, mob.motionY, challenger.posY, arenaFloor);
        view.participants.forEach(participant -> LOGGER.info("dimension score participant={} score={} eligibility={}",
                participant.id, participant.score, participant.eligibility));
    }

    private void cleanup() {
        if (world != null) {
            for (Entity entity : world.getEntities(Entity.class, candidate -> candidate.getTags().contains(TAG)
                    || candidate instanceof EntityShulkerBullet || candidate instanceof EntityFireball)) {
                if (entity instanceof EntityPlayerMP) {
                    world.removeEntityDangerously(entity);
                } else {
                    entity.setDead();
                }
            }
        }
        if (ticket != null) {
            ForgeChunkManager.releaseTicket(ticket);
        }
        ticket = null;
        mob = null;
        incumbent = null;
        challenger = null;
        spawnerPlayer = null;
        spawner = null;
        obstacle = null;
        world = null;
    }
}
