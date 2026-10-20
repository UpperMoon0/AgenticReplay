package com.replaymod.agent;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class FrozenTerrainFrameWiringTest {
    @Test public void terrainUpdateRunsOnceOutsideTheReplayTickLoopAndAfterViewportRestore() throws Exception {
        ClassNode type=new ClassNode();
        try(var stream=getClass().getResourceAsStream("/com/replaymod/render/rendering/VideoRenderer.class")) {
            assertNotNull(stream);new ClassReader(stream).accept(type,0);
        }
        MethodNode frame=type.methods.stream().filter(m->m.name.equals("updateForNextFrame")).findFirst().orElseThrow();
        int terrain=-1,restore=-1,lastWorldTick=-1,calls=0;
        for(int i=0;i<frame.instructions.size();i++)if(frame.instructions.get(i) instanceof MethodInsnNode call) {
            if(call.owner.equals("com/replaymod/agent/NativeDistantTerrain")&&call.name.equals("tick")){terrain=i;calls++;}
            if(call.owner.endsWith("/VirtualWindow")&&call.name.equals("unbind"))restore=i;
            if(call.owner.equals(type.name)&&call.name.equals("tick"))lastWorldTick=i;
        }
        assertEquals(1,calls);assertTrue(terrain>lastWorldTick);assertTrue(restore>=0&&terrain>restore);
        for(int i=0;i<frame.instructions.size();i++)if(frame.instructions.get(i) instanceof JumpInsnNode jump) {
            int target=frame.instructions.indexOf(jump.label);
            assertFalse("Terrain callback inside a world-tick loop",target<i&&target<=terrain&&terrain<=i);
        }
    }
}
