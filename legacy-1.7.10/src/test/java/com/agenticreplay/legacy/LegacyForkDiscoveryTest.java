package com.agenticreplay.legacy;

import cpw.mods.fml.common.discovery.ModCandidate;
import cpw.mods.fml.common.discovery.ContainerType;
import cpw.mods.fml.common.discovery.asm.ASMModParser;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModContainerFactory;
import cpw.mods.fml.common.MetadataCollection;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import static org.junit.Assert.*;

public class LegacyForkDiscoveryTest {
    @Test public void corePluginDefersAllOriginalConfigurationsUntilInjectData() throws Exception {
        try (JarFile jar = new JarFile(System.getProperty("agenticreplay.distributable"));
             JarFile original = new JarFile("engine-inputs/replaymod-1.7.10-2.5.2.jar")) {
            String entry = "com/replaymod/core/LoadingPlugin.class";
            ClassNode plugin = new ClassNode(Opcodes.ASM5);
            ClassNode upstream = new ClassNode(Opcodes.ASM5);
            new ClassReader(jar.getInputStream(jar.getEntry(entry))).accept(plugin, 0);
            new ClassReader(original.getInputStream(original.getEntry(entry))).accept(upstream, 0);
            assertEquals(configurations(upstream), configurations(plugin));
            assertEquals(8, configurations(plugin).size());
            boolean registersLater = false;
            for (MethodNode method : plugin.methods) for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                assertNotEquals("Core plugin must not bootstrap a second Mixin loader",
                        "org/spongepowered/asm/launch/MixinBootstrap", call.owner);
                if ("<init>".equals(method.name)) assertFalse(call.owner.startsWith("org/spongepowered/"));
                if ("injectData".equals(method.name) && "org/spongepowered/asm/mixin/Mixins".equals(call.owner)
                        && "addConfiguration".equals(call.name)) registersLater = true;
            }
            assertTrue(registersLater);
        }
    }

    private static Set<String> configurations(ClassNode type) {
        Set<String> result = new TreeSet<>();
        for (MethodNode method : type.methods) for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof LdcInsnNode && ((LdcInsnNode) instruction).cst instanceof String) {
                String value = (String) ((LdcInsnNode) instruction).cst;
                if (value.startsWith("mixins.") && value.endsWith(".json")) result.add(value);
            }
        }
        return result;
    }

    @Test public void unimixinsIsTheOnlyMixinRuntimeProvider() throws Exception {
        try (JarFile jar = new JarFile(System.getProperty("agenticreplay.distributable"))) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                assertFalse(name, name.startsWith("org/spongepowered/") && name.endsWith(".class"));
                assertFalse(name, name.startsWith("META-INF/services/org.spongepowered."));
            }
            assertNull(jar.getEntry("META-INF/services/javax.annotation.processing.Processor"));
            assertNotNull(jar.getEntry("mixins.core.replaymod.json"));
            assertNotNull(jar.getEntry("com/replaymod/core/LoadingPlugin.class"));
        }
    }

    @Test public void actualFMLParserFindsBothModsInTheSingleArtifact() throws Exception {
        // The real launcher sets this package-private field before discovery.
        java.lang.reflect.Field side = cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");
        side.setAccessible(true);
        side.set(null, cpw.mods.fml.relauncher.Side.CLIENT);
        File file = new File(System.getProperty("agenticreplay.distributable"));
        ModCandidate candidate = new ModCandidate(file, file, ContainerType.JAR);
        try (JarFile jar = new JarFile(file)) {
            Map<String, String> expected = new LinkedHashMap<>();
            expected.put("com/replaymod/core/ReplayMod.class", "replaymod");
            expected.put("com/agenticreplay/legacy/AgenticReplayLegacy.class", "agenticreplay");
            for (Map.Entry<String, String> entry : expected.entrySet()) {
                try (InputStream stream = jar.getInputStream(jar.getEntry(entry.getKey()))) {
                    ASMModParser parser = new ASMModParser(stream);
                    parser.validate();
                    ModContainer mod = ModContainerFactory.instance().build(parser, file, candidate);
                    assertNotNull(entry.getKey(), mod);
                    assertEquals(entry.getValue(), mod.getModId());
                }
            }
            MetadataCollection metadata = MetadataCollection.from(jar.getInputStream(jar.getEntry("mcmod.info")), file.getName());
            assertFalse(metadata.getMetadataForId("replaymod", null).autogenerated);
            assertFalse(metadata.getMetadataForId("agenticreplay", null).autogenerated);
        }
    }

    @Test public void manifestRetainsFMLDiscoveryWithoutCascadedTweaker() throws Exception {
        try (JarFile jar = new JarFile(System.getProperty("agenticreplay.distributable"))) {
            Attributes attributes = jar.getManifest().getMainAttributes();
            assertNull(attributes.getValue("TweakClass"));
            assertNull(attributes.getValue("TweakOrder"));
            // GTNH JarJar 0.3.7-beta's ModSide branch reads a private FML field.
            // Preserve upstream's absence of that optional manifest attribute.
            assertNull(attributes.getValue("ModSide"));
            assertEquals("true", attributes.getValue("ForceLoadAsMod"));
            assertEquals("true", attributes.getValue("FMLCorePluginContainsFMLMod"));
            assertEquals("com.replaymod.core.LoadingPlugin", attributes.getValue("FMLCorePlugin"));
            assertEquals("replaymod_at.cfg", attributes.getValue("FMLAT"));
            assertNotNull(jar.getEntry("com/replaymod/core/ReplayMod.class"));
            assertNotNull(jar.getEntry("com/agenticreplay/legacy/AgenticReplayLegacy.class"));
            assertNotNull(jar.getEntry("META-INF/LICENSE-agenticreplay.txt"));
            assertNotNull(jar.getEntry("META-INF/LICENSE-agenticreplay-common.txt"));
        }
    }
}
