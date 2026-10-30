/* SPDX-License-Identifier: GPL-3.0-or-later
 * Based on ReplayMod ff47174fd6e94c051eaf6c0a0f3f4745a6b0e885.
 */
package com.replaymod.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Keep other mods' GL injection targets and observe state after the real call. */
public final class GLStateTrackerTransformer implements IClassTransformer {
    private static final String TRACKER = "com/replaymod/render/hooks/GLStateTracker";

    @Override public byte[] transform(String name, String transformedName, byte[] input) {
        if (input == null) return null;
        if (TRACKER.replace('/', '.').equals(name) || TRACKER.replace('/', '.').equals(transformedName)) return input;
        ClassNode type = new ClassNode(Opcodes.ASM5);
        new ClassReader(input).accept(type, 0);
        boolean changed = false;
        for (MethodNode method : type.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; ) {
                AbstractInsnNode next = instruction.getNext();
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (isGLCall(call) && needsObservation(call) && !alreadyObserved(call)) {
                        method.instructions.insertBefore(call, new InsnNode(Opcodes.DUP));
                        method.instructions.insert(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                TRACKER, "notify_" + call.name, "(I)V", false));
                        changed = true;
                    }
                }
                instruction = next;
            }
        }
        if (!changed) return input;
        // DUP adds a temporary stack slot, including in methods with live values
        // below the GL argument. Frames at existing boundaries remain unchanged.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        type.accept(writer);
        return writer.toByteArray();
    }

    private static boolean isGLCall(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKESTATIC && !call.itf
                && "org/lwjgl/opengl/GL11".equals(call.owner) && "(I)V".equals(call.desc)
                && ("glEnable".equals(call.name) || "glDisable".equals(call.name));
    }

    private static boolean alreadyObserved(MethodInsnNode call) {
        AbstractInsnNode next = call.getNext();
        if (!(next instanceof MethodInsnNode)) return false;
        MethodInsnNode observer = (MethodInsnNode) next;
        return observer.getOpcode() == Opcodes.INVOKESTATIC && TRACKER.equals(observer.owner)
                && ("notify_" + call.name).equals(observer.name) && "(I)V".equals(observer.desc);
    }

    private static boolean needsObservation(MethodInsnNode call) {
        AbstractInsnNode argument = call.getPrevious();
        while (argument != null && argument.getOpcode() < 0) argument = argument.getPrevious();
        Integer constant = null;
        if (argument instanceof IntInsnNode && (argument.getOpcode() == Opcodes.BIPUSH || argument.getOpcode() == Opcodes.SIPUSH))
            constant = ((IntInsnNode) argument).operand;
        else if (argument instanceof LdcInsnNode && ((LdcInsnNode) argument).cst instanceof Integer)
            constant = (Integer) ((LdcInsnNode) argument).cst;
        else if (argument != null && argument.getOpcode() >= Opcodes.ICONST_M1 && argument.getOpcode() <= Opcodes.ICONST_5)
            constant = argument.getOpcode() - Opcodes.ICONST_0;
        // The upstream tracker only observes GL_FOG and GL_TEXTURE_2D. Leave
        // other constant call sequences byte-for-byte intact: NEI's ASM matcher
        // requires the original SIPUSH GL_LIGHTING; glEnable sequence.
        // Dynamic arguments still need the original once-only capture path.
        return constant == null || constant == 2912 || constant == 3553;
    }
}
