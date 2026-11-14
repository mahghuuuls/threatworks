package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.config.ServerTuning.MobType;
import com.mahghuuuls.threatworks.compat.DefiledLandsCompatibility;
import com.mahghuuuls.threatworks.compat.DungeonMobsCompatibility;
import com.mahghuuuls.threatworks.compat.FoliaathCompatibility;
import com.mahghuuuls.threatworks.compat.GaiaCompatibility;
import com.mahghuuuls.threatworks.compat.IceAndFireCompatibility;
import com.mahghuuuls.threatworks.compat.SpecialAiCompatibility;
import com.mahghuuuls.threatworks.compat.SpecialMobsCompatibility;
import com.mahghuuuls.threatworks.threat.Encounter.TargetObservation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAIAttackRangedBow;
import net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAICreeperSwell;
import net.minecraft.entity.ai.EntityAIFleeSun;
import net.minecraft.entity.ai.EntityAILeapAtTarget;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.ai.EntityAIZombieAttack;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEndermite;
import net.minecraft.entity.monster.EntityShulker;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.EntityZombieVillager;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemBow;
import net.minecraft.block.material.Material;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathFinder;
import net.minecraft.pathfinding.PathNavigateClimber;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.pathfinding.WalkNodeProcessor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraft.world.EnumDifficulty;

import java.util.function.Predicate;

final class MobTargets {
    // Bound the straight-line flight probe to local combat.
    private static final double MELEE_BLAZE_PURSUIT_RANGE = 16.0;

    private MobTargets() {
    }

    static boolean matchesBehavior(EntityCreature mob, MobType type) {
        if (!hasSupportedState(mob, type)) {
            return false;
        }
        boolean bow = usesBow(mob);
        Class<?> attack = EntityAIAttackMelee.class;
        Class<?> declaringMob = null;
        String nestedAttack = null;
        int priority = 4;
        if (mob instanceof EntityZombie) {
            attack = EntityAIZombieAttack.class;
            priority = 2;
        } else if (mob instanceof AbstractSkeleton) {
            attack = bow ? EntityAIAttackRangedBow.class : EntityAIAttackMelee.class;
        } else if (mob instanceof EntitySpider) {
            declaringMob = EntitySpider.class;
            nestedAttack = "AISpiderAttack";
        } else if (mob instanceof EntityBlaze) {
            declaringMob = EntityBlaze.class;
            nestedAttack = "AIFireballAttack";
        } else if (mob instanceof EntityShulker) {
            declaringMob = EntityShulker.class;
            nestedAttack = "AIAttack";
        } else if (mob instanceof EntityEndermite) {
            priority = 2;
        } else {
            int providerMelee = providerMeleePriority(type);
            if (providerMelee >= 0) {
                priority = providerMelee;
            }
        }
        boolean matched = false;
        boolean creeper = mob instanceof EntityCreeper;
        boolean specialBlaze = SpecialMobsCompatibility.isVariantBlaze(type);
        boolean foundSwell = false;
        for (EntityAITaskEntry entry : mob.tasks.taskEntries) {
            Class<?> goal = entry.action.getClass();
            boolean vanillaSkeletonMelee = attack == EntityAIAttackMelee.class
                    && goal.getSuperclass() == attack && goal.getEnclosingClass() == AbstractSkeleton.class;
            boolean providerSkeletonMelee = attack == EntityAIAttackMelee.class
                    && SpecialMobsCompatibility.isVariantSkeletonMelee(type, goal);
            boolean matchesAttack = nestedAttack == null ? goal == attack || vanillaSkeletonMelee || providerSkeletonMelee
                    : goal.getEnclosingClass() == declaringMob && goal.getSimpleName().equals(nestedAttack);
            if (type == MobType.DEFILED_SCUTTLER) {
                matchesAttack = DefiledLandsCompatibility.matchesScuttlerAttack(type, goal);
            } else if (specialBlaze) {
                matchesAttack = SpecialMobsCompatibility.isVariantBlazeAttack(goal);
            }
            if (matchesAttack) {
                int mutex = entry.action.getMutexBits();
                if (matched || entry.priority != priority || (specialBlaze ? mutex != 3 : (mutex & 3) != 3)) {
                    return false;
                }
                matched = true;
            } else if (creeper && goal == EntityAICreeperSwell.class) {
                if (foundSwell || entry.priority != 2 || entry.action.getMutexBits() != 1) {
                    return false;
                }
                foundSwell = true;
            } else if (SpecialAiCompatibility.hasUnknownCombatGoal(entry)
                    || !SpecialAiCompatibility.isCombatReaction(entry)
                    && !GaiaCompatibility.isAntWander(type, entry)
                    && !IceAndFireCompatibility.isHorseGoal(type, entry)
                    && (entry.priority > priority ? !entry.action.isInterruptible()
                    : (entry.action.getMutexBits() & 3) != 0 && !nativeCompetingGoal(mob, entry))) {
                return false;
            }
        }
        return matched && (!creeper || foundSwell);
    }

    private static boolean nativeCompetingGoal(EntityCreature mob, EntityAITaskEntry entry) {
        Class<?> goal = entry.action.getClass();
        return mob instanceof AbstractSkeleton && (goal == EntityAIFleeSun.class || goal == EntityAIAvoidEntity.class)
                || mob instanceof EntitySpider && goal == EntityAILeapAtTarget.class
                || mob instanceof EntityShulker && goal == EntityAIWatchClosest.class
                || mob instanceof EntityCreeper && isCreeperAvoidance(entry);
    }

    private static boolean isCreeperAvoidance(EntityAITaskEntry entry) {
        return entry.priority == 3 && entry.action.getClass() == EntityAIAvoidEntity.class
                && entry.action.getMutexBits() == 1;
    }

    static boolean canControlImmediately(EntityCreature mob, MobType type) {
        boolean providerGoalCheck = SpecialMobsCompatibility.requiresLiveGoalContract(type)
                || providerMeleePriority(type) >= 0;
        return (providerGoalCheck ? matchesBehavior(mob, type) : hasSupportedState(mob, type))
                && !inNativePhase(mob, type);
    }

    private static boolean hasSupportedState(EntityCreature mob, MobType type) {
        Class<?> navigator = mob instanceof EntitySpider ? PathNavigateClimber.class : PathNavigateGround.class;
        return !mob.isAIDisabled() && !mob.tasks.isControlFlagDisabled(1) && !mob.tasks.isControlFlagDisabled(2)
                && (mob instanceof EntityShulker || mob.getNavigator().getClass() == navigator)
                && (!(mob instanceof EntityZombieVillager) || !((EntityZombieVillager) mob).isConverting())
                && (GaiaCompatibility.meleePriority(type) < 0 || GaiaCompatibility.isHostile(mob))
                && (IceAndFireCompatibility.meleePriority(type) < 0 || IceAndFireCompatibility.isUncommandedOnFoot(mob))
                && (!SpecialMobsCompatibility.isVariantSkeleton(type) || !mob.isRiding()
                && SpecialMobsCompatibility.hasSupportedSkeletonWeapon(type, mob, usesBow(mob)))
                && (!SpecialMobsCompatibility.isVariantBlaze(type) || !mob.isRiding() && !mob.isBeingRidden()
                && SpecialMobsCompatibility.hasSupportedBlazeRange(type, mob))
                && (!(SpecialMobsCompatibility.isVariantSpider(type) || DungeonMobsCompatibility.meleePriority(type) >= 0
                || mob instanceof EntityCreeper || type == MobType.GAIA_DULLAHAN || type == MobType.DEFILED_SCUTTLER)
                || !mob.isRiding() && !mob.isBeingRidden())
                && (type != MobType.SPECIAL_SKELETON || usesBow(mob));
    }

    private static int providerMeleePriority(MobType type) {
        int dungeon = DungeonMobsCompatibility.meleePriority(type);
        if (dungeon >= 0) {
            return dungeon;
        }
        int defiled = DefiledLandsCompatibility.meleePriority(type);
        if (defiled >= 0) {
            return defiled;
        }
        int gaia = GaiaCompatibility.meleePriority(type);
        return gaia >= 0 ? gaia : IceAndFireCompatibility.meleePriority(type);
    }

    static boolean inNativePhase(EntityCreature mob, MobType type) {
        if (IceAndFireCompatibility.usesAnimatedMelee(type) && IceAndFireCompatibility.animatedMeleeUnavailable(type, mob)) {
            return true;
        }
        if (mob instanceof EntitySpider && type != MobType.DEFILED_SCUTTLER && mob.getBrightness() >= 0.5F
                || mob instanceof EntityShulker && !canAttackWhileAttached((EntityShulker) mob)) {
            return true;
        }
        if (mob instanceof EntityCreeper) {
            EntityCreeper creeper = (EntityCreeper) mob;
            if (creeper.getCreeperState() > 0 || creeper.hasIgnited() || creeper.isWet() || creeper.isBurning()) {
                return true;
            }
        }
        for (EntityAITaskEntry entry : mob.tasks.taskEntries) {
            if (entry.using && (SpecialAiCompatibility.isCombatReaction(entry)
                    || SpecialAiCompatibility.isBlockGriefing(entry)
                    || GaiaCompatibility.isAntWander(type, entry)
                    || IceAndFireCompatibility.isHorseGoal(type, entry)
                    || mob instanceof EntityCreeper
                    && (entry.action.getClass() == EntityAICreeperSwell.class || isCreeperAvoidance(entry))
                    || SpecialMobsCompatibility.isVariantSkeleton(type)
                    && (entry.action.getClass() == EntityAIFleeSun.class || entry.action.getClass() == EntityAIAvoidEntity.class))) {
                return true;
            }
        }
        return false;
    }

    private static boolean canAttackWhileAttached(EntityShulker shulker) {
        if (shulker.world.getDifficulty() == EnumDifficulty.PEACEFUL || !(shulker.world instanceof WorldServer)) {
            return false;
        }
        BlockPos anchor = shulker.getAttachmentPos();
        if (anchor == null) {
            return false;
        }
        LoadedPathTerrain terrain = new LoadedPathTerrain((WorldServer) shulker.world);
        EnumFacing face = shulker.getAttachmentFacing();
        return terrain.isAirBlock(anchor) && terrain.isNormalCube(anchor.offset(face), false)
                && !terrain.isNormalCube(anchor.offset(face.getOpposite()), true);
    }

    private static boolean usesBow(EntityCreature mob) {
        return mob instanceof AbstractSkeleton && mob.getHeldItemMainhand().getItem() instanceof ItemBow;
    }

    static EntityLivingBase resolve(WorldServer world, EntityCreature mob, Entity entity, MobType type) {
        if (!(entity instanceof EntityLivingBase)) {
            return null;
        }
        EntityLivingBase target = (EntityLivingBase) entity;
        BlockPos position = new BlockPos(target);
        double range = followRange(mob);
        if (SpecialMobsCompatibility.isVariantBlaze(type)) {
            double attackRange = SpecialMobsCompatibility.isMeleeBlaze(type) ? MELEE_BLAZE_PURSUIT_RANGE
                    : SpecialMobsCompatibility.blazeRange(mob);
            range = Math.min(range, attackRange);
        }
        return isAttackable(mob, target) && IceAndFireCompatibility.acceptsTarget(type, target)
                && target.world == world && world.isBlockLoaded(position)
                && world.getWorldBorder().contains(position)
                && mob.isWithinHomeDistanceFromPosition(position)
                && mob.getDistanceSq(target) <= range * range
                && (!(mob instanceof EntityShulker) || mob.getDistanceSq(target) < 400.0)
                && (!(mob instanceof EntityBlaze) || mob.getDistanceSq(target) < range * range) ? target : null;
    }

    static TargetObservation observe(WorldServer world, EntityCreature mob, Entity entity, WorkSample sample, MobType type) {
        EntityLivingBase target = resolve(world, mob, entity, type);
        if (target == null) {
            return TargetObservation.INELIGIBLE;
        }
        Vec3d position = mob.getPositionVector();
        LoadedPathTerrain terrain = new LoadedPathTerrain(world);
        boolean bow = usesBow(mob);
        boolean needsSight = !(mob instanceof EntityZombie && target instanceof EntityVillager)
                && !DefiledLandsCompatibility.canAcquireWithoutSight(type, target);
        boolean sightLost = needsSight && !sees(terrain, mob, target, position);
        if (SpecialMobsCompatibility.isMeleeBlaze(type)) {
            double distanceSquared = mob.getDistanceSq(target);
            if (distanceSquared < 4.0) {
                return sightLost ? TargetObservation.obscured(true) : TargetObservation.IN_REACH;
            }
            double distance = Math.sqrt(distanceSquared);
            Vec3d approach = target.getPositionVector().subtract(position).scale((distance - 1.0) / distance);
            if (sample != null) {
                sample.pathQuery();
            }
            if (!terrain.clearFlight(mob.getEntityBoundingBox(), approach)) {
                return TargetObservation.INELIGIBLE;
            }
            return sightLost ? TargetObservation.obscured(false) : TargetObservation.pursuit(distance);
        }
        if (mob instanceof EntityBlaze || mob instanceof EntityShulker) {
            return observeProjectile(terrain, mob, target, sightLost);
        }
        boolean attackGeometry = canAttackFrom(terrain, mob, target, position, bow, type);
        if (!attackGeometry && !canPursue(world, terrain, mob, target, bow, sample, type)) {
            return TargetObservation.INELIGIBLE;
        }
        if (sightLost) {
            return TargetObservation.obscured(!bow && !IceAndFireCompatibility.usesAnimatedMelee(type) && attackGeometry);
        }
        return attackGeometry ? TargetObservation.IN_REACH : TargetObservation.pursuit(Math.sqrt(mob.getDistanceSq(target)));
    }

    private static TargetObservation observeProjectile(LoadedPathTerrain terrain, EntityCreature mob,
                                                       EntityLivingBase target, boolean sightLost) {
        Vec3d to = target.getPositionVector().add(0, target.height / 2.0, 0);
        Vec3d from;
        if (mob instanceof EntityBlaze) {
            if (mob.getDistanceSq(target) < 4.0) {
                return sightLost ? TargetObservation.obscured(true) : TargetObservation.IN_REACH;
            }
            if (target.isImmuneToFire() || target.isPotionActive(MobEffects.FIRE_RESISTANCE)) {
                return TargetObservation.INELIGIBLE;
            }
            if (sightLost) {
                return TargetObservation.obscured(false);
            }
            from = mob.getPositionVector().add(0, mob.height / 2.0 + 0.5, 0);
            // The native aim vector is computed half a block below the launch origin.
            to = to.add(0, 0.5, 0);
        } else {
            EntityShulker shulker = (EntityShulker) mob;
            BlockPos attachment = shulker.getAttachmentPos();
            if (attachment == null) {
                return TargetObservation.INELIGIBLE;
            }
            from = new Vec3d(attachment).add(0.5, 0.5, 0.5);
            if (sightLost) {
                return TargetObservation.obscured(false);
            }
            if (!hasShulkerLaunch(attachment, shulker.getAttachmentFacing().getAxis(), to, terrain::isAirBlock)) {
                return TargetObservation.INELIGIBLE;
            }
        }
        return terrain.clearLine(from, to) ? TargetObservation.IN_REACH : TargetObservation.INELIGIBLE;
    }

    static boolean hasShulkerLaunch(BlockPos origin, EnumFacing.Axis attachmentAxis, Vec3d targetCenter,
                                    Predicate<BlockPos> isAir) {
        BlockPos target = new BlockPos(targetCenter);
        if (target.distanceSqToCenter(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5) < 4.0) {
            return true;
        }
        double distance = origin.distanceSq(target);
        for (EnumFacing face : EnumFacing.values()) {
            BlockPos next = origin.offset(face);
            if (face.getAxis() != attachmentAxis && next.distanceSq(target) < distance && isAir.test(next)) {
                return true;
            }
        }
        return false;
    }

    static boolean isAttackable(EntityLiving mob, EntityLivingBase target) {
        if (target == null || target == mob || !target.isEntityAlive() || target.world != mob.world
                || target.getIsInvulnerable() || mob.isOnSameTeam(target) || !mob.canAttackClass(target.getClass())) {
            return false;
        }
        if (target instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) target;
            return !player.isSpectator() && !player.capabilities.disableDamage;
        }
        return true;
    }

    static EntityLivingBase resolveStationary(WorldServer world, EntityCreature mob, Entity entity,
                                               FoliaathCompatibility policy) {
        EntityLivingBase target = resolve(world, mob, entity, MobType.FOLIAATH);
        return target != null && policy.accepts(target) && policy.inBiteRange(mob, target) ? target : null;
    }

    static TargetObservation observeStationary(WorldServer world, EntityCreature mob, Entity entity,
                                                FoliaathCompatibility policy) {
        EntityLivingBase target = resolveStationary(world, mob, entity, policy);
        if (target == null) {
            return TargetObservation.INELIGIBLE;
        }
        return sees(new LoadedPathTerrain(world), mob, target, mob.getPositionVector())
                ? TargetObservation.IN_REACH : TargetObservation.obscured(true);
    }

    private static double followRange(EntityLiving mob) {
        return mob.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue();
    }

    private static boolean canPursue(WorldServer world, LoadedPathTerrain terrain, EntityLiving mob,
                                      EntityLivingBase target, boolean bow, WorkSample sample, MobType type) {
        if (!(mob.getNavigator() instanceof PathNavigateGround)) {
            return false;
        }
        PathNavigateGround navigator = (PathNavigateGround) mob.getNavigator();
        // Ask the vanilla search for a route without changing the live navigator or loading chunks.
        WalkNodeProcessor nodes = new WalkNodeProcessor();
        nodes.setCanEnterDoors(navigator.getEnterDoors());
        nodes.setCanSwim(navigator.getCanSwim());
        nodes.setCanOpenDoors(mob instanceof EntityZombie && ((EntityZombie) mob).isBreakDoorsTaskSet());
        BlockPos destination = groundDestination(terrain, new BlockPos(target), world.getHeight());
        if (sample != null) {
            sample.pathQuery();
        }
        Path path = new PathFinder(nodes).findPath(terrain, mob, destination, (float) followRange(mob));
        if (path == null) {
            return false;
        }
        int length = usablePathLength(world, terrain, mob, path);
        // A partial path is viable only if a reachable waypoint has an actual attack.
        for (int i = length - 1; i > 0; i--) {
            Vec3d waypoint = path.getVectorFromIndex(mob, i);
            BlockPos floor = new BlockPos(waypoint).down();
            double height = terrain.getBlockState(floor).getBoundingBox(terrain, floor).maxY;
            Vec3d stance = waypoint.add(0.0, height - 1.0, 0.0);
            if ((!(bow || IceAndFireCompatibility.usesAnimatedMelee(type)) || sees(terrain, mob, target, stance))
                    && canAttackFrom(terrain, mob, target, stance, bow, type)) {
                return true;
            }
        }
        return false;
    }

    // Match PathNavigateGround's target height normalization using only the loaded terrain view.
    private static BlockPos groundDestination(LoadedPathTerrain terrain, BlockPos pos, int worldHeight) {
        if (terrain.isAirBlock(pos)) {
            BlockPos below = pos.down();
            while (below.getY() > 0 && terrain.isAirBlock(below)) {
                below = below.down();
            }
            if (below.getY() > 0) {
                return below.up();
            }
            pos = below;
            while (pos.getY() < worldHeight && terrain.isAirBlock(pos)) {
                pos = pos.up();
            }
        }
        while (pos.getY() < worldHeight && terrain.getBlockState(pos).getMaterial().isSolid()) {
            pos = pos.up();
        }
        return pos;
    }

    private static int usablePathLength(WorldServer world, LoadedPathTerrain terrain, EntityLiving mob, Path path) {
        int length = path.getCurrentPathLength();
        if (mob instanceof AbstractSkeleton && world.isDaytime()
                && mob.getItemStackFromSlot(EntityEquipmentSlot.HEAD).isEmpty()
                && !terrain.seesSky(new BlockPos(MathHelper.floor(mob.posX),
                (int) (mob.getEntityBoundingBox().minY + 0.5D), MathHelper.floor(mob.posZ)))) {
            for (int i = 0; i < length; i++) {
                if (terrain.seesSky(new BlockPos(path.getVectorFromIndex(mob, i)))) {
                    return i - 1;
                }
            }
        }
        return length;
    }

    private static boolean sees(LoadedPathTerrain terrain, EntityLiving mob, EntityLivingBase target, Vec3d from) {
        return terrain.clearLine(from.add(0.0, mob.getEyeHeight(), 0.0),
                target.getPositionVector().add(0.0, target.getEyeHeight(), 0.0));
    }

    private static boolean canAttackFrom(LoadedPathTerrain terrain, EntityLiving mob, EntityLivingBase target,
                                         Vec3d from, boolean bow, MobType type) {
        if (bow) {
            return SkeletonBow.canReach(from.add(0.0, mob.getEyeHeight() - 0.1F, 0.0),
                    target.getEntityBoundingBox(), target.height, terrain::clearLine,
                    position -> terrain.getBlockState(new BlockPos(position)).getMaterial() == Material.WATER);
        }
        if (mob instanceof EntityCreeper) {
            return from.squareDistanceTo(target.posX, target.posY, target.posZ) < 9.0
                    && sees(terrain, mob, target, from);
        }
        if (IceAndFireCompatibility.usesAnimatedMelee(type)) {
            return IceAndFireCompatibility.animatedMeleeReaches(from, target);
        }
        float reach = mob.width * 2.0F;
        float reachSquared = mob instanceof EntitySpider ? 4.0F : reach * reach;
        return from.squareDistanceTo(target.posX, target.getEntityBoundingBox().minY, target.posZ)
                <= reachSquared + target.width;
    }

}
