package io.github.skyblueheat.echoclaims.domain.claim;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimTest {

    private static final UUID CLAIM_ID = UUID.randomUUID();
    private static final UUID INCIDENT_ID = UUID.randomUUID();
    private static final UUID PLAYER_UUID = UUID.randomUUID();

    @Test
    void createsValidClaim() {
        assertDoesNotThrow(() -> new Claim(
                CLAIM_ID, "ABC12345", INCIDENT_ID, PLAYER_UUID,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, "Lost items",
                10_000L, 0L, 0L, 0, Map.of()
        ));
    }

    @Test
    void stripsAndValidatesPublicReference() {
        Claim claim = new Claim(
                CLAIM_ID, "  ABC12345  ", INCIDENT_ID, PLAYER_UUID,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, "",
                10_000L, 0L, 0L, 0, Map.of()
        );
        assertEquals("ABC12345", claim.publicReference());
    }

    @Test
    void rejectsBlankPublicReference() {
        assertThrows(IllegalArgumentException.class, () -> new Claim(
                CLAIM_ID, "  ", INCIDENT_ID, PLAYER_UUID,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, "",
                10_000L, 0L, 0L, 0, Map.of()
        ));
    }

    @Test
    void rejectsNullPublicReference() {
        assertThrows(NullPointerException.class, () -> new Claim(
                CLAIM_ID, null, INCIDENT_ID, PLAYER_UUID,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, "",
                10_000L, 0L, 0L, 0, Map.of()
        ));
    }

    @Test
    void rejectsNegativeTimestamps() {
        assertThrows(IllegalArgumentException.class, () -> new Claim(
                CLAIM_ID, "ABC12345", INCIDENT_ID, PLAYER_UUID,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, "",
                -1L, 0L, 0L, 0, Map.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new Claim(
                CLAIM_ID, "ABC12345", INCIDENT_ID, PLAYER_UUID,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, "",
                10_000L, -1L, 0L, 0, Map.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new Claim(
                CLAIM_ID, "ABC12345", INCIDENT_ID, PLAYER_UUID,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, "",
                10_000L, 0L, -1L, 0, Map.of()
        ));
    }

    @Test
    void rejectsNegativeVersion() {
        assertThrows(IllegalArgumentException.class, () -> new Claim(
                CLAIM_ID, "ABC12345", INCIDENT_ID, PLAYER_UUID,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, "",
                10_000L, 0L, 0L, -1, Map.of()
        ));
    }

    @Test
    void stripsDescription() {
        Claim claim = new Claim(
                CLAIM_ID, "ABC12345", INCIDENT_ID, PLAYER_UUID,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, "  hello  ",
                10_000L, 0L, 0L, 0, Map.of()
        );
        assertEquals("hello", claim.description());
    }

    @Test
    void nullDescriptionBecomesEmpty() {
        Claim claim = new Claim(
                CLAIM_ID, "ABC12345", INCIDENT_ID, PLAYER_UUID,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, null,
                10_000L, 0L, 0L, 0, Map.of()
        );
        assertEquals("", claim.description());
        assertFalse(claim.hasDescription());
    }

    @Test
    void defensiveCopiesMetadata() {
        Map<String, String> mutable = new LinkedHashMap<>();
        mutable.put("key1", "value1");
        Claim claim = new Claim(
                CLAIM_ID, "ABC12345", INCIDENT_ID, PLAYER_UUID,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, "",
                10_000L, 0L, 0L, 0, mutable
        );
        mutable.put("key2", "value2");
        assertEquals("value1", claim.metadataValue("key1"));
        assertFalse(claim.metadata().containsKey("key2"));
    }

    @Test
    void statusConvenienceMethods() {
        Claim draft = new Claim(
                CLAIM_ID, "ABC12345", INCIDENT_ID, PLAYER_UUID,
                ClaimStatus.DRAFT, ClaimSource.PLAYER_COMMAND, "",
                10_000L, 0L, 0L, 0, Map.of()
        );
        assertTrue(draft.isDraft());
        assertTrue(draft.isOpen());
        assertFalse(draft.isSubmitted());
        assertFalse(draft.isCancelled());

        Claim submitted = new Claim(
                CLAIM_ID, "ABC12345", INCIDENT_ID, PLAYER_UUID,
                ClaimStatus.SUBMITTED, ClaimSource.PLAYER_COMMAND, "",
                10_000L, 20_000L, 0L, 1, Map.of()
        );
        assertTrue(submitted.isSubmitted());
        assertTrue(submitted.isOpen());
        assertFalse(submitted.isDraft());

        Claim cancelled = new Claim(
                CLAIM_ID, "ABC12345", INCIDENT_ID, PLAYER_UUID,
                ClaimStatus.CANCELLED, ClaimSource.PLAYER_COMMAND, "",
                10_000L, 0L, 30_000L, 1, Map.of()
        );
        assertTrue(cancelled.isCancelled());
        assertFalse(cancelled.isOpen());
    }
}
