package com.agenticreplay.legacy;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.Assert.*;

/** Ensure the source replacement never cancels vanilla's polling loop. */
public class LegacyGuiInputTest {
    @Test public void onlyPerEventDispatchIsIntercepted() throws Exception {
        String file = "/com/replaymod/recording/mixin/MixinGuiScreen.class";
        try (InputStream stream = getClass().getResourceAsStream(file)) {
            assertNotNull("Source-owned MixinGuiScreen is missing", stream);
            ClassNode type = new ClassNode(Opcodes.ASM5);
            new ClassReader(stream).accept(type, 0);
            Set<String> targets = new HashSet<>();
            for (MethodNode method : type.methods) {
                if (method.visibleAnnotations == null) continue;
                for (AnnotationNode annotation : method.visibleAnnotations) {
                    if (!annotation.desc.endsWith("/Inject;")) continue;
                    for (int index=0; index<annotation.values.size(); index+=2) {
                        if ("method".equals(annotation.values.get(index))) {
                            Object value = annotation.values.get(index+1);
                            if (value instanceof java.util.List) for(Object item : (java.util.List<?>)value) targets.add(String.valueOf(item));
                            else targets.add(String.valueOf(value));
                        }
                    }
                }
            }
            assertTrue(targets.contains("handleMouseInput"));
            assertTrue(targets.contains("handleKeyboardInput"));
            assertFalse("Must not suppress Angelica's wrapped Mouse.next/Keyboard.next hooks",
                    targets.contains("handleInput"));
        }
    }
}
