/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.recording.packet;

import com.replaymod.core.ReplayMod;
import com.replaymod.editor.gui.MarkerProcessor;
import com.replaymod.replaystudio.replay.ReplayFile;
import com.replaymod.replaystudio.replay.ReplayMetaData;
import com.replaymod.replaystudio.us.myles.ViaVersion.api.Pair;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Retain a browser-visible, unprocessed recording if optional cuts/splits fail. */
public final class SafeMarkerProcessor {
    private static final Logger LOG = LogManager.getLogger();
    private SafeMarkerProcessor() {}

    public static List<Pair<Path, ReplayMetaData>> apply(Path path, Consumer<Float> progress) throws IOException {
        // MarkerProcessor can move the raw input before attempting its chunk squash.
        // Preserve a separate copy before calling it, never mutate that copy.
        Path recovery = RawReplayRecovery.preserve(path);
        try {
            List<Pair<Path, ReplayMetaData>> processed = MarkerProcessor.apply(path, progress);
            // Keep the raw copy if cleanup fails; correctness beats space savings.
            try { RawReplayRecovery.discard(recovery); }
            catch (IOException cleanup) { LOG.warn("Unable to remove redundant raw replay copy {}", recovery, cleanup); }
            return processed;
        } catch (IOException | RuntimeException failure) {
            LOG.error("Replay cut/split processing failed; retaining unprocessed replay at {}", recovery, failure);
            try (ReplayFile file = ReplayMod.instance.openReplay(recovery)) {
                // The browser sees the original metadata/markers, not fabricated cut results.
                return Collections.singletonList(new Pair<>(recovery, file.getMetaData()));
            } catch (Exception recoveryError) {
                failure.addSuppressed(recoveryError);
                if (failure instanceof IOException) throw (IOException) failure;
                throw (RuntimeException) failure;
            }
        }
    }
}
