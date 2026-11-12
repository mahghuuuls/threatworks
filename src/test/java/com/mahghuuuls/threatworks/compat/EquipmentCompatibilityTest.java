package com.mahghuuuls.threatworks.compat;

import baubles.api.cap.BaublesContainer;
import com.mahghuuuls.threatworks.api.ItemThreatPointProvider;
import com.mahghuuuls.threatworks.config.ServerTuning;
import com.mahghuuuls.threatworks.config.TuningFiles;
import com.mahghuuuls.threatworks.points.ThreatPoints;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentCompatibilityTest {
    private static final Logger LOGGER = LogManager.getLogger(EquipmentCompatibilityTest.class);
    @TempDir Path directory;

    @BeforeAll
    static void loadItems() {
        Bootstrap.register();
    }

    @Test
    void admitsThePinnedMetadataAndUsesMinimumVersions() {
        assertNull(BaublesCompatibility.load(null, LOGGER));
        assertNull(BaublesCompatibility.load("1.5.1", LOGGER));
        assertNotNull(BaublesCompatibility.load("1.5.2", LOGGER));
        assertNotNull(BaublesCompatibility.load("1.5.3", LOGGER));
        assertNull(QualityToolsCompatibility.load(null, LOGGER));
        assertNull(QualityToolsCompatibility.load("1.0.6_for_1.12.2", LOGGER));
        assertNotNull(QualityToolsCompatibility.load("1.0.7_for_1.12.2", LOGGER));
        assertNotNull(QualityToolsCompatibility.load("1.0.8", LOGGER));
    }

    @Test
    void walksTheReleasedInventoryAndAppliesMultipleAliasesOnlyOnce() throws Exception {
        CountingBaubles inventory = new CountingBaubles();
        BaublesEquipment baubles = new BaublesEquipment(LOGGER);
        ThreatPoints points = new ThreatPoints(message -> fail(message));
        AtomicInteger providerCalls = new AtomicInteger();
        points.register(Items.STICK, new ItemThreatPointProvider() {
            @Override
            public double points(ItemStack stack, EntityEquipmentSlot slot) {
                return fail("An accessory must not masquerade as vanilla equipment");
            }

            @Override
            public double baublePoints(ItemStack stack, int slot) {
                providerCalls.incrementAndGet();
                assertEquals(1, slot);
                return 50;
            }
        });
        points.installEquipmentSources(wearer -> baubles.equipped(inventory), QualityToolsCompatibility::collect);
        EntityArmorStand wearer = new EntityArmorStand(null);
        ItemStack ring = new ItemStack(Items.STICK);
        quality(ring, modifier(100, 0, UUID.randomUUID()), "baubles_ring", "BAUBLES_TRINKET", "baubles_ring");
        inventory.setStackInSlot(1, ring);
        ServerTuning tuning = tuning();
        assertEquals(2.5, points.multiplier(wearer, tuning));
        assertEquals(1, providerCalls.get());
        assertEquals(inventory.getSlots(), inventory.reads);

        inventory.setStackInSlot(1, ItemStack.EMPTY);
        assertEquals(1, points.multiplier(wearer, tuning));
        assertEquals(1, providerCalls.get());
    }

    @Test
    void rereadsTheMatchingSlotAndQualityAndRejectsInvalidRecords() throws Exception {
        ThreatPoints points = new ThreatPoints(message -> fail(message));
        points.register(Items.STICK, (stack, slot) -> 50);
        points.installEquipmentSources(null, QualityToolsCompatibility::collect);
        EntityArmorStand wearer = new EntityArmorStand(null);
        wearer.setSilent(true);
        ItemStack stack = new ItemStack(Items.STICK);
        UUID id = UUID.randomUUID();
        NBTTagCompound first = modifier(50, 0, id);
        NBTTagList mods = quality(stack, first, "mainhand", "MAINHAND");
        mods.appendTag(first.copy());
        wearer.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, stack);
        ServerTuning tuning = tuning();
        assertEquals(2, points.multiplier(wearer, tuning));

        wearer.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, ItemStack.EMPTY);
        wearer.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, stack);
        assertEquals(1.5, points.multiplier(wearer, tuning));
        quality(stack, modifier(-50, 0, id), "offhand");
        assertEquals(1, points.multiplier(wearer, tuning));

        quality(stack, modifier(99, 1, id), "offhand");
        assertEquals(1.5, points.multiplier(wearer, tuning));
        quality(stack, modifier(Double.NaN, 0, id), "offhand");
        assertEquals(1.5, points.multiplier(wearer, tuning));
        mods = quality(stack, modifier(100, 0, id), "offhand");
        mods.appendTag(modifier(-100, 0, id));
        assertEquals(1.5, points.multiplier(wearer, tuning));
        NBTTagCompound malformed = modifier(80, 0, id);
        malformed.removeTag("Operation");
        quality(stack, malformed, "offhand");
        assertEquals(1.5, points.multiplier(wearer, tuning));
    }

    @Test
    void scalesOverflowingTotalsWithoutLosingLaterCancellation() throws Exception {
        ThreatPoints points = new ThreatPoints(message -> fail(message));
        points.installEquipmentSources(null, QualityToolsCompatibility::collect);
        EntityArmorStand wearer = new EntityArmorStand(null);
        wearer.setSilent(true);
        ItemStack stack = new ItemStack(Items.STICK);
        double bound = ServerTuning.MAX_ITEM_POINTS;
        NBTTagList mods = quality(stack, modifier(-bound, 0, UUID.randomUUID()), "mainhand");
        for (int i = 1; i < 9; i++) {
            mods.appendTag(modifier(-bound, 0, UUID.randomUUID()));
        }
        wearer.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, stack);
        ServerTuning tuning = tuning();
        double expected = 1 / (1 + (bound / 100) * 9);
        assertEquals(expected, points.multiplier(wearer, tuning), Math.ulp(expected));
        for (int i = 0; i < 9; i++) {
            mods.appendTag(modifier(bound, 0, UUID.randomUUID()));
        }
        mods.appendTag(modifier(100, 0, UUID.randomUUID()));
        assertEquals(2, points.multiplier(wearer, tuning));
    }

    private ServerTuning tuning() throws Exception {
        List<String> warnings = new ArrayList<>();
        ServerTuning tuning = TuningFiles.load(directory.resolve("threatworks.cfg"), null, id -> true, warnings);
        assertTrue(warnings.isEmpty());
        return tuning;
    }

    private static NBTTagCompound modifier(double amount, int operation, UUID id) {
        NBTTagCompound data = SharedMonsterAttributes.writeAttributeModifierToNBT(
                new AttributeModifier(id, "test quality", amount, operation));
        data.setString("AttributeName", "threatworks.threat_points");
        return data;
    }

    private static NBTTagList quality(ItemStack stack, NBTTagCompound modifier, String... slotNames) {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList slots = new NBTTagList();
        for (String slotName : slotNames) {
            slots.appendTag(new NBTTagString(slotName));
        }
        NBTTagList mods = new NBTTagList();
        mods.appendTag(modifier);
        tag.setTag("Slots", slots);
        tag.setTag("AttributeModifiers", mods);
        stack.setTagInfo("Quality", tag);
        return mods;
    }

    private static final class CountingBaubles extends BaublesContainer {
        int reads;

        @Override
        public boolean isItemValidForSlot(int slot, ItemStack stack, EntityLivingBase wearer) {
            return slot == 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            reads++;
            return super.getStackInSlot(slot);
        }
    }
}
