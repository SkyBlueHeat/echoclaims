package dev.worldecho.domain.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LotCompatibilityFingerprintTest {

    @Test
    void sameFingerprintsAreCompatible() {
        LotCompatibilityFingerprint a = LotCompatibilityFingerprint.builder()
                .providerId("minecraft")
                .material("minecraft:cobblestone")
                .build();
        LotCompatibilityFingerprint b = LotCompatibilityFingerprint.builder()
                .providerId("minecraft")
                .material("minecraft:cobblestone")
                .build();
        assertTrue(a.isCompatibleWith(b));
        assertEquals(a.serialize(), b.serialize());
    }

    @Test
    void differentMaterialsAreIncompatible() {
        LotCompatibilityFingerprint a = LotCompatibilityFingerprint.builder()
                .material("minecraft:cobblestone")
                .build();
        LotCompatibilityFingerprint b = LotCompatibilityFingerprint.builder()
                .material("minecraft:dirt")
                .build();
        assertFalse(a.isCompatibleWith(b));
    }

    @Test
    void amountDoesNotAffectFingerprint() {
        LotCompatibilityFingerprint a = LotCompatibilityFingerprint.builder()
                .material("minecraft:cobblestone")
                .build();
        LotCompatibilityFingerprint b = LotCompatibilityFingerprint.builder()
                .material("minecraft:cobblestone")
                .build();
        assertEquals(a.serialize(), b.serialize());
    }

    @Test
    void damageValueAffectsFingerprint() {
        LotCompatibilityFingerprint a = LotCompatibilityFingerprint.builder()
                .material("minecraft:stone")
                .damageValue(0)
                .build();
        LotCompatibilityFingerprint b = LotCompatibilityFingerprint.builder()
                .material("minecraft:stone")
                .damageValue(10)
                .build();
        assertFalse(a.isCompatibleWith(b));
    }

    @Test
    void enchantmentsAffectFingerprint() {
        LotCompatibilityFingerprint a = LotCompatibilityFingerprint.builder()
                .material("minecraft:stick")
                .enchantments(java.util.Map.of("minecraft:sharpness", 5))
                .build();
        LotCompatibilityFingerprint b = LotCompatibilityFingerprint.builder()
                .material("minecraft:stick")
                .enchantments(java.util.Map.of("minecraft:sharpness", 3))
                .build();
        assertFalse(a.isCompatibleWith(b));
    }

    @Test
    void serializeIsDeterministic() {
        LotCompatibilityFingerprint fp = LotCompatibilityFingerprint.builder()
                .providerId("minecraft")
                .contentKeyId("minecraft:cobblestone")
                .material("minecraft:cobblestone")
                .damageValue(0)
                .build();
        String s1 = fp.serialize();
        String s2 = fp.serialize();
        assertEquals(s1, s2);
    }

    @Test
    void normalizeIsCaseInsensitive() {
        LotCompatibilityFingerprint a = LotCompatibilityFingerprint.builder()
                .providerId("Minecraft")
                .material("MINECRAFT:COBBLESTONE")
                .build();
        LotCompatibilityFingerprint b = LotCompatibilityFingerprint.builder()
                .providerId("minecraft")
                .material("minecraft:cobblestone")
                .build();
        assertTrue(a.isCompatibleWith(b));
    }
}
