package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.statusworks.api.ContributionService;
import com.mahghuuuls.statusworks.api.RegistrationResult;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.api.contribution.ContributionGroup;
import com.mahghuuuls.statusworks.api.contribution.ContributionGroupType;
import com.mahghuuuls.threatworks.points.ThreatPoints;
import net.minecraft.entity.EntityLivingBase;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.function.DoubleConsumer;

final class StatusworksThreatPoints implements ThreatPoints.StatusSource, ContributionGroupType {
    private static final String ID = "threatworks:threat_points";

    private final ContributionService contributions;
    private final Logger logger;
    private boolean warned;

    StatusworksThreatPoints(ContributionService contributions, Logger logger) {
        this.contributions = contributions;
        this.logger = logger;
    }

    static StatusworksThreatPoints register(Logger logger) {
        StatusworksThreatPoints source = new StatusworksThreatPoints(StatusworksApi.contributions(), logger);
        RegistrationResult result = StatusworksApi.registerContributionType(source);
        if (!result.isAccepted()) {
            throw new IllegalStateException(result.error());
        }
        return source;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean appliesTo(EntityLivingBase entity) {
        return true;
    }

    // Statusworks owns the current group and lifetime. Signal callbacks need no local state.
    @Override
    public boolean invalidate(EntityLivingBase entity) {
        return true;
    }

    @Override
    public void settled(EntityLivingBase entity, Map<String, Double> group) {}

    @Override
    public void onValue(EntityLivingBase entity, String key, double value) {}

    @Override
    public void onEnd(EntityLivingBase entity, String key) {}

    @Override
    public void collect(EntityLivingBase wearer, DoubleConsumer points) {
        Map<String, Double> current;
        try {
            ContributionGroup group = contributions.group(wearer, ID);
            if (!group.isAvailable()) {
                return;
            }
            current = group.values();
        } catch (RuntimeException | LinkageError failure) {
            warnOnce("could not read the active contribution group", failure);
            return;
        }
        for (Double value : current.values()) {
            if (value != null && Double.isFinite(value)) {
                points.accept(value);
            } else {
                warnOnce("invalid value in the active contribution group", null);
            }
        }
    }

    private void warnOnce(String reason, Throwable failure) {
        if (!warned) {
            warned = true;
            logger.warn("Ignoring Statusworks threat points: " + reason, failure);
        }
    }
}
