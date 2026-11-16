package com.mahghuuuls.threatworks.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WizardryLateMixinsTest {
    @Test
    void optionalCallSitesAreMatchedIndependentlyInThePinnedProvider() throws IOException {
        ClassNode slime = read(WizardryLateMixins.SLIME);
        ClassNode snowGolem = read(WizardryLateMixins.SNOW_GOLEM);
        assertEquals(1, WizardryLateMixins.matchingCalls(slime, true));
        assertEquals(1, WizardryLateMixins.matchingCalls(snowGolem, false));
        assertEquals(0, WizardryLateMixins.matchingCalls(slime, false));
        assertEquals(0, WizardryLateMixins.matchingCalls(snowGolem, true));

        slime.methods.removeIf(method -> method.name.equals("onUpdate") || method.name.equals("func_70071_h_"));
        assertEquals(0, WizardryLateMixins.matchingCalls(slime, true));
        assertEquals(1, WizardryLateMixins.matchingCalls(snowGolem, false));
    }

    private static ClassNode read(String name) throws IOException {
        String path = name.replace('.', '/') + ".class";
        try (InputStream stream = WizardryLateMixinsTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream);
            ClassNode type = new ClassNode();
            new ClassReader(stream).accept(type, 0);
            return type;
        }
    }
}
