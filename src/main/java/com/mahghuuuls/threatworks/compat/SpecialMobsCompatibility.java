package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;

public final class SpecialMobsCompatibility {
    private static final String ZOMBIE_PACKAGE = "fathertoast.specialmobs.entity.zombie.";

    private SpecialMobsCompatibility() {
    }

    public static boolean isMeleeZombie(MobType type) {
        return zombieClass(type) != null;
    }

    public static boolean matches(MobType type, EntityCreature mob) {
        String zombieClass = zombieClass(type);
        if (zombieClass != null) {
            return mob instanceof EntityZombie && mob.getClass().getName().equals(ZOMBIE_PACKAGE + zombieClass);
        }
        return type == MobType.SPECIAL_SKELETON && mob instanceof EntitySkeleton && mob.getClass().getName().equals(
                "fathertoast.specialmobs.entity.skeleton.Entity_SpecialSkeleton");
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
}
