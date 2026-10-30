package com.agenticreplay.legacy;

import com.replaymod.replay.BootstrapPacketOrder;
import com.replaymod.core.asm.ReplayBootstrapTransformer;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LegacyBootstrapOrderTest {
    @Test public void earlySelfSpawnIsRetainedUntilAfterJoin() {
        BootstrapPacketOrder order = new BootstrapPacketOrder();
        byte[] spawn = {12, 99};
        assertTrue(order.defer(spawn));
        spawn[1] = 0;
        assertFalse(order.defer(new byte[]{63, 1}));
        assertFalse(order.defer(new byte[]{1, 3}));
        List<byte[]> pending = order.joined();
        assertEquals(1, pending.size()); assertArrayEquals(new byte[]{12, 99}, pending.get(0));
        assertFalse(order.defer(new byte[]{12, 2}));
        assertTrue(order.joined().isEmpty());
    }
    @Test public void restartDropsOldPendingSpawnAndRearmsOrdering() {
        BootstrapPacketOrder order = new BootstrapPacketOrder();
        assertTrue(order.defer(new byte[]{12, 1})); order.reset();
        assertTrue(order.joined().isEmpty()); order.reset();
        assertTrue(order.defer(new byte[]{12, 2}));
        assertArrayEquals(new byte[]{12, 2}, order.joined().get(0));
    }
    @Test public void normalRecordingAndNonPlayerPacketsKeepTheirOrder() {
        BootstrapPacketOrder order = new BootstrapPacketOrder();
        assertFalse(order.defer(new byte[0])); assertFalse(order.defer(new byte[]{2}));
        assertTrue(order.joined().isEmpty()); assertFalse(order.defer(new byte[]{12}));
    }
    @Test(expected=IllegalStateException.class) public void corruptPrefixHasBoundedMemory() {
        BootstrapPacketOrder order = new BootstrapPacketOrder();
        for (int i=0;i<1025;i++) order.defer(new byte[]{12});
    }
    @Test public void actualPinnedHandlerSelectsNewSenderExactlyOnce() throws Exception {
        String target="com.replaymod.replay.ReplayHandler";
        byte[] original;
        try(java.io.InputStream in=getClass().getResourceAsStream("/"+target.replace('.','/')+".class")){
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;
            while((n=in.read(b))!=-1)out.write(b,0,n);original=out.toByteArray();
        }
        ReplayBootstrapTransformer t=new ReplayBootstrapTransformer();byte[] result=t.transform(target,target,original);
        assertNotSame(original,result); assertSame(result,t.transform(target,target,result));
        assertSame(original,t.transform("other","other",original)); assertNull(t.transform(target,target,null));
    }
}
