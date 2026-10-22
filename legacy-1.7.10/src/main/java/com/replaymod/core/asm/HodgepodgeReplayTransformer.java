/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Keep Hodgepodge's batched tile descriptions in replay packet order. */
public final class HodgepodgeReplayTransformer implements IClassTransformer {
    private static final String TARGET="com.mitchej123.hodgepodge.hax.TileEntityDescriptionBatcher$BatchedDescriptionHandler";
    private static final String HOOK="com/replaymod/replay/HodgepodgeReplayCompat";
    @Override public byte[] transform(String name,String transformedName,byte[] input) {
        if(input==null || !(TARGET.equals(name)||TARGET.equals(transformedName)))return input;
        ClassNode type=new ClassNode(Opcodes.ASM5);new ClassReader(input).accept(type,0);
        boolean changed=false;
        for(MethodNode method:type.methods)for(AbstractInsnNode instruction:method.instructions.toArray()) {
            if(!(instruction instanceof FieldInsnNode))continue;
            FieldInsnNode field=(FieldInsnNode)instruction;
            if(field.getOpcode()!=Opcodes.GETSTATIC || !"com/mitchej123/hodgepodge/config/SpeedupsConfig".equals(field.owner)
                    || !"asyncBatchedNBTParsing".equals(field.name) || !"Z".equals(field.desc))continue;
            AbstractInsnNode next=instruction.getNext();
            if(next instanceof MethodInsnNode && HOOK.equals(((MethodInsnNode)next).owner))continue;
            method.instructions.insert(instruction,new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"useAsync","(Z)Z",false));
            changed=true;
        }
        if(!changed)return input;
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);type.accept(writer);return writer.toByteArray();
    }
}
