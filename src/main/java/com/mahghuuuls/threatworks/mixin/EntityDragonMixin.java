package com.mahghuuuls.threatworks.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mahghuuuls.threatworks.server.DragonDamage;
import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MultiPartEntityPart;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityDragon.class)
abstract class EntityDragonMixin implements DragonDamage {
    @Unique private Hit threatworks$hit;

    @WrapMethod(method = "attackEntityFromPart")
    private boolean threatworks$observeMultipartHit(MultiPartEntityPart part, DamageSource source, float amount,
                                                   Operation<Boolean> original) {
        EntityDragon dragon = (EntityDragon) (Object) this;
        Hit previous = threatworks$hit;
        EncounterCoordinator coordinator = EncounterCoordinator.INSTANCE;
        Hit hit = EncounterCoordinator.supports(dragon)
                ? new Hit(source, coordinator.dragonOpeningTarget(dragon)) : null;
        threatworks$hit = hit;
        try {
            boolean accepted = original.call(part, source, amount);
            if (accepted && hit != null) {
                coordinator.finishDragonHit(dragon, source, hit.incumbent, hit.before, hit.after);
            }
            return accepted;
        } finally {
            threatworks$hit = previous;
        }
    }

    @Override
    public void threatworks$recordHealthWrite(DamageSource source, float before, float after) {
        Hit hit = threatworks$hit;
        if (hit != null && hit.source == source && !hit.sampled) {
            hit.before = before;
            hit.after = after;
            hit.sampled = true;
        }
    }

    private static final class Hit {
        final DamageSource source;
        final EntityLivingBase incumbent;
        float before;
        float after;
        boolean sampled;

        Hit(DamageSource source, EntityLivingBase incumbent) {
            this.source = source;
            this.incumbent = incumbent;
        }
    }
}
