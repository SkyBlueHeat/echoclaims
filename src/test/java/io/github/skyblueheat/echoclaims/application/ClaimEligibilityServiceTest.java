package io.github.skyblueheat.echoclaims.application;

import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentStatus;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentType;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimEligibilityServiceTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OTHER_PLAYER = UUID.randomUUID();

    private Incident incident(IncidentStatus status) {
        return new Incident(
                UUID.randomUUID(), IncidentType.PLAYER_DEATH, PLAYER,
                10_000L, "world", new Coordinates(0, 0, 0),
                "FALL", null, "", UUID.randomUUID(), null, status,
                Map.of(), "dedup-1"
        );
    }

    @Test
    void eligibleWhenIncidentIsOpenAndOwnedByPlayerWithSnapshot() {
        var result = ClaimEligibilityService.evaluate(
                incident(IncidentStatus.OPEN), PLAYER);
        assertTrue(result.isEligible());
        assertTrue(result.rejectionReason().isEmpty());
    }

    @Test
    void rejectsNullIncident() {
        var result = ClaimEligibilityService.evaluate(null, PLAYER);
        assertFalse(result.isEligible());
        assertTrue(result.rejectionReason().isPresent());
    }

    @Test
    void rejectsWrongPlayer() {
        var result = ClaimEligibilityService.evaluate(
                incident(IncidentStatus.OPEN), OTHER_PLAYER);
        assertFalse(result.isEligible());
        assertTrue(result.rejectionReason().isPresent());
    }

    @Test
    void rejectsNonOpenStatus() {
        var result = ClaimEligibilityService.evaluate(
                incident(IncidentStatus.RESOLVED), PLAYER);
        assertFalse(result.isEligible());
        assertTrue(result.rejectionReason().isPresent());
    }
}
