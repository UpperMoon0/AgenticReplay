package com.agenticreplay.legacy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Pure, independently testable filesystem boundary for replay RPC arguments. */
final class LegacyReplayFiles {
    private LegacyReplayFiles() {}

    static void validateName(String name) {
        if (name == null || name.length() > 200 || !name.endsWith(".mcpr") || name.equals(".mcpr")
                || name.contains("/") || name.contains("\\") || name.contains(":")) {
            throw new IllegalArgumentException("name must be a plain .mcpr filename");
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isISOControl(name.charAt(i))) throw new IllegalArgumentException("Replay filenames must not contain control characters");
        }
    }

    static Path resolve(Path directory, String name, boolean existing) throws IOException {
        validateName(name);
        Path root = directory.toRealPath();
        Path path = root.resolve(name).normalize();
        if (!root.equals(path.getParent()) || Files.isSymbolicLink(path)) throw new IllegalArgumentException("Replay path is outside the replay directory");
        if (existing && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("Replay not found");
        return path;
    }
}
