package dev.worldecho.domain.item;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnershipSubjectTest {

    @Test
    void playerSubjectHasCorrectType() {
        UUID uuid = UUID.randomUUID();
        OwnershipSubject subject = OwnershipSubject.player(uuid);
        assertEquals(OwnershipSubjectType.PLAYER, subject.type());
        assertEquals(uuid.toString(), subject.stableId());
    }

    @Test
    void playerSubjectWithDisplayName() {
        UUID uuid = UUID.randomUUID();
        OwnershipSubject subject = OwnershipSubject.player(uuid, "Steve");
        assertEquals("Steve", subject.displayName());
    }

    @Test
    void entitySubjectHasCorrectType() {
        UUID uuid = UUID.randomUUID();
        OwnershipSubject subject = OwnershipSubject.entity(uuid, "Zombie");
        assertEquals(OwnershipSubjectType.ENTITY, subject.type());
        assertEquals("Zombie", subject.displayName());
    }

    @Test
    void containerSubjectEncodesLocation() {
        OwnershipSubject subject = OwnershipSubject.container("world", 10, 64, -5);
        assertEquals(OwnershipSubjectType.CONTAINER, subject.type());
        assertEquals("world:10:64:-5", subject.stableId());
    }

    @Test
    void worldDropSubjectEncodesLocation() {
        OwnershipSubject subject = OwnershipSubject.worldDrop("world_nether", 0, 0, 0);
        assertEquals(OwnershipSubjectType.WORLD_DROP, subject.type());
        assertEquals("world_nether:0:0:0", subject.stableId());
    }

    @Test
    void systemSubjectRejectsBlankToken() {
        assertThrows(IllegalArgumentException.class, () -> OwnershipSubject.system(""));
        assertThrows(IllegalArgumentException.class, () -> OwnershipSubject.system("  "));
    }

    @Test
    void unknownSubjectIsValid() {
        OwnershipSubject subject = OwnershipSubject.unknown();
        assertEquals(OwnershipSubjectType.UNKNOWN, subject.type());
        assertTrue(subject.isValid());
    }

    @Test
    void isValidForPlayerWithValidUuid() {
        OwnershipSubject subject = OwnershipSubject.player(UUID.randomUUID());
        assertTrue(subject.isValid());
    }

    @Test
    void isValidForPlayerWithInvalidUuid() {
        OwnershipSubject subject = new OwnershipSubject(OwnershipSubjectType.PLAYER, "not-a-uuid", "");
        assertFalse(subject.isValid());
    }

    @Test
    void isValidForContainerWithNonEmptyId() {
        OwnershipSubject subject = OwnershipSubject.container("world", 1, 2, 3);
        assertTrue(subject.isValid());
    }

    @Test
    void isValidForContainerWithEmptyId() {
        OwnershipSubject subject = new OwnershipSubject(OwnershipSubjectType.CONTAINER, "", "");
        assertFalse(subject.isValid());
    }

    @Test
    void describeReturnsTypeAndId() {
        UUID uuid = UUID.randomUUID();
        OwnershipSubject subject = OwnershipSubject.player(uuid);
        assertEquals("player:" + uuid, subject.describe());
    }

    @Test
    void subjectTypeFromTokenRoundTrips() {
        for (OwnershipSubjectType type : OwnershipSubjectType.values()) {
            assertEquals(type, OwnershipSubjectType.fromToken(type.token()));
        }
    }

    @Test
    void subjectTypeFromTokenRejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> OwnershipSubjectType.fromToken("dragon"));
    }

    @Test
    void transitionReasonFromTokenRoundTrips() {
        for (OwnershipTransitionReason reason : OwnershipTransitionReason.values()) {
            assertEquals(reason, OwnershipTransitionReason.fromToken(reason.token()));
        }
    }

    @Test
    void transitionReasonFromTokenRejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> OwnershipTransitionReason.fromToken("explosion"));
    }

    @Test
    void displayNameDefaultsToEmptyForFactoryWithoutIt() {
        OwnershipSubject subject = OwnershipSubject.player(UUID.randomUUID());
        assertTrue(subject.displayName().isEmpty());
        assertTrue(subject.optionalDisplayName().isEmpty());
    }

    @Test
    void stableIdIsLowercased() {
        OwnershipSubject subject = new OwnershipSubject(OwnershipSubjectType.SYSTEM, "MySystem", "");
        assertEquals("mysystem", subject.stableId());
    }
}
