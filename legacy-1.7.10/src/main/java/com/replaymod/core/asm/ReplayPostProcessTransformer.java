/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Wrap only the pinned optional marker processing call; preserve raw packets. */
public final class ReplayPostProcessTransformer implements IClassTransformer {
    private static final String TARGET = "com.replaymod.recording.packet.PacketListener";
    private static final String ORIGINAL = "com/replaymod/editor/gui/MarkerProcessor";
    private static final String SAFE = "com/replaymod/recording/packet/SafeMarkerProcessor";
    private static final String DESC = "(Ljava/nio/file/Path;Ljava/util/function/Consumer;)Ljava/util/List;";

    @Override public byte[] transform(String name, String transformedName, byte[] input) {
        if (input == null || !(TARGET.equals(name) || TARGET.equals(transformedName))) return input;
        ClassNode type = new ClassNode(Opcodes.ASM5);
        new ClassReader(input).accept(type, 0);
        int matches = 0;
        for (MethodNode method : type.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == Opcodes.INVOKESTATIC && "apply".equals(call.name)
                        && DESC.equals(call.desc) && SAFE.equals(call.owner)) return input;
                if (call.getOpcode() == Opcodes.INVOKESTATIC && "apply".equals(call.name)
                        && DESC.equals(call.desc) && ORIGINAL.equals(call.owner)) {
                    call.owner = SAFE;
                    matches++;
                }
            }
        }
        if (matches != 1) throw new IllegalStateException("Pinned replay marker processor call changed: " + matches);
        ClassWriter writer = new ClassWriter(0);
        type.accept(writer);
        return writer.toByteArray();
    }
}
