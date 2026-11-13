package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.ThreatworksMod;
import com.mahghuuuls.threatworks.api.ThreatActions;
import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import com.mahghuuuls.threatworks.server.EncounterCoordinator.Inspection;
import com.mahghuuuls.threatworks.threat.Encounter;
import com.mojang.authlib.GameProfile;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

final class CoverageFixtureCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");
    private static final String TAG = "tw_coverage";
    private static final double X = 12.5;
    private static final int Y = 20;
    private static final double Z = 12.5;
    private final ThreatworksFixture fixture;
    private ForgeChunkManager.Ticket ticket;

    CoverageFixtureCommand(ThreatworksFixture fixture) {
        this.fixture = fixture;
    }

    @Override
    public String getName() {
        return "twcoverage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/twcoverage <operation> [entity ID]";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0 || !server.isDedicatedServer() || !(server.getFolderName().startsWith("TW-Campaign-I")
                || server.getFolderName().startsWith("TW-Campaign-J") || server.getFolderName().startsWith("TW-Campaign-K")
                || server.getFolderName().startsWith("TW-Campaign-L"))) {
            throw new CommandException(getUsage(sender));
        }
        WorldServer world = server.getWorld(0);
        if (args.length < 2 && ("seed".equals(args[0]) || "natural".equals(args[0]) || "adopt".equals(args[0]))) {
            throw new CommandException(getUsage(sender));
        }
        String operation = args[0];
        if (ticket == null && ("natural".equals(operation) || "seed".equals(operation))) {
            ticket = ForgeChunkManager.requestTicket(fixture, world, ForgeChunkManager.Type.NORMAL);
            if (ticket == null) {
                throw new CommandException("Could not request a coverage chunk ticket.");
            }
            for (int x = -1; x <= 2; x++) {
                for (int z = -1; z <= 2; z++) {
                    ForgeChunkManager.forceChunk(ticket, new ChunkPos(x, z));
                }
            }
        }
        switch (operation) {
            case "seed":
                seed(world, args[1], args.length > 2 && "solo".equals(args[2]));
                break;
            case "natural":
                natural(world, "skeleton".equals(args[1]));
                break;
            case "adopt":
                adopt(world, args[1]);
                break;
            case "cleanup":
                cleanup(world);
                if (ticket != null) {
                    ForgeChunkManager.releaseTicket(ticket);
                    ticket = null;
                }
                break;
            default:
                act(world, operation);
        }
    }

    private static void arena(WorldServer world) {
        arena(world, Y);
    }

    private static void arena(WorldServer world, int height) {
        cleanup(world);
        for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(-12, height - 1, -12), new BlockPos(40, height - 1, 40))) {
            world.setBlockState(pos, Blocks.STONE.getDefaultState(), 2);
        }
    }

    private static void cleanup(WorldServer world) {
        for (Entity entity : world.getEntities(Entity.class, candidate -> candidate.getTags().contains(TAG)
                || candidate instanceof EntityArrow && ((EntityArrow) candidate).shootingEntity != null
                && ((EntityArrow) candidate).shootingEntity.getTags().contains(TAG))) {
            if (entity instanceof EntityPlayerMP) {
                world.removeEntityDangerously(entity);
            } else {
                entity.setDead();
            }
        }
    }

    private static void seed(WorldServer world, String id, boolean solo) throws CommandException {
        int height = "defiledlands:shambler".equals(id) ? 60 : Y;
        arena(world, height);
        Entity entity = EntityList.createEntityByIDFromName(new ResourceLocation(id), world);
        if (!(entity instanceof EntityCreature)) {
            throw new CommandException("Unknown coverage fixture mob " + id);
        }
        EntityCreature mob = (EntityCreature) entity;
        mob.setPosition(X, height, Z);
        mob.onInitialSpawn(world.getDifficultyForLocation(mob.getPosition()), null);
        // Keep exact-ID fixtures separate from the natural replacement probe.
        if (id.startsWith("minecraft:")) {
            mob.getEntityData().setBoolean("SpecialMobsInit", true);
        }
        configureMob(mob);
        place(world, mob, "tw_i_mob", X, height, Z);
        actors(world, mob, solo);
    }

    private static void natural(WorldServer world, boolean skeleton) throws CommandException {
        arena(world);
        for (int i = 0; i < 12; i++) {
            EntityCreature mob = skeleton ? new EntitySkeleton(world) : new EntityZombie(world);
            mob.addTag(TAG);
            mob.addTag("tw_i_candidate");
            mob.setPosition(3.5 + i * 2, Y, Z);
            mob.onInitialSpawn(world.getDifficultyForLocation(mob.getPosition()), null);
            boolean admitted = world.spawnEntity(mob);
            LOGGER.info("coverage naturalInput={} accepted={} uuid={}", EntityList.getKey(mob), admitted, mob.getUniqueID());
        }
    }

    private static void adopt(WorldServer world, String id) throws CommandException {
        EntityCreature selected = world.getEntities(EntityCreature.class, mob -> mob.getTags().contains("tw_i_candidate"))
                .stream().filter(mob -> id.equals(String.valueOf(EntityList.getKey(mob))))
                .min(Comparator.comparingDouble(mob -> mob.posX)).orElse(null);
        if (selected == null) {
            throw new CommandException("The native replacement hook did not produce " + id);
        }
        for (EntityCreature other : world.getEntities(EntityCreature.class, mob -> mob.getTags().contains("tw_i_candidate"))) {
            if (other != selected) {
                other.setDead();
            }
        }
        selected.setPosition(X, Y, Z);
        selected.addTag("tw_i_mob");
        configureMob(selected);
        LOGGER.info("coverage naturalOutput={} uuid={} goals={}", EntityList.getKey(selected), selected.getUniqueID(),
                selected.tasks.taskEntries.size());
        actors(world, selected, false);
    }

    private static void configureMob(EntityCreature mob) {
        mob.enablePersistence();
        if (mob instanceof EntityZombie) {
            ((EntityZombie) mob).setChild(false);
        }
        mob.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(80);
        mob.setHealth(80);
    }

    private static void actors(WorldServer world, EntityCreature mob, boolean solo) throws CommandException {
        if (!solo) {
            EntityIronGolem guard = new EntityIronGolem(world);
            guard.setNoAI(true);
            if ("mowziesmobs:foliaath".equals(String.valueOf(EntityList.getKey(mob)))) {
                guard.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1000);
                guard.setHealth(1000);
            }
            double offset = mob instanceof AbstractSkeleton && mob.getHeldItemMainhand().getItem() instanceof net.minecraft.item.ItemBow
                    ? 9 : 2;
            place(world, guard, "tw_i_guard", X + offset, mob.posY, Z);
        }
        FakePlayer player = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "CampaignI")) {
            @Override
            public net.minecraft.util.math.Vec3d getPositionVector() {
                return new net.minecraft.util.math.Vec3d(posX, posY, posZ);
            }

            @Override
            public boolean isEntityInvulnerable(net.minecraft.util.DamageSource source) {
                return false;
            }
        };
        player.connection = new NetHandlerPlayServer(world.getMinecraftServer(),
                new NetworkManager(EnumPacketDirection.SERVERBOUND), player) {
            @Override
            public void sendPacket(Packet<?> packet) {
                // The server-side player has no remote connection.
            }
        };
        player.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(100);
        player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(8);
        player.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        player.setHealth(100);
        ReflectionHelper.setPrivateValue(EntityPlayerMP.class, player, 0, "respawnInvulnerabilityTicks", "field_147101_bU");
        player.setSneaking(true);
        player.setInvisible(true);
        place(world, player, "tw_i_player", X, mob.posY, Z + 13);
        LOGGER.info("coverage actors mob={} player={} guard={}", mob.getUniqueID(), player.getUniqueID(),
                identity(get(world, EntityIronGolem.class, "tw_i_guard")));
        report(mob, world);
    }

    private static void place(WorldServer world, Entity entity, String tag, double x, double z) throws CommandException {
        place(world, entity, tag, x, Y, z);
    }

    private static void place(WorldServer world, Entity entity, String tag, double x, double y, double z) throws CommandException {
        entity.addTag(TAG);
        entity.addTag(tag);
        entity.setPosition(x, y, z);
        if (!world.spawnEntity(entity)) {
            throw new CommandException("Could not spawn " + tag);
        }
    }

    private static void act(WorldServer world, String operation) throws CommandException {
        EntityCreature mob = get(world, EntityCreature.class, "tw_i_mob");
        EntityPlayerMP player = get(world, EntityPlayerMP.class, "tw_i_player");
        EntityIronGolem guard = get(world, EntityIronGolem.class, "tw_i_guard");
        if (mob == null || player == null) {
            throw new CommandException("Coverage actors are missing.");
        }
        ThreatActions actions = ThreatworksMod.threatActions();
        switch (operation) {
            case "read":
                break;
            case "absorb":
                mob.setAbsorptionAmount(40);
                attack(mob, player, operation);
                mob.setAbsorptionAmount(0);
                mob.setRevengeTarget(null);
                player.setPosition(X, Y, Z + 13);
                break;
            case "hit":
                attack(mob, player, operation);
                break;
            case "guard-retaliation":
                if (guard == null) {
                    throw new CommandException("Missing native retaliation guard.");
                }
                player.setPosition(X, mob.posY, Z + 13);
                player.capabilities.disableDamage = true;
                guard.attackEntityAsMob(mob);
                break;
            case "legal-player":
                restorePlayer(player);
                break;
            case "retaliation-opening":
            case "opening":
                if (guard == null || mob.getAttackTarget() != guard) {
                    throw new CommandException("Expected the native guard immediately before the opening hit.");
                }
                if ("retaliation-opening".equals(operation)) {
                    restorePlayer(player);
                }
                attack(mob, player, "hit");
                break;
            case "cancel":
                mob.addTag("threatworks_cancel_next");
                attack(mob, player, operation);
                player.setPosition(X, Y, Z + 13);
                break;
            case "lethal":
                mob.setHealth(3);
                attack(mob, player, operation);
                Map<WorldServer, ?> worlds = ReflectionHelper.getPrivateValue(EncounterCoordinator.class,
                        EncounterCoordinator.INSTANCE, "worlds");
                Object partition = worlds.get(world);
                Encounter lethal;
                try {
                    Field encounters = partition.getClass().getDeclaredField("encounters");
                    encounters.setAccessible(true);
                    lethal = (Encounter) ((Map<?, ?>) encounters.get(partition)).get(mob.getUniqueID());
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Could not inspect the encounter before lethal removal.", exception);
                }
                LOGGER.info("coverage lethalCredit mob={} participant={} score={}", mob.getUniqueID(),
                        player.getUniqueID(), lethal == null ? null : lethal.copyScores().get(player.getUniqueID()));
                break;
            case "taunt":
                player.setInvisible(false);
                player.hurtResistantTime = 0;
                player.setPosition(mob.posX - 7, Y, mob.posZ + 2);
                LOGGER.info("coverage taunt={}", actions.taunt(mob, player));
                break;
            case "phase-taunt":
                LOGGER.info("coverage phaseTaunt={}", actions.taunt(mob, player));
                break;
            case "phase-native-grant":
                if ("native_phase".equals(EncounterCoordinator.INSTANCE.inspect(mob, 0).status)) {
                    LOGGER.info("coverage phaseNativeGrant={}", actions.grant(mob, player, 1.0));
                }
                break;
            case "phase-open":
                if (guard == null || mob.getAttackTarget() != guard
                        || !"supported_inactive".equals(EncounterCoordinator.INSTANCE.inspect(mob, 0).status)) {
                    LOGGER.info("coverage phaseOpen=WAITING");
                    break;
                }
                guard.setPosition(mob.posX + 2.0, Y, mob.posZ);
                player.setInvisible(false);
                attack(mob, player, "hit");
                player.setPosition(mob.posX - 3.0, Y, mob.posZ);
                guard.setPosition(mob.posX + 6.0, Y, mob.posZ);
                break;
            case "phase-reject":
                if (!"tracked".equals(EncounterCoordinator.INSTANCE.inspect(mob, 0).status)
                        || mob.getAttackTarget() == player) {
                    LOGGER.info("coverage phaseReject=WAITING");
                    break;
                }
                player.capabilities.disableDamage = false;
                player.setInvisible(false);
                player.setPosition(mob.posX - 2.0, Y, mob.posZ);
                if (guard != null && actions.grant(mob, guard, 50) != ThreatActions.Result.APPLIED) {
                    throw new CommandException("Could not raise the incumbent above the pending taunter.");
                }
                mob.addTag("tw_j_reject_assignment");
                LOGGER.info("coverage phaseReject={}", actions.taunt(mob, player));
                mob.removeTag("tw_j_reject_assignment");
                break;
            case "withdraw-guard":
                if (guard != null) {
                    guard.setPosition(X + 13, Y, Z);
                }
                break;
            case "phase-player":
                player.setInvisible(false);
                player.capabilities.disableDamage = false;
                player.setPosition(mob.posX - 3.0, Y, mob.posZ);
                break;
            case "illegal":
                player.setInvisible(true);
                LOGGER.info("coverage phaseTaunt={}", actions.taunt(mob, player));
                player.setInvisible(false);
                break;
            case "grant":
                LOGGER.info("coverage grant={}", actions.grant(mob, player, 30));
                break;
            case "challenger":
                player.setInvisible(false);
                player.setPosition(mob.posX - 8, Y, mob.posZ + 2);
                LOGGER.info("coverage challengerGrant={}", actions.grant(mob, player, 30));
                break;
            case "approach":
                player.setPosition(mob.posX - 1.2, Y, mob.posZ);
                break;
            case "far":
                player.setPosition(mob.posX - 12, Y, mob.posZ);
                break;
            case "state":
                mob.setNoAI(true);
                break;
            case "restore-state":
                mob.setNoAI(false);
                break;
            case "competing-goal":
            case "defiled-native-goal":
                boolean probeSetter = "defiled-native-goal".equals(operation);
                if (probeSetter && (!mob.getServer().getFolderName().startsWith("TW-Campaign-L") || guard == null
                        || !guard.isEntityAlive() || mob.getAttackTarget() != player
                        || !"defiledlands:host".equals(String.valueOf(EntityList.getKey(mob))))) {
                    throw new CommandException("Expected a tracked Campaign L Host targeting the player.");
                }
                mob.tasks.addTask(0, new net.minecraft.entity.ai.EntityAIBase() {
                    {
                        setMutexBits(1);
                    }

                    @Override
                    public boolean shouldExecute() {
                        return true;
                    }
                });
                if (probeSetter) {
                    mob.setAttackTarget(guard);
                    if (mob.getAttackTarget() != guard) {
                        throw new CommandException("The native setter was blocked after a competing goal appeared.");
                    }
                }
                break;
            case "defiled-covered-opening":
                if (!mob.getServer().getFolderName().startsWith("TW-Campaign-L") || guard == null
                        || mob.getAttackTarget() != guard
                        || !"defiledlands:shambler_twisted".equals(String.valueOf(EntityList.getKey(mob)))) {
                    throw new CommandException("Expected a Campaign L Twisted Shambler targeting the guard.");
                }
                restorePlayer(player);
                attack(mob, player, "hit");
                BlockPos origin = mob.getPosition();
                for (BlockPos pos : BlockPos.getAllInBox(origin.add(-2, 0, -2), origin.add(-2, 2, 2))) {
                    world.setBlockState(pos, Blocks.STONE.getDefaultState(), 2);
                }
                player.setPosition(mob.posX - 5.0, mob.posY, mob.posZ);
                mob.addTag("tw_l_sight_probe");
                LOGGER.info("coverage defiledSight mob={} player={} visible={}", mob.getUniqueID(),
                        player.getUniqueID(), mob.canEntityBeSeen(player));
                break;
            case "zombie-bow":
                if (!mob.getServer().getFolderName().startsWith("TW-Campaign-L") || !(mob instanceof EntityZombie)
                        || guard == null || !guard.isEntityAlive() || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a Campaign L zombie targeting the player with a live guard.");
                }
                mob.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(net.minecraft.init.Items.BOW));
                mob.setAttackTarget(guard);
                if (mob.getAttackTarget() != guard) {
                    throw new CommandException("The native setter was blocked after changing weapon modes.");
                }
                guard.setPosition(mob.posX + 6, Y, mob.posZ);
                break;
            case "threatening-tnt":
                if (!mob.getServer().getFolderName().startsWith("TW-Campaign-L")) {
                    throw new CommandException("Expected a Campaign L world.");
                }
                // Leave the native retreat lane clear of the player currently in melee range.
                player.setPosition(mob.posX + 7, mob.posY, mob.posZ + 1);
                EntityTNTPrimed tnt = new EntityTNTPrimed(world, mob.posX, mob.posY, mob.posZ + 3, null);
                tnt.setFuse(400);
                tnt.setNoGravity(true);
                tnt.motionX = 0;
                tnt.motionY = 0;
                tnt.motionZ = 0;
                tnt.addTag(TAG);
                tnt.addTag("tw_l_tnt");
                if (!world.spawnEntity(tnt)) {
                    throw new CommandException("Could not spawn the native avoidance stimulus.");
                }
                mob.addTag("tw_l_avoidance_probe");
                LOGGER.info("coverage avoidanceStimulus x={} z={}", tnt.posX, tnt.posZ);
                break;
            case "remove-tnt":
                EntityTNTPrimed activeTnt = get(world, EntityTNTPrimed.class, "tw_l_tnt");
                if (activeTnt == null) {
                    throw new CommandException("The native avoidance stimulus is missing.");
                }
                activeTnt.setDead();
                break;
            case "unsupported":
                if (!(mob instanceof EntitySkeleton)) {
                    throw new CommandException("Expected a skeleton");
                }
                mob.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, ItemStack.EMPTY);
                break;
            case "participant":
                EntityCreature other = get(world, EntityCreature.class, "tw_i_attacker");
                if (other == null) {
                    Entity entity = EntityList.createEntityByIDFromName(new ResourceLocation("minecraft:zombie"), world);
                    if (!(entity instanceof EntityCreature)) {
                        throw new CommandException("Could not create excluded attacker");
                    }
                    other = (EntityCreature) entity;
                    other.getEntityData().setBoolean("SpecialMobsInit", true);
                    configureMob(other);
                    place(world, other, "tw_i_attacker", mob.posX - 1.5, mob.posZ);
                }
                float before = mob.getHealth();
                other.attackEntityAsMob(mob);
                LOGGER.info("coverage participant={} damage={} sourceStatus={}", other.getUniqueID(),
                        before - mob.getHealth(), EncounterCoordinator.INSTANCE.inspect(other, 0).status);
                break;
            case "withdraw-attacker":
                EntityCreature attacker = get(world, EntityCreature.class, "tw_i_attacker");
                if (attacker != null) {
                    attacker.setDead();
                }
                break;
            case "release":
                player.setPosition(30.5, Y, 30.5);
                player.capabilities.disableDamage = true;
                break;
            case "return":
                player.setPosition(mob.posX - 5, Y, mob.posZ + 2);
                player.capabilities.disableDamage = false;
                player.setInvisible(false);
                player.hurtResistantTime = 0;
                break;
            case "clear-guard":
                if (guard != null) {
                    guard.setDead();
                }
                break;
            case "native-guard":
                EntityIronGolem nextGuard = new EntityIronGolem(world);
                nextGuard.setNoAI(true);
                place(world, nextGuard, "tw_i_guard", mob.posX + 2, mob.posZ);
                LOGGER.info("coverage nativeGuard={} mob={}", nextGuard.getUniqueID(), mob.getUniqueID());
                break;
            default:
                throw new CommandException("Unknown coverage operation " + operation);
        }
        report(mob, world);
    }

    private static void restorePlayer(EntityPlayerMP player) {
        player.capabilities.disableDamage = false;
        player.setInvisible(false);
        player.hurtResistantTime = 0;
    }

    static void attack(EntityCreature mob, EntityPlayerMP player, String operation) {
        player.setPosition(mob.posX - 1.3, mob.posY, mob.posZ);
        player.resetCooldown();
        for (int tick = 0; tick < 30; tick++) {
            player.onUpdateEntity();
        }
        float hp = mob.getHealth();
        float absorption = mob.getAbsorptionAmount();
        player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, new ItemStack(net.minecraft.init.Items.STICK));
        player.attackTargetEntityWithCurrentItem(mob);
        LOGGER.info("coverage hit kind={} player={} mob={} hpBefore={} hpAfter={} absorptionBefore={} absorptionAfter={}",
                operation, player.getUniqueID(), mob.getUniqueID(), hp, mob.getHealth(), absorption, mob.getAbsorptionAmount());
    }

    static void report(EntityCreature mob, WorldServer world) {
        Inspection view = EncounterCoordinator.INSTANCE.inspect(mob, 16);
        EntityLivingBase guard = get(world, EntityIronGolem.class, "tw_i_guard");
        EntityLivingBase player = get(world, EntityPlayerMP.class, "tw_i_player");
        LOGGER.info("coverage state tick={} mob={} type={} status={} target={} guardHp={} playerHp={} x={} z={} weapon={} navigator={}",
                world.getMinecraftServer().getTickCounter(), mob.getUniqueID(), view.typeId, view.status, view.target,
                guard == null ? -1 : guard.getHealth(), player == null ? -1 : player.getHealth(), mob.posX, mob.posZ,
                mob.getHeldItemMainhand(), mob.getNavigator().getClass().getName());
        LOGGER.info("coverage behavior mob={} age={} y={} noAi={} goals={}", mob.getUniqueID(), mob.ticksExisted,
                mob.posY, mob.isAIDisabled(), mob.tasks.taskEntries.stream()
                        .map(entry -> entry.priority + ":" + entry.action.getClass().getName() + ":" + entry.action.getMutexBits())
                        .collect(java.util.stream.Collectors.joining(",")));
        if (world.getMinecraftServer().getFolderName().startsWith("TW-Campaign-L")) {
            LOGGER.info("coverage activeGoals mob={} goals={}", mob.getUniqueID(), mob.tasks.taskEntries.stream()
                    .filter(entry -> entry.using).map(entry -> entry.action.getClass().getName())
                    .collect(java.util.stream.Collectors.joining(",")));
            Path path = mob.getNavigator().getPath();
            PathPoint destination = path == null ? null : path.getFinalPathPoint();
            if (destination != null) {
                LOGGER.info("coverage path mob={} x={} z={}", mob.getUniqueID(), destination.x + 0.5, destination.z + 0.5);
            }
        }
        view.participants.forEach(participant -> LOGGER.info("coverage score mob={} participant={} score={} eligibility={}",
                mob.getUniqueID(), participant.id, participant.score, participant.eligibility));
    }

    private static String identity(Entity entity) {
        return entity == null ? "none" : entity.getUniqueID().toString();
    }

    private static <T extends Entity> T get(WorldServer world, Class<T> type, String tag) {
        return world.getEntitiesWithinAABB(type, new AxisAlignedBB(-64, 0, -64, 64, 256, 64),
                entity -> entity.isEntityAlive() && entity.getTags().contains(tag)).stream().findFirst().orElse(null);
    }
}
