package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import com.mahghuuuls.threatworks.server.EncounterCoordinator.Inspection;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackRanged;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.stream.Collectors;

final class WitherFixtureCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");
    private WorldServer world;
    private EntityWither wither;
    private EntityPlayerMP left;
    private EntityPlayerMP right;

    @Override
    public String getName() {
        return "twwither";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/twwither <setup|read|swing|thorns|arrow|taunt|grant|move|shortreach> [actor] [amount or x]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0 || !server.isDedicatedServer() || !server.getFolderName().startsWith("TW-Vanilla-Wither")) {
            throw new CommandException(getUsage(sender));
        }
        if ("setup".equals(args[0]) && wither == null) {
            setup(server.getWorld(0));
        } else if (wither == null) {
            throw new CommandException("Set up the disposable Wither arena first.");
        } else {
            switch (args[0]) {
                case "read":
                    break;
                case "swing":
                    swing(actor(args), (float) parseDouble(args[2], 0, 300));
                    break;
                case "thorns":
                    thorns(actor(args));
                    break;
                case "arrow":
                    arrow(actor(args));
                    break;
                case "taunt":
                    LOGGER.info("wither taunt={}", EncounterCoordinator.INSTANCE.taunt(wither, actor(args)));
                    break;
                case "grant":
                    LOGGER.info("wither grant={}", EncounterCoordinator.INSTANCE.grant(wither, actor(args),
                            parseDouble(args[2], 0, 1000)));
                    break;
                case "move":
                    place(actor(args), parseInt(args[2], -60, 60));
                    break;
                case "shortreach":
                    replaceRangedGoal();
                    break;
                default:
                    throw new CommandException(getUsage(sender));
            }
        }
        report();
    }

    private void setup(WorldServer nextWorld) {
        world = nextWorld;
        for (int x = -5; x <= 5; x++) {
            for (int z = -4; z <= 4; z++) {
                world.getChunkProvider().provideChunk(x, z);
            }
        }
        world.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
        world.getGameRules().setOrCreateGameRule("mobGriefing", "false");
        wither = new EntityWither(world);
        // The native watched-head slots reserve entity ID zero for "no target".
        left = player("WitherLeft", -12);
        right = player("WitherRight", 26);
        wither.setPosition(0.5, world.getHeight(new BlockPos(0, 0, 0)).getY(), 0.5);
        wither.ignite();
        world.spawnEntity(wither);
        LOGGER.info("wither actors mob={} left={} right={} leftId={} rightId={} initialCountdown={}", wither.getUniqueID(),
                left.getUniqueID(), right.getUniqueID(), left.getEntityId(), right.getEntityId(), wither.getInvulTime());
    }

    private EntityPlayerMP player(String name, int x) {
        EntityPlayerMP player = CoverageFixtureCommand.fixturePlayer(world, "minecraft:wither", name);
        player.addTag("tw_l_tick_hurt_window");
        player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(500.0);
        player.setHealth(500.0F);
        place(player, x);
        world.spawnEntity(player);
        return player;
    }

    private void place(EntityPlayerMP player, int x) {
        double y = world.getHeight(new BlockPos(x, 0, 0)).getY();
        player.setPositionAndUpdate(x + 0.5, y, 0.5);
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

    private void swing(EntityPlayerMP player, float amount) {
        float before = wither.getHealth();
        Vec3d previous = player.getPositionVector();
        player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(amount);
        ReflectionHelper.setPrivateValue(EntityLivingBase.class, player, 100,
                "ticksSinceLastSwing", "field_184617_aD");
        player.setPosition(wither.posX, wither.posY, wither.posZ + 2);
        player.onGround = true;
        try {
            player.attackTargetEntityWithCurrentItem(wither);
        } finally {
            player.setPosition(previous.x, previous.y, previous.z);
        }
        LOGGER.info("wither hit mode=swing actor={} input={} before={} after={}", player.getName(), amount, before,
                wither.getHealth());
    }

    private void arrow(EntityPlayerMP player) {
        EntityTippedArrow arrow = new EntityTippedArrow(world, player);
        arrow.shoot(player, 0, 0, 0, 1, 0);
        float before = wither.getHealth();
        boolean accepted = wither.attackEntityFrom(DamageSource.causeArrowDamage(arrow, player), 20.0F);
        LOGGER.info("wither hit mode=arrow actor={} before={} after={} accepted={}",
                player.getName(), before, wither.getHealth(), accepted);
    }

    private void thorns(EntityPlayerMP player) {
        float before = wither.getHealth();
        boolean accepted = wither.attackEntityFrom(DamageSource.causeThornsDamage(player), 8.0F);
        LOGGER.info("wither hit mode=thorns actor={} before={} after={} accepted={}",
                player.getName(), before, wither.getHealth(), accepted);
    }

    private void replaceRangedGoal() {
        EntityAITaskEntry nativeGoal = wither.tasks.taskEntries.stream()
                .filter(entry -> entry.action.getClass() == EntityAIAttackRanged.class)
                .findFirst().orElseThrow(IllegalStateException::new);
        wither.tasks.removeTask(nativeGoal.action);
        wither.tasks.addTask(nativeGoal.priority, new EntityAIAttackRanged(wither, 1.0, 40, 5.0F));
        LOGGER.info("wither goal replaced with five-block ranged radius");
    }

    @SubscribeEvent
    public void fireball(EntityJoinWorldEvent event) {
        if (wither == null || event.getWorld() != world || !(event.getEntity() instanceof EntityWitherSkull)) {
            return;
        }
        EntityWitherSkull skull = (EntityWitherSkull) event.getEntity();
        if (skull.shootingEntity != wither) {
            return;
        }
        double x = skull.posX - wither.posX;
        double y = skull.posY - wither.posY;
        double z = skull.posZ - wither.posZ;
        // Vanilla launches from y+3 at center and y+2.2 with opposite radial offsets at the sides.
        double yaw = wither.renderYawOffset * 0.017453292F;
        int slot = Math.abs(y - 3.0) < 0.01 && x * x + z * z < 0.001 ? 0
                : x * MathHelper.cos((float) yaw) + z * MathHelper.sin((float) yaw) < 0 ? 1 : 2;
        Vec3d motion = new Vec3d(skull.accelerationX, skull.accelerationY, skull.accelerationZ).normalize();
        LOGGER.info("wither skull id={} slot={} aimLeft={} aimRight={} nativeTarget={} watched0={} watched1={} watched2={} x={} y={} z={}",
                skull.getUniqueID(), slot, aim(skull, left, motion), aim(skull, right, motion),
                targetName(wither.getAttackTarget()), watched(0), watched(1), watched(2), x, y, z);
    }

    @SubscribeEvent
    public void impact(ProjectileImpactEvent.Fireball event) {
        if (wither == null || !(event.getFireball() instanceof EntityWitherSkull)
                || event.getFireball().shootingEntity != wither) {
            return;
        }
        Entity hit = event.getRayTraceResult().entityHit;
        if (hit == left || hit == right) {
            LOGGER.info("wither impact skull={} victim={} canceled={}",
                    event.getFireball().getUniqueID(), hit.getName(), event.isCanceled());
        }
    }

    private static double aim(EntityWitherSkull skull, EntityPlayerMP player, Vec3d motion) {
        return new Vec3d(player.posX - skull.posX, player.posY + player.getEyeHeight() * 0.5 - skull.posY,
                player.posZ - skull.posZ).normalize().dotProduct(motion);
    }

    private String watched(int slot) {
        int id = wither.getWatchedTargetId(slot);
        return id > 0 ? targetName(world.getEntityByID(id)) : "none";
    }

    private static String targetName(Entity entity) {
        return entity == null ? "none" : entity.getName();
    }

    private void report() {
        Inspection state = EncounterCoordinator.INSTANCE.inspect(wither, 10);
        LOGGER.info("wither state countdown={} hp={} armored={} tracked={} status={} center={} side1={} side2={} "
                        + "taunter={} tauntTicks={} score={} eligibility={} posX={} posY={} distanceLeft={} distanceRight={} hpLeft={} hpRight={}",
                wither.getInvulTime(), wither.getHealth(), wither.isArmored(), state.tracked, state.status,
                targetName(wither.getAttackTarget()), watched(1), watched(2), state.taunter, state.tauntTicks,
                state.participants.stream().map(it -> it.id + ":" + it.score).collect(Collectors.joining(",")),
                state.participants.stream().map(it -> it.id + ":" + it.eligibility).collect(Collectors.joining(",")),
                wither.posX, wither.posY, wither.getDistance(left), wither.getDistance(right),
                left.getHealth(), right.getHealth());
    }
}
