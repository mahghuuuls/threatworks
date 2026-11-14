package com.mahghuuuls.threatworks.config;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import com.mahghuuuls.threatworks.threat.Encounter;
import com.mahghuuuls.threatworks.threat.Encounter.Rules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.mahghuuuls.threatworks.config.ServerTuning.MobType.SKELETON;
import static com.mahghuuuls.threatworks.config.ServerTuning.MobType.SPECIAL_SKELETON;
import static com.mahghuuuls.threatworks.config.ServerTuning.MobType.SPECIAL_ZOMBIE;
import static com.mahghuuuls.threatworks.config.ServerTuning.MobType.ZOMBIE;
import static com.mahghuuuls.threatworks.threat.Encounter.TargetObservation.IN_REACH;
import static com.mahghuuuls.threatworks.threat.Encounter.TargetObservation.obscured;
import static com.mahghuuuls.threatworks.threat.Encounter.TargetObservation.pursuit;
import static org.junit.jupiter.api.Assertions.*;

class ServerTuningTest {
    @TempDir Path directory;
    private final List<String> warnings = new ArrayList<>();
    private static final Set<String> VANILLA_DEFAULTS = new HashSet<>(Arrays.asList(
            "minecraft:zombie", "minecraft:husk", "minecraft:zombie_villager", "minecraft:skeleton",
            "minecraft:stray", "minecraft:wither_skeleton", "minecraft:spider", "minecraft:cave_spider",
            "minecraft:silverfish", "minecraft:endermite", "minecraft:vindication_illager", "minecraft:blaze",
            "minecraft:ghast", "minecraft:slime", "minecraft:magma_cube", "minecraft:shulker"));

    private static Set<String> enabledVanilla(ServerTuning tuning) {
        return Arrays.stream(MobType.values()).filter(tuning::isEnabled).map(type -> type.id)
                .filter(id -> id.startsWith("minecraft:")).collect(Collectors.toSet());
    }

    @Test
    void specialZombieVariantsRequireAnExactOptInInExpandedMode() throws Exception {
        ServerTuning fresh = TuningFiles.load(directory.resolve("fresh.cfg"), null, id -> true, warnings);
        String optIn = "S:mobOptIn <\n specialmobs:brutezombie\n specialmobs:plaguezombie\n >";
        ServerTuning expanded = TuningFiles.load(directory.resolve("expanded.cfg"), optIn, id -> true, warnings);
        ServerTuning legacy = TuningFiles.load(directory.resolve("legacy-zombies.cfg"),
                "S:enabledMobs <\n minecraft:zombie\n specialmobs:brutezombie\n >\n" + optIn, id -> true, warnings);

        assertFalse(fresh.isEnabled(MobType.BRUTE_ZOMBIE));
        assertTrue(expanded.isEnabled(MobType.BRUTE_ZOMBIE));
        assertTrue(expanded.isEnabled(MobType.PLAGUE_ZOMBIE));
        assertFalse(expanded.isEnabled(MobType.GIANT_ZOMBIE));
        assertTrue(legacy.isEnabled(ZOMBIE));
        assertFalse(legacy.isEnabled(MobType.BRUTE_ZOMBIE));
        assertFalse(legacy.isEnabled(MobType.PLAGUE_ZOMBIE));
    }

    @Test
    void freshFileAndReloadUseApprovedDefaults() throws Exception {
        Path file = directory.resolve("threatworks.cfg");
        ServerTuning created = TuningFiles.load(file, null, id -> true, warnings);
        assertTrue(Files.isRegularFile(file));
        ServerTuning reloaded = TuningFiles.load(file, null, id -> true, warnings);
        for (ServerTuning tuning : new ServerTuning[] {created, reloaded}) {
            assertEquals(VANILLA_DEFAULTS, enabledVanilla(tuning));
            assertTrue(tuning.isEnabled(SPECIAL_ZOMBIE));
            assertFalse(tuning.isEnabled(SPECIAL_SKELETON));
            assertNull(tuning.assignedPoints("minecraft:stick", null));
            Rules rules = tuning.rulesFor(ZOMBIE);
            assertArrayEquals(new double[] {0.2, 0.4, 8, 16, 10, 60, 200, 40}, new double[] {
                    rules.ordinaryLead, rules.maximumChaseLead, rules.chaseStart, rules.chaseEnd,
                    rules.openingThreat, rules.tauntTicks, rules.resetTicks, rules.sightTicks
            });
        }
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void legacyKeepsOnlyPreviouslyEnabledTypesAcrossRestartUntilExpanded() throws Exception {
        Path file = directory.resolve("legacy.cfg");
        ServerTuning legacy = TuningFiles.load(file, "S:enabledMobs <\n minecraft:zombie\n"
                + " specialmobs:specialzombie\n >\nS:mobOptIn <\n specialmobs:specialskeleton\n >\nD:openingThreat=16",
                id -> true, warnings);
        ServerTuning restarted = TuningFiles.load(file, null, id -> true, warnings);
        for (ServerTuning tuning : new ServerTuning[] {legacy, restarted}) {
            assertEquals(Collections.singleton("minecraft:zombie"), enabledVanilla(tuning));
            assertFalse(tuning.isEnabled(SPECIAL_ZOMBIE));
            assertFalse(tuning.isEnabled(SPECIAL_SKELETON));
            assertEquals(16, tuning.rulesFor(SPECIAL_ZOMBIE).openingThreat);
        }
        String saved = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(saved.contains("S:mobCoverage=legacy"));
        Files.write(file, saved.replace("S:mobCoverage=legacy", "S:mobCoverage=expanded")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ServerTuning expanded = TuningFiles.load(file, null, id -> true, warnings);
        assertEquals(VANILLA_DEFAULTS, enabledVanilla(expanded));
        assertTrue(expanded.isEnabled(SPECIAL_ZOMBIE));
        assertTrue(expanded.isEnabled(SPECIAL_SKELETON));
        assertEquals(16, expanded.rulesFor(SPECIAL_ZOMBIE).openingThreat);
    }

    @Test
    void emptyLegacyListRemainsEmptyAcrossRestart() throws Exception {
        Path file = directory.resolve("empty.cfg");
        ServerTuning legacy = TuningFiles.load(file, "S:enabledMobs <\n >", id -> true, warnings);
        ServerTuning restarted = TuningFiles.load(file, null, id -> true, warnings);
        for (ServerTuning tuning : new ServerTuning[] {legacy, restarted}) {
            assertFalse(tuning.isEnabled(ZOMBIE));
            assertFalse(tuning.isEnabled(SKELETON));
            assertFalse(tuning.isEnabled(SPECIAL_ZOMBIE));
            assertFalse(tuning.isEnabled(SPECIAL_SKELETON));
        }
    }

    @Test
    void blacklistRetainsExactAbsentIdsAndReportsEachBadValueOncePerLoad() throws Exception {
        Path file = directory.resolve("blacklist.cfg");
        ServerTuning tuning = TuningFiles.load(file, "S:mobBlacklist <\n minecraft:zombie\n minecraft:zombie\n"
                + " absent:mob\n specialmobs:specialskeleton\n bad\n bad\n >\nS:mobOptIn <\n"
                + " specialmobs:specialskeleton\n minecraft:creeper\n minecraft:creeper\n >", id -> false, warnings);
        assertTrue(tuning.isBlacklisted("minecraft:zombie"));
        assertTrue(tuning.isBlacklisted("specialmobs:specialskeleton"));
        assertTrue(tuning.isBlacklisted("absent:mob"));
        assertFalse(tuning.isBlacklisted("bad"));
        assertFalse(tuning.isBlacklisted("absent:other"));
        assertTrue(tuning.isEnabled(SPECIAL_SKELETON));
        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("mobBlacklist entry 'bad'")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("mobOptIn entry 'minecraft:creeper'")));
    }

    @Test
    void rejectsUnsafeEntriesAndPairsButKeepsUnrelatedSettings() throws Exception {
        Path file = directory.resolve("threatworks.cfg");
        ServerTuning tuning = TuningFiles.load(file, "D:ordinarySwitchLead=0.8\nD:maximumChaseLead=0.1\n"
                + "D:chaseIncreaseStart=17\nD:chaseIncreaseEnd=12\nD:openingThreat=15\n"
                + "D:tauntDuration=1.25\nD:noEligibleResetDelay=0\nD:sightLossGrace=Infinity\n"
                + "S:enabledMobs <\n minecraft:zombie\n minecraft:creeper\n >\n"
                + "S:openingThreatOverrides <\n minecraft:zombie=29\n minecraft:skeleton=NaN\n >\n"
                + "S:itemThreatPointAssignments <\n minecraft:stick=50\n minecraft:stick=10\n minecraft:stick@0=0\n"
                + " minecraft:stick@1=NaN\n minecraft:stone@2=-40\n minecraft:missing=100\n >",
                id -> !"minecraft:missing".equals(id), warnings);
        assertTrue(tuning.isEnabled(ZOMBIE));
        assertFalse(tuning.isEnabled(SKELETON));
        Rules zombie = tuning.rulesFor(ZOMBIE);
        assertArrayEquals(new double[] {0.2, 0.4, 8, 16, 29, 25, 0, 40}, new double[] {
                zombie.ordinaryLead, zombie.maximumChaseLead, zombie.chaseStart, zombie.chaseEnd,
                zombie.openingThreat, zombie.tauntTicks, zombie.resetTicks, zombie.sightTicks
        });
        assertEquals(15, tuning.rulesFor(SKELETON).openingThreat);
        assertEquals(50, tuning.assignedPoints("minecraft:stick", null).doubleValue());
        assertEquals(0, tuning.assignedPoints("minecraft:stick", 0).doubleValue());
        assertNull(tuning.assignedPoints("minecraft:stick", 1));
        assertEquals(-40, tuning.assignedPoints("minecraft:stone", 2).doubleValue());
        assertNull(tuning.assignedPoints("minecraft:missing", null));
        for (String part : new String[] {"ordinarySwitchLead=0.2, maximumChaseLead=0.4",
                "chaseIncreaseStart=8.0, chaseIncreaseEnd=16.0", "sightLossGrace=Infinity is invalid; using 2.0",
                "minecraft:creeper", "minecraft:skeleton=NaN' is invalid; using 15.0",
                "minecraft:stick=10'; keeping 50.0", "minecraft:stick@1=NaN'; using pack value 50.0", "minecraft:missing"}) {
            assertTrue(warnings.stream().anyMatch(warning -> warning.contains(part)), part + ": " + warnings);
        }
    }

    @Test
    void loadedRulesDriveSeedSwitchingTauntSightAndReset() throws Exception {
        ServerTuning tuning = TuningFiles.load(directory.resolve("threatworks.cfg"),
                "D:ordinarySwitchLead=0.5\nD:maximumChaseLead=1\nD:chaseIncreaseStart=2\nD:chaseIncreaseEnd=4\n"
                + "D:tauntDuration=0.25\nD:noEligibleResetDelay=0.5\nD:sightLossGrace=0\n"
                + "S:openingThreatOverrides <\n minecraft:zombie=20\n >", id -> true, warnings);
        Rules rules = tuning.rulesFor(ZOMBIE);
        UUID incumbent = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Encounter fight = Encounter.recordHit(null, player, incumbent, 100, 35, rules);
        assertEquals(20, fight.copyScores().get(incumbent).doubleValue());
        assertEquals(incumbent, fight.selectTarget(103, id -> id.equals(incumbent) ? IN_REACH : pursuit(3)));
        assertEquals(player, fight.selectTarget(104, id -> IN_REACH));
        assertSame(fight, Encounter.tryTaunt(fight, player, incumbent, 105, id -> IN_REACH, rules));
        assertEquals(5, fight.remainingTauntTicks(105));
        assertEquals(0, fight.remainingTauntTicks(110));
        assertNull(fight.selectTarget(111, id -> obscured(true)));
        assertFalse(fight.resetDue(120));
        assertTrue(fight.resetDue(121));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }
}
