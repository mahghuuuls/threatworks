package com.mahghuuuls.threatworks;

import com.mahghuuuls.threatworks.api.ThreatActions;
import com.mahghuuuls.threatworks.command.ThreatworksCommand;
import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;

@Mod(
        modid = Tags.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        acceptableRemoteVersions = "*",
        dependencies = "required-after:mixinbooter@[12.1,)"
)
public final class ThreatworksMod {
    /** Returns the server action interface. Calls outside a loaded server world are unavailable. */
    public static ThreatActions threatActions() {
        return EncounterCoordinator.INSTANCE;
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(EncounterCoordinator.INSTANCE);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new ThreatworksCommand());
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        EncounterCoordinator.INSTANCE.serverStopped();
    }
}
