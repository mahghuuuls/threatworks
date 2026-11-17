package com.mahghuuuls.threatworks.api;

import net.minecraft.entity.EntityLivingBase;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Read-only views of one participant on the logical server thread. */
public interface ThreatQueries {
    /** Reads two live entities in the same loaded server world without running target selection. */
    View query(EntityLivingBase mob, EntityLivingBase participant);

    enum State {
        SERVER_WORLD_UNAVAILABLE, WRONG_THREAD, MOB_UNAVAILABLE, INVALID_PARTICIPANT,
        UNSUPPORTED, SUPPORT_UNKNOWN, INACTIVE, TRACKED
    }

    enum Confirmation {
        YES, NO, UNKNOWN
    }

    enum Focus {
        ORDINARY, WITHER_CENTER, DRAGON_STRAFE, GUARDIAN_BEAM, UNKNOWN
    }

    /** Snapshot with no references to live game entities or internal collections. */
    final class View {
        private final State state;
        private final Confirmation ordinarySwitching;
        private final Double threat;
        private final Confirmation eligibility;
        private final Focus focus;
        private final Confirmation aggro;
        private final Confirmation tauntActive;
        private final Confirmation tauntForcingFocus;
        private final Integer tauntTicks;
        private final Takeover takeover;

        public View(State state, Confirmation ordinarySwitching, Double threat, Confirmation eligibility,
                    Focus focus, Confirmation aggro, Confirmation tauntActive, Confirmation tauntForcingFocus,
                    Integer tauntTicks, Takeover takeover) {
            this.state = state;
            this.ordinarySwitching = ordinarySwitching;
            this.threat = threat;
            this.eligibility = eligibility;
            this.focus = focus;
            this.aggro = aggro;
            this.tauntActive = tauntActive;
            this.tauntForcingFocus = tauntForcingFocus;
            this.tauntTicks = tauntTicks;
            this.takeover = takeover;
        }

        /** INACTIVE recognizes a vanilla type without an active encounter. TRACKED means an encounter exists. */
        public State state() { return state; }

        /** Whether ordinary selection governs the current combat path. */
        public Confirmation ordinarySwitching() { return ordinarySwitching; }

        /** A live participant with no credited points in a tracked encounter has known zero threat. */
        public OptionalDouble threat() { return threat == null ? OptionalDouble.empty() : OptionalDouble.of(threat); }

        /** The latest usable eligibility observation, including incumbent sight grace. */
        public Confirmation eligibility() { return eligibility; }

        public Focus focus() { return focus; }

        /** Whether this participant has aggro on the named focus path. */
        public Confirmation aggro() { return aggro; }

        /** Encounter-level state, regardless of who applied the taunt. */
        public Confirmation tauntActive() { return tauntActive; }

        public Confirmation tauntForcingFocus() { return tauntForcingFocus; }

        public OptionalInt tauntTicks() { return tauntTicks == null ? OptionalInt.empty() : OptionalInt.of(tauntTicks); }

        /** A reliable ordinary takeover score observed within the last 25 server ticks, when available. */
        public Optional<Takeover> takeover() { return Optional.ofNullable(takeover); }
    }

    /** A score boundary for the queried participant at the observed ordinary decision inputs. */
    final class Takeover {
        private final double score;
        private final boolean equalitySuffices;
        private final int observationTick;
        private final double observedThreat;

        public Takeover(double score, boolean equalitySuffices, int observationTick, double observedThreat) {
            this.score = score;
            this.equalitySuffices = equalitySuffices;
            this.observationTick = observationTick;
            this.observedThreat = observedThreat;
        }

        public double score() { return score; }

        public boolean equalitySuffices() { return equalitySuffices; }

        public int observationTick() { return observationTick; }

        public double observedThreat() { return observedThreat; }
    }
}
