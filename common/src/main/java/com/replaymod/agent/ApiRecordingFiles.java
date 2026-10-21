package com.replaymod.agent;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Publish a completely saved recording without a rename dialog or overwriting an existing replay. */
public final class ApiRecordingFiles {
    private ApiRecordingFiles() { }

    public static Path finish(Path source, Path replayFolder) throws IOException {
        Path destination = replayFolder.resolve(source.getFileName());
        if (source.toAbsolutePath().normalize().equals(destination.toAbsolutePath().normalize())) return source;
        String filename = source.getFileName().toString();
        String base = filename.endsWith(".mcpr") ? filename.substring(0, filename.length() - 5) : filename;
        for (int suffix = 0; ; suffix++) {
            Path candidate = suffix == 0 ? destination : replayFolder.resolve(base + "." + suffix + ".mcpr");
            try { return Files.move(source, candidate); }
            catch (FileAlreadyExistsException collision) { /* Preserve the other recording and try a new name. */ }
        }
    }
}
