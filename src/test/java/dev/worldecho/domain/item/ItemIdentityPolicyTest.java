package dev.worldecho.domain.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemIdentityPolicyTest {

    @Test
    void plainStackableItemClassifiedAsLot() {
        ObservedItemDescriptor d = ObservedItemDescriptor.builder()
                .material("minecraft:cobblestone")
                .maxStackSize(64)
                .amount(32)
                .build();
        IdentityClassificationResult result = ItemIdentityPolicy.classify(d);
        assertTrue(result.isLot());
        assertTrue(result.reasons().contains("stackable"));
        assertEquals(0.9, result.confidence(), 0.001);
    }

    @Test
    void damageableItemClassifiedAsUnique() {
        ObservedItemDescriptor d = ObservedItemDescriptor.builder()
                .material("minecraft:diamond_sword")
                .maxStackSize(1)
                .amount(1)
                .damageable(true)
                .build();
        IdentityClassificationResult result = ItemIdentityPolicy.classify(d);
        assertTrue(result.isUnique());
        assertTrue(result.reasons().contains("damageable"));
    }

    @Test
    void enchantedItemClassifiedAsUnique() {
        ObservedItemDescriptor d = ObservedItemDescriptor.builder()
                .material("minecraft:stick")
                .maxStackSize(64)
                .amount(1)
                .enchantmentsPresent(true)
                .build();
        IdentityClassificationResult result = ItemIdentityPolicy.classify(d);
        assertTrue(result.isUnique());
        assertTrue(result.reasons().contains("enchantments-present"));
    }

    @Test
    void customNamedItemClassifiedAsUnique() {
        ObservedItemDescriptor d = ObservedItemDescriptor.builder()
                .material("minecraft:stone")
                .maxStackSize(64)
                .amount(1)
                .customNamePresent(true)
                .build();
        IdentityClassificationResult result = ItemIdentityPolicy.classify(d);
        assertTrue(result.isUnique());
        assertTrue(result.reasons().contains("custom-name-present"));
    }

    @Test
    void itemWithLoreClassifiedAsUnique() {
        ObservedItemDescriptor d = ObservedItemDescriptor.builder()
                .material("minecraft:stone")
                .maxStackSize(64)
                .amount(1)
                .lorePresent(true)
                .build();
        IdentityClassificationResult result = ItemIdentityPolicy.classify(d);
        assertTrue(result.isUnique());
    }

    @Test
    void itemWithCustomModelDataClassifiedAsUnique() {
        ObservedItemDescriptor d = ObservedItemDescriptor.builder()
                .material("minecraft:stone")
                .maxStackSize(64)
                .amount(1)
                .customModelDataPresent(true)
                .build();
        IdentityClassificationResult result = ItemIdentityPolicy.classify(d);
        assertTrue(result.isUnique());
    }

    @Test
    void existingIdentityStaysUnique() {
        ObservedItemDescriptor d = ObservedItemDescriptor.builder()
                .material("minecraft:cobblestone")
                .maxStackSize(64)
                .amount(32)
                .existingWorldEchoIdentity(true)
                .build();
        IdentityClassificationResult result = ItemIdentityPolicy.classify(d);
        assertTrue(result.isUnique());
        assertTrue(result.reasons().contains("existing-worldecho-identity"));
    }

    @Test
    void memoryLinkedItemClassifiedAsUnique() {
        ObservedItemDescriptor d = ObservedItemDescriptor.builder()
                .material("minecraft:cobblestone")
                .maxStackSize(64)
                .amount(32)
                .memoryLinked(true)
                .build();
        IdentityClassificationResult result = ItemIdentityPolicy.classify(d);
        assertTrue(result.isUnique());
    }

    @Test
    void storyLinkedItemClassifiedAsUnique() {
        ObservedItemDescriptor d = ObservedItemDescriptor.builder()
                .material("minecraft:cobblestone")
                .maxStackSize(64)
                .amount(32)
                .storyLinked(true)
                .build();
        IdentityClassificationResult result = ItemIdentityPolicy.classify(d);
        assertTrue(result.isUnique());
    }

    @Test
    void bindingIndicatesUniqueOverridesStackable() {
        ObservedItemDescriptor d = ObservedItemDescriptor.builder()
                .material("minecraft:cobblestone")
                .maxStackSize(64)
                .amount(32)
                .bindingIndicatesUnique(true)
                .build();
        IdentityClassificationResult result = ItemIdentityPolicy.classify(d);
        assertTrue(result.isUnique());
    }

    @Test
    void nonVanillaProviderWithoutPdcPrefersUnique() {
        ObservedItemDescriptor d = ObservedItemDescriptor.builder()
                .providerId("mythicmobs")
                .material("minecraft:stone")
                .maxStackSize(64)
                .amount(1)
                .build();
        IdentityClassificationResult result = ItemIdentityPolicy.classify(d);
        assertTrue(result.isUnique());
    }

    @Test
    void nonVanillaProviderWithPdcClassifiedAsUnique() {
        ObservedItemDescriptor d = ObservedItemDescriptor.builder()
                .providerId("mythicmobs")
                .material("minecraft:stone")
                .maxStackSize(64)
                .amount(1)
                .meaningfulProviderPdcPresent(true)
                .build();
        IdentityClassificationResult result = ItemIdentityPolicy.classify(d);
        assertTrue(result.isUnique());
    }

    @Test
    void uncertainDefaultsToUnique() {
        ObservedItemDescriptor d = ObservedItemDescriptor.builder()
                .material("minecraft:stone")
                .maxStackSize(64)
                .amount(1)
                .damageable(true)
                .build();
        IdentityClassificationResult result = ItemIdentityPolicy.classify(d);
        assertTrue(result.isUnique());
        assertEquals(1.0, result.confidence(), 0.001);
    }

    @Test
    void nullDescriptorThrows() {
        assertThrows(NullPointerException.class, () -> ItemIdentityPolicy.classify(null));
    }
}
