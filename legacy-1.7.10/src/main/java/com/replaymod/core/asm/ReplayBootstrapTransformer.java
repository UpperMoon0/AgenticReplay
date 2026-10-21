/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Select the maintained sender for both ordinary playback and offline export. */
public final class ReplayBootstrapTransformer implements IClassTransformer {
    private static final String TARGET = "com.replaymod.replay.ReplayHandler";
    private static final String ORIGINAL = "com/replaymod/replay/FullReplaySender";
    private static final String ORDERED = "com/replaymod/replay/BootstrapReplaySender";

    @Override public byte[] transform(String name, String transformedName, byte[] input) {
        if (input == null || !(TARGET.equals(name) || TARGET.equals(transformedName))) return input;
        ClassNode type = new ClassNode(Opcodes.ASM5);
        new ClassReader(input).accept(type, 0);
        int allocations = 0, constructors = 0;
        for (MethodNode method : type.methods) for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn.getOpcode() == Opcodes.NEW && insn instanceof TypeInsnNode && ORIGINAL.equals(((TypeInsnNode) insn).desc)) {
                ((TypeInsnNode) insn).desc = ORDERED; allocations++;
            } else if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() == Opcodes.INVOKESPECIAL && ORIGINAL.equals(call.owner) && "<init>".equals(call.name)) {
                    call.owner = ORDERED; constructors++;
                }
            }
        }
        if (allocations == 0 && constructors == 0) return input;
        if (allocations != 1 || constructors != 1) throw new IllegalStateException("Pinned replay sender constructor changed");
        ClassWriter writer = new ClassWriter(0); type.accept(writer); return writer.toByteArray();
    }
}
