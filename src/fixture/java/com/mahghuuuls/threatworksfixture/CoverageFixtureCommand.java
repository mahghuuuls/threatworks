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
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.projectile.EntityArrow;
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
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

final class CoverageFixtureCommand extends CommandBase {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");
    static final String TAG = "tw_coverage";
    private static final double X = 12.5;
    private static final int Y = 20;
    private static final String WILDFIRE = "specialmobs:wildfireblaze";
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
        int height = id.startsWith("grimoireofgaia:") ? 64 : "defiledlands:shambler".equals(id) ? 60 : Y;
        arena(world, height);
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
        if (!(entity instanceof EntityCreature)) {
            throw new CommandException("Unknown coverage fixture mob " + id);
        }
        EntityCreature mob = (EntityCreature) entity;
        mob.setPosition(X, height, Z);
        mob.onInitialSpawn(world.getDifficultyForLocation(mob.getPosition()), null);
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
        if ("specialmobs:jumpingcreeper".equals(String.valueOf(EntityList.getKey(mob)))) {
            mob.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        }
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
        boolean wildfireFight = WILDFIRE.equals(String.valueOf(EntityList.getKey(mob)));
        FakePlayer player = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "CampaignI")) {
            @Override
            public void onUpdate() {
                // FakePlayer skips the countdown a connected player gets in EntityPlayerMP.
                if (wildfireFight && hurtResistantTime > 0) {
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
        player.setSneaking(true);
        player.setInvisible(true);
        if (String.valueOf(EntityList.getKey(mob)).startsWith("grimoireofgaia:")) {
            player.addTag("tw_l_gaia_effect");
        }
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

    private void act(WorldServer world, String operation) throws CommandException {
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
            case "gaia-vertical-impact":
                LOGGER.info("coverage gaiaVertical mob={} player={} playerY={} mobY={} velocityY={}",
                        mob.getUniqueID(), player.getUniqueID(), player.posY, mob.posY, player.motionY);
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
                if ("specialmobs:jumpingcreeper".equals(String.valueOf(EntityList.getKey(mob)))) {
                    // The golem's normal attack adds lift even when its victim resists knockback.
                    mob.attackEntityFrom(DamageSource.causeMobDamage(guard), 1.0F);
                    if ("creeper-jump-retaliation".equals(operation)) {
                        mob.addTag("tw_l_jumping_pursuit");
                    }
                } else {
                    guard.attackEntityAsMob(mob);
                }
                break;
            case "legal-player":
                restorePlayer(player);
                break;
            case "blaze-opening":
                if (!(mob instanceof EntityBlaze) || guard == null || mob.getAttackTarget() != guard) {
                    throw new CommandException("Expected a blaze targeting the guard before the opening hit.");
                }
                restorePlayer(player);
                String openingId = String.valueOf(EntityList.getKey(mob));
                boolean conflagration = "specialmobs:conflagrationblaze".equals(openingId);
                byte feedBefore = conflagration ? blazeFeedLevel(mob) : 0;
                attack(mob, player, "hit");
                if (conflagration) {
                    LOGGER.info("coverage blazeFeeding mob={} player={} before={} after={}", mob.getUniqueID(),
                            player.getUniqueID(), feedBefore, blazeFeedLevel(mob));
                }
                // Give Wildfire's long ranged cycle an elevated target to follow throughout the fight.
                double playerHeight = WILDFIRE.equals(openingId) ? 5 : 0;
                player.setPosition(mob.posX - 6, mob.posY + playerHeight, mob.posZ);
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
                if (!mob.getServer().getFolderName().startsWith("TW-Campaign-L") || guard == null
                        || !"specialmobs:fireskeleton".equals(String.valueOf(EntityList.getKey(mob)))) {
                    throw new CommandException("Expected a Campaign L Fire Skeleton.");
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

    private static byte blazeFeedLevel(EntityCreature mob) throws CommandException {
        NBTTagCompound data = specialMobData(mob);
        if (!data.hasKey("FeedLevel", Constants.NBT.TAG_BYTE)) {
            throw new CommandException("Expected a native Conflagration Blaze feeding level.");
        }
        return data.getByte("FeedLevel");
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
