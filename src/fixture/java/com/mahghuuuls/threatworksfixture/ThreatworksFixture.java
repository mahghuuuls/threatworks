package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.ThreatworksMod;
import com.mahghuuuls.threatworks.api.SummonThreatCreditEvent;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAreaEffectCloud;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Items;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

@Mod(modid = "threatworksfixture", name = "Threatworks Verification Fixture", version = "1.0.0",
        acceptedMinecraftVersions = "[1.12.2]", acceptableRemoteVersions = "*",
        dependencies = "required-after:threatworks")
public final class ThreatworksFixture {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");
    private static final String CANCEL_NEXT = "threatworks_cancel_next";
    private static final String REENTER_PLAYER = "threatworks_reenter_next_player";
    private static final String REENTER_GOLEM = "threatworks_reenter_next_golem";

    private EntityLiving probingMob;
    private int nestedNotifications;
    private UUID webSpider;
    private UUID webVictim;
    private BlockPos webImpact;
    private boolean webBeforeImpact;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("points_provider item=minecraft:stick registered={}",
                ThreatworksMod.registerItemThreatPoints(Items.STICK, (stack, slot) -> {
                    LOGGER.info("points_provider item=minecraft:stick slot={} count={} points=100", slot, stack.getCount());
                    return 100;
                }));
        MinecraftForge.EVENT_BUS.register(this);
        ForgeChunkManager.setForcedChunkLoadingCallback(this,
                (tickets, world) -> tickets.forEach(ForgeChunkManager::releaseTicket));
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new ThreatActionsCommand());
        event.registerServerCommand(new ArrowFixtureCommand());
        event.registerServerCommand(new SummonFixtureCommand(this));
        event.registerServerCommand(new EquipmentFixtureCommand());
        event.registerServerCommand(new WorkloadFixtureCommand());
        event.registerServerCommand(new CoverageFixtureCommand(this));
        event.registerServerCommand(new DimensionCoverageCommand(this));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingDamage(LivingDamageEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        Entity source = event.getSource().getTrueSource();
        Entity immediate = event.getSource().getImmediateSource();
        if (!victim.world.isRemote && victim.getTags().contains(DimensionCoverageCommand.TAG)
                && source instanceof EntityLiving && source.getTags().contains(DimensionCoverageCommand.TAG)
                && immediate != null && event.getAmount() > 0) {
            LOGGER.info("dimension impact mob={} target={} projectile={} source={} amount={} dimension={}",
                    source.getUniqueID(), victim.getUniqueID(), EntityList.getKey(immediate),
                    event.getSource().getDamageType(), event.getAmount(), victim.dimension);
        }
        if (!victim.world.isRemote && logsCoverageCombat(victim)
                && source instanceof EntityLiving && source.getTags().contains("tw_i_mob")
                && (victim.getTags().contains("tw_i_guard") || victim.getTags().contains("tw_i_player"))
                && event.getAmount() > 0) {
            LOGGER.info("coverage nativeImpact mob={} target={} source={} amount={}", source.getUniqueID(),
                    victim.getUniqueID(), event.getSource().getDamageType(), event.getAmount());
            String id = String.valueOf(EntityList.getKey(source));
            if (victim.getTags().contains("tw_i_player")
                    && ("specialmobs:webspider".equals(id) || "specialmobs:webcavespider".equals(id))
                    && (!source.getUniqueID().equals(webSpider) || !victim.getUniqueID().equals(webVictim))) {
                webSpider = source.getUniqueID();
                webVictim = victim.getUniqueID();
                webImpact = victim.getPosition();
                webBeforeImpact = hasWeb(victim, webImpact);
            }
        }
        if (!victim.world.isRemote && victim.getTags().contains("tw_summon_probe")) {
            SummonFixtureCommand.reportImpact(victim, event.getSource(), event.getAmount());
        }
        if (!victim.world.isRemote && victim.getTags().contains("tw_arrow_probe")
                && event.getSource().getImmediateSource() instanceof EntityArrow) {
            EntityArrow arrow = (EntityArrow) event.getSource().getImmediateSource();
            LOGGER.info("arrow_impact arrow={} victim={} shooter={} source={} amount={} worldTick={}",
                    arrow.getUniqueID(), victim.getUniqueID(), ArrowFixtureCommand.identity(arrow.shootingEntity),
                    ArrowFixtureCommand.identity(event.getSource().getTrueSource()), event.getAmount(),
                    victim.world.getTotalWorldTime());
        }
        if (!victim.world.isRemote && event.getAmount() > 0.0F
                && "player".equals(event.getSource().getDamageType()) && victim.removeTag(CANCEL_NEXT)) {
            event.setCanceled(true);
            LOGGER.info("canceled_player_damage victim={} amount={}", victim.getUniqueID(), event.getAmount());
        }
    }

    @SubscribeEvent
    public void onDoomCloud(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || !(event.getEntity() instanceof EntityAreaEffectCloud)) {
            return;
        }
        EntityAreaEffectCloud cloud = (EntityAreaEffectCloud) event.getEntity();
        EntityLivingBase owner = cloud.getOwner();
        if (!(owner instanceof EntityLiving) || !owner.getTags().contains("tw_i_mob")
                || !"specialmobs:doomcreeper".equals(String.valueOf(EntityList.getKey(owner)))) {
            return;
        }
        EntityLivingBase target = ((EntityLiving) owner).getAttackTarget();
        if (target == null) {
            return;
        }
        cloud.addTag(CoverageFixtureCommand.TAG);
        target.addTag("tw_l_doom_cloud_target");
        NBTTagList effects = cloud.writeToNBT(new NBTTagCompound()).getTagList("Effects", 10);
        for (int index = 0; index < effects.tagCount(); index++) {
            PotionEffect effect = PotionEffect.readCustomPotionEffectFromNBT(effects.getCompoundTagAt(index));
            if (effect != null) {
                LOGGER.info("coverage doomCloud mob={} target={} cloud={} potion={} amplifier={} duration={} radius={} distance={}",
                        owner.getUniqueID(), target.getUniqueID(), cloud.getUniqueID(), effect.getPotion().getRegistryName(),
                        effect.getAmplifier(), effect.getDuration(), cloud.getRadius(), cloud.getDistance(target));
            }
        }
    }

    void reportSpiderWeb(EntityCreature mob, EntityPlayerMP player) {
        boolean matches = mob.getUniqueID().equals(webSpider) && player.getUniqueID().equals(webVictim);
        LOGGER.info("coverage webSpiderEffect mob={} player={} before={} web={}", mob.getUniqueID(),
                player.getUniqueID(), matches && webBeforeImpact, matches && hasWeb(player, webImpact));
    }

    private static boolean hasWeb(EntityLivingBase victim, BlockPos position) {
        return victim.world.getBlockState(position).getBlock() == Blocks.WEB
                || victim.world.getBlockState(position.up()).getBlock() == Blocks.WEB;
    }

    @SubscribeEvent
    public void onGaiaPotion(PotionEvent.PotionAddedEvent event) {
        EntityLivingBase target = event.getEntityLiving();
        if (!target.world.isRemote && target.getTags().contains("tw_l_doom_cloud_target")) {
            PotionEffect effect = event.getPotionEffect();
            LOGGER.info("coverage doomPotion target={} potion={} amplifier={} duration={}", target.getUniqueID(),
                    effect.getPotion().getRegistryName(), effect.getAmplifier(), effect.getDuration());
        }
        if (!target.world.isRemote && target.getTags().contains("tw_l_gaia_effect")) {
            LOGGER.info("coverage gaiaPotion player={} potion={} amplifier={} duration={}", target.getUniqueID(),
                    event.getPotionEffect().getPotion().getRegistryName(), event.getPotionEffect().getAmplifier(),
                    event.getPotionEffect().getDuration());
        }
    }

    @SubscribeEvent
    public void onSummonCredit(SummonThreatCreditEvent event) {
        if (event.getTarget().removeTag("tw_redirect_next")) {
            event.creditOwner();
            LOGGER.info("summon_redirect target={} summon={} owner={} tick={}", event.getTarget().getUniqueID(),
                    event.getSummon().getUniqueID(), event.getOwner().getUniqueID(),
                    event.getTarget().world.getTotalWorldTime());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(LivingDeathEvent event) {
        EntityLivingBase player = event.getEntityLiving();
        if (player instanceof EntityPlayerMP && player.removeTag("threatworks_cancel_death")) {
            player.setHealth(4.0F);
            event.setCanceled(true);
            LOGGER.info("canceled_player_death player={} worldTick={}", player.getUniqueID(),
                    player.world.getTotalWorldTime());
        }
    }

    @SubscribeEvent
    public void onTargetSet(LivingSetAttackTargetEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity.world.isRemote || !(entity instanceof EntityLiving)) {
            return;
        }
        if (entity == probingMob) {
            nestedNotifications++;
            return;
        }
        EntityLivingBase target = event.getTarget();
        if (entity.getTags().contains("tw_i_mob") && entity.getServer().getFolderName().startsWith("TW-Campaign-J")) {
            boolean coordinated = coordinatedTargetWrite();
            LOGGER.info("phase_write mob={} requested={} coordinated={} tick={}", entity.getUniqueID(),
                    target == null ? null : target.getUniqueID(), coordinated, entity.getServer().getTickCounter());
            if (coordinated && entity.removeTag("tw_j_reject_assignment")) {
                LOGGER.info("phase_nestedGrant={}", ThreatworksMod.threatActions().grant(entity, target, 100.0));
                ReflectionHelper.setPrivateValue(EntityLiving.class, (EntityLiving) entity, null,
                        "attackTarget", "field_70696_bz");
                LOGGER.info("phase_rejected mob={} requested={}", entity.getUniqueID(),
                        target == null ? null : target.getUniqueID());
            }
        }
        if (logsCoverageCombat(entity)
                && (entity.getTags().contains("tw_i_mob") || entity.getTags().contains(DimensionCoverageCommand.TAG))) {
            boolean coordinated = coordinatedTargetWrite();
            LOGGER.info("coverage targetWrite mob={} requested={} coordinated={} tick={}", entity.getUniqueID(),
                    target == null ? null : target.getUniqueID(), coordinated, entity.getServer().getTickCounter());
            if (coordinated && target instanceof EntityPlayerMP && entity.getTags().contains("tw_l_sight_probe")) {
                LOGGER.info("coverage coordinatedSight mob={} player={} visible={} tick={}", entity.getUniqueID(),
                        target.getUniqueID(), entity.canEntityBeSeen(target), entity.getServer().getTickCounter());
            }
        }
        if (target != null && entity.getTags().contains("tw_g_mob_0")) {
            LOGGER.info("work_target mob={} target={} serverTick={}", entity.getUniqueID(), target.getUniqueID(),
                    entity.getServer().getTickCounter());
        }
        if (target != null && entity.removeTag("threatworks_taunt_during_target_write")) {
            LOGGER.info("nested_taunt mob={} participant={} result={} worldTick={}", entity.getUniqueID(),
                    target.getUniqueID(), ThreatworksMod.threatActions().taunt(entity, target),
                    entity.world.getTotalWorldTime());
        }
        boolean armed = target instanceof EntityPlayerMP && entity.removeTag(REENTER_PLAYER)
                || target instanceof EntityIronGolem && entity.removeTag(REENTER_GOLEM);
        if (!armed) {
            return;
        }
        EntityLiving mob = (EntityLiving) entity;
        probingMob = mob;
        nestedNotifications = 0;
        try {
            mob.setAttackTarget(target);
            mob.setAttackTarget(null);
        } finally {
            probingMob = null;
        }
        LOGGER.info("target_reentry mob={} target={} nestedNotifications={} retained={} worldTick={}",
                mob.getUniqueID(), target.getUniqueID(), nestedNotifications, mob.getAttackTarget() == target,
                mob.world.getTotalWorldTime());
    }

    private static boolean coordinatedTargetWrite() {
        return java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(frame -> frame.getClassName().equals("com.mahghuuuls.threatworks.server.EncounterCoordinator")
                        && frame.getMethodName().equals("writeTarget"));
    }

    private static boolean logsCoverageCombat(EntityLivingBase entity) {
        String folder = entity.getServer().getFolderName();
        return folder.startsWith("TW-Campaign-K") || folder.startsWith("TW-Campaign-L");
    }

    @SubscribeEvent
    public void onPhaseTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
            return;
        }
        String folder = event.world.getMinecraftServer().getFolderName();
        if (folder.startsWith("TW-Campaign-L")) {
            event.world.getEntities(EntityCreature.class, mob -> mob.getTags().contains("tw_l_avoidance_probe")
                    && mob.tasks.taskEntries.stream().anyMatch(entry -> entry.using && entry.action.getClass().getName()
                    .equals("fathertoast.specialai.ai.react.EntityAIAvoidExplosions"))).forEach(mob -> {
                mob.removeTag("tw_l_avoidance_probe");
                CoverageFixtureCommand.report(mob, (net.minecraft.world.WorldServer) event.world);
                EntityLivingBase participant = mob.getAttackTarget();
                LOGGER.info("coverage avoidanceTaunt={}", ThreatworksMod.threatActions().taunt(mob, participant));
            });
            return;
        }
        if (!folder.startsWith("TW-Campaign-J")) {
            return;
        }
        event.world.getEntities(EntityLiving.class, mob -> mob.getTags().contains("tw_i_mob")
                && mob.getTags().contains("tw_j_observe")).forEach(mob -> {
            com.mahghuuuls.threatworks.server.EncounterCoordinator.Inspection view =
                    com.mahghuuuls.threatworks.server.EncounterCoordinator.INSTANCE.inspect(mob, 16);
            String scores = view.participants.stream().map(p -> p.id + "=" + p.score)
                    .collect(java.util.stream.Collectors.joining(","));
            LOGGER.info("phase_state tick={} mob={} status={} target={} taunt={} reset={} scores={} animation={}",
                    mob.getServer().getTickCounter(), mob.getUniqueID(), view.status, view.target, view.taunter,
                    view.resetTicks, scores, nativeAnimation(mob));
        });
    }

    private static String nativeAnimation(EntityLiving mob) {
        if (!"mowziesmobs:foliaath".equals(String.valueOf(net.minecraft.entity.EntityList.getKey(mob)))) {
            return "none";
        }
        Class<?> type = mob.getClass();
        try {
            Object animation = type.getMethod("getAnimation").invoke(mob);
            if (animation == type.getField("ATTACK_ANIMATION").get(null)) {
                return "bite";
            }
            if (animation == type.getField("HURT_ANIMATION").get(null)) {
                return "hurt";
            }
            return type.getField("active").getBoolean(mob) ? "ready" : "inactive";
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Foliaath fixture could not read native animation", failure);
        }
    }
}
