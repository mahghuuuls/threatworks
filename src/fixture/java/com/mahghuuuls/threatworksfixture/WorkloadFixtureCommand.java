package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.ThreatworksMod;
import com.mahghuuuls.threatworks.api.ThreatActions;
import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import com.mahghuuuls.threatworks.server.WorkloadEligibility;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.UUID;

final class WorkloadFixtureCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");
    private static final String MOB = "tw_g_mob";
    private static final String GUARD = "tw_g_guard";
    private static final int LANES = 16;
    private static final int GUARDS = 4;
    private static final int HEIGHT = 80;

    @Override
    public String getName() {
        return "twwork";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/twwork <seed|activate|idle|active|arm|move-ready|move|switched|cleanup>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        WorldServer world = player.getServerWorld();
        if (args.length != 1 || world.provider.getDimension() != 0) {
            throw new WrongUsageException(getUsage(sender));
        }
        switch (args[0]) {
            case "seed":
                seed(world);
                break;
            case "activate":
                activate(world, player);
                break;
            case "idle":
            case "active":
                inspect(world, player.getUniqueID(), "active".equals(args[0]));
                break;
            case "arm": {
                EntityZombie mob = mob(world, 0);
                EntityIronGolem challenger = guard(world, 0, 3);
                EntityIronGolem incumbent = guard(world, 0, 0);
                incumbent.setPositionAndUpdate(x(0) + 1.5, HEIGHT, z(0) + 0.5);
                challenger.setPositionAndUpdate(x(0) + 11.5, HEIGHT, z(0) + 0.5);
                grant(mob, challenger, 55);
                if (mob.getAttackTarget() != incumbent) {
                    throw new CommandException("Expected the original guard before movement.");
                }
                LOGGER.info("work_arm mob={} incumbent={} challenger={} x={} tick={}", mob.getUniqueID(),
                        incumbent.getUniqueID(), challenger.getUniqueID(), challenger.posX, server.getTickCounter());
                break;
            }
            case "move": {
                EntityZombie mob = mob(world, 0);
                EntityIronGolem challenger = guard(world, 0, 3);
                if (mob.getAttackTarget() != guard(world, 0, 0)) {
                    throw new CommandException("Expected the original guard immediately before movement.");
                }
                challenger.setPositionAndUpdate(x(0) + 1.5, HEIGHT, z(0) + 1.5);
                LOGGER.info("work_move mob={} challenger={} x={} z={} serverTick={}", mob.getUniqueID(),
                        challenger.getUniqueID(), challenger.posX, challenger.posZ, server.getTickCounter());
                break;
            }
            case "move-ready": {
                EntityZombie mob = mob(world, 0);
                EntityIronGolem challenger = guard(world, 0, 3);
                EncounterCoordinator.Inspection view = EncounterCoordinator.INSTANCE.inspect(mob, 8);
                if (mob.getAttackTarget() != guard(world, 0, 0) || !view.participants.stream()
                        .anyMatch(p -> p.id.equals(challenger.getUniqueID())
                                && p.score == 125)
                        || !WorkloadEligibility.observesEligible(world, mob, challenger, MobType.ZOMBIE)) {
                    throw new CommandException("Expected an eligible challenger still behind the chase threshold.");
                }
                LOGGER.info("work_move_ready mob={} challenger={} score=125 eligibility=eligible",
                        mob.getUniqueID(), challenger.getUniqueID());
                break;
            }
            case "switched": {
                EntityZombie mob = mob(world, 0);
                EntityIronGolem challenger = guard(world, 0, 3);
                if (mob.getAttackTarget() != challenger) {
                    throw new CommandException("The challenger did not become the live target.");
                }
                LOGGER.info("work_switched mob={} target={} serverTick={}", mob.getUniqueID(),
                        challenger.getUniqueID(), server.getTickCounter());
                break;
            }
            case "cleanup":
                world.getEntities(EntityZombie.class, entity -> entity.getTags().contains(MOB))
                        .forEach(EntityLiving::setDead);
                world.getEntities(EntityIronGolem.class, entity -> entity.getTags().contains(GUARD))
                        .forEach(EntityLiving::setDead);
                break;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    private static void seed(WorldServer world) throws CommandException {
        for (int lane = 0; lane < LANES; lane++) {
            int x = x(lane);
            int z = z(lane);
            for (int dx = -2; dx <= 15; dx++) {
                for (int dz = -3; dz <= 4; dz++) {
                    world.setBlockState(new BlockPos(x + dx, HEIGHT - 1, z + dz), Blocks.STONE.getDefaultState(), 2);
                }
            }
            EntityZombie mob = new EntityZombie(world);
            place(world, mob, MOB, mobTag(lane), x + 0.5, z + 0.5);
            mob.setNoAI(false);
            mob.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0);
            mob.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(12);
        }
        for (int lane = 0; lane < LANES; lane++) {
            for (int guard = 0; guard < GUARDS; guard++) {
                EntityIronGolem entity = new EntityIronGolem(world);
                place(world, entity, GUARD, guardTag(lane, guard), x(lane) + 3.5 + guard,
                        z(lane) + (guard % 2 == 0 ? 2.5 : -1.5));
            }
        }
    }

    private static void place(WorldServer world, EntityLiving entity, String kind, String tag,
                              double x, double z) throws CommandException {
        entity.setNoAI(true);
        entity.setNoGravity(true);
        entity.enablePersistence();
        entity.addTag(kind);
        entity.addTag(tag);
        entity.setPosition(x, HEIGHT, z);
        if (!world.spawnEntity(entity)) {
            throw new CommandException("Could not spawn " + tag);
        }
    }

    private static void activate(WorldServer world, EntityPlayerMP player) throws CommandException {
        for (int lane = 0; lane < LANES; lane++) {
            EntityZombie mob = mob(world, lane);
            float before = mob.getHealth();
            player.setPositionAndUpdate(mob.posX, HEIGHT, mob.posZ - 2);
            // The same server-side vanilla attack used by the player's attack packet.
            player.attackTargetEntityWithCurrentItem(mob);
            float after = mob.getHealth();
            if (after >= before || !EncounterCoordinator.INSTANCE.inspect(mob, 0).tracked) {
                throw new CommandException("Player attack did not activate lane " + lane);
            }
            mob.setHealth(before);
            for (int guard = 0; guard < GUARDS; guard++) {
                grant(mob, guard(world, lane, guard), 100 - guard * 10);
            }
            LOGGER.info("work_activation lane={} mob={} player={} lost={} guards={}", lane, mob.getUniqueID(),
                    player.getUniqueID(), before - after, GUARDS);
        }
    }

    private static void grant(EntityZombie mob, EntityIronGolem guard, double amount) throws CommandException {
        if (ThreatworksMod.threatActions().grant(mob, guard, amount) != ThreatActions.Result.APPLIED) {
            throw new CommandException("Guard grant was refused.");
        }
    }

    private static void inspect(WorldServer world, UUID player, boolean tracked) throws CommandException {
        int activation = 0;
        int eligible = 0;
        for (int lane = 0; lane < LANES; lane++) {
            EntityZombie mob = mob(world, lane);
            EncounterCoordinator.Inspection view = EncounterCoordinator.INSTANCE.inspect(mob, 8);
            if (view.tracked != tracked || view.omitted != 0) {
                throw new CommandException("Wrong encounter state at lane " + lane);
            }
            for (int slot = 0; slot < GUARDS; slot++) {
                EntityIronGolem guard = guard(world, lane, slot);
                if (tracked) {
                    if (!view.participants.stream().anyMatch(participant -> participant.id.equals(guard.getUniqueID()))
                            || !WorkloadEligibility.observesEligible(world, mob, guard, MobType.ZOMBIE)) {
                        throw new CommandException("Missing eligible guard in lane " + lane + " slot " + slot);
                    }
                    eligible++;
                }
            }
            if (tracked) {
                if (view.participants.size() != GUARDS + 1 || !view.participants.stream().anyMatch(p -> p.id.equals(player))
                        || !guard(world, lane, 0).getUniqueID().equals(view.target)) {
                    throw new CommandException("Unexpected retained score or live target in lane " + lane);
                }
                activation++;
            }
        }
        LOGGER.info("work_load mobs={} guards={} tracked={} eligibleGuards={} activationPlayers={} serverTick={}",
                LANES, LANES * GUARDS, tracked ? LANES : 0, eligible, activation, world.getMinecraftServer().getTickCounter());
    }

    private static EntityZombie mob(WorldServer world, int lane) throws CommandException {
        return requireOne(world.getEntities(EntityZombie.class, entity -> entity.getTags().contains(mobTag(lane))));
    }

    private static EntityIronGolem guard(WorldServer world, int lane, int slot) throws CommandException {
        return requireOne(world.getEntities(EntityIronGolem.class, entity -> entity.getTags().contains(guardTag(lane, slot))));
    }

    private static <T extends EntityLiving> T requireOne(List<T> entities) throws CommandException {
        if (entities.size() != 1 || !entities.get(0).isEntityAlive()) {
            throw new CommandException("Expected exactly one loaded living workload fixture.");
        }
        return entities.get(0);
    }

    private static int x(int lane) {
        return (lane % 4) * 24;
    }

    private static int z(int lane) {
        return (lane / 4) * 24;
    }

    private static String mobTag(int lane) {
        return "tw_g_mob_" + lane;
    }

    private static String guardTag(int lane, int slot) {
        return "tw_g_guard_" + lane + "_" + slot;
    }
}
