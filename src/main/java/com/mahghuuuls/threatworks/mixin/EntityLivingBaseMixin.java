package com.mahghuuuls.threatworks.mixin;

import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityLivingBase.class)
abstract class EntityLivingBaseMixin {
    @Redirect(
            method = "damageEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;setHealth(F)V"),
            require = 1
    )
    private void threatworks$observeHealthWrite(EntityLivingBase victim, float newHealth,
                                                DamageSource source, float incomingDamage) {
        boolean supported = EncounterCoordinator.supports(victim);
        EntityLivingBase incumbent = supported ? ((EntityLiving) victim).getAttackTarget() : null;
        float before = supported ? victim.getHealth() : 0.0F;
        victim.setHealth(newHealth);
        if (supported) {
            EncounterCoordinator.INSTANCE.onHealthWritten(victim, source, incumbent, before, victim.getHealth());
        }
    }
}
