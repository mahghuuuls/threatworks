package com.mahghuuuls.threatworks;

import com.mahghuuuls.threatworks.api.ItemThreatPointProvider;
import com.mahghuuuls.threatworks.api.ThreatActions;
import com.mahghuuuls.threatworks.command.ThreatworksCommand;
import com.mahghuuuls.threatworks.compat.BaublesCompatibility;
import com.mahghuuuls.threatworks.compat.JafsCompatibility;
import com.mahghuuuls.threatworks.compat.QualityToolsCompatibility;
import com.mahghuuuls.threatworks.compat.SetworksCompatibility;
import com.mahghuuuls.threatworks.compat.WizardryCompatibility;
import com.mahghuuuls.threatworks.config.ServerTuning;
import com.mahghuuuls.threatworks.points.ThreatPoints;
import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(
        modid = Tags.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        acceptableRemoteVersions = "*",
        dependencies = "required-after:mixinbooter@[12.1,);after:jafs;after:setworks"
)
public final class ThreatworksMod {
    private static final Logger LOGGER = LogManager.getLogger(Tags.MOD_ID);
    private static final ThreatPoints POINTS = new ThreatPoints(LOGGER::warn);
    private File configFile;

    /** Returns the server action interface. Calls outside a loaded server world are unavailable. */
    public static ThreatActions threatActions() {
        return EncounterCoordinator.INSTANCE;
    }

    /**
     * Registers one base points provider for a registered item during Forge initialization or
     * post-initialization. Registration closes at load completion.
     * Returns false if the registration is invalid, conflicts, or arrives after registration closes.
     */
    public static boolean registerItemThreatPoints(Item item, ItemThreatPointProvider provider) {
        return POINTS.register(item, provider);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        configFile = event.getSuggestedConfigurationFile();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        POINTS.installFoodSource(JafsCompatibility.load(LOGGER));
        POINTS.installSetBonusSource(SetworksCompatibility.load(LOGGER));
        MinecraftForge.EVENT_BUS.register(EncounterCoordinator.INSTANCE);
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        POINTS.installEquipmentSources(BaublesCompatibility.load(LOGGER), QualityToolsCompatibility.load(LOGGER));
        POINTS.freezeRegistrations();
        EncounterCoordinator.INSTANCE.setSummonSource(WizardryCompatibility.load(LOGGER));
    }

    @Mod.EventHandler
    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        ServerTuning tuning = ServerTuning.load(configFile,
                id -> ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id)), LOGGER::warn);
        EncounterCoordinator.INSTANCE.startServer(tuning, POINTS);
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
