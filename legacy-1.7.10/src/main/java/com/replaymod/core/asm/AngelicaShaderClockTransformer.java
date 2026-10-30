/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class AngelicaShaderClockTransformer implements IClassTransformer {
    private static final String TARGET="net.coderbot.iris.uniforms.SystemTimeUniforms$Timer";
    private static final String HOOK="com/replaymod/render/hooks/AngelicaShaderClock";
    @Override public byte[] transform(String name,String transformedName,byte[] input) {
        if(input==null||!(TARGET.equals(name)||TARGET.equals(transformedName)))return input;
        ClassNode type=new ClassNode(Opcodes.ASM5);new ClassReader(input).accept(type,0);
        for(MethodNode method:type.methods) {
            if(!"beginFrame".equals(method.name)||!"(J)V".equals(method.desc))continue;
            for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode&&HOOK.equals(((MethodInsnNode)i).owner))return input;
            LabelNode live=new LabelNode();InsnList guard=new InsnList();
            guard.add(new VarInsnNode(Opcodes.ALOAD,0));guard.add(new VarInsnNode(Opcodes.LLOAD,1));
            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"update","(Ljava/lang/Object;J)Z",false));
            guard.add(new JumpInsnNode(Opcodes.IFEQ,live));guard.add(new InsnNode(Opcodes.RETURN));guard.add(live);
            guard.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));method.instructions.insert(guard);
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);type.accept(writer);return writer.toByteArray();
        }
        return input;
    }
}
