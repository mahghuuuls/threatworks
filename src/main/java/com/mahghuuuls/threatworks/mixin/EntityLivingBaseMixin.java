package com.mahghuuuls.threatworks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mahghuuuls.threatworks.compat.SlimeHostDamage;
import com.mahghuuuls.threatworks.server.DamageAttribution;
import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import com.mahghuuuls.threatworks.server.DragonDamage;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityLivingBase.class)
abstract class EntityLivingBaseMixin {
    @Inject(method = "attackEntityFrom", at = @At("HEAD"))
    private void threatworks$bindHostAttack(DamageSource source, float amount, CallbackInfoReturnable<Boolean> result,
                                          @Share("hostAttack") LocalRef<SlimeHostDamage> attack) {
        attack.set(SlimeHostDamage.claimAttack((EntityLivingBase) (Object) this, source, amount));
    }

    @WrapOperation(method = "attackEntityFrom", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/EntityLivingBase;damageEntity(Lnet/minecraft/util/DamageSource;F)V"))
    private void threatworks$bindHostDamageCall(EntityLivingBase victim, DamageSource source, float amount,
                                                Operation<Void> original,
                                                @Share("hostAttack") LocalRef<SlimeHostDamage> attack) {
        SlimeHostDamage evidence = attack.get();
        if (evidence == null) {
            original.call(victim, source, amount);
        } else {
            evidence.invokeDamage(() -> original.call(victim, source, amount));
        }
    }

    @Inject(method = "damageEntity", at = @At("HEAD"))
    private void threatworks$bindHostDamageEntry(DamageSource source, float amount, CallbackInfo result,
                                                @Share("hostDamage") LocalRef<SlimeHostDamage> damage) {
        damage.set(SlimeHostDamage.claimDamage((EntityLivingBase) (Object) this, source));
    }

    @Redirect(
            method = "damageEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;setHealth(F)V"),
            require = 1
    )
    private void threatworks$observeHealthWrite(EntityLivingBase victim, float newHealth,
                                                DamageSource source, float incomingDamage,
                                                @Share("hostDamage") LocalRef<SlimeHostDamage> damage) {
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
            float after = victim.getHealth();
            SlimeHostDamage evidence = damage.get();
            DamageAttribution hit = evidence == null ? null : evidence.applied(victim, source, before, after);
            EncounterCoordinator.INSTANCE.onHealthWritten(victim, source, incumbent, before, after, hit);
        }
    }
}
