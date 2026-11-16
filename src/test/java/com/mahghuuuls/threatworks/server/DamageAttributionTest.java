package com.mahghuuuls.threatworks.server;

import com.mahghuuuls.threatworks.compat.WizardryCompatibilityTestAccess;
import electroblob.wizardry.entity.living.EntityBlazeMinion;
import electroblob.wizardry.entity.living.EntityIceWraith;
import electroblob.wizardry.entity.living.EntityLightningWraith;
import electroblob.wizardry.entity.living.EntitySkeletonMinion;
import electroblob.wizardry.entity.living.EntitySpiritWolf;
import electroblob.wizardry.entity.living.EntityZombieMinion;
import electroblob.wizardry.entity.projectile.EntityIceShard;
import electroblob.wizardry.util.IndirectMinionDamage;
import electroblob.wizardry.util.MagicDamage;
import electroblob.wizardry.util.MinionDamage;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void wizardryArcUsesTheActualLightningWraithAndImpactPoints() {
        EntityLightningWraith wraith = new EntityLightningWraith(null);
        wraith.setOwnerId(shooter.getUniqueID());
        DamageSource arc = new MinionDamage("shock", wraith, shooter, MagicDamage.DamageType.SHOCK, false);
        DamageAttribution hit = DamageAttribution.resolve(arc, worldSession, newWizardrySource());
        assertNotNull(hit);
        assertSame(wraith, hit.attacker);
        assertSame(shooter, hit.summonOwner);
        assertFalse(hit.mayOpenEncounter());
        assertEquals(1.25, hit.multiplier(() -> 1.25));
    }

    @Test
    void wizardryFireballUsesBaseThreatAndChecksTheOriginalShooter() {
        EntityBlazeMinion blaze = new EntityBlazeMinion(null);
        blaze.setOwnerId(shooter.getUniqueID());
        EntitySmallFireball fireball = new EntitySmallFireball(null);
        fireball.shootingEntity = blaze;
        DamageSource impact = new IndirectMinionDamage("fireball", fireball, blaze, shooter,
                MagicDamage.DamageType.MAGIC, false).setFireDamage().setProjectile();
        DamageAttribution.SummonSource adapter = newWizardrySource();
        DamageAttribution hit = DamageAttribution.resolve(impact, worldSession, adapter);
        assertNotNull(hit);
        assertSame(blaze, hit.attacker);
        assertSame(shooter, hit.summonOwner);
        assertFalse(hit.mayOpenEncounter());
        assertEquals(1.0, hit.multiplier(() -> fail("Fireball has no snapshot")));
        DamageSource raw = DamageSource.causeFireballDamage(fireball, blaze);
        assertSame(blaze, DamageAttribution.resolve(raw, worldSession, adapter).attacker);

        EntityBlazeMinion otherBlaze = new EntityBlazeMinion(null);
        otherBlaze.setOwnerId(shooter.getUniqueID());
        fireball.shootingEntity = otherBlaze;
        assertNull(DamageAttribution.resolve(impact, worldSession, adapter));

        fireball.shootingEntity = null;
        assertNull(DamageAttribution.resolve(raw, worldSession, adapter));
        assertNull(DamageAttribution.resolve(DamageSource.causeFireballDamage(fireball, null), worldSession, adapter));
    }

    @Test
    void wizardryDoesNotTreatASecondSummonAsAProjectile() {
        EntityZombieMinion originalActor = new EntityZombieMinion(null);
        EntityZombieMinion intermediateCaster = new EntityZombieMinion(null);
        originalActor.setOwnerId(intermediateCaster.getUniqueID());
        intermediateCaster.setOwnerId(shooter.getUniqueID());
        DamageSource rewrapped = new IndirectMinionDamage("mob", originalActor, intermediateCaster, shooter,
                MagicDamage.DamageType.MAGIC, false);
        DamageSource raw = new EntityDamageSourceIndirect("mob", originalActor, intermediateCaster);
        assertNull(DamageAttribution.resolve(rewrapped, worldSession, newWizardrySource()));
        assertNull(DamageAttribution.resolve(raw, worldSession, newWizardrySource()));
        DamageSource unknownCarrier = new IndirectMinionDamage("wizardry_magic", new EntityXPOrb(null),
                intermediateCaster, shooter, MagicDamage.DamageType.MAGIC, false);
        assertNull(DamageAttribution.resolve(unknownCarrier, worldSession, newWizardrySource()));
    }

    @Test
    void wizardryIceShardUsesItsNativeCasterEvenWithoutAPlayerWrapper() {
        EntityIceWraith wraith = new EntityIceWraith(null);
        wraith.setOwnerId(shooter.getUniqueID());
        EntityIceShard shard = new EntityIceShard(null);
        shard.setCaster(wraith);
        DamageSource source = new EntityDamageSourceIndirect("wizardry_magic", shard, wraith);
        DamageAttribution.SummonSource adapter = newWizardrySource();
        DamageAttribution hit = DamageAttribution.resolve(source, worldSession, adapter);
        assertNotNull(hit);
        assertSame(wraith, hit.attacker);
        assertEquals(1.0, hit.multiplier(() -> fail("Ice Shard has no vanilla arrow snapshot")));
        shard.setCaster(shooter);
        assertNull(DamageAttribution.resolve(source, worldSession, adapter));
    }

    @Test
    void missingOriginalShooterFieldDoesNotDisableDirectOrRawSummonPaths() {
        DamageAttribution.SummonSource adapter = WizardryCompatibilityTestAccess.withoutOriginalShooterField();
        EntityZombieMinion zombie = new EntityZombieMinion(null);
        zombie.setOwnerId(shooter.getUniqueID());
        DamageSource direct = new MinionDamage("mob", zombie, shooter, MagicDamage.DamageType.MAGIC, false);
        assertSame(zombie, DamageAttribution.resolve(direct, worldSession, adapter).attacker);

        TestArrow arrow = new TestArrow(zombie);
        DamageSource wrapped = new IndirectMinionDamage("arrow", arrow, zombie, shooter,
                MagicDamage.DamageType.MAGIC, false).setProjectile();
        assertNull(DamageAttribution.resolve(wrapped, worldSession, adapter));
        assertSame(zombie, DamageAttribution.resolve(DamageSource.causeArrowDamage(arrow, zombie), worldSession,
                adapter).attacker);
    }

    @Test
    void spiritWolfUsesItsNativeTameableOwnerContract() {
        EntitySpiritWolf wolf = new EntitySpiritWolf(null);
        wolf.setOwnerId(shooter.getUniqueID());
        wolf.setTamed(true);
        DamageAttribution.SummonSource adapter = newWizardrySource();
        DamageAttribution hit = DamageAttribution.resolve(DamageSource.causeMobDamage(wolf), worldSession, adapter);
        assertNotNull(hit);
        assertSame(wolf, hit.attacker);
        assertFalse(hit.mayOpenEncounter());
        assertNull(DamageAttribution.resolve(DamageSource.MAGIC, worldSession, adapter));
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

    @Test
    void missingSavedCasterDoesNotQualifyNewSummonPathsOrRemoveReleasedRawCredit() {
        UUID emptyCaster = new UUID(0, 0);
        EntityBlazeMinion blaze = new EntityBlazeMinion(null);
        EntityZombieMinion zombie = new EntityZombieMinion(null);
        EntitySkeletonMinion skeleton = new EntitySkeletonMinion(null);
        DamageAttribution.SummonSource adapter = newWizardrySource();
        blaze.setOwnerId(emptyCaster);
        zombie.setOwnerId(emptyCaster);
        skeleton.setOwnerId(emptyCaster);
        EntitySmallFireball fireball = new EntitySmallFireball(null);
        fireball.shootingEntity = blaze;

        assertNull(DamageAttribution.resolve(DamageSource.causeFireballDamage(fireball, blaze), worldSession, adapter));
        DamageAttribution ordinaryMelee = DamageAttribution.resolve(DamageSource.causeMobDamage(blaze), worldSession,
                adapter);
        assertSame(blaze, ordinaryMelee.attacker);
        assertNull(ordinaryMelee.summonOwner);
        assertSame(zombie, DamageAttribution.resolve(DamageSource.causeMobDamage(zombie), worldSession, adapter).attacker);
        TestArrow arrow = new TestArrow(skeleton);
        assertSame(skeleton, DamageAttribution.resolve(DamageSource.causeArrowDamage(arrow, skeleton), worldSession,
                adapter).attacker);
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
