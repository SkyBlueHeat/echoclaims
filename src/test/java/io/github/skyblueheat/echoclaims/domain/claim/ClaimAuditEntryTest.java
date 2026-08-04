package io.github.skyblueheat.echoclaims.domain.claim;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimAuditEntryTest {

    private static final UUID ENTRY_ID = UUID.randomUUID();
    private static final UUID CLAIM_ID = UUID.randomUUID();
    private static final UUID ACTOR_UUID = UUID.randomUUID();

    @Test
    void createsValidEntry() {
        assertDoesNotThrow(() -> new ClaimAuditEntry(
                ENTRY_ID, CLAIM_ID, ClaimActorType.PLAYER, ACTOR_UUID,
                ClaimAction.CREATED, "Claim created", 10_000L, 0, Map.of()
        ));
    }

    @Test
    void allowsNullActorUuid() {
        ClaimAuditEntry entry = new ClaimAuditEntry(
                ENTRY_ID, CLAIM_ID, ClaimActorType.SYSTEM, null,
                ClaimAction.CREATED, "", 10_000L, 0, Map.of()
        );
        assertNull(entry.actorUuid());
    }

    @Test
    void stripsReason() {
        ClaimAuditEntry entry = new ClaimAuditEntry(
                ENTRY_ID, CLAIM_ID, ClaimActorType.PLAYER, ACTOR_UUID,
                ClaimAction.CANCELLED, "  cancelled by player  ", 10_000L, 1, Map.of()
        );
        assertEquals("cancelled by player", entry.reason());
        assertTrue(entry.hasReason());
    }

    @Test
    void nullReasonBecomesEmpty() {
        ClaimAuditEntry entry = new ClaimAuditEntry(
                ENTRY_ID, CLAIM_ID, ClaimActorType.PLAYER, ACTOR_UUID,
                ClaimAction.CREATED, null, 10_000L, 0, Map.of()
        );
        assertEquals("", entry.reason());
        assertFalse(entry.hasReason());
    }

    @Test
    void rejectsNegativeTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimAuditEntry(
                ENTRY_ID, CLAIM_ID, ClaimActorType.PLAYER, ACTOR_UUID,
                ClaimAction.CREATED, "", -1L, 0, Map.of()
        ));
    }

    @Test
    void rejectsNegativeClaimVersion() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimAuditEntry(
                ENTRY_ID, CLAIM_ID, ClaimActorType.PLAYER, ACTOR_UUID,
                ClaimAction.CREATED, "", 10_000L, -1, Map.of()
        ));
    }

    @Test
    void defensiveCopiesMetadata() {
        var mutable = new java.util.LinkedHashMap<String, String>();
        mutable.put("k", "v");
        ClaimAuditEntry entry = new ClaimAuditEntry(
                ENTRY_ID, CLAIM_ID, ClaimActorType.PLAYER, ACTOR_UUID,
                ClaimAction.CREATED, "", 10_000L, 0, mutable
        );
        mutable.put("k2", "v2");
        assertEquals("v", entry.metadata().get("k"));
        assertFalse(entry.metadata().containsKey("k2"));
    }
}
