package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.server.DamageAttribution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Correlates Wizardry's anonymous host hit with one native damage invocation. */
public final class SlimeHostDamage {
    private static final ThreadLocal<SlimeHostDamage> ATTACK = new ThreadLocal<>();
    private static final ThreadLocal<SlimeHostDamage> DAMAGE = new ThreadLocal<>();
    private static final ClassValue<Boolean> TRUSTED_DISPATCH = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> victimType) {
            try {
                Class<?> attack = declaration(victimType, "attackEntityFrom", "func_70097_a",
                        DamageSource.class, float.class);
                if (attack != EntityLivingBase.class && attack != EntityMob.class && attack != EntityZombie.class) {
                    return false;
                }
                // These native overrides delegate directly, with EntityMob checking invulnerability first.
                return declaration(victimType, "damageEntity", "func_70665_d", DamageSource.class, float.class)
                        == EntityLivingBase.class
                        && declaration(victimType, "setHealth", "func_70606_j", float.class) == EntityLivingBase.class
                        && declaration(victimType, "isEntityInvulnerable", "func_180431_b", DamageSource.class)
                        == Entity.class;
            } catch (LinkageError | RuntimeException failure) {
                return false;
            }
        }
    };

    private final EntityLivingBase slime;
    private final Entity victim;
    private final UUID caster;
    private boolean credited;

    private SlimeHostDamage(EntityLivingBase slime, Entity victim, UUID caster) {
        this.slime = slime;
        this.victim = victim;
        this.caster = caster;
    }

    public static boolean invoke(EntityLivingBase slime, Entity victim, DamageSource source, float amount,
                                 UUID caster, BooleanSupplier attack) {
        if (source != DamageSource.MAGIC || amount != 1.0F || !WizardryCompatibility.hasCaster(caster)
                || !(slime.world instanceof WorldServer) || victim.world != slime.world
                || !((WorldServer) slime.world).getMinecraftServer().isCallingFromMinecraftThread()
                || slime.getRidingEntity() != victim || !slime.isEntityAlive()
                || !hasTrustedDispatch(victim.getClass())) {
            return attack.getAsBoolean();
        }
        return scopeAttack(slime, victim, caster, attack);
    }

    static boolean hasTrustedDispatch(Class<?> victimType) {
        return TRUSTED_DISPATCH.get(victimType);
    }

    static boolean scopeAttack(EntityLivingBase slime, Entity victim, UUID caster, BooleanSupplier attack) {
        SlimeHostDamage previous = ATTACK.get();
        ATTACK.set(new SlimeHostDamage(slime, victim, caster));
        try {
            return attack.getAsBoolean();
        } finally {
            restore(ATTACK, previous);
        }
    }

    public static SlimeHostDamage claimAttack(EntityLivingBase target, DamageSource source, float amount) {
        SlimeHostDamage evidence = ATTACK.get();
        if (evidence != null) {
            ATTACK.remove();
        }
        return evidence != null && evidence.matches(target, source) && amount == 1.0F ? evidence : null;
    }

    public void invokeDamage(Runnable damage) {
        SlimeHostDamage previous = DAMAGE.get();
        DAMAGE.set(this);
        try {
            damage.run();
        } finally {
            restore(DAMAGE, previous);
        }
    }

    public static SlimeHostDamage claimDamage(EntityLivingBase target, DamageSource source) {
        SlimeHostDamage evidence = DAMAGE.get();
        if (evidence != null) {
            DAMAGE.remove();
        }
        return evidence != null && evidence.matches(target, source) ? evidence : null;
    }

    public DamageAttribution applied(EntityLivingBase target, DamageSource source, float before, float after) {
        if (credited || !(after < before) || !matches(target, source) || slime.world != target.world) {
            return null;
        }
        credited = true;
        return DamageAttribution.summonDirect(slime, WizardryCompatibility.liveCaster(slime, caster));
    }

    private boolean matches(EntityLivingBase target, DamageSource source) {
        return victim == target && source == DamageSource.MAGIC;
    }

    private static void restore(ThreadLocal<SlimeHostDamage> slot, SlimeHostDamage previous) {
        if (previous == null) {
            slot.remove();
        } else {
            slot.set(previous);
        }
    }

    private static Class<?> declaration(Class<?> type, String mcp, String srg, Class<?>... args) {
        for (Class<?> candidate = type; candidate != null; candidate = candidate.getSuperclass()) {
            for (String name : new String[] {mcp, srg}) {
                try {
                    return candidate.getDeclaredMethod(name, args).getDeclaringClass();
                } catch (NoSuchMethodException ignored) {
                    // Continue with the other runtime name and then its superclass.
                }
            }
        }
        return null;
    }
}
