package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.threat.Encounter.TargetObservation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.dragon.phase.PhaseList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.feature.WorldGenEndPodium;
import net.minecraftforge.common.ForgeHooks;

/** Observes the conditions used by the Dragon's native ordinary strafe. */
final class DragonTargeting implements MobPolicy {
    private static final double STRAFE_RANGE = 64.0;

    static boolean isActive(EntityDragon dragon) {
        return dragon.world instanceof WorldServer && dragon.isEntityAlive() && !dragon.isAIDisabled()
                && dragon.getPhaseManager().getCurrentPhase().getType() != PhaseList.DYING;
    }

    @Override
    public boolean controlsImmediately(EntityLiving mob) {
        return false;
    }

    @Override
    public boolean accepts(EntityLiving mob, EntityLivingBase participant) {
        return participant instanceof EntityPlayerMP;
    }

    @Override
    public EntityPlayerMP resolve(WorldServer world, EntityLiving mob, Entity candidate) {
        if (!(candidate instanceof EntityPlayerMP)) {
            return null;
        }
        EntityPlayerMP player = (EntityPlayerMP) candidate;
        BlockPos position = new BlockPos(player);
        return MobTargets.isAttackable(mob, player) && world.getEntityFromUuid(player.getUniqueID()) == player
                && world.isBlockLoaded(position)
                && world.getWorldBorder().contains(position) ? player : null;
    }

    @Override
    public TargetObservation observe(WorldServer world, EntityLiving mob, Entity candidate, WorkSample sample) {
        return observe(world, mob, candidate, loadedPodium(world));
    }

    TargetObservation observe(WorldServer world, EntityLiving mob, Entity candidate, BlockPos podium) {
        EntityPlayerMP player = eligiblePlayer(world, mob, candidate, podium);
        if (player == null) {
            return TargetObservation.INELIGIBLE;
        }
        double distance = mob.getDistanceSq(player);
        // Native flight can reach the player's position through terrain. Fireball release still needs sight.
        return distance < STRAFE_RANGE * STRAFE_RANGE && mob.canEntityBeSeen(player)
                ? TargetObservation.IN_REACH : TargetObservation.pursuit(Math.sqrt(distance));
    }

    static BlockPos loadedPodium(WorldServer world) {
        BlockPos origin = WorldGenEndPodium.END_PODIUM_LOCATION;
        return world.isBlockLoaded(origin) ? world.getTopSolidOrLiquidBlock(origin) : null;
    }

    EntityPlayerMP eligiblePlayer(WorldServer world, EntityLiving mob, Entity candidate, BlockPos podium) {
        if (podium == null) {
            return null;
        }
        EntityPlayerMP player = resolve(world, mob, candidate);
        if (player == null) {
            return null;
        }
        // Keep the holding pattern's podium-relative search and Forge's visibility adjustment.
        double range = STRAFE_RANGE;
        if (player.isSneaking()) {
            range *= 0.800000011920929D;
        }
        if (player.isInvisible()) {
            range *= (double) (0.7F * Math.max(0.1F, player.getArmorVisibility()));
        }
        range = ForgeHooks.getPlayerVisibilityDistance(player, range, STRAFE_RANGE);
        double x = player.posX - (podium.getX() + 0.5);
        double y = player.posY - (podium.getY() + 0.5);
        double z = player.posZ - (podium.getZ() + 0.5);
        return x * x + z * z < range * range && Math.abs(y) < STRAFE_RANGE * STRAFE_RANGE ? player : null;
    }
}
