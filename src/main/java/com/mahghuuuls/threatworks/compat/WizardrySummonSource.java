package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.server.DamageAttribution;
import electroblob.wizardry.entity.living.EntitySkeletonMinion;
import electroblob.wizardry.entity.living.EntitySpiritWolf;
import electroblob.wizardry.entity.living.EntityZombieMinion;
import electroblob.wizardry.entity.living.ISummonedCreature;
import electroblob.wizardry.entity.projectile.EntityMagicArrow;
import electroblob.wizardry.util.IndirectMinionDamage;
import electroblob.wizardry.util.MinionDamage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.UUID;
import java.util.function.Function;

final class WizardrySummonSource implements DamageAttribution.SummonSource {
    private final Class<?> directWrapper = MinionDamage.class.asSubclass(EntityDamageSource.class);
    private final Class<?> indirectWrapper = IndirectMinionDamage.class.asSubclass(EntityDamageSourceIndirect.class);
    private final Class<?> summonedCreature = ISummonedCreature.class.asSubclass(IEntityOwnable.class);
    private final Class<?> spiritWolf = EntitySpiritWolf.class.asSubclass(EntityTameable.class);
    private final Class<?> zombie = EntityZombieMinion.class.asSubclass(EntityLivingBase.class);
    private final Class<?> skeleton = EntitySkeletonMinion.class.asSubclass(EntityLivingBase.class);
    private final Field originalShooter;
    private final Function<Entity, EntityLivingBase> magicArrowCaster;

    WizardrySummonSource(Field originalShooter, Function<Entity, EntityLivingBase> magicArrowCaster) {
        this.originalShooter = originalShooter;
        this.magicArrowCaster = magicArrowCaster;
    }

    static WizardrySummonSource load(Logger logger) {
        Field shooter = null;
        try {
            shooter = IndirectMinionDamage.class.getDeclaredField("minion");
            if (shooter.getType() != Entity.class || Modifier.isStatic(shooter.getModifiers())) {
                throw new NoSuchFieldException("Unexpected Wizardry indirect minion source field");
            }
            shooter.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            logger.warn("Wizardry wrapped projectile attribution unavailable: original minion field is inaccessible",
                    failure);
            shooter = null;
        }
        return new WizardrySummonSource(shooter, loadMagicArrowCaster(logger));
    }

    private static Function<Entity, EntityLivingBase> loadMagicArrowCaster(Logger logger) {
        try {
            Class<EntityMagicArrow> magicArrow = EntityMagicArrow.class;
            Function<EntityMagicArrow, EntityLivingBase> getCaster = EntityMagicArrow::getCaster;
            return projectile -> magicArrow.isInstance(projectile) ? getCaster.apply(magicArrow.cast(projectile)) : null;
        } catch (LinkageError | RuntimeException failure) {
            logger.warn("Wizardry Magic Arrow caster lookup unavailable", failure);
            return projectile -> null;
        }
    }

    @Override
    public boolean handles(DamageSource source) {
        if (directWrapper.isInstance(source) || indirectWrapper.isInstance(source) || SnowGolemOrigin.handles(source)) {
            return true;
        }
        Entity actor = source.getTrueSource();
        if (verifiedOwnerId(actor) != null) {
            return true;
        }
        // Preserve the baseline admission of the original zombie and skeleton paths.
        Entity immediate = source.getImmediateSource();
        return zombie.isInstance(actor) || skeleton.isInstance(actor)
                || immediate instanceof EntityArrow
                && skeleton.isInstance(((EntityArrow) immediate).shootingEntity);
    }

    @Override
    public DamageAttribution resolve(DamageSource source, Object worldSession) {
        if (SnowGolemOrigin.handles(source)) {
            return SnowGolemOrigin.resolve(source, worldSession);
        }
        Entity direct = source.getImmediateSource();
        Entity reported = source.getTrueSource();
        if (directWrapper.isInstance(source)) {
            return verifiedOwner(direct, reported)
                    ? DamageAttribution.summonDirect((EntityLivingBase) direct, (EntityLivingBase) reported)
                    : null;
        }
        if (indirectWrapper.isInstance(source)) {
            Entity actor = originalShooter(source);
            return verifiedOwner(actor, reported)
                    ? indirect((EntityLivingBase) actor, (EntityLivingBase) reported, direct, worldSession)
                    : null;
        }

        UUID ownerId = verifiedOwnerId(reported);
        if (ownerId == null) {
            return legacyUnownedHit(source, reported, direct, worldSession);
        }
        if (!(source instanceof EntityDamageSource)) {
            return null;
        }
        EntityLivingBase actor = (EntityLivingBase) reported;
        EntityLivingBase caster = WizardryCompatibility.liveCaster(actor, ownerId);
        if (source instanceof EntityDamageSourceIndirect) {
            return indirect(actor, caster, direct, worldSession);
        }
        return direct == actor ? DamageAttribution.summonDirect(actor, caster) : null;
    }

    private DamageAttribution legacyUnownedHit(DamageSource source, Entity actor, Entity immediate,
                                              Object worldSession) {
        if (!WizardryCompatibility.UNSET_CASTER.equals(ownerId(actor))) {
            return null;
        }
        // Keep the two released raw-source cases for saved minions with an empty caster tag.
        EntityLivingBase minion = (EntityLivingBase) actor;
        if (actor.getClass() == zombie && immediate == actor && source.getClass() == EntityDamageSource.class
                && "mob".equals(source.getDamageType()) && !source.isProjectile()) {
            return DamageAttribution.summonDirect(minion, null);
        }
        if (actor.getClass() == skeleton && immediate instanceof EntityArrow
                && source.getClass() == EntityDamageSourceIndirect.class
                && "arrow".equals(source.getDamageType()) && source.isProjectile()) {
            return indirect(minion, null, immediate, worldSession);
        }
        return null;
    }

    private DamageAttribution indirect(EntityLivingBase actor, EntityLivingBase caster, Entity projectile,
                                       Object worldSession) {
        if (projectile == null || projectile instanceof EntityLivingBase || projectile.world != actor.world) {
            return null;
        }
        // A second Wizardry rewrap can put an intermediate caster in the minion field.
        if (projectileShooter(projectile) != actor) {
            return null;
        }
        return DamageAttribution.summonIndirect(actor, caster, projectile, worldSession);
    }

    private Entity projectileShooter(Entity projectile) {
        if (projectile instanceof EntityArrow) {
            return ((EntityArrow) projectile).shootingEntity;
        }
        if (projectile instanceof EntityFireball) {
            return ((EntityFireball) projectile).shootingEntity;
        }
        if (projectile instanceof EntityThrowable) {
            return ((EntityThrowable) projectile).getThrower();
        }
        return magicArrowCaster.apply(projectile);
    }

    private Entity originalShooter(DamageSource source) {
        if (originalShooter == null) {
            return null;
        }
        try {
            return (Entity) originalShooter.get(source);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private UUID ownerId(Entity actor) {
        if (!(actor instanceof EntityLivingBase)) {
            return null;
        }
        if (summonedCreature.isInstance(actor)) {
            return ((IEntityOwnable) actor).getOwnerId();
        }
        if (spiritWolf.isInstance(actor) && ((EntityTameable) actor).isTamed()) {
            return ((EntityTameable) actor).getOwnerId();
        }
        return null;
    }

    private boolean verifiedOwner(Entity actor, Entity owner) {
        return actor != null && owner instanceof EntityLivingBase && actor != owner && actor.world == owner.world
                && owner.getUniqueID().equals(verifiedOwnerId(actor));
    }

    private UUID verifiedOwnerId(Entity actor) {
        UUID id = ownerId(actor);
        return WizardryCompatibility.hasCaster(id) ? id : null;
    }
}
