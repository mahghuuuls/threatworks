package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIFleeSun;
import net.minecraft.entity.ai.EntityAILeapAtTarget;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.init.Items;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Exact provider classes that use the standard ground-melee contract. */
public final class GroundMeleeCompatibility {
    private static final Map<MobType, Binding> BINDINGS = new EnumMap<>(MobType.class);
    private static final Logger LOGGER = LogManager.getLogger("threatworks");
    private static final String STYGIAN = "fluke.stygian.entity.Entity";
    private static final String VEILINGS = "com.sirsquidly.veilings.common.entity.";
    private static final String MOC_HOSTILE = "drzhark.mocreatures.entity.hostile.MoCEntity";
    private static final String MOC_WANDER = "drzhark.mocreatures.entity.ai.EntityAIWanderMoC2";
    private static final String TORO_ZOMBIE_LEAP = "net.torocraft.toroquest.entities.ai.EntityAIZombieLeap";
    private static final String TORO_RAID = "net.torocraft.toroquest.entities.ai.EntityAIRaid";
    private static MethodHandle horseIsFlyer;
    private static MethodHandle horseIsTamed;
    private static MethodHandle skipperSwell;
    private static MethodHandle supaikaSlam;
    private static MethodHandle magmaMolten;
    private static MethodHandle gruntMolten;

    public static void initialize() {
        Set<String> unsupported = new HashSet<>();
        // The TestPACK stygian-2.1.2.jar reports Forge mod version 2.1.0.
        bind(unsupported, MobType.STYGIAN_CRACKY, "stygian", "2.1.0", "fluke.stygian.entity.EntityCracky", 4);
        bind(unsupported, MobType.STYGIAN_GLUTTERMITE, "stygian", "2.1.0", "fluke.stygian.entity.EntityGluttermite", 2);
        bind(unsupported, MobType.STYGIAN_ENDERMITE, "stygian", "2.1.0", "fluke.stygian.entity.EntityStygianEndermite", 2);
        bind(unsupported, MobType.STYGIAN_WATCHLING, "stygian", "2.1.0", "fluke.stygian.entity.EntityWatchling", 2);
        bind(unsupported, MobType.STYGIAN_SUPAIKA, "stygian", "2.1.0", STYGIAN + "Supaika", 2);
        bind(unsupported, MobType.STYGIAN_LUNAR_ZORP, "stygian", "2.1.0", STYGIAN + "LunarZorp", 4);
        bind(unsupported, MobType.STYGIAN_SKIPPER, "stygian", "2.1.0", STYGIAN + "Skipper", 4);
        bind(unsupported, MobType.STYGIAN_END_SKELETON, "stygian", "2.1.0", STYGIAN + "EndSkeleton", 4);
        skipperSwell = bindState(MobType.STYGIAN_SKIPPER, "getSwellState", int.class);
        supaikaSlam = bindSupaikaSlam();
        bind(unsupported, MobType.MAGMA_MONSTER, "magma_monsters", "0.3.0", "magma_monsters.entities.EntityMagmaMonster", 2);
        bind(unsupported, MobType.MAGMA_MONSTER_GRUNT, "magma_monsters", "0.3.0", "magma_monsters.entities.EntityMagmaMonsterGrunt", 2);
        magmaMolten = bindState(MobType.MAGMA_MONSTER, "getMolten", boolean.class);
        gruntMolten = bindState(MobType.MAGMA_MONSTER_GRUNT, "getMolten", boolean.class);
        bind(unsupported, MobType.VEILINGS_WICKED_CUSTODIAN, "veilings", "1.2.1", VEILINGS + "wicked.EntityWickedVeilingCustodian", 1);
        bind(unsupported, MobType.VEILINGS_WICKED_DEFT, "veilings", "1.2.1", VEILINGS + "wicked.EntityWickedVeilingDeft", 1);
        bind(unsupported, MobType.EERIE_CURSED_ARMOR, "eerieentities", "1.0.7", "com.jarhax.eerieentities.entities.EntityCursedArmor", 2);
        bind(unsupported, MobType.EERIE_NETHER_KNIGHT, "eerieentities", "1.0.7", "com.jarhax.eerieentities.entities.EntityNetherKnight", 2);
        bind(unsupported, MobType.ILLAGERS_FURANTUR, "illagers_plus", "1.1", "com.litewolf101.objects.entities.EntityFurantur", 4);
        bind(unsupported, MobType.ILLAGERS_ARCHER, "illagers_plus", "1.1", "com.litewolf101.objects.entities.EntityArcher", 6);
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

    private static MethodHandle bindSupaikaSlam() {
        MobType type = MobType.STYGIAN_SUPAIKA;
        Binding binding = BINDINGS.get(type);
        if (binding == null) {
            return null;
        }
        try {
            Class<?> owner = Class.forName(binding.className, false, Loader.instance().getModClassLoader());
            // Stygian strips the public timer getter from dedicated servers.
            Field timer = owner.getDeclaredField("slamtimer");
            timer.setAccessible(true);
            return MethodHandles.lookup().unreflectGetter(timer).asType(MethodType.methodType(int.class, EntityCreature.class));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            BINDINGS.remove(type);
            LOGGER.warn("Supaika targeting unavailable because the native slam timer could not be bound.", failure);
            return null;
        }
    }

    private static MethodHandle bindState(MobType type, String name, Class<?> result) {
        Binding binding = BINDINGS.get(type);
        if (binding == null) {
            return null;
        }
        try {
            Class<?> owner = Class.forName(binding.className, false, Loader.instance().getModClassLoader());
            return MethodHandles.publicLookup().findVirtual(owner, name, MethodType.methodType(result))
                    .asType(MethodType.methodType(result, EntityCreature.class));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            BINDINGS.remove(type);
            LOGGER.warn("{} targeting unavailable because {} could not be bound.", type.id, name, failure);
            return null;
        }
    }

    public static boolean usesStygianBow(MobType type, EntityCreature mob) {
        return type == MobType.STYGIAN_END_SKELETON && mob.getHeldItemMainhand().getItem() == Items.BOW;
    }

    public static boolean isStygianVariant(MobType type) {
        return type == MobType.STYGIAN_SUPAIKA || type == MobType.STYGIAN_LUNAR_ZORP
                || type == MobType.STYGIAN_SKIPPER || type == MobType.STYGIAN_END_SKELETON;
    }

    public static int stygianNativeGoal(MobType type, EntityAITaskEntry entry) {
        Class<?> goal = entry.action.getClass();
        int mutex = entry.action.getMutexBits();
        int priority = entry.priority;
        if (type == MobType.STYGIAN_LUNAR_ZORP && priority == 2 && mutex == 5 && goal.getName().equals(STYGIAN + "LunarZorp$AILeapAttack")
                || type == MobType.STYGIAN_SKIPPER && priority == 3 && mutex == 5 && goal == EntityAILeapAtTarget.class) {
            return 1;
        }
        return type == MobType.STYGIAN_SKIPPER && priority == 2 && mutex == 1
                && goal.getName().equals(STYGIAN + "Skipper$EntityAISkipperSwell") ? 2 : 0;
    }

    public static boolean hasStygianGoals(MobType type, int goals) {
        return goals == (type == MobType.STYGIAN_SKIPPER ? 3 : type == MobType.STYGIAN_LUNAR_ZORP ? 1 : 0);
    }

    public static boolean usesStygianMeleeLane(MobType type) {
        return type == MobType.STYGIAN_SUPAIKA;
    }

    private static boolean stygianNativePhase(MobType type, EntityCreature mob) {
        if (type == MobType.STYGIAN_LUNAR_ZORP) {
            return mob.isChild();
        }
        try {
            if (type == MobType.STYGIAN_SUPAIKA) {
                return mob.getHealth() < mob.getMaxHealth() * 0.4F
                        || supaikaSlam == null || (int) supaikaSlam.invokeExact(mob) >= 130;
            }
            if (type == MobType.STYGIAN_SKIPPER) {
                return mob.isOnLadder() || mob.getHealth() < mob.getMaxHealth() * 0.4F
                        || skipperSwell == null || (int) skipperSwell.invokeExact(mob) > 0;
            }
            return false;
        } catch (Throwable failure) {
            return true;
        }
    }

    public static boolean isWickedVeiling(MobType type) {
        return type == MobType.VEILINGS_WICKED_CUSTODIAN || type == MobType.VEILINGS_WICKED_DEFT;
    }

    public static boolean isMagmaMonster(MobType type) {
        return type == MobType.MAGMA_MONSTER || type == MobType.MAGMA_MONSTER_GRUNT;
    }

    public static boolean isMagmaAttack(MobType type, Class<?> goal) {
        Binding binding = BINDINGS.get(type);
        return binding != null && goal.getName().equals(binding.className + "$AIMonsterAttack");
    }

    public static boolean isMagmaFireball(MobType type, EntityAITaskEntry entry) {
        Binding binding = BINDINGS.get(type);
        return isMagmaMonster(type) && binding != null && entry.priority == 1 && entry.action.getMutexBits() == 3
                && entry.action.getClass().getName().equals(binding.className + "$AIFireballAttack");
    }

    private static boolean isMolten(MobType type, EntityCreature mob) {
        MethodHandle state = type == MobType.MAGMA_MONSTER ? magmaMolten : gruntMolten;
        try {
            return state == null || (boolean) state.invokeExact(mob);
        } catch (Throwable failure) {
            return true;
        }
    }

    public static boolean isWickedVeilingGoal(MobType type, EntityAITaskEntry entry) {
        return entry.priority == 1 && (type == MobType.VEILINGS_WICKED_CUSTODIAN && entry.action.getMutexBits() == 0
                && entry.action.getClass().getName().equals(VEILINGS + "ai.EntityAIVeilingHealNearby")
                || type == MobType.VEILINGS_WICKED_DEFT && entry.action.getMutexBits() == 5
                && entry.action.getClass() == EntityAILeapAtTarget.class);
    }

    public static boolean isWickedVeilingUtility(MobType type, EntityAITaskEntry entry) {
        if (!isWickedVeiling(type)) {
            return false;
        }
        String name = entry.action.getClass().getName();
        int mutex = entry.action.getMutexBits();
        return entry.priority == 3 && (mutex == 0 && name.equals(VEILINGS + "ai.EntityAIVeilingDislikeSunlight")
                || mutex == 3 && (name.equals(VEILINGS + "ai.EntityAIWickedVeilingHarass")
                || name.equals(VEILINGS + "ai.EntityAIWickedVeilingGetItem")))
                || entry.priority == 7 && mutex == 0 && (name.equals(VEILINGS + "ai.EntityAIVeilingUseItem")
                || name.equals(VEILINGS + "ai.EntityAIVeilingSnowballFight"));
    }

    public static boolean hasUnknownVeilingGoal(MobType type, EntityAITaskEntry entry) {
        if (!isWickedVeiling(type) || isWickedVeilingUtility(type, entry)) {
            return false;
        }
        String name = entry.action.getClass().getName();
        return name.equals(VEILINGS + "ai.EntityAIVeilingDislikeSunlight")
                || name.equals(VEILINGS + "ai.EntityAIWickedVeilingHarass")
                || name.equals(VEILINGS + "ai.EntityAIWickedVeilingGetItem")
                || name.equals(VEILINGS + "ai.EntityAIVeilingUseItem")
                || name.equals(VEILINGS + "ai.EntityAIVeilingSnowballFight");
    }

    private static boolean veilingNativePhase(EntityCreature mob) {
        if (!(mob instanceof EntityTameable)) {
            return true;
        }
        EntityTameable veiling = (EntityTameable) mob;
        return veiling.isTamed() || veiling.getOwnerId() != null || veiling.isSitting() || veiling.isChild();
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
                || type == MobType.ILLAGERS_FURANTUR || type == MobType.ILLAGERS_ARCHER
                || type == MobType.GAIA_MITE || isLightAvoiding(type)
                || type == MobType.CHARM_SPECTRE || type == MobType.MOC_HORSE_MOB || usesMocDarkMelee(type)
                || isToroZombieRaider(type) || isNaturaSpider(type)
                || type == MobType.NATURA_NITRO_CREEPER || type == MobType.DEEPER_DEPTHS_BOGGED || isStygianVariant(type)
                || isWickedVeiling(type) || isMagmaMonster(type);
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
                || isStygianVariant(type) && (mob.isWet() || stygianNativePhase(type, mob))
                || isWickedVeiling(type) && veilingNativePhase(mob)
                || isMagmaMonster(type) && isMolten(type, mob)
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
