package com.mahghuuuls.threatworks.api;

import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;

/** Supplies one signed base contribution for a live equipped stack on the server thread. */
@FunctionalInterface
public interface ItemThreatPointProvider {
    /**
     * Inspect the stack without changing it. Return finite signed points or zero.
     * Threatworks skips this call when a pack assignment overrides the stack.
     */
    double points(ItemStack stack, EntityEquipmentSlot slot);
}
