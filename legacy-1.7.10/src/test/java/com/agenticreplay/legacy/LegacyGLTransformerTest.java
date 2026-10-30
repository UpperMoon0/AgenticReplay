package com.agenticreplay.legacy;

import com.replaymod.core.asm.GLStateTrackerTransformer;
import com.replaymod.render.hooks.FogStateCallback;
import com.replaymod.render.hooks.GLStateTracker;
import com.replaymod.render.hooks.Texture2DStateCallback;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistration;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import java.util.*;
import static org.junit.Assert.*;

public class LegacyGLTransformerTest implements Opcodes {
    private static final String GL = "org/lwjgl/opengl/GL11";
    private static final String TRACKER = "com/replaymod/render/hooks/GLStateTracker";
    private static final List<String> EVENTS = new ArrayList<>();
    public static void record(int capability) { EVENTS.add("gl:" + capability); }
    public static int nextCapability() { EVENTS.add("argument"); return 2912; }

    @Test public void evaluatesDynamicArgumentsOnlyOnceAndStillTracksFog() throws Exception {
        EVENTS.clear();
        EventRegistration<FogStateCallback> fog = EventRegistration.register(FogStateCallback.EVENT,
                enabled -> EVENTS.add("fog:" + enabled));
        try {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(V1_8, ACC_PUBLIC, "DynamicFixture", null, "java/lang/Object", null);
            MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "render", "()J", null, null);
            method.visitCode(); method.visitLdcInsn(7L);
            method.visitMethodInsn(INVOKESTATIC, getClass().getName().replace('.', '/'), "nextCapability", "()I", false);
            method.visitMethodInsn(INVOKESTATIC, GL, "glEnable", "(I)V", false);
            method.visitInsn(LRETURN); method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd();
            byte[] transformed = new GLStateTrackerTransformer().transform("DynamicFixture", "DynamicFixture", writer.toByteArray());
            FixtureLoader loader = new FixtureLoader();
            loader.define("org.lwjgl.opengl.GL11", glStub());
            assertEquals(7L, loader.define("DynamicFixture", transformed).getMethod("render").invoke(null));
            assertEquals(Arrays.asList("argument", "gl:2912", "fog:true"), EVENTS);
        } finally { fog.unregister(); }
    }

    @Test public void preservesRequiredInjectionTargetsAndRecomputesMaxStack() throws Exception {
        byte[] original = fixture("Fixture", false);
        byte[] transformed = new GLStateTrackerTransformer().transform("Fixture", "Fixture", original);
        ClassNode type = new ClassNode(ASM5);
        new ClassReader(transformed).accept(type, 0);
        MethodNode method = type.methods.get(0);
        assertEquals(4, method.maxStack); // live long (2) + capability + duplicated capability
        List<String> calls = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                calls.add(call.owner + "." + call.name + call.desc);
            }
        }
        assertEquals(Arrays.asList(GL + ".glEnable(I)V", TRACKER + ".notify_glEnable(I)V",
                GL + ".glDisable(I)V", TRACKER + ".notify_glDisable(I)V"), calls);
        new Analyzer<>(new BasicVerifier()).analyze(type.name, method);
        assertSame(transformed, new GLStateTrackerTransformer().transform("Fixture", "Fixture", transformed));
    }

    @Test public void executesGLExactlyOnceThenRealTrackerCallbacks() throws Exception {
        EVENTS.clear();
        EventRegistration<FogStateCallback> fog = EventRegistration.register(FogStateCallback.EVENT,
                enabled -> EVENTS.add("fog:" + enabled));
        EventRegistration<Texture2DStateCallback> texture = EventRegistration.register(Texture2DStateCallback.EVENT,
                (slot, enabled) -> EVENTS.add("texture:" + slot + ":" + enabled));
        try {
            GLStateTracker.getInstance().updateActiveTexture(33987); // GL_TEXTURE0 + 3
            byte[] code = new GLStateTrackerTransformer().transform("Fixture", "Fixture", fixture("Fixture", false));
            FixtureLoader loader = new FixtureLoader();
            loader.define("org.lwjgl.opengl.GL11", glStub());
            Class<?> type = loader.define("Fixture", code);
            assertEquals(7L, type.getMethod("renderOverlays").invoke(null));
            assertEquals(Arrays.asList("gl:2912", "fog:true", "gl:3553", "texture:3:false"), EVENTS);
        } finally { texture.unregister(); fog.unregister(); }
    }

    @Test public void ignoresNullTrackerAndUnrelatedCalls() {
        GLStateTrackerTransformer transformer = new GLStateTrackerTransformer();
        assertNull(transformer.transform(null, null, null));
        byte[] tracker = fixture(TRACKER, false);
        assertSame(tracker, transformer.transform(TRACKER.replace('/', '.'), null, tracker));
        assertSame(tracker, transformer.transform(null, TRACKER.replace('/', '.'), tracker));
        byte[] unrelated = fixture("Other", true);
        assertSame(unrelated, transformer.transform(null, null, unrelated));
    }

    @Test public void preservesNEIsUntrackedLightingInstructionSequence() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC, "NEIFixture", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "drawScreen", "()V", null, null);
        method.visitCode();
        method.visitMethodInsn(INVOKESTATIC, "FixtureHooks", "drawForeground", "()V", false);
        method.visitIntInsn(SIPUSH, 2896); // NEI n_renderObjects: GL_LIGHTING
        method.visitMethodInsn(INVOKESTATIC, GL, "glEnable", "(I)V", false);
        method.visitInsn(RETURN); method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd();
        byte[] code = writer.toByteArray();
        assertSame(code, new GLStateTrackerTransformer().transform("NEIFixture", "NEIFixture", code));
    }

    @Test public void retainsTheRealVanillaOverlayInjectionTarget() throws Exception {
        byte[] original;
        try (java.io.InputStream input = getClass().getResourceAsStream("/net/minecraft/client/renderer/ItemRenderer.class")) {
            assertNotNull(input);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = input.read(buffer)) >= 0; ) bytes.write(buffer, 0, n);
            original = bytes.toByteArray();
        }
        ClassNode before = new ClassNode(ASM5), after = new ClassNode(ASM5);
        new ClassReader(original).accept(before, 0);
        new ClassReader(new GLStateTrackerTransformer().transform(before.name.replace('/', '.'), before.name.replace('/', '.'), original)).accept(after, 0);
        MethodNode overlay = before.methods.stream().filter(m -> m.name.equals("renderOverlays")).findFirst().get();
        MethodNode transformed = after.methods.stream().filter(m -> m.name.equals("renderOverlays")).findFirst().get();
        long targets = Arrays.stream(overlay.instructions.toArray()).filter(i -> i instanceof MethodInsnNode)
                .map(i -> (MethodInsnNode)i).filter(i -> i.owner.equals(GL) && i.name.equals("glEnable") && i.desc.equals("(I)V")).count();
        long preserved = Arrays.stream(transformed.instructions.toArray()).filter(i -> i instanceof MethodInsnNode)
                .map(i -> (MethodInsnNode)i).filter(i -> i.owner.equals(GL) && i.name.equals("glEnable") && i.desc.equals("(I)V")).count();
        assertTrue("Galacticraft's required target must exist", targets > 0);
        assertEquals(targets, preserved);
        new Analyzer<>(new BasicVerifier()).analyze(after.name, transformed);
    }

    private static byte[] fixture(String name, boolean unrelated) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "renderOverlays", "()J", null, null);
        method.visitCode();
        method.visitLdcInsn(7L);
        method.visitLdcInsn(2912); // GL_FOG
        method.visitMethodInsn(INVOKESTATIC, GL, unrelated ? "glClear" : "glEnable", "(I)V", false);
        method.visitLdcInsn(3553); // GL_TEXTURE_2D
        method.visitMethodInsn(INVOKESTATIC, GL, unrelated ? "glClear" : "glDisable", "(I)V", false);
        method.visitInsn(LRETURN);
        method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] glStub() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC, GL, null, "java/lang/Object", null);
        for (String name : Arrays.asList("glEnable", "glDisable")) {
            MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, name, "(I)V", null, null);
            method.visitCode(); method.visitVarInsn(ILOAD, 0);
            method.visitMethodInsn(INVOKESTATIC, LegacyGLTransformerTest.class.getName().replace('.', '/'), "record", "(I)V", false);
            method.visitInsn(RETURN); method.visitMaxs(0, 0); method.visitEnd();
        }
        writer.visitEnd(); return writer.toByteArray();
    }
    private static class FixtureLoader extends ClassLoader {
        Class<?> define(String name, byte[] code) { return defineClass(name, code, 0, code.length); }
    }
}
