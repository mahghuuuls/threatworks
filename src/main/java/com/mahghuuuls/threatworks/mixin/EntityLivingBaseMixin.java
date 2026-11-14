package com.mahghuuuls.threatworks.mixin;

import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import com.mahghuuuls.threatworks.server.DragonDamage;
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
        if (victim instanceof DragonDamage) {
            float before = victim.getHealth();
            victim.setHealth(newHealth);
            ((DragonDamage) victim).threatworks$recordHealthWrite(source, before, victim.getHealth());
            return;
        }
        boolean supported = EncounterCoordinator.supports(victim);
        EntityLivingBase incumbent = supported ? ((EntityLiving) victim).getAttackTarget() : null;
        float before = supported ? victim.getHealth() : 0.0F;
        victim.setHealth(newHealth);
        if (supported) {
            EncounterCoordinator.INSTANCE.onHealthWritten(victim, source, incumbent, before, victim.getHealth());
        }
    }
}
