package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIFleeSun;
import net.minecraft.entity.ai.EntityAILeapAtTarget;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Exact provider classes that use the standard ground-melee contract. */
public final class GroundMeleeCompatibility {
    private static final Map<MobType, Binding> BINDINGS = new EnumMap<>(MobType.class);
    private static final Logger LOGGER = LogManager.getLogger("threatworks");
    private static final String MOC_HOSTILE = "drzhark.mocreatures.entity.hostile.MoCEntity";
    private static final String MOC_WANDER = "drzhark.mocreatures.entity.ai.EntityAIWanderMoC2";
    private static final String TORO_ZOMBIE_LEAP = "net.torocraft.toroquest.entities.ai.EntityAIZombieLeap";
    private static final String TORO_RAID = "net.torocraft.toroquest.entities.ai.EntityAIRaid";
    private static MethodHandle horseIsFlyer;
    private static MethodHandle horseIsTamed;

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
        bind(unsupported, MobType.PVJ_BANSHEE, "pvj", "1.6.4", "vibrantjourneys.entities.monster.EntityBanshee", 2);
        bind(unsupported, MobType.PVJ_SHADE, "pvj", "1.6.4", "vibrantjourneys.entities.monster.EntityShade", 2);
        bind(unsupported, MobType.CHARM_SPECTRE, "charm", "1.4", "svenhjol.charm.world.entity.EntitySpectre", 2);
        bind(unsupported, MobType.MOC_HORSE_MOB, "mocreatures", "12.5.1", "drzhark.mocreatures.entity.hostile.MoCEntityHorseMob", 2);
        bind(unsupported, MobType.MOC_CAVE_SCORPION, "mocreatures", "12.5.1", MOC_HOSTILE + "CaveScorpion", 4);
        bind(unsupported, MobType.MOC_DIRT_SCORPION, "mocreatures", "12.5.1", MOC_HOSTILE + "DirtScorpion", 4);
        bind(unsupported, MobType.MOC_FIRE_SCORPION, "mocreatures", "12.5.1", MOC_HOSTILE + "FireScorpion", 4);
        bind(unsupported, MobType.MOC_FROST_SCORPION, "mocreatures", "12.5.1", MOC_HOSTILE + "FrostScorpion", 4);
        bind(unsupported, MobType.MOC_UNDEAD_SCORPION, "mocreatures", "12.5.1", MOC_HOSTILE + "UndeadScorpion", 4);
        bind(unsupported, MobType.MOC_SILVER_SKELETON, "mocreatures", "12.5.1", MOC_HOSTILE + "SilverSkeleton", 2);
        String toro = "net.torocraft.toroquest.entities.Entity";
        bind(unsupported, MobType.TORO_ZOMBIE_RAIDER, "toroquest", "1.12.2-5.3", toro + "ZombieRaider", 3);
        bind(unsupported, MobType.TORO_ZOMBIE_VILLAGER_RAIDER, "toroquest", "1.12.2-5.3", toro + "ZombieVillagerRaider", 3);
        String natura = "com.progwml6.natura.entities.entity.monster.Entity";
        bind(unsupported, MobType.NATURA_HEATSCAR, "natura", "4.6.1", natura + "HeatscarSpider", 4);
        bind(unsupported, MobType.NATURA_BABY_HEATSCAR, "natura", "4.6.1", natura + "BabyHeatscarSpider", 4);
        bind(unsupported, MobType.NATURA_NITRO_CREEPER, "natura", "4.6.1", natura + "NitroCreeper", 3);
        bind(unsupported, MobType.DEEPER_DEPTHS_BOGGED, "deeperdepths", "1.1.2a",
                "com.deeperdepths.common.entities.EntityBogged", 4);
        bindHorseGroundState();
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

    private static void bindHorseGroundState() {
        Binding horse = BINDINGS.get(MobType.MOC_HORSE_MOB);
        if (horse == null) {
            return;
        }
        try {
            Class<?> type = Class.forName(horse.className, false, Loader.instance().getModClassLoader());
            MethodType signature = MethodType.methodType(boolean.class);
            MethodType receiver = MethodType.methodType(boolean.class, EntityCreature.class);
            horseIsFlyer = MethodHandles.publicLookup().findVirtual(type, "isFlyer", signature).asType(receiver);
            horseIsTamed = MethodHandles.publicLookup().findVirtual(type, "getIsTamed", signature).asType(receiver);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            BINDINGS.remove(MobType.MOC_HORSE_MOB);
            LOGGER.warn("Mo' Creatures HorseMob targeting unavailable because its ground form could not be bound.", failure);
        }
    }

    private static boolean horseUsesNativeControl(EntityCreature horse) {
        try {
            return horseIsFlyer == null || horseIsTamed == null || (boolean) horseIsFlyer.invokeExact(horse)
                    || (boolean) horseIsTamed.invokeExact(horse);
        } catch (Throwable failure) {
            return true;
        }
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
                || type == MobType.ILLAGERS_FURANTUR || type == MobType.GAIA_MITE || isLightAvoiding(type)
                || type == MobType.CHARM_SPECTRE || type == MobType.MOC_HORSE_MOB || usesMocDarkMelee(type)
                || isToroZombieRaider(type) || isNaturaSpider(type)
                || type == MobType.NATURA_NITRO_CREEPER || type == MobType.DEEPER_DEPTHS_BOGGED;
    }

    public static boolean isNaturaSpider(MobType type) {
        return type == MobType.NATURA_HEATSCAR || type == MobType.NATURA_BABY_HEATSCAR;
    }

    public static boolean isNaturaSpiderLeap(MobType type, EntityAITaskEntry entry) {
        return isNaturaSpider(type) && entry.priority == 3 && entry.action.getMutexBits() == 5
                && entry.action.getClass() == EntityAILeapAtTarget.class;
    }

    private static boolean isLightAvoiding(MobType type) {
        return type == MobType.PVJ_BANSHEE || type == MobType.PVJ_SHADE;
    }

    public static boolean isLightAvoidance(MobType type, EntityAITaskEntry entry) {
        return isLightAvoiding(type) && entry.action.getMutexBits() == 1
                && (entry.priority == 0 && entry.action.getClass().getName().equals("vibrantjourneys.entities.ai.EntityAIAvoidLight")
                || entry.priority == 3 && entry.action.getClass() == EntityAIFleeSun.class);
    }

    private static boolean isMocScorpion(MobType type) {
        return type == MobType.MOC_CAVE_SCORPION || type == MobType.MOC_DIRT_SCORPION || type == MobType.MOC_FIRE_SCORPION
                || type == MobType.MOC_FROST_SCORPION || type == MobType.MOC_UNDEAD_SCORPION;
    }

    public static boolean usesClimbingNavigator(MobType type) {
        return isMocScorpion(type);
    }

    public static boolean usesMocDarkMelee(MobType type) {
        return usesClimbingNavigator(type) || type == MobType.MOC_SILVER_SKELETON;
    }

    public static boolean isMocDarkMeleeGoal(MobType type, Class<?> goal) {
        if (!usesMocDarkMelee(type)) {
            return false;
        }
        String name = isMocScorpion(type) ? "Scorpion$AIScorpionAttack" : "SilverSkeleton$AISkeletonAttack";
        return goal.getName().equals(MOC_HOSTILE + name);
    }

    public static boolean isMocScorpionLeap(MobType type, EntityAITaskEntry entry) {
        return isMocScorpion(type) && entry.priority == 3 && entry.action.getMutexBits() == 5
                && entry.action.getClass() == EntityAILeapAtTarget.class;
    }

    public static boolean isMocWander(MobType type, EntityAITaskEntry entry) {
        return usesMocDarkMelee(type) && entry.priority == 4 && entry.action.getMutexBits() == 1
                && entry.action.getClass().getName().equals(MOC_WANDER);
    }

    public static boolean hasUnknownMocGoal(MobType type, EntityAITaskEntry entry) {
        Class<?> goal = entry.action.getClass();
        return isMocScorpion(type) && goal == EntityAILeapAtTarget.class && !isMocScorpionLeap(type, entry)
                || usesMocDarkMelee(type) && goal.getName().equals(MOC_WANDER)
                && !isMocWander(type, entry);
    }

    public static boolean isToroZombieRaider(MobType type) {
        return type == MobType.TORO_ZOMBIE_RAIDER || type == MobType.TORO_ZOMBIE_VILLAGER_RAIDER;
    }

    public static boolean isToroRaiderGoal(MobType type, EntityAITaskEntry entry) {
        if (!isToroZombieRaider(type)) {
            return false;
        }
        String name = entry.action.getClass().getName();
        int mutex = entry.action.getMutexBits();
        // The constructor and persisted raid location can register distinct instances of the native raid task.
        int locationPriority = type == MobType.TORO_ZOMBIE_RAIDER ? 3 : 4;
        return entry.priority == 2 && mutex == 0 && name.equals(TORO_ZOMBIE_LEAP)
                || (entry.priority == 7 || entry.priority == locationPriority) && mutex == 1 && name.equals(TORO_RAID);
    }

    public static boolean hasUnknownToroRaiderGoal(MobType type, EntityAITaskEntry entry) {
        if (!isToroZombieRaider(type)) {
            return false;
        }
        String name = entry.action.getClass().getName();
        return (name.equals(TORO_ZOMBIE_LEAP) || name.equals(TORO_RAID)) && !isToroRaiderGoal(type, entry);
    }

    public static boolean nativePhase(MobType type, EntityCreature mob) {
        return BINDINGS.containsKey(type) && (mob.isRiding() || mob.isBeingRidden() || mob.isInWater() || mob.isInLava()
                || isLightAvoiding(type) && mob.isBurning()
                || usesMocDarkMelee(type) && mob.getBrightness() >= 0.5F
                || (usesClimbingNavigator(type) || isNaturaSpider(type)) && mob.isOnLadder()
                || type == MobType.MOC_HORSE_MOB && horseUsesNativeControl(mob)
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
