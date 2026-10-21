package com.agenticreplay.legacy;
import com.replaymod.render.hooks.AngelicaShaderClock;
import com.replaymod.core.asm.AngelicaShaderClockTransformer;
import org.objectweb.asm.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class LegacyShaderClockTest {
 @Test public void animationPhaseDependsOnOutputTimeAcrossFrameRates() {
  assertEquals(0,AngelicaShaderClock.counter(1,60),0);
  assertEquals(2,AngelicaShaderClock.counter(121,60),0);
  assertEquals(AngelicaShaderClock.counter(121,60),AngelicaShaderClock.counter(61,30),0);
  assertEquals(0,AngelicaShaderClock.counter(216001,60),0);
 }
 @Test public void clockGuardIsIdempotentAndLeavesOtherClassesAlone() {
  String target="net.coderbot.iris.uniforms.SystemTimeUniforms$Timer";
  ClassWriter writer=new ClassWriter(0);writer.visit(52,Opcodes.ACC_PUBLIC,target.replace('.','/'),null,"java/lang/Object",null);
  MethodVisitor m=writer.visitMethod(Opcodes.ACC_PUBLIC,"beginFrame","(J)V",null,null);m.visitCode();m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,3);m.visitEnd();writer.visitEnd();
  byte[] original=writer.toByteArray();AngelicaShaderClockTransformer t=new AngelicaShaderClockTransformer();byte[] changed=t.transform(target,target,original);
  assertNotSame(original,changed);assertSame(changed,t.transform(target,target,changed));assertSame(original,t.transform("other","other",original));
 }
}
