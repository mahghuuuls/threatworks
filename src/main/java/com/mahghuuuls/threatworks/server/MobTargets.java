package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.threat.Encounter.TargetObservation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemBow;
import net.minecraft.block.material.Material;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathFinder;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.pathfinding.WalkNodeProcessor;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

final class MobTargets {
    private static final ResourceLocation ZOMBIE_ID = new ResourceLocation("minecraft", "zombie");
    private static final ResourceLocation SKELETON_ID = new ResourceLocation("minecraft", "skeleton");

    private MobTargets() {
    }

    static boolean supports(EntityLivingBase mob) {
        if (!(mob.world instanceof WorldServer)) {
            return false;
        }
        Class<?> type = mob.getClass();
        return (type == EntityZombie.class && ZOMBIE_ID.equals(EntityList.getKey(mob)))
                || (type == EntitySkeleton.class && SKELETON_ID.equals(EntityList.getKey(mob)));
    }

    static EntityLivingBase resolve(WorldServer world, EntityCreature mob, Entity entity) {
        if (!(entity instanceof EntityLivingBase)) {
            return null;
        }
        EntityLivingBase target = (EntityLivingBase) entity;
        BlockPos position = new BlockPos(target);
        double range = followRange(mob);
        return isAttackable(mob, target) && target.world == world && world.isBlockLoaded(position)
                && world.getWorldBorder().contains(position)
                && mob.isWithinHomeDistanceFromPosition(position)
                && mob.getDistanceSq(target) <= range * range ? target : null;
    }

    static TargetObservation observe(WorldServer world, EntityCreature mob, Entity entity) {
        EntityLivingBase target = resolve(world, mob, entity);
        if (target == null) {
            return TargetObservation.INELIGIBLE;
        }
        Vec3d position = mob.getPositionVector();
        LoadedPathTerrain terrain = new LoadedPathTerrain(world);
        boolean bow = mob instanceof EntitySkeleton && mob.getHeldItemMainhand().getItem() instanceof ItemBow;
        // The zombie's villager goal acquires without sight. Its player and golem goals,
        // and the skeleton's target goals, require sight when acquiring a new target.
        boolean needsSight = !(mob instanceof EntityZombie && target instanceof EntityVillager);
        boolean sightLost = needsSight && !sees(terrain, mob, target, position);
        boolean attackGeometry = canAttackFrom(terrain, mob, target, position, bow);
        if (!attackGeometry && !canPursue(world, terrain, mob, target, bow)) {
            return TargetObservation.INELIGIBLE;
        }
        if (sightLost) {
            return TargetObservation.obscured(!bow && attackGeometry);
        }
        return attackGeometry ? TargetObservation.IN_REACH : TargetObservation.pursuit(Math.sqrt(mob.getDistanceSq(target)));
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

    private static double followRange(EntityLiving mob) {
        return mob.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).getAttributeValue();
    }

    private static boolean canPursue(WorldServer world, LoadedPathTerrain terrain, EntityLiving mob,
                                      EntityLivingBase target, boolean bow) {
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
            if ((!bow || sees(terrain, mob, target, stance)) && canAttackFrom(terrain, mob, target, stance, bow)) {
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
        if (mob instanceof EntitySkeleton && world.isDaytime()
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
                                         Vec3d from, boolean bow) {
        if (bow) {
            return SkeletonBow.canReach(from.add(0.0, mob.getEyeHeight() - 0.1F, 0.0),
                    target.getEntityBoundingBox(), target.height, terrain::clearLine,
                    position -> terrain.getBlockState(new BlockPos(position)).getMaterial() == Material.WATER);
        }
        float reach = mob.width * 2.0F;
        return from.squareDistanceTo(target.posX, target.getEntityBoundingBox().minY, target.posZ)
                <= reach * reach + target.width;
    }

}
