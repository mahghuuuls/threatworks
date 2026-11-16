package com.mahghuuuls.threatworks.compat.wizardrymixin.snowgolem;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mahghuuuls.threatworks.compat.SnowGolemOrigin;
import com.mahghuuuls.threatworks.compat.WizardryLateMixins;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = WizardryLateMixins.SNOW_GOLEM, remap = false)
abstract class SummonSnowGolemMixin {
    @WrapOperation(method = WizardryLateMixins.CAST, remap = false, require = 1, allow = 1, at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity" + WizardryLateMixins.SPAWN),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/World;func_72838_d" + WizardryLateMixins.SPAWN)
    })
    private boolean threatworks$observeSpawn(World world, Entity entity, Operation<Boolean> original,
                                             @Local(argsOnly = true) EntityPlayer caster) {
        boolean wasAdded = entity.isAddedToWorld();
        boolean spawned = original.call(world, entity);
        if (spawned && !wasAdded) {
            SnowGolemOrigin.observe(world, entity, caster);
        }
        return spawned;
    }
}
