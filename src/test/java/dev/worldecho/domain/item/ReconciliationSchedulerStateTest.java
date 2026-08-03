package dev.worldecho.domain.item;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReconciliationSchedulerStateTest {

    @Test
    void firstScheduleSucceeds() {
        ReconciliationSchedulerState state = new ReconciliationSchedulerState();
        UUID player = UUID.randomUUID();
        assertTrue(state.trySchedule(player));
        assertTrue(state.isPending(player));
        assertEquals(1, state.pendingCount());
    }

    @Test
    void sameTickEventCoalescesIntoOne() {
        ReconciliationSchedulerState state = new ReconciliationSchedulerState();
        UUID player = UUID.randomUUID();
        assertTrue(state.trySchedule(player));
        assertFalse(state.trySchedule(player), "Second call in same tick should coalesce");
        assertEquals(1, state.pendingCount());
    }

    @Test
    void differentPlayersBothSchedule() {
        ReconciliationSchedulerState state = new ReconciliationSchedulerState();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        assertTrue(state.trySchedule(p1));
        assertTrue(state.trySchedule(p2));
        assertEquals(2, state.pendingCount());
    }

    @Test
    void clearRemovesPendingState() {
        ReconciliationSchedulerState state = new ReconciliationSchedulerState();
        UUID player = UUID.randomUUID();
        state.trySchedule(player);
        state.clear(player);
        assertFalse(state.isPending(player));
        assertEquals(0, state.pendingCount());
    }

    @Test
    void shutdownRejectsNewScheduling() {
        ReconciliationSchedulerState state = new ReconciliationSchedulerState();
        state.shutdown();
        UUID player = UUID.randomUUID();
        assertFalse(state.trySchedule(player), "Shutdown should reject new scheduling");
        assertEquals(0, state.pendingCount());
    }

    @Test
    void shutdownClearsPendingState() {
        ReconciliationSchedulerState state = new ReconciliationSchedulerState();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        state.trySchedule(p1);
        state.trySchedule(p2);
        state.shutdown();
        assertEquals(0, state.pendingCount());
        assertFalse(state.isPending(p1));
        assertFalse(state.isPending(p2));
    }

    @Test
    void playerCanBeRescheduledAfterClear() {
        ReconciliationSchedulerState state = new ReconciliationSchedulerState();
        UUID player = UUID.randomUUID();
        state.trySchedule(player);
        state.clear(player);
        assertTrue(state.trySchedule(player), "Player should be schedulable again after clear");
    }

    @Test
    void playerCanBeRescheduledAcrossTicks() {
        ReconciliationSchedulerState state = new ReconciliationSchedulerState();
        UUID player = UUID.randomUUID();
        state.trySchedule(player);
        state.clear(player);
        // Next tick
        assertTrue(state.trySchedule(player));
        state.clear(player);
        // Third tick
        assertTrue(state.trySchedule(player));
    }

    @Test
    void shutdownIsIdempotent() {
        ReconciliationSchedulerState state = new ReconciliationSchedulerState();
        state.shutdown();
        state.shutdown();
        assertFalse(state.trySchedule(UUID.randomUUID()));
    }
}
