package com.mahghuuuls.threatworks.config;

import net.minecraftforge.fml.relauncher.FMLInjectionData;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

public final class TuningFiles {
    private TuningFiles() {
    }

    public static ServerTuning load(Path file, String input, Predicate<String> registeredItem, List<String> warnings)
            throws Exception {
        if (input != null) {
            Files.write(file, ("server {\n" + input + "\n}\n").getBytes(StandardCharsets.UTF_8));
        }
        // Forge's file-backed constructor expects the launcher to have supplied a game directory.
        Field home = FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        synchronized (FMLInjectionData.class) {
            Object previous = home.get(null);
            home.set(null, file.getParent().toFile());
            try {
                return ServerTuning.load(file.toFile(), registeredItem, warnings::add);
            } finally {
                home.set(null, previous);
            }
        }
    }
}
