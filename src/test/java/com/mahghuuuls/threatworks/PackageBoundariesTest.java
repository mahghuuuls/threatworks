package com.mahghuuuls.threatworks;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageBoundariesTest {
    @Test
    void importsKeepTheRulesPureAndOptionalAndClientTypesOutOfCommonCode() throws IOException {
        Path root = Paths.get(System.getProperty("threatworks.sourceDirectory"), "com", "mahghuuuls", "threatworks");
        List<Path> sources;
        try (Stream<Path> paths = Files.walk(root)) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList());
        }
        assertFalse(sources.isEmpty());
        for (Path source : sources) {
            Path relative = root.relativize(source);
            String area = relative.getName(0).toString();
            for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
                String declaration = line.trim();
                if (!declaration.startsWith("import ")) {
                    continue;
                }
                String name = declaration.substring("import ".length()).replaceFirst("^static ", "");
                assertFalse(name.startsWith("net.minecraft.client.") || name.startsWith("net.minecraftforge.client."),
                        relative + ": " + line);
                if ("threat".equals(area)) {
                    assertTrue(name.startsWith("java.") || name.startsWith("com.mahghuuuls.threatworks.threat."),
                            relative + ": " + line);
                }
                if (!"compat".equals(area)) {
                    assertFalse(name.startsWith("baubles.") || name.startsWith("electroblob.")
                                    || name.startsWith("com.tmtravlr.qualitytools.")
                                    || name.startsWith("com.mahghuuuls.jafs.")
                                    || name.startsWith("com.mahghuuuls.setworks."),
                            relative + ": " + line);
                }
            }
        }
    }
}
