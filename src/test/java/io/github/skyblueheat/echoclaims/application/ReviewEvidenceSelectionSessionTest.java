package io.github.skyblueheat.echoclaims.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewEvidenceSelectionSessionTest {

    private static final UUID STAFF_A = UUID.randomUUID();
    private static final UUID STAFF_B = UUID.randomUUID();
    private static final UUID CLAIM_1 = UUID.randomUUID();
    private static final UUID CLAIM_2 = UUID.randomUUID();

    @Test
    void storeAndResolveSameClaim() {
        ReviewEvidenceSelectionSession session = new ReviewEvidenceSelectionSession(
                Duration.ofSeconds(60), 10);
        List<String> refs = List.of("ref1", "ref2", "ref3");
        session.store(STAFF_A, CLAIM_1, refs);

        assertEquals(Optional.of("ref1"), session.resolve(STAFF_A, CLAIM_1, 1));
        assertEquals(Optional.of("ref2"), session.resolve(STAFF_A, CLAIM_1, 2));
        assertEquals(Optional.of("ref3"), session.resolve(STAFF_A, CLAIM_1, 3));
    }

    @Test
    void resolveWrongClaimReturnsEmpty() {
        ReviewEvidenceSelectionSession session = new ReviewEvidenceSelectionSession(
                Duration.ofSeconds(60), 10);
        session.store(STAFF_A, CLAIM_1, List.of("ref1"));

        assertFalse(session.resolve(STAFF_A, CLAIM_2, 1).isPresent());
    }

    @Test
    void storeForDifferentClaimsDoesNotOverwrite() {
        ReviewEvidenceSelectionSession session = new ReviewEvidenceSelectionSession(
                Duration.ofSeconds(60), 10);
        session.store(STAFF_A, CLAIM_1, List.of("ref1", "ref2"));
        session.store(STAFF_A, CLAIM_2, List.of("ref3", "ref4"));

        assertEquals(Optional.of("ref1"), session.resolve(STAFF_A, CLAIM_1, 1));
        assertEquals(Optional.of("ref3"), session.resolve(STAFF_A, CLAIM_2, 1));
    }

    @Test
    void resolveNonExistentStaffReturnsEmpty() {
        ReviewEvidenceSelectionSession session = new ReviewEvidenceSelectionSession(
                Duration.ofSeconds(60), 10);
        session.store(STAFF_A, CLAIM_1, List.of("ref1"));

        assertFalse(session.resolve(STAFF_B, CLAIM_1, 1).isPresent());
    }

    @Test
    void resolveIndexZeroReturnsEmpty() {
        ReviewEvidenceSelectionSession session = new ReviewEvidenceSelectionSession(
                Duration.ofSeconds(60), 10);
        session.store(STAFF_A, CLAIM_1, List.of("ref1"));

        assertFalse(session.resolve(STAFF_A, CLAIM_1, 0).isPresent());
    }

    @Test
    void resolveNegativeIndexReturnsEmpty() {
        ReviewEvidenceSelectionSession session = new ReviewEvidenceSelectionSession(
                Duration.ofSeconds(60), 10);
        session.store(STAFF_A, CLAIM_1, List.of("ref1"));

        assertFalse(session.resolve(STAFF_A, CLAIM_1, -1).isPresent());
    }

    @Test
    void resolveIndexBeyondBoundsReturnsEmpty() {
        ReviewEvidenceSelectionSession session = new ReviewEvidenceSelectionSession(
                Duration.ofSeconds(60), 10);
        session.store(STAFF_A, CLAIM_1, List.of("ref1"));

        assertFalse(session.resolve(STAFF_A, CLAIM_1, 2).isPresent());
    }

    @Test
    void expiredSessionReturnsEmpty() throws InterruptedException {
        ReviewEvidenceSelectionSession session = new ReviewEvidenceSelectionSession(
                Duration.ofMillis(50), 10);
        session.store(STAFF_A, CLAIM_1, List.of("ref1"));

        Thread.sleep(60);
        assertFalse(session.resolve(STAFF_A, CLAIM_1, 1).isPresent());
    }

    @Test
    void clearRemovesAllSessionsForStaff() {
        ReviewEvidenceSelectionSession session = new ReviewEvidenceSelectionSession(
                Duration.ofSeconds(60), 10);
        session.store(STAFF_A, CLAIM_1, List.of("ref1"));
        session.store(STAFF_A, CLAIM_2, List.of("ref2"));
        session.clear(STAFF_A);

        assertFalse(session.resolve(STAFF_A, CLAIM_1, 1).isPresent());
        assertFalse(session.resolve(STAFF_A, CLAIM_2, 1).isPresent());
    }

    @Test
    void clearAllRemovesEverything() {
        ReviewEvidenceSelectionSession session = new ReviewEvidenceSelectionSession(
                Duration.ofSeconds(60), 10);
        session.store(STAFF_A, CLAIM_1, List.of("ref1"));
        session.store(STAFF_B, CLAIM_2, List.of("ref2"));
        session.clearAll();

        assertFalse(session.resolve(STAFF_A, CLAIM_1, 1).isPresent());
        assertFalse(session.resolve(STAFF_B, CLAIM_2, 1).isPresent());
    }

    @Test
    void trackedStaffCountReturnsDistinctStaff() {
        ReviewEvidenceSelectionSession session = new ReviewEvidenceSelectionSession(
                Duration.ofSeconds(60), 10);
        session.store(STAFF_A, CLAIM_1, List.of("ref1"));
        session.store(STAFF_A, CLAIM_2, List.of("ref2"));
        session.store(STAFF_B, CLAIM_1, List.of("ref3"));

        assertEquals(2, session.trackedStaffCount());
    }

    @Test
    void evictsOldestWhenMaxStaffReached() {
        ReviewEvidenceSelectionSession session = new ReviewEvidenceSelectionSession(
                Duration.ofSeconds(60), 2);
        session.store(STAFF_A, CLAIM_1, List.of("ref1"));
        session.store(STAFF_B, CLAIM_1, List.of("ref2"));
        session.store(UUID.randomUUID(), CLAIM_1, List.of("ref3"));

        assertEquals(2, session.trackedStaffCount());
    }

    @Test
    void storeRejectsNullStaffUuid() {
        ReviewEvidenceSelectionSession session = new ReviewEvidenceSelectionSession(
                Duration.ofSeconds(60), 10);
        assertThrows(NullPointerException.class, () ->
                session.store(null, CLAIM_1, List.of("ref1")));
    }

    @Test
    void storeRejectsNullClaimId() {
        ReviewEvidenceSelectionSession session = new ReviewEvidenceSelectionSession(
                Duration.ofSeconds(60), 10);
        assertThrows(NullPointerException.class, () ->
                session.store(STAFF_A, null, List.of("ref1")));
    }

    @Test
    void storeRejectsNullReferences() {
        ReviewEvidenceSelectionSession session = new ReviewEvidenceSelectionSession(
                Duration.ofSeconds(60), 10);
        assertThrows(NullPointerException.class, () ->
                session.store(STAFF_A, CLAIM_1, null));
    }

    @Test
    void resolveRejectsNullStaffUuid() {
        ReviewEvidenceSelectionSession session = new ReviewEvidenceSelectionSession(
                Duration.ofSeconds(60), 10);
        assertThrows(NullPointerException.class, () ->
                session.resolve(null, CLAIM_1, 1));
    }

    @Test
    void resolveRejectsNullClaimId() {
        ReviewEvidenceSelectionSession session = new ReviewEvidenceSelectionSession(
                Duration.ofSeconds(60), 10);
        assertThrows(NullPointerException.class, () ->
                session.resolve(STAFF_A, null, 1));
    }
}
