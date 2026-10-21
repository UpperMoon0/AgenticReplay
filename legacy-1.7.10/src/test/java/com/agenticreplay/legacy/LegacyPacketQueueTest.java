package com.agenticreplay.legacy;
import com.replaymod.replay.ReplayPacketQueue;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class LegacyPacketQueueTest {
 static class Dev { private Queue<String> receivedPacketsQueue=new ArrayDeque<>(); }
 static class Production { private Queue<String> field_150748_i=new ArrayDeque<>(); }
 static class Embedded extends Production {}
 @Test public void receivesOnlyTheNamedInboundQueueInBothNamingEnvironments() throws Exception {
  Dev dev=new Dev();Production production=new Production();
  assertSame(dev.receivedPacketsQueue,ReplayPacketQueue.received(dev));
  assertSame(production.field_150748_i,ReplayPacketQueue.received(production));
  Embedded embedded=new Embedded();assertSame(((Production)embedded).field_150748_i,ReplayPacketQueue.received(embedded));
  dev.receivedPacketsQueue.add("old");dev.receivedPacketsQueue.add("new");
  assertEquals("old",ReplayPacketQueue.received(dev).poll());assertEquals("new",ReplayPacketQueue.received(dev).poll());
 }
 @Test public void actualVanillaQueueIsPresent() throws Exception {
  assertEquals(Queue.class,net.minecraft.network.NetworkManager.class.getDeclaredField("receivedPacketsQueue").getType());
 }
}
