package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import com.mahghuuuls.threatworks.server.EncounterCoordinator.Inspection;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.monster.EntityEvoker;
import net.minecraft.entity.monster.EntityVex;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityEvokerFangs;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class IllagerFixtureCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");
    private final ThreatworksFixture fixture;
    private WorldServer world;
    private EntityCreature mob;
    private EntityPlayerMP left;
    private EntityPlayerMP right;
    private EntitySheep sheep;
    private final List<EntityVex> summons = new ArrayList<>();
    private String lastAction;
    private EntityVex reloaded;
    private EntityEvoker ally;

    IllagerFixtureCommand(ThreatworksFixture fixture) {
        this.fixture = fixture;
    }

    @Override
    public String getName() {
        return "twillager";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/twillager <setup|read|hit|start|taunt|grant|compete|restage|retreat|reset|ally|reload> [actor or case] [amount]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0 || !server.isDedicatedServer() || !server.getFolderName().startsWith("TW-Vanilla-Illager")) {
            throw new CommandException(getUsage(sender));
        }
        if ("setup".equals(args[0]) && mob == null) {
            setup(server.getWorld(0), args[1]);
            report("state");
            return;
        }
        if (mob == null) {
            throw new CommandException("The arena is empty.");
        }
        switch (args[0]) {
            case "read":
                break;
            case "hit":
                float before = mob.getHealth();
                mob.attackEntityFrom(DamageSource.causePlayerDamage(actor(args)),
                        (float) parseDouble(args[2], 0, 10));
                LOGGER.info("illager input actor={} before={} after={}", actor(args).getName(), before, mob.getHealth());
                break;
            case "grant":
                LOGGER.info("illager grant={}", EncounterCoordinator.INSTANCE.grant(mob, actor(args),
                        parseDouble(args[2], 0, 1000)));
                break;
            case "taunt":
                LOGGER.info("illager taunt={}", EncounterCoordinator.INSTANCE.taunt(mob, actor(args)));
                break;
            case "start":
                left.capabilities.disableDamage = false;
                right.capabilities.disableDamage = false;
                LOGGER.info("illager taunt={}", EncounterCoordinator.INSTANCE.taunt(mob, left));
                break;
            case "compete":
                mob.setAttackTarget(actor(args));
                break;
            case "restage":
                if (!"idle".equals(action())) {
                    throw new CommandException("Actors can be restaged between native actions.");
                }
                right.setPositionAndUpdate(mob.posX + 7, mob.posY, mob.posZ);
                break;
            case "reset":
                mob.setPositionAndUpdate(0.5, 4, 0.5);
                place(left, -9);
                place(right, 10);
                break;
            case "ally":
                LOGGER.info("illager ally={}", EncounterCoordinator.INSTANCE.taunt(mob, ally));
                break;
            case "retreat":
                if (!"idle".equals(action())) {
                    throw new CommandException("Wait for the current spell to end.");
                }
                right.setPositionAndUpdate(mob.posX + 3.5, 4, mob.posZ);
                break;
            case "reload":
                reload();
                break;
            default:
                throw new CommandException(getUsage(sender));
        }
        report("state");
    }

    private void setup(WorldServer next, String mode) throws CommandException {
        world = next;
        ForgeChunkManager.Ticket ticket = ForgeChunkManager.requestTicket(fixture, world, ForgeChunkManager.Type.NORMAL);
        if (ticket == null) {
            throw new CommandException("Could not hold the arena.");
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getChunkProvider().provideChunk(x, z);
                ForgeChunkManager.forceChunk(ticket, new ChunkPos(x, z));
            }
        }
        world.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
        world.getGameRules().setOrCreateGameRule("mobGriefing", "true");
        mob = "vex".equals(mode) ? new EntityVex(world) : new EntityEvoker(world);
        mob.setPosition(0.5, 4, 0.5);
        mob.enablePersistence();
        if ("sheep".equals(mode)) {
            sheep = new EntitySheep(world);
            sheep.setPosition(3.5, 4, 0.5);
            sheep.setFleeceColor(EnumDyeColor.BLUE);
            world.spawnEntity(sheep);
        } else {
            left = player("IllagerLeft", -9);
            right = player("IllagerRight", 10);
            if (mob instanceof EntityEvoker) {
                ally = new EntityEvoker(world);
                ally.setNoAI(true);
                ally.setPosition(4.5, 4, 4.5);
                world.spawnEntity(ally);
            }
        }
        world.spawnEntity(mob);
        if (left != null) {
            LOGGER.info("illager actors mob={} left={} right={}", mob.getUniqueID(), left.getUniqueID(),
                    right.getUniqueID());
        }
    }

    private EntityPlayerMP player(String name, int x) {
        EntityPlayerMP player = CoverageFixtureCommand.fixturePlayer(world, "illager", name);
        player.addTag("tw_l_tick_hurt_window");
        player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(250);
        player.setHealth(250);
        player.capabilities.disableDamage = true;
        place(player, x);
        world.spawnEntity(player);
        return player;
    }

    private static void place(EntityPlayerMP player, int x) {
        player.setPositionAndUpdate(x + 0.5, 4, 0.5);
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

    private void reload() throws CommandException {
        EntityVex summoned = summons.stream().filter(Entity::isEntityAlive).findFirst().orElse(null);
        if (summoned == null) {
            throw new CommandException("A native summon is required first.");
        }
        NBTTagCompound data = summoned.writeToNBT(new NBTTagCompound());
        summoned.setDead();
        data.removeTag("UUIDMost");
        data.removeTag("UUIDLeast");
        EntityVex copy = new EntityVex(world);
        copy.readEntityFromNBT(data);
        copy.setPosition(0.5, 4, 6.5);
        reloaded = copy;
        world.spawnEntity(copy);
        LOGGER.info("illager reload owner={} life={} status={} taunt={}", name(copy.getOwner()),
                data.getInteger("LifeTicks"), EncounterCoordinator.INSTANCE.inspect(copy, 2).status,
                EncounterCoordinator.INSTANCE.taunt(copy, right));
    }

    @SubscribeEvent
    public void spawned(EntityJoinWorldEvent event) {
        if (!(mob instanceof EntityEvoker) || event.getWorld() != world) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof EntityEvokerFangs && ((EntityEvokerFangs) entity).getCaster() == mob) {
            LOGGER.info("illager fang uuid={} target={} x={} y={} z={}", entity.getUniqueID(), name(mob.getAttackTarget()),
                    entity.posX, entity.posY, entity.posZ);
        } else if (entity instanceof EntityVex && ((EntityVex) entity).getOwner() == mob) {
            EntityVex vex = (EntityVex) entity;
            summons.add(vex);
            LOGGER.info("illager summon uuid={} owner={} life={}", entity.getUniqueID(), mob.getUniqueID(),
                    vex.writeToNBT(new NBTTagCompound()).getInteger("LifeTicks"));
        }
    }

    @SubscribeEvent
    public void damage(LivingDamageEvent event) {
        if (mob != null && (event.getSource().getTrueSource() == mob || event.getEntityLiving() == reloaded)) {
            LOGGER.info("illager damage source={} victim={} before={} amount={} charge={} casterTarget={} origin={}",
                    event.getSource().getDamageType(), name(event.getEntityLiving()), event.getEntityLiving().getHealth(),
                    event.getAmount(), mob instanceof EntityVex && ((EntityVex) mob).isCharging(),
                    name(mob.getAttackTarget()), event.getSource().getImmediateSource() == null ? "none"
                            : event.getSource().getImmediateSource().getUniqueID());
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (mob == null || event.world != world || event.phase != TickEvent.Phase.END) {
            return;
        }
        String state = action() + "/" + name(mob.getAttackTarget());
        if (!state.equals(lastAction)) {
            lastAction = state;
            report("action");
        }
        for (EntityVex summon : summons) {
            if (!summon.getTags().contains("tw_observed_owner") && summon.getAttackTarget() != null
                    && summon.getAttackTarget() == mob.getAttackTarget()
                    && summon.targetTasks.taskEntries.stream().anyMatch(task -> task.using
                    && task.action.getClass().getEnclosingClass() == EntityVex.class
                    && task.action.getClass().getSimpleName().equals("AICopyOwnerTarget"))) {
                summon.addTag("tw_observed_owner");
                LOGGER.info("illager owner-follow uuid={} target={} ownerTarget={} status={} nativeGoal=AICopyOwnerTarget", summon.getUniqueID(),
                        name(summon.getAttackTarget()), name(mob.getAttackTarget()),
                        EncounterCoordinator.INSTANCE.inspect(summon, 2).status);
            }
        }
    }

    private String action() {
        if (mob instanceof EntityVex) {
            return ((EntityVex) mob).isCharging() ? "charge" : "idle";
        }
        if (((EntityEvoker) mob).isSpellcasting()) {
            return "casting";
        }
        return mob.tasks.taskEntries.stream().anyMatch(it -> it.using && it.action instanceof EntityAIAvoidEntity)
                ? "retreat" : "idle";
    }

    private static String name(Entity entity) {
        return entity == null ? "none" : entity.getName();
    }

    private void report(String kind) {
        Inspection state = EncounterCoordinator.INSTANCE.inspect(mob, 8);
        LOGGER.info("illager {} action={} target={} status={} tracked={} hp={} x={} leftHp={} rightHp={} sheep={} score={}",
                kind, action(), name(mob.getAttackTarget()), state.status, state.tracked, mob.getHealth(), mob.posX,
                left == null ? 0 : left.getHealth(), right == null ? 0 : right.getHealth(),
                sheep == null ? "none" : sheep.getFleeceColor().getName(),
                state.participants.stream().map(it -> it.id + ":" + it.score).collect(Collectors.joining(",")));
    }
}
