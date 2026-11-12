package com.mahghuuuls.threatworks.server;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.DamageSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

class DamageAttributionTest {
    private final Object worldSession = new Object();
    private final EntityLivingBase shooter = new EntityArmorStand(null);

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void arrowUsesItsOwnSnapshotAndNeverReadsCurrentGear() {
        TestArrow arrow = new TestArrow(shooter);
        arrow.launch = new ArrowLaunch.Snapshot(shooter.getUniqueID(), worldSession, -100);
        DamageSource impact = DamageSource.causeArrowDamage(arrow, shooter);
        DamageAttribution hit = DamageAttribution.resolve(impact, worldSession);
        assertSame(shooter, hit.attacker);
        assertEquals(-100, hit.points(() -> fail("Arrow impact cannot read equipment")));
    }

    @Test
    void knownShooterWithoutReliableObservationUsesBasePoints() {
        TestArrow arrow = new TestArrow(shooter);
        DamageSource impact = DamageSource.causeArrowDamage(arrow, shooter);
        assertEquals(0, DamageAttribution.resolve(impact, worldSession).points(() -> fail("No snapshot")));
        arrow.launch = new ArrowLaunch.Snapshot(shooter.getUniqueID(), new Object(), 100);
        assertEquals(0, DamageAttribution.resolve(impact, worldSession).points(() -> fail("Stale snapshot")));
    }

    @Test
    void absentOrConflictingShooterGetsNoCredit() {
        TestArrow arrow = new TestArrow(shooter);
        EntityLivingBase other = new EntityArmorStand(null);
        assertNull(DamageAttribution.resolve(DamageSource.causeArrowDamage(arrow, other), worldSession));
        arrow.launch = new ArrowLaunch.Snapshot(other.getUniqueID(), worldSession, 100);
        assertNull(DamageAttribution.resolve(DamageSource.causeArrowDamage(arrow, shooter), worldSession));
        arrow.shootingEntity = null;
        assertNull(DamageAttribution.resolve(DamageSource.causeArrowDamage(arrow, arrow), worldSession));
        assertNull(DamageAttribution.resolve(DamageSource.causeArrowDamage(arrow, shooter), worldSession));
    }

    private static final class TestArrow extends EntityTippedArrow implements ArrowLaunch {
        Snapshot launch;

        TestArrow(EntityLivingBase shooter) {
            super(null, shooter);
        }

        @Override
        public Snapshot threatworks$getLaunch() {
            return launch;
        }
    }
}
