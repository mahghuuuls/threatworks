package com.mahghuuuls.threatworks.command;

import net.minecraft.command.ICommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreatworksCommandTest {
    @Test
    void productionCommandUsesTheOperatorPermissionBoundary() {
        ThreatworksCommand command = new ThreatworksCommand();

        assertFalse(command.checkPermission(null, senderWithLevel(0)));
        assertFalse(command.checkPermission(null, senderWithLevel(1)));
        assertTrue(command.checkPermission(null, senderWithLevel(2)));
    }

    private static ICommandSender senderWithLevel(int level) {
        return (ICommandSender) Proxy.newProxyInstance(
                ICommandSender.class.getClassLoader(),
                new Class<?>[]{ICommandSender.class},
                (sender, method, args) -> {
                    if ("canUseCommand".equals(method.getName())) {
                        return (Integer) args[0] <= level && "threatworks".equals(args[1]);
                    }
                    throw new AssertionError("Unexpected command sender call: " + method.getName());
                }
        );
    }
}
