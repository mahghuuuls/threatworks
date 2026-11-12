package com.mahghuuuls.threatworks.compat;

import com.mahghuuuls.jafs.api.PropertyOperation;
import com.mahghuuuls.jafs.api.PropertySpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JafsCompatibilityTest {
    private static final Logger LOGGER = LogManager.getLogger(JafsCompatibilityTest.class);

    @Test
    void absentOldAndUnboundParentsDoNotInstallAReader() {
        org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger) LOGGER;
        List<String> messages = new ArrayList<>();
        AbstractAppender capture = new AbstractAppender("jafs-test", null, null) {
            @Override
            public void append(LogEvent event) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        };
        capture.start();
        logger.addAppender(capture);
        try {
            assertNull(JafsCompatibility.load(null, logger));
            assertTrue(messages.isEmpty());
            assertNull(JafsCompatibility.load("1.1.0", logger));
            assertTrue(messages.get(0).contains("requires 1.2.0 or newer"));
            assertNull(JafsCompatibility.load("1.2.0", logger));
            assertTrue(messages.get(1).contains("could not register"));
            assertEquals(2, messages.size());
        } finally {
            logger.removeAppender(capture);
            capture.stop();
        }
    }

    @Test
    void registersAFlatPropertyWithSignedValues() {
        PropertySpec property = JafsFoodPoints.property();
        assertEquals("threatworks:threat_points", property.id());
        assertEquals(1, property.operations().size());
        PropertySpec.Range flat = property.range(PropertyOperation.FLAT);
        assertTrue(flat.contains(-100));
        assertTrue(flat.contains(100));
    }
}
