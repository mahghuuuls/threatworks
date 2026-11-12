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

    /**
     * Supplies base points for an equipped Bauble under the same server-thread and pack override
     * contract as {@link #points}. The index is local to the installed handler's slot layout.
     */
    default double baublePoints(ItemStack stack, int slot) {
        return 0;
    }
}
