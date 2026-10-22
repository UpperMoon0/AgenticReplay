package com.agenticreplay.legacy;

import com.replaymod.recording.packet.OrderedClientTasks;
import com.replaymod.core.asm.RecordingOrderTransformer;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;
import static org.junit.Assert.*;

public class LegacyRecordingOrderTest {
    @Test public void pendingJoinPrecedesMainThreadSyntheticSpawnAndFinalization() {
        boolean[] client = {false};
        List<Runnable> scheduled = new ArrayList<>();
        List<String> saved = new ArrayList<>();
        OrderedClientTasks queue = new OrderedClientTasks(() -> client[0], scheduled::add);
        queue.submit(() -> saved.add("join"));
        queue.submit(() -> saved.add("chunks"));
        assertEquals(1, scheduled.size());
        client[0] = true;
        queue.submit(() -> saved.add("self-spawn"));
        client[0] = false;
        queue.submit(() -> saved.add("last-packet"));
        queue.submit(() -> saved.add("finalize"));
        client[0] = true;
        for (Runnable drain : new ArrayList<>(scheduled)) drain.run();
        assertEquals(Arrays.asList("join", "chunks", "self-spawn", "last-packet", "finalize"), saved);
    }

    @Test public void reentrantSyntheticPacketDoesNotOvertakeAnEarlierPacket() {
        boolean[] client = {false};
        List<Runnable> scheduled = new ArrayList<>();
        List<String> saved = new ArrayList<>();
        OrderedClientTasks queue = new OrderedClientTasks(() -> client[0], scheduled::add);
        queue.submit(() -> { saved.add("join"); queue.submit(() -> saved.add("self-spawn")); });
        queue.submit(() -> saved.add("chunks"));
        client[0] = true;
        scheduled.get(0).run();
        assertEquals(Arrays.asList("join", "chunks", "self-spawn"), saved);
    }

    @Test public void actualPinnedRecorderUsesOrderedListenerAndTransformIsIdempotent() throws Exception {
        String target = "com.replaymod.recording.handler.ConnectionEventHandler";
        byte[] original;
        try (java.io.InputStream in = getClass().getResourceAsStream("/" + target.replace('.', '/') + ".class")) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            original = out.toByteArray();
        }
        RecordingOrderTransformer transformer = new RecordingOrderTransformer();
        byte[] result = transformer.transform(target, target, original);
        assertNotSame(original, result);
        assertSame(result, transformer.transform(target, target, result));
        assertSame(original, transformer.transform("unrelated", "unrelated", original));
        assertNull(transformer.transform(target, target, null));
        ClassNode type = new ClassNode(Opcodes.ASM5);
        new ClassReader(result).accept(type, 0);
        int constructors = 0;
        for (MethodNode method : type.methods) for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if ("<init>".equals(call.name) && call.owner.equals("com/replaymod/recording/packet/OrderedPacketListener")) constructors++;
            }
        }
        assertEquals(1, constructors);
    }
}
