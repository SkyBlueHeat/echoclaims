package io.github.skyblueheat.echoclaims.domain.snapshot;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotItemTest {

    @Test
    void validItemIsConstructed() {
        SnapshotItem item = new SnapshotItem(
                "0", "minecraft:diamond_sword", 1,
                "base64data", "oraxen:mythic_blade", 42,
                "Excalibur", 10, 1561,
                Map.of("minecraft:sharpness", 5)
        );

        assertEquals("0", item.slot());
        assertEquals("minecraft:diamond_sword", item.materialKey());
        assertEquals(1, item.amount());
        assertEquals("oraxen:mythic_blade", item.contentIdentity());
        assertEquals(42, item.score());
        assertEquals("Excalibur", item.displayName());
        assertEquals(10, item.damage());
        assertEquals(1561, item.maxDurability());
        assertTrue(item.hasEnchantments());
        assertTrue(item.hasDisplayName());
        assertTrue(item.hasContentIdentity());
        assertFalse(item.isAir());
    }

    @Test
    void blankSlotThrows() {
        assertThrows(IllegalArgumentException.class, () -> new SnapshotItem(
                "  ", "minecraft:stone", 1, "", "", 0, "", 0, 0, Map.of()
        ));
    }

    @Test
    void blankMaterialKeyThrows() {
        assertThrows(IllegalArgumentException.class, () -> new SnapshotItem(
                "0", "  ", 1, "", "", 0, "", 0, 0, Map.of()
        ));
    }

    @Test
    void materialKeyWithoutNamespaceGetsMinecraftPrefix() {
        SnapshotItem item = new SnapshotItem(
                "0", "stone", 1, "", "", 0, "", 0, 0, Map.of()
        );

        assertEquals("minecraft:stone", item.materialKey());
    }

    @Test
    void airMaterialIsAir() {
        SnapshotItem item = new SnapshotItem(
                "0", "minecraft:air", 1, "", "", 0, "", 0, 0, Map.of()
        );
        assertTrue(item.isAir());
    }

    @Test
    void zeroAmountIsAir() {
        SnapshotItem item = new SnapshotItem(
                "0", "minecraft:stone", 0, "", "", 0, "", 0, 0, Map.of()
        );
        assertTrue(item.isAir());
    }

    @Test
    void negativeAmountIsClampedToZero() {
        SnapshotItem item = new SnapshotItem(
                "0", "minecraft:stone", -5, "", "", 0, "", 0, 0, Map.of()
        );
        assertEquals(0, item.amount());
    }

    @Test
    void negativeScoreIsClampedToZero() {
        SnapshotItem item = new SnapshotItem(
                "0", "minecraft:stone", 1, "", "", -10, "", 0, 0, Map.of()
        );
        assertEquals(0, item.score());
    }

    @Test
    void enchantmentsAreDefensivelyCopiedAndSorted() {
        Map<String, Integer> input = Map.of(
                "minecraft:unbreaking", 3,
                "minecraft:sharpness", 5
        );
        SnapshotItem item = new SnapshotItem(
                "0", "minecraft:diamond_sword", 1, "", "", 0, "", 0, 0, input
        );

        assertEquals(2, item.enchantments().size());
        assertEquals(5, item.enchantments().get("minecraft:sharpness"));
        assertThrows(UnsupportedOperationException.class, () ->
                item.enchantments().put("test", 1));
    }

    @Test
    void nullEnchantmentsProducesEmptyMap() {
        SnapshotItem item = new SnapshotItem(
                "0", "minecraft:stone", 1, "", "", 0, "", 0, 0, null
        );
        assertFalse(item.hasEnchantments());
        assertTrue(item.enchantments().isEmpty());
    }

    @Test
    void slotIsNormalizedToLowerCase() {
        SnapshotItem item = new SnapshotItem(
                "CURSOR", "minecraft:stone", 1, "", "", 0, "", 0, 0, Map.of()
        );
        assertEquals("cursor", item.slot());
    }
}
