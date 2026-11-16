package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.statusworks.api.ContributionService;
import com.mahghuuuls.statusworks.api.contribution.ContributionGroup;
import com.mahghuuuls.statusworks.api.contribution.GroupAvailability;
import com.mahghuuuls.threatworks.config.ServerTuning;
import com.mahghuuuls.threatworks.config.TuningFiles;
import com.mahghuuuls.threatworks.points.ThreatPoints;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.init.Bootstrap;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

class StatusworksThreatPointsTest {
    @TempDir Path directory;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void onlyCombinesAvailableFiniteValuesAndReadsAgainOnTheNextAction() throws Exception {
        ThreatPoints points = new ThreatPoints(message -> fail(message));
        EntityArmorStand wearer = new EntityArmorStand(null);
        AtomicReference<ContributionGroup> current = new AtomicReference<>();
        points.installFoodSource(entity -> 100);
        points.installStatusSource(new StatusworksThreatPoints(new ContributionService() {
            @Override
            public Map<String, Double> currentValues(Entity entity, String typeId) {
                return fail("Use the availability-aware group query");
            }

            @Override
            public ContributionGroup group(Entity entity, String typeId) {
                assertEquals(wearer, entity);
                assertEquals("threatworks:threat_points", typeId);
                return current.get();
            }
        }, LogManager.getLogger(StatusworksThreatPointsTest.class)));
        ServerTuning tuning = TuningFiles.load(directory.resolve("threatworks.cfg"), null, id -> true, new ArrayList<>());
        Map<String, Double> entries = new LinkedHashMap<>();
        entries.put("fixed", -150.0);
        entries.put("stacked", 50.0);
        entries.put("invalid", Double.POSITIVE_INFINITY);
        current.set(ContributionGroup.available(entries));
        assertEquals(1.0, points.multiplier(wearer, tuning));

        for (GroupAvailability state : GroupAvailability.values()) {
            if (state != GroupAvailability.AVAILABLE) {
                current.set(ContributionGroup.of(state));
                assertEquals(2.0, points.multiplier(wearer, tuning));
            }
        }

        entries.put("stacked", 100.0);
        current.set(ContributionGroup.available(entries));
        assertEquals(1.5, points.multiplier(wearer, tuning));
    }

    @Test
    void skipsAbsentAndOlderProvidersBeforeRegistration() {
        assertNull(StatusworksCompatibility.load((String) null, LogManager.getLogger()));
        assertNull(StatusworksCompatibility.load("1.2.0", LogManager.getLogger()));
    }
}
