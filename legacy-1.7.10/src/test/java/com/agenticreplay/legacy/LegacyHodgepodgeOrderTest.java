package com.agenticreplay.legacy;

import com.replaymod.core.asm.HodgepodgeReplayTransformer;
import com.replaymod.replay.HodgepodgeReplayCompat;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class LegacyHodgepodgeOrderTest {
    private static final String TARGET="com.mitchej123.hodgepodge.hax.TileEntityDescriptionBatcher$BatchedDescriptionHandler";
    @Test public void liveParsingPreferenceIsPreservedButReplayCannotReorderDescriptions() {
        assertTrue(HodgepodgeReplayCompat.useAsync(true,false));
        assertFalse(HodgepodgeReplayCompat.useAsync(false,false));
        assertFalse(HodgepodgeReplayCompat.useAsync(true,true));
        assertFalse(HodgepodgeReplayCompat.useAsync(false,true));
    }
    @Test public void guardFollowsOnlyTheExactHodgepodgeSettingAndIsIdempotent() {
        ClassWriter writer=new ClassWriter(0);
        writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,TARGET.replace('.','/'),null,"java/lang/Object",null);
        MethodVisitor method=writer.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"configured","()Z",null,null);
        method.visitCode();method.visitFieldInsn(Opcodes.GETSTATIC,"com/mitchej123/hodgepodge/config/SpeedupsConfig","asyncBatchedNBTParsing","Z");
        method.visitInsn(Opcodes.IRETURN);method.visitMaxs(1,0);method.visitEnd();writer.visitEnd();
        byte[] original=writer.toByteArray();HodgepodgeReplayTransformer transformer=new HodgepodgeReplayTransformer();
        assertSame(original,transformer.transform("another.Class","another.Class",original));
        assertNull(transformer.transform(TARGET,TARGET,null));
        byte[] changed=transformer.transform(TARGET,TARGET,original);assertNotSame(original,changed);
        ClassNode type=new ClassNode();new ClassReader(changed).accept(type,0);
        AbstractInsnNode hook=type.methods.get(0).instructions.getFirst().getNext();
        assertTrue(hook instanceof MethodInsnNode);
        assertEquals("com/replaymod/replay/HodgepodgeReplayCompat",((MethodInsnNode)hook).owner);
        assertEquals("(Z)Z",((MethodInsnNode)hook).desc);
        assertSame(changed,transformer.transform(TARGET,TARGET,changed));
    }
}
