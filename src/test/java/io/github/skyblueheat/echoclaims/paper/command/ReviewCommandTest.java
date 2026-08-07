package io.github.skyblueheat.echoclaims.paper.command;

import io.github.skyblueheat.echoclaims.application.EvidenceLookupService;
import io.github.skyblueheat.echoclaims.application.ReviewEvidenceSelectionSession;
import io.github.skyblueheat.echoclaims.application.ReviewMetrics;
import io.github.skyblueheat.echoclaims.application.ReviewService;
import io.github.skyblueheat.echoclaims.application.ReviewServiceConfig;
import io.github.skyblueheat.echoclaims.config.EchoClaimsSettings;
import io.github.skyblueheat.echoclaims.paper.message.MessageService;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewCommentRepository;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewItemDecisionRepository;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewRepository;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewStore;
import io.github.skyblueheat.echoclaims.persistence.DatabaseManager;
import io.github.skyblueheat.echoclaims.persistence.SqliteClaimReviewCommentRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteClaimReviewItemDecisionRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteClaimReviewRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteClaimReviewStore;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewCommandTest {

    private EchoClaimsCommand command;
    private TestMessageService messages;
    private TestSender consoleSender;
    private TestSender authorizedPlayer;
    private TestSender unauthorizedPlayer;
    private ExecutorService queryExecutor;
    private List<Runnable> syncTasks;
    private DatabaseManager databaseManager;
    private ReviewService reviewService;
    private ReviewEvidenceSelectionSession evidenceSession;

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        messages = new TestMessageService();
        consoleSender = new TestSender(true, true);
        authorizedPlayer = new TestSender(false, true);
        unauthorizedPlayer = new TestSender(false, false);
        syncTasks = new ArrayList<>();
        queryExecutor = Executors.newSingleThreadExecutor();

        databaseManager = new DatabaseManager(tempDir.resolve("review_cmd_test.db"));
        databaseManager.initialize();
        ClaimReviewRepository reviewRepo = new SqliteClaimReviewRepository(databaseManager);
        ClaimReviewCommentRepository commentRepo = new SqliteClaimReviewCommentRepository(databaseManager);
        ClaimReviewItemDecisionRepository decisionRepo = new SqliteClaimReviewItemDecisionRepository(databaseManager);
        ClaimReviewStore reviewStore = new SqliteClaimReviewStore(databaseManager);
        reviewService = new ReviewService(reviewRepo, commentRepo, decisionRepo, reviewStore,
                new ReviewMetrics(), ReviewServiceConfig.defaults());
        evidenceSession = new ReviewEvidenceSelectionSession(java.time.Duration.ofSeconds(60), 10);

        command = new EchoClaimsCommand(
                null,
                () -> messages,
                () -> null,
                () -> new TestEvidenceLookup(),
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> null,
                () -> reviewService,
                () -> evidenceSession,
                () -> new ReviewMetrics(),
                () -> null,
                () -> null,
                () -> EchoClaimsSettings.defaults(),
                queryExecutor,
                runnable -> syncTasks.add(runnable),
                Logger.getLogger("test")
        );
    }

    private void awaitAsyncAndSync() throws Exception {
        CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {}, queryExecutor);
        f.get();
        Thread.sleep(50);
        for (Runnable task : syncTasks) {
            task.run();
        }
        syncTasks.clear();
    }

    @Test
    void reviewWithNoSubcommandShowsUsage() {
        command.onCommand(authorizedPlayer, null, "echoclaims", new String[]{"review"});
        assertEquals("review-usage", messages.lastKey);
    }

    @Test
    void reviewQueueRequiresPermission() {
        command.onCommand(unauthorizedPlayer, null, "echoclaims", new String[]{"review", "queue"});
        assertEquals("no-permission", messages.lastKey);
    }

    @Test
    void reviewStartRequiresPermission() {
        command.onCommand(unauthorizedPlayer, null, "echoclaims", new String[]{"review", "start", "REF12345"});
        assertEquals("no-permission", messages.lastKey);
    }

    @Test
    void reviewTakeoverRequiresPermission() {
        command.onCommand(unauthorizedPlayer, null, "echoclaims",
                new String[]{"review", "takeover", "REF12345", "reason"});
        assertEquals("no-permission", messages.lastKey);
    }

    @Test
    void reviewNoteRequiresPermission() {
        command.onCommand(unauthorizedPlayer, null, "echoclaims",
                new String[]{"review", "note", "REF12345", "some note"});
        assertEquals("no-permission", messages.lastKey);
    }

    @Test
    void reviewApproveRequiresPermission() {
        command.onCommand(unauthorizedPlayer, null, "echoclaims",
                new String[]{"review", "approve", "REF12345"});
        assertEquals("no-permission", messages.lastKey);
    }

    @Test
    void reviewRejectRequiresPermission() {
        command.onCommand(unauthorizedPlayer, null, "echoclaims",
                new String[]{"review", "reject", "REF12345", "summary"});
        assertEquals("no-permission", messages.lastKey);
    }

    @Test
    void reviewHistoryRequiresPermission() {
        command.onCommand(unauthorizedPlayer, null, "echoclaims",
                new String[]{"review", "history", "REF12345"});
        assertEquals("no-permission", messages.lastKey);
    }

    @Test
    void reviewTakeoverRequiresPlayer() {
        command.onCommand(authorizedPlayer, null, "echoclaims",
                new String[]{"review", "takeover", "REF12345", "reason"});
        assertEquals("claim-player-only", messages.lastKey);
    }

    @Test
    void reviewStartRequiresPlayer() {
        command.onCommand(authorizedPlayer, null, "echoclaims",
                new String[]{"review", "start", "REF12345"});
        assertEquals("claim-player-only", messages.lastKey);
    }

    @Test
    void reviewNoteRequiresPlayer() {
        command.onCommand(authorizedPlayer, null, "echoclaims",
                new String[]{"review", "note", "REF12345", "some note"});
        assertEquals("claim-player-only", messages.lastKey);
    }

    @Test
    void reviewRejectRequiresPlayer() {
        command.onCommand(authorizedPlayer, null, "echoclaims",
                new String[]{"review", "reject", "REF12345", "summary"});
        assertEquals("claim-player-only", messages.lastKey);
    }

    @Test
    void tabCompleteReviewSubcommands() {
        List<String> result = command.onTabComplete(authorizedPlayer, null, "ec",
                new String[]{"review", ""});
        assertFalse(result.isEmpty());
        assertTrue(result.contains("queue"));
        assertTrue(result.contains("start"));
        assertTrue(result.contains("takeover"));
        assertTrue(result.contains("reject"));
        assertTrue(result.contains("history"));
    }

    @Test
    void tabCompleteReviewWithPrefix() {
        List<String> result = command.onTabComplete(authorizedPlayer, null, "ec",
                new String[]{"review", "ap"});
        assertEquals(List.of("approve"), result);
    }

    @Test
    void tabCompleteReviewPartial() {
        List<String> result = command.onTabComplete(authorizedPlayer, null, "ec",
                new String[]{"review", "re"});
        assertTrue(result.contains("request-info"));
        assertTrue(result.contains("reject"));
    }

    @Test
    void consoleCannotRunReviewStart() {
        command.onCommand(consoleSender, null, "echoclaims",
                new String[]{"review", "start", "REF12345"});
        assertEquals("claim-player-only", messages.lastKey);
    }

    private static class TestMessageService implements MessageService {
        String lastKey = "";
        Map<String, String> lastParams = Map.of();

        @Override
        public void send(CommandSender sender, String key) {
            lastKey = key;
            lastParams = Map.of();
        }

        @Override
        public void send(CommandSender sender, String key, Map<String, String> placeholders) {
            lastKey = key;
            lastParams = placeholders;
        }
    }

    private static class TestSender implements CommandSender {
        private final boolean isConsole;
        private final boolean hasAdminPermission;

        TestSender(boolean isConsole, boolean hasAdminPermission) {
            this.isConsole = isConsole;
            this.hasAdminPermission = hasAdminPermission;
        }

        @Override
        public void sendMessage(String message) {}

        @Override
        public void sendMessage(String... messages) {}

        @Override
        @SuppressWarnings("deprecation")
        public void sendMessage(UUID sender, String... messages) {}

        @Override
        public void sendMessage(UUID sender, String message) {}

        @Override
        public void sendMessage(net.kyori.adventure.text.Component message) {}

        public void sendMessage(net.kyori.adventure.text.Component... messages) {}

        public void sendMessage(UUID sender, net.kyori.adventure.text.Component... messages) {}

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
        public void removeAttachment(PermissionAttachment attachment) {}

        @Override
        public void recalculatePermissions() {}

        @Override
        public Set<PermissionAttachmentInfo> getEffectivePermissions() {
            return new HashSet<>();
        }

        @Override
        public boolean isOp() {
            return isConsole || hasAdminPermission;
        }

        @Override
        public void setOp(boolean value) {}
    }

    private static class TestEvidenceLookup extends EvidenceLookupService {
        TestEvidenceLookup() {
            super(new DummyIncidentRepository(), new DummySnapshotRepository(), 10);
        }

        private static class DummyIncidentRepository implements io.github.skyblueheat.echoclaims.persistence.IncidentRepository {
            @Override public void insert(io.github.skyblueheat.echoclaims.domain.incident.Incident incident) {}
            @Override public void updatePostEventSnapshot(UUID incidentId, UUID snapshotId) {}
            @Override public java.util.Optional<io.github.skyblueheat.echoclaims.domain.incident.Incident> findById(UUID id) { return java.util.Optional.empty(); }
            @Override public List<io.github.skyblueheat.echoclaims.domain.incident.Incident> findRecentByPlayer(UUID playerUuid, int limit) { return List.of(); }
            @Override public List<io.github.skyblueheat.echoclaims.domain.incident.Incident> findByTypeAndTimeRange(io.github.skyblueheat.echoclaims.domain.incident.IncidentType type, long from, long to, int limit) { return List.of(); }
            @Override public long count() { return 0; }
        }

        private static class DummySnapshotRepository implements io.github.skyblueheat.echoclaims.persistence.InventorySnapshotRepository {
            @Override public void insert(io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot snapshot) {}
            @Override public java.util.Optional<io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot> findById(UUID id) { return java.util.Optional.empty(); }
            @Override public List<io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot> findRecentByPlayer(UUID playerUuid, int limit) { return List.of(); }
            @Override public long count() { return 0; }
        }
    }
}
