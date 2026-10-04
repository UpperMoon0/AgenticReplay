package com.replaymod.agent;
import org.junit.Test;
import java.nio.file.*;
import static org.junit.Assert.*;

public class ApiPathsTest {
    @Test public void allowsFileNames() throws Exception {
        Path root = Files.createTempDirectory("agent-replay-test");
        try { assertEquals(root.toRealPath().resolve("shot.mcpr"), ApiPaths.resolve(root, "shot.mcpr", ".mcpr")); }
        finally { Files.delete(root); }
    }
    @Test public void rejectsTraversalAndDriveNames() throws Exception {
        Path root = Files.createTempDirectory("agent-replay-test");
        try {
            for (String name : new String[]{"../shot.mcpr", "..\\shot.mcpr", "C:shot.mcpr", "/shot.mcpr", "shot.txt", ""}) {
                try { ApiPaths.resolve(root, name, ".mcpr"); fail("Accepted " + name); }
                catch (IllegalArgumentException expected) {}
            }
        } finally { Files.delete(root); }
    }
    @Test public void rejectsExternalLink() throws Exception {
        Path root = Files.createTempDirectory("agent-replay-test"), outside = Files.createTempFile("outside", ".mcpr");
        Path link = root.resolve("shot.mcpr");
        try {
            try { Files.createSymbolicLink(link, outside); }
            catch (UnsupportedOperationException | java.io.IOException ignored) { return; }
            try { ApiPaths.resolve(root, "shot.mcpr", ".mcpr"); fail("Accepted external link"); }
            catch (IllegalArgumentException expected) {}
        } finally { Files.deleteIfExists(link); Files.delete(root); Files.delete(outside); }
    }
}
