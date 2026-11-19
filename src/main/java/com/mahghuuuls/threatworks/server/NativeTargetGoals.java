package com.mahghuuuls.threatworks.server;

import java.util.UUID;

/** Identifies the original native target goals on a live mob. */
public interface NativeTargetGoals {
    boolean threatworks$hasOriginalTargetGoals();

    interface Nearest {
        Class<?> threatworks$targetClass();
    }

    interface CachedTarget {
        void threatworks$forgetTarget(UUID playerId);
    }
}
