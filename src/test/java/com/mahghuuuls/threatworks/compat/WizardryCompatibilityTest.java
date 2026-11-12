package com.mahghuuuls.threatworks.compat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WizardryCompatibilityTest {
    @Test
    void adapterRequiresTheMinimumVersionAndAvailableCapabilities() {
        Logger logger = LogManager.getLogger(getClass());
        assertNull(WizardryCompatibility.load(null, logger));
        assertNull(WizardryCompatibility.load("4.3.18", logger));
        assertNotNull(WizardryCompatibility.load("4.3.20", logger));
        assertNotNull(WizardryCompatibilityTestAccess.qualified());
    }
}
