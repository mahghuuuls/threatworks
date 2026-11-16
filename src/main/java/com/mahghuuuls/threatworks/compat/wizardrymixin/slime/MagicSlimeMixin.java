package com.mahghuuuls.threatworks.compat.wizardrymixin.slime;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mahghuuuls.threatworks.compat.SlimeHostDamage;
import com.mahghuuuls.threatworks.compat.WizardryLateMixins;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = WizardryLateMixins.SLIME, remap = false)
abstract class MagicSlimeMixin {
    @WrapOperation(method = {"onUpdate()V", "func_70071_h_()V"}, remap = false, require = 1, allow = 1, at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;attackEntityFrom" + WizardryLateMixins.ATTACK),
            @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;func_70097_a" + WizardryLateMixins.ATTACK)
    })
    private boolean threatworks$observeHostAttack(Entity host, DamageSource source, float amount,
                                                  Operation<Boolean> original) {
        EntityLivingBase slime = (EntityLivingBase) (Object) this;
        return SlimeHostDamage.invoke(slime, host, source, amount, ((IEntityOwnable) slime).getOwnerId(),
                () -> original.call(host, source, amount));
    }
}
