/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Keep Angelica's own blocking build/upload path active during offline export. */
public final class AngelicaReplayTransformer implements IClassTransformer {
    private static final String TARGET = "com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer";
    private static final String DESC = "(Lorg/embeddedt/embeddium/impl/render/viewport/Viewport;Lorg/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer$CameraState;IZZ)V";
    private static final String HOOK = "com/replaymod/render/hooks/AngelicaReplayCompat";

    @Override public byte[] transform(String name, String transformedName, byte[] input) {
        if (input == null || !(TARGET.equals(name) || TARGET.equals(transformedName))) return input;
        ClassNode type = new ClassNode(Opcodes.ASM5);
        new ClassReader(input).accept(type, 0);
        for (MethodNode method : type.methods) {
            if (!"setupTerrain".equals(method.name) || !DESC.equals(method.desc)) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode && HOOK.equals(((MethodInsnNode) instruction).owner)) return input;
            }
            InsnList before = new InsnList();
            before.add(new VarInsnNode(Opcodes.ILOAD, 5));
            before.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "forceImmediateTerrain", "(Z)Z", false));
            before.add(new VarInsnNode(Opcodes.ISTORE, 5));
            method.instructions.insert(before);
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() != Opcodes.RETURN) continue;
                InsnList after = new InsnList();
                after.add(new VarInsnNode(Opcodes.ALOAD, 0));
                after.add(new VarInsnNode(Opcodes.ALOAD, 1));
                after.add(new VarInsnNode(Opcodes.ALOAD, 2));
                after.add(new VarInsnNode(Opcodes.ILOAD, 3));
                after.add(new VarInsnNode(Opcodes.ILOAD, 4));
                after.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "finishTerrain",
                        "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;IZ)V", false));
                method.instructions.insertBefore(instruction, after);
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            type.accept(writer);
            return writer.toByteArray();
        }
        // Other Angelica versions do not inherit a claim of compatibility.
        return input;
    }
}
