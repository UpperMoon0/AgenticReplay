/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.recording.packet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Independent raw replay copy before the optional marker processor can move it. */
public final class RawReplayRecovery {
    private RawReplayRecovery() {}

    public static Path preserve(Path rawReplay) throws IOException {
        Path parent = rawReplay.toAbsolutePath().getParent();
        if (parent == null) throw new IOException("Replay has no destination directory: " + rawReplay);
        String name = rawReplay.getFileName().toString();
        String stem = name.endsWith(".mcpr") ? name.substring(0, name.length() - 5) : name;
        for (int i = 0; i < 10000; i++) {
            String suffix = i == 0 ? "" : "_" + i;
            Path output = parent.resolve("Recovered_" + stem + "_unprocessed" + suffix + ".mcpr");
            try {
                // Do not use hard links: postprocessing may mutate or move the source.
                Files.copy(rawReplay, output);
                return output;
            } catch (java.nio.file.FileAlreadyExistsException duplicate) {
                // Never overwrite an existing recording, including a previous recovery.
            }
        }
        throw new IOException("Cannot allocate an unused recovery replay path");
    }

    public static void discard(Path recovery) throws IOException {
        Files.delete(recovery);
    }
}
