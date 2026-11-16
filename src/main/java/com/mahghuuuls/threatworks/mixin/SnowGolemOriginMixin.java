package com.mahghuuuls.threatworks.mixin;

import com.mahghuuuls.threatworks.compat.SnowGolemOrigin;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

@Mixin(EntitySnowman.class)
abstract class SnowGolemOriginMixin extends EntityGolem implements SnowGolemOrigin {
    @Unique private UUID threatworks$wizardryCaster;

    SnowGolemOriginMixin(World world) {
        super(world);
    }

    @Override
    public UUID threatworks$wizardryCaster() {
        return threatworks$wizardryCaster;
    }

    @Override
    public void threatworks$observeWizardrySpawn(UUID caster) {
        threatworks$wizardryCaster = caster;
    }

    @Override
    public void onRemovedFromWorld() {
        threatworks$wizardryCaster = null;
        super.onRemovedFromWorld();
    }
}
