package com.mahghuuuls.threatworks.compat;

import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.DamageSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlimeHostDamageTest {
    @BeforeAll
    static void init() {
        Bootstrap.register();
    }

    @Test
    void unknownDispatchAndHealthOverridesCannotBindTheAnonymousSource() {
        assertTrue(SlimeHostDamage.hasTrustedDispatch(EntityZombie.class));
        EntityZombie overridingEntry = new EntityZombie(null) {
            @Override
            public boolean attackEntityFrom(DamageSource source, float amount) {
                return super.attackEntityFrom(source, amount);
            }
        };
        EntityZombie overridingHealth = new EntityZombie(null) {
            @Override
            public void setHealth(float health) {
                super.setHealth(health);
            }
        };
        assertFalse(SlimeHostDamage.hasTrustedDispatch(overridingEntry.getClass()));
        assertFalse(SlimeHostDamage.hasTrustedDispatch(overridingHealth.getClass()));
    }

    @Test
    void nestedEntriesCannotBorrowTheClaimedAttackOrDamageInvocation() {
        EntityZombie host = new EntityZombie(null);
        EntitySlime slime = new EntitySlime(null);
        SlimeHostDamage.scopeAttack(slime, host, UUID.randomUUID(), () -> {
            SlimeHostDamage attack = SlimeHostDamage.claimAttack(host, DamageSource.MAGIC, 1);
            assertNotNull(attack);
            assertNull(SlimeHostDamage.claimAttack(host, DamageSource.MAGIC, 1));
            attack.invokeDamage(() -> {
                SlimeHostDamage write = SlimeHostDamage.claimDamage(host, DamageSource.MAGIC);
                assertSame(attack, write);
                assertNull(SlimeHostDamage.claimDamage(host, DamageSource.MAGIC));
                assertNull(write.applied(host, DamageSource.MAGIC, 10, 10));
                assertNotNull(write.applied(host, DamageSource.MAGIC, 10, 9));
                assertNull(write.applied(host, DamageSource.MAGIC, 9, 8));
            });
            return true;
        });
        assertNull(SlimeHostDamage.claimAttack(host, DamageSource.MAGIC, 1));
        assertNull(SlimeHostDamage.claimDamage(host, DamageSource.MAGIC));
    }

    @Test
    void scopesCleanUpEvenWhenTheOriginalCallThrowsBeforeEnteringDamage() {
        EntityZombie host = new EntityZombie(null);
        EntityZombie other = new EntityZombie(null);
        EntitySlime slime = new EntitySlime(null);
        UUID caster = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> SlimeHostDamage.scopeAttack(slime, host, caster, () -> {
            throw new IllegalStateException();
        }));
        assertNull(SlimeHostDamage.claimAttack(host, DamageSource.MAGIC, 1));

        SlimeHostDamage.scopeAttack(slime, host, caster, () -> {
            SlimeHostDamage attack = SlimeHostDamage.claimAttack(host, DamageSource.MAGIC, 1);
            assertNotNull(attack);
            assertThrows(IllegalStateException.class, () -> attack.invokeDamage(() -> {
                throw new IllegalStateException();
            }));
            assertNull(SlimeHostDamage.claimDamage(host, DamageSource.MAGIC));
            return true;
        });
        SlimeHostDamage.scopeAttack(slime, host, caster, () -> {
            assertNull(SlimeHostDamage.claimAttack(other, DamageSource.MAGIC, 1));
            assertNull(SlimeHostDamage.claimAttack(host, DamageSource.MAGIC, 1));
            return false;
        });
    }
}
