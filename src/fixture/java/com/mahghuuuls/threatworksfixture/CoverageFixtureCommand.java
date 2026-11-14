package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.ThreatworksMod;
import com.mahghuuuls.threatworks.api.ThreatActions;
import com.mahghuuuls.threatworks.compat.SpecialMobsCompatibility;
import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import com.mahghuuuls.threatworks.server.EncounterCoordinator.Inspection;
import com.mahghuuuls.threatworks.threat.Encounter;
import com.mojang.authlib.GameProfile;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAILeapAtTarget;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

final class CoverageFixtureCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");
    static final String TAG = "tw_coverage";
    private static final double X = 12.5;
    private static final int Y = 20;
    private static final String WILDFIRE = "specialmobs:wildfireblaze";
    static final String CINDER = "specialmobs:cinderblaze";
    static final String EMBER = "specialmobs:emberblaze";
    static final String JOLT = "specialmobs:joltblaze";
    private static final String DREAD_THRALL = "iceandfire:dread_thrall";
    private static final String DREAD_KNIGHT = "iceandfire:dread_knight";
    static final String DREAD_BEAST = "iceandfire:dread_beast";
    static final String DREAD_GHOUL = "iceandfire:dread_ghoul";
    static final String DREAD_SCUTTLER = "iceandfire:dread_scuttler";
    static final String SCUTTLER_WALL_PROBE = "tw_l_dread_scuttler_wall";
    private static final int JOLT_FLOOR = 4;
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
                if (CINDER.equals(args[1])) {
                    adoptCinder(world);
                } else {
                    adopt(world, args[1]);
                }
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
        world.weatherEffects.stream().filter(entity -> entity.getTags().contains(TAG)).forEach(Entity::setDead);
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
        int height = id.startsWith("grimoireofgaia:") ? 64 : "defiledlands:shambler".equals(id) ? 60
                : JOLT.equals(id) ? JOLT_FLOOR : Y;
        arena(world, height);
        if (JOLT.equals(id)) {
            for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(-12, height - 1, 41), new BlockPos(40, height - 1, 54))) {
                world.setBlockState(pos, Blocks.STONE.getDefaultState(), 2);
            }
        }
        if (id.startsWith("dungeonmobs:")) {
            for (BlockPos roof : BlockPos.getAllInBox(new BlockPos(0, height + 6, 0), new BlockPos(28, height + 6, 28))) {
                world.setBlockState(roof, Blocks.STONE.getDefaultState(), 2);
            }
        }
        if (id.equals("specialmobs:webspider") || id.equals("specialmobs:webcavespider")) {
            for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(0, height, 0), new BlockPos(27, height + 3, 27))) {
                if (world.getBlockState(pos).getBlock() == Blocks.WEB) {
                    world.setBlockToAir(pos);
                }
            }
        }
        Entity entity = EntityList.createEntityByIDFromName(new ResourceLocation(id), world);
        if (entity instanceof EntityGhast) {
            seedGhast(world, (EntityGhast) entity, id, height);
            return;
        }
        if (entity instanceof EntitySlime) {
            seedSlime(world, (EntitySlime) entity, id, height);
            return;
        }
        if (!(entity instanceof EntityCreature)) {
            throw new CommandException("Unknown coverage fixture mob " + id);
        }
        EntityCreature mob = (EntityCreature) entity;
        mob.setPosition(X, height, Z);
        mob.onInitialSpawn(world.getDifficultyForLocation(mob.getPosition()), null);
        if ("mocreatures:horsemob".equals(id)) {
            setHorseFixtureForm(mob, 23);
        }
        mob.getRecursivePassengers().forEach(passenger -> passenger.addTag(TAG));
        if (mob.getRidingEntity() != null) {
            mob.getRidingEntity().addTag(TAG);
        }
        // Keep exact-ID fixtures separate from the natural replacement probe.
        if (id.startsWith("minecraft:")) {
            mob.getEntityData().setBoolean("SpecialMobsInit", true);
        }
        configureMob(mob);
        place(world, mob, "tw_i_mob", X, height, Z);
        if (DREAD_THRALL.equals(id) || DREAD_KNIGHT.equals(id) || animatedDread(id)) {
            dreadState(mob, "seed");
        }
        actors(world, mob, solo);
    }

    private static void seedGhast(WorldServer world, EntityGhast ghast, String id, int height) throws CommandException {
        double altitude = height + 10;
        ghast.setPosition(X, altitude, Z);
        ghast.onInitialSpawn(world.getDifficultyForLocation(ghast.getPosition()), null);
        ghast.enablePersistence();
        ghast.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(80);
        ghast.setHealth(80);
        EntityPlayerMP incumbent = fixturePlayer(world, id, "GhastIncumbent");
        EntityPlayerMP challenger = fixturePlayer(world, id, "GhastChallenger");
        incumbent.addTag("tw_l_tick_hurt_window");
        challenger.addTag("tw_l_tick_hurt_window");
        incumbent.setNoGravity(true);
        challenger.setNoGravity(true);
        challenger.capabilities.disableDamage = true;
        double spacing = meleeGhast(id) ? 5.0 : 9.0;
        place(world, incumbent, "tw_i_guard", X + spacing, altitude + 0.5, Z);
        place(world, challenger, "tw_i_player", X - spacing, altitude + 0.5, Z);
        if ("minecraft:ghast".equals(id)) {
            ghast.getEntityData().setBoolean("SpecialMobsInit", true);
        }
        place(world, ghast, "tw_i_mob", X, altitude, Z);
        LOGGER.info("coverage actors mob={} player={} guard={}", ghast.getUniqueID(),
                challenger.getUniqueID(), incumbent.getUniqueID());
        report(ghast, world);
    }

    private static boolean meleeGhast(String id) {
        return "specialmobs:babyghast".equals(id) || "specialmobs:fighterghast".equals(id)
                || "specialmobs:unholyghast".equals(id);
    }

    private static void seedSlime(WorldServer world, EntitySlime slime, String id, int height) throws CommandException {
        slime.setPosition(X, height, Z);
        slime.onInitialSpawn(world.getDifficultyForLocation(slime.getPosition()), null);
        setFixtureSlimeSize(slime, 4);
        slime.enablePersistence();
        slime.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(80);
        slime.setHealth(80);
        EntityPlayerMP incumbent = fixturePlayer(world, id, "SlimeIncumbent");
        EntityPlayerMP challenger = fixturePlayer(world, id, "SlimeChallenger");
        incumbent.addTag("tw_l_tick_player");
        challenger.addTag("tw_l_tick_player");
        challenger.capabilities.disableDamage = true;
        double startingRange = id.equals("specialmobs:stickylavaslime") || id.equals("specialmobs:caramelslime")
                ? 5 : 6;
        place(world, incumbent, "tw_i_guard", X + startingRange, height, Z);
        place(world, challenger, "tw_i_player", X - 7, height, Z);
        if (id.startsWith("minecraft:")) {
            slime.getEntityData().setBoolean("SpecialMobsInit", true);
        }
        place(world, slime, "tw_i_mob", X, height, Z);
        LOGGER.info("coverage actors mob={} player={} guard={}", slime.getUniqueID(),
                challenger.getUniqueID(), incumbent.getUniqueID());
        report(slime, world);
    }

    private static void setFixtureSlimeSize(EntitySlime slime, int size) {
        NBTTagCompound data = slime.writeToNBT(new NBTTagCompound());
        data.setInteger("Size", size - 1);
        slime.readFromNBT(data);
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
        if ("specialmobs:jumpingcreeper".equals(String.valueOf(EntityList.getKey(mob)))) {
            mob.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        }
        mob.setHealth(80);
    }

    private static void adoptCinder(WorldServer world) throws CommandException {
        EntityCreature parent = get(world, EntityCreature.class, "tw_i_mob");
        EntityIronGolem guard = get(world, EntityIronGolem.class, "tw_i_guard");
        EntityPlayerMP player = get(world, EntityPlayerMP.class, "tw_i_player");
        if (parent == null || !WILDFIRE.equals(String.valueOf(EntityList.getKey(parent))) || guard == null || player == null) {
            throw new CommandException("Expected a Wildfire and its native Cinder offspring.");
        }
        EntityCreature child = get(world, EntityCreature.class, ThreatworksFixture.CINDER_OFFSPRING);
        if (child == null || child.getAttackTarget() != guard) {
            throw new CommandException("Expected a live Cinder with the inherited guard target.");
        }
        parent.setDead();
        world.getEntities(Entity.class, entity -> (entity != child && entity.getTags().contains(ThreatworksFixture.CINDER_OFFSPRING))
                || (entity instanceof EntityFireball && ((EntityFireball) entity).shootingEntity == parent))
                .forEach(Entity::setDead);
        player.capabilities.disableDamage = true;
        player.addTag("tw_l_tick_hurt_window");
        child.addTag("tw_i_mob");
        LOGGER.info("coverage cinderAdopt mob={} parent={} guard={} tick={}", child.getUniqueID(),
                parent.getUniqueID(), guard.getUniqueID(), world.getTotalWorldTime());
        report(child, world);
    }

    private static void actors(WorldServer world, EntityCreature mob, boolean solo) throws CommandException {
        String id = String.valueOf(EntityList.getKey(mob));
        boolean cqrGoblinFaction = isCqrGoblinFaction(mob);
        boolean spectre = "charm:spectre".equals(id);
        boolean twoPlayers = cqrGoblinFaction || "cqrepoured:gremlin".equals(id) || spectre
                || mob instanceof net.minecraft.entity.monster.EntityWitch;
        if (!solo && twoPlayers) {
            EntityPlayerMP incumbent = fixturePlayer(world, id, "MeleeIncumbent");
            if (cqrGoblinFaction) {
                setCqrHostileReputation(mob, incumbent);
            }
            incumbent.addTag("tw_l_tick_hurt_window");
            double spacing = spectre ? 7 : mob instanceof net.minecraft.entity.monster.EntityWitch ? 6 : 2;
            place(world, incumbent, "tw_i_guard", X + spacing, mob.posY, Z);
        } else if (!solo) {
            EntityIronGolem guard = new EntityIronGolem(world);
            guard.setNoAI(true);
            if ("mowziesmobs:foliaath".equals(String.valueOf(EntityList.getKey(mob)))) {
                guard.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1000);
                guard.setHealth(1000);
            }
            double offset = mob instanceof EntityCreeper || mob instanceof EntityBlaze || mob instanceof AbstractSkeleton
                    && mob.getHeldItemMainhand().getItem() instanceof net.minecraft.item.ItemBow ? 9 : 2;
            if ("specialmobs:jumpingcreeper".equals(String.valueOf(EntityList.getKey(mob)))) {
                offset = 5.5;
            }
            double guardY = mob.posY;
            if (mob instanceof EntityBlaze) {
                guardY += 5;
                world.setBlockState(new BlockPos(X + offset, guardY - 1, Z), Blocks.STONE.getDefaultState(), 2);
            }
            place(world, guard, "tw_i_guard", X + offset, guardY, Z);
        }
        EntityPlayerMP player = fixturePlayer(world, id, "CampaignI");
        player.setSneaking(true);
        player.setInvisible(true);
        if (twoPlayers) {
            player.capabilities.disableDamage = true;
            player.addTag("tw_l_tick_hurt_window");
        }
        if (cqrGoblinFaction) {
            setCqrHostileReputation(mob, player);
        }
        if (id.startsWith("grimoireofgaia:")) {
            player.addTag("tw_l_gaia_effect");
        }
        place(world, player, "tw_i_player", X, mob.posY, Z + (twoPlayers ? 27 : 13));
        LOGGER.info("coverage actors mob={} player={} guard={}", mob.getUniqueID(), player.getUniqueID(),
                identity(get(world, EntityLivingBase.class, "tw_i_guard")));
        report(mob, world);
    }

    private static EntityPlayerMP fixturePlayer(WorldServer world, String id, String name) {
        FakePlayer player = new FakePlayer(world, new GameProfile(UUID.randomUUID(), name)) {
            @Override
            public void onUpdate() {
                if (getTags().contains("tw_l_tick_player")) {
                    onUpdateEntity();
                    if (hurtResistantTime > 0) {
                        --hurtResistantTime;
                    }
                    return;
                }
                // FakePlayer skips the countdown a connected player gets in EntityPlayerMP.
                if ((WILDFIRE.equals(id) || DREAD_GHOUL.equals(id) || meleeBlazeName(id) != null
                        || getTags().contains("tw_l_tick_hurt_window"))
                        && hurtResistantTime > 0) {
                    --hurtResistantTime;
                }
            }

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
        return player;
    }

    private static boolean isCqrGoblinFaction(EntityCreature mob) {
        String id = String.valueOf(EntityList.getKey(mob));
        return "cqrepoured:ogre".equals(id) || "cqrepoured:orc".equals(id);
    }

    private static void setCqrHostileReputation(EntityCreature mob, EntityPlayerMP player) throws CommandException {
        try {
            ClassLoader loader = mob.getClass().getClassLoader();
            Class<?> registryType = loader.loadClass("team.cqr.cqrepoured.faction.FactionRegistry");
            Class<?> factionType = loader.loadClass("team.cqr.cqrepoured.faction.Faction");
            Class<?> roughType = loader.loadClass("team.cqr.cqrepoured.faction.EReputationState$EReputationStateRough");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Object registry = lookup.findStatic(registryType, "instance", MethodType.methodType(registryType, Entity.class))
                    .invoke(mob);
            Object faction = lookup.findVirtual(registryType, "getFactionOf", MethodType.methodType(factionType, Entity.class))
                    .invoke(registry, mob);
            lookup.findVirtual(registryType, "changeReputationTo",
                            MethodType.methodType(void.class, EntityPlayerMP.class, int.class, factionType))
                    .invoke(registry, player, -500, faction);
            Object result = lookup.findVirtual(registryType, "getReputationOf", MethodType.methodType(roughType, UUID.class, factionType))
                    .invoke(registry, player.getUniqueID(), faction);
            if (!"ENEMY".equals(String.valueOf(result))) {
                throw new CommandException("The CQR player must have native hostile reputation.");
            }
            LOGGER.info("coverage cqrReputation mob={} player={} reputation={}",
                    mob.getUniqueID(), player.getUniqueID(), result);
        } catch (CommandException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            LOGGER.warn("Native reputation setup failed for {}.", EntityList.getKey(mob), failure);
            throw new CommandException("Could not set the disposable player's native CQR reputation.");
        }
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

    private void act(WorldServer world, String operation) throws CommandException {
        EntityLiving living = get(world, EntityLiving.class, "tw_i_mob");
        EntityPlayerMP player = get(world, EntityPlayerMP.class, "tw_i_player");
        EntityLivingBase guard = get(world, EntityLivingBase.class, "tw_i_guard");
        if (living == null || player == null) {
            throw new CommandException("Coverage actors are missing (mob=" + (living != null)
                    + ", player=" + (player != null) + ", guard=" + (guard != null) + ").");
        }
        if (living instanceof EntityGhast) {
            actGhast(world, (EntityGhast) living, player, guard, operation);
            return;
        }
        if (living instanceof EntitySlime) {
            actSlime(world, (EntitySlime) living, player, guard, operation);
            return;
        }
        EntityCreature mob = (EntityCreature) living;
        ThreatActions actions = ThreatworksMod.threatActions();
        switch (operation) {
            case "read":
                break;
            case "dread-state":
                dreadState(mob, "read");
                break;
            case "dread-beast-emergence":
                if (!animatedDread(String.valueOf(EntityList.getKey(mob)))) {
                    throw new CommandException("Expected an animated Dread mob.");
                }
                restorePlayer(player);
                player.setPosition(mob.posX - 2.5, mob.posY, mob.posZ);
                dreadState(mob, "emergence-probe");
                LOGGER.info("coverage dreadBeastEmergence mob={} player={} result={}",
                        mob.getUniqueID(), player.getUniqueID(), actions.taunt(mob, player));
                break;
            case "dread-beast-range":
                if (!animatedDread(String.valueOf(EntityList.getKey(mob))) || !mob.onGround || guard == null) {
                    throw new CommandException("Expected a grounded animated Dread mob and a live guard.");
                }
                restorePlayer(player);
                // The gap isolates the immediate bite rule from the ground pursuit check.
                mob.setPosition(X, Y, Z);
                guard.setPosition(X - 2, Y, Z);
                ThreatActions.Result guardTaunt = actions.taunt(mob, guard);
                if (guardTaunt != ThreatActions.Result.APPLIED || mob.getAttackTarget() != guard) {
                    throw new CommandException("Could not establish the guard as incumbent for the bite range probe.");
                }
                for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(13, Y - 1, -12), new BlockPos(40, Y - 1, 40))) {
                    world.setBlockToAir(pos);
                }
                player.setPosition(X + 4.0, Y, Z);
                ThreatActions.Result edge = actions.taunt(mob, player);
                boolean edgeSight = mob.canEntityBeSeen(player);
                player.setPosition(X + 3.75, Y, Z);
                BlockPos wall = new BlockPos(X + 2, Y, Z);
                world.setBlockState(wall, Blocks.STONE.getDefaultState(), 2);
                world.setBlockState(wall.up(), Blocks.STONE.getDefaultState(), 2);
                boolean blockedSight = world.rayTraceBlocks(mob.getPositionEyes(1.0F), player.getPositionEyes(1.0F),
                        false, true, false) == null;
                ThreatActions.Result blockedBite = actions.taunt(mob, player);
                world.setBlockToAir(wall);
                world.setBlockToAir(wall.up());
                ThreatActions.Result inside = actions.taunt(mob, player);
                LOGGER.info("coverage dreadBeastRange mob={} player={} edge=4.0 edgeSight={} edgeResult={} blockedSight={} blockedResult={} inside={} insideResult={}",
                        mob.getUniqueID(), player.getUniqueID(), edgeSight, edge, blockedSight, blockedBite, mob.getDistance(player), inside);
                break;
            case "dread-scuttler-wall":
                if (!DREAD_SCUTTLER.equals(String.valueOf(EntityList.getKey(mob))) || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a tracked Dread Scuttler targeting the player.");
                }
                mob.setPosition(X, Y, Z);
                mob.getNavigator().clearPath();
                for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(14, Y, 9), new BlockPos(14, Y + 3, 16))) {
                    world.setBlockState(pos, Blocks.STONE.getDefaultState(), 2);
                }
                player.setPosition(X + 2, Y, Z + 5);
                // Give the mob one horizontal movement toward the wall; its own travel handles contact and climbing.
                mob.motionX = 1.5;
                mob.motionY = 0;
                mob.motionZ = 0;
                mob.addTag(SCUTTLER_WALL_PROBE);
                LOGGER.info("coverage dreadScuttlerWall mob={} player={} y={} tick={}",
                        mob.getUniqueID(), player.getUniqueID(), mob.posY, world.getTotalWorldTime());
                break;
            case "dread-command":
                if (guard == null || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a Dread mob targeting the challenger.");
                }
                Entity commander = EntityList.createEntityByIDFromName(new ResourceLocation("iceandfire:dread_lich"), world);
                if (!(commander instanceof EntityCreature)) {
                    throw new CommandException("Dread Lich was unavailable.");
                }
                EntityCreature lich = (EntityCreature) commander;
                lich.setNoAI(true);
                place(world, lich, "tw_l_dread_commander", mob.posX, mob.posY, mob.posZ + 11);
                lich.setAttackTarget(guard);
                try {
                    mob.getClass().getMethod("setCommanderId", UUID.class).invoke(mob, lich.getUniqueID());
                } catch (ReflectiveOperationException failure) {
                    throw new CommandException("The native Dread commander could not be assigned.");
                }
                mob.addTag("tw_l_dread_command_probe");
                dreadState(mob, "command");
                break;
            case "dread-petrify":
                if (guard == null || mob.getAttackTarget() != player || !guard.isEntityAlive()) {
                    throw new CommandException("Expected a live Dread combat guard and selected challenger.");
                }
                try {
                    Class<?> handler = Class.forName("net.ilexiconn.llibrary.server.entity.EntityPropertiesHandler");
                    Class<?> stone = Class.forName("com.github.alexthe666.iceandfire.entity.StoneEntityProperties");
                    Object properties = handler.getMethod("getProperties", Entity.class, Class.class)
                            .invoke(handler.getField("INSTANCE").get(null), guard, stone);
                    stone.getField("isStone").setBoolean(properties, true);
                    LOGGER.info("coverage dreadStone mob={} guard={} alive={} allied={} stone={}", mob.getUniqueID(),
                            guard.getUniqueID(), guard.isEntityAlive(), mob.isOnSameTeam(guard),
                            stone.getField("isStone").getBoolean(properties));
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    throw new CommandException("Could not mark the live combat guard as petrified.");
                }
                break;
            case "dread-horse":
                if (!DREAD_KNIGHT.equals(String.valueOf(EntityList.getKey(mob))) || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a Dread Knight targeting the challenger.");
                }
                EntityHorse dreadHorse = new EntityHorse(world);
                dreadHorse.setNoAI(true);
                place(world, dreadHorse, "tw_l_dread_horse", mob.posX, mob.posY, mob.posZ + 7);
                LOGGER.info("coverage dreadHorse mob={} horse={} tamed={} distance={}", mob.getUniqueID(),
                        dreadHorse.getUniqueID(), dreadHorse.isTame(), mob.getDistance(dreadHorse));
                mob.addTag("tw_l_dread_mount_probe");
                break;
            case "silverfish-effects":
                if (!mob.getServer().getFolderName().startsWith("TW-Campaign-L") || guard == null) {
                    throw new CommandException("Expected Campaign L combat actors.");
                }
                LOGGER.info("coverage silverfishEffects mob={} guard={} guardBlind={} guardPoison={} player={} playerBlind={} playerPoison={}",
                        mob.getUniqueID(), guard.getUniqueID(), guard.isPotionActive(MobEffects.BLINDNESS),
                        guard.isPotionActive(MobEffects.POISON), player.getUniqueID(), player.isPotionActive(MobEffects.BLINDNESS),
                        player.isPotionActive(MobEffects.POISON));
                break;
            case "spider-effects":
                LOGGER.info("coverage spiderEffects mob={} player={} slow={} weak={} poison={} playerJump={} mobJump={}",
                        mob.getUniqueID(), player.getUniqueID(), player.isPotionActive(MobEffects.SLOWNESS),
                        player.isPotionActive(MobEffects.WEAKNESS), player.isPotionActive(MobEffects.POISON),
                        player.isPotionActive(MobEffects.JUMP_BOOST), mob.isPotionActive(MobEffects.JUMP_BOOST));
                break;
            case "witch-spider-prepare":
                player.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, 1200));
                LOGGER.info("coverage witchSpiderPrepare mob={} player={} playerJump={} mobJump={}",
                        mob.getUniqueID(), player.getUniqueID(), player.isPotionActive(MobEffects.JUMP_BOOST),
                        mob.isPotionActive(MobEffects.JUMP_BOOST));
                break;
            case "hungry-spider-prepare":
                player.inventory.setInventorySlotContents(1, new ItemStack(Items.APPLE));
                LOGGER.info("coverage hungrySpiderPrepare mob={} player={} apples={}", mob.getUniqueID(),
                        player.getUniqueID(), player.inventory.getStackInSlot(1).getCount());
                break;
            case "hungry-spider-effect":
                LOGGER.info("coverage hungrySpiderEffect mob={} player={} apples={}", mob.getUniqueID(),
                        player.getUniqueID(), player.inventory.getStackInSlot(1).getCount());
                break;
            case "web-spider-effect":
                fixture.reportSpiderWeb(mob, player);
                break;
            case "mother-spider-finish":
                mob.setHealth(3);
                mob.hurtResistantTime = 0;
                attack(mob, player, "mother-spider-finish");
                String baby = String.valueOf(EntityList.getKey(mob)).equals("specialmobs:mothercavespider")
                        ? "specialmobs:babycavespider" : "specialmobs:babyspider";
                world.getEntitiesWithinAABB(EntityCreature.class, mob.getEntityBoundingBox().grow(2),
                        child -> baby.equals(String.valueOf(EntityList.getKey(child))) && child.ticksExisted == 0)
                        .forEach(child -> {
                            child.addTag(TAG);
                            child.getRecursivePassengers().forEach(passenger -> passenger.addTag(TAG));
                        });
                LOGGER.info("coverage motherSpiderFinish mob={} player={} dead={}", mob.getUniqueID(),
                        player.getUniqueID(), !mob.isEntityAlive());
                break;
            case "spider-daylight-target":
                if (!(mob instanceof EntitySpider) || mob.getBrightness() < 0.5F || guard == null
                        || !guard.isEntityAlive()) {
                    throw new CommandException("Expected a spider in daylight with a live guard.");
                }
                EntityLivingBase previous = mob.getAttackTarget();
                EntityLivingBase next = previous == guard ? player : guard;
                mob.setAttackTarget(next);
                if (mob.getAttackTarget() != next) {
                    throw new CommandException("The native setter was blocked in daylight.");
                }
                LOGGER.info("coverage spiderDaylightTarget mob={} previous={} next={}", mob.getUniqueID(),
                        previous == null ? null : previous.getUniqueID(), next.getUniqueID());
                break;
            case "dungeon-hook-prepare":
                player.setItemStackToSlot(EntityEquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                LOGGER.info("coverage dungeonHookPrepare mob={} player={} bootsDamage={}", mob.getUniqueID(),
                        player.getUniqueID(), player.getItemStackFromSlot(EntityEquipmentSlot.FEET).getItemDamage());
                break;
            case "dungeon-effects":
                PotionEffect slowness = player.getActivePotionEffect(MobEffects.SLOWNESS);
                LOGGER.info("coverage dungeonEffects mob={} player={} slowness={} bootsDamage={} nausea={} roof={}",
                        mob.getUniqueID(), player.getUniqueID(), slowness == null ? -1 : slowness.getAmplifier(),
                        player.getItemStackFromSlot(EntityEquipmentSlot.FEET).getItemDamage(),
                        player.isPotionActive(MobEffects.NAUSEA), !world.canSeeSky(mob.getPosition()));
                break;
            case "fish-spell-handoff":
                if (!"mod_lavacow:foglet".equals(String.valueOf(EntityList.getKey(mob)))
                        || guard == null || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a tracked Foglet targeting the player.");
                }
                boolean casting = mob.tasks.taskEntries.stream().anyMatch(entry -> entry.using
                        && entry.action.getClass().getName().endsWith("EntityFoglet$AICastingApell"));
                if (casting) {
                    fishNativeHandoff(actions, mob, player, guard, "spell");
                }
                break;
            case "fish-hanging-handoff":
                if (!"mod_lavacow:isnachi".equals(String.valueOf(EntityList.getKey(mob)))
                        || guard == null || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a tracked Isnachi targeting the player.");
                }
                try {
                    if (mob.tasks.taskEntries.stream().anyMatch(entry -> entry.using
                            && entry.action.getClass().getName().endsWith("$AIClimbimgTree"))) {
                        throw new CommandException("Hanging probe requires the climbing goal to be inactive.");
                    }
                    mob.getClass().getMethod("setIsHanging", boolean.class).invoke(mob, true);
                    mob.setNoGravity(true);
                    if (!(Boolean) mob.getClass().getMethod("getIsHanging").invoke(mob)) {
                        throw new CommandException("The provider did not enter its hanging state.");
                    }
                } catch (ReflectiveOperationException error) {
                    throw new CommandException("Could not set the provider's native hanging state.");
                }
                fishNativeHandoff(actions, mob, player, guard, "hanging");
                break;
            case "fish-tamed-handoff":
                String fishTamedId = String.valueOf(EntityList.getKey(mob));
                if (!("mod_lavacow:mummy".equals(fishTamedId) || "mod_lavacow:amber_scarab".equals(fishTamedId))
                        || !(mob instanceof EntityTameable)
                        || guard == null || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a tracked wild Fish mob targeting the player.");
                }
                EntityTameable tamedMob = (EntityTameable) mob;
                tamedMob.setTamed(true);
                tamedMob.setOwnerId(player.getUniqueID());
                fishNativeHandoff(actions, mob, player, guard, "tamed");
                break;
            case "fish-leap-handoff":
                if (!"mod_lavacow:amber_scarab".equals(String.valueOf(EntityList.getKey(mob)))
                        || guard == null || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a tracked wild Amber Scarab targeting the player.");
                }
                if (mob.tasks.taskEntries.stream().anyMatch(task -> task.using
                        && task.action.getClass() == net.minecraft.entity.ai.EntityAILeapAtTarget.class)) {
                    fishNativeHandoff(actions, mob, player, guard, "leap");
                } else {
                    player.setPosition(mob.posX - 3.0, mob.posY, mob.posZ);
                }
                break;
            case "fish-breeding-item-handoff":
                if (!"mod_lavacow:weta".equals(String.valueOf(EntityList.getKey(mob))) || guard == null) {
                    throw new CommandException("Expected a tracked wild Weta and its guard.");
                }
                if (mob.tasks.taskEntries.stream().anyMatch(task -> task.using && task.action.getClass().getName()
                        .equals("fathertoast.specialai.ai.grief.EntityAIEatBreedingItem"))) {
                    player.setNoGravity(false);
                    guard.setNoGravity(false);
                    player.setPosition(mob.posX - 1.3, mob.posY, mob.posZ);
                    guard.setPosition(mob.posX + 1.3, mob.posY, mob.posZ);
                    restorePlayer(player);
                    if (mob.getAttackTarget() != player) {
                        mob.setAttackTarget(player);
                    }
                    fishNativeHandoff(actions, mob, player, guard, "breeding-item");
                } else if (!mob.getTags().contains("tw_l_weta_food")) {
                    ResourceLocation food = new ResourceLocation("mod_lavacow:canepork");
                    ItemStack bait = new ItemStack(ForgeRegistries.ITEMS.getValue(food));
                    if (bait.isEmpty() || !((EntityTameable) mob).isBreedingItem(bait)) {
                        throw new CommandException("The native Weta rejected the fixture food.");
                    }
                    EntityItem droppedFood = new EntityItem(world, mob.posX + 5, mob.posY, mob.posZ, bait);
                    droppedFood.motionX = 0;
                    droppedFood.motionY = 0;
                    droppedFood.motionZ = 0;
                    place(world, droppedFood, "tw_l_weta_food", mob.posX + 5, mob.posY, mob.posZ);
                    mob.addTag("tw_l_weta_food");
                    // Leave both scored participants alive but outside engagement while the food goal starts.
                    guard.setNoGravity(true);
                    player.setNoGravity(true);
                    guard.setPosition(mob.posX, mob.posY + 40, mob.posZ);
                    player.setPosition(mob.posX, mob.posY + 40, mob.posZ + 4);
                    player.capabilities.disableDamage = true;
                    mob.getNavigator().clearPath();
                    world.getGameRules().setOrCreateGameRule("mobGriefing", "true");
                    LOGGER.info("coverage fishDroppedBreedingItem mob={} item={} id={}", mob.getUniqueID(), food,
                            droppedFood.getUniqueID());
                }
                break;
            case "fish-rising-handoff":
                if (!"mod_lavacow:unburied".equals(String.valueOf(EntityList.getKey(mob)))
                        || guard == null || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a tracked wild Unburied targeting the player.");
                }
                try {
                    Class<?> zombieBase = mob.getClass().getSuperclass();
                    Field risingTicks = zombieBase.getDeclaredField("risingTicks");
                    risingTicks.setAccessible(true);
                    risingTicks.setInt(mob, 20);
                    if (!(Boolean) mob.getClass().getMethod("isRising").invoke(mob)) {
                        throw new CommandException("The provider did not enter its rising state.");
                    }
                } catch (ReflectiveOperationException error) {
                    throw new CommandException("Could not observe the provider's native rising state.");
                }
                fishNativeHandoff(actions, mob, player, guard, "rising");
                break;
            case "gaia-vertical-impact":
                LOGGER.info("coverage gaiaVertical mob={} player={} playerY={} mobY={} velocityY={}",
                        mob.getUniqueID(), player.getUniqueID(), player.posY, mob.posY, player.motionY);
                break;
            case "quark-melee-effects": {
                String quarkId = String.valueOf(EntityList.getKey(mob));
                if (!("quark:wraith".equals(quarkId) || "quark:dweller".equals(quarkId))) {
                    throw new CommandException("Expected the pinned Quark Wraith or Dweller.");
                }
                PotionEffect slow = player.getActivePotionEffect(MobEffects.SLOWNESS);
                LOGGER.info("coverage quarkMeleeEffects mob={} player={} invisible={} slowAmplifier={}",
                        mob.getUniqueID(), player.getUniqueID(), mob.isPotionActive(MobEffects.INVISIBILITY),
                        slow == null ? -1 : slow.getAmplifier());
                break;
            }
            case "quark-forgotten-melee":
                if (!"quark:forgotten".equals(String.valueOf(EntityList.getKey(mob))) || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected the tracked Quark Forgotten targeting the player.");
                }
                // Match the connected player's normal damage-immunity countdown in this server-only fixture.
                player.addTag("tw_l_tick_hurt_window");
                player.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 100));
                player.setPosition(mob.posX - 1.3, mob.posY, mob.posZ);
                for (EntityArrow arrow : world.getEntities(EntityArrow.class, arrow -> arrow.shootingEntity == mob)) {
                    arrow.setDead();
                }
                LOGGER.info("coverage quarkForgottenMelee mob={} player={} blindnessStimulus={}",
                        mob.getUniqueID(), player.getUniqueID(), player.isPotionActive(MobEffects.BLINDNESS));
                break;
            case "gaia-swim-handoff":
                String gaiaId = String.valueOf(EntityList.getKey(mob));
                if (!(gaiaId.equals("grimoireofgaia:naga") || gaiaId.equals("grimoireofgaia:sharko"))
                        || guard == null || mob.getAttackTarget() != player || mob.isInWater()) {
                    throw new CommandException("Expected a tracked dry Gaia swimmer targeting the player.");
                }
                for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(17, 64, 16), new BlockPos(19, 65, 18))) {
                    world.setBlockState(pos, Blocks.WATER.getDefaultState(), 2);
                }
                mob.setPosition(18.5, 64, 17.5);
                mob.handleWaterMovement();
                if (!mob.isInWater()) {
                    throw new CommandException("Expected the Gaia swimmer to be in water.");
                }
                LOGGER.info("coverage gaiaSwimHandoff mob={} former={} requested={} water=true taunt={}",
                        mob.getUniqueID(), player.getUniqueID(), guard.getUniqueID(), actions.taunt(mob, player));
                mob.setAttackTarget(guard);
                if (mob.getAttackTarget() != guard) {
                    throw new CommandException("The Gaia swimmer did not accept the native target.");
                }
                break;
            case "gaia-swim-exit":
                for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(17, 64, 16), new BlockPos(19, 65, 18))) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
                }
                mob.setPosition(X, 64, Z);
                mob.handleWaterMovement();
                LOGGER.info("coverage gaiaSwimExit mob={} water={}", mob.getUniqueID(), mob.isInWater());
                break;
            case "gaia-spriggan-visibility":
                if (!"grimoireofgaia:spriggan".equals(String.valueOf(EntityList.getKey(mob)))) {
                    throw new CommandException("Expected Gaia's Spriggan.");
                }
                LOGGER.info("coverage gaiaSpriggan mob={} player={} invisible={} distance={}", mob.getUniqueID(),
                        player.getUniqueID(), mob.isPotionActive(MobEffects.INVISIBILITY), mob.getDistance(player));
                break;
            case "gaia-matango-spawn-stimulus":
                ResourceLocation matangoId = new ResourceLocation("grimoireofgaia:matango");
                if (!matangoId.equals(EntityList.getKey(mob)) || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a tracked Matango targeting the player.");
                }
                Entity nativeMatango = EntityList.createEntityByIDFromName(matangoId, world);
                if (!(nativeMatango instanceof EntityLivingBase)) {
                    throw new CommandException("Gaia's Matango is missing.");
                }
                float naturalMax = ((EntityLivingBase) nativeMatango).getMaxHealth();
                mob.setHealth(naturalMax * 0.6F);
                LOGGER.info("coverage gaiaMatangoSpawnStimulus mob={} hp={} naturalMax={}", mob.getUniqueID(),
                        mob.getHealth(), naturalMax);
                break;
            case "gaia-ant-wander":
                if (guard == null || mob.getAttackTarget() != player
                        || !"grimoireofgaia:ant".equals(String.valueOf(EntityList.getKey(mob)))) {
                    throw new CommandException("Expected a tracked Ant targeting the player.");
                }
                EntityAITaskEntry wanderEntry = mob.tasks.taskEntries.stream()
                        .filter(entry -> entry.priority == 2 && entry.action.getClass() == EntityAIWander.class)
                        .findFirst().orElseThrow(() -> new CommandException("Ant wander goal was missing."));
                EntityAIWander wander = (EntityAIWander) wanderEntry.action;
                boolean pathStarted = false;
                for (int attempt = 0; attempt < 8 && !pathStarted; attempt++) {
                    wander.makeUpdate();
                    if (wander.shouldExecute()) {
                        wander.startExecuting();
                        pathStarted = !mob.getNavigator().noPath();
                    }
                }
                if (!pathStarted) {
                    throw new CommandException("Ant did not take the native wander path.");
                }
                // Stimulate the same-tick setter boundary with the exact goal running.
                wanderEntry.using = true;
                try {
                    mob.setAttackTarget(guard);
                    if (mob.getAttackTarget() != guard) {
                        throw new CommandException("The native setter was blocked while the Ant wandered.");
                    }
                    LOGGER.info("coverage antWanderTaunt={}", actions.taunt(mob, player));
                    report(mob, world);
                } finally {
                    wanderEntry.using = false;
                }
                return;
            case "umber-aura-prepare":
                if (guard == null || mob.getAttackTarget() != guard) {
                    throw new CommandException("Expected a native guard target for the Umber Hulk aura probe.");
                }
                player.capabilities.disableDamage = false;
                player.removePotionEffect(MobEffects.NAUSEA);
                player.setPosition(mob.posX, mob.posY, mob.posZ + 4);
                LOGGER.info("coverage umberAura mob={} player={} guard={} nausea={} playerHp={}",
                        mob.getUniqueID(), player.getUniqueID(), guard.getUniqueID(),
                        player.isPotionActive(MobEffects.NAUSEA), player.getHealth());
                break;
            case "dungeon-ghoul-mount":
                if (guard == null || mob.getAttackTarget() != player
                        || !"dungeonmobs:dmghoul".equals(String.valueOf(EntityList.getKey(mob)))) {
                    throw new CommandException("Expected a tracked Ghoul targeting the player.");
                }
                net.minecraft.entity.passive.EntityChicken chicken = new net.minecraft.entity.passive.EntityChicken(world);
                chicken.enablePersistence();
                place(world, chicken, "tw_l_ghoul_mount", mob.posX, mob.posY, mob.posZ);
                if (!mob.startRiding(chicken, true)) {
                    throw new CommandException("Could not mount the Ghoul.");
                }
                mob.setAttackTarget(guard);
                if (mob.getAttackTarget() != guard) {
                    throw new CommandException("The native setter was blocked after the Ghoul mounted.");
                }
                break;
            case "gaia-dullahan-mount":
                if (guard == null || mob.getAttackTarget() != player
                        || !"grimoireofgaia:dullahan".equals(String.valueOf(EntityList.getKey(mob)))) {
                    throw new CommandException("Expected a tracked Dullahan targeting the player.");
                }
                Entity horse = EntityList.createEntityByIDFromName(new ResourceLocation("grimoireofgaia:horse"), world);
                if (horse == null) {
                    throw new CommandException("Gaia Horse was unavailable.");
                }
                place(world, horse, "tw_l_gaia_mount", mob.posX, mob.posY, mob.posZ);
                if (!mob.startRiding(horse, true)) {
                    throw new CommandException("Could not mount the Dullahan.");
                }
                mob.setAttackTarget(guard);
                if (mob.getAttackTarget() != guard) {
                    throw new CommandException("The native setter was blocked after the Dullahan mounted.");
                }
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
            case "creeper-jump-retaliation":
                if (guard == null) {
                    throw new CommandException("Missing native retaliation guard.");
                }
                player.setPosition(X, mob.posY, Z + 13);
                player.capabilities.disableDamage = true;
                String retaliatingId = String.valueOf(EntityList.getKey(mob));
                if (JOLT.equals(retaliatingId)) {
                    guard.setPosition(mob.posX, JOLT_FLOOR, mob.posZ + 25);
                    // Stimulate the native hurt-by-target goal without triggering Jolt's reactive teleport.
                    mob.setRevengeTarget(guard);
                } else if ("specialmobs:jumpingcreeper".equals(retaliatingId)) {
                    // The golem's normal attack adds lift even when its victim resists knockback.
                    mob.attackEntityFrom(DamageSource.causeMobDamage(guard), 1.0F);
                    if ("creeper-jump-retaliation".equals(operation)) {
                        mob.addTag("tw_l_jumping_pursuit");
                    }
                } else if (mob instanceof net.minecraft.entity.passive.EntityTameable
                        && retaliatingId.startsWith("mod_lavacow:")) {
                    // Exercise native retaliation without leaving the opening in the guard's hurt window.
                    mob.setRevengeTarget(guard);
                } else {
                    guard.attackEntityAsMob(mob);
                }
                break;
            case "legal-player":
                restorePlayer(player);
                break;
            case "ground-player-opening":
                if (!(guard instanceof EntityPlayerMP) || mob.getAttackTarget() != guard) {
                    throw new CommandException("Expected the native player incumbent.");
                }
                restorePlayer(player);
                attack(mob, player, "hit");
                player.setPosition(mob.posX - 3.5, mob.posY, mob.posZ);
                guard.setPosition(mob.posX + 3.2, mob.posY, mob.posZ);
                break;
            case "moc-flying-handoff":
                if (guard == null || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a tracked ground HorseMob targeting the challenger.");
                }
                setHorseFixtureForm(mob, 32);
                mob.setAttackTarget(guard);
                LOGGER.info("coverage mocHorseHandoff mob={} guard={} target={} taunt={}", mob.getUniqueID(),
                        guard.getUniqueID(), identity(mob.getAttackTarget()), actions.taunt(mob, player));
                break;
            case "witch-opening":
            case "witch-native-redirect":
                if (!(mob instanceof net.minecraft.entity.monster.EntityWitch) || !(guard instanceof EntityPlayerMP)
                        || mob.getAttackTarget() != guard) {
                    throw new CommandException("Expected a native Witch targeting its first player.");
                }
                restorePlayer(player);
                if ("witch-opening".equals(operation)) {
                    attack(mob, player, "witch");
                }
                player.setPosition(mob.posX - 6.0, mob.posY, mob.posZ);
                guard.setPosition(mob.posX + 6.0, mob.posY, mob.posZ);
                if ("witch-native-redirect".equals(operation)) {
                    mob.setAttackTarget(player);
                }
                break;
            case "witch-drinking-handoff": {
                if (!(mob instanceof net.minecraft.entity.monster.EntityWitch) || guard == null
                        || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a tracked Witch targeting the challenger.");
                }
                net.minecraft.entity.monster.EntityWitch witch = (net.minecraft.entity.monster.EntityWitch) mob;
                witch.setDrinkingPotion(true);
                try {
                    witch.setAttackTarget(guard);
                    LOGGER.info("coverage witchDrinkingHandoff mob={} guard={} target={} taunt={}",
                            witch.getUniqueID(), guard.getUniqueID(), identity(witch.getAttackTarget()),
                            actions.taunt(witch, player));
                    report(witch, world);
                } finally {
                    witch.setDrinkingPotion(false);
                }
                return;
            }
            case "pvj-burning-handoff": {
                if (guard == null || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a tracked light-avoiding mob targeting the player.");
                }
                mob.setFire(8);
                try {
                    mob.setAttackTarget(guard);
                    LOGGER.info("coverage pvjBurningHandoff mob={} guard={} target={} burning={} taunt={}",
                            mob.getUniqueID(), guard.getUniqueID(), identity(mob.getAttackTarget()),
                            mob.isBurning(), actions.taunt(mob, player));
                    report(mob, world);
                } finally {
                    mob.extinguish();
                }
                return;
            }
            case "ordinary-guard-revenge":
            case "cqr-sword-guard-revenge":
                if (guard == null || mob.getAttackTarget() != null && mob.getAttackTarget() != guard) {
                    throw new CommandException("Expected a fresh mob or a mob already targeting its coverage guard.");
                }
                if ("cqr-sword-guard-revenge".equals(operation)) {
                    if (!"cqrepoured:zombie".equals(String.valueOf(EntityList.getKey(mob)))) {
                        throw new CommandException("Expected the CQR Zombie sword control.");
                    }
                    mob.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                }
                player.setPosition(X, mob.posY, Z + (guard instanceof EntityPlayerMP ? 27 : 13));
                player.capabilities.disableDamage = true;
                guard.setPosition(mob.posX + 1.4, mob.posY, mob.posZ);
                mob.setRevengeTarget(guard);
                break;
            case "unseen-fighting-handoff":
                String knightId = String.valueOf(EntityList.getKey(mob));
                if (!(knightId.equals("da:ancient_knight") || knightId.equals("da:ancient_knight_rapier"))
                        || guard == null || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a tracked Unseen knight targeting the player.");
                }
                boolean fighting;
                try {
                    fighting = (boolean) MethodHandles.publicLookup()
                            .findVirtual(mob.getClass(), "isFightMode", MethodType.methodType(boolean.class))
                            .asType(MethodType.methodType(boolean.class, EntityCreature.class)).invokeExact(mob);
                } catch (RuntimeException | Error failure) {
                    throw failure;
                } catch (Throwable failure) {
                    throw new CommandException("Could not read the knight's native fight mode.");
                }
                if (fighting) {
                    LOGGER.info("coverage unseenFightHandoff mob={} former={} requested={} fighting=true taunt={}",
                            mob.getUniqueID(), player.getUniqueID(), guard.getUniqueID(), actions.taunt(mob, player));
                    mob.setAttackTarget(guard);
                    if (mob.getAttackTarget() != guard) {
                        throw new CommandException("The fighting knight did not accept the native target.");
                    }
                }
                break;
            case "cqr-zombie-boundaries": {
                if (!"cqrepoured:zombie".equals(String.valueOf(EntityList.getKey(mob))) || guard == null
                        || mob.getAttackTarget() != player || !mob.getHeldItemMainhand().isEmpty()) {
                    throw new CommandException("Expected the tracked unarmed CQR Zombie.");
                }
                Entity ally = EntityList.createEntityByIDFromName(new ResourceLocation("cqrepoured:zombie"), world);
                if (!(ally instanceof EntityCreature)) {
                    throw new CommandException("The allied CQR Zombie was unavailable.");
                }
                place(world, ally, "tw_l_cqr_ally", mob.posX + 1.0, mob.posY, mob.posZ);
                ThreatActions.Result sameFaction = actions.taunt(mob, (EntityCreature) ally);
                Vec3d mobPosition = mob.getPositionVector();
                Vec3d guardPosition = guard.getPositionVector();
                guard.setPosition(mob.posX - 4, mob.posY, mob.posZ);
                boolean beyondVisible = mob.canEntityBeSeen(guard);
                boolean withinFollowRange = mob.getDistance(guard)
                        < mob.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue();
                ThreatActions.Result beyond = actions.taunt(mob, guard);
                double wallX = Math.floor(mob.posX);
                mob.setPosition(wallX + 0.1, mob.posY, mob.posZ);
                guard.setPosition(wallX - 1.1, mob.posY, mob.posZ);
                BlockPos cqrWall = new BlockPos(wallX - 0.5, mob.posY, mob.posZ);
                for (int height = 0; height < 3; height++) {
                    world.setBlockState(cqrWall.up(height), Blocks.STONE.getDefaultState(), 2);
                }
                ThreatActions.Result blocked;
                boolean withinReach;
                try {
                    withinReach = (boolean) MethodHandles.publicLookup().findVirtual(mob.getClass(), "isInAttackReach",
                            MethodType.methodType(boolean.class, EntityLivingBase.class))
                            .asType(MethodType.methodType(boolean.class, EntityCreature.class, EntityLivingBase.class))
                            .invokeExact(mob, (EntityLivingBase) guard);
                    blocked = actions.taunt(mob, guard);
                } catch (RuntimeException | Error failure) {
                    throw failure;
                } catch (Throwable failure) {
                    throw new CommandException("Could not read the CQR Zombie's native reach.");
                } finally {
                    for (int height = 0; height < 3; height++) {
                        world.setBlockToAir(cqrWall.up(height));
                    }
                }
                mob.setPosition(mobPosition.x, mobPosition.y, mobPosition.z);
                guard.setPosition(guardPosition.x, guardPosition.y, guardPosition.z);
                LOGGER.info("coverage cqrBounds mob={} player={} allied={} beyond={} beyondVisible={} withinFollowRange={} blocked={} inReach={}",
                        mob.getUniqueID(), player.getUniqueID(), sameFaction, beyond, beyondVisible, withinFollowRange, blocked, withinReach);
                mob.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                LOGGER.info("coverage cqrBow mob={} former={} requested={} taunt={}",
                        mob.getUniqueID(), player.getUniqueID(), guard.getUniqueID(), actions.taunt(mob, player));
                mob.setAttackTarget(guard);
                if (mob.getAttackTarget() != guard) {
                    throw new CommandException("The bow-equipped Zombie did not accept the native target.");
                }
                report(mob, world);
                mob.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, ItemStack.EMPTY);
                try {
                    MethodHandles.publicLookup().findVirtual(mob.getClass(), "setLeader",
                            MethodType.methodType(void.class, EntityLivingBase.class))
                            .asType(MethodType.methodType(void.class, EntityCreature.class, EntityLivingBase.class))
                            .invokeExact(mob, (EntityLivingBase) ally);
                } catch (RuntimeException | Error failure) {
                    throw failure;
                } catch (Throwable failure) {
                    throw new CommandException("Could not assign the CQR Zombie's native leader.");
                }
                LOGGER.info("coverage cqrLeader mob={} leader={} taunt={}",
                        mob.getUniqueID(), ally.getUniqueID(), actions.taunt(mob, player));
                break;
            }
            case "cqr-mandril-leap":
                if (!"cqrepoured:mandril".equals(String.valueOf(EntityList.getKey(mob))) || guard == null
                        || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a tracked Mandril targeting the player.");
                }
                if (mob.tasks.taskEntries.stream().anyMatch(task -> task.using
                        && task.priority == 9 && task.action.getClass() == EntityAILeapAtTarget.class)) {
                    player.setPosition(mob.posX - 1.30, mob.posY, mob.posZ);
                    LOGGER.info("coverage cqrMandrilLeap mob={} former={} requested={} taunt={}",
                            mob.getUniqueID(), player.getUniqueID(), guard.getUniqueID(), actions.taunt(mob, player));
                    mob.setAttackTarget(guard);
                    if (mob.getAttackTarget() != guard) {
                        throw new CommandException("The leaping Mandril did not accept the native target.");
                    }
                } else {
                    player.setPosition(mob.posX - 2.1, mob.posY, mob.posZ);
                }
                break;
            case "cqr-mandril-changed-leap":
                if (!"cqrepoured:mandril".equals(String.valueOf(EntityList.getKey(mob))) || guard == null
                        || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a tracked Mandril targeting the player.");
                }
                EntityAITaskEntry nativeLeap = mob.tasks.taskEntries.stream()
                        .filter(task -> task.priority == 9 && task.action.getClass() == EntityAILeapAtTarget.class)
                        .findFirst().orElseThrow(() -> new CommandException("Mandril's native leap was missing."));
                nativeLeap.action.setMutexBits(4);
                LOGGER.info("coverage cqrMandrilChangedLeap mob={} player={} guard={} mutex={} taunt={}",
                        mob.getUniqueID(), player.getUniqueID(), guard.getUniqueID(),
                        nativeLeap.action.getMutexBits(), actions.taunt(mob, player));
                mob.setAttackTarget(guard);
                if (mob.getAttackTarget() != guard) {
                    throw new CommandException("The modified Mandril did not accept a native target.");
                }
                break;
            case "cqr-reputation-default":
                if (!isCqrGoblinFaction(mob) || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a tracked CQR goblin-faction mob targeting the player.");
                }
                EntityPlayerMP newcomer = fixturePlayer(world, String.valueOf(EntityList.getKey(mob)), "CqrNewcomer");
                place(world, newcomer, "tw_l_cqr_newcomer", mob.posX - 0.9, mob.posY, mob.posZ);
                LOGGER.info("coverage cqrDefaultReputation mob={} player={} taunt={}",
                        mob.getUniqueID(), newcomer.getUniqueID(), actions.taunt(mob, newcomer));
                break;
            case "cqr-zombie-pursuit":
            case "cqr-zombie-pursuit-switch":
            case "cqr-zombie-pursuit-setter":
                if (!"cqrepoured:zombie".equals(String.valueOf(EntityList.getKey(mob)))
                        || guard == null || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected the tracked CQR Zombie targeting the player.");
                }
                boolean setterProbe = "cqr-zombie-pursuit-setter".equals(operation);
                double guardOffset = "cqr-zombie-pursuit".equals(operation) ? 12.0 : 1.3;
                guard.setPosition(mob.posX + guardOffset, mob.posY, mob.posZ);
                if (setterProbe) {
                    mob.setAttackTarget(guard);
                    if (mob.getAttackTarget() != player) {
                        throw new CommandException("The initial selected target was not protected at melee reach.");
                    }
                }
                player.setPosition(mob.posX - 5.0, mob.posY, mob.posZ);
                LOGGER.info("coverage cqrPursuit mob={} player={} distance={} taunt={}", mob.getUniqueID(),
                        player.getUniqueID(), mob.getDistance(player), actions.taunt(mob, player));
                if (setterProbe) {
                    mob.setAttackTarget(guard);
                    if (mob.getAttackTarget() != guard) {
                        throw new CommandException("The provider was unable to change its pursuit target.");
                    }
                    LOGGER.info("coverage cqrPursuitSetter mob={} guard={} before=blocked after=accepted",
                            mob.getUniqueID(), guard.getUniqueID());
                }
                break;
            case "unseen-opening":
                if (guard == null || mob.getAttackTarget() != guard) {
                    throw new CommandException("Expected the native guard before the Unseen opening.");
                }
                if ("supported_inactive".equals(EncounterCoordinator.INSTANCE.inspect(mob, 0).status)) {
                    restorePlayer(player);
                    float priorHealth = mob.getHealth();
                    attack(mob, player, "hit");
                    if (mob.getHealth() == priorHealth) {
                        player.capabilities.disableDamage = true;
                        player.setPosition(X, mob.posY, Z + 13);
                        mob.setRevengeTarget(guard);
                    }
                }
                break;
            case "unseen-target-bounds": {
                String id = String.valueOf(EntityList.getKey(mob));
                double reach = "da:ancient_knight".equals(id) ? 9.0 : "da:ancient_knight_rapier".equals(id) ? 6.0 : 0.0;
                if (reach == 0.0 || !"supported_inactive".equals(EncounterCoordinator.INSTANCE.inspect(mob, 0).status)) {
                    break;
                }
                restorePlayer(player);
                player.setPosition(mob.posX - reach - 0.25, mob.posY, mob.posZ);
                Object beyond = actions.taunt(mob, player);
                player.setPosition(mob.posX - 3, mob.posY, mob.posZ);
                BlockPos knightWall = new BlockPos(mob.posX - 1.5, mob.posY, mob.posZ);
                for (int height = 0; height < 4; height++) {
                    world.setBlockState(knightWall.up(height), Blocks.STONE.getDefaultState(), 2);
                }
                Object blocked;
                try {
                    blocked = actions.taunt(mob, player);
                } finally {
                    for (int height = 0; height < 4; height++) {
                        world.setBlockToAir(knightWall.up(height));
                    }
                }
                BlockPos floor = knightWall.down();
                world.setBlockToAir(floor);
                boolean pitVisible = mob.canEntityBeSeen(player);
                Object pit = actions.taunt(mob, player);
                world.setBlockState(floor, Blocks.STONE.getDefaultState(), 2);
                Entity allied = EntityList.createEntityByIDFromName(new ResourceLocation("da:ancient_knight_rapier"), world);
                if (!(allied instanceof EntityCreature)) {
                    throw new CommandException("The allied Rot Knight class was unavailable.");
                }
                place(world, allied, "tw_l_rot_ally", mob.posX + 1.5, mob.posY, mob.posZ);
                Object friend = actions.taunt(mob, (EntityCreature) allied);
                allied.setDead();
                player.setPosition(mob.posX - reach, mob.posY, mob.posZ);
                Object atKnightReach = actions.taunt(mob, player);
                LOGGER.info("coverage unseenBounds mob={} player={} reach={} beyond={} blocked={} pitVisible={} pit={} allied={} edge={}",
                        mob.getUniqueID(), player.getUniqueID(), reach, beyond, blocked, pitVisible, pit, friend, atKnightReach);
                break;
            }
            case "eerie-armor-wear":
                if (!"eerieentities:cursed_armor".equals(String.valueOf(EntityList.getKey(mob)))
                        || guard == null || mob.getAttackTarget() != guard) {
                    throw new CommandException("Expected Cursed Armor targeting its coverage guard.");
                }
                double priorArmor = mob.getEntityAttribute(SharedMonsterAttributes.ARMOR).getAttributeValue();
                float priorHealth = mob.getHealth();
                restorePlayer(player);
                for (int hit = 0; mob.getTotalArmorValue() > 0 && hit < 100; hit++) {
                    attack(mob, player, "eerie-armor-blocked");
                }
                LOGGER.info("coverage eerieArmorWear mob={} armorBefore={} armorAfter={} lostHealth={}", mob.getUniqueID(),
                        priorArmor, mob.getTotalArmorValue(), priorHealth - mob.getHealth());
                if (priorArmor < 1 || mob.getTotalArmorValue() > 0 || mob.getHealth() != priorHealth) {
                    throw new CommandException("Expected native armor to wear down before any health loss.");
                }
                break;
            case "stygian-watchling-child":
                if (!"stygian:watchling".equals(String.valueOf(EntityList.getKey(mob)))
                        || mob.isChild() || guard == null || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a tracked adult Watchling targeting the player.");
                }
                try {
                    mob.getClass().getMethod("setChild", boolean.class).invoke(mob, true);
                } catch (ReflectiveOperationException error) {
                    throw new CommandException("Could not set Watchling's native child state.");
                }
                if (!mob.isChild()) {
                    throw new CommandException("Expected the Watchling's native child flag.");
                }
                LOGGER.info("coverage watchlingChild mob={} former={} requested={} child=true taunt={}",
                        mob.getUniqueID(), player.getUniqueID(), guard.getUniqueID(), actions.taunt(mob, player));
                mob.setAttackTarget(guard);
                if (mob.getAttackTarget() != guard) {
                    throw new CommandException("The child Watchling did not accept the native target.");
                }
                break;
            case "stygian-watchling-rain":
                if (!"stygian:watchling".equals(String.valueOf(EntityList.getKey(mob)))
                        || mob.isChild() || guard == null || mob.isWet() || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a tracked dry adult Watchling targeting the player.");
                }
                world.getWorldInfo().setRaining(true);
                world.getWorldInfo().setRainTime(200);
                ReflectionHelper.setPrivateValue(World.class, world, 1.0F, "rainingStrength", "field_73004_o");
                ReflectionHelper.setPrivateValue(World.class, world, 1.0F, "prevRainingStrength", "field_73003_n");
                if (!mob.isWet() || mob.isInWater()) {
                    throw new CommandException("Expected rain without submersion.");
                }
                LOGGER.info("coverage watchlingRain mob={} former={} requested={} wet=true swimming=false taunt={}",
                        mob.getUniqueID(), player.getUniqueID(), guard.getUniqueID(), actions.taunt(mob, player));
                mob.setAttackTarget(guard);
                if (mob.getAttackTarget() != guard) {
                    throw new CommandException("The wet Watchling did not accept the native target.");
                }
                break;
            case "blaze-opening":
                if (!(mob instanceof EntityBlaze) || guard == null || mob.getAttackTarget() != guard) {
                    throw new CommandException("Expected a blaze targeting the guard before the opening hit.");
                }
                restorePlayer(player);
                String openingId = String.valueOf(EntityList.getKey(mob));
                boolean conflagration = "specialmobs:conflagrationblaze".equals(openingId);
                Vec3d joltStart = JOLT.equals(openingId) ? mob.getPositionVector() : null;
                String meleeBlaze = meleeBlazeName(openingId);
                byte feedBefore = conflagration ? blazeFeedLevel(mob) : 0;
                if (joltStart != null) {
                    player.addTag("tw_l_jolt_opening");
                } else if (meleeBlaze != null) {
                    // Keep the native incumbent in melee reach through the opening hit.
                    guard.setPosition(mob.posX + 1.25, mob.posY, mob.posZ);
                }
                attack(mob, player, "hit");
                if (joltStart != null) {
                    player.removeTag("tw_l_jolt_opening");
                    LOGGER.info("coverage joltOpeningMove mob={} player={} tick={} moved={}", mob.getUniqueID(),
                            player.getUniqueID(), world.getTotalWorldTime(), mob.getPositionVector().distanceTo(joltStart));
                }
                if (conflagration) {
                    LOGGER.info("coverage blazeFeeding mob={} player={} before={} after={}", mob.getUniqueID(),
                            player.getUniqueID(), feedBefore, blazeFeedLevel(mob));
                }
                // Give Wildfire's long ranged cycle an elevated target to follow throughout the fight.
                double playerHeight = WILDFIRE.equals(openingId) ? 5 : meleeBlaze != null ? 4 : 0;
                player.setPosition(mob.posX - 6, mob.posY + playerHeight, mob.posZ);
                if (meleeBlaze != null) {
                    LOGGER.info("coverage {}Opening mob={} player={} distance={} y={} targetY={} range={}",
                            meleeBlaze, mob.getUniqueID(), player.getUniqueID(), mob.getDistance(player), mob.posY, player.posY,
                            SpecialMobsCompatibility.blazeRange(mob));
                }
                break;
            case "cinder-blocked":
            case "cinder-clear":
            case "cinder-edge":
            case "ember-blocked":
            case "ember-clear":
            case "ember-edge":
            case "jolt-blocked":
            case "jolt-clear":
            case "jolt-edge":
                String probeBlaze = meleeBlazeName(String.valueOf(EntityList.getKey(mob)));
                if (probeBlaze == null || !operation.startsWith(probeBlaze + "-")) {
                    throw new CommandException("Expected the matching melee Blaze.");
                }
                boolean blocked = operation.endsWith("-blocked");
                double gapForMelee = operation.endsWith("-edge") ? 16 : 6;
                int probeY = "jolt".equals(probeBlaze) ? JOLT_FLOOR : Y;
                restorePlayer(player);
                mob.setPosition(X, probeY, Z);
                player.setPosition(X - gapForMelee, probeY, Z);
                BlockPos obstacle = new BlockPos(X - 2, probeY, Z);
                world.setBlockState(obstacle, blocked ? Blocks.STONE_SLAB.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
                player.addPotionEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE, 200, 0));
                boolean visible = world.rayTraceBlocks(mob.getPositionEyes(1.0F), player.getPositionEyes(1.0F),
                        false, true, false) == null;
                LOGGER.info("coverage {}Probe mob={} player={} step={} distance={} range={} followRange={} visible={} resistant={} result={}",
                        probeBlaze, mob.getUniqueID(), player.getUniqueID(), operation, mob.getDistance(player),
                        SpecialMobsCompatibility.blazeRange(mob),
                        mob.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue(),
                        visible, player.isPotionActive(MobEffects.FIRE_RESISTANCE),
                        actions.taunt(mob, player));
                break;
            case "jolt-arrow":
                if (!JOLT.equals(String.valueOf(EntityList.getKey(mob))) || mob.getAttackTarget() != null) {
                    throw new CommandException("Expected an inactive Jolt Blaze.");
                }
                restorePlayer(player);
                player.setPosition(X, Y + 80, Z);
                EntityTippedArrow joltArrow = new EntityTippedArrow(world, player);
                joltArrow.addTag("tw_l_jolt_arrow");
                joltArrow.setPosition(mob.posX - 2.5, mob.posY + mob.height * 0.5, mob.posZ);
                joltArrow.shoot(mob.posX - joltArrow.posX, mob.posY + mob.height * 0.5 - joltArrow.posY,
                        mob.posZ - joltArrow.posZ, 2.0F, 0.0F);
                if (!world.spawnEntity(joltArrow)) {
                    throw new CommandException("Jolt fixture arrow failed to spawn.");
                }
                LOGGER.info("coverage joltArrow mob={} player={} arrow={} health={}", mob.getUniqueID(),
                        player.getUniqueID(), joltArrow.getUniqueID(), mob.getHealth());
                break;
            case "wildfire-finish":
                if (!WILDFIRE.equals(String.valueOf(EntityList.getKey(mob)))
                        || mob.getAttackTarget() != player) {
                    throw new CommandException("Expected a Wildfire Blaze targeting the player before the finishing hit.");
                }
                NBTTagCompound wildfireData = specialMobData(mob);
                if (!wildfireData.hasKey("BabiesOnDeath", Constants.NBT.TAG_INT)) {
                    throw new CommandException("Expected a native Wildfire Blaze death spawn count.");
                }
                int deathSpawns = wildfireData.getInteger("BabiesOnDeath");
                mob.setHealth(3);
                mob.hurtResistantTime = 0;
                attack(mob, player, "wildfire-finish");
                LOGGER.info("coverage wildfireFinish mob={} player={} dead={} tick={} expectedChildren={}", mob.getUniqueID(),
                        player.getUniqueID(), !mob.isEntityAlive(), world.getTotalWorldTime(), deathSpawns);
                break;
            case "blaze-range-outside":
            case "blaze-range-edge":
            case "blaze-range-inside":
            case "blaze-range-resistant":
                String blazeId = String.valueOf(EntityList.getKey(mob));
                if (!"specialmobs:infernoblaze".equals(blazeId) && !"specialmobs:specialblaze".equals(blazeId)
                        && !WILDFIRE.equals(blazeId)) {
                    throw new CommandException("Expected a supported Special Mobs Blaze.");
                }
                double gap = "blaze-range-outside".equals(operation) ? 24 : "blaze-range-edge".equals(operation) ? 20 : 8;
                restorePlayer(player);
                player.setPosition(mob.posX - gap, mob.posY, mob.posZ);
                if ("blaze-range-resistant".equals(operation)) {
                    player.addPotionEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE, 100, 0));
                } else {
                    player.removePotionEffect(MobEffects.FIRE_RESISTANCE);
                }
                LOGGER.info("coverage blazeRange mob={} player={} distance={} range={} followRange={} resistant={} result={}",
                        mob.getUniqueID(), player.getUniqueID(), mob.getDistance(player),
                        SpecialMobsCompatibility.blazeRange(mob),
                        mob.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue(),
                        player.isPotionActive(MobEffects.FIRE_RESISTANCE),
                        actions.taunt(mob, player));
                break;
            case "blaze-range-release":
            case "blaze-range-return":
                if (!"specialmobs:infernoblaze".equals(String.valueOf(EntityList.getKey(mob))) || guard == null) {
                    throw new CommandException("Expected a tracked Inferno Blaze and its guard.");
                }
                boolean release = "blaze-range-release".equals(operation);
                if (release && mob.getAttackTarget() != player) {
                    throw new CommandException("Expected the coordinated player immediately before range exit.");
                }
                if (release) {
                    mob.setAttackTarget(guard);
                    if (mob.getAttackTarget() != player) {
                        throw new CommandException("The in-range player did not retain coordinated targeting.");
                    }
                    LOGGER.info("coverage blazeRangeHeld mob={} player={} distance={}", mob.getUniqueID(),
                            player.getUniqueID(), mob.getDistance(player));
                }
                player.setPosition(mob.posX - (release ? 24 : 6), mob.posY, mob.posZ);
                if (release) {
                    mob.setAttackTarget(guard);
                    if (mob.getAttackTarget() != guard) {
                        throw new CommandException("The native setter was blocked after the incumbent left fireball range.");
                    }
                }
                LOGGER.info("coverage blazeRangeTransition mob={} player={} step={} distance={} range={} target={}",
                        mob.getUniqueID(), player.getUniqueID(), operation, mob.getDistance(player),
                        SpecialMobsCompatibility.blazeRange(mob), identity(mob.getAttackTarget()));
                break;
            case "creeper-opening":
                if (!(mob instanceof EntityCreeper) || guard == null || mob.getAttackTarget() != guard
                        || mob.getDistanceSq(guard) < 9.0 || ((EntityCreeper) mob).getCreeperState() > 0) {
                    throw new CommandException("Expected a creeper pursuing its guard before swelling.");
                }
                restorePlayer(player);
                attack(mob, player, "hit");
                player.setPosition(mob.posX - 5.0, mob.posY, mob.posZ);
                break;
            case "creeper-jump-space":
                if (!(mob instanceof EntityCreeper) || guard == null || mob.getAttackTarget() != player
                        || ((EntityCreeper) mob).getCreeperState() > 0 || ((EntityCreeper) mob).hasIgnited()
                        || !mob.onGround || Math.abs(mob.motionY) > 0.1
                        || !"specialmobs:jumpingcreeper".equals(String.valueOf(EntityList.getKey(mob)))) {
                    throw new CommandException("Expected a grounded Jumping Creeper pursuing the player before swelling.");
                }
                player.setPosition(mob.posX - 8.5, mob.posY, mob.posZ);
                guard.setPosition(mob.posX + 10, mob.posY, mob.posZ);
                mob.addTag("tw_l_jumping_probe");
                LOGGER.info("coverage creeperJumpSpace mob={} target={} distance={} guardDistance={} y={} grounded=true vy={}",
                        mob.getUniqueID(), player.getUniqueID(), mob.getDistance(player), mob.getDistance(guard),
                        mob.posY, mob.motionY);
                break;
            case "creeper-short-blast":
                if (!(mob instanceof EntityCreeper) || mob.getAttackTarget() != player
                        || ((EntityCreeper) mob).getCreeperState() <= 0) {
                    throw new CommandException("Expected a selected player and a swelling creeper.");
                }
                player.setPosition(mob.posX - 1.1, mob.posY, mob.posZ);
                LOGGER.info("coverage creeperShortBlast mob={} player={} distance={}", mob.getUniqueID(),
                        player.getUniqueID(), mob.getDistance(player));
                break;
            case "creeper-splitting-shot":
                if (!(mob instanceof EntityCreeper) || mob.getAttackTarget() != player
                        || ((EntityCreeper) mob).getCreeperState() > 0 || ((EntityCreeper) mob).hasIgnited()
                        || !"specialmobs:splittingcreeper".equals(String.valueOf(EntityList.getKey(mob)))) {
                    throw new CommandException("Expected a Splitting Creeper pursuing the player before its fuse.");
                }
                player.setPosition(mob.posX, mob.posY, mob.posZ + 3.8);
                EntityTippedArrow shot = new EntityTippedArrow(world, player);
                shot.addTag("tw_l_splitting_shot");
                shot.shoot(mob.posX - shot.posX, mob.posY + mob.height * 0.5 - shot.posY, mob.posZ - shot.posZ,
                        2.0F, 0.0F);
                if (!world.spawnEntity(shot)) {
                    throw new CommandException("Splitting Creeper fixture arrow failed to spawn.");
                }
                LOGGER.info("coverage creeperSplittingShot mob={} player={} arrow={} distance={} fuse={} ignited={}",
                        mob.getUniqueID(), player.getUniqueID(), shot.getUniqueID(), mob.getDistance(player),
                        ((EntityCreeper) mob).getCreeperState(), ((EntityCreeper) mob).hasIgnited());
                break;
            case "creeper-burn":
                if (!(mob instanceof EntityCreeper) || mob.getAttackTarget() != player
                        || ((EntityCreeper) mob).getCreeperState() > 0 || mob.isBurning()
                        || !"specialmobs:deathcreeper".equals(String.valueOf(EntityList.getKey(mob)))) {
                    throw new CommandException("Expected a Death Creeper pursuing the player before its fuse.");
                }
                player.setPosition(mob.posX, mob.posY, mob.posZ + 5.0);
                mob.setFire(5);
                LOGGER.info("coverage creeperBurn mob={} player={} distance={} burning={} fuse={} ignited={}",
                        mob.getUniqueID(), player.getUniqueID(), mob.getDistance(player), mob.isBurning(),
                        ((EntityCreeper) mob).getCreeperState(), ((EntityCreeper) mob).hasIgnited());
                break;
            case "creeper-fuse-read":
                if (!(mob instanceof EntityCreeper)) {
                    throw new CommandException("Expected a creeper.");
                }
                LOGGER.info("coverage creeperFuse mob={} player={} distance={} burning={} fuse={} ignited={}",
                        mob.getUniqueID(), player.getUniqueID(), mob.getDistance(player), mob.isBurning(),
                        ((EntityCreeper) mob).getCreeperState(), ((EntityCreeper) mob).hasIgnited());
                break;
            case "creeper-ocelot":
                if (!(mob instanceof EntityCreeper) || guard == null || mob.getAttackTarget() != player
                        || ((EntityCreeper) mob).getCreeperState() > 0) {
                    throw new CommandException("Expected a creeper pursuing the selected player before swelling.");
                }
                EntityOcelot ocelot = new EntityOcelot(world);
                ocelot.setNoAI(true);
                ocelot.enablePersistence();
                place(world, ocelot, "tw_l_creeper_ocelot", mob.posX, mob.posY, mob.posZ + 2.0);
                LOGGER.info("coverage creeperOcelot mob={} cat={} x={} z={}", mob.getUniqueID(), ocelot.getUniqueID(),
                        ocelot.posX, ocelot.posZ);
                break;
            case "creeper-ocelot-handoff":
                EntityOcelot cat = get(world, EntityOcelot.class, "tw_l_creeper_ocelot");
                boolean avoiding = mob.tasks.taskEntries.stream().anyMatch(entry -> entry.using && entry.priority == 3
                        && entry.action.getClass() == EntityAIAvoidEntity.class);
                if (!(mob instanceof EntityCreeper) || cat == null || guard == null) {
                    throw new CommandException("Expected the fixture creeper, ocelot and guard.");
                }
                Path retreat = mob.getNavigator().getPath();
                if (avoiding && retreat != null && !retreat.isFinished()) {
                    Vec3d end = retreat.getVectorFromIndex(mob, retreat.getCurrentPathLength() - 1);
                    if (end.squareDistanceTo(cat.getPositionVector()) <= cat.getDistanceSq(mob)) {
                        break;
                    }
                    if (mob.getAttackTarget() != player) {
                        throw new CommandException("Expected the player before the native setter probe.");
                    }
                    mob.setAttackTarget(guard);
                    if (mob.getAttackTarget() != guard) {
                        throw new CommandException("The native setter was blocked during ocelot avoidance.");
                    }
                    mob.setAttackTarget(player);
                    if (mob.getAttackTarget() != player) {
                        throw new CommandException("The native setter could not restore the player.");
                    }
                    LOGGER.info("coverage creeperAvoidanceTaunt mob={} cat={} target={} result={} endX={} endZ={}",
                            mob.getUniqueID(), cat.getUniqueID(), identity(mob.getAttackTarget()), actions.taunt(mob, guard),
                            end.x, end.z);
                    report(mob, world);
                    cat.setDead();
                    mob.getNavigator().clearPath();
                    return;
                }
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
                if (isCqrGoblinFaction(mob) || "cqrepoured:gremlin".equals(String.valueOf(EntityList.getKey(mob)))) {
                    mob.motionX = 0;
                    mob.motionZ = 0;
                    // Native revenge retains the closer incumbent. Both players fit inside the melee lane.
                    player.setPosition(mob.posX, mob.posY, mob.posZ + 1.2);
                    guard.setPosition(mob.posX + 0.8, mob.posY, mob.posZ);
                }
                if (animatedDread(String.valueOf(EntityList.getKey(mob)))) {
                    player.setPosition(mob.posX - 6.0, mob.posY, mob.posZ);
                    LOGGER.info("coverage dreadBeastOpening mob={} player={} distance={}", mob.getUniqueID(),
                            player.getUniqueID(), mob.getDistance(player));
                }
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
                String defiledId = String.valueOf(EntityList.getKey(mob));
                if (probeSetter && (!mob.getServer().getFolderName().startsWith("TW-Campaign-L") || guard == null
                        || !guard.isEntityAlive() || mob.getAttackTarget() != player
                        || !("defiledlands:host".equals(defiledId) || "defiledlands:scuttler".equals(defiledId)))) {
                    throw new CommandException("Expected a tracked Campaign L Host or Scuttler targeting the player.");
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
                String coveredId = String.valueOf(EntityList.getKey(mob));
                if (!mob.getServer().getFolderName().startsWith("TW-Campaign-L") || guard == null
                        || mob.getAttackTarget() != guard
                        || !("defiledlands:shambler_twisted".equals(coveredId) || "defiledlands:scuttler".equals(coveredId))) {
                    throw new CommandException("Expected a Campaign L Twisted Shambler or Scuttler targeting the guard.");
                }
                restorePlayer(player);
                attack(mob, player, "hit");
                BlockPos origin = mob.getPosition();
                for (BlockPos pos : BlockPos.getAllInBox(origin.add(-2, 0, -2), origin.add(-2, 2, 2))) {
                    world.setBlockState(pos, Blocks.STONE.getDefaultState(), 2);
                }
                player.setPosition(mob.posX - 5.0, mob.posY, mob.posZ);
                mob.addTag("tw_l_sight_probe");
                LOGGER.info("coverage defiledSight mob={} player={} visible={} brightness={}", mob.getUniqueID(),
                        player.getUniqueID(), mob.canEntityBeSeen(player), mob.getBrightness());
                break;
            case "gaia-neutral-feed":
                if (!mob.getServer().getFolderName().startsWith("TW-Campaign-L") || guard == null
                        || !guard.isEntityAlive() || mob.getAttackTarget() != player
                        || !"grimoireofgaia:mummy".equals(String.valueOf(EntityList.getKey(mob)))) {
                    throw new CommandException("Expected a tracked Campaign L Mummy targeting the player.");
                }
                ResourceLocation feed = new ResourceLocation("grimoireofgaia:food_monster_feed_premium");
                if (!ForgeRegistries.ITEMS.containsKey(feed)) {
                    throw new CommandException("Gaia premium monster feed was not registered.");
                }
                player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, new ItemStack(ForgeRegistries.ITEMS.getValue(feed)));
                if (!mob.processInitialInteract(player, net.minecraft.util.EnumHand.MAIN_HAND)
                        || !player.getHeldItemMainhand().isEmpty()) {
                    throw new CommandException("The Gaia mob did not consume the native feed.");
                }
                LOGGER.info("coverage gaiaFeed mob={} item={} consumed=true", mob.getUniqueID(), feed);
                mob.setAttackTarget(guard);
                if (mob.getAttackTarget() != guard) {
                    throw new CommandException("The native setter was blocked after the mob consumed Gaia feed.");
                }
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
            case "variant-skeleton-bow":
                if (!mob.getServer().getFolderName().startsWith("TW-Campaign-L") || guard == null
                        || !guard.isEntityAlive() || mob.getAttackTarget() != player
                        || !"specialmobs:knightskeleton".equals(String.valueOf(EntityList.getKey(mob)))) {
                    throw new CommandException("Expected a tracked Campaign L Knight Skeleton targeting the player.");
                }
                mob.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(net.minecraft.init.Items.BOW));
                mob.setAttackTarget(guard);
                if (mob.getAttackTarget() != guard) {
                    throw new CommandException("The native setter was blocked after changing skeleton weapon modes.");
                }
                guard.setPosition(mob.posX + 6, mob.posY, mob.posZ);
                break;
            case "variant-skeleton-wolf":
                String avoidingId = String.valueOf(EntityList.getKey(mob));
                if (!mob.getServer().getFolderName().startsWith("TW-Campaign-L") || guard == null
                        || !("specialmobs:fireskeleton".equals(avoidingId) || "quark:ashen".equals(avoidingId))) {
                    throw new CommandException("Expected a Campaign L Fire Skeleton or Quark Ashen.");
                }
                net.minecraft.entity.passive.EntityWolf wolf = new net.minecraft.entity.passive.EntityWolf(world);
                wolf.setNoAI(true);
                wolf.enablePersistence();
                place(world, wolf, "tw_l_wolf", mob.posX, mob.posY, mob.posZ + 4);
                break;
            case "variant-skeleton-remove-wolf":
                net.minecraft.entity.passive.EntityWolf wolfProbe = get(world, net.minecraft.entity.passive.EntityWolf.class, "tw_l_wolf");
                if (wolfProbe != null) {
                    wolfProbe.setDead();
                }
                LOGGER.info("coverage wolfRemoved present={}", wolfProbe != null);
                break;
            case "quark-ashen-return":
                if (!"quark:ashen".equals(String.valueOf(EntityList.getKey(mob)))) {
                    throw new CommandException("Expected a Campaign L Quark Ashen.");
                }
                // Restore a local bow lane after the unmodified wolf goal has completed its retreat.
                player.setPosition(mob.posX - 3, mob.posY, mob.posZ);
                player.setInvisible(false);
                player.addTag("tw_l_tick_hurt_window");
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

    static void observeJumpingPursuit(WorldServer world, EntityCreature mob) {
        EntityIronGolem guard = get(world, EntityIronGolem.class, "tw_i_guard");
        EntityPlayerMP player = get(world, EntityPlayerMP.class, "tw_i_player");
        if (guard == null || player == null || mob.getAttackTarget() != guard || !mob.onGround || mob.posX <= X + 0.15
                || mob.getDistanceSq(guard) <= 9.0 || ((EntityCreeper) mob).getCreeperState() > 0
                || !mob.tasks.taskEntries.stream().anyMatch(entry -> entry.using && entry.action instanceof EntityAIAttackMelee)) {
            return;
        }
        Path path = mob.getNavigator().getPath();
        PathPoint destination = path == null ? null : path.getFinalPathPoint();
        if (destination == null || Math.abs(destination.x + 0.5 - guard.posX) > 0.5 || destination.z + 0.5 != guard.posZ) {
            return;
        }
        mob.removeTag("tw_l_jumping_pursuit");
        LOGGER.info("coverage creeperJumpPursuit mob={} target={} x={} distance={} pathX={} pathZ={}",
                mob.getUniqueID(), guard.getUniqueID(), mob.posX, mob.getDistance(guard),
                destination.x + 0.5, destination.z + 0.5);
        report(mob, world);
        restorePlayer(player);
        attack(mob, player, "hit");
        guard.setPosition(mob.posX + 5, mob.posY, mob.posZ);
        player.setPosition(mob.posX - 4, mob.posY, mob.posZ);
        report(mob, world);
    }

    private static void restorePlayer(EntityPlayerMP player) {
        player.capabilities.disableDamage = false;
        player.setInvisible(false);
        player.hurtResistantTime = 0;
    }

    private static void setHorseFixtureForm(EntityCreature horse, int form) throws CommandException {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            lookup.findVirtual(horse.getClass(), "setType", MethodType.methodType(void.class, int.class))
                    .invoke(horse, form);
            boolean flyer = (boolean) lookup.findVirtual(horse.getClass(), "isFlyer", MethodType.methodType(boolean.class))
                    .invoke(horse);
            LOGGER.info("coverage mocHorseForm mob={} form={} flyer={}", horse.getUniqueID(), form, flyer);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new CommandException("The exact HorseMob fixture did not expose its form state.");
        }
    }

    static void attack(EntityLiving mob, EntityPlayerMP player, String operation) {
        if ("da:ancient_knight_rapier".equals(String.valueOf(EntityList.getKey(mob)))) {
            // Approach from behind the provider's directional parry when measuring a health-losing opening.
            double yaw = Math.toRadians(mob.rotationYaw);
            player.setPosition(mob.posX + Math.sin(yaw) * 1.3, mob.posY, mob.posZ - Math.cos(yaw) * 1.3);
        } else {
            player.setPosition(mob.posX - 1.3, mob.posY, mob.posZ);
        }
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

    private static void actGhast(WorldServer world, EntityGhast mob, EntityPlayerMP player,
                                  EntityLivingBase guard, String operation) throws CommandException {
        if (guard == null) {
            throw new CommandException("Expected the ghast's player incumbent.");
        }
        if ("ghast-second-lane".equals(operation) || "ghast-opening".equals(operation)) {
            if (mob.getAttackTarget() != guard) {
                throw new CommandException("Expected native player acquisition before changing the ghast's target.");
            }
            restorePlayer(player);
            boolean melee = meleeGhast(String.valueOf(EntityList.getKey(mob)));
            double spacing = melee ? 5.0 : Math.max(9.0, mob.width + 2.0);
            if ("ghast-opening".equals(operation)) {
                attack(mob, player, "ghast");
                guard.setPosition(mob.posX + spacing, mob.posY + 0.5, mob.posZ);
                player.setPosition(mob.posX - spacing, mob.posY + 0.5, mob.posZ);
            } else {
                player.setPosition(mob.posX - spacing, mob.posY + 0.5, mob.posZ);
                mob.setAttackTarget(player);
                if (mob.getAttackTarget() != player) {
                    throw new CommandException("The ghast's native setter rejected the second player.");
                }
            }
        } else if ("ghast-melee-geometry".equals(operation)) {
            if (mob.getAttackTarget() != player || !meleeGhast(String.valueOf(EntityList.getKey(mob)))) {
                throw new CommandException("Expected a melee ghast targeting the challenger.");
            }
            ThreatActions actions = ThreatworksMod.threatActions();
            Vec3d former = guard.getPositionVector();
            mob.setPosition(X, Y + 10, Z);
            guard.setPosition(X + 13, Y + 10, Z);
            ThreatActions.Result distant = actions.taunt(mob, guard);
            guard.setPosition(X + 6, Y + 10, Z);
            // A low protrusion clips the flying body while leaving the eye ray clear.
            BlockPos obstruction = new BlockPos(X + 3, Y + 10, Z);
            world.setBlockState(obstruction, Blocks.STONE.getDefaultState());
            guard.setPosition(X + 6, Y + 10.5, Z);
            Vec3d eye = mob.getPositionVector().add(0, mob.getEyeHeight(), 0);
            Vec3d targetEye = guard.getPositionVector().add(0, guard.getEyeHeight(), 0);
            boolean openEyes = world.rayTraceBlocks(eye, targetEye, false, true, false) == null;
            ThreatActions.Result blocked = actions.taunt(mob, guard);
            world.setBlockToAir(obstruction);
            LOGGER.info("coverage ghastMeleeGeometry mob={} guard={} distant={} eyeClear={} blocked={} clear={}",
                    mob.getUniqueID(), guard.getUniqueID(), distant, openEyes, blocked, actions.taunt(mob, guard));
            guard.setPosition(former.x, former.y, former.z);
        } else if ("ghast-geometry".equals(operation)) {
            if (mob.getAttackTarget() != player) {
                throw new CommandException("Expected the ghast's selected challenger.");
            }
            ThreatActions actions = ThreatworksMod.threatActions();
            Vec3d former = guard.getPositionVector();
            guard.setPosition(mob.posX + mob.width - 0.5, mob.posY, mob.posZ);
            ThreatActions.Result tooClose = actions.taunt(mob, guard);
            guard.setPosition(mob.posX + 9, mob.posY + 0.5, mob.posZ);
            BlockPos wall = new BlockPos(mob.posX + 5, mob.posY, mob.posZ);
            for (BlockPos pos : BlockPos.getAllInBox(wall.add(0, -4, -2), wall.add(0, 6, 2))) {
                world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState(), 2);
            }
            ThreatActions.Result blocked = actions.taunt(mob, guard);
            for (BlockPos pos : BlockPos.getAllInBox(wall.add(0, -4, -2), wall.add(0, 6, 2))) {
                world.setBlockToAir(pos);
            }
            // Separate the eye ray and the downward fireball lane at the same wall plane.
            mob.setPosition(Math.floor(mob.posX) + 0.5, Math.floor(mob.posY), Math.floor(mob.posZ) + 0.5);
            guard.setPosition(mob.posX + 9, mob.posY - 2, mob.posZ);
            BlockPos projectileBlock = new BlockPos(mob.posX + 7, mob.posY - 1, mob.posZ);
            world.setBlockState(projectileBlock, Blocks.OBSIDIAN.getDefaultState(), 2);
            Vec3d eye = mob.getPositionVector().add(0, mob.getEyeHeight(), 0);
            Vec3d guardEye = guard.getPositionVector().add(0, guard.getEyeHeight(), 0);
            boolean seesEye = world.rayTraceBlocks(eye, guardEye, false, true, false) == null;
            ThreatActions.Result projectileBlocked = actions.taunt(mob, guard);
            world.setBlockToAir(projectileBlock);
            LOGGER.info("coverage ghastGeometry mob={} guard={} tooClose={} blocked={} eyeClear={} projectileBlocked={} clear={}",
                    mob.getUniqueID(), guard.getUniqueID(), tooClose, blocked, seesEye, projectileBlocked,
                    actions.taunt(mob, guard));
            guard.setPosition(former.x, former.y, former.z);
        } else if ("ghast-modified".equals(operation)) {
            if (mob.getAttackTarget() != player) {
                throw new CommandException("Expected the ghast's selected challenger.");
            }
            EntityAITaskEntry selector = mob.targetTasks.taskEntries.iterator().next();
            selector.action.setMutexBits(1);
            LOGGER.info("coverage ghastModified mob={} guard={} newMutex={} taunt={}",
                    mob.getUniqueID(), guard.getUniqueID(), selector.action.getMutexBits(),
                    ThreatworksMod.threatActions().taunt(mob, player));
            mob.setAttackTarget(guard);
            if (mob.getAttackTarget() != guard) {
                throw new CommandException("The modified ghast did not accept its native target.");
            }
        } else if (!"read".equals(operation)) {
            throw new CommandException("Unsupported ghast fixture operation.");
        }
        report(mob, world);
    }

    private static void actSlime(WorldServer world, EntitySlime slime, EntityPlayerMP player,
                                 EntityLivingBase guard, String operation) throws CommandException {
        if ("slime-native-redirect".equals(operation) || "slime-opening".equals(operation)) {
            if (slime.getAttackTarget() != guard || !slime.onGround) {
                throw new CommandException("Expected the grounded slime targeting its first participant.");
            }
            restorePlayer(player);
            if ("slime-opening".equals(operation)) {
                attack(slime, player, "slime");
                if (player.getRidingEntity() == slime) {
                    player.dismountRidingEntity();
                }
                player.setPosition(slime.posX - 6, slime.posY, slime.posZ);
                guard.setPosition(slime.posX + 4, slime.posY, slime.posZ);
                player.hurtResistantTime = 0;
            } else {
                player.setPosition(slime.posX - 6, slime.posY, slime.posZ);
                slime.setAttackTarget(player);
                if (slime.getAttackTarget() != player) {
                    throw new CommandException("The slime's native setter rejected the second player.");
                }
            }
        } else if ("slime-bounds".equals(operation)) {
            ThreatActions actions = ThreatworksMod.threatActions();
            slime.setPosition(X, Y, Z);
            guard.setPosition(X + 7, Y, Z);
            guard.onGround = true;
            BlockPos obstruction = new BlockPos(X + 3, Y, Z);
            world.setBlockState(obstruction, Blocks.OBSIDIAN.getDefaultState(), 2);
            ThreatActions.Result body = actions.taunt(slime, guard);
            world.setBlockToAir(obstruction);
            world.setBlockToAir(obstruction.down());
            ThreatActions.Result gap = actions.taunt(slime, guard);
            world.setBlockState(obstruction.down(), Blocks.STONE.getDefaultState(), 2);
            BlockPos overhead = obstruction.up(3);
            world.setBlockState(overhead, Blocks.OBSIDIAN.getDefaultState(), 2);
            Vec3d eye = slime.getPositionVector().add(0, slime.getEyeHeight(), 0);
            Vec3d guardEye = guard.getPositionVector().add(0, guard.getEyeHeight(), 0);
            boolean openSight = world.rayTraceBlocks(eye, guardEye, false, true, false) == null;
            ThreatActions.Result roof = actions.taunt(slime, guard);
            world.setBlockToAir(overhead);
            LOGGER.info("coverage slimeBounds mob={} guard={} body={} gap={} openSight={} roof={} clear={}",
                    slime.getUniqueID(), guard.getUniqueID(), body, gap, openSight, roof, actions.taunt(slime, guard));
        } else if ("slime-changed-selector".equals(operation)) {
            if (slime.getAttackTarget() != player) {
                throw new CommandException("Expected a tracked slime targeting the player.");
            }
            EntityAITaskEntry nearest = slime.targetTasks.taskEntries.stream()
                    .filter(task -> task.priority == 3 && task.action.getClass() == net.minecraft.entity.ai.EntityAIFindEntityNearest.class)
                    .findFirst().orElseThrow(() -> new CommandException("Slime's native golem selector was missing."));
            slime.targetTasks.removeTask(nearest.action);
            slime.targetTasks.addTask(3, new net.minecraft.entity.ai.EntityAIFindEntityNearest(slime, EntityZombie.class));
            LOGGER.info("coverage slimeChangedSelector mob={} guard={} targetClass=Zombie taunt={}",
                    slime.getUniqueID(), guard.getUniqueID(), ThreatworksMod.threatActions().taunt(slime, player));
            slime.setAttackTarget(guard);
            if (slime.getAttackTarget() != guard) {
                throw new CommandException("The modified slime did not accept its native target.");
            }
        } else if ("slime-rider-release".equals(operation)) {
            if (guard.getRidingEntity() != slime) {
                throw new CommandException("The incumbent is not held by the slime.");
            }
            ThreatActions.Result tauntWhileMounted = ThreatworksMod.threatActions().taunt(slime, player);
            guard.dismountRidingEntity();
            guard.setPosition(slime.posX + 4, slime.posY, slime.posZ);
            LOGGER.info("coverage slimeRiderRelease mob={} guard={} mountedTaunt={} stillRiding={}",
                    slime.getUniqueID(), guard.getUniqueID(), tauntWhileMounted, guard.getRidingEntity() == slime);
        } else if ("slime-tiny".equals(operation)) {
            setFixtureSlimeSize(slime, 1);
            LOGGER.info("coverage slimeTiny mob={} guard={} taunt={}",
                    slime.getUniqueID(), guard.getUniqueID(), ThreatworksMod.threatActions().taunt(slime, player));
            slime.setAttackTarget(guard);
            if (slime.getAttackTarget() != guard) {
                throw new CommandException("The tiny slime did not accept its native target.");
            }
        } else if (!"read".equals(operation)) {
            throw new CommandException("Unsupported slime fixture operation.");
        }
        LOGGER.info("coverage slimeState mob={} target={} onGround={} size={} x={} y={} z={} playerDistance={} guardDistance={}",
                slime.getUniqueID(), identity(slime.getAttackTarget()), slime.onGround, slime.getSlimeSize(),
                slime.posX, slime.posY, slime.posZ, slime.getDistance(player), slime.getDistance(guard));
        report(slime, world);
    }

    static void report(EntityLiving mob, WorldServer world) {
        Inspection view = EncounterCoordinator.INSTANCE.inspect(mob, 16);
        EntityLivingBase guard = get(world, EntityLivingBase.class, "tw_i_guard");
        EntityLivingBase player = get(world, EntityPlayerMP.class, "tw_i_player");
        LOGGER.info("coverage state tick={} mob={} type={} status={} target={} guardHp={} playerHp={} x={} z={} weapon={} navigator={}",
                world.getMinecraftServer().getTickCounter(), mob.getUniqueID(), view.typeId, view.status, view.target,
                guard == null ? -1 : guard.getHealth(), player == null ? -1 : player.getHealth(), mob.posX, mob.posZ,
                mob.getHeldItemMainhand(), mob.getNavigator().getClass().getName());
        LOGGER.info("coverage behavior mob={} age={} y={} noAi={} riding={} ridden={} goals={}", mob.getUniqueID(),
                mob.ticksExisted, mob.posY, mob.isAIDisabled(), mob.isRiding(), mob.isBeingRidden(), mob.tasks.taskEntries.stream()
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

    private static void fishNativeHandoff(ThreatActions actions, EntityCreature mob,
                                          EntityLivingBase player, EntityLivingBase guard, String phase)
            throws CommandException {
        LOGGER.info("coverage fishNativeHandoff mob={} former={} requested={} phase={} taunt={}",
                mob.getUniqueID(), player.getUniqueID(), guard.getUniqueID(), phase, actions.taunt(mob, player));
        mob.setAttackTarget(guard);
        if (mob.getAttackTarget() != guard) {
            throw new CommandException("The tracked Fish mob did not accept the native phase target.");
        }
    }

    private static byte blazeFeedLevel(EntityCreature mob) throws CommandException {
        NBTTagCompound data = specialMobData(mob);
        if (!data.hasKey("FeedLevel", Constants.NBT.TAG_BYTE)) {
            throw new CommandException("Expected a native Conflagration Blaze feeding level.");
        }
        return data.getByte("FeedLevel");
    }

    private static void dreadState(EntityCreature mob, String step) throws CommandException {
        try {
            Class<?> dreadClass = mob.getClass();
            Object current = dreadClass.getMethod("getAnimation").invoke(mob);
            Object emergence = dreadClass.getField("ANIMATION_SPAWN").get(null);
            Object commander = dreadClass.getMethod("getCommanderId").invoke(mob);
            Entity mount = mob.getRidingEntity();
            LOGGER.info("coverage dreadState mob={} step={} emergence={} commander={} mount={} tick={}", mob.getUniqueID(),
                    step, current == emergence, commander, mount == null ? null : mount.getUniqueID(),
                    mob.world.getTotalWorldTime());
        } catch (ReflectiveOperationException failure) {
            throw new CommandException("Could not read native Dread state.");
        }
    }

    static boolean animatedDread(String id) {
        return DREAD_BEAST.equals(id) || DREAD_GHOUL.equals(id) || DREAD_SCUTTLER.equals(id);
    }

    static void observeScuttlerClimb(WorldServer world, EntityCreature mob) {
        EntityPlayerMP player = get(world, EntityPlayerMP.class, "tw_i_player");
        EntityIronGolem guard = get(world, EntityIronGolem.class, "tw_i_guard");
        if (player == null || guard == null) {
            return;
        }
        if (mob.posY <= Y || !mob.isOnLadder() || !mob.collidedHorizontally || mob.motionY <= 0) {
            // Keep pushing into the wall. All vertical travel is native.
            mob.motionX = 0.3;
            return;
        }
        mob.removeTag(SCUTTLER_WALL_PROBE);
        String status = EncounterCoordinator.INSTANCE.inspect(mob, 0).status;
        ThreatActions.Result taunt = ThreatworksMod.threatActions().taunt(mob, player);
        EntityLivingBase previous = mob.getAttackTarget();
        EntityLivingBase requested = previous == guard ? player : guard;
        mob.setAttackTarget(requested);
        LOGGER.info("coverage dreadScuttlerClimb mob={} player={} previous={} requested={} tick={} y={} vy={} ladder={} collision={} status={} taunt={} setterAccepted={}",
                mob.getUniqueID(), player.getUniqueID(), identity(previous), requested.getUniqueID(), world.getTotalWorldTime(),
                mob.posY, mob.motionY, mob.isOnLadder(), mob.collidedHorizontally, status, taunt, mob.getAttackTarget() == requested);
    }

    static void recordDreadAttack(EntityCreature mob, EntityLivingBase victim, String id) {
        try {
            boolean ghoul = DREAD_GHOUL.equals(id);
            Class<?> entityClass = mob.getClass();
            Object attack = entityClass.getField(ghoul ? "ANIMATION_SLASH" : "ANIMATION_BITE").get(null);
            boolean animated = entityClass.getMethod("getAnimation").invoke(mob) == attack;
            Object frame = entityClass.getMethod("getAnimationTick").invoke(mob);
            LOGGER.info("coverage {} mob={} victim={} target={} tick={} {}={} frame={} distance={} visible={}",
                    ghoul ? "dreadGhoulSlash" : DREAD_SCUTTLER.equals(id) ? "dreadScuttlerBite" : "dreadBeastBite",
                    mob.getUniqueID(), victim.getUniqueID(), ArrowFixtureCommand.identity(mob.getAttackTarget()),
                    mob.world.getTotalWorldTime(), ghoul ? "slash" : "bite", animated, frame,
                    mob.getDistance(victim), mob.canEntityBeSeen(victim));
        } catch (ReflectiveOperationException failure) {
            LOGGER.warn("Could not read the native Dread attack for {}.", id, failure);
        }
    }

    static String meleeBlazeName(String id) {
        switch (id) {
            case CINDER:
                return "cinder";
            case EMBER:
                return "ember";
            case JOLT:
                return "jolt";
            default:
                return null;
        }
    }

    private static NBTTagCompound specialMobData(EntityCreature mob) {
        return mob.writeToNBT(new NBTTagCompound()).getCompoundTag("ForgeData").getCompoundTag("SpecialMobsData");
    }

    private static String identity(Entity entity) {
        return entity == null ? "none" : entity.getUniqueID().toString();
    }

    private static <T extends Entity> T get(WorldServer world, Class<T> type, String tag) {
        return world.getEntitiesWithinAABB(type, new AxisAlignedBB(-64, 0, -64, 64, 256, 64),
                entity -> entity.isEntityAlive() && entity.getTags().contains(tag)).stream().findFirst().orElse(null);
    }
}
