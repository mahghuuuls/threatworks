package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.threatworks.server.DamageAttribution;
import org.apache.logging.log4j.LogManager;

public final class WizardryCompatibilityTestAccess {
    private WizardryCompatibilityTestAccess() {}

    public static DamageAttribution.SummonSource qualified() {
        return WizardryCompatibility.load("4.3.19", LogManager.getLogger(WizardryCompatibilityTestAccess.class));
    }

    public static DamageAttribution.SummonSource withoutOriginalShooterField() {
        return new WizardrySummonSource(null, projectile -> null);
    }
}
