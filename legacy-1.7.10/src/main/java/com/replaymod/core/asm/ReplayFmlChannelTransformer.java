/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.replaymod.core.asm;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
public class ReplayFmlChannelTransformer implements IClassTransformer {
 private static final String TARGET="cpw.mods.fml.common.network.handshake.NetworkDispatcher",HOOK="com/replaymod/replay/ReplayFmlCompat";
 public byte[] transform(String a,String b,byte[] input){
  if(input==null||!(TARGET.equals(a)||TARGET.equals(b)))return input;
  ClassNode c=new ClassNode(Opcodes.ASM5);new ClassReader(input).accept(c,0);
  for(MethodNode m:c.methods)if(m.name.equals("rejectHandshake")&&m.desc.equals("(Ljava/lang/String;)V")){
   for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode&&HOOK.equals(((MethodInsnNode)i).owner))return input;
   LabelNode normal=new LabelNode();InsnList l=new InsnList();l.add(new VarInsnNode(Opcodes.ALOAD,1));l.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"keepReplayOpen","(Ljava/lang/String;)Z",false));l.add(new JumpInsnNode(Opcodes.IFEQ,normal));l.add(new InsnNode(Opcodes.RETURN));l.add(normal);l.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));m.instructions.insert(l);
   ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);return w.toByteArray();
  }throw new IllegalStateException("Pinned Forge replay dispatcher hook missing");
 }
}
