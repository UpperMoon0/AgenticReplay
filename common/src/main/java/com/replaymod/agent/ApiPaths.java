package com.replaymod.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Deliberately only accepts file names, never remote-supplied filesystem paths. */
public final class ApiPaths {
    private ApiPaths() {}
    public static Path resolve(Path root, String name, String extension) throws IOException {
        if (name == null || name.isBlank() || name.length() > 200 ||
            name.contains("/") || name.contains("\\") || name.contains(":") ||
            name.equals(".") || name.equals("..") || !name.endsWith(extension)) {
            throw new IllegalArgumentException("Expected a plain file name ending in " + extension);
        }
        Files.createDirectories(root);
        Path base = root.toRealPath();
        Path result = base.resolve(name);
        if (Files.isSymbolicLink(result) || (Files.exists(result) && !result.toRealPath().getParent().equals(base))) {
            throw new IllegalArgumentException("Links outside the replay directory are forbidden");
        }
        return result;
    }
}
