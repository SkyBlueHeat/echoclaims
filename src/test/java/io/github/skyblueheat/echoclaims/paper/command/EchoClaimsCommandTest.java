package io.github.skyblueheat.echoclaims.paper.command;

import io.github.skyblueheat.echoclaims.application.EvidenceLookupService;
import io.github.skyblueheat.echoclaims.application.StatusService;
import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentStatus;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentType;
import io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import io.github.skyblueheat.echoclaims.domain.snapshot.SnapshotItem;
import io.github.skyblueheat.echoclaims.paper.message.MessageService;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EchoClaimsCommandTest {

    private EchoClaimsCommand command;
    private TestMessageService messages;
    private TestSender consoleSender;
    private TestSender authorizedPlayer;
    private TestSender unauthorizedPlayer;
    private ExecutorService queryExecutor;
    private List<Runnable> syncTasks;
    private TestEvidenceLookup evidenceLookup;
    private StatusService statusService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        messages = new TestMessageService();
        consoleSender = new TestSender(true, true);
        authorizedPlayer = new TestSender(false, true);
        unauthorizedPlayer = new TestSender(false, false);
        syncTasks = new ArrayList<>();
        evidenceLookup = new TestEvidenceLookup();
        statusService = null;

        queryExecutor = Executors.newSingleThreadExecutor();

        command = new EchoClaimsCommand(
                null,
                () -> messages,
                () -> statusService,
                () -> evidenceLookup,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> io.github.skyblueheat.echoclaims.config.EchoClaimsSettings.defaults(),
                queryExecutor,
                runnable -> syncTasks.add(runnable),
                Logger.getLogger("test")
        );
    }

    private void runSyncTasks() {
        for (Runnable task : syncTasks) {
            task.run();
        }
        syncTasks.clear();
    }

    private void awaitAsyncAndSync() throws Exception {
        CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {}, queryExecutor);
        f.get();
        Thread.sleep(50);
        runSyncTasks();
    }

    @Test
    void noArgsShowsUsage() {
        command.onCommand(consoleSender, null, "echoclaims", new String[0]);
        assertTrue(messages.lastKey.equals("usage"));
    }

    @Test
    void unknownSubcommandShowsUnknownMessage() {
        command.onCommand(consoleSender, null, "echoclaims", new String[]{"frobnicate"});
        assertTrue(messages.lastKey.equals("unknown-subcommand"));
    }

    @Test
    void unauthorizedPlayerGetsNoPermission() {
        command.onCommand(unauthorizedPlayer, null, "echoclaims", new String[]{"status"});
        assertTrue(messages.lastKey.equals("no-permission"));
    }

    @Test
    void authorizedPlayerCanRunStatus() {
        command.onCommand(authorizedPlayer, null, "echoclaims", new String[]{"status"});
        assertTrue(messages.lastKey.equals("not-ready") || messages.lastKey.equals("status-header"));
    }

    @Test
    void consoleCanRunStatus() {
        command.onCommand(consoleSender, null, "echoclaims", new String[]{"status"});
        assertTrue(messages.lastKey.equals("not-ready") || messages.lastKey.equals("status-header"));
    }

    @Test
    void storageNotReadyResponseForIncidents() {
        evidenceLookup = null;
        command.onCommand(authorizedPlayer, null, "echoclaims",
                new String[]{"incidents", UUID.randomUUID().toString()});
        assertEquals("not-ready", messages.lastKey);
    }

    @Test
    void storageNotReadyResponseForIncident() {
        evidenceLookup = null;
        command.onCommand(authorizedPlayer, null, "echoclaims",
                new String[]{"incident", UUID.randomUUID().toString()});
        assertEquals("not-ready", messages.lastKey);
    }

    @Test
    void storageNotReadyResponseForSnapshot() {
        evidenceLookup = null;
        command.onCommand(authorizedPlayer, null, "echoclaims",
                new String[]{"snapshot", UUID.randomUUID().toString()});
        assertEquals("not-ready", messages.lastKey);
    }

    @Test
    void invalidIncidentUuidShowsInvalidUuidMessage() {
        command.onCommand(authorizedPlayer, null, "echoclaims",
                new String[]{"incident", "not-a-uuid"});
        assertEquals("invalid-uuid", messages.lastKey);
    }

    @Test
    void invalidSnapshotUuidShowsInvalidUuidMessage() {
        command.onCommand(authorizedPlayer, null, "echoclaims",
                new String[]{"snapshot", "not-a-uuid"});
        assertEquals("invalid-uuid", messages.lastKey);
    }

    @Test
    void incidentsWithMissingArgsShowsUsage() {
        command.onCommand(authorizedPlayer, null, "echoclaims", new String[]{"incidents"});
        assertEquals("usage", messages.lastKey);
    }

    @Test
    void incidentWithMissingArgsShowsUsage() {
        command.onCommand(authorizedPlayer, null, "echoclaims", new String[]{"incident"});
        assertEquals("usage", messages.lastKey);
    }

    @Test
    void snapshotWithMissingArgsShowsUsage() {
        command.onCommand(authorizedPlayer, null, "echoclaims", new String[]{"snapshot"});
        assertEquals("usage", messages.lastKey);
    }

    @Test
    void missingIncidentShowsNotFound() throws Exception {
        evidenceLookup.incidentResult = Optional.empty();
        command.onCommand(authorizedPlayer, null, "echoclaims",
                new String[]{"incident", UUID.randomUUID().toString()});
        awaitAsyncAndSync();
        assertEquals("incident-not-found", messages.lastKey);
    }

    @Test
    void missingSnapshotShowsNotFound() throws Exception {
        evidenceLookup.snapshotResult = Optional.empty();
        command.onCommand(authorizedPlayer, null, "echoclaims",
                new String[]{"snapshot", UUID.randomUUID().toString()});
        awaitAsyncAndSync();
        assertEquals("snapshot-not-found", messages.lastKey);
    }

    @Test
    void foundIncidentShowsIncidentDetail() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        evidenceLookup.incidentResult = Optional.of(new Incident(
                incidentId, IncidentType.PLAYER_DEATH, player,
                10_000L, "world", new Coordinates(0, 0, 0),
                "FALL", null, "", snapshotId, null, IncidentStatus.OPEN,
                Map.of(), "dedup-1"
        ));
        command.onCommand(authorizedPlayer, null, "echoclaims",
                new String[]{"incident", incidentId.toString()});
        awaitAsyncAndSync();
        assertTrue(messages.sentKey("incident-header"));
    }

    @Test
    void foundSnapshotShowsSnapshotDetail() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        evidenceLookup.snapshotResult = Optional.of(new InventorySnapshot(
                snapshotId, player, CaptureReason.PRE_DEATH, 10_000L,
                "world", new Coordinates(0, 0, 0), "survival",
                20, 20, 0, 0,
                List.of(new SnapshotItem("0", "minecraft:stone", 32, "data", "", 5, "", 0, 0, Map.of())),
                List.of(), null, null, 1
        ));
        command.onCommand(authorizedPlayer, null, "echoclaims",
                new String[]{"snapshot", snapshotId.toString()});
        awaitAsyncAndSync();
        assertTrue(messages.sentKey("snapshot-header"));
    }

    @Test
    void incidentsListShowsResults() throws Exception {
        UUID player = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        evidenceLookup.incidentsResult = List.of(
                new Incident(UUID.randomUUID(), IncidentType.PLAYER_DEATH, player,
                        5_000L, "world", new Coordinates(0, 0, 0),
                        "FALL", null, "", snapshotId, null, IncidentStatus.OPEN,
                        Map.of(), "k1"),
                new Incident(UUID.randomUUID(), IncidentType.PLAYER_DEATH, player,
                        10_000L, "world", new Coordinates(0, 0, 0),
                        "FALL", null, "", snapshotId, null, IncidentStatus.OPEN,
                        Map.of(), "k2")
        );
        command.onCommand(authorizedPlayer, null, "echoclaims",
                new String[]{"incidents", player.toString()});
        awaitAsyncAndSync();
        assertTrue(messages.sentKey("incidents-header"));
        assertTrue(messages.sentKey("incidents-count"));
    }

    @Test
    void emptyIncidentsListShowsEmptyMessage() throws Exception {
        evidenceLookup.incidentsResult = List.of();
        command.onCommand(authorizedPlayer, null, "echoclaims",
                new String[]{"incidents", UUID.randomUUID().toString()});
        awaitAsyncAndSync();
        assertTrue(messages.sentKey("incidents-empty"));
    }

    @Test
    void tabCompleteReturnsSubcommands() {
        List<String> completions = command.onTabComplete(authorizedPlayer, null, "echoclaims", new String[]{"st"});
        assertEquals(1, completions.size());
        assertEquals("status", completions.get(0));
    }

    @Test
    void tabCompleteWithNoPrefixReturnsAllSubcommands() {
        List<String> completions = command.onTabComplete(authorizedPlayer, null, "echoclaims", new String[]{""});
        assertEquals(6, completions.size());
    }

    @Test
    void tabCompleteWithSecondArgReturnsEmpty() {
        List<String> completions = command.onTabComplete(authorizedPlayer, null, "echoclaims",
                new String[]{"status", "extra"});
        assertTrue(completions.isEmpty());
    }

    private static class TestMessageService implements MessageService {
        final List<String> sentKeys = new ArrayList<>();
        String lastKey = "";
        Map<String, String> lastPlaceholders = Map.of();

        @Override
        public void send(CommandSender sender, String key) {
            this.lastKey = key;
            this.sentKeys.add(key);
        }

        @Override
        public void send(CommandSender sender, String key, Map<String, String> placeholders) {
            this.lastKey = key;
            this.sentKeys.add(key);
            this.lastPlaceholders = placeholders;
        }

        boolean sentKey(String key) {
            return sentKeys.contains(key);
        }
    }

    private static class TestSender implements CommandSender {
        private final boolean isConsole;
        private final boolean hasAdminPermission;
        private final List<String> messages = new ArrayList<>();

        TestSender(boolean isConsole, boolean hasAdminPermission) {
            this.isConsole = isConsole;
            this.hasAdminPermission = hasAdminPermission;
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }

        @Override
        public void sendMessage(String... messages) {
            for (String m : messages) {
                this.messages.add(m);
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public void sendMessage(UUID sender, String... messages) {
            for (String m : messages) {
                this.messages.add(m);
            }
        }

        @Override
        public void sendMessage(UUID sender, String message) {
            messages.add(message);
        }

        @Override
        public void sendMessage(net.kyori.adventure.text.Component message) {
            messages.add(message.toString());
        }

        public void sendMessage(net.kyori.adventure.text.Component... messages) {
            for (var m : messages) {
                this.messages.add(m.toString());
            }
        }

        public void sendMessage(UUID sender, net.kyori.adventure.text.Component... messages) {
            for (var m : messages) {
                this.messages.add(m.toString());
            }
        }

        @Override
        public org.bukkit.Server getServer() {
            return null;
        }

        @Override
        public String getName() {
            return isConsole ? "CONSOLE" : "TestPlayer";
        }

        @Override
        public net.kyori.adventure.text.Component name() {
            return net.kyori.adventure.text.Component.text(getName());
        }

        @Override
        @SuppressWarnings("deprecation")
        public org.bukkit.command.CommandSender.Spigot spigot() {
            return new org.bukkit.command.CommandSender.Spigot();
        }

        @Override
        public boolean isPermissionSet(String name) {
            return hasAdminPermission;
        }

        @Override
        public boolean isPermissionSet(Permission perm) {
            return hasAdminPermission;
        }

        @Override
        public boolean hasPermission(String name) {
            return hasAdminPermission;
        }

        @Override
        public boolean hasPermission(Permission perm) {
            return hasAdminPermission;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
            return null;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin) {
            return null;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
            return null;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
            return null;
        }

        @Override
        public void removeAttachment(PermissionAttachment attachment) {
        }

        @Override
        public void recalculatePermissions() {
        }

        @Override
        public Set<PermissionAttachmentInfo> getEffectivePermissions() {
            return new java.util.HashSet<>();
        }

        @Override
        public boolean isOp() {
            return isConsole || hasAdminPermission;
        }

        @Override
        public void setOp(boolean value) {
        }
    }

    private static class TestEvidenceLookup extends EvidenceLookupService {
        Optional<Incident> incidentResult = Optional.empty();
        Optional<InventorySnapshot> snapshotResult = Optional.empty();
        List<Incident> incidentsResult = List.of();

        TestEvidenceLookup() {
            super(new DummyIncidentRepository(), new DummySnapshotRepository(), 10);
        }

        @Override
        public List<Incident> findIncidentsByPlayer(UUID playerUuid) {
            return incidentsResult;
        }

        @Override
        public Optional<Incident> findIncidentById(UUID id) {
            return incidentResult;
        }

        @Override
        public Optional<InventorySnapshot> findSnapshotById(UUID id) {
            return snapshotResult;
        }

        @Override
        public int maxRecentResults() {
            return 10;
        }
    }

    private static class DummyIncidentRepository implements io.github.skyblueheat.echoclaims.persistence.IncidentRepository {
        @Override public void insert(Incident incident) {}
        @Override public void updatePostEventSnapshot(UUID incidentId, UUID snapshotId) {}
        @Override public Optional<Incident> findById(UUID id) { return Optional.empty(); }
        @Override public List<Incident> findRecentByPlayer(UUID playerUuid, int limit) { return List.of(); }
        @Override public List<Incident> findByTypeAndTimeRange(io.github.skyblueheat.echoclaims.domain.incident.IncidentType type, long from, long to, int limit) { return List.of(); }
        @Override public long count() { return 0; }
    }

    private static class DummySnapshotRepository implements io.github.skyblueheat.echoclaims.persistence.InventorySnapshotRepository {
        @Override public void insert(InventorySnapshot snapshot) {}
        @Override public Optional<InventorySnapshot> findById(UUID id) { return Optional.empty(); }
        @Override public List<InventorySnapshot> findRecentByPlayer(UUID playerUuid, int limit) { return List.of(); }
        @Override public long count() { return 0; }
    }
}
