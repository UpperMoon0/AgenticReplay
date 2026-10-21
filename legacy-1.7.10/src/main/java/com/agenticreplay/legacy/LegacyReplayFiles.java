package com.agenticreplay.legacy;

import com.replaymod.agent.ApiPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** The legacy engine's .mcpr/existing-file policy over the shared path boundary. */
final class LegacyReplayFiles {
    private LegacyReplayFiles() {}
    static void validateName(String name) { ApiPaths.validateName(name, ".mcpr"); }
    static Path resolve(Path directory, String name, boolean existing) throws IOException {
        Path path = ApiPaths.resolve(directory, name, ".mcpr");
        if (existing && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("Replay not found");
        return path;
    }
}
