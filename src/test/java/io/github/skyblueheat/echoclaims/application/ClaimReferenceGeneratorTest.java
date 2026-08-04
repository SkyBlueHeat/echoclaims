package io.github.skyblueheat.echoclaims.application;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimReferenceGeneratorTest {

    @Test
    void generatesEightCharacterReference() {
        ClaimReferenceGenerator generator = new ClaimReferenceGenerator();
        String ref = generator.generate();
        assertEquals(8, ref.length());
    }

    @Test
    void usesOnlyAlphanumericChars() {
        ClaimReferenceGenerator generator = new ClaimReferenceGenerator();
        for (int i = 0; i < 100; i++) {
            String ref = generator.generate();
            assertTrue(ref.matches("[A-Z2-9]+"),
                    "Reference contains invalid characters: " + ref);
        }
    }

    @Test
    void generatesUniqueReferences() {
        ClaimReferenceGenerator generator = new ClaimReferenceGenerator();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String ref = generator.generate();
            assertTrue(seen.add(ref), "Duplicate reference generated: " + ref);
        }
    }

    @Test
    void checkerIsConsulted() {
        Set<String> used = new HashSet<>();
        used.add("AAAAAAAA");
        ClaimReferenceGenerator generator = new ClaimReferenceGenerator(
                ref -> !used.contains(ref)
        );
        String ref = generator.generate();
        assertFalse(used.contains(ref));
    }

    @Test
    void throwsAfterMaxAttemptsWhenAllCollide() {
        ClaimReferenceGenerator generator = new ClaimReferenceGenerator(
                new SecureRandom(), ref -> false
        );
        assertThrows(IllegalStateException.class, generator::generate);
    }
}
