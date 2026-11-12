package com.mahghuuuls.threatworks.points;

import com.mahghuuuls.threatworks.config.ServerTuning;
import com.mahghuuuls.threatworks.config.TuningFiles;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static net.minecraft.inventory.EntityEquipmentSlot.*;
import static org.junit.jupiter.api.Assertions.*;

class ThreatPointsTest {
    @TempDir Path directory;
    private final List<String> warnings = new ArrayList<>();

    @BeforeAll
    static void loadVanillaItems() {
        Bootstrap.register();
    }

    @Test
    void resolvesExactThenWildcardThenProviderOncePerNonemptySlot() throws Exception {
        ServerTuning tuning = loadAssignments("minecraft:stick=50\n minecraft:stick@1=0\n minecraft:leather_boots=-150");
        ThreatPoints points = new ThreatPoints(warnings::add);
        List<EntityEquipmentSlot> called = new ArrayList<>();
        assertTrue(points.register(Items.STICK, (stack, slot) -> {
            fail("Pack override must bypass provider");
            return 0;
        }));
        assertTrue(points.register(Items.BLAZE_ROD, (stack, slot) -> {
            called.add(slot);
            return 100;
        }));
        assertFalse(points.register(Items.BLAZE_ROD, (stack, slot) -> -1000));
        points.freezeRegistrations();
        assertFalse(points.register(Items.APPLE, (stack, slot) -> 100));

        Map<EntityEquipmentSlot, ItemStack> equipment = new EnumMap<>(EntityEquipmentSlot.class);
        equipment.put(MAINHAND, new ItemStack(Items.STICK, 64, 1));
        equipment.put(OFFHAND, new ItemStack(Items.STICK, 32, 2));
        equipment.put(HEAD, new ItemStack(Items.BLAZE_ROD));
        equipment.put(FEET, new ItemStack(Items.LEATHER_BOOTS));
        equipment.put(LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
        List<EntityEquipmentSlot> read = new ArrayList<>();
        assertEquals(0, points.total(slot -> {
            read.add(slot);
            return equipment.getOrDefault(slot, ItemStack.EMPTY);
        }, tuning));
        assertEquals(Arrays.asList(EntityEquipmentSlot.values()), read);
        assertEquals(Arrays.asList(HEAD), called);
        assertEquals(2, warnings.size());
    }

    @Test
    void providerFailuresLeaveOtherSourcesWorkingAndWarnOnce() throws Exception {
        ServerTuning tuning = loadAssignments("minecraft:leather_boots=100");
        ThreatPoints points = new ThreatPoints(warnings::add);
        assertTrue(points.register(Items.STICK, (stack, slot) -> Double.NaN));
        assertTrue(points.register(Items.BLAZE_ROD, (stack, slot) -> {
            throw new IllegalArgumentException("fixture");
        }));
        points.freezeRegistrations();
        Map<EntityEquipmentSlot, ItemStack> equipment = new EnumMap<>(EntityEquipmentSlot.class);
        equipment.put(MAINHAND, new ItemStack(Items.STICK));
        equipment.put(OFFHAND, new ItemStack(Items.BLAZE_ROD));
        equipment.put(FEET, new ItemStack(Items.LEATHER_BOOTS));
        for (int i = 0; i < 3; i++) {
            assertEquals(100, points.total(slot -> equipment.getOrDefault(slot, ItemStack.EMPTY), tuning));
        }
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).contains("minecraft:stick"));
        assertTrue(warnings.get(1).contains("minecraft:blaze_rod"));
    }

    @Test
    void convertsSignedTotalsAndPreservesSmallPositiveGains() {
        assertEquals(0, ThreatPoints.damageThreat(0, 100));
        assertEquals(3, ThreatPoints.damageThreat(3, 0));
        assertEquals(6, ThreatPoints.damageThreat(3, 100));
        assertEquals(1.5, ThreatPoints.damageThreat(3, -100));
        assertTrue(ThreatPoints.damageThreat(Float.MIN_VALUE, -ServerTuning.MAX_ITEM_POINTS) > 0);
        assertEquals(Double.MIN_VALUE, ThreatPoints.damageThreat(Double.MIN_VALUE, -ServerTuning.MAX_ITEM_POINTS));
        assertEquals(Double.MAX_VALUE, ThreatPoints.damageThreat(Float.MAX_VALUE, ServerTuning.MAX_ITEM_POINTS));
    }

    private ServerTuning loadAssignments(String assignments) throws Exception {
        return TuningFiles.load(directory.resolve("threatworks.cfg"),
                "S:itemThreatPointAssignments <\n " + assignments + "\n >", id -> true, warnings);
    }
}
