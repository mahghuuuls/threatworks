package com.mahghuuuls.threatworks.api;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * Posted on the server for an applied summon hit in a tracked encounter when its owner is
 * a valid participant. The source is supplied for inspection and must not be changed.
 */
public final class SummonThreatCreditEvent extends Event {
    private final EntityLivingBase target;
    private final EntityLivingBase summon;
    private final EntityLivingBase owner;
    private final DamageSource source;
    private boolean ownerCreditRequested;

    public SummonThreatCreditEvent(EntityLivingBase target, EntityLivingBase summon,
                                  EntityLivingBase owner, DamageSource source) {
        this.target = target;
        this.summon = summon;
        this.owner = owner;
        this.source = source;
    }

    public EntityLivingBase getTarget() { return target; }
    public EntityLivingBase getSummon() { return summon; }
    public EntityLivingBase getOwner() { return owner; }
    public DamageSource getSource() { return source; }

    /** Requests credit for the verified owner of this hit instead of the summon. */
    public void creditOwner() { ownerCreditRequested = true; }

    public boolean isOwnerCreditRequested() { return ownerCreditRequested; }
}
