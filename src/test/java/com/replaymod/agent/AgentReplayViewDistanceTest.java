package com.replaymod.agent;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class AgentReplayViewDistanceTest {
    private int clientDistance = 12;
    private void setBudget(int value) {
        AgentReplayViewDistance.set(value, () -> clientDistance, distance -> clientDistance = distance);
    }
    @After public void closeSession() { AgentReplayViewDistance.clear(); }
    @Test public void recordedLowDistanceCannotUndoCameraBudgetOnRepeatedSeeks() {
        setBudget(32);
        assertEquals(32, AgentReplayViewDistance.effective(8));
        assertEquals(32, AgentReplayViewDistance.effective(8));
        AgentReplayViewDistance.clear();
        assertEquals(8, AgentReplayViewDistance.effective(8));
    }
    @Test public void invalidBudgetsDoNotMutateTheCurrentSession() {
        setBudget(16);
        for (int invalid : new int[] {1, 33}) {
            try {
                setBudget(invalid);
                fail("Invalid distance accepted");
            } catch (IllegalArgumentException expected) { }
        }
        assertEquals(16, AgentReplayViewDistance.effective(8));
        assertEquals(16, clientDistance);
    }
    @Test public void closeRestoresOriginalClientSettingAfterMultipleBudgetChanges() {
        setBudget(32);
        setBudget(16);
        AgentReplayViewDistance.clear();
        assertEquals(12, clientDistance);
        assertEquals(8, AgentReplayViewDistance.effective(8));
    }
    @Test public void laterReplayUsesRecordedClampAndSnapshotsItsOwnClientSetting() {
        setBudget(32);
        AgentReplayViewDistance.clear();
        assertEquals(6, AgentReplayViewDistance.effective(6));
        clientDistance = 10;
        setBudget(24);
        AgentReplayViewDistance.clear();
        assertEquals(10, clientDistance);
        assertEquals(6, AgentReplayViewDistance.effective(6));
    }
    @Test public void repeatedCleanupDoesNotOverwriteLaterLiveSettings() {
        setBudget(32);
        AgentReplayViewDistance.clear();
        clientDistance = 10;
        AgentReplayViewDistance.clear();
        assertEquals(10, clientDistance);
    }
    @Test public void invalidFirstBudgetDoesNotCaptureClientSettings() {
        try {
            setBudget(33);
            fail("Invalid distance accepted");
        } catch (IllegalArgumentException expected) { }
        clientDistance = 10;
        AgentReplayViewDistance.clear();
        assertEquals(10, clientDistance);
        assertEquals(8, AgentReplayViewDistance.effective(8));
    }
    @Test public void restorationFailureStillClearsOverrideAndSavedSetting() {
        AgentReplayViewDistance.set(32, () -> 12, distance -> {
            if (distance == 12) throw new IllegalStateException("Restoration failed");
        });
        try {
            AgentReplayViewDistance.clear();
            fail("Restoration failure expected");
        } catch (IllegalStateException expected) { }
        assertEquals(8, AgentReplayViewDistance.effective(8));
        AgentReplayViewDistance.clear();
    }
}
