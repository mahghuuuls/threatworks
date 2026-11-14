package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Exact provider classes that use the standard ground-melee contract. */
public final class GroundMeleeCompatibility {
    private static final Map<MobType, Binding> BINDINGS = new EnumMap<>(MobType.class);
    private static final Logger LOGGER = LogManager.getLogger("threatworks");

    public static void initialize() {
        Set<String> unsupported = new HashSet<>();
        // The TestPACK stygian-2.1.2.jar reports Forge mod version 2.1.0.
        bind(unsupported, MobType.STYGIAN_CRACKY, "stygian", "2.1.0", "fluke.stygian.entity.EntityCracky", 4);
        bind(unsupported, MobType.STYGIAN_GLUTTERMITE, "stygian", "2.1.0", "fluke.stygian.entity.EntityGluttermite", 2);
        bind(unsupported, MobType.STYGIAN_ENDERMITE, "stygian", "2.1.0", "fluke.stygian.entity.EntityStygianEndermite", 2);
        bind(unsupported, MobType.STYGIAN_WATCHLING, "stygian", "2.1.0", "fluke.stygian.entity.EntityWatchling", 2);
        bind(unsupported, MobType.EERIE_CURSED_ARMOR, "eerieentities", "1.0.7", "com.jarhax.eerieentities.entities.EntityCursedArmor", 2);
        bind(unsupported, MobType.EERIE_NETHER_KNIGHT, "eerieentities", "1.0.7", "com.jarhax.eerieentities.entities.EntityNetherKnight", 2);
        bind(unsupported, MobType.ILLAGERS_FURANTUR, "illagers_plus", "1.1", "com.litewolf101.objects.entities.EntityFurantur", 4);
        bind(unsupported, MobType.GAIA_MITE, "grimoireofgaia", "1.7.2", "gaia.entity.monster.EntityGaiaMite", 2);
        String quark = "vazkii.quark.world.entity.Entity";
        bind(unsupported, MobType.QUARK_ASHEN, "quark", "r1.6-192", quark + "Ashen", 4);
        bind(unsupported, MobType.QUARK_DWELLER, "quark", "r1.6-192", quark + "Dweller", 2);
        bind(unsupported, MobType.QUARK_FORGOTTEN, "quark", "r1.6-192", quark + "Forgotten", 4);
        bind(unsupported, MobType.QUARK_PIRATE, "quark", "r1.6-192", quark + "Pirate", 4);
        bind(unsupported, MobType.QUARK_WRAITH, "quark", "r1.6-192", quark + "Wraith", 2);
    }

    private GroundMeleeCompatibility() {
    }

    private static void bind(Set<String> unsupported, MobType type, String providerId, String minimum,
                             String className, int priority) {
        ModContainer provider = Loader.instance().getIndexedModList().get(providerId);
        if (provider == null) {
            return;
        }
        if (new ComparableVersion(provider.getVersion()).compareTo(new ComparableVersion(minimum)) < 0) {
            if (unsupported.add(providerId) && !"grimoireofgaia".equals(providerId)) {
                LOGGER.warn("{} targeting unavailable for version {}. Update to at least {}.",
                        provider.getName(), provider.getVersion(), minimum);
            }
            return;
        }
        BINDINGS.put(type, new Binding(className, priority));
    }

    public static boolean matches(MobType type, EntityCreature mob) {
        Binding binding = BINDINGS.get(type);
        return binding != null && mob.getClass().getName().equals(binding.className);
    }

    public static int meleePriority(MobType type) {
        Binding binding = BINDINGS.get(type);
        return binding == null ? -1 : binding.priority;
    }

    public static boolean isQuarkSkeleton(MobType type) {
        return type == MobType.QUARK_ASHEN || type == MobType.QUARK_FORGOTTEN || type == MobType.QUARK_PIRATE;
    }

    public static boolean requiresNonEliteCombat(MobType type) {
        return isQuarkSkeleton(type) || type == MobType.QUARK_DWELLER || type == MobType.QUARK_WRAITH
                || type == MobType.ILLAGERS_FURANTUR || type == MobType.GAIA_MITE;
    }

    public static boolean nativePhase(MobType type, EntityCreature mob) {
        return BINDINGS.containsKey(type) && (mob.isRiding() || mob.isBeingRidden() || mob.isInWater() || mob.isInLava()
                || type == MobType.STYGIAN_WATCHLING && mob.isChild()
                || (type == MobType.STYGIAN_GLUTTERMITE || type == MobType.STYGIAN_ENDERMITE
                    || type == MobType.STYGIAN_WATCHLING) && mob.isWet());
    }

    private static final class Binding {
        private final String className;
        private final int priority;

        private Binding(String className, int priority) {
            this.className = className;
            this.priority = priority;
        }
    }
}
