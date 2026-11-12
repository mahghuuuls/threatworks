package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.ThreatworksMod;
import com.mahghuuuls.threatworks.api.SummonThreatCreditEvent;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Items;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingDamage(LivingDamageEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
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
}
