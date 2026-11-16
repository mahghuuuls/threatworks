package com.mahghuuuls.threatworks.compat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.DamageSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowGolemOriginTest {
    @BeforeAll
    static void init() {
        Bootstrap.register();
    }

    @Test
    void markerAndNativeThrowerMustAgreeWithTheDamageSource() {
        MarkedGolem marked = new MarkedGolem();
        marked.threatworks$observeWizardrySpawn(UUID.randomUUID());
        EntitySnowball verifiedProjectile = snowball(marked);
        Object worldSession = new Object();
        assertNotNull(SnowGolemOrigin.resolve(DamageSource.causeThrownDamage(verifiedProjectile, marked), worldSession));

        EntitySnowman reported = new EntitySnowman(null);
        DamageSource conflict = DamageSource.causeThrownDamage(verifiedProjectile, reported);
        assertTrue(SnowGolemOrigin.handles(conflict));
        assertNull(SnowGolemOrigin.resolve(conflict, worldSession));
        assertNull(SnowGolemOrigin.resolve(DamageSource.causeThrownDamage(snowball(null), reported), worldSession));
        assertNull(SnowGolemOrigin.resolve(DamageSource.causeThrownDamage(snowball(reported), reported), worldSession));
    }

    private static EntitySnowball snowball(EntityLivingBase nativeShooter) {
        return new EntitySnowball(null) {
            @Override
            public EntityLivingBase getThrower() {
                return nativeShooter;
            }
        };
    }

    private static final class MarkedGolem extends EntitySnowman implements SnowGolemOrigin {
        private UUID caster;

        MarkedGolem() {
            super(null);
        }

        @Override
        public UUID threatworks$wizardryCaster() { return caster; }

        @Override
        public void threatworks$observeWizardrySpawn(UUID caster) { this.caster = caster; }
    }
}
