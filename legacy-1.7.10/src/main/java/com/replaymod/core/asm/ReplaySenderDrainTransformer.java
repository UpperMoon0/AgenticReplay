/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.core.asm;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Deliver synchronous packets before camera paths apply their final position. */
public final class ReplaySenderDrainTransformer implements IClassTransformer {
 private static final String TARGET="com.replaymod.replay.FullReplaySender",HOOK="com/replaymod/replay/ReplayPacketPump";
 @Override public byte[] transform(String name,String transformedName,byte[] input) {
  if(input==null||!(TARGET.equals(name)||TARGET.equals(transformedName)))return input;
  ClassNode type=new ClassNode(Opcodes.ASM5);new ClassReader(input).accept(type,0);
  for(MethodNode method:type.methods)if("sendPacketsTill".equals(method.name)&&"(I)V".equals(method.desc)) {
   for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode&&HOOK.equals(((MethodInsnNode)i).owner))return input;
   for(AbstractInsnNode i:method.instructions.toArray())if(i.getOpcode()==Opcodes.RETURN)
    method.instructions.insertBefore(i,new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"drain","()V",false));
   ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);type.accept(writer);return writer.toByteArray();
  }
  throw new IllegalStateException("Pinned synchronous replay sender missing");
 }
}
