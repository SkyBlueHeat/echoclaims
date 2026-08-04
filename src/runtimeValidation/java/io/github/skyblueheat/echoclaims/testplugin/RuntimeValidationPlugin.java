package io.github.skyblueheat.echoclaims.testplugin;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class RuntimeValidationPlugin extends JavaPlugin implements Listener {

    private final List<String> results = new ArrayList<>();
    private final List<String> errorLog = new ArrayList<>();
    private final AtomicInteger passed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private volatile UUID testPlayerUuid;
    private volatile UUID secondPlayerUuid;
    private volatile UUID firstIncidentId;
    private volatile UUID secondIncidentId;
    private volatile UUID firstSnapshotId;
    private volatile String firstClaimReference;
    private volatile UUID firstClaimId;
    private volatile String secondClaimReference;
    private volatile UUID secondClaimId;
    private volatile UUID secondSnapshotId;
    private final AtomicInteger deathCount = new AtomicInteger(0);
    private volatile CountDownLatch deathLatch;
    private volatile CountDownLatch respawnLatch;
    private volatile CountDownLatch secondPlayerLatch;
    private volatile boolean validationStarted = false;
    private Map<String, String> incidentEvidenceBefore;
    private Map<String, String> snapshotEvidenceBefore;
    private Map<String, String> snapshotItemsEvidenceBefore;

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Override
    public void onEnable() {
        getLogger().info("RuntimeValidationPlugin: waiting for player to join");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        writeResults();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().setOp(true);
        if (validationStarted && secondPlayerLatch != null
                && !event.getPlayer().getUniqueId().equals(testPlayerUuid)) {
            secondPlayerUuid = event.getPlayer().getUniqueId();
            getLogger().info("RuntimeValidationPlugin: second player joined - " + event.getPlayer().getName());
            secondPlayerLatch.countDown();
            return;
        }
        if (validationStarted) return;
        validationStarted = true;
        testPlayerUuid = event.getPlayer().getUniqueId();
        getLogger().info("RuntimeValidationPlugin: player joined - " + event.getPlayer().getName());

        new BukkitRunnable() {
            @Override
            public void run() {
                runValidation();
            }
        }.runTaskLater(this, 200L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getEntity().getUniqueId().equals(testPlayerUuid)) {
            deathCount.incrementAndGet();
            if (deathLatch != null) {
                deathLatch.countDown();
            }
            getLogger().info("Validation: detected player death #" + deathCount.get());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (event.getPlayer().getUniqueId().equals(testPlayerUuid)) {
            if (respawnLatch != null) {
                respawnLatch.countDown();
            }
            getLogger().info("Validation: detected player respawn");
        }
    }

    private void runValidation() {
        Thread validationThread = new Thread(() -> {
            try {
                check("EchoClaims_loaded", this::checkEchoClaimsLoaded);
                check("EchoClaims_ready", this::checkEchoClaimsReady);
                check("schema_migration_v1_v2", this::checkSchemaMigrations);
                check("schema_migration_v3_v4", this::checkSchemaMigrationsV3V4);
                check("no_WorldEcho_branding", this::checkNoWorldEchoBranding);
                check("player_given_items", this::givePlayerItems);
                check("first_death_triggered", this::triggerFirstDeath);
                check("first_incident_created", this::verifyFirstIncident);
                check("first_snapshot_contents", this::verifyFirstSnapshotContents);
                check("second_death_triggered", this::triggerSecondDeath);
                check("second_incident_created", this::verifySecondIncident);
                check("command_status", this::runCommandStatus);
                check("command_incidents", this::runCommandIncidents);
                check("command_incident", this::runCommandIncident);
                check("command_snapshot", this::runCommandSnapshot);
                check("command_ec_alias", this::runCommandEcAlias);
                check("keepInventory_false_incident_metadata", this::verifyKeepInventoryFalseMetadata);
                check("keepInventory_true_death", this::triggerKeepInventoryTrueDeath);
                check("keepInventory_true_items_retained", this::verifyKeepInventoryTrueItemsRetained);
                check("keepInventory_true_incident_metadata", this::verifyKeepInventoryTrueMetadata);
                check("restart_persistence", this::verifyRestartPersistence);
                check("migration_idempotency", this::verifyMigrationIdempotency);
                check("claim_claimable_list", this::runClaimClaimable);
                check("claim_create_by_index", this::runClaimCreateByIndex);
                check("claim_created_in_db", this::verifyClaimCreatedInDb);
                check("claim_list_command", this::runClaimListCommand);
                check("claim_view_command", this::runClaimViewCommand);
                check("claim_submit_command", this::runClaimSubmitCommand);
                check("claim_submitted_in_db", this::verifyClaimSubmittedInDb);
                check("claim_cancel_command", this::runClaimCancelCommand);
                check("claim_cancelled_in_db", this::verifyClaimCancelledInDb);
                check("claim_duplicate_rejected", this::verifyDuplicateClaimRejected);
                check("claim_audit_entries", this::verifyClaimAuditEntries);

                // ─── Security flow checks ───
                check("incident_captured_before_claims", this::verifyIncidentCapturedBeforeClaims);
                check("snapshot_captured_before_claims", this::verifySnapshotCapturedBeforeClaims);
                check("capture_evidence_state", this::captureEvidenceState);
                check("second_claim_for_security", this::createSecondClaimForSecurity);
                check("second_player_connected", this::connectSecondPlayer);
                check("cross_player_create_attempt", this::attemptCrossPlayerCreate);
                check("cross_player_create_rejected", this::verifyCrossPlayerCreateRejected);
                check("cross_player_create_no_claim_row", this::verifyNoClaimRowForSecondPlayer);
                check("cross_player_create_no_audit_row", this::verifyNoAuditRowForSecondPlayer);
                check("cross_player_view_attempt", this::attemptCrossPlayerView);
                check("cross_player_view_rejected", this::verifyCrossPlayerViewRejected);
                check("console_staff_list", this::runConsoleStaffList);
                check("console_staff_view", this::runConsoleStaffView);
                check("console_staff_audit", this::runConsoleStaffAudit);
                check("staff_commands_return_data", this::verifyStaffCommandsReturnData);
                check("cancelled_rejects_submit", this::verifyCancelledRejectsSubmit);
                check("cancelled_rejects_cancel", this::verifyCancelledRejectsCancel);
                check("terminal_no_extra_mutation", this::verifyTerminalNoExtraMutation);
                check("incident_evidence_unchanged", this::verifyIncidentEvidenceUnchanged);
                check("snapshot_evidence_unchanged", this::verifySnapshotEvidenceUnchanged);
                check("claim_reference_resolves_after_restart", this::verifyClaimReferenceResolvesAfterRestart);
                check("audit_history_resolves_after_restart", this::verifyAuditHistoryResolvesAfterRestart);

                check("no_exceptions", this::checkNoExceptions);
            } catch (Exception e) {
                results.add("FAIL:validation_framework:" + e.getClass().getSimpleName() + ": " + e.getMessage());
                failed.incrementAndGet();
            } finally {
                check("shutdown_drain", this::checkShutdownDrain);
                check("no_remaining_threads", this::checkNoRemainingThreads);
                writeResults();
                getLogger().info("RuntimeValidationPlugin: " + passed.get() + " passed, " + failed.get() + " failed, shutting down server");
                getServer().shutdown();
            }
        }, "EchoClaims-Validation");
        validationThread.setDaemon(true);
        validationThread.start();
    }

    private void check(String name, ThrowingRunnable validation) {
        try {
            validation.run();
            results.add("PASS:" + name);
            passed.incrementAndGet();
        } catch (Throwable t) {
            results.add("FAIL:" + name + ":" + t.getClass().getSimpleName() + ": " + t.getMessage());
            failed.incrementAndGet();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private void runOnMain(Runnable task) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Exception[] error = new Exception[1];
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Exception e) {
                    error[0] = e;
                } finally {
                    latch.countDown();
                }
            }
        }.runTask(this);
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new AssertionError("Main thread task timed out");
        }
        if (error[0] != null) throw error[0];
    }

    private Path getDbPath() {
        var plugin = getServer().getPluginManager().getPlugin("EchoClaims");
        require(plugin != null, "EchoClaims plugin not found");
        return plugin.getDataFolder().toPath().resolve("echoclaims.db");
    }

    private void checkEchoClaimsLoaded() throws Exception {
        runOnMain(() -> {
            var plugin = getServer().getPluginManager().getPlugin("EchoClaims");
            require(plugin != null, "EchoClaims plugin not found");
            require(plugin.isEnabled(), "EchoClaims plugin is not enabled");
        });
    }

    private void checkEchoClaimsReady() throws Exception {
        int attempts = 0;
        while (attempts < 60) {
            Path dbPath = getDbPath();
            if (Files.exists(dbPath)) {
                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
                     Statement stmt = conn.createStatement()) {
                    ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'");
                    List<String> tables = new ArrayList<>();
                    while (rs.next()) {
                        tables.add(rs.getString("name"));
                    }
                    rs.close();
                    if (tables.contains("incidents") && tables.contains("inventory_snapshots")) {
                        return;
                    }
                }
            }
            Thread.sleep(1000);
            attempts++;
        }
        throw new AssertionError("EchoClaims did not reach ready state within 60 seconds");
    }

    private void checkSchemaMigrations() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version ORDER BY version");
            List<Integer> versions = new ArrayList<>();
            while (rs.next()) {
                versions.add(rs.getInt("version"));
            }
            rs.close();
            require(versions.contains(1), "Migration v1 not applied");
            require(versions.contains(2), "Migration v2 not applied");
        }
    }

    private void checkSchemaMigrationsV3V4() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version ORDER BY version");
            List<Integer> versions = new ArrayList<>();
            while (rs.next()) {
                versions.add(rs.getInt("version"));
            }
            rs.close();
            require(versions.contains(3), "Migration v3 not applied");
            require(versions.contains(4), "Migration v4 not applied");

            ResultSet tblRs = stmt.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('claims','claim_audit_entries')");
            List<String> tables = new ArrayList<>();
            while (tblRs.next()) {
                tables.add(tblRs.getString("name"));
            }
            tblRs.close();
            require(tables.contains("claims"), "claims table not found");
            require(tables.contains("claim_audit_entries"), "claim_audit_entries table not found");

            ResultSet idxRs = stmt.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_claims_active_unique'");
            require(idxRs.next(), "idx_claims_active_unique index not found");
            idxRs.close();
        }
    }

    private void checkNoWorldEchoBranding() {
        var plugin = getServer().getPluginManager().getPlugin("EchoClaims");
        require(plugin != null, "EchoClaims plugin not found");
        require(plugin.getName().equals("EchoClaims"), "Plugin name is not EchoClaims");
        Path dataFolder = plugin.getDataFolder().toPath();
        if (Files.exists(dataFolder.resolve("config.yml"))) {
            try {
                String config = Files.readString(dataFolder.resolve("config.yml"));
                require(!config.toLowerCase().contains("worldecho"), "WorldEcho branding in config.yml");
            } catch (Exception e) {
                throw new AssertionError("Could not read config.yml: " + e.getMessage());
            }
        }
    }

    private void givePlayerItems() throws Exception {
        runOnMain(() -> {
            Player player = getServer().getPlayer(testPlayerUuid);
            require(player != null, "Test player not found");
            player.getWorld().setGameRule(GameRules.KEEP_INVENTORY, false);
            PlayerInventory inv = player.getInventory();
            inv.clear();
            inv.setItem(0, ItemStack.of(Material.STONE, 32));
            inv.setItem(1, ItemStack.of(Material.BREAD, 16));
            ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD, 1);
            sword.addEnchantment(Enchantment.SHARPNESS, 5);
            inv.setItem(2, sword);
            inv.setChestplate(ItemStack.of(Material.DIAMOND_CHESTPLATE, 1));
            inv.setBoots(ItemStack.of(Material.IRON_BOOTS, 1));
            inv.setItemInOffHand(ItemStack.of(Material.SHIELD, 1));
            player.updateInventory();
            getLogger().info("Validation: gave player items");
        });
    }

    private void triggerFirstDeath() throws Exception {
        deathLatch = new CountDownLatch(1);
        respawnLatch = new CountDownLatch(1);
        runOnMain(() -> {
            Player player = getServer().getPlayer(testPlayerUuid);
            require(player != null, "Test player not found");
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(0.0);
        });
        require(deathLatch.await(30, TimeUnit.SECONDS), "Death event not received");
        require(respawnLatch.await(30, TimeUnit.SECONDS), "Respawn event not received");
        Thread.sleep(3000);
    }

    private void verifyFirstIncident() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT id, incident_type, pre_event_snapshot_uuid, status FROM incidents " +
                    "WHERE player_uuid = '" + testPlayerUuid + "' ORDER BY occurred_at ASC LIMIT 1");
            require(rs.next(), "No incident found for test player");
            String idStr = rs.getString("id");
            require(idStr != null, "Incident id is null");
            firstIncidentId = UUID.fromString(idStr);
            String snapStr = rs.getString("pre_event_snapshot_uuid");
            require(snapStr != null, "Snapshot UUID is null");
            firstSnapshotId = UUID.fromString(snapStr);
            require("PLAYER_DEATH".equals(rs.getString("incident_type")), "Type is not PLAYER_DEATH");
            require("OPEN".equals(rs.getString("status")), "Status is not OPEN");
            rs.close();
            ResultSet countRs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM incidents WHERE player_uuid = '" + testPlayerUuid + "'");
            countRs.next();
            require(countRs.getInt(1) == 1, "Expected 1 incident, found " + countRs.getInt(1));
            countRs.close();
        }
    }

    private void verifyFirstSnapshotContents() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT capture_reason FROM inventory_snapshots WHERE id = '" + firstSnapshotId + "'");
            require(rs.next(), "Snapshot not found for id " + firstSnapshotId);
            require("PRE_DEATH".equals(rs.getString("capture_reason")), "Reason is not PRE_DEATH");
            rs.close();
            ResultSet itemsRs = stmt.executeQuery(
                    "SELECT slot, material_key FROM snapshot_items WHERE snapshot_uuid = '" + firstSnapshotId + "'");
            boolean hasArmor = false, hasOffhand = false, hasInventory = false, hasEnchanted = false;
            while (itemsRs.next()) {
                String slot = itemsRs.getString("slot");
                String material = itemsRs.getString("material_key");
                int slotNum = Integer.parseInt(slot);
                if (slotNum >= 36 && slotNum <= 39) hasArmor = true;
                if ("40".equals(slot)) hasOffhand = true;
                if (slotNum < 36) hasInventory = true;
                if ("minecraft:diamond_sword".equals(material)) hasEnchanted = true;
            }
            itemsRs.close();
            require(hasInventory, "No inventory items in snapshot");
            require(hasArmor, "No armor items in snapshot");
            require(hasOffhand, "No offhand item in snapshot");
            require(hasEnchanted, "No enchanted item in snapshot");
        }
    }

    private void triggerSecondDeath() throws Exception {
        runOnMain(() -> {
            Player player = getServer().getPlayer(testPlayerUuid);
            require(player != null, "Test player not found");
            PlayerInventory inv = player.getInventory();
            inv.clear();
            inv.setItem(0, ItemStack.of(Material.COBBLESTONE, 64));
            inv.setItem(1, ItemStack.of(Material.APPLE, 8));
            ItemStack axe = ItemStack.of(Material.DIAMOND_AXE, 1);
            axe.addEnchantment(Enchantment.EFFICIENCY, 3);
            inv.setItem(2, axe);
            inv.setHelmet(ItemStack.of(Material.GOLDEN_HELMET, 1));
            player.updateInventory();
            Location loc = player.getLocation();
            player.teleport(new Location(loc.getWorld(), loc.getX() + 100, loc.getY(), loc.getZ() + 100));
        });
        deathLatch = new CountDownLatch(1);
        respawnLatch = new CountDownLatch(1);
        runOnMain(() -> {
            Player player = getServer().getPlayer(testPlayerUuid);
            require(player != null, "Test player not found");
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(0.0);
        });
        require(deathLatch.await(30, TimeUnit.SECONDS), "Second death event not received");
        require(respawnLatch.await(30, TimeUnit.SECONDS), "Second respawn not received");
        Thread.sleep(3000);
    }

    private void verifySecondIncident() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet countRs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM incidents WHERE player_uuid = '" + testPlayerUuid + "'");
            countRs.next();
            require(countRs.getInt(1) == 2, "Expected 2 incidents (no dedup), found " + countRs.getInt(1));
            countRs.close();
        }
    }

    private void runCommandStatus() throws Exception {
        runOnMain(() -> getServer().dispatchCommand(getServer().getConsoleSender(), "echoclaims status"));
    }

    private void runCommandIncidents() throws Exception {
        runOnMain(() -> getServer().dispatchCommand(getServer().getConsoleSender(), "echoclaims incidents " + testPlayerUuid));
    }

    private void runCommandIncident() throws Exception {
        runOnMain(() -> getServer().dispatchCommand(getServer().getConsoleSender(), "echoclaims incident " + firstIncidentId));
    }

    private void runCommandSnapshot() throws Exception {
        runOnMain(() -> getServer().dispatchCommand(getServer().getConsoleSender(), "echoclaims snapshot " + firstSnapshotId));
    }

    private void runCommandEcAlias() throws Exception {
        runOnMain(() -> getServer().dispatchCommand(getServer().getConsoleSender(), "ec status"));
    }

    private void verifyKeepInventoryFalseMetadata() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT metadata FROM incidents WHERE player_uuid = '" + testPlayerUuid + "' ORDER BY occurred_at ASC LIMIT 2");
            while (rs.next()) {
                String metadata = rs.getString("metadata");
                require(metadata != null, "Incident metadata is null");
                require(metadata.contains("keepInventory=false"),
                        "keepInventory=false not recorded in incident metadata: " + metadata);
            }
            rs.close();
        }
    }

    private void triggerKeepInventoryTrueDeath() throws Exception {
        runOnMain(() -> {
            Player player = getServer().getPlayer(testPlayerUuid);
            require(player != null, "Test player not found");
            World world = player.getWorld();
            world.setGameRule(GameRules.KEEP_INVENTORY, true);
            PlayerInventory inv = player.getInventory();
            inv.clear();
            inv.setItem(0, ItemStack.of(Material.DIAMOND, 1));
            inv.setItem(1, ItemStack.of(Material.IRON_INGOT, 4));
            player.updateInventory();
            getLogger().info("Validation: set keepInventory=true and gave items for third death");
        });
        deathLatch = new CountDownLatch(1);
        respawnLatch = new CountDownLatch(1);
        runOnMain(() -> {
            Player player = getServer().getPlayer(testPlayerUuid);
            require(player != null, "Test player not found");
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(0.0);
        });
        require(deathLatch.await(30, TimeUnit.SECONDS), "Third death event not received");
        require(respawnLatch.await(30, TimeUnit.SECONDS), "Third respawn not received");
        Thread.sleep(3000);
    }

    private void verifyKeepInventoryTrueItemsRetained() throws Exception {
        runOnMain(() -> {
            Player player = getServer().getPlayer(testPlayerUuid);
            require(player != null, "Test player not found");
            PlayerInventory inv = player.getInventory();
            boolean hasDiamond = false;
            boolean hasIron = false;
            for (int i = 0; i < 36; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && !item.getType().isAir()) {
                    if (item.getType() == Material.DIAMOND) hasDiamond = true;
                    if (item.getType() == Material.IRON_INGOT) hasIron = true;
                }
            }
            require(hasDiamond, "Player did not retain diamond after respawn with keepInventory=true");
            require(hasIron, "Player did not retain iron ingot after respawn with keepInventory=true");
            getLogger().info("Validation: verified items retained with keepInventory=true");
        });
    }

    private void verifyKeepInventoryTrueMetadata() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT metadata FROM incidents WHERE player_uuid = '" + testPlayerUuid + "' ORDER BY occurred_at DESC LIMIT 1");
            require(rs.next(), "No third incident found");
            String metadata = rs.getString("metadata");
            require(metadata != null, "Third incident metadata is null");
            require(metadata.contains("keepInventory=true"),
                    "keepInventory=true not recorded in incident metadata: " + metadata);
            rs.close();
        }
        runOnMain(() -> {
            Player player = getServer().getPlayer(testPlayerUuid);
            require(player != null, "Test player not found");
            player.setGameMode(GameMode.CREATIVE);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            getLogger().info("Validation: set test player to creative mode to prevent further deaths");
        });
    }

    private void verifyRestartPersistence() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM incidents WHERE player_uuid = '" + testPlayerUuid + "'");
            rs.next();
            require(rs.getInt(1) >= 3, "Incidents not persisted, found " + rs.getInt(1));
            rs.close();
            ResultSet snapRs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM inventory_snapshots WHERE player_uuid = '" + testPlayerUuid + "'");
            snapRs.next();
            require(snapRs.getInt(1) >= 3, "Snapshots not persisted, found " + snapRs.getInt(1));
            snapRs.close();
        }
    }

    private void verifyMigrationIdempotency() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version ORDER BY version");
            List<Integer> versions = new ArrayList<>();
            while (rs.next()) {
                versions.add(rs.getInt("version"));
            }
            rs.close();
            require(versions.contains(1), "Migration v1 missing");
            require(versions.contains(2), "Migration v2 missing");
            require(versions.contains(3), "Migration v3 missing");
            require(versions.contains(4), "Migration v4 missing");
            require(versions.size() == 4, "Unexpected migration count: " + versions.size() + " versions: " + versions);
        }
    }

    private void runClaimClaimable() throws Exception {
        runOnMain(() -> {
            Player player = getServer().getPlayer(testPlayerUuid);
            require(player != null, "Test player not found for claim claimable");
            getServer().dispatchCommand(player, "echoclaims claim claimable");
        });
        Thread.sleep(2000);
    }

    private void runClaimCreateByIndex() throws Exception {
        runOnMain(() -> {
            Player player = getServer().getPlayer(testPlayerUuid);
            require(player != null, "Test player not found for claim create");
            getServer().dispatchCommand(player, "echoclaims claim create 1 Test claim from runtime validation");
        });
        Thread.sleep(5000);
    }

    private void verifyClaimCreatedInDb() throws Exception {
        Path dbPath = getDbPath();
        boolean found = false;
        for (int attempt = 0; attempt < 10; attempt++) {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                        "SELECT id, public_reference, status, incident_id FROM claims " +
                        "WHERE player_uuid = '" + testPlayerUuid + "' ORDER BY created_at ASC LIMIT 1");
                if (rs.next()) {
                    firstClaimId = UUID.fromString(rs.getString("id"));
                    firstClaimReference = rs.getString("public_reference");
                    require(firstClaimReference != null && firstClaimReference.length() == 8,
                            "Claim reference should be 8 chars, got: " + firstClaimReference);
                    require("DRAFT".equals(rs.getString("status")), "Claim status should be DRAFT");
                    require(rs.getString("incident_id") != null, "Claim incident_id is null");
                    rs.close();

                    ResultSet auditRs = stmt.executeQuery(
                            "SELECT action FROM claim_audit_entries WHERE claim_id = '" + firstClaimId + "'");
                    require(auditRs.next(), "No audit entry for claim creation");
                    require("CREATED".equals(auditRs.getString("action")), "First audit action should be CREATED");
                    auditRs.close();
                    found = true;
                    break;
                }
                rs.close();
            }
            Thread.sleep(1000);
        }
        require(found, "No claim found for test player after 10 retries");
    }

    private void runClaimListCommand() throws Exception {
        runOnMain(() -> {
            Player player = getServer().getPlayer(testPlayerUuid);
            require(player != null, "Test player not found for claim list");
            getServer().dispatchCommand(player, "echoclaims claim list");
        });
        Thread.sleep(2000);
    }

    private void runClaimViewCommand() throws Exception {
        runOnMain(() -> {
            Player player = getServer().getPlayer(testPlayerUuid);
            require(player != null, "Test player not found for claim view");
            getServer().dispatchCommand(player, "echoclaims claim view " + firstClaimReference);
        });
        Thread.sleep(2000);
    }

    private void runClaimSubmitCommand() throws Exception {
        runOnMain(() -> {
            Player player = getServer().getPlayer(testPlayerUuid);
            require(player != null, "Test player not found for claim submit");
            getServer().dispatchCommand(player, "echoclaims claim submit " + firstClaimReference);
        });
        Thread.sleep(5000);
    }

    private void verifyClaimSubmittedInDb() throws Exception {
        Path dbPath = getDbPath();
        boolean verified = false;
        for (int attempt = 0; attempt < 10; attempt++) {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                        "SELECT status, submitted_at FROM claims WHERE id = '" + firstClaimId + "'");
                if (rs.next() && "SUBMITTED".equals(rs.getString("status")) && rs.getLong("submitted_at") > 0) {
                    verified = true;
                    rs.close();
                    break;
                }
                rs.close();
            }
            Thread.sleep(1000);
        }
        require(verified, "Claim not found as SUBMITTED after 10 retries");
    }

    private void runClaimCancelCommand() throws Exception {
        runOnMain(() -> {
            Player player = getServer().getPlayer(testPlayerUuid);
            require(player != null, "Test player not found for claim cancel");
            getServer().dispatchCommand(player, "echoclaims claim cancel " + firstClaimReference);
        });
        Thread.sleep(5000);
    }

    private void verifyClaimCancelledInDb() throws Exception {
        Path dbPath = getDbPath();
        boolean verified = false;
        for (int attempt = 0; attempt < 10; attempt++) {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                        "SELECT status, cancelled_at FROM claims WHERE id = '" + firstClaimId + "'");
                if (rs.next() && "CANCELLED".equals(rs.getString("status")) && rs.getLong("cancelled_at") > 0) {
                    verified = true;
                    rs.close();
                    break;
                }
                rs.close();
            }
            Thread.sleep(1000);
        }
        require(verified, "Claim not found as CANCELLED after 10 retries");
    }

    private void verifyDuplicateClaimRejected() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM claims WHERE player_uuid = '" + testPlayerUuid + "' " +
                    "AND status IN ('DRAFT','SUBMITTED')");
            rs.next();
            require(rs.getInt(1) == 0, "Should have no active claims after cancellation, found " + rs.getInt(1));
            rs.close();
        }
    }

    private void verifyClaimAuditEntries() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT action FROM claim_audit_entries WHERE claim_id = '" + firstClaimId + "' ORDER BY recorded_at ASC");
            List<String> actions = new ArrayList<>();
            while (rs.next()) {
                actions.add(rs.getString("action"));
            }
            rs.close();
            require(actions.size() >= 3, "Expected at least 3 audit entries, found " + actions.size());
            require("CREATED".equals(actions.get(0)), "First audit should be CREATED, got " + actions.get(0));
            require(actions.contains("SUBMITTED"), "Audit should contain SUBMITTED");
            require(actions.contains("CANCELLED"), "Audit should contain CANCELLED");
        }
    }

    // ─── Security flow check methods ───

    private void verifyIncidentCapturedBeforeClaims() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT id FROM incidents WHERE player_uuid = '" + testPlayerUuid + "' " +
                    "AND occurred_at < (SELECT MIN(created_at) FROM claims WHERE player_uuid = '" + testPlayerUuid + "')");
            require(rs.next(), "No incident found that was created before the first claim");
            rs.close();
        }
    }

    private void verifySnapshotCapturedBeforeClaims() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM inventory_snapshots WHERE player_uuid = '" + testPlayerUuid + "' " +
                    "AND captured_at < (SELECT MIN(created_at) FROM claims WHERE player_uuid = '" + testPlayerUuid + "')");
            rs.next();
            require(rs.getInt(1) > 0, "No snapshots captured before first claim");
            rs.close();
            ResultSet itemsRs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM snapshot_items si " +
                    "INNER JOIN inventory_snapshots s ON si.snapshot_uuid = s.id " +
                    "WHERE s.player_uuid = '" + testPlayerUuid + "'");
            itemsRs.next();
            require(itemsRs.getInt(1) > 0, "No snapshot items captured before first claim");
            itemsRs.close();
        }
    }

    private void captureEvidenceState() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            incidentEvidenceBefore = new LinkedHashMap<>();
            ResultSet rs = stmt.executeQuery(
                    "SELECT id, incident_type, player_uuid, occurred_at, world_id, x, y, z, cause, " +
                    "killer_player_uuid, killer_entity_key, pre_event_snapshot_uuid, " +
                    "post_event_snapshot_uuid, status, metadata, deduplication_key " +
                    "FROM incidents WHERE player_uuid = '" + testPlayerUuid + "' ORDER BY occurred_at ASC");
            while (rs.next()) {
                String id = rs.getString("id");
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= 16; i++) {
                    if (i > 1) sb.append("|");
                    sb.append(rs.getString(i));
                }
                incidentEvidenceBefore.put(id, sb.toString());
            }
            rs.close();
            require(incidentEvidenceBefore.size() >= 2, "Expected at least 2 incidents for evidence comparison");

            snapshotEvidenceBefore = new LinkedHashMap<>();
            ResultSet snapRs = stmt.executeQuery(
                    "SELECT id, player_uuid, capture_reason, captured_at, world_id, x, y, z, " +
                    "game_mode, health, food_level, experience_level, total_experience, schema_version " +
                    "FROM inventory_snapshots WHERE player_uuid = '" + testPlayerUuid + "' ORDER BY captured_at ASC");
            while (snapRs.next()) {
                String id = snapRs.getString("id");
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= 13; i++) {
                    if (i > 1) sb.append("|");
                    sb.append(snapRs.getString(i));
                }
                snapshotEvidenceBefore.put(id, sb.toString());
            }
            snapRs.close();

            snapshotItemsEvidenceBefore = new LinkedHashMap<>();
            ResultSet itemsRs = stmt.executeQuery(
                    "SELECT si.id, si.snapshot_uuid, si.slot, si.slot_type, si.material_key, " +
                    "si.amount, si.serialized_data, si.content_identity, si.score, " +
                    "si.display_name, si.damage, si.max_durability, si.enchantments " +
                    "FROM snapshot_items si INNER JOIN inventory_snapshots s ON si.snapshot_uuid = s.id " +
                    "WHERE s.player_uuid = '" + testPlayerUuid + "' ORDER BY si.id ASC");
            while (itemsRs.next()) {
                String id = itemsRs.getString("id");
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= 13; i++) {
                    if (i > 1) sb.append("|");
                    sb.append(itemsRs.getString(i));
                }
                snapshotItemsEvidenceBefore.put(id, sb.toString());
            }
            itemsRs.close();
            require(snapshotItemsEvidenceBefore.size() > 0, "No snapshot items captured for evidence comparison");
        }
    }

    private void createSecondClaimForSecurity() throws Exception {
        getLogger().info("Validation: waiting for rate limit cooldown to expire before second claim");
        Thread.sleep(15000);
        runOnMain(() -> {
            Player player = getServer().getPlayer(testPlayerUuid);
            require(player != null, "Test player not found for second claim claimable");
            getServer().dispatchCommand(player, "echoclaims claim claimable");
        });
        Thread.sleep(2000);
        runOnMain(() -> {
            Player player = getServer().getPlayer(testPlayerUuid);
            require(player != null, "Test player not found for second claim create");
            getServer().dispatchCommand(player, "echoclaims claim create 1 Second claim for security flow");
        });
        Thread.sleep(5000);

        Path dbPath = getDbPath();
        boolean found = false;
        for (int attempt = 0; attempt < 10; attempt++) {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
                 Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(
                        "SELECT id, public_reference, incident_id FROM claims " +
                        "WHERE player_uuid = '" + testPlayerUuid + "' AND status = 'DRAFT' ORDER BY created_at DESC LIMIT 1");
                if (rs.next()) {
                    secondClaimId = UUID.fromString(rs.getString("id"));
                    secondClaimReference = rs.getString("public_reference");
                    secondIncidentId = UUID.fromString(rs.getString("incident_id"));
                    found = true;
                    rs.close();
                    break;
                }
                rs.close();
            }
            Thread.sleep(1000);
        }
        require(found, "Second claim not created for security flow");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet snapRs = stmt.executeQuery(
                    "SELECT pre_event_snapshot_uuid FROM incidents WHERE id = '" + secondIncidentId + "'");
            require(snapRs.next(), "Second incident not found");
            secondSnapshotId = UUID.fromString(snapRs.getString("pre_event_snapshot_uuid"));
            snapRs.close();
        }
    }

    private void connectSecondPlayer() throws Exception {
        secondPlayerLatch = new CountDownLatch(1);
        Path triggerFile = Path.of("need-second-bot");
        Files.writeString(triggerFile, "1");
        getLogger().info("RuntimeValidationPlugin: signaling for second bot connection");
        require(secondPlayerLatch.await(60, TimeUnit.SECONDS), "Second player did not join within 60 seconds");
        Thread.sleep(2000);
        runOnMain(() -> {
            Player player2 = getServer().getPlayer(secondPlayerUuid);
            require(player2 != null, "Second player not found after join");
            player2.setGameMode(GameMode.CREATIVE);
            player2.setHealth(20.0);
            player2.setFoodLevel(20);
            getLogger().info("Validation: set second player to creative mode to prevent deaths");
        });
    }

    private void attemptCrossPlayerCreate() throws Exception {
        require(secondPlayerUuid != null, "Second player UUID is null");
        require(secondIncidentId != null, "Second incident UUID is null");
        runOnMain(() -> {
            Player player2 = getServer().getPlayer(secondPlayerUuid);
            require(player2 != null, "Second player not found online");
            getServer().dispatchCommand(player2, "echoclaims claim create " + secondIncidentId + " cross-player attempt");
        });
        Thread.sleep(5000);
    }

    private void verifyCrossPlayerCreateRejected() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM claims WHERE player_uuid = '" + secondPlayerUuid + "'");
            rs.next();
            require(rs.getInt(1) == 0, "Cross-player claim was not rejected — found " + rs.getInt(1) + " claims for second player");
            rs.close();
        }
    }

    private void verifyNoClaimRowForSecondPlayer() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM claims WHERE player_uuid = '" + secondPlayerUuid + "'");
            rs.next();
            require(rs.getInt(1) == 0, "Claim row exists for second player — should not");
            rs.close();
        }
    }

    private void verifyNoAuditRowForSecondPlayer() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM claim_audit_entries ca " +
                    "INNER JOIN claims c ON ca.claim_id = c.id " +
                    "WHERE c.player_uuid = '" + secondPlayerUuid + "'");
            rs.next();
            require(rs.getInt(1) == 0, "Audit row exists for second player's rejected claim — should not");
            rs.close();
        }
    }

    private void attemptCrossPlayerView() throws Exception {
        require(secondPlayerUuid != null, "Second player UUID is null");
        require(secondClaimReference != null, "Second claim reference is null");
        runOnMain(() -> {
            Player player2 = getServer().getPlayer(secondPlayerUuid);
            require(player2 != null, "Second player not found online for view attempt");
            getServer().dispatchCommand(player2, "echoclaims claim view " + secondClaimReference);
        });
        Thread.sleep(3000);
    }

    private void verifyCrossPlayerViewRejected() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT player_uuid FROM claims WHERE public_reference = '" + secondClaimReference + "'");
            require(rs.next(), "Second claim not found in DB");
            require(rs.getString("player_uuid").equals(testPlayerUuid.toString()),
                    "Second claim ownership changed — cross-player view may have succeeded");
            rs.close();
        }
    }

    private void runConsoleStaffList() throws Exception {
        require(testPlayerUuid != null, "Test player UUID is null");
        runOnMain(() -> {
            getServer().dispatchCommand(getServer().getConsoleSender(),
                    "echoclaims claim staff-list " + testPlayerUuid);
        });
        Thread.sleep(3000);
    }

    private void runConsoleStaffView() throws Exception {
        require(secondClaimReference != null, "Second claim reference is null");
        runOnMain(() -> {
            getServer().dispatchCommand(getServer().getConsoleSender(),
                    "echoclaims claim staff-view " + secondClaimReference);
        });
        Thread.sleep(3000);
    }

    private void runConsoleStaffAudit() throws Exception {
        require(firstClaimReference != null, "First claim reference is null");
        runOnMain(() -> {
            getServer().dispatchCommand(getServer().getConsoleSender(),
                    "echoclaims claim staff-view " + firstClaimReference);
        });
        Thread.sleep(3000);
    }

    private void verifyStaffCommandsReturnData() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT id, public_reference, status FROM claims WHERE player_uuid = '" + testPlayerUuid + "' ORDER BY created_at ASC");
            List<String> refs = new ArrayList<>();
            List<String> statuses = new ArrayList<>();
            while (rs.next()) {
                refs.add(rs.getString("public_reference"));
                statuses.add(rs.getString("status"));
            }
            rs.close();
            require(refs.size() >= 2, "Expected at least 2 claims for test player, found " + refs.size());
            require(refs.contains(firstClaimReference), "First claim reference not found in DB");
            require(refs.contains(secondClaimReference), "Second claim reference not found in DB");
            require("CANCELLED".equals(statuses.get(0)), "First claim should be CANCELLED");
            require("DRAFT".equals(statuses.get(1)), "Second claim should be DRAFT");

            ResultSet auditRs = stmt.executeQuery(
                    "SELECT action FROM claim_audit_entries WHERE claim_id = '" + firstClaimId + "' ORDER BY recorded_at ASC");
            List<String> actions = new ArrayList<>();
            while (auditRs.next()) {
                actions.add(auditRs.getString("action"));
            }
            auditRs.close();
            require(actions.size() >= 3, "First claim should have at least 3 audit entries, found " + actions.size());
            require("CREATED".equals(actions.get(0)), "First audit should be CREATED");
            require(actions.contains("SUBMITTED"), "Audit should contain SUBMITTED");
            require(actions.contains("CANCELLED"), "Audit should contain CANCELLED");

            ResultSet audit2Rs = stmt.executeQuery(
                    "SELECT action FROM claim_audit_entries WHERE claim_id = '" + secondClaimId + "' ORDER BY recorded_at ASC");
            List<String> actions2 = new ArrayList<>();
            while (audit2Rs.next()) {
                actions2.add(audit2Rs.getString("action"));
            }
            audit2Rs.close();
            require(actions2.size() >= 1, "Second claim should have at least 1 audit entry");
            require("CREATED".equals(actions2.get(0)), "Second claim first audit should be CREATED");
        }
    }

    private void verifyCancelledRejectsSubmit() throws Exception {
        int auditCountBefore = getAuditCount(firstClaimId);
        runOnMain(() -> {
            Player player = getServer().getPlayer(testPlayerUuid);
            require(player != null, "Test player not found for cancelled submit test");
            getServer().dispatchCommand(player, "echoclaims claim submit " + firstClaimReference);
        });
        Thread.sleep(3000);
        int auditCountAfter = getAuditCount(firstClaimId);
        require(auditCountAfter == auditCountBefore,
                "Cancelled claim submit should not create new audit entries. Before=" + auditCountBefore + " After=" + auditCountAfter);

        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT status FROM claims WHERE id = '" + firstClaimId + "'");
            require(rs.next(), "First claim not found");
            require("CANCELLED".equals(rs.getString("status")), "Claim status changed from CANCELLED after rejected submit");
            rs.close();
        }
    }

    private void verifyCancelledRejectsCancel() throws Exception {
        int auditCountBefore = getAuditCount(firstClaimId);
        runOnMain(() -> {
            Player player = getServer().getPlayer(testPlayerUuid);
            require(player != null, "Test player not found for cancelled cancel test");
            getServer().dispatchCommand(player, "echoclaims claim cancel " + firstClaimReference);
        });
        Thread.sleep(3000);
        int auditCountAfter = getAuditCount(firstClaimId);
        require(auditCountAfter == auditCountBefore,
                "Cancelled claim second cancel should not create new audit entries. Before=" + auditCountBefore + " After=" + auditCountAfter);

        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT status, version FROM claims WHERE id = '" + firstClaimId + "'");
            require(rs.next(), "First claim not found");
            require("CANCELLED".equals(rs.getString("status")), "Claim status changed from CANCELLED after rejected cancel");
            rs.close();
        }
    }

    private void verifyTerminalNoExtraMutation() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT status, version, submitted_at, cancelled_at FROM claims WHERE id = '" + firstClaimId + "'");
            require(rs.next(), "First claim not found");
            require("CANCELLED".equals(rs.getString("status")), "Claim should still be CANCELLED");
            require(rs.getInt("version") == 2, "Claim version should be 2 (created=0, submitted=1, cancelled=2), got " + rs.getInt("version"));
            rs.close();

            ResultSet auditRs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM claim_audit_entries WHERE claim_id = '" + firstClaimId + "'");
            auditRs.next();
            require(auditRs.getInt(1) == 3, "Expected exactly 3 audit entries for first claim (CREATED, SUBMITTED, CANCELLED), found " + auditRs.getInt(1));
            auditRs.close();
        }
    }

    private void verifyIncidentEvidenceUnchanged() throws Exception {
        require(incidentEvidenceBefore != null, "Incident evidence state was not captured");
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT id, incident_type, player_uuid, occurred_at, world_id, x, y, z, cause, " +
                    "killer_player_uuid, killer_entity_key, pre_event_snapshot_uuid, " +
                    "post_event_snapshot_uuid, status, metadata, deduplication_key " +
                    "FROM incidents WHERE player_uuid = '" + testPlayerUuid + "' ORDER BY occurred_at ASC");
            Map<String, String> current = new LinkedHashMap<>();
            while (rs.next()) {
                String id = rs.getString("id");
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= 16; i++) {
                    if (i > 1) sb.append("|");
                    sb.append(rs.getString(i));
                }
                current.put(id, sb.toString());
            }
            rs.close();
            require(current.size() == incidentEvidenceBefore.size(),
                    "Incident count changed: before=" + incidentEvidenceBefore.size() + " after=" + current.size());
            for (var entry : incidentEvidenceBefore.entrySet()) {
                String currentVal = current.get(entry.getKey());
                require(currentVal != null, "Incident " + entry.getKey() + " disappeared from DB");
                require(currentVal.equals(entry.getValue()),
                        "Incident " + entry.getKey() + " evidence changed: before=" + entry.getValue() + " after=" + currentVal);
            }
        }
    }

    private void verifySnapshotEvidenceUnchanged() throws Exception {
        require(snapshotEvidenceBefore != null, "Snapshot evidence state was not captured");
        require(snapshotItemsEvidenceBefore != null, "Snapshot items evidence state was not captured");
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT id, player_uuid, capture_reason, captured_at, world_id, x, y, z, " +
                    "game_mode, health, food_level, experience_level, total_experience, schema_version " +
                    "FROM inventory_snapshots WHERE player_uuid = '" + testPlayerUuid + "' ORDER BY captured_at ASC");
            Map<String, String> current = new LinkedHashMap<>();
            while (rs.next()) {
                String id = rs.getString("id");
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= 13; i++) {
                    if (i > 1) sb.append("|");
                    sb.append(rs.getString(i));
                }
                current.put(id, sb.toString());
            }
            rs.close();
            require(current.size() == snapshotEvidenceBefore.size(),
                    "Snapshot count changed: before=" + snapshotEvidenceBefore.size() + " after=" + current.size());
            for (var entry : snapshotEvidenceBefore.entrySet()) {
                String currentVal = current.get(entry.getKey());
                require(currentVal != null, "Snapshot " + entry.getKey() + " disappeared from DB");
                require(currentVal.equals(entry.getValue()),
                        "Snapshot " + entry.getKey() + " evidence changed");
            }

            ResultSet itemsRs = stmt.executeQuery(
                    "SELECT si.id, si.snapshot_uuid, si.slot, si.slot_type, si.material_key, " +
                    "si.amount, si.serialized_data, si.content_identity, si.score, " +
                    "si.display_name, si.damage, si.max_durability, si.enchantments " +
                    "FROM snapshot_items si INNER JOIN inventory_snapshots s ON si.snapshot_uuid = s.id " +
                    "WHERE s.player_uuid = '" + testPlayerUuid + "' ORDER BY si.id ASC");
            Map<String, String> currentItems = new LinkedHashMap<>();
            while (itemsRs.next()) {
                String id = itemsRs.getString("id");
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= 13; i++) {
                    if (i > 1) sb.append("|");
                    sb.append(itemsRs.getString(i));
                }
                currentItems.put(id, sb.toString());
            }
            itemsRs.close();
            require(currentItems.size() == snapshotItemsEvidenceBefore.size(),
                    "Snapshot items count changed: before=" + snapshotItemsEvidenceBefore.size() + " after=" + currentItems.size());
            for (var entry : snapshotItemsEvidenceBefore.entrySet()) {
                String currentVal = currentItems.get(entry.getKey());
                require(currentVal != null, "Snapshot item " + entry.getKey() + " disappeared from DB");
                require(currentVal.equals(entry.getValue()),
                        "Snapshot item " + entry.getKey() + " evidence changed");
            }
        }
    }

    private void verifyClaimReferenceResolvesAfterRestart() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT id, public_reference, status, incident_id, player_uuid FROM claims " +
                    "WHERE public_reference = '" + secondClaimReference + "'");
            require(rs.next(), "Second claim reference " + secondClaimReference + " does not resolve in DB");
            require(rs.getString("id").equals(secondClaimId.toString()),
                    "Claim reference resolves to wrong claim ID");
            require("DRAFT".equals(rs.getString("status")), "Second claim should still be DRAFT");
            rs.close();
        }
    }

    private void verifyAuditHistoryResolvesAfterRestart() throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT action, actor_type, claim_version FROM claim_audit_entries " +
                    "WHERE claim_id = '" + firstClaimId + "' ORDER BY recorded_at ASC");
            List<String> actions = new ArrayList<>();
            while (rs.next()) {
                actions.add(rs.getString("action"));
            }
            rs.close();
            require(actions.size() == 3, "Audit history should have exactly 3 entries, found " + actions.size());
            require("CREATED".equals(actions.get(0)), "First audit should be CREATED");
            require("SUBMITTED".equals(actions.get(1)), "Second audit should be SUBMITTED");
            require("CANCELLED".equals(actions.get(2)), "Third audit should be CANCELLED");

            ResultSet rs2 = stmt.executeQuery(
                    "SELECT action FROM claim_audit_entries " +
                    "WHERE claim_id = '" + secondClaimId + "' ORDER BY recorded_at ASC");
            List<String> actions2 = new ArrayList<>();
            while (rs2.next()) {
                actions2.add(rs2.getString("action"));
            }
            rs2.close();
            require(actions2.size() >= 1, "Second claim audit history should have at least 1 entry");
            require("CREATED".equals(actions2.get(0)), "Second claim first audit should be CREATED");
        }
    }

    private int getAuditCount(UUID claimId) throws Exception {
        Path dbPath = getDbPath();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM claim_audit_entries WHERE claim_id = '" + claimId + "'");
            rs.next();
            int count = rs.getInt(1);
            rs.close();
            return count;
        }
    }

    private void checkNoExceptions() throws Exception {
        Path logFile = Path.of("logs/latest.log");
        if (Files.exists(logFile)) {
            String log = Files.readString(logFile);
            for (String line : log.split("\n")) {
                if ((line.contains("EchoClaims") || line.contains("echoclaims"))
                        && (line.contains("Exception") || line.contains("SEVERE"))
                        && !line.contains("RuntimeValidation")) {
                    errorLog.add(line.trim());
                }
            }
            require(errorLog.isEmpty(), "Exceptions found in log: " + String.join("; ", errorLog));
        }
    }

    private void checkShutdownDrain() throws Exception {
        runOnMain(() -> {
            var plugin = getServer().getPluginManager().getPlugin("EchoClaims");
            require(plugin != null && plugin.isEnabled(), "EchoClaims not enabled at shutdown check");
        });
    }

    private void checkNoRemainingThreads() {
        int count = 0;
        for (var thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().contains("echoclaims") && !thread.getName().contains("RuntimeValidation") && !thread.getName().contains("EchoClaims-Validation")) {
                count++;
            }
        }
        getLogger().info("Validation: " + count + " echoclaims thread(s) before shutdown");
    }

    private void writeResults() {
        try {
            Path resultsFile = Path.of("validation-results.txt");
            List<String> lines = new ArrayList<>(results);
            lines.add("SUMMARY:executed=" + (passed.get() + failed.get()) + ",passed=" + passed.get() + ",failed=" + failed.get() + ",skipped=0");
            if (!errorLog.isEmpty()) {
                lines.add("ERRORS:");
                lines.addAll(errorLog);
            }
            Files.write(resultsFile, lines);
        } catch (Exception e) {
            getLogger().severe("Failed to write validation results: " + e.getMessage());
        }
    }
}
