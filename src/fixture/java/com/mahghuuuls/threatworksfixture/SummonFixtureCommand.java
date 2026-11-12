package com.mahghuuuls.threatworksfixture;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.common.ForgeChunkManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class SummonFixtureCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");
    private final ThreatworksFixture fixture;
    private ForgeChunkManager.Ticket arenaTicket;

    SummonFixtureCommand(ThreatworksFixture fixture) {
        this.fixture = fixture;
    }

    @Override
    public String getName() { return "twsummon"; }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/twsummon inspect <entity>, <melee|ranged> <minion> <target>, dimension <player> <-1|0>, hold <entity>, or release";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 2 && "hold".equals(args[0])) {
            Entity anchor = getEntity(server, sender, args[1]);
            if (arenaTicket != null) {
                throw new CommandException("The test arena is already held.");
            }
            arenaTicket = ForgeChunkManager.requestTicket(fixture, anchor.world, ForgeChunkManager.Type.NORMAL);
            if (arenaTicket == null) {
                throw new CommandException("Could not keep the test arena loaded.");
            }
            ChunkPos chunk = new ChunkPos(anchor.getPosition());
            ForgeChunkManager.forceChunk(arenaTicket, chunk);
            LOGGER.info("summon_arena_held dimension={} chunk={}", anchor.dimension, chunk);
            return;
        }
        if (args.length == 1 && "release".equals(args[0]) && arenaTicket != null) {
            ForgeChunkManager.releaseTicket(arenaTicket);
            arenaTicket = null;
            LOGGER.info("summon_arena_released");
            return;
        }
        if (args.length == 3 && "dimension".equals(args[0])) {
            EntityPlayerMP player = getPlayer(server, sender, args[1]);
            int destination = parseInt(args[2], -1, 0);
            if (player.dimension == destination || server.getWorld(destination) == null) {
                throw new CommandException("Expected a different loaded test dimension.");
            }
            int from = player.dimension;
            player.changeDimension(destination, (world, entity, yaw) ->
                    entity.setLocationAndAngles(8.5, destination == 0 ? 4 : 100, 6, 0, 0));
            if (player.dimension != destination) {
                throw new CommandException("The test player did not leave its original dimension.");
            }
            LOGGER.info("summon_caster_dimension player={} from={} to={}", player.getUniqueID(), from, destination);
            return;
        }
        if (args.length == 2 && "inspect".equals(args[0])) {
            EntityLivingBase entity = getEntity(server, sender, args[1], EntityLivingBase.class);
            EntityLivingBase target = entity instanceof EntityLiving ? ((EntityLiving) entity).getAttackTarget() : null;
            LOGGER.info("summon_probe entity={} type={} owner={} attackTarget={} health={} redirectArmed={} tick={}",
                    entity.getUniqueID(), EntityList.getKey(entity),
                    entity instanceof IEntityOwnable ? ((IEntityOwnable) entity).getOwnerId() : null,
                    ArrowFixtureCommand.identity(target), entity.getHealth(),
                    entity.getTags().contains("tw_redirect_next"), entity.world.getTotalWorldTime());
            return;
        }
        if (args.length == 3 && ("melee".equals(args[0]) || "ranged".equals(args[0]))) {
            EntityLiving actor = getEntity(server, sender, args[1], EntityLiving.class);
            EntityLivingBase target = getEntity(server, sender, args[2], EntityLivingBase.class);
            if (!(actor instanceof IEntityOwnable) || actor.world != target.world) {
                throw new CommandException("Expected an owned minion in the target's world.");
            }
            LOGGER.info("summon_action action={} actor={} owner={} target={} tick={}", args[0], actor.getUniqueID(),
                    ((IEntityOwnable) actor).getOwnerId(), target.getUniqueID(), actor.world.getTotalWorldTime());
            if ("melee".equals(args[0])) {
                actor.attackEntityAsMob(target);
            } else if (actor instanceof IRangedAttackMob) {
                ((IRangedAttackMob) actor).attackEntityWithRangedAttack(target, 1.0f);
            } else {
                throw new CommandException("Expected a ranged minion.");
            }
            return;
        }
        throw new WrongUsageException(getUsage(sender));
    }

    static void reportImpact(EntityLivingBase target, DamageSource source, float amount) {
        Entity immediate = source.getImmediateSource();
        Entity shooter = immediate instanceof EntityArrow ? ((EntityArrow) immediate).shootingEntity : null;
        LOGGER.info("summon_impact target={} source={} wrapper={} immediate={} shooter={} trueSource={} amount={} tick={}",
                target.getUniqueID(), source.getDamageType(), source.getClass().getName(),
                ArrowFixtureCommand.identity(immediate), ArrowFixtureCommand.identity(shooter),
                ArrowFixtureCommand.identity(source.getTrueSource()), amount, target.world.getTotalWorldTime());
    }
}
