package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

public final class UnseenDungeonCompatibility {
    private static final String ROOT = "com.dungeon_additions.da.entity.";
    private static final Logger LOGGER = LogManager.getLogger("threatworks");

    private UnseenDungeonCompatibility() {
    }

    public static boolean isSupportedType(MobType type) {
        return type == MobType.UNSEEN_ANCIENT_KNIGHT || type == MobType.UNSEEN_KNIGHT_RAPIER;
    }

    public static boolean matches(MobType type, EntityCreature mob) {
        Binding binding = binding(type);
        return binding != null && mob.getClass() == binding.entity;
    }

    public static boolean hasNavigator(EntityCreature mob) {
        return mob.getNavigator().getClass().getName().equals(ROOT + "pathing.MobGroundNavigate");
    }

    public static boolean isAttackGoal(MobType type, Class<?> goal) {
        return goal.getName().equals(ROOT + (type == MobType.UNSEEN_ANCIENT_KNIGHT
                ? "ai.EntityAIAttackRotKnight" : "ai.EntityAIAttackRotKnightRapier"));
    }

    public static boolean hasHostileRole(MobType type, EntityCreature mob) {
        Binding binding = binding(type);
        try {
            return binding != null && !binding.friendly.getBoolean(mob) && !binding.boss.getBoolean(mob);
        } catch (IllegalAccessException | RuntimeException failure) {
            return false;
        }
    }

    public static boolean yieldsToNative(MobType type, EntityCreature mob) {
        Binding binding = binding(type);
        try {
            return mob.isRiding() || mob.isBeingRidden() || mob.isInWater() || mob.isInLava() || binding == null
                    || binding.randomRetreat.getBoolean(mob) || binding.holdPosition.getBoolean(mob)
                    || isTrue(binding.idle, mob) || isTrue(binding.fighting, mob)
                    || isTrue(binding.fullBody, mob) || isTrue(binding.immovable, mob)
                    || binding.drinking != null && (isTrue(binding.drinking, mob) || isTrue(binding.blocking, mob));
        } catch (IllegalAccessException | RuntimeException | LinkageError failure) {
            return true;
        }
    }

    public static boolean acceptsTarget(MobType type, EntityLivingBase target) {
        Binding binding = binding(type);
        return binding != null && !binding.rotBase.isInstance(target);
    }

    public static boolean withinAttackStartRange(MobType type, EntityCreature mob, EntityLivingBase target) {
        double rangeSquared = type == MobType.UNSEEN_KNIGHT_RAPIER ? 36.0 : 81.0;
        return mob.getDistanceSq(target.posX, target.getEntityBoundingBox().minY, target.posZ) <= rangeSquared;
    }

    private static Binding binding(MobType type) {
        if (type == MobType.UNSEEN_ANCIENT_KNIGHT) {
            return Installed.KNIGHT;
        }
        return type == MobType.UNSEEN_KNIGHT_RAPIER ? Installed.RAPIER : null;
    }

    private static boolean isTrue(MethodHandle method, EntityCreature mob) {
        try {
            return (boolean) method.invokeExact(mob);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Unseen's Dungeon Additions native state read failed.", failure);
        }
    }

    private static final class Installed {
        private static final Binding KNIGHT = bind("EntityRotKnight", false);
        private static final Binding RAPIER = bind("EntityRotKnightRapier", true);

        private static Binding bind(String name, boolean rapier) {
            ModContainer provider = Loader.instance().getIndexedModList().get("da");
            if (provider == null || new ComparableVersion(provider.getVersion()).compareTo(new ComparableVersion("0.1.7.5")) < 0) {
                if (provider != null) {
                    LOGGER.warn("Unseen's Dungeon Additions targeting unavailable for version {}.", provider.getVersion());
                }
                return null;
            }
            try {
                ClassLoader loader = Loader.instance().getModClassLoader();
                Class<?> entity = Class.forName(ROOT + "rot_knights." + name, false, loader).asSubclass(EntityCreature.class);
                Class<?> rotBase = Class.forName(ROOT + "rot_knights.EntityRotBase", false, loader).asSubclass(EntityCreature.class);
                return new Binding(entity.asSubclass(rotBase), rotBase, rapier);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                LOGGER.warn("Unseen's Dungeon Additions targeting contract could not be bound.", failure);
                return null;
            }
        }
    }

    private static final class Binding {
        private final Class<?> entity;
        private final Class<?> rotBase;
        private final Field friendly;
        private final Field boss;
        private final Field randomRetreat;
        private final Field holdPosition;
        private final MethodHandle idle;
        private final MethodHandle fighting;
        private final MethodHandle fullBody;
        private final MethodHandle immovable;
        private final MethodHandle drinking;
        private final MethodHandle blocking;

        private Binding(Class<?> entity, Class<?> rotBase, boolean rapier) throws ReflectiveOperationException {
            this.entity = entity;
            this.rotBase = rotBase;
            friendly = entity.getField("isFriendlyCreature");
            boss = entity.getField("iAmBossMob");
            randomRetreat = entity.getField("isRandomGetAway");
            holdPosition = entity.getField("holdPosition");
            idle = stateReader(entity, "isIdleMode");
            fighting = stateReader(entity, "isFightMode");
            fullBody = stateReader(entity, "isFullBodyUsage");
            immovable = stateReader(entity, "isImmovable");
            drinking = rapier ? stateReader(entity, "isDrinkPotion") : null;
            blocking = rapier ? stateReader(entity, "isBlockAction") : null;
        }

        private static MethodHandle stateReader(Class<?> entity, String name) throws ReflectiveOperationException {
            // Bind the exact signature without resolving unrelated client-only provider methods.
            return MethodHandles.publicLookup().findVirtual(entity, name, MethodType.methodType(boolean.class))
                    .asType(MethodType.methodType(boolean.class, EntityCreature.class));
        }
    }
}
