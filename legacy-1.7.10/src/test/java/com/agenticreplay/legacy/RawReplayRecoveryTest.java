package com.agenticreplay.legacy;

import com.replaymod.recording.packet.RawReplayRecovery;
import com.replaymod.core.asm.ReplayPostProcessTransformer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.Assert.*;

public class RawReplayRecoveryTest {
    @Test public void preservesIndependentRawCopyAndNeverOverwritesPreviousRecovery() throws Exception {
        Path dir = Files.createTempDirectory("replay-recovery-");
        try {
            Path source = dir.resolve("capture.mcpr");
            Files.write(source, new byte[] {1,2,3,4});
            Path first = RawReplayRecovery.preserve(source);
            Files.write(source, new byte[] {8,9});
            Path second = RawReplayRecovery.preserve(source);
            assertArrayEquals(new byte[]{1,2,3,4}, Files.readAllBytes(first));
            assertArrayEquals(new byte[]{8,9}, Files.readAllBytes(second));
            assertNotEquals(first, second);
            RawReplayRecovery.discard(first);
            assertTrue(Files.exists(second));
            assertArrayEquals(new byte[]{8,9}, Files.readAllBytes(source));
        } finally {
            try (java.util.stream.Stream<Path> remaining = Files.list(dir)) {
                remaining.forEach(path -> { try { Files.delete(path); } catch (Exception e) { throw new RuntimeException(e); } });
            }
            Files.delete(dir);
        }
    }

    @Test public void pinnedRecorderPostProcessorCallIsWrappedExactlyOnce() throws Exception {
        String target = "com.replaymod.recording.packet.PacketListener";
        byte[] original;
        try (java.io.InputStream in = getClass().getResourceAsStream("/" + target.replace('.', '/') + ".class")) {
            assertNotNull(in);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            original = out.toByteArray();
        }
        ReplayPostProcessTransformer transformer = new ReplayPostProcessTransformer();
        byte[] patched = transformer.transform(target,target,original);
        assertNotSame(original,patched);
        assertSame(patched, transformer.transform(target,target,patched));
        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(patched).accept(node,0);
        int wrapped = 0;
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if ("com/replaymod/recording/packet/SafeMarkerProcessor".equals(call.owner)
                    && "apply".equals(call.name)) wrapped++;
        }
        assertEquals(1,wrapped);
    }
}
