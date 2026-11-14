package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class NeutralFixtureCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");
    private final ThreatworksFixture fixture;
    private WorldServer world;
    private EntityMob mob;
    private EntityPigZombie peer;
    private EntityPlayerMP left;
    private EntityPlayerMP right;
    private boolean cancel;

    NeutralFixtureCommand(ThreatworksFixture fixture) {
        this.fixture = fixture;
    }

    @Override
    public String getName() {
        return "twneutral";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/twneutral <setup|read|cancel|hit|arrow|look|away|pumpkin|unmask|day|night|invalidate|counterfactual> [mode]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!server.isDedicatedServer() || !server.getFolderName().startsWith("TW-Vanilla-Neutral") || args.length == 0) {
            throw new CommandException(getUsage(sender));
        }
        if ("setup".equals(args[0]) && mob == null) {
            setup(server.getWorld(0), args[1]);
        } else if (mob == null) {
            throw new CommandException("Set up the arena first.");
        } else {
            switch (args[0]) {
                case "read":
                    break;
                case "cancel":
                    cancel = true;
                    try {
                        strike(DamageSource.causePlayerDamage(left));
                    } finally {
                        cancel = false;
                    }
                    break;
                case "hit":
                    strike(DamageSource.causePlayerDamage(left.capabilities.disableDamage ? right : left));
                    break;
                case "arrow":
                    EntityTippedArrow arrow = new EntityTippedArrow(world, left);
                    arrow.shoot(left, 0, 0, 0, 1, 0);
                    strike(DamageSource.causeArrowDamage(arrow, left));
                    break;
                case "look":
                    double x = mob.posX - left.posX;
                    double y = mob.posY + mob.getEyeHeight() - left.posY - left.getEyeHeight();
                    double z = mob.posZ - left.posZ;
                    left.rotationYaw = (float) (Math.atan2(z, x) * 180 / Math.PI - 90);
                    left.rotationPitch = (float) (-Math.atan2(y, Math.hypot(x, z)) * 180 / Math.PI);
                    left.prevRotationYaw = left.rotationYaw;
                    left.prevRotationPitch = left.rotationPitch;
                    break;
                case "away":
                    left.rotationYaw = 0;
                    left.prevRotationYaw = 0;
                    break;
                case "pumpkin":
                    left.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Blocks.PUMPKIN));
                    break;
                case "unmask":
                    left.setItemStackToSlot(EntityEquipmentSlot.HEAD, ItemStack.EMPTY);
                    break;
                case "day":
                    world.setWorldTime(6000);
                    break;
                case "night":
                    world.setWorldTime(18000);
                    break;
                case "invalidate":
                    left.capabilities.disableDamage = true;
                    break;
                case "counterfactual":
                    mob.setAttackTarget(right);
                    break;
                default:
                    throw new CommandException(getUsage(sender));
            }
        }
        report();
    }

    private void setup(WorldServer next, String mode) throws CommandException {
        world = next;
        ForgeChunkManager.Ticket ticket = ForgeChunkManager.requestTicket(fixture, world, ForgeChunkManager.Type.NORMAL);
        if (ticket == null) {
            throw new CommandException("Could not hold the neutral arena.");
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getChunkProvider().provideChunk(x, z);
                ForgeChunkManager.forceChunk(ticket, new ChunkPos(x, z));
            }
        }
        world.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
        world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
        world.setWorldTime(18000);
        mob = "pig".equals(mode) ? new EntityPigZombie(world) : new EntityEnderman(world);
        mob.enablePersistence();
        mob.setPosition(0.5, 4, 0.5);
        left = player("NeutralLeft", -8, 0);
        right = player("NeutralRight", 9, 0);
        world.spawnEntity(mob);
        if (mob instanceof EntityPigZombie) {
            peer = new EntityPigZombie(world);
            peer.enablePersistence();
            peer.setPosition(4.5, 4, 1.5);
            world.spawnEntity(peer);
        }
    }

    private EntityPlayerMP player(String name, int x, int z) {
        EntityPlayerMP player = CoverageFixtureCommand.fixturePlayer(world, "neutral", name);
        player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000);
        player.setPosition(x + 0.5, 4, z + 0.5);
        world.spawnEntity(player);
        return player;
    }

    private void strike(DamageSource source) {
        float before = mob.getHealth();
        boolean accepted = mob.attackEntityFrom(source, 3.0F);
        LOGGER.info("neutral hit source={} before={} after={} accepted={}", source.getDamageType(), before, mob.getHealth(), accepted);
    }

    @SubscribeEvent
    public void cancelHit(LivingAttackEvent event) {
        if (cancel && event.getEntityLiving() == mob) {
            event.setCanceled(true);
        }
    }

    private static String name(EntityLivingBase target) {
        return target == null ? "none" : target.getName();
    }

    private void report() {
        NBTTagCompound data = mob.writeToNBT(new NBTTagCompound());
        LOGGER.info("neutral state type={} anger={} peerAnger={} target={} revenge={} status={} hp={} tick={} x={} y={} z={} day={} sky={}",
                mob instanceof EntityPigZombie ? "pig" : "enderman", data.getShort("Anger"),
                peer == null ? 0 : peer.writeToNBT(new NBTTagCompound()).getShort("Anger"), name(mob.getAttackTarget()),
                name(mob.getRevengeTarget()), EncounterCoordinator.INSTANCE.inspect(mob, 2).status, mob.getHealth(),
                mob.ticksExisted, mob.posX, mob.posY, mob.posZ, world.isDaytime(), world.canSeeSky(mob.getPosition()));
    }
}
