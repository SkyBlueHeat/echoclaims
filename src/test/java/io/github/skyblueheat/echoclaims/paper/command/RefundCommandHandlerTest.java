package io.github.skyblueheat.echoclaims.paper.command;

import io.github.skyblueheat.echoclaims.application.RefundDeliveryAdapter;
import io.github.skyblueheat.echoclaims.application.RefundService;
import io.github.skyblueheat.echoclaims.application.RefundServiceConfig;
import io.github.skyblueheat.echoclaims.config.EchoClaimsSettings;
import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAction;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimActorType;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAuditEntry;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimSource;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;
import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentStatus;
import io.github.skyblueheat.echoclaims.domain.incident.IncidentType;
import io.github.skyblueheat.echoclaims.domain.refund.Refund;
import io.github.skyblueheat.echoclaims.domain.refund.RefundItem;
import io.github.skyblueheat.echoclaims.domain.refund.RefundItemStatus;
import io.github.skyblueheat.echoclaims.domain.refund.RefundStatus;
import io.github.skyblueheat.echoclaims.domain.review.ClaimReview;
import io.github.skyblueheat.echoclaims.domain.review.ReviewState;
import io.github.skyblueheat.echoclaims.domain.snapshot.CaptureReason;
import io.github.skyblueheat.echoclaims.domain.snapshot.Coordinates;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import io.github.skyblueheat.echoclaims.paper.message.MessageService;
import io.github.skyblueheat.echoclaims.persistence.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class RefundCommandHandlerTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private ClaimStore claimStore;
    private ClaimReviewStore reviewStore;
    private IncidentRepository incidentRepository;
    private InventorySnapshotRepository snapshotRepository;
    private RefundRepository refundRepository;
    private RefundItemRepository itemRepository;
    private RefundAuditRepository auditRepository;
    private RefundStore refundStore;
    private ClaimReviewRepository reviewRepository;
    private ClaimReviewItemDecisionRepository decisionRepository;
    private RefundService refundService;
    private EchoClaimsSettings settings;

    private TestMessageService messages;
    private ExecutorService queryExecutor;
    private List<Runnable> syncTasks;
    private RefundCommandHandler handler;
    private RefundDeliveryAdapter adapter;

    private UUID playerUuid;
    private UUID otherPlayerUuid;
    private UUID claimId;
    private UUID reviewId;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(tempDir.resolve("refund_cmd_test.db"));
        databaseManager.initialize();
        claimStore = new SqliteClaimStore(databaseManager);
        reviewStore = new SqliteClaimReviewStore(databaseManager);
        incidentRepository = new SqliteIncidentRepository(databaseManager);
        snapshotRepository = new SqliteInventorySnapshotRepository(databaseManager);
        refundRepository = new SqliteRefundRepository(databaseManager);
        itemRepository = new SqliteRefundItemRepository(databaseManager);
        auditRepository = new SqliteRefundAuditRepository(databaseManager);
        refundStore = new SqliteRefundStore(databaseManager);
        reviewRepository = new SqliteClaimReviewRepository(databaseManager);
        decisionRepository = new SqliteClaimReviewItemDecisionRepository(databaseManager);

        settings = EchoClaimsSettings.defaults();
        RefundServiceConfig config = new RefundServiceConfig(true, true, false, 100, true);
        refundService = new RefundService(
                refundRepository, itemRepository, auditRepository, refundStore,
                reviewRepository, decisionRepository, snapshotRepository, config
        );
        adapter = item -> RefundDeliveryAdapter.ItemDeliveryResult.success(item.remainingQuantity());

        messages = new TestMessageService();
        queryExecutor = Executors.newSingleThreadExecutor();
        syncTasks = new ArrayList<>();

        handler = new RefundCommandHandler(
                () -> messages,
                () -> refundService,
                () -> adapter,
                () -> settings,
                queryExecutor,
                runnable -> syncTasks.add(runnable),
                Logger.getLogger("test")
        );

        seedClaimAndReview();
    }

    private void seedClaimAndReview() throws Exception {
        playerUuid = UUID.randomUUID();
        otherPlayerUuid = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        snapshotRepository.insert(new InventorySnapshot(
                snapshotId, playerUuid, CaptureReason.PRE_DEATH,
                10_000L, "world", new Coordinates(0, 0, 0), "survival",
                20, 20, 0, 0, List.of(), List.of(), null, null, 1
        ));
        UUID incidentId = UUID.randomUUID();
        incidentRepository.insert(new Incident(
                incidentId, IncidentType.PLAYER_DEATH, playerUuid,
                10_000L, "world", new Coordinates(0, 0, 0),
                "FALL", null, "", snapshotId, null, IncidentStatus.OPEN,
                Map.of(), "dedup-" + UUID.randomUUID()
        ));
        claimId = UUID.randomUUID();
        Claim claim = new Claim(
                claimId, "REF" + claimId.toString().substring(0, 8).toUpperCase(),
                incidentId, playerUuid, ClaimStatus.SUBMITTED, ClaimSource.PLAYER_COMMAND,
                "desc", 10_000L, 10_000L, 0L, 0, Map.of()
        );
        claimStore.createClaim(claim, new ClaimAuditEntry(
                UUID.randomUUID(), claim.id(), ClaimActorType.PLAYER,
                claim.playerUuid(), ClaimAction.CREATED, "",
                System.currentTimeMillis(), 0, Map.of()
        ));

        reviewId = UUID.randomUUID();
        UUID staffUuid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        ClaimReview review = new ClaimReview(
                reviewId, claim.id(), staffUuid,
                ReviewState.OPEN, null, "", now, now, 0L, 0, Map.of()
        );
        ClaimAuditEntry audit = new ClaimAuditEntry(
                UUID.randomUUID(), claim.id(), ClaimActorType.STAFF,
                staffUuid, ClaimAction.REVIEW_STARTED, "test",
                now, 1, Map.of()
        );
        reviewStore.startReview(review, claim.id(), claim.version(), audit);
    }

    private void seedRefund() throws Exception {
        long now = System.currentTimeMillis();
        UUID refundId = UUID.randomUUID();
        Refund refund = new Refund(
                refundId, claimId, reviewId, playerUuid,
                RefundStatus.READY, now, 0, 0, Map.of()
        );
        refundRepository.insert(refund);

        RefundItem item = new RefundItem(
                UUID.randomUUID(), refundId, claimId, reviewId, playerUuid,
                "snap:0", UUID.randomUUID(), "diamond", 10, 0,
                "base64data", RefundItemStatus.PENDING, "", now, now, 0
        );
        itemRepository.insert(item);
    }

    private void awaitAsyncAndSync() throws Exception {
        CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {}, queryExecutor);
        f.get();
        Thread.sleep(50);
        for (Runnable task : syncTasks) task.run();
        syncTasks.clear();
    }

    private CommandSender playerSender(UUID uuid, Set<String> perms) {
        return (CommandSender) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{CommandSender.class, Player.class},
                new PlayerInvocationHandler(uuid, perms)
        );
    }

    private CommandSender consoleSender(Set<String> perms) {
        return (CommandSender) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{CommandSender.class},
                new ConsoleInvocationHandler(perms)
        );
    }

    // ─── Permission tests (6 subcommands × no-perm vs perm) ───

    @Test void statusWithoutPermissionGetsNoPermission() {
        handler.handle(playerSender(playerUuid, Set.of()), new String[]{"refund", "status", claimId.toString()});
        assertEquals("no-permission", messages.lastKey);
    }

    @Test void statusWithPermissionShowsStatus() throws Exception {
        seedRefund();
        handler.handle(playerSender(playerUuid, Set.of("echoclaims.staff.refund.status")),
                new String[]{"refund", "status", claimId.toString()});
        awaitAsyncAndSync();
        assertTrue(messages.sentKeys.contains("refund-status-header"));
    }

    @Test void executeWithoutPermissionGetsNoPermission() {
        handler.handle(playerSender(playerUuid, Set.of()), new String[]{"refund", "execute", claimId.toString()});
        assertEquals("no-permission", messages.lastKey);
    }

    @Test void executeWithPermissionRunsExecution() throws Exception {
        seedRefund();
        handler.handle(playerSender(playerUuid, Set.of("echoclaims.staff.refund.execute")),
                new String[]{"refund", "execute", claimId.toString()});
        awaitAsyncAndSync();
        assertEquals("refund-completed", messages.lastKey);
    }

    @Test void historyWithoutPermissionGetsNoPermission() {
        handler.handle(playerSender(playerUuid, Set.of()), new String[]{"refund", "history", claimId.toString()});
        assertEquals("no-permission", messages.lastKey);
    }

    @Test void historyWithPermissionShowsHistory() throws Exception {
        seedRefund();
        handler.handle(playerSender(playerUuid, Set.of("echoclaims.staff.refund.history")),
                new String[]{"refund", "history", claimId.toString()});
        awaitAsyncAndSync();
        assertTrue(messages.sentKeys.contains("refund-history-header") || messages.sentKeys.contains("refund-history-empty"));
    }

    @Test void retryWithoutPermissionGetsNoPermission() {
        handler.handle(playerSender(playerUuid, Set.of()), new String[]{"refund", "retry", claimId.toString()});
        assertEquals("no-permission", messages.lastKey);
    }

    @Test void retryWithPermissionAttemptsRetry() throws Exception {
        seedRefund();
        handler.handle(playerSender(playerUuid, Set.of("echoclaims.staff.refund.retry")),
                new String[]{"refund", "retry", claimId.toString()});
        awaitAsyncAndSync();
        assertTrue(messages.sentKeys.contains("refund-retry-success") || messages.sentKeys.contains("refund-retry-rejected"));
    }

    @Test void pendingWithoutPermissionGetsNoPermission() {
        handler.handle(playerSender(playerUuid, Set.of()), new String[]{"refund", "pending"});
        assertEquals("no-permission", messages.lastKey);
    }

    @Test void pendingWithPermissionShowsPending() throws Exception {
        seedRefund();
        handler.handle(playerSender(playerUuid, Set.of("echoclaims.refund.pending")),
                new String[]{"refund", "pending"});
        awaitAsyncAndSync();
        assertTrue(messages.sentKeys.contains("refund-pending-header") || messages.sentKeys.contains("refund-no-pending"));
    }

    @Test void claimWithoutPermissionGetsNoPermission() {
        handler.handle(playerSender(playerUuid, Set.of()), new String[]{"refund", "claim", claimId.toString()});
        assertEquals("no-permission", messages.lastKey);
    }

    @Test void claimWithPermissionExecutesDelivery() throws Exception {
        seedRefund();
        handler.handle(playerSender(playerUuid, Set.of("echoclaims.refund.claim")),
                new String[]{"refund", "claim", claimId.toString()});
        awaitAsyncAndSync();
        assertEquals("refund-completed", messages.lastKey);
    }

    // ─── Ownership tests ───

    @Test void claimByOtherPlayerGetsNotOwned() throws Exception {
        seedRefund();
        handler.handle(playerSender(otherPlayerUuid, Set.of("echoclaims.refund.claim")),
                new String[]{"refund", "claim", claimId.toString()});
        awaitAsyncAndSync();
        assertEquals("refund-not-owned", messages.lastKey);
    }

    @Test void claimByOwnerSucceeds() throws Exception {
        seedRefund();
        handler.handle(playerSender(playerUuid, Set.of("echoclaims.refund.claim")),
                new String[]{"refund", "claim", claimId.toString()});
        awaitAsyncAndSync();
        assertEquals("refund-completed", messages.lastKey);
    }

    // ─── Missing arguments ───

    @Test void statusMissingClaimRefShowsUsage() {
        handler.handle(playerSender(playerUuid, Set.of("echoclaims.staff.refund.status")),
                new String[]{"refund", "status"});
        assertEquals("refund-usage", messages.lastKey);
    }

    @Test void executeMissingClaimRefShowsUsage() {
        handler.handle(playerSender(playerUuid, Set.of("echoclaims.staff.refund.execute")),
                new String[]{"refund", "execute"});
        assertEquals("refund-usage", messages.lastKey);
    }

    @Test void claimMissingClaimRefShowsUsage() {
        handler.handle(playerSender(playerUuid, Set.of("echoclaims.refund.claim")),
                new String[]{"refund", "claim"});
        assertEquals("refund-usage", messages.lastKey);
    }

    // ─── Refund not found ───

    @Test void statusRefundNotFound() throws Exception {
        handler.handle(playerSender(playerUuid, Set.of("echoclaims.staff.refund.status")),
                new String[]{"refund", "status", UUID.randomUUID().toString()});
        awaitAsyncAndSync();
        assertEquals("refund-not-found", messages.lastKey);
    }

    @Test void executeRefundNotFound() throws Exception {
        handler.handle(playerSender(playerUuid, Set.of("echoclaims.staff.refund.execute")),
                new String[]{"refund", "execute", UUID.randomUUID().toString()});
        awaitAsyncAndSync();
        assertTrue(messages.sentKeys.contains("refund-rejected"));
    }

    // ─── Tab completion test ───

    @Test void tabCompletionReturnsAllRefundSubcommands() {
        List<String> subs = RefundCommandHandler.REFUND_SUBCOMMANDS;
        assertIterableEquals(List.of("status", "execute", "history", "retry", "pending", "claim"), subs);
    }

    // ─── Disabled refunds ───

    @Test void disabledRefundsShowsDisabledMessage() {
        handler = new RefundCommandHandler(
                () -> messages,
                () -> refundService,
                () -> adapter,
                () -> new EchoClaimsSettings(
                        "en", "", null, "test.db", null, 0, 0,
                        false, false, false, 0, 0, 0,
                        true, null, 0, null, 0,
                        true, null, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        null, false, false,
                        false, false, false, 100, true
                ),
                queryExecutor,
                runnable -> syncTasks.add(runnable),
                Logger.getLogger("test")
        );
        handler.handle(playerSender(playerUuid, Set.of("echoclaims.staff.refund.status")),
                new String[]{"refund", "status", claimId.toString()});
        assertEquals("refund-disabled", messages.lastKey);
    }

    // ─── Console sender for player-only commands ───

    @Test void pendingFromConsoleShowsPlayerOnly() {
        handler.handle(consoleSender(Set.of("echoclaims.refund.pending")),
                new String[]{"refund", "pending"});
        assertEquals("claim-player-only", messages.lastKey);
    }

    @Test void claimFromConsoleShowsPlayerOnly() {
        handler.handle(consoleSender(Set.of("echoclaims.refund.claim")),
                new String[]{"refund", "claim", claimId.toString()});
        assertEquals("claim-player-only", messages.lastKey);
    }

    // ─── Staff commands from console (should work, not player-only) ───

    @Test void statusFromConsoleWorks() throws Exception {
        seedRefund();
        handler.handle(consoleSender(Set.of("echoclaims.staff.refund.status")),
                new String[]{"refund", "status", claimId.toString()});
        awaitAsyncAndSync();
        assertTrue(messages.sentKeys.contains("refund-status-header"));
    }

    @Test void executeFromConsoleWorks() throws Exception {
        seedRefund();
        handler.handle(consoleSender(Set.of("echoclaims.staff.refund.execute")),
                new String[]{"refund", "execute", claimId.toString()});
        awaitAsyncAndSync();
        assertTrue(messages.sentKeys.contains("refund-completed") || messages.sentKeys.contains("refund-rejected"));
    }

    @Test void historyFromConsoleWorks() throws Exception {
        seedRefund();
        handler.handle(consoleSender(Set.of("echoclaims.staff.refund.history")),
                new String[]{"refund", "history", claimId.toString()});
        awaitAsyncAndSync();
        assertTrue(messages.sentKeys.contains("refund-history-header") || messages.sentKeys.contains("refund-history-empty"));
    }

    @Test void retryFromConsoleWorks() throws Exception {
        seedRefund();
        handler.handle(consoleSender(Set.of("echoclaims.staff.refund.retry")),
                new String[]{"refund", "retry", claimId.toString()});
        awaitAsyncAndSync();
        assertTrue(messages.sentKeys.contains("refund-retry-success") || messages.sentKeys.contains("refund-retry-rejected"));
    }

    // ─── Invalid claim reference ───

    @Test void statusInvalidClaimRefShowsNotFound() throws Exception {
        handler.handle(playerSender(playerUuid, Set.of("echoclaims.staff.refund.status")),
                new String[]{"refund", "status", UUID.randomUUID().toString()});
        awaitAsyncAndSync();
        assertEquals("refund-not-found", messages.lastKey);
    }

    // ─── Admin permission grants all ───

    @Test void adminPermissionGrantsAllRefundCommands() throws Exception {
        seedRefund();
        handler.handle(playerSender(playerUuid, Set.of("echoclaims.admin")),
                new String[]{"refund", "status", claimId.toString()});
        awaitAsyncAndSync();
        assertTrue(messages.sentKeys.contains("refund-status-header"));
    }

    // ═══ Test doubles ═══

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
    }

    private static class PlayerInvocationHandler implements InvocationHandler {
        private final UUID uuid;
        private final Set<String> perms;

        PlayerInvocationHandler(UUID uuid, Set<String> perms) {
            this.uuid = uuid;
            this.perms = perms;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getUniqueId" -> uuid;
                case "getName" -> "TestPlayer";
                case "hasPermission" -> {
                    String perm = (String) args[0];
                    yield perms.contains(perm) || perms.contains("echoclaims.admin");
                }
                case "isPermissionSet" -> perms.contains((String) args[0]);
                case "isOp" -> false;
                case "sendMessage" -> null;
                case "getServer" -> null;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "TestPlayer[" + uuid + "]";
                default -> getDefault(method.getReturnType());
            };
        }
    }

    private static class ConsoleInvocationHandler implements InvocationHandler {
        private final Set<String> perms;

        ConsoleInvocationHandler(Set<String> perms) {
            this.perms = perms;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getName" -> "CONSOLE";
                case "hasPermission" -> {
                    String perm = (String) args[0];
                    yield perms.contains(perm) || perms.contains("echoclaims.admin");
                }
                case "isPermissionSet" -> perms.contains((String) args[0]);
                case "isOp" -> true;
                case "sendMessage" -> null;
                case "getServer" -> null;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "CONSOLE";
                default -> getDefault(method.getReturnType());
            };
        }
    }

    private static Object getDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        if (type == void.class) return null;
        return null;
    }
}
