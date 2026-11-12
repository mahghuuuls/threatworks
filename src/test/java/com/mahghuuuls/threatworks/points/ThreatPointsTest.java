package com.mahghuuuls.threatworks.points;

import com.mahghuuuls.threatworks.config.ServerTuning;
import com.mahghuuuls.threatworks.config.TuningFiles;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.entity.item.EntityArmorStand;
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
import java.util.concurrent.atomic.AtomicInteger;

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
        assertEquals(1, points.multiplier(slot -> {
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
            assertEquals(2, points.multiplier(slot -> equipment.getOrDefault(slot, ItemStack.EMPTY), tuning));
        }
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).contains("minecraft:stick"));
        assertTrue(warnings.get(1).contains("minecraft:blaze_rod"));
    }

    @Test
    void readsCurrentFoodOnceAndCombinesItWithEquipmentBeforeConversion() throws Exception {
        ThreatPoints points = new ThreatPoints(warnings::add);
        EntityArmorStand wearer = new EntityArmorStand(null);
        wearer.setSilent(true);
        wearer.setItemStackToSlot(OFFHAND, new ItemStack(Items.STICK));
        ServerTuning tuning = loadAssignments("minecraft:stick=100");
        AtomicInteger current = new AtomicInteger(-100);
        AtomicInteger reads = new AtomicInteger();
        points.installFoodSource(entity -> {
            assertSame(wearer, entity);
            reads.incrementAndGet();
            return current.get();
        });

        assertEquals(1, points.multiplier(wearer, tuning));
        current.set(0);
        assertEquals(2, points.multiplier(wearer, tuning));
        current.set(100);
        assertEquals(3, points.multiplier(wearer, tuning));
        assertEquals(3, reads.get());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void convertsSignedTotalsAndPreservesSmallPositiveGains() {
        assertEquals(0, ThreatPoints.damageThreat(0, 2));
        assertEquals(3, ThreatPoints.damageThreat(3, 1));
        assertEquals(6, ThreatPoints.damageThreat(3, 2));
        assertEquals(1.5, ThreatPoints.damageThreat(3, 0.5));
        assertTrue(ThreatPoints.damageThreat(Float.MIN_VALUE, 1e-307) > 0);
        assertEquals(Double.MIN_VALUE, ThreatPoints.damageThreat(Double.MIN_VALUE, 1e-307));
        assertEquals(Double.MAX_VALUE, ThreatPoints.damageThreat(Float.MAX_VALUE, 1e307));
    }

    private ServerTuning loadAssignments(String assignments) throws Exception {
        return TuningFiles.load(directory.resolve("threatworks.cfg"),
                "S:itemThreatPointAssignments <\n " + assignments + "\n >", id -> true, warnings);
    }
}
