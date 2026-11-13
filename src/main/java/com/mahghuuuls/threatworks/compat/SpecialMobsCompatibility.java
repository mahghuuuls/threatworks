package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.Logger;

public final class SpecialMobsCompatibility {
    private static final String ZOMBIE_PACKAGE = "fathertoast.specialmobs.entity.zombie.";
    private static final String SILVERFISH_PACKAGE = "fathertoast.specialmobs.entity.silverfish.";
    private static final String SILVERFISH_MINIMUM = "1.0.4";

    private SpecialMobsCompatibility() {
    }

    public static void reportSilverfishVersion(Logger logger) {
        if (Installed.MOD != null && !Installed.SILVERFISH_SUPPORTED) {
            logger.warn("Special Mobs silverfish targeting unavailable for version {}. Update to at least {}.",
                    Installed.MOD.getVersion(), SILVERFISH_MINIMUM);
        }
    }

    public static boolean requiresLiveMeleeContract(MobType type) {
        return zombieClass(type) != null || silverfishClass(type) != null;
    }

    public static boolean matches(MobType type, EntityCreature mob) {
        String zombieClass = zombieClass(type);
        if (zombieClass != null) {
            return mob instanceof EntityZombie && mob.getClass().getName().equals(ZOMBIE_PACKAGE + zombieClass);
        }
        String silverfishClass = silverfishClass(type);
        if (silverfishClass != null) {
            return Installed.SILVERFISH_SUPPORTED && mob instanceof EntitySilverfish
                    && mob.getClass().getName().equals(SILVERFISH_PACKAGE + silverfishClass);
        }
        return type == MobType.SPECIAL_SKELETON && mob instanceof EntitySkeleton && mob.getClass().getName().equals(
                "fathertoast.specialmobs.entity.skeleton.Entity_SpecialSkeleton");
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

    private static final class Installed {
        private static final ModContainer MOD = Loader.instance().getIndexedModList().get("specialmobs");
        private static final boolean SILVERFISH_SUPPORTED = MOD != null
                && supportsSilverfish(MOD.getVersion());
    }

    private static boolean supportsSilverfish(String version) {
        String numeric = version.replaceFirst("_for_mc1\\.12\\.2$", "");
        return numeric.matches("\\d+\\.\\d+\\.\\d+")
                && new ComparableVersion(numeric).compareTo(new ComparableVersion(SILVERFISH_MINIMUM)) >= 0;
    }
}
