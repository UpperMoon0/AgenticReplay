package com.replaymod.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared filename and symlink boundary; accepts names, never caller-supplied paths. */
public final class ApiPaths {
    private ApiPaths() {}
    public static void validateName(String name, String extension) {
        if (name == null || name.length() > 200 || name.length() <= extension.length()
                || !name.endsWith(extension) || name.contains("/") || name.contains("\\") || name.contains(":"))
            throw new IllegalArgumentException("Expected a plain file name ending in " + extension);
        for (int i = 0; i < name.length(); i++) {
            if (Character.isISOControl(name.charAt(i))) throw new IllegalArgumentException("File names must not contain control characters");
        }
    }
    public static Path resolve(Path root, String name, String extension) throws IOException {
        validateName(name, extension);
        Files.createDirectories(root);
        Path base = root.toRealPath();
        Path result = base.resolve(name).normalize();
        if (!base.equals(result.getParent()) || Files.isSymbolicLink(result)
                || (Files.exists(result) && !result.toRealPath().getParent().equals(base)))
            throw new IllegalArgumentException("Links outside the replay directory are forbidden");
        return result;
    }
}
