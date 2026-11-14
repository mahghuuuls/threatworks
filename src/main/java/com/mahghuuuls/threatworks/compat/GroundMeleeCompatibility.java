package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;

import java.util.EnumMap;
import java.util.Map;

/** Exact provider classes that use the standard ground-melee contract. */
public final class GroundMeleeCompatibility {
    private static final Map<MobType, Binding> BINDINGS = new EnumMap<>(MobType.class);

    static {
        // The TestPACK stygian-2.1.2.jar reports Forge mod version 2.1.0.
        bind(MobType.STYGIAN_CRACKY, "stygian", "2.1.0", "fluke.stygian.entity.EntityCracky", 4);
        bind(MobType.STYGIAN_GLUTTERMITE, "stygian", "2.1.0", "fluke.stygian.entity.EntityGluttermite", 2);
        bind(MobType.STYGIAN_ENDERMITE, "stygian", "2.1.0", "fluke.stygian.entity.EntityStygianEndermite", 2);
        bind(MobType.STYGIAN_WATCHLING, "stygian", "2.1.0", "fluke.stygian.entity.EntityWatchling", 2);
        bind(MobType.EERIE_CURSED_ARMOR, "eerieentities", "1.0.7", "com.jarhax.eerieentities.entities.EntityCursedArmor", 2);
        bind(MobType.EERIE_NETHER_KNIGHT, "eerieentities", "1.0.7", "com.jarhax.eerieentities.entities.EntityNetherKnight", 2);
    }

    private GroundMeleeCompatibility() {
    }

    private static void bind(MobType type, String providerId, String minimum, String className, int priority) {
        ModContainer provider = Loader.instance().getIndexedModList().get(providerId);
        if (provider != null && new ComparableVersion(provider.getVersion()).compareTo(new ComparableVersion(minimum)) >= 0) {
            BINDINGS.put(type, new Binding(className, priority));
        }
    }

    public static boolean matches(MobType type, EntityCreature mob) {
        Binding binding = BINDINGS.get(type);
        return binding != null && mob.getClass().getName().equals(binding.className);
    }

    public static int meleePriority(MobType type) {
        Binding binding = BINDINGS.get(type);
        return binding == null ? -1 : binding.priority;
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
