package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.server.DamageAttribution;
import electroblob.wizardry.entity.living.EntitySkeletonMinion;
import electroblob.wizardry.entity.living.EntityZombieMinion;
import electroblob.wizardry.entity.living.ISummonedCreature;
import electroblob.wizardry.util.IndirectMinionDamage;
import electroblob.wizardry.util.MinionDamage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.UUID;
import java.util.function.Function;

final class WizardrySummonSource implements DamageAttribution.SummonSource {
    private final Class<?> melee = MinionDamage.class.asSubclass(EntityDamageSource.class);
    private final Class<?> ranged = IndirectMinionDamage.class.asSubclass(EntityDamageSourceIndirect.class);
    private final Class<?> zombie = EntityZombieMinion.class.asSubclass(EntityLivingBase.class);
    private final Class<?> skeleton = EntitySkeletonMinion.class.asSubclass(EntityLivingBase.class);
    private final Function<ISummonedCreature, UUID> ownerId = ISummonedCreature::getOwnerId;
    private final Field originalShooter;

    WizardrySummonSource() throws ReflectiveOperationException {
        zombie.asSubclass(ISummonedCreature.class);
        skeleton.asSubclass(ISummonedCreature.class);
        // The wrapper's public true source is the caster; it keeps the original attacker here.
        originalShooter = ranged.getDeclaredField("minion");
        if (originalShooter.getType() != Entity.class || Modifier.isStatic(originalShooter.getModifiers())) {
            throw new NoSuchFieldException("Unexpected Wizardry indirect minion source field");
        }
        originalShooter.setAccessible(true);
    }

    @Override
    public boolean handles(DamageSource source) {
        Entity immediate = source.getImmediateSource();
        return melee.isInstance(source) || ranged.isInstance(source)
                || zombie.isInstance(source.getTrueSource()) || skeleton.isInstance(source.getTrueSource())
                || (immediate instanceof EntityArrow && skeleton.isInstance(((EntityArrow) immediate).shootingEntity));
    }

    @Override
    public DamageAttribution resolve(DamageSource source, Object worldSession) {
        Entity immediate = source.getImmediateSource();
        Entity trueSource = source.getTrueSource();
        if ("mob".equals(source.getDamageType())
                && !source.isProjectile() && immediate != null && immediate.getClass() == zombie) {
            EntityLivingBase minion = (EntityLivingBase) immediate;
            if (source.getClass() == melee && trueSource instanceof EntityLivingBase && ownedBy(minion, trueSource)) {
                return DamageAttribution.summonMelee(minion, (EntityLivingBase) trueSource);
            }
            // Wizardry leaves the vanilla source intact when the caster is not in the minion's world.
            if (source.getClass() == EntityDamageSource.class && trueSource == minion && hasOwner(minion)) {
                return DamageAttribution.summonMelee(minion, null);
            }
            return null;
        }
        if (!"arrow".equals(source.getDamageType()) || !source.isProjectile() || !(immediate instanceof EntityArrow)) {
            return null;
        }
        EntityArrow arrow = (EntityArrow) immediate;
        Entity shooter = arrow.shootingEntity;
        if (shooter == null || shooter.getClass() != skeleton) {
            return null;
        }
        EntityLivingBase minion = (EntityLivingBase) shooter;
        if (source.getClass() == EntityDamageSourceIndirect.class && trueSource == minion && hasOwner(minion)) {
            return DamageAttribution.summonArrow(minion, null, arrow, worldSession);
        }
        if (source.getClass() == ranged && trueSource instanceof EntityLivingBase && ownedBy(minion, trueSource)
                && matchesOriginalShooter(source, minion)) {
            return DamageAttribution.summonArrow(minion, (EntityLivingBase) trueSource, arrow, worldSession);
        }
        return null;
    }

    private boolean matchesOriginalShooter(DamageSource source, Entity shooter) {
        try {
            return originalShooter.get(source) == shooter;
        } catch (IllegalAccessException ignored) {
            return false;
        }
    }

    private boolean hasOwner(Entity summon) {
        return ownerId.apply((ISummonedCreature) summon) != null;
    }

    private boolean ownedBy(Entity summon, Entity owner) {
        return summon != owner && summon.world == owner.world
                && owner.getUniqueID().equals(ownerId.apply((ISummonedCreature) summon));
    }
}
