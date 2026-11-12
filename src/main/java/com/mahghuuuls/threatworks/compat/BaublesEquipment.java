package com.mahghuuuls.threatworks.compat;

import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.mahghuuuls.threatworks.points.ThreatPoints;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

final class BaublesEquipment implements ThreatPoints.Accessories {
    private final Function<EntityPlayer, IBaublesItemHandler> handlerFor = BaublesApi::getBaublesHandler;
    private final List<List<String>> namesByIndex = slotNames();
    private final Logger logger;
    private boolean warned;

    BaublesEquipment(Logger logger) {
        this.logger = logger;
    }

    @Override
    public List<ThreatPoints.Accessory> equipped(EntityLivingBase wearer) {
        if (!(wearer instanceof EntityPlayer)) {
            return Collections.emptyList();
        }
        try {
            IBaublesItemHandler handler = handlerFor.apply((EntityPlayer) wearer);
            if (handler != null) {
                return equipped(handler);
            }
        } catch (LinkageError | RuntimeException failure) {
            if (!warned) {
                warned = true;
                logger.warn("Skipping Baubles threat points for an incompatible inventory", failure);
            }
        }
        return Collections.emptyList();
    }

    List<ThreatPoints.Accessory> equipped(IBaublesItemHandler handler) {
        int count = handler.getSlots();
        if (count != namesByIndex.size()) {
            throw new IllegalStateException("Baubles slot count differs from its public slot types: " + count);
        }
        List<ThreatPoints.Accessory> equipped = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            ItemStack stack = handler.getStackInSlot(index);
            if (!stack.isEmpty()) {
                equipped.add(new ThreatPoints.Accessory(stack, index, namesByIndex.get(index)));
            }
        }
        return equipped;
    }

    private static List<List<String>> slotNames() {
        List<List<String>> names = new ArrayList<>();
        for (BaubleType type : BaubleType.values()) {
            String name = "baubles_" + type.name().toLowerCase(Locale.ROOT);
            for (int index : type.getValidSlots()) {
                if (index < 0 || index >= 64) {
                    throw new IllegalStateException("Baubles declared an unsupported slot index: " + index);
                }
                while (names.size() <= index) {
                    names.add(new ArrayList<>());
                }
                names.get(index).add(name);
            }
        }
        if (names.isEmpty() || names.stream().anyMatch(List::isEmpty)) {
            throw new IllegalStateException("Baubles did not declare a complete slot layout");
        }
        return names;
    }
}
