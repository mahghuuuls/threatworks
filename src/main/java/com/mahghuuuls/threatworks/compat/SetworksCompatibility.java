package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.points.ThreatPoints;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.Logger;

public final class SetworksCompatibility {
    private static final ComparableVersion MINIMUM = new ComparableVersion("1.1.0");

    private SetworksCompatibility() {}

    public static ThreatPoints.SetBonusSource load(Logger logger) {
        ModContainer mod = Loader.instance().getIndexedModList().get("setworks");
        return load(mod == null || !Loader.isModLoaded("setworks") ? null : mod.getVersion(), logger);
    }

    static ThreatPoints.SetBonusSource load(String version, Logger logger) {
        if (version == null) {
            return null;
        }
        if (new ComparableVersion(version).compareTo(MINIMUM) < 0) {
            logger.warn("Skipping Setworks threat points for version {}; requires {} or newer", version, MINIMUM);
            return null;
        }
        try {
            ThreatPoints.SetBonusSource source = SetworksThreatPoints.register();
            logger.info("Setworks threat points enabled for {}", version);
            return source;
        } catch (LinkageError | RuntimeException failure) {
            logger.warn("Skipping Setworks threat points: could not register the threat points effect", failure);
            return null;
        }
    }
}
