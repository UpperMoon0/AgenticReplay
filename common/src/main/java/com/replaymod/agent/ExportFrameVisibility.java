package com.replaymod.agent;

import java.util.Collection;
import java.util.Map;
import java.util.HashMap;

/** Visibility eligibility for explicitly authored actors, only during their render frame. */
public final class ExportFrameVisibility {
    public record Actor(int id, double x, double y, double z) {}
    private record Block(int x, int y, int z) {}
    private static final Map<Integer, Block> EMPTY = Map.of();
    private static final ThreadLocal<Map<Integer, Block>> CURRENT = ThreadLocal.withInitial(() -> EMPTY);
    private ExportFrameVisibility() {}

    public static Scope open(Collection<Actor> actors) {
        Map<Integer, Block> positions = new HashMap<>();
        for (Actor actor : actors) {
            if (!Double.isFinite(actor.x) || !Double.isFinite(actor.y) || !Double.isFinite(actor.z))
                throw new IllegalArgumentException("Finite actor position required");
            positions.put(actor.id, new Block((int)Math.floor(actor.x), (int)Math.floor(actor.y), (int)Math.floor(actor.z)));
        }
        Map<Integer, Block> next = Map.copyOf(positions), previous = CURRENT.get();
        Thread owner = Thread.currentThread();
        CURRENT.set(next);
        return new Scope() {
            private boolean closed;
            @Override public void close() {
                if (closed) return;
                if (Thread.currentThread() != owner || CURRENT.get() != next)
                    throw new IllegalStateException("Export visibility scopes must close on their owner thread in reverse order");
                if (previous == EMPTY) CURRENT.remove(); else CURRENT.set(previous);
                closed = true;
            }
        };
    }

    public static boolean actor(int id) { return CURRENT.get().containsKey(id); }
    public static boolean block(int x, int y, int z) { return CURRENT.get().containsValue(new Block(x, y, z)); }
    public interface Scope extends AutoCloseable { @Override void close(); }
}
