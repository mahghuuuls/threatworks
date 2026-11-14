package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.threat.Encounter.TargetObservation;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumDifficulty;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

final class WitchCombat {
    private static final double POTION_LANE_RANGE_SQUARED = 64.0;
    private static final DataParameter<Boolean> DRINKING = drinkingParameter();

    private WitchCombat() {
    }

    static boolean nativePhase(EntityWitch witch) {
        // Special Mobs' virtual isDrinkingPotion() can consume a flag that its native update uses.
        return DRINKING == null || witch.getDataManager().get(DRINKING)
                || witch.isRiding() || witch.isBeingRidden() || witch.isInWater() || witch.isInLava()
                || witch.world.getDifficulty() == EnumDifficulty.PEACEFUL;
    }

    static TargetObservation observe(LoadedPathTerrain terrain, EntityWitch witch, EntityLivingBase target,
                                     boolean sightLost) {
        if (witch.getDistanceSq(target) > POTION_LANE_RANGE_SQUARED) {
            return TargetObservation.INELIGIBLE;
        }
        if (sightLost) {
            return TargetObservation.obscured(false);
        }
        Vec3d from = witch.getPositionVector().add(0.0, witch.getEyeHeight() - 0.1, 0.0);
        Vec3d to = target.getPositionVector().add(0.0, target.getEyeHeight() - 1.1, 0.0);
        Vec3d apex = from.add(to).scale(0.5).add(0.0, 0.25, 0.0);
        return terrain.clearLine(from, apex) && terrain.clearLine(apex, to)
                ? TargetObservation.IN_REACH : TargetObservation.INELIGIBLE;
    }

    @SuppressWarnings("unchecked")
    private static DataParameter<Boolean> drinkingParameter() {
        try {
            return (DataParameter<Boolean>) ReflectionHelper.findField(EntityWitch.class, "IS_DRINKING", "field_184731_c")
                    .get(null);
        } catch (IllegalAccessException | RuntimeException unavailable) {
            return null;
        }
    }
}
