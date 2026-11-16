package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.server.DamageAttribution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

public final class WizardryCompatibility {
    private static final ComparableVersion MINIMUM_VERSION = new ComparableVersion("4.3.19");
    // Wizardry deserializes a missing caster tag as the all-zero UUID.
    static final UUID UNSET_CASTER = new UUID(0, 0);

    private WizardryCompatibility() {}

    public static DamageAttribution.SummonSource load(Logger logger) {
        ModContainer mod = Loader.instance().getIndexedModList().get("ebwizardry");
        return load(mod == null || !Loader.isModLoaded("ebwizardry") ? null : mod.getVersion(), logger);
    }

    static boolean supportedVersion(String version) {
        return version != null && new ComparableVersion(version).compareTo(MINIMUM_VERSION) >= 0;
    }

    static boolean hasCaster(UUID id) {
        return id != null && !UNSET_CASTER.equals(id);
    }

    static EntityLivingBase liveCaster(EntityLivingBase summon, UUID id) {
        Entity caster = hasCaster(id) && summon.world instanceof WorldServer
                ? ((WorldServer) summon.world).getEntityFromUuid(id) : null;
        return caster != summon && caster instanceof EntityLivingBase && caster.world == summon.world
                && caster.isEntityAlive() ? (EntityLivingBase) caster : null;
    }

    static DamageAttribution.SummonSource load(String version, Logger logger) {
        if (version == null) {
            return null;
        }
        if (!supportedVersion(version)) {
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
