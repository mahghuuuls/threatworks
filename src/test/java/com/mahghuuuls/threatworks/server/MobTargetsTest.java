package com.mahghuuuls.threatworks.server;

import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobTargetsTest {
    private static final BlockPos ANCHOR = new BlockPos(0, 64, 0);

    @Test
    void homingBulletNeedsAnOpenFirstStepTowardADistantTargetOffTheAttachmentAxis() {
        Vec3d diagonal = new Vec3d(4.5, 68.5, 0.5);
        assertTrue(MobTargets.hasShulkerLaunch(ANCHOR, Axis.Y, diagonal, ANCHOR.east()::equals));
        assertFalse(MobTargets.hasShulkerLaunch(ANCHOR, Axis.Y, diagonal, ANCHOR.up()::equals));
        assertFalse(MobTargets.hasShulkerLaunch(ANCHOR, Axis.Y, diagonal, ANCHOR.west()::equals));
        assertFalse(MobTargets.hasShulkerLaunch(ANCHOR, Axis.Y, diagonal, position -> false));
    }

    @Test
    void homingBulletUsesDirectFlightWithinTwoBlocks() {
        assertTrue(MobTargets.hasShulkerLaunch(ANCHOR, Axis.Y, new Vec3d(0.5, 65.5, 0.5), position -> false));
        assertFalse(MobTargets.hasShulkerLaunch(ANCHOR, Axis.Y, new Vec3d(0.5, 66.5, 0.5), position -> false));
    }
}
