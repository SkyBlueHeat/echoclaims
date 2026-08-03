package io.github.skyblueheat.echoclaims.integration.bukkit;

import io.github.skyblueheat.echoclaims.integration.ItemSerializationException;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BukkitItemSerializer}.
 *
 * <p>This test class has two groups:</p>
 * <ol>
 *   <li><b>Error-handling and configuration tests</b> — run in CI without a server.
 *       Cover null/empty handling, corrupted payloads, unsupported format versions,
 *       format version introspection, and configuration validation.</li>
 *   <li><b>Round-trip tests</b> — gated by {@code -Dechoclaims.paper.runtime=true}.
 *       These require a running Paper server because {@code ItemStack.serializeAsBytes()}
 *       and {@code ItemStack.deserializeBytes()} depend on {@code RegistryAccess}.
 *       Run them during Paper runtime validation:
 *       {@code java -Dechoclaims.paper.runtime=true -jar paper.jar}</li>
 * </ol>
 */
class BukkitItemSerializerTest {

    private final BukkitItemSerializer serializer = new BukkitItemSerializer();

    // ─── Error-handling and configuration tests (run in CI) ───

    @Test
    void nullItemReturnsEmptyString() throws Exception {
        assertEquals("", serializer.serialize(null));
    }

    @Test
    void emptyStringDeserializesToNull() throws Exception {
        assertNull(serializer.deserialize(""));
    }

    @Test
    void nullStringDeserializesToNull() throws Exception {
        assertNull(serializer.deserialize(null));
    }

    @Test
    void corruptedPayloadThrowsException() {
        assertThrows(ItemSerializationException.class, () ->
                serializer.deserialize("!!!not_valid_base64!!!")
        );
    }

    @Test
    void truncatedPayloadThrowsException() {
        String truncated = java.util.Base64.getEncoder().encodeToString(new byte[]{0, 1});
        assertThrows(ItemSerializationException.class, () ->
                serializer.deserialize(truncated)
        );
    }

    @Test
    void unsupportedFormatVersionThrowsException() {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(8)
                .order(java.nio.ByteOrder.BIG_ENDIAN);
        buffer.putInt(999);
        buffer.putInt(0);
        String unsupported = java.util.Base64.getEncoder().encodeToString(buffer.array());

        assertThrows(ItemSerializationException.class, () ->
                serializer.deserialize(unsupported)
        );
    }

    @Test
    void formatVersionIsOne() {
        assertEquals(1, serializer.formatVersion());
    }

    @Test
    void maxPayloadBytesReturnsConfiguredValue() {
        BukkitItemSerializer custom = new BukkitItemSerializer(1024);
        assertEquals(1024, custom.maxPayloadBytes());
    }

    @Test
    void maxPayloadBytesEnforcesMinimum() {
        BukkitItemSerializer tiny = new BukkitItemSerializer(10);
        assertEquals(256, tiny.maxPayloadBytes());
    }

    @Test
    void peekFormatVersionReturnsMinusOneForEmpty() {
        assertEquals(-1, BukkitItemSerializer.peekFormatVersion(""));
    }

    @Test
    void peekFormatVersionReturnsMinusOneForCorrupted() {
        assertEquals(-1, BukkitItemSerializer.peekFormatVersion("!!!invalid!!!"));
    }

    @Test
    void peekFormatVersionReturnsMinusOneForTooShort() {
        String shortPayload = java.util.Base64.getEncoder().encodeToString(new byte[]{1, 2});
        assertEquals(-1, BukkitItemSerializer.peekFormatVersion(shortPayload));
    }

    @Test
    void peekFormatVersionReturnsVersionForValidHeader() {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(8)
                .order(java.nio.ByteOrder.BIG_ENDIAN);
        buffer.putInt(1);
        buffer.putInt(100);
        String validHeader = java.util.Base64.getEncoder().encodeToString(buffer.array());
        assertEquals(1, BukkitItemSerializer.peekFormatVersion(validHeader));
    }

    @Test
    void peekFormatVersionReturnsCustomVersion() {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(8)
                .order(java.nio.ByteOrder.BIG_ENDIAN);
        buffer.putInt(42);
        buffer.putInt(0);
        String customVersion = java.util.Base64.getEncoder().encodeToString(buffer.array());
        assertEquals(42, BukkitItemSerializer.peekFormatVersion(customVersion));
    }

    @Test
    void defaultMaxPayloadBytesIsConfigured() {
        assertTrue(serializer.maxPayloadBytes() > 0);
        assertTrue(serializer.maxPayloadBytes() >= 256);
    }

    // ─── Round-trip tests (require Paper server runtime) ───

    @Test
    @EnabledIfSystemProperty(named = "echoclaims.paper.runtime", matches = "true")
    void ordinaryVanillaItemRoundTrips() throws Exception {
        ItemStack item = ItemStack.of(Material.DIAMOND_SWORD, 1);
        String encoded = serializer.serialize(item);
        assertNotNull(encoded);
        assertFalse(encoded.isEmpty());

        ItemStack decoded = serializer.deserialize(encoded);
        assertNotNull(decoded);
        assertEquals(Material.DIAMOND_SWORD, decoded.getType());
        assertEquals(1, decoded.getAmount());
    }

    @Test
    @EnabledIfSystemProperty(named = "echoclaims.paper.runtime", matches = "true")
    void enchantmentsArePreservedInRoundTrip() throws Exception {
        ItemStack item = ItemStack.of(Material.DIAMOND_SWORD, 1);
        item.addEnchantment(Enchantment.SHARPNESS, 5);
        item.addEnchantment(Enchantment.UNBREAKING, 3);

        String encoded = serializer.serialize(item);
        ItemStack decoded = serializer.deserialize(encoded);

        assertNotNull(decoded);
        assertEquals(5, decoded.getEnchantmentLevel(Enchantment.SHARPNESS));
        assertEquals(3, decoded.getEnchantmentLevel(Enchantment.UNBREAKING));
    }

    @Test
    @EnabledIfSystemProperty(named = "echoclaims.paper.runtime", matches = "true")
    void customDisplayNameIsPreservedInRoundTrip() throws Exception {
        ItemStack item = ItemStack.of(Material.DIAMOND_SWORD, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Excalibur"));
        item.setItemMeta(meta);

        String encoded = serializer.serialize(item);
        ItemStack decoded = serializer.deserialize(encoded);

        assertNotNull(decoded);
        assertNotNull(decoded.getItemMeta());
        assertTrue(decoded.getItemMeta().hasDisplayName());
    }

    @Test
    @EnabledIfSystemProperty(named = "echoclaims.paper.runtime", matches = "true")
    void loreIsPreservedInRoundTrip() throws Exception {
        ItemStack item = ItemStack.of(Material.WRITTEN_BOOK, 1);
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(
                net.kyori.adventure.text.Component.text("Line 1"),
                net.kyori.adventure.text.Component.text("Line 2")
        ));
        item.setItemMeta(meta);

        String encoded = serializer.serialize(item);
        ItemStack decoded = serializer.deserialize(encoded);

        assertNotNull(decoded);
        assertNotNull(decoded.getItemMeta());
        assertTrue(decoded.getItemMeta().hasLore());
        List<net.kyori.adventure.text.Component> lore = decoded.getItemMeta().lore();
        assertEquals(2, lore.size());
    }

    @Test
    @EnabledIfSystemProperty(named = "echoclaims.paper.runtime", matches = "true")
    void damageIsPreservedInRoundTrip() throws Exception {
        ItemStack item = ItemStack.of(Material.DIAMOND_SWORD, 1);
        ItemMeta meta = item.getItemMeta();
        ((Damageable) meta).setDamage(500);
        item.setItemMeta(meta);

        String encoded = serializer.serialize(item);
        ItemStack decoded = serializer.deserialize(encoded);

        assertNotNull(decoded);
        ItemMeta decodedMeta = decoded.getItemMeta();
        assertNotNull(decodedMeta);
        assertTrue(decodedMeta instanceof Damageable);
        assertEquals(500, ((Damageable) decodedMeta).getDamage());
    }

    @Test
    @EnabledIfSystemProperty(named = "echoclaims.paper.runtime", matches = "true")
    void unbreakableMetadataIsPreservedInRoundTrip() throws Exception {
        ItemStack item = ItemStack.of(Material.NETHERITE_PICKAXE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        item.setItemMeta(meta);

        String encoded = serializer.serialize(item);
        ItemStack decoded = serializer.deserialize(encoded);

        assertNotNull(decoded);
        ItemMeta decodedMeta = decoded.getItemMeta();
        assertNotNull(decodedMeta);
        assertTrue(decodedMeta.isUnbreakable());
    }

    @Test
    @EnabledIfSystemProperty(named = "echoclaims.paper.runtime", matches = "true")
    void potionMetadataIsPreservedInRoundTrip() throws Exception {
        ItemStack item = ItemStack.of(Material.POTION, 1);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.addCustomEffect(new PotionEffect(PotionEffectType.STRENGTH, 3600, 1), true);
        item.setItemMeta(meta);

        String encoded = serializer.serialize(item);
        ItemStack decoded = serializer.deserialize(encoded);

        assertNotNull(decoded);
        ItemMeta decodedMeta = decoded.getItemMeta();
        assertNotNull(decodedMeta);
        assertTrue(decodedMeta instanceof PotionMeta);
        PotionMeta decodedPotion = (PotionMeta) decodedMeta;
        assertTrue(decodedPotion.hasCustomEffects());
        assertTrue(decodedPotion.hasCustomEffect(PotionEffectType.STRENGTH));
    }

    @Test
    @EnabledIfSystemProperty(named = "echoclaims.paper.runtime", matches = "true")
    void armorItemRoundTrips() throws Exception {
        ItemStack item = ItemStack.of(Material.DIAMOND_CHESTPLATE, 1);
        item.addEnchantment(Enchantment.PROTECTION, 4);

        String encoded = serializer.serialize(item);
        ItemStack decoded = serializer.deserialize(encoded);

        assertNotNull(decoded);
        assertEquals(Material.DIAMOND_CHESTPLATE, decoded.getType());
        assertEquals(4, decoded.getEnchantmentLevel(Enchantment.PROTECTION));
    }

    @Test
    @EnabledIfSystemProperty(named = "echoclaims.paper.runtime", matches = "true")
    void offhandCompatibleItemRoundTrips() throws Exception {
        ItemStack item = ItemStack.of(Material.SHIELD, 1);

        String encoded = serializer.serialize(item);
        ItemStack decoded = serializer.deserialize(encoded);

        assertNotNull(decoded);
        assertEquals(Material.SHIELD, decoded.getType());
        assertEquals(1, decoded.getAmount());
    }

    @Test
    @EnabledIfSystemProperty(named = "echoclaims.paper.runtime", matches = "true")
    void oversizedPayloadThrowsException() throws Exception {
        BukkitItemSerializer smallSerializer = new BukkitItemSerializer(300);

        ItemStack item = ItemStack.of(Material.DIAMOND_BLOCK, 64);
        assertThrows(ItemSerializationException.class, () ->
                smallSerializer.serialize(item)
        );
    }

    @Test
    @EnabledIfSystemProperty(named = "echoclaims.paper.runtime", matches = "true")
    void amountIsPreservedInRoundTrip() throws Exception {
        ItemStack item = ItemStack.of(Material.STONE, 42);
        String encoded = serializer.serialize(item);
        ItemStack decoded = serializer.deserialize(encoded);
        assertNotNull(decoded);
        assertEquals(42, decoded.getAmount());
    }

    @Test
    @EnabledIfSystemProperty(named = "echoclaims.paper.runtime", matches = "true")
    void airItemReturnsEmptyString() throws Exception {
        ItemStack air = ItemStack.of(Material.AIR, 1);
        assertEquals("", serializer.serialize(air));
    }

    @Test
    @EnabledIfSystemProperty(named = "echoclaims.paper.runtime", matches = "true")
    void differentItemsProduceDifferentSerializations() throws Exception {
        ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD, 1);
        ItemStack pickaxe = ItemStack.of(Material.DIAMOND_PICKAXE, 1);

        String swordEncoded = serializer.serialize(sword);
        String pickaxeEncoded = serializer.serialize(pickaxe);

        assertFalse(swordEncoded.equals(pickaxeEncoded));
    }

    @Test
    @EnabledIfSystemProperty(named = "echoclaims.paper.runtime", matches = "true")
    void peekFormatVersionReturnsOneForValidData() throws Exception {
        ItemStack item = ItemStack.of(Material.STONE, 1);
        String encoded = serializer.serialize(item);
        assertEquals(1, BukkitItemSerializer.peekFormatVersion(encoded));
    }
}
