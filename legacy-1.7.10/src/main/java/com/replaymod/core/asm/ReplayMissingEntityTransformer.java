/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Keep vanilla/live behavior; only skip an impossible bed action in historical replay state. */
public final class ReplayMissingEntityTransformer implements IClassTransformer {
    private static final String TARGET="net.minecraft.client.network.NetHandlerPlayClient";
    private static final String HOOK="com/replaymod/replay/ReplayMissingEntityCompat";
    @Override public byte[] transform(String name,String transformedName,byte[] input) {
        if(input==null||!(TARGET.equals(name)||TARGET.equals(transformedName)))return input;
        ClassNode type=new ClassNode(Opcodes.ASM5);new ClassReader(input).accept(type,0);
        for(MethodNode method:type.methods) {
            String mapped=cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(type.name,method.name,method.desc);
            if(!(method.name.equals("handleUseBed")||mapped.equals("func_147278_a")))continue;
            for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode && HOOK.equals(((MethodInsnNode)i).owner))return input;
            LabelNode vanilla=new LabelNode();InsnList guard=new InsnList();
            guard.add(new VarInsnNode(Opcodes.ALOAD,1));
            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"skipBed","(Ljava/lang/Object;)Z",false));
            guard.add(new JumpInsnNode(Opcodes.IFEQ,vanilla));guard.add(new InsnNode(Opcodes.RETURN));guard.add(vanilla);
            // The added branch has the unchanged method-entry locals and an empty stack.
            guard.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));method.instructions.insert(guard);
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);type.accept(writer);return writer.toByteArray();
        }
        throw new IllegalStateException("Pinned bed packet handler missing");
    }
}
