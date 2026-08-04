package io.github.skyblueheat.echoclaims.testplugin;

import io.github.skyblueheat.echoclaims.integration.bukkit.BukkitItemSerializer;
import io.github.skyblueheat.echoclaims.integration.ItemSerializationException;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Paper plugin that runs BukkitItemSerializer round-trip tests
 * on the main thread during {@code onEnable()}, writes results to a file,
 * and then stops the server.
 *
 * <p>The results file is written to {@code results.txt} in the server
 * working directory. Each line is either {@code PASS:<testName>} or
 * {@code FAIL:<testName>:<errorMessage>}. The last line is
 * {@code SUMMARY:executed=N,passed=N,failed=N,skipped=N}.</p>
 */
public final class SerializationTestPlugin extends JavaPlugin {

    private final List<String> results = new ArrayList<>();
    private int passed = 0;
    private int failed = 0;

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Override
    public void onEnable() {
        getLogger().info("SerializationTestPlugin: starting round-trip tests");

        runTest("ordinaryVanillaItem", this::ordinaryVanillaItem);
        runTest("enchantments", this::enchantments);
        runTest("customDisplayName", this::customDisplayName);
        runTest("lore", this::lore);
        runTest("damage", this::damage);
        runTest("unbreakable", this::unbreakable);
        runTest("potionMetadata", this::potionMetadata);
        runTest("armorItem", this::armorItem);
        runTest("offhandItem", this::offhandItem);
        runTest("amountPreservation", this::amountPreservation);
        runTest("oversizedPayload", this::oversizedPayload);
        runTest("airItem", this::airItem);
        runTest("serializationUniqueness", this::serializationUniqueness);
        runTest("formatVersionInspection", this::formatVersionInspection);

        writeResults();

        getLogger().info("SerializationTestPlugin: " + passed + " passed, " + failed + " failed, shutting down server");
        getServer().shutdown();
    }

    private void runTest(String name, ThrowingRunnable test) {
        try {
            test.run();
            results.add("PASS:" + name);
            passed++;
        } catch (Throwable t) {
            results.add("FAIL:" + name + ":" + t.getClass().getSimpleName() + ": " + t.getMessage());
            failed++;
        }
    }

    private void ordinaryVanillaItem() throws Exception {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        ItemStack item = ItemStack.of(Material.DIAMOND_SWORD, 1);
        String encoded = serializer.serialize(item);
        assert !encoded.isEmpty() : "encoded should not be empty";
        ItemStack decoded = serializer.deserialize(encoded);
        assert decoded != null : "decoded should not be null";
        assert decoded.getType() == Material.DIAMOND_SWORD : "type mismatch";
        assert decoded.getAmount() == 1 : "amount mismatch";
    }

    private void enchantments() throws Exception {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        ItemStack item = ItemStack.of(Material.DIAMOND_SWORD, 1);
        item.addEnchantment(Enchantment.SHARPNESS, 5);
        item.addEnchantment(Enchantment.UNBREAKING, 3);
        String encoded = serializer.serialize(item);
        ItemStack decoded = serializer.deserialize(encoded);
        assert decoded != null;
        assert decoded.getEnchantmentLevel(Enchantment.SHARPNESS) == 5 : "Sharpness mismatch";
        assert decoded.getEnchantmentLevel(Enchantment.UNBREAKING) == 3 : "Unbreaking mismatch";
    }

    private void customDisplayName() throws Exception {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        ItemStack item = ItemStack.of(Material.DIAMOND_SWORD, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Excalibur"));
        item.setItemMeta(meta);
        String encoded = serializer.serialize(item);
        ItemStack decoded = serializer.deserialize(encoded);
        assert decoded != null;
        assert decoded.getItemMeta().hasDisplayName() : "display name not preserved";
    }

    private void lore() throws Exception {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        ItemStack item = ItemStack.of(Material.WRITTEN_BOOK, 1);
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(
                net.kyori.adventure.text.Component.text("Line 1"),
                net.kyori.adventure.text.Component.text("Line 2")
        ));
        item.setItemMeta(meta);
        String encoded = serializer.serialize(item);
        ItemStack decoded = serializer.deserialize(encoded);
        assert decoded != null;
        assert decoded.getItemMeta().hasLore() : "lore not preserved";
        assert decoded.getItemMeta().lore().size() == 2 : "lore line count mismatch";
    }

    private void damage() throws Exception {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        ItemStack item = ItemStack.of(Material.DIAMOND_SWORD, 1);
        ItemMeta meta = item.getItemMeta();
        ((Damageable) meta).setDamage(500);
        item.setItemMeta(meta);
        String encoded = serializer.serialize(item);
        ItemStack decoded = serializer.deserialize(encoded);
        assert decoded != null;
        ItemMeta decodedMeta = decoded.getItemMeta();
        assert decodedMeta instanceof Damageable : "not Damageable";
        assert ((Damageable) decodedMeta).getDamage() == 500 : "damage mismatch";
    }

    private void unbreakable() throws Exception {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        ItemStack item = ItemStack.of(Material.NETHERITE_PICKAXE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        String encoded = serializer.serialize(item);
        ItemStack decoded = serializer.deserialize(encoded);
        assert decoded != null;
        assert decoded.getItemMeta().isUnbreakable() : "unbreakable not preserved";
    }

    private void potionMetadata() throws Exception {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        ItemStack item = ItemStack.of(Material.POTION, 1);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.addCustomEffect(new PotionEffect(PotionEffectType.STRENGTH, 3600, 1), true);
        item.setItemMeta(meta);
        String encoded = serializer.serialize(item);
        ItemStack decoded = serializer.deserialize(encoded);
        assert decoded != null;
        ItemMeta decodedMeta = decoded.getItemMeta();
        assert decodedMeta instanceof PotionMeta : "not PotionMeta";
        PotionMeta decodedPotion = (PotionMeta) decodedMeta;
        assert decodedPotion.hasCustomEffects() : "no custom effects";
        assert decodedPotion.hasCustomEffect(PotionEffectType.STRENGTH) : "Strength effect missing";
    }

    private void armorItem() throws Exception {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        ItemStack item = ItemStack.of(Material.DIAMOND_CHESTPLATE, 1);
        item.addEnchantment(Enchantment.PROTECTION, 4);
        String encoded = serializer.serialize(item);
        ItemStack decoded = serializer.deserialize(encoded);
        assert decoded != null;
        assert decoded.getType() == Material.DIAMOND_CHESTPLATE : "type mismatch";
        assert decoded.getEnchantmentLevel(Enchantment.PROTECTION) == 4 : "Protection mismatch";
    }

    private void offhandItem() throws Exception {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        ItemStack item = ItemStack.of(Material.SHIELD, 1);
        String encoded = serializer.serialize(item);
        ItemStack decoded = serializer.deserialize(encoded);
        assert decoded != null;
        assert decoded.getType() == Material.SHIELD : "type mismatch";
        assert decoded.getAmount() == 1 : "amount mismatch";
    }

    private void amountPreservation() throws Exception {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        ItemStack item = ItemStack.of(Material.STONE, 42);
        String encoded = serializer.serialize(item);
        ItemStack decoded = serializer.deserialize(encoded);
        assert decoded != null;
        assert decoded.getAmount() == 42 : "amount mismatch";
    }

    private void oversizedPayload() throws Exception {
        BukkitItemSerializer tinySerializer = new BukkitItemSerializer(256);
        ItemStack item = ItemStack.of(Material.DIAMOND_SWORD, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Very long display name that should push the serialized size over the 256 byte limit"));
        meta.lore(List.of(
                net.kyori.adventure.text.Component.text("Lore line 1 with some text"),
                net.kyori.adventure.text.Component.text("Lore line 2 with more text"),
                net.kyori.adventure.text.Component.text("Lore line 3 with even more text")
        ));
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        item.addEnchantment(Enchantment.SHARPNESS, 5);
        item.addEnchantment(Enchantment.UNBREAKING, 3);
        try {
            tinySerializer.serialize(item);
            throw new AssertionError("should have thrown ItemSerializationException");
        } catch (ItemSerializationException expected) {
            // expected
        }
    }

    private void airItem() throws Exception {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        ItemStack air = ItemStack.of(Material.AIR, 1);
        String encoded = serializer.serialize(air);
        assert encoded.isEmpty() : "air should serialize to empty string";
    }

    private void serializationUniqueness() throws Exception {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD, 1);
        ItemStack pickaxe = ItemStack.of(Material.DIAMOND_PICKAXE, 1);
        String swordEncoded = serializer.serialize(sword);
        String pickaxeEncoded = serializer.serialize(pickaxe);
        assert !swordEncoded.equals(pickaxeEncoded) : "different items should have different serializations";
    }

    private void formatVersionInspection() throws Exception {
        BukkitItemSerializer serializer = new BukkitItemSerializer();
        ItemStack item = ItemStack.of(Material.STONE, 1);
        String encoded = serializer.serialize(item);
        int version = BukkitItemSerializer.peekFormatVersion(encoded);
        assert version == 1 : "format version should be 1, got " + version;
    }

    private void writeResults() {
        try {
            Path resultsFile = Path.of("results.txt");
            List<String> lines = new ArrayList<>(results);
            lines.add("SUMMARY:executed=" + (passed + failed) + ",passed=" + passed + ",failed=" + failed + ",skipped=0");
            Files.write(resultsFile, lines);
        } catch (Exception e) {
            getLogger().severe("Failed to write results: " + e.getMessage());
        }
    }
}
