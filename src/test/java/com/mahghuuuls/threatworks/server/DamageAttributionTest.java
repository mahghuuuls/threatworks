package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.compat.WizardryCompatibilityTestAccess;
import electroblob.wizardry.entity.living.EntitySkeletonMinion;
import electroblob.wizardry.entity.living.EntityZombieMinion;
import electroblob.wizardry.util.IndirectMinionDamage;
import electroblob.wizardry.util.MagicDamage;
import electroblob.wizardry.util.MinionDamage;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        arrow.launch = new ArrowLaunch.Snapshot(shooter.getUniqueID(), worldSession, 0.5);
        DamageSource impact = DamageSource.causeArrowDamage(arrow, shooter);
        DamageAttribution hit = DamageAttribution.resolve(impact, worldSession);
        assertSame(shooter, hit.attacker);
        assertEquals(0.5, hit.multiplier(() -> fail("Arrow impact cannot read equipment")));
    }

    @Test
    void knownShooterWithoutReliableObservationUsesBasePoints() {
        TestArrow arrow = new TestArrow(shooter);
        DamageSource impact = DamageSource.causeArrowDamage(arrow, shooter);
        assertEquals(1, DamageAttribution.resolve(impact, worldSession).multiplier(() -> fail("No snapshot")));
        arrow.launch = new ArrowLaunch.Snapshot(shooter.getUniqueID(), new Object(), 2);
        assertEquals(1, DamageAttribution.resolve(impact, worldSession).multiplier(() -> fail("Stale snapshot")));
    }

    @Test
    void absentOrConflictingShooterGetsNoCredit() {
        TestArrow arrow = new TestArrow(shooter);
        EntityLivingBase other = new EntityArmorStand(null);
        assertNull(DamageAttribution.resolve(DamageSource.causeArrowDamage(arrow, other), worldSession));
        arrow.launch = new ArrowLaunch.Snapshot(other.getUniqueID(), worldSession, 2);
        assertNull(DamageAttribution.resolve(DamageSource.causeArrowDamage(arrow, shooter), worldSession));
        arrow.shootingEntity = null;
        assertNull(DamageAttribution.resolve(DamageSource.causeArrowDamage(arrow, arrow), worldSession));
        assertNull(DamageAttribution.resolve(DamageSource.causeArrowDamage(arrow, shooter), worldSession));
    }

    @Test
    void ordinaryMobMeleeCreditsTheAttackerWithoutOpeningAndRejectsAnIndirectAlias() {
        EntityZombie zombie = new EntityZombie(null);
        DamageAttribution hit = DamageAttribution.resolve(DamageSource.causeMobDamage(zombie), worldSession);
        assertSame(zombie, hit.attacker);
        assertFalse(hit.mayOpenEncounter());
        assertEquals(1.5, hit.multiplier(() -> 1.5));
        assertNull(DamageAttribution.resolve(new EntityDamageSourceIndirect("mob", shooter, zombie), worldSession));
    }

    @Test
    void wizardryMeleeCreditsTheOwnedMinionAndCannotOpenAnEncounter() {
        EntityZombieMinion zombie = new EntityZombieMinion(null);
        zombie.setOwnerId(shooter.getUniqueID());
        DamageSource source = new MinionDamage("mob", zombie, shooter, MagicDamage.DamageType.MAGIC, false);
        DamageAttribution hit = DamageAttribution.resolve(source, worldSession, newWizardrySource());
        assertSame(zombie, hit.attacker);
        assertSame(shooter, hit.summonOwner);
        assertFalse(hit.mayOpenEncounter());
        zombie.setOwnerId(zombie.getUniqueID());
        assertNull(DamageAttribution.resolve(source, worldSession, newWizardrySource()));
    }

    @Test
    void wizardryArrowKeepsTheMinionSnapshotAndRejectsAnUnverifiableShooter() {
        EntitySkeletonMinion skeleton = new EntitySkeletonMinion(null);
        skeleton.setOwnerId(shooter.getUniqueID());
        TestArrow arrow = new TestArrow(skeleton);
        DamageSource source = new IndirectMinionDamage("arrow", arrow, skeleton, shooter,
                MagicDamage.DamageType.MAGIC, false).setProjectile();
        DamageAttribution.SummonSource adapter = newWizardrySource();
        assertEquals(1, DamageAttribution.resolve(source, worldSession, adapter).multiplier(() -> fail("No launch snapshot")));
        arrow.launch = new ArrowLaunch.Snapshot(skeleton.getUniqueID(), worldSession, 2);
        DamageAttribution hit = DamageAttribution.resolve(source, worldSession, adapter);
        assertSame(skeleton, hit.attacker);
        assertSame(shooter, hit.summonOwner);
        assertFalse(hit.mayOpenEncounter());
        assertEquals(2, hit.multiplier(() -> fail("Use the minion's launch snapshot")));
        EntitySkeletonMinion otherMinion = new EntitySkeletonMinion(null);
        otherMinion.setOwnerId(shooter.getUniqueID());
        arrow.launch = null;
        arrow.shootingEntity = otherMinion;
        assertNull(DamageAttribution.resolve(source, worldSession, adapter));
        arrow.shootingEntity = null;
        assertNull(DamageAttribution.resolve(source, worldSession, adapter));
    }

    @Test
    void wizardryMinionsKeepCreditWhenTheirCasterIsNotInTheWorld() {
        EntityZombieMinion zombie = new EntityZombieMinion(null);
        EntitySkeletonMinion skeleton = new EntitySkeletonMinion(null);
        zombie.setOwnerId(shooter.getUniqueID());
        skeleton.setOwnerId(shooter.getUniqueID());
        DamageAttribution.SummonSource adapter = newWizardrySource();
        DamageAttribution melee = DamageAttribution.resolve(DamageSource.causeMobDamage(zombie), worldSession, adapter);
        assertSame(zombie, melee.attacker);
        assertNull(melee.summonOwner);
        assertFalse(melee.mayOpenEncounter());
        TestArrow arrow = new TestArrow(skeleton);
        DamageAttribution ranged = DamageAttribution.resolve(DamageSource.causeArrowDamage(arrow, skeleton), worldSession, adapter);
        assertSame(skeleton, ranged.attacker);
        assertNull(ranged.summonOwner);
        assertFalse(ranged.mayOpenEncounter());
        assertEquals(1, ranged.multiplier(() -> fail("No launch snapshot")));
        skeleton.setOwnerId(null);
        assertNull(DamageAttribution.resolve(DamageSource.causeArrowDamage(arrow, skeleton), worldSession, adapter));
    }

    private static DamageAttribution.SummonSource newWizardrySource() {
        return WizardryCompatibilityTestAccess.qualified();
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
