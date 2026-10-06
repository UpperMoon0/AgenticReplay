package com.replaymod.agent;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class AgentReplayViewDistanceTest {
    @After public void closeSession() { AgentReplayViewDistance.clear(); }
    @Test public void recordedLowDistanceCannotUndoCameraBudgetOnRepeatedSeeks() {
        AgentReplayViewDistance.set(32);
        assertEquals(32, AgentReplayViewDistance.effective(8));
        assertEquals(32, AgentReplayViewDistance.effective(8));
        AgentReplayViewDistance.clear();
        assertEquals(8, AgentReplayViewDistance.effective(8));
    }
    @Test public void invalidBudgetsDoNotMutateTheCurrentSession() {
        AgentReplayViewDistance.set(16);
        for (int invalid : new int[] {1, 33}) {
            try {
                AgentReplayViewDistance.set(invalid);
                fail("Invalid distance accepted");
            } catch (IllegalArgumentException expected) { }
        }
        assertEquals(16, AgentReplayViewDistance.effective(8));
    }
}
