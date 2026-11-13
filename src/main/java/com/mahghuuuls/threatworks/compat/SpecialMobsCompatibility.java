package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Items;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.Logger;

public final class SpecialMobsCompatibility {
    private static final String ZOMBIE_PACKAGE = "fathertoast.specialmobs.entity.zombie.";
    private static final String SILVERFISH_PACKAGE = "fathertoast.specialmobs.entity.silverfish.";
    private static final String ENTITY_PACKAGE = "fathertoast.specialmobs.entity.";
    private static final String VARIANT_MINIMUM = "1.0.4";

    private SpecialMobsCompatibility() {
    }

    public static void reportVariantVersion(Logger logger) {
        if (Installed.MOD != null && !Installed.VARIANTS_SUPPORTED) {
            logger.warn("Special Mobs silverfish, skeleton, spider and creeper variant targeting unavailable for version {}. Update to at least {}.",
                    Installed.MOD.getVersion(), VARIANT_MINIMUM);
        }
    }

    public static boolean requiresLiveGoalContract(MobType type) {
        return zombieClass(type) != null || silverfishClass(type) != null || skeletonClass(type) != null
                || isVariantSpider(type) || creeperClass(type) != null;
    }

    public static boolean matches(MobType type, EntityCreature mob) {
        String zombieClass = zombieClass(type);
        if (zombieClass != null) {
            return mob instanceof EntityZombie && mob.getClass().getName().equals(ZOMBIE_PACKAGE + zombieClass);
        }
        String silverfishClass = silverfishClass(type);
        if (silverfishClass != null) {
            return Installed.VARIANTS_SUPPORTED && mob instanceof EntitySilverfish
                    && mob.getClass().getName().equals(SILVERFISH_PACKAGE + silverfishClass);
        }
        String skeletonClass = skeletonClass(type);
        if (skeletonClass != null) {
            return Installed.VARIANTS_SUPPORTED && mob instanceof AbstractSkeleton
                    && mob.getClass().getName().equals(ENTITY_PACKAGE + skeletonClass);
        }
        String spiderClass = spiderClass(type);
        if (spiderClass != null) {
            return Installed.VARIANTS_SUPPORTED && mob instanceof EntitySpider
                    && mob.getClass().getName().equals(ENTITY_PACKAGE + spiderClass);
        }
        String creeperClass = creeperClass(type);
        if (creeperClass != null) {
            return Installed.VARIANTS_SUPPORTED && mob instanceof EntityCreeper
                    && mob.getClass().getName().equals(ENTITY_PACKAGE + creeperClass);
        }
        return type == MobType.SPECIAL_SKELETON && mob instanceof EntitySkeleton && mob.getClass().getName().equals(
                "fathertoast.specialmobs.entity.skeleton.Entity_SpecialSkeleton");
    }

    public static boolean isVariantSkeletonMelee(MobType type, Class<?> goal) {
        return skeletonClass(type) != null && goal.getSuperclass() == EntityAIAttackMelee.class
                && goal.getName().equals("fathertoast.specialmobs.ai.EntityAISpecialAttackMelee");
    }

    public static boolean isVariantSkeleton(MobType type) {
        return skeletonClass(type) != null;
    }

    public static boolean isVariantSpider(MobType type) {
        return spiderClass(type) != null;
    }

    public static boolean hasSupportedSkeletonWeapon(MobType type, EntityCreature mob, boolean usesBow) {
        switch (type) {
            case KNIGHT_SKELETON:
            case SPECIAL_WITHER_SKELETON:
            case BRUTE_WITHER_SKELETON:
            case GIANT_WITHER_SKELETON:
            case KNIGHT_WITHER_SKELETON:
                return !usesBow;
            default:
                return mob.getHeldItemMainhand().getItem() == Items.BOW;
        }
    }

    private static String skeletonClass(MobType type) {
        switch (type) {
            case BRUTE_SKELETON:
                return "skeleton.EntityBruteSkeleton";
            case FIRE_SKELETON:
                return "skeleton.EntityFireSkeleton";
            case GATLING_SKELETON:
                return "skeleton.EntityGatlingSkeleton";
            case GIANT_SKELETON:
                return "skeleton.EntityGiantSkeleton";
            case KNIGHT_SKELETON:
                return "skeleton.EntityKnightSkeleton";
            case POISON_SKELETON:
                return "skeleton.EntityPoisonSkeleton";
            case SNIPER_SKELETON:
                return "skeleton.EntitySniperSkeleton";
            case STRAY_SPECIAL_SKELETON:
                return "skeleton.EntityStraySkeleton";
            case SPECIAL_WITHER_SKELETON:
                return "witherskeleton.Entity_SpecialWitherSkeleton";
            case BRUTE_WITHER_SKELETON:
                return "witherskeleton.EntityBruteWitherSkeleton";
            case GATLING_WITHER_SKELETON:
                return "witherskeleton.EntityGatlingWitherSkeleton";
            case GIANT_WITHER_SKELETON:
                return "witherskeleton.EntityGiantWitherSkeleton";
            case KNIGHT_WITHER_SKELETON:
                return "witherskeleton.EntityKnightWitherSkeleton";
            case SNIPER_WITHER_SKELETON:
                return "witherskeleton.EntitySniperWitherSkeleton";
            default:
                return null;
        }
    }

    private static String silverfishClass(MobType type) {
        switch (type) {
            case SPECIAL_SILVERFISH:
                return "Entity_SpecialSilverfish";
            case BLINDING_SILVERFISH:
                return "EntityBlindingSilverfish";
            case POISON_SILVERFISH:
                return "EntityPoisonSilverfish";
            case TOUGH_SILVERFISH:
                return "EntityToughSilverfish";
            default:
                return null;
        }
    }

    private static String spiderClass(MobType type) {
        switch (type) {
            case SPECIAL_SPIDER:
                return "spider.Entity_SpecialSpider";
            case SPECIAL_CAVE_SPIDER:
                return "cavespider.Entity_SpecialCaveSpider";
            case BABY_SPIDER:
                return "spider.EntityBabySpider";
            case BABY_CAVE_SPIDER:
                return "cavespider.EntityBabyCaveSpider";
            case DESERT_SPIDER:
                return "spider.EntityDesertSpider";
            case GIANT_SPIDER:
                return "spider.EntityGiantSpider";
            case PALE_SPIDER:
                return "spider.EntityPaleSpider";
            case POISON_SPIDER:
                return "spider.EntityPoisonSpider";
            case WITCH_SPIDER:
                return "spider.EntityWitchSpider";
            case WITCH_CAVE_SPIDER:
                return "cavespider.EntityWitchCaveSpider";
            case HUNGRY_SPIDER:
                return "spider.EntityHungrySpider";
            case MOTHER_SPIDER:
                return "spider.EntityMotherSpider";
            case MOTHER_CAVE_SPIDER:
                return "cavespider.EntityMotherCaveSpider";
            case WEB_SPIDER:
                return "spider.EntityWebSpider";
            case WEB_CAVE_SPIDER:
                return "cavespider.EntityWebCaveSpider";
            default:
                return null;
        }
    }

    private static String zombieClass(MobType type) {
        switch (type) {
            case SPECIAL_ZOMBIE:
                return "Entity_SpecialZombie";
            case BRUTE_ZOMBIE:
                return "EntityBruteZombie";
            case FIRE_ZOMBIE:
                return "EntityFireZombie";
            case GIANT_ZOMBIE:
                return "EntityGiantZombie";
            case HUNGRY_ZOMBIE:
                return "EntityHungryZombie";
            case HUSK_ZOMBIE:
                return "EntityHuskZombie";
            case PLAGUE_ZOMBIE:
                return "EntityPlagueZombie";
            default:
                return null;
        }
    }

    private static String creeperClass(MobType type) {
        switch (type) {
            case SPECIAL_CREEPER:
                return "creeper.Entity_SpecialCreeper";
            case FIRE_CREEPER:
                return "creeper.EntityFireCreeper";
            default:
                return null;
        }
    }

    private static final class Installed {
        private static final ModContainer MOD = Loader.instance().getIndexedModList().get("specialmobs");
        private static final boolean VARIANTS_SUPPORTED = MOD != null && supportsVariants(MOD.getVersion());
    }

    private static boolean supportsVariants(String version) {
        String numeric = version.replaceFirst("_for_mc1\\.12\\.2$", "");
        return numeric.matches("\\d+\\.\\d+\\.\\d+")
                && new ComparableVersion(numeric).compareTo(new ComparableVersion(VARIANT_MINIMUM)) >= 0;
    }
}
