package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;
import org.apache.logging.log4j.Logger;

public final class DefiledLandsCompatibility {
    private static final String ENTITY_PACKAGE = "lykrast.defiledlands.common.entity.monster.";
    private static final String MINIMUM = "1.4.3";

    private DefiledLandsCompatibility() {
    }

    public static void reportVersion(Logger logger) {
        if (Installed.MOD != null && !Installed.SUPPORTED) {
            logger.warn("Defiled Lands targeting unavailable for version {}. Update to at least {}.",
                    Installed.MOD.getVersion(), MINIMUM);
        }
    }

    public static int meleePriority(MobType type) {
        switch (type) {
            case DEFILED_HOST:
            case DEFILED_SCUTTLER:
                return 4;
            case DEFILED_SHAMBLER:
            case DEFILED_TWISTED_SHAMBLER:
                return 2;
            default:
                return -1;
        }
    }

    public static boolean canAcquireWithoutSight(MobType type, EntityLivingBase candidate) {
        return (type == MobType.DEFILED_TWISTED_SHAMBLER || type == MobType.DEFILED_SCUTTLER)
                && candidate instanceof EntityPlayer && Installed.EXACT_PIN;
    }

    public static boolean matchesScuttlerAttack(MobType type, Class<?> goal) {
        return type == MobType.DEFILED_SCUTTLER
                && goal.getName().equals(ENTITY_PACKAGE + "EntityScuttler$AISpiderAttack")
                && goal.getSuperclass().getName().equals("lykrast.defiledlands.common.entity.ai.EntityAIAttackMeleeStrafe");
    }

    public static boolean matches(MobType type, EntityCreature mob) {
        String entityClass;
        switch (type) {
            case DEFILED_HOST:
                entityClass = "EntityHost";
                break;
            case DEFILED_SHAMBLER:
                entityClass = "EntityShambler";
                break;
            case DEFILED_TWISTED_SHAMBLER:
                entityClass = "EntityShamblerTwisted";
                break;
            case DEFILED_SCUTTLER:
                entityClass = "EntityScuttler";
                break;
            default:
                return false;
        }
        return Installed.SUPPORTED && mob.getClass().getName().equals(ENTITY_PACKAGE + entityClass);
    }

    private static final class Installed {
        private static final ModContainer MOD = Loader.instance().getIndexedModList().get("defiledlands");
        private static final boolean SUPPORTED = MOD != null
                && new ComparableVersion(MOD.getVersion()).compareTo(new ComparableVersion(MINIMUM)) >= 0;
        private static final boolean EXACT_PIN = MOD != null && MINIMUM.equals(MOD.getVersion());
    }
}
