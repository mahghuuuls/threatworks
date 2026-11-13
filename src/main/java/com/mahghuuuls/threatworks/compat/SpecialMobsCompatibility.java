package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;

public final class SpecialMobsCompatibility {
    private SpecialMobsCompatibility() {
    }

    public static boolean matches(MobType type, EntityCreature mob) {
        switch (type) {
            case SPECIAL_ZOMBIE:
                return mob instanceof EntityZombie && mob.getClass().getName().equals(
                        "fathertoast.specialmobs.entity.zombie.Entity_SpecialZombie");
            case SPECIAL_SKELETON:
                return mob instanceof EntitySkeleton && mob.getClass().getName().equals(
                        "fathertoast.specialmobs.entity.skeleton.Entity_SpecialSkeleton");
            default:
                return false;
        }
    }
}
