package com.mahghuuuls.threatworks.compat;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Read-only access to the verified provider's stationary combat state. */
public final class FoliaathCompatibility {
    private static final String ROOT = "com.bobmowzie.mowziesmobs.server.";
    private static final String ENTITY = ROOT + "entity.foliaath.EntityFoliaath";
    private static final String BABY = ROOT + "entity.foliaath.EntityBabyFoliaath";
    private static final String ANIMATION_AI = ROOT + "ai.animation.";
    private static final String VERSION = "1.5.8";

    private final Class<?> entityClass;
    private final Class<?> babyClass;
    private final Field active;
    private final Field frame;
    private final Method animation;
    private final Object idle;
    private final Object bite;
    private final Object hurt;
    private final Logger logger;
    private boolean failed;

    private FoliaathCompatibility(Class<?> entityClass, Logger logger) throws ReflectiveOperationException {
        this.entityClass = entityClass.asSubclass(EntityCreature.class);
        babyClass = Class.forName(BABY, false, entityClass.getClassLoader()).asSubclass(EntityCreature.class);
        this.logger = logger;
        active = entityClass.getField("active");
        frame = entityClass.getField("frame");
        animation = entityClass.getMethod("getAnimation");
        idle = entityClass.getField("NO_ANIMATION").get(null);
        bite = entityClass.getField("ATTACK_ANIMATION").get(null);
        hurt = entityClass.getField("HURT_ANIMATION").get(null);
        if (active.getType() != boolean.class || frame.getType() != int.class
                || idle == null || bite == null || hurt == null || idle == bite || bite == hurt || idle == hurt) {
            throw new IllegalArgumentException("Unexpected Foliaath animation contract");
        }
    }

    public static FoliaathCompatibility load(Logger logger) {
        ModContainer mod = Loader.instance().getIndexedModList().get("mowziesmobs");
        if (mod == null) {
            return null;
        }
        if (!VERSION.equals(mod.getVersion())) {
            logger.warn("Foliaath targeting unavailable for Mowzie's Mobs {}. Verified version is {}.",
                    mod.getVersion(), VERSION);
            return null;
        }
        try {
            return new FoliaathCompatibility(Class.forName(ENTITY, false, Loader.instance().getModClassLoader()), logger);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            logger.warn("Foliaath targeting unavailable because its combat state could not be bound.", failure);
            return null;
        }
    }

    public boolean matches(EntityCreature mob) {
        return !failed && mob.getClass() == entityClass;
    }

    public boolean matchesGoals(EntityCreature mob) {
        if (mob.isAIDisabled() || mob.tasks.taskEntries.size() != 4 || mob.targetTasks.taskEntries.size() != 1) {
            return false;
        }
        int goals = 0;
        for (EntityAITaskEntry entry : mob.tasks.taskEntries) {
            Class<?> type = entry.action.getClass();
            int mutex = entry.action.getMutexBits();
            if (entry.priority == 0 && mutex == 4 && type == EntityAISwimming.class) {
                goals |= 1;
            } else if (entry.priority == 1 && mutex == 8 && type.getName().equals(ANIMATION_AI + "AnimationAttackAI")) {
                goals |= 2;
            } else if (entry.priority == 1 && mutex == 8 && type.getName().equals(ANIMATION_AI + "AnimationTakeDamage")) {
                goals |= 4;
            } else if (entry.priority == 1 && mutex == 8 && type.getName().equals(ANIMATION_AI + "AnimationDieAI")) {
                goals |= 8;
            }
        }
        EntityAITaskEntry targeting = mob.targetTasks.taskEntries.iterator().next();
        return goals == 15 && targeting.priority == 3 && targeting.action.getClass().getName().equals(ENTITY + "$1");
    }

    public Phase phase(EntityCreature mob) {
        if (failed || mob.isAIDisabled() || mob.tasks.isControlFlagDisabled(8)) {
            return Phase.UNKNOWN;
        }
        try {
            Object current = animation.invoke(mob);
            if (current == bite || current == hurt) {
                return Phase.NATIVE;
            }
            if (current != idle) {
                return Phase.UNKNOWN;
            }
            // Native target refresh writes null after every twentieth idle frame.
            return active.getBoolean(mob) && frame.getInt(mob) % 20 != 0 ? Phase.CONTROL : Phase.NATIVE;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            failed = true;
            logger.warn("Foliaath targeting disabled after its combat state became unavailable.", failure);
            return Phase.UNKNOWN;
        }
    }

    public boolean accepts(EntityLivingBase target) {
        return (target instanceof EntityPlayer || target instanceof EntityCreature) && !target.isInvisible()
                && !entityClass.isInstance(target) && !babyClass.isInstance(target);
    }

    public boolean inBiteRange(EntityCreature mob, EntityLivingBase target) {
        double height = target.posY - mob.posY;
        return height >= -1.0 && height <= 2.0 && mob.getDistance(target) - target.width / 2.0F <= 4.0F;
    }

    public enum Phase {
        CONTROL, NATIVE, UNKNOWN
    }
}
