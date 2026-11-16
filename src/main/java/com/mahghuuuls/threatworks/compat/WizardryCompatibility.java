package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.server.DamageAttribution;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.Logger;

public final class WizardryCompatibility {
    private static final ComparableVersion MINIMUM_VERSION = new ComparableVersion("4.3.19");

    private WizardryCompatibility() {}

    public static DamageAttribution.SummonSource load(Logger logger) {
        ModContainer mod = Loader.instance().getIndexedModList().get("ebwizardry");
        return load(mod == null || !Loader.isModLoaded("ebwizardry") ? null : mod.getVersion(), logger);
    }

    static DamageAttribution.SummonSource load(String version, Logger logger) {
        if (version == null) {
            return null;
        }
        if (new ComparableVersion(version).compareTo(MINIMUM_VERSION) < 0) {
            logger.warn("Skipping Wizardry summon attribution for version {}; requires {} or newer",
                    version, MINIMUM_VERSION);
            return null;
        }
        try {
            DamageAttribution.SummonSource adapter = WizardrySummonSource.load(logger);
            logger.info("Wizardry summon attribution enabled for {}", version);
            return adapter;
        } catch (LinkageError | RuntimeException failure) {
            logger.warn("Skipping Wizardry summon attribution: required minion attribution capabilities unavailable",
                    failure);
            return null;
        }
    }
}
