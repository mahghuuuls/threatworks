package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning;
import com.mahghuuuls.threatworks.points.ThreatPoints;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleConsumer;

public final class QualityToolsCompatibility {
    private static final ComparableVersion MINIMUM = new ComparableVersion("1.0.7");
    private static final String RELEASE_SUFFIX = "_for_1.12.2";
    private static final String POINTS_ATTRIBUTE = "threatworks.threat_points";

    private QualityToolsCompatibility() {}

    public static ThreatPoints.QualitySource load(Logger logger) {
        ModContainer mod = Loader.instance().getIndexedModList().get("qualitytools");
        return load(mod == null || !Loader.isModLoaded("qualitytools") ? null : mod.getVersion(), logger);
    }

    static ThreatPoints.QualitySource load(String version, Logger logger) {
        if (version == null) {
            return null;
        }
        String comparable = version.endsWith(RELEASE_SUFFIX)
                ? version.substring(0, version.length() - RELEASE_SUFFIX.length()) : version;
        if (new ComparableVersion(comparable).compareTo(MINIMUM) < 0) {
            logger.warn("Skipping Quality Tools threat points for version {}; requires {} or newer", version, MINIMUM);
            return null;
        }
        logger.info("Quality Tools threat points enabled for {}", version);
        return QualityToolsCompatibility::collect;
    }

    static void collect(ItemStack stack, List<String> slots, DoubleConsumer points) {
        NBTTagCompound quality = stack.getSubCompound("Quality");
        if (quality == null || !matches(quality.getTagList("Slots", NBT.TAG_STRING), slots)) {
            return;
        }
        NBTTagList modifiers = quality.getTagList("AttributeModifiers", NBT.TAG_COMPOUND);
        Map<UUID, Double> matched = null;
        for (int i = 0; i < modifiers.tagCount(); i++) {
            NBTTagCompound modifier = modifiers.getCompoundTagAt(i);
            if (!POINTS_ATTRIBUTE.equals(modifier.getString("AttributeName"))) {
                continue;
            }
            if (!modifier.hasKey("Operation", NBT.TAG_INT) || modifier.getInteger("Operation") != 0
                    || !modifier.hasKey("Amount", NBT.TAG_DOUBLE) || !modifier.hasUniqueId("UUID")) {
                return;
            }
            double amount = modifier.getDouble("Amount");
            if (!ServerTuning.validItemPoints(amount)) {
                return;
            }
            if (matched == null) {
                matched = new LinkedHashMap<>();
            }
            Double previous = matched.putIfAbsent(modifier.getUniqueId("UUID"), amount);
            if (previous != null && previous != amount) {
                return;
            }
        }
        if (matched != null) {
            matched.values().forEach(points::accept);
        }
    }

    private static boolean matches(NBTTagList qualitySlots, List<String> equipmentNames) {
        for (int i = 0; i < qualitySlots.tagCount(); i++) {
            String name = qualitySlots.getStringTagAt(i);
            for (String equipmentName : equipmentNames) {
                if (equipmentName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
