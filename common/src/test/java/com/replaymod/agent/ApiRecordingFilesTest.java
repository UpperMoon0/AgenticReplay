package com.replaymod.agent;

import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ApiRecordingFilesTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void completedRecordingBecomesVisibleInReplayFolder() throws Exception {
        var source = temp.newFolder("recording").toPath().resolve("session.mcpr");
        var destination = temp.newFolder("replays").toPath();
        Files.writeString(source, "completed replay");
        var result = ApiRecordingFiles.finish(source, destination);
        assertEquals(destination.resolve("session.mcpr"), result);
        assertEquals("completed replay", Files.readString(result));
        assertFalse(Files.exists(source));
    }

    @Test public void collidingNamesPreserveBothExistingRecordings() throws Exception {
        var source = temp.newFolder("recording").toPath().resolve("session.mcpr");
        var destination = temp.newFolder("replays").toPath();
        Files.writeString(source, "new");
        Files.writeString(destination.resolve("session.mcpr"), "first");
        Files.writeString(destination.resolve("session.1.mcpr"), "second");
        assertEquals(destination.resolve("session.2.mcpr"), ApiRecordingFiles.finish(source, destination));
        assertEquals("first", Files.readString(destination.resolve("session.mcpr")));
        assertEquals("second", Files.readString(destination.resolve("session.1.mcpr")));
        assertEquals("new", Files.readString(destination.resolve("session.2.mcpr")));
    }

    @Test public void alreadyPublishedReplayKeepsItsNameAndContents() throws Exception {
        var folder = temp.newFolder().toPath();
        var source = Files.writeString(folder.resolve("session.mcpr"), "saved");
        assertEquals(source, ApiRecordingFiles.finish(source, folder));
        assertEquals("saved", Files.readString(source));
    }
}
