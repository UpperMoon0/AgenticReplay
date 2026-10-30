package com.agenticreplay.legacy;
import com.replaymod.core.asm.ReplayMissingEntityTransformer;
import org.junit.Test;
import static org.junit.Assert.*;
public class LegacyMissingEntityTest {
    @Test public void actualBedHandlerGetsOneIdempotentScopedGuard() throws Exception {
        String target="net.minecraft.client.network.NetHandlerPlayClient";byte[] original;
        try(java.io.InputStream in=getClass().getResourceAsStream("/"+target.replace('.','/')+".class")){
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;
            while((n=in.read(b))!=-1)out.write(b,0,n);original=out.toByteArray();
        }
        ReplayMissingEntityTransformer t=new ReplayMissingEntityTransformer();byte[] result=t.transform(target,target,original);
        assertNotSame(original,result);assertSame(result,t.transform(target,target,result));
        assertSame(original,t.transform("other","other",original));assertNull(t.transform(target,target,null));
        org.objectweb.asm.ClassReader reader=new org.objectweb.asm.ClassReader(result);
        reader.accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM5){},org.objectweb.asm.ClassReader.EXPAND_FRAMES);
    }
}
