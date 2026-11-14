package com.mahghuuuls.threatworks.server;

import net.minecraft.entity.EntityLivingBase;

/** The target held by the native strafe phase and how that particular run began. */
public interface DragonStrafe {
    EntityLivingBase threatworks$strafeTarget();

    boolean threatworks$isOrdinaryStrafe();

    void threatworks$markOrdinaryStrafe();
}
