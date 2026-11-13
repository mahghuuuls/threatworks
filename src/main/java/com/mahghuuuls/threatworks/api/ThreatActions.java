package com.mahghuuuls.threatworks.api;

import net.minecraft.entity.EntityLivingBase;

/** Server-thread operations for integrations that own their own costs and cooldowns. */
public interface ThreatActions {
    /** Adds finite positive threat to one participant in an existing encounter. */
    Result grant(EntityLivingBase mob, EntityLivingBase participant, double amount);

    /** Takes aggro immediately for the default duration. An eligible player can open an encounter. */
    Result taunt(EntityLivingBase mob, EntityLivingBase participant);

    enum Status {
        APPLIED, UNAVAILABLE, REJECTED
    }

    enum Result {
        APPLIED(Status.APPLIED),
        SERVER_WORLD_UNAVAILABLE(Status.UNAVAILABLE),
        WRONG_THREAD(Status.REJECTED),
        UNSUPPORTED_MOB(Status.REJECTED),
        MOB_UNAVAILABLE(Status.REJECTED),
        INVALID_PARTICIPANT(Status.REJECTED),
        UNTRACKED(Status.REJECTED),
        INVALID_AMOUNT(Status.REJECTED),
        SCORE_LIMIT(Status.REJECTED),
        INELIGIBLE_TAUNTER(Status.REJECTED),
        TARGET_WRITE_IN_PROGRESS(Status.REJECTED),
        TARGET_ASSIGNMENT_REJECTED(Status.REJECTED);

        private final Status status;

        Result(Status status) {
            this.status = status;
        }

        public Status status() {
            return status;
        }
    }
}
