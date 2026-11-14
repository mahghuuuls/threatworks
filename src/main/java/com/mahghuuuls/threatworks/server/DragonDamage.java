package com.mahghuuuls.threatworks.server;

import net.minecraft.util.DamageSource;

/** Receives the shared health sample while a Dragon multipart damage call is in progress. */
public interface DragonDamage {
    void threatworks$recordHealthWrite(DamageSource source, float before, float after);
}
