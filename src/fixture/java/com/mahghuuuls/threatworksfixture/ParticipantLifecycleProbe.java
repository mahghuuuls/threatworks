package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.ThreatworksMod;
import com.mahghuuuls.threatworks.api.ThreatActions;
import com.mahghuuuls.threatworks.api.ThreatQueries.Confirmation;
import com.mahghuuuls.threatworks.api.ThreatQueries.State;
import com.mahghuuuls.threatworks.api.ThreatQueries.View;
import net.minecraft.command.CommandException;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ParticipantLifecycleProbe {
    private static final Logger LOGGER = LogManager.getLogger("ThreatworksFixture");
    private final ParticipantQueryConsumer consumer = new ParticipantQueryConsumer();
    private final EntityLivingBase mob;
    private final EntityPlayerMP oldPlayer;
    private final EntityPlayerMP anchor;
    private final WorldServer world;
    private final int started;
    private EntityPlayerMP rejoined;
    private boolean logoutSeen;
    private boolean logoutVerified;

    private ParticipantLifecycleProbe(EntityLivingBase mob, EntityPlayerMP player, EntityPlayerMP anchor) {
        this.mob = mob;
        oldPlayer = player;
        this.anchor = anchor;
        world = player.getServerWorld();
        started = world.getMinecraftServer().getTickCounter();
    }

    static void arm(EntityLivingBase mob, EntityPlayerMP player) throws CommandException {
        if (!player.getServerWorld().isSpawnChunk(mob.chunkCoordX, mob.chunkCoordZ)) {
            throw new CommandException("The lifecycle mob must be in a retained spawn chunk");
        }
        EntityPlayerMP anchor = ParticipantQueryFixture.fake(player.getServerWorld(), "HitraHold",
                mob.posX + 1, mob.posY, mob.posZ);
        if (ThreatworksMod.threatActions().grant(mob, anchor, 2) != ThreatActions.Result.APPLIED) {
            player.getServerWorld().removeEntityDangerously(anchor);
            throw new CommandException("Lifecycle fixture needs an active encounter");
        }
        MinecraftForge.EVENT_BUS.register(new ParticipantLifecycleProbe(mob, player, anchor));
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player == oldPlayer) logoutSeen = true;
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (logoutSeen && event.player != oldPlayer && event.player instanceof EntityPlayerMP
                && event.player.getUniqueID().equals(oldPlayer.getUniqueID())) {
            rejoined = (EntityPlayerMP) event.player;
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world != world) return;
        if (world.getMinecraftServer().getTickCounter() - started > 2400) {
            LOGGER.error("h1_lifecycle_timed_out");
            stop();
        } else if (logoutSeen && !logoutVerified && world.getEntityFromUuid(oldPlayer.getUniqueID()) != oldPlayer) {
            View departed = consumer.read(mob, oldPlayer);
            View retained = consumer.read(mob, anchor);
            ThreatQueryCommand.report("disconnected", mob, oldPlayer, departed);
            ThreatQueryCommand.report("retained_without_client", mob, anchor, retained);
            logoutVerified = departed.state() == State.INVALID_PARTICIPANT && !departed.tauntTicks().isPresent()
                    && departed.aggro() == Confirmation.UNKNOWN && departed.eligibility() == Confirmation.UNKNOWN
                    && retained.state() == State.TRACKED && retained.threat().orElse(0) == 2;
            if (logoutVerified) LOGGER.info("h1_lifecycle_logout oldEntityId={}", oldPlayer.getEntityId());
            else stop();
        } else if (logoutVerified && rejoined != null && world.getEntityFromUuid(rejoined.getUniqueID()) == rejoined) {
            View stale = consumer.read(mob, oldPlayer);
            View fresh = consumer.read(mob, rejoined);
            ThreatQueryCommand.report("stale_after_rejoin", mob, oldPlayer, stale);
            ThreatQueryCommand.report("current_after_rejoin", mob, rejoined, fresh);
            if (stale.state() == State.INVALID_PARTICIPANT && fresh.state() == State.TRACKED
                    && fresh.tauntActive() == Confirmation.NO && fresh.tauntForcingFocus() == Confirmation.NO) {
                LOGGER.info("h1_lifecycle_rejoin oldEntityId={} newEntityId={} uuid={}", oldPlayer.getEntityId(),
                        rejoined.getEntityId(), rejoined.getUniqueID());
            }
            stop();
        }
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (event.getWorld() == world) MinecraftForge.EVENT_BUS.unregister(this);
    }

    private void stop() {
        MinecraftForge.EVENT_BUS.unregister(this);
        world.removeEntityDangerously(anchor);
    }
}
