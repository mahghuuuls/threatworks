package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.points.ThreatPoints;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.Logger;

public final class StatusworksCompatibility {
    private static final String MOD_ID = "statusworks";
    private static final ComparableVersion MINIMUM = new ComparableVersion("1.3.0");

    private StatusworksCompatibility() {}

    public static ThreatPoints.StatusSource load(Logger logger) {
        ModContainer mod = Loader.instance().getIndexedModList().get(MOD_ID);
        return load(mod == null || !Loader.isModLoaded(MOD_ID) ? null : mod.getVersion(), logger);
    }

    static ThreatPoints.StatusSource load(String version, Logger logger) {
        if (version == null) {
            return null;
        }
        if (new ComparableVersion(version).compareTo(MINIMUM) < 0) {
            logger.warn("Skipping Statusworks threat points for version {}; requires {} or newer", version, MINIMUM);
            return null;
        }
        try {
            ThreatPoints.StatusSource source = StatusworksThreatPoints.register(logger);
            logger.info("Statusworks threat points enabled for {}", version);
            return source;
        } catch (LinkageError | RuntimeException failure) {
            logger.warn("Skipping Statusworks threat points: could not register the contribution type", failure);
            return null;
        }
    }
}
