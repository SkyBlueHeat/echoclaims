package io.github.skyblueheat.echoclaims.domain.incident;

import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentTest {

    private static final UUID INCIDENT = UUID.randomUUID();
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID SNAPSHOT = UUID.randomUUID();

    @Test
    void validIncidentIsConstructed() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("keepInventory", "false");
        metadata.put("lastDamageCause", "ENTITY_ATTACK");

        Incident incident = new Incident(
                INCIDENT, IncidentType.PLAYER_DEATH, PLAYER,
                1000L, "world", new Coordinates(1, 2, 3),
                "ENTITY_ATTACK", UUID.randomUUID(), "minecraft:zombie",
                SNAPSHOT, null, IncidentStatus.OPEN,
                metadata, "PLAYER_DEATH:" + PLAYER + ":world:1:2:3:1"
        );

        assertEquals(IncidentType.PLAYER_DEATH, incident.type());
        assertEquals("ENTITY_ATTACK", incident.cause());
        assertTrue(incident.hasKillerEntity());
        assertTrue(incident.hasKillerPlayer());
        assertFalse(incident.hasPostEventSnapshot());
        assertTrue(incident.hasMetadata());
        assertEquals("false", incident.metadataValue("keepInventory"));
    }

    @Test
    void blankDeduplicationKeyThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Incident(
                INCIDENT, IncidentType.PLAYER_DEATH, PLAYER,
                1000L, "world", new Coordinates(0, 0, 0),
                "", null, "",
                SNAPSHOT, null, IncidentStatus.OPEN,
                Map.of(), "  "
        ));
    }

    @Test
    void nullPreEventSnapshotThrows() {
        assertThrows(NullPointerException.class, () -> new Incident(
                INCIDENT, IncidentType.PLAYER_DEATH, PLAYER,
                1000L, "world", new Coordinates(0, 0, 0),
                "", null, "",
                null, null, IncidentStatus.OPEN,
                Map.of(), "key"
        ));
    }

    @Test
    void blankWorldIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Incident(
                INCIDENT, IncidentType.PLAYER_DEATH, PLAYER,
                1000L, "  ", new Coordinates(0, 0, 0),
                "", null, "",
                SNAPSHOT, null, IncidentStatus.OPEN,
                Map.of(), "key"
        ));
    }

    @Test
    void metadataIsDefensivelyCopied() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("key1", "value1");

        Incident incident = new Incident(
                INCIDENT, IncidentType.PLAYER_DEATH, PLAYER,
                1000L, "world", new Coordinates(0, 0, 0),
                "", null, "",
                SNAPSHOT, null, IncidentStatus.OPEN,
                input, "key"
        );

        input.put("key2", "value2");
        assertEquals(1, incident.metadata().size());
        assertThrows(UnsupportedOperationException.class, () ->
                incident.metadata().put("key3", "value3"));
    }

    @Test
    void blankMetadataKeysAreSkipped() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("  ", "value");
        input.put("valid", "data");

        Incident incident = new Incident(
                INCIDENT, IncidentType.PLAYER_DEATH, PLAYER,
                1000L, "world", new Coordinates(0, 0, 0),
                "", null, "",
                SNAPSHOT, null, IncidentStatus.OPEN,
                input, "key"
        );

        assertEquals(1, incident.metadata().size());
        assertEquals("data", incident.metadataValue("valid"));
    }

    @Test
    void nullStatusDefaultsToOpen() {
        Incident incident = new Incident(
                INCIDENT, IncidentType.PLAYER_DEATH, PLAYER,
                1000L, "world", new Coordinates(0, 0, 0),
                "", null, "",
                SNAPSHOT, null, null,
                Map.of(), "key"
        );

        assertEquals(IncidentStatus.OPEN, incident.status());
    }

    @Test
    void killerEntityKeyIsNormalizedToLowerCase() {
        Incident incident = new Incident(
                INCIDENT, IncidentType.PLAYER_DEATH, PLAYER,
                1000L, "world", new Coordinates(0, 0, 0),
                "", null, "MINECRAFT:SKELETON",
                SNAPSHOT, null, IncidentStatus.OPEN,
                Map.of(), "key"
        );

        assertEquals("minecraft:skeleton", incident.killerEntityKey());
    }

    @Test
    void negativeOccurredAtThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Incident(
                INCIDENT, IncidentType.PLAYER_DEATH, PLAYER,
                -1L, "world", new Coordinates(0, 0, 0),
                "", null, "",
                SNAPSHOT, null, IncidentStatus.OPEN,
                Map.of(), "key"
        ));
    }
}
