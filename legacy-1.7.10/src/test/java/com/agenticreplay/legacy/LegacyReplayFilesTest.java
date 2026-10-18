package com.agenticreplay.legacy;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class LegacyReplayFilesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void acceptsPlainReplayNames() {
        LegacyReplayFiles.validateName("session.mcpr");
        LegacyReplayFiles.validateName("2026-10-18 cafe take (2).mcpr");
        LegacyReplayFiles.validateName("撮影.mcpr");
    }

    @Test public void rejectsTraversalAbsolutePathsAndInvalidNames() {
        String[] names = {null, "", ".mcpr", "a.zip", "../session.mcpr", "a/../session.mcpr", "/tmp/a.mcpr",
                "C:\\a.mcpr", "a\\b.mcpr", "a:b.mcpr", "a\n.mcpr", "a\r.mcpr", "a\t.mcpr", "a" + (char) 0 + ".mcpr"};
        for (String name : names) {
            try { LegacyReplayFiles.validateName(name); fail("Accepted invalid filename: " + name); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void enforcesNameLengthLimit() {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < 196; i++) name.append('a');
        name.append(".mcpr");
        try { LegacyReplayFiles.validateName(name.toString()); fail("Accepted >200 characters"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void resolvesOnlyExistingRegularFiles() throws Exception {
        Path root = temporary.newFolder("replays").toPath();
        Path replay = Files.createFile(root.resolve("session.mcpr"));
        assertEquals(replay.toRealPath(), LegacyReplayFiles.resolve(root, "session.mcpr", true));
        assertEquals(root.toRealPath().resolve("new.mcpr"), LegacyReplayFiles.resolve(root, "new.mcpr", false));
        try { LegacyReplayFiles.resolve(root, "new.mcpr", true); fail("Accepted missing file"); }
        catch (IllegalArgumentException expected) { }
        Files.createDirectory(root.resolve("directory.mcpr"));
        try { LegacyReplayFiles.resolve(root, "directory.mcpr", true); fail("Accepted directory"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void refusesSymlinkTargetsEvenInsideRoot() throws Exception {
        Path root = temporary.newFolder("linked-replays").toPath();
        Path actual = Files.createFile(root.resolve("actual.mcpr"));
        try { Files.createSymbolicLink(root.resolve("link.mcpr"), actual); }
        catch (UnsupportedOperationException unavailable) { org.junit.Assume.assumeNoException(unavailable); }
        catch (java.nio.file.FileSystemException unavailable) { org.junit.Assume.assumeNoException(unavailable); }
        for (boolean existing : new boolean[] {true, false}) {
            try { LegacyReplayFiles.resolve(root, "link.mcpr", existing); fail("Accepted symbolic link"); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
