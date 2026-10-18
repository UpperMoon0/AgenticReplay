package com.agenticreplay.legacy;
import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
public class LegacyClientQueueTest {
    @Test public void renderDrainRunsQueuedTaskWithoutSimulationTick() {
        LegacyClientQueue queue = new LegacyClientQueue(); AtomicInteger count = new AtomicInteger();
        queue.enqueue(count::incrementAndGet); assertEquals(0,count.get()); queue.drain(); assertEquals(1,count.get());
    }
    @Test(expected=RejectedExecutionException.class) public void boundsAdmissionWithoutBlocking() {
        LegacyClientQueue queue = new LegacyClientQueue(1); queue.enqueue(()->{}); queue.enqueue(()->{});
    }
    @Test public void enqueueFromWorkerIsNotBlockedByExecutingTask() throws Exception {
        LegacyClientQueue queue = new LegacyClientQueue(); AtomicInteger count = new AtomicInteger();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            queue.enqueue(()-> {
                try { worker.submit(()->queue.enqueue(count::incrementAndGet)).get(1,TimeUnit.SECONDS); }
                catch (Exception failure) { throw new AssertionError("Queue lock held during client execution",failure); }
            });
            queue.drain(); assertEquals(0,count.get()); queue.drain(); assertEquals(1,count.get());
        } finally { worker.shutdownNow(); }
    }
    @Test public void nestedRenderCannotReenterAndCancelledTaskCannotMutate() {
        LegacyClientQueue queue = new LegacyClientQueue(); AtomicInteger count = new AtomicInteger();
        queue.enqueue(()-> { queue.enqueue(count::incrementAndGet); queue.drain(); assertEquals(0,count.get()); });
        FutureTask<Void> cancelled = new FutureTask<Void>(() -> { count.addAndGet(100); return null; });
        queue.enqueue(cancelled); cancelled.cancel(false); queue.drain(); assertEquals(0,count.get());
        queue.drain(); assertEquals(1,count.get());
    }
}
