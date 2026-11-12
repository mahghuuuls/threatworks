package com.mahghuuuls.threatworks.server;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkeletonBowTest {
    private static final Vec3d ORIGIN = new Vec3d(0.0, 1.64, 0.0);
    private static final AxisAlignedBB GOLEM = new AxisAlignedBB(16.3, 0.0, -0.7, 17.7, 2.7, 0.7);

    @Test
    void clearVanillaArcCanHitBeyondTheDistanceWhereTheBowGoalStopsApproaching() {
        assertTrue(SkeletonBow.canReach(ORIGIN, GOLEM, 2.7F, (from, to) -> true, position -> false));
    }

    @Test
    void theArcClearsALowBlockButIntersectsAHighBlockAboveTheDirectBodyRay() {
        AxisAlignedBB low = new AxisAlignedBB(8.0, 0.0, -1.0, 9.0, 1.5, 1.0);
        AxisAlignedBB high = new AxisAlignedBB(8.0, 2.1, -1.0, 9.0, 3.5, 1.0);

        assertTrue(SkeletonBow.canReach(ORIGIN, GOLEM, 2.7F,
                (from, to) -> low.calculateIntercept(from, to) == null, position -> false));
        assertFalse(SkeletonBow.canReach(ORIGIN, GOLEM, 2.7F,
                (from, to) -> high.calculateIntercept(from, to) == null, position -> false));
    }
}
