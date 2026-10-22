package com.agenticreplay.legacy;
import org.junit.Test;import static org.junit.Assert.*;
import net.minecraft.launchwrapper.IClassTransformer;
import com.replaymod.core.asm.ReplaySenderDrainTransformer;
import com.replaymod.core.asm.ReplayFmlChannelTransformer;
public class LegacyDeliveryHooksTest {
 private void verify(String target,IClassTransformer transformer)throws Exception {
  byte[] original;
  try(java.io.InputStream in=getClass().getResourceAsStream("/"+target.replace('.','/')+".class")){
   assertNotNull(in);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;
   while((n=in.read(b))!=-1)out.write(b,0,n);original=out.toByteArray();
  }
  byte[] changed=transformer.transform(target,target,original);assertNotSame(original,changed);
  assertSame(changed,transformer.transform(target,target,changed));
  assertSame(original,transformer.transform("other","other",original));assertNull(transformer.transform(target,target,null));
 }
 @Test public void synchronousSenderDeliversBeforeReturningToTheCameraPath()throws Exception {
  verify("com.replaymod.replay.FullReplaySender",new ReplaySenderDrainTransformer());
 }
 @Test public void actualForgeDispatcherContainsTheReplayOnlyGuard()throws Exception {
  verify("cpw.mods.fml.common.network.handshake.NetworkDispatcher",new ReplayFmlChannelTransformer());
 }
}
