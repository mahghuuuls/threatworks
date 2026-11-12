package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.points.ThreatPoints;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.Logger;

public final class BaublesCompatibility {
    private static final ComparableVersion MINIMUM = new ComparableVersion("1.5.2");

    private BaublesCompatibility() {}

    public static ThreatPoints.Accessories load(Logger logger) {
        ModContainer mod = Loader.instance().getIndexedModList().get("baubles");
        return load(mod == null || !Loader.isModLoaded("baubles") ? null : mod.getVersion(), logger);
    }

    static ThreatPoints.Accessories load(String version, Logger logger) {
        if (version == null) {
            return null;
        }
        if (new ComparableVersion(version).compareTo(MINIMUM) < 0) {
            logger.warn("Skipping Baubles threat points for version {}; requires {} or newer", version, MINIMUM);
            return null;
        }
        try {
            ThreatPoints.Accessories source = new BaublesEquipment(logger);
            logger.info("Baubles threat points enabled for {}", version);
            return source;
        } catch (LinkageError | RuntimeException failure) {
            logger.warn("Skipping Baubles threat points: the equipped slot API is incompatible", failure);
            return null;
        }
    }
}
