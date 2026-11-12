package com.mahghuuuls.threatworks.points;

import com.mahghuuuls.threatworks.api.ItemThreatPointProvider;
import com.mahghuuuls.threatworks.config.ServerTuning;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** Resolves one base value per physical slot and converts the signed total once per qualifying hit. */
public final class ThreatPoints {
    private static final EntityEquipmentSlot[] VANILLA_SLOTS = EntityEquipmentSlot.values();

    private final Map<Item, RegisteredProvider> providers = new IdentityHashMap<>();
    private final Consumer<String> warn;
    private boolean frozen;

    public ThreatPoints(Consumer<String> warn) {
        this.warn = warn;
    }

    public synchronized boolean register(Item item, ItemThreatPointProvider provider) {
        ResourceLocation id = item == null ? null : item.getRegistryName();
        if (frozen) {
            warn.accept("Rejected item threat point provider for " + id
                    + "; register during Forge initialization or post-initialization");
            return false;
        }
        if (id == null || Item.REGISTRY.getObject(id) != item || provider == null) {
            warn.accept("Rejected item threat point provider for " + id
                    + "; supply a provider for a registered item");
            return false;
        }
        if (providers.putIfAbsent(item, new RegisteredProvider(provider)) != null) {
            warn.accept("Conflicting item threat point provider for " + id + "; keeping the first registration");
            return false;
        }
        return true;
    }

    public synchronized void freezeRegistrations() {
        frozen = true;
    }

    public double total(EntityLivingBase wearer, ServerTuning tuning) {
        return total(wearer::getItemStackFromSlot, tuning);
    }

    double total(Function<EntityEquipmentSlot, ItemStack> equipment, ServerTuning tuning) {
        double total = 0;
        double correction = 0;
        for (EntityEquipmentSlot slot : VANILLA_SLOTS) {
            ItemStack stack = equipment.apply(slot);
            if (!stack.isEmpty()) {
                double points = basePoints(stack, slot, tuning);
                // Retain a small contribution when larger positive and negative slots cancel.
                double combined = total + points;
                correction += Math.abs(total) >= Math.abs(points)
                        ? (total - combined) + points : (points - combined) + total;
                total = combined;
            }
        }
        return total + correction;
    }

    private double basePoints(ItemStack stack, EntityEquipmentSlot slot, ServerTuning tuning) {
        Item item = stack.getItem();
        ResourceLocation id = item.getRegistryName();
        if (id == null) {
            return 0;
        }
        String itemId = id.toString();
        Double assigned = tuning.assignedPoints(itemId, stack.getMetadata());
        if (assigned == null) {
            assigned = tuning.assignedPoints(itemId, null);
        }
        if (assigned != null) {
            return assigned;
        }
        RegisteredProvider registered = providers.get(item);
        if (registered == null) {
            return 0;
        }
        try {
            double points = registered.provider.points(stack, slot);
            if (ServerTuning.validItemPoints(points)) {
                return points;
            }
            invalidProvider(registered, id, "returned unsupported points " + points);
        } catch (RuntimeException | LinkageError failure) {
            invalidProvider(registered, id, "failed with " + failure.getClass().getName());
        }
        return 0;
    }

    private void invalidProvider(RegisteredProvider registered, ResourceLocation id, String reason) {
        if (!registered.warned) {
            registered.warned = true;
            warn.accept("Item threat point provider for " + id + " " + reason
                    + "; using zero for invalid results");
        }
    }

    public static double damageThreat(double healthLost, double totalPoints) {
        if (!(healthLost > 0) || !Double.isFinite(healthLost)) {
            return 0;
        }
        double base = healthLost * ServerTuning.BASE_THREAT_PER_HEALTH_LOST;
        double scaled = Math.abs(totalPoints) / ServerTuning.POINT_SCALE;
        double multiplier = totalPoints < 0 ? 1 / (1 + scaled) : 1 + scaled;
        // Clamp the product: a quotient used as an overflow precheck can round up.
        return Math.max(Double.MIN_VALUE, Math.min(Double.MAX_VALUE, base * multiplier));
    }

    private static final class RegisteredProvider {
        final ItemThreatPointProvider provider;
        boolean warned;

        RegisteredProvider(ItemThreatPointProvider provider) {
            this.provider = provider;
        }
    }
}
