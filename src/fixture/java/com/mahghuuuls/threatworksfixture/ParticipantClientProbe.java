package com.mahghuuuls.threatworksfixture;

import com.mahghuuuls.threatworks.ThreatworksMod;
import com.mahghuuuls.threatworks.api.ThreatQueries.State;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;

@Mod.EventBusSubscriber(modid = "threatworksfixture", value = Side.CLIENT)
public final class ParticipantClientProbe {
    private static boolean checked;

    private ParticipantClientProbe() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || checked) {
            return;
        }
        Minecraft game = Minecraft.getMinecraft();
        WorldClient world = game.world;
        if (world == null || game.player == null) {
            return;
        }
        for (Entity entity : world.loadedEntityList) {
            if (entity instanceof EntityLiving) {
                State state = ThreatworksMod.threatQueries().query((EntityLiving) entity, game.player).state();
                LogManager.getLogger("ThreatworksFixture").info("h1_client_world state={}", state);
                checked = true;
                return;
            }
        }
    }
}
