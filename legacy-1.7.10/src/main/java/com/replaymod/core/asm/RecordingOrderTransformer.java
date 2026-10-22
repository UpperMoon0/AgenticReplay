/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Select the source-built listener without replacing unrelated engine code. */
public final class RecordingOrderTransformer implements IClassTransformer {
    private static final String TARGET = "com.replaymod.recording.handler.ConnectionEventHandler";
    private static final String ORIGINAL = "com/replaymod/recording/packet/PacketListener";
    private static final String ORDERED = "com/replaymod/recording/packet/OrderedPacketListener";

    @Override public byte[] transform(String name, String transformedName, byte[] input) {
        if (input == null || !(TARGET.equals(name) || TARGET.equals(transformedName))) return input;
        ClassNode type = new ClassNode(Opcodes.ASM5);
        new ClassReader(input).accept(type, 0);
        int allocations = 0, constructors = 0;
        for (MethodNode method : type.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() == Opcodes.NEW && instruction instanceof TypeInsnNode) {
                    TypeInsnNode allocation = (TypeInsnNode) instruction;
                    if (ORIGINAL.equals(allocation.desc)) { allocation.desc = ORDERED; allocations++; }
                } else if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.getOpcode() == Opcodes.INVOKESPECIAL && ORIGINAL.equals(call.owner)
                            && "<init>".equals(call.name)) { call.owner = ORDERED; constructors++; }
                }
            }
        }
        if (allocations == 0 && constructors == 0) return input;
        if (allocations != 1 || constructors != 1)
            throw new IllegalStateException("Pinned recorder constructor changed");
        ClassWriter writer = new ClassWriter(0);
        type.accept(writer);
        return writer.toByteArray();
    }
}
