package com.mahghuuuls.threatworks.compat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.Logger;

import java.util.function.ToDoubleFunction;

public final class JafsCompatibility {
    private static final ComparableVersion MINIMUM = new ComparableVersion("1.2.0");

    private JafsCompatibility() {}

    public static ToDoubleFunction<EntityLivingBase> load(Logger logger) {
        ModContainer mod = Loader.instance().getIndexedModList().get("jafs");
        return load(mod == null || !Loader.isModLoaded("jafs") ? null : mod.getVersion(), logger);
    }

    static ToDoubleFunction<EntityLivingBase> load(String version, Logger logger) {
        if (version == null) {
            return null;
        }
        if (new ComparableVersion(version).compareTo(MINIMUM) < 0) {
            logger.warn("Skipping JAFS threat points for version {}; requires {} or newer", version, MINIMUM);
            return null;
        }
        try {
            JafsFoodPoints source = JafsFoodPoints.register(logger);
            logger.info("JAFS threat points enabled for {}", version);
            return source;
        } catch (LinkageError | ReflectiveOperationException | RuntimeException failure) {
            logger.warn("Skipping JAFS threat points: could not register the flat food property", failure);
            return null;
        }
    }
}
