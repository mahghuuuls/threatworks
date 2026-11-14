package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.compat.SpecialMobsCompatibility;
import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import com.mahghuuuls.threatworks.threat.Encounter.TargetObservation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIFindEntityNearest;
import net.minecraft.entity.ai.EntityAIFindEntityNearestPlayer;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

/** Native contact damage and short, clear ground approaches for hopping slimes. */
final class SlimeMobPolicy implements MobPolicy {
    private static final double LOCAL_HOP_RANGE = 12;
    private static final Field NEAREST_CLASS = nearestClassField();
    private final boolean providerContact;

    SlimeMobPolicy(MobType type) {
        providerContact = SpecialMobsCompatibility.isHoppingSlime(type);
    }

    static boolean matchesType(MobType type) {
        return type == MobType.SLIME || type == MobType.MAGMA_CUBE || SpecialMobsCompatibility.isHoppingSlime(type);
    }

    static boolean matches(MobType type, EntityLiving mob) {
        if (type == MobType.SLIME) {
            return mob.getClass() == EntitySlime.class;
        }
        if (type == MobType.MAGMA_CUBE) {
            return mob.getClass() == EntityMagmaCube.class;
        }
        return SpecialMobsCompatibility.matchesHoppingSlime(type, mob);
    }

    static boolean hasNativeContract(EntityLiving mob) {
        if (mob.isAIDisabled() || mob.tasks.isControlFlagDisabled(1) || mob.tasks.isControlFlagDisabled(2)
                || mob.tasks.isControlFlagDisabled(4) || !matchesInner(mob.getMoveHelper().getClass(), "SlimeMoveHelper")
                || mob.tasks.taskEntries.size() != 4 || mob.targetTasks.taskEntries.size() != 2) {
            return false;
        }
        int goalMask = 0;
        for (EntityAITaskEntry task : mob.tasks.taskEntries) {
            Class<?> goal = task.action.getClass();
            int mutex = task.action.getMutexBits();
            if (task.priority == 1 && mutex == 5 && matchesInner(goal, "AISlimeFloat")) {
                goalMask |= 1;
            } else if (task.priority == 2 && mutex == 2 && matchesInner(goal, "AISlimeAttack")) {
                goalMask |= 2;
            } else if (task.priority == 3 && mutex == 2 && matchesInner(goal, "AISlimeFaceRandom")) {
                goalMask |= 4;
            } else if (task.priority == 5 && mutex == 5 && matchesInner(goal, "AISlimeHop")) {
                goalMask |= 8;
            }
        }
        int selectorMask = 0;
        for (EntityAITaskEntry task : mob.targetTasks.taskEntries) {
            if (task.action.getMutexBits() != 0) {
                return false;
            }
            if (task.priority == 1 && task.action.getClass() == EntityAIFindEntityNearestPlayer.class) {
                selectorMask |= 1;
            } else if (task.priority == 3 && task.action.getClass() == EntityAIFindEntityNearest.class
                    && targetsGolems(task)) {
                selectorMask |= 2;
            }
        }
        return goalMask == 15 && selectorMask == 3;
    }

    private static boolean matchesInner(Class<?> actual, String name) {
        return actual.getEnclosingClass() == EntitySlime.class && actual.getSimpleName().equals(name);
    }

    private static Field nearestClassField() {
        try {
            return ReflectionHelper.findField(EntityAIFindEntityNearest.class, "classToCheck", "field_179439_f");
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private static boolean targetsGolems(EntityAITaskEntry entry) {
        if (NEAREST_CLASS == null) {
            return false;
        }
        try {
            return NEAREST_CLASS.get(entry.action) == EntityIronGolem.class;
        } catch (IllegalAccessException unavailable) {
            return false;
        }
    }

    static boolean isNativePhase(EntityLiving mob) {
        return ((EntitySlime) mob).getSlimeSize() < 2 || mob.isRiding() || mob.isBeingRidden()
                || mob.isInWater() || mob.isInLava() || mob.isPotionActive(MobEffects.LEVITATION)
                || mob.world.getDifficulty() == EnumDifficulty.PEACEFUL;
    }

    @Override
    public boolean controlsImmediately(EntityLiving mob) {
        return hasNativeContract(mob) && !isNativePhase(mob);
    }

    @Override
    public boolean accepts(EntityLiving mob, EntityLivingBase participant) {
        return participant instanceof EntityPlayer || participant instanceof EntityIronGolem;
    }

    @Override
    public EntityLivingBase resolve(WorldServer world, EntityLiving mob, Entity candidate) {
        if (!(candidate instanceof EntityLivingBase)) {
            return null;
        }
        EntityLivingBase target = (EntityLivingBase) candidate;
        double range = mob.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue();
        BlockPos pos = new BlockPos(target);
        return accepts(mob, target) && MobTargets.isAttackable(mob, target) && target.world == world
                && world.isBlockLoaded(pos) && world.getWorldBorder().contains(pos)
                && mob.getDistanceSq(target) <= range * range ? target : null;
    }

    @Override
    public TargetObservation observe(WorldServer world, EntityLiving mob, Entity candidate, WorkSample sample) {
        EntityLivingBase target = resolve(world, mob, candidate);
        if (target == null) {
            return TargetObservation.INELIGIBLE;
        }
        double distanceSquared = mob.getDistanceSq(target);
        if (distanceSquared > LOCAL_HOP_RANGE * LOCAL_HOP_RANGE) {
            return TargetObservation.INELIGIBLE;
        }
        Vec3d from = mob.getPositionVector();
        Vec3d to = target.getPositionVector();
        LoadedPathTerrain terrain = new LoadedPathTerrain(world);
        boolean visible = terrain.clearLine(from.add(0, mob.getEyeHeight(), 0), to.add(0, target.getEyeHeight(), 0));
        double contact = 0.6 * ((EntitySlime) mob).getSlimeSize();
        double contactSquared = providerContact ? mob.width * mob.width + target.width * target.width : contact * contact;
        boolean contactDispatch = target instanceof EntityPlayer
                ? target.getEntityBoundingBox().grow(1.0, 0.5, 1.0).intersects(mob.getEntityBoundingBox())
                : mob.getEntityBoundingBox().intersects(target.getEntityBoundingBox());
        if (distanceSquared < contactSquared && contactDispatch) {
            return visible ? TargetObservation.IN_REACH : TargetObservation.obscured(false);
        }
        if (!target.onGround || from.y < to.y - 0.125 || from.y > to.y + 5.0) {
            return TargetObservation.INELIGIBLE;
        }
        if (sample != null) {
            sample.pathQuery();
        }
        // Mid-hop, check a supported landing approach and the current airborne collision corridor.
        Vec3d footing = new Vec3d(from.x, to.y, from.z);
        if (!terrain.clearLevelGround(mob.getEntityBoundingBox().offset(0, footing.y - from.y, 0), footing, to)
                || !mob.onGround && !terrain.clearFlight(mob.getEntityBoundingBox(),
                new Vec3d(to.x - from.x, 0, to.z - from.z))) {
            return TargetObservation.INELIGIBLE;
        }
        return visible ? TargetObservation.pursuit(Math.sqrt(distanceSquared)) : TargetObservation.obscured(false);
    }

    @Override
    public boolean preservesNativeTarget(WorldServer world, EntityLiving mob, EntityLivingBase target) {
        return resolve(world, mob, target) != null && !observe(world, mob, target, null).isEligible();
    }

    @Override
    public boolean releasesNativeWrite(WorldServer world, EntityLiving mob, EntityLivingBase target) {
        return resolve(world, mob, target) != null && mob.getDistanceSq(target) > LOCAL_HOP_RANGE * LOCAL_HOP_RANGE;
    }
}
