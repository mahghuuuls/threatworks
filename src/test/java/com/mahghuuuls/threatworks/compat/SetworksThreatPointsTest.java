package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.setworks.api.CombinationRule;
import com.mahghuuuls.threatworks.config.TuningFiles;
import com.mahghuuuls.threatworks.config.ServerTuning;
import com.mahghuuuls.threatworks.points.ThreatPoints;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.init.Bootstrap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SetworksThreatPointsTest {
    @TempDir Path directory;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void combinesDistinctInstancesAndRemovesOnlyTheNamedInstance() throws Exception {
        SetworksThreatPoints effects = new SetworksThreatPoints();
        ThreatPoints points = new ThreatPoints(message -> fail(message));
        points.installSetBonusSource(effects);
        points.installFoodSource(wearer -> 100);
        EntityArmorStand player = new EntityArmorStand(null);
        ServerTuning tuning = TuningFiles.load(directory.resolve("threatworks.cfg"), null, id -> true, new ArrayList<>());

        effects.applyTo(player, "first", amount("-100"));
        effects.applyTo(player, "first", amount("-100"));
        effects.applyTo(player, "second", amount("50"));
        assertEquals(1.5, points.multiplier(player, tuning));
        effects.withdrawFrom(player, "first");
        effects.withdrawFrom(player, "first");
        assertEquals(2.5, points.multiplier(player, tuning));
        effects.withdrawFrom(player, "second");
        assertEquals(2, points.multiplier(player, tuning));
    }

    @Test
    void replacementDoesNotInheritOrWithdrawTheNewInstancesBonus() {
        SetworksThreatPoints effects = new SetworksThreatPoints();
        EntityArmorStand oldPlayer = new EntityArmorStand(null);
        EntityArmorStand replacement = new EntityArmorStand(null);
        replacement.setUniqueId(oldPlayer.getUniqueID());
        effects.applyTo(oldPlayer, "same-bonus", amount("25"));
        assertEquals(0, collected(effects, replacement));
        effects.applyTo(replacement, "same-bonus", amount("50"));
        effects.withdrawFrom(oldPlayer, "same-bonus");
        effects.applyTo(oldPlayer, "other-bonus", amount("25"));
        effects.discard(oldPlayer);
        assertEquals(0, collected(effects, oldPlayer));
        assertEquals(50, collected(effects, replacement));
        effects.clear();
        assertEquals(0, collected(effects, replacement));
    }

    @Test
    void validatesTheEffectParameters() {
        SetworksThreatPoints effects = new SetworksThreatPoints();
        assertEquals("threatworks:threat_points", effects.id());
        assertEquals(CombinationRule.SUM, effects.combinationRule());
        assertNull(effects.validate(amount("-50")));
        assertNull(effects.validate(amount("100")));
        assertNotNull(effects.validate(amount("NaN")));
        assertNotNull(effects.validate(amount("Infinity")));
        assertNotNull(effects.validate(amount("garbage")));
        assertNotNull(effects.validate(Collections.emptyMap()));
    }

    @Test
    void declinesMissingAndOlderSetworks() {
        Logger logger = (Logger) LogManager.getLogger(SetworksThreatPointsTest.class);
        List<String> warnings = new ArrayList<>();
        AbstractAppender capture = new AbstractAppender("setworks-test", null, null) {
            @Override
            public void append(LogEvent event) {
                warnings.add(event.getMessage().getFormattedMessage());
            }
        };
        capture.start();
        logger.addAppender(capture);
        try {
            assertNull(SetworksCompatibility.load((String) null, logger));
            assertTrue(warnings.isEmpty());
            assertNull(SetworksCompatibility.load("1.0.9", logger));
            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("requires 1.1.0 or newer"));
        } finally {
            logger.removeAppender(capture);
            capture.stop();
        }
    }

    private static Map<String, String> amount(String value) {
        return Collections.singletonMap("amount", value);
    }

    private static double collected(SetworksThreatPoints effects, EntityArmorStand player) {
        double[] total = {0};
        effects.collect(player, value -> total[0] += value);
        return total[0];
    }
}
