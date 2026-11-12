package com.mahghuuuls.threatworks.points;

import com.mahghuuuls.threatworks.api.ItemThreatPointProvider;
import com.mahghuuuls.threatworks.config.ServerTuning;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/** Resolves one base value per physical slot and converts the signed total once per qualifying hit. */
public final class ThreatPoints {
    private static final EntityEquipmentSlot[] VANILLA_SLOTS = EntityEquipmentSlot.values();

    private final Map<Item, RegisteredProvider> providers = new IdentityHashMap<>();
    private final Consumer<String> warn;
    private Accessories accessories = wearer -> Collections.emptyList();
    private QualitySource qualities = (stack, slots, points) -> {};
    private ToDoubleFunction<EntityLivingBase> food = wearer -> 0;
    private SetBonusSource setBonuses;
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

    public void installEquipmentSources(Accessories accessories, QualitySource qualities) {
        if (accessories != null) {
            this.accessories = accessories;
        }
        if (qualities != null) {
            this.qualities = qualities;
        }
    }

    public void installFoodSource(ToDoubleFunction<EntityLivingBase> food) {
        if (food != null) {
            this.food = food;
        }
    }

    public double multiplier(EntityLivingBase wearer, ServerTuning tuning) {
        Sum total = new Sum();
        total.add(food.applyAsDouble(wearer));
        if (setBonuses != null) {
            setBonuses.collect(wearer, total::add);
        }
        return multiplier(wearer::getItemStackFromSlot, accessories.equipped(wearer), total, tuning);
    }

    double multiplier(Function<EntityEquipmentSlot, ItemStack> equipment, ServerTuning tuning) {
        return multiplier(equipment, Collections.emptyList(), new Sum(), tuning);
    }

    private double multiplier(Function<EntityEquipmentSlot, ItemStack> equipment, List<Accessory> worn,
                              Sum total, ServerTuning tuning) {
        for (EntityEquipmentSlot slot : VANILLA_SLOTS) {
            ItemStack stack = equipment.apply(slot);
            if (!stack.isEmpty()) {
                total.add(basePoints(stack, tuning, provider -> provider.points(stack, slot)));
                qualities.collect(stack, Collections.singletonList(slot.getName()), total::add);
            }
        }
        for (Accessory slot : worn) {
            ItemStack stack = slot.stack;
            if (!stack.isEmpty()) {
                total.add(basePoints(stack, tuning, provider -> provider.baublePoints(stack, slot.index)));
                qualities.collect(stack, slot.qualitySlots, total::add);
            }
        }
        return total.multiplier();
    }

    public void installSetBonusSource(SetBonusSource setBonuses) {
        this.setBonuses = setBonuses;
    }

    public void discardSetBonuses(EntityLivingBase player) {
        if (setBonuses != null) {
            setBonuses.discard(player);
        }
    }

    public void clearSetBonuses() {
        if (setBonuses != null) {
            setBonuses.clear();
        }
    }

    private double basePoints(ItemStack stack, ServerTuning tuning, ToDoubleFunction<ItemThreatPointProvider> read) {
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
            double points = read.applyAsDouble(registered.provider);
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

    public static double damageThreat(double healthLost, double multiplier) {
        if (!(healthLost > 0) || !Double.isFinite(healthLost)) {
            return 0;
        }
        double base = healthLost * ServerTuning.BASE_THREAT_PER_HEALTH_LOST;
        // Clamp the product: a quotient used as an overflow precheck can round up.
        return Math.max(Double.MIN_VALUE, Math.min(Double.MAX_VALUE, base * multiplier));
    }

    @FunctionalInterface
    public interface Accessories {
        List<Accessory> equipped(EntityLivingBase wearer);
    }

    @FunctionalInterface
    public interface QualitySource {
        void collect(ItemStack stack, List<String> slots, DoubleConsumer points);
    }

    public interface SetBonusSource {
        void collect(EntityLivingBase wearer, DoubleConsumer points);

        void discard(EntityLivingBase player);

        void clear();
    }

    /** One physical entry. Multiple names may describe the same slot for quality matching. */
    public static final class Accessory {
        final ItemStack stack;
        final int index;
        final List<String> qualitySlots;

        public Accessory(ItemStack stack, int index, List<String> qualitySlots) {
            this.stack = stack;
            this.index = index;
            this.qualitySlots = qualitySlots;
        }
    }

    private static final class Sum {
        private double total;
        private double correction;
        private BigDecimal wide;

        void add(double points) {
            if (wide != null) {
                wide = wide.add(new BigDecimal(points));
                return;
            }
            // Keep small contributions when larger ones cancel. Only widen if a running sum overflows.
            double combined = total + points;
            if (Double.isFinite(combined)) {
                correction += Math.abs(total) >= Math.abs(points)
                        ? (total - combined) + points : (points - combined) + total;
                total = combined;
            } else {
                wide = new BigDecimal(total).add(new BigDecimal(correction)).add(new BigDecimal(points));
            }
        }

        double multiplier() {
            double points = total + correction;
            if (wide == null && Double.isFinite(points)) {
                double scaled = points / ServerTuning.POINT_SCALE;
                return scaled < 0 ? 1 / (1 - scaled) : 1 + scaled;
            }
            BigDecimal sum = wide == null ? new BigDecimal(total).add(new BigDecimal(correction)) : wide;
            BigDecimal scale = BigDecimal.valueOf(ServerTuning.POINT_SCALE);
            return sum.signum() < 0
                    ? scale.divide(scale.subtract(sum), MathContext.DECIMAL128).doubleValue()
                    : sum.add(scale).divide(scale, MathContext.DECIMAL128).doubleValue();
        }
    }

    private static final class RegisteredProvider {
        final ItemThreatPointProvider provider;
        boolean warned;

        RegisteredProvider(ItemThreatPointProvider provider) {
            this.provider = provider;
        }
    }
}
