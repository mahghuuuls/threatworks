package com.mahghuuuls.threatworks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mahghuuuls.threatworks.server.DragonStrafe;
import com.mahghuuuls.threatworks.server.EncounterCoordinator;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.dragon.phase.IPhase;
import net.minecraft.entity.boss.dragon.phase.PhaseBase;
import net.minecraft.entity.boss.dragon.phase.PhaseHoldingPattern;
import net.minecraft.entity.boss.dragon.phase.PhaseList;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PhaseHoldingPattern.class)
abstract class PhaseHoldingPatternMixin extends PhaseBase {
    protected PhaseHoldingPatternMixin(EntityDragon dragon) {
        super(dragon);
    }

    @WrapOperation(method = "findNewTarget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/boss/dragon/phase/PhaseHoldingPattern;strafePlayer(Lnet/minecraft/entity/player/EntityPlayer;)V"),
            require = 1)
    private void threatworks$chooseOrdinaryStrafe(PhaseHoldingPattern phase, EntityPlayer vanilla,
                                                  Operation<Void> original) {
        EncounterCoordinator coordinator = EncounterCoordinator.INSTANCE;
        EntityPlayer target = coordinator.chooseDragonStrafe(dragon, vanilla);
        // An eligibility callback can end the fight or change phase during selection.
        if (dragon.isDead || dragon.getPhaseManager().getCurrentPhase() != phase) {
            return;
        }
        original.call(phase, target);
        IPhase current = dragon.getPhaseManager().getCurrentPhase();
        if (current.getType() == PhaseList.STRAFE_PLAYER) {
            DragonStrafe strafe = (DragonStrafe) current;
            strafe.threatworks$markOrdinaryStrafe();
            coordinator.confirmDragonStrafe(dragon, strafe.threatworks$strafeTarget());
        }
    }
}
