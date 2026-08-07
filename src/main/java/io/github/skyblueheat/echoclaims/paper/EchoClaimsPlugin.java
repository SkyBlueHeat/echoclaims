package io.github.skyblueheat.echoclaims.paper;

import io.github.skyblueheat.echoclaims.application.ClaimCreationService;
import io.github.skyblueheat.echoclaims.application.IncidentSelectionSession;
import io.github.skyblueheat.echoclaims.application.ClaimLookupService;
import io.github.skyblueheat.echoclaims.application.ClaimMetrics;
import io.github.skyblueheat.echoclaims.application.ClaimRateLimitService;
import io.github.skyblueheat.echoclaims.application.ClaimReferenceGenerator;
import io.github.skyblueheat.echoclaims.application.ClaimTransitionService;
import io.github.skyblueheat.echoclaims.application.EvidenceLookupService;
import io.github.skyblueheat.echoclaims.application.EvidenceMetrics;
import io.github.skyblueheat.echoclaims.application.EvidencePersistenceService;
import io.github.skyblueheat.echoclaims.application.ItemValueScorer;
import io.github.skyblueheat.echoclaims.application.RefundDeliveryAdapter;
import io.github.skyblueheat.echoclaims.application.RefundService;
import io.github.skyblueheat.echoclaims.application.ReviewEvidenceSelectionSession;
import io.github.skyblueheat.echoclaims.application.ReviewMetrics;
import io.github.skyblueheat.echoclaims.application.ReviewService;
import io.github.skyblueheat.echoclaims.application.StatusService;
import io.github.skyblueheat.echoclaims.config.EchoClaimsSettings;
import io.github.skyblueheat.echoclaims.config.SettingsLoadResult;
import io.github.skyblueheat.echoclaims.config.SettingsLoader;
import io.github.skyblueheat.echoclaims.integration.IntegrationRegistry;
import io.github.skyblueheat.echoclaims.integration.ItemSerializer;
import io.github.skyblueheat.echoclaims.integration.bukkit.BukkitItemSerializer;
import io.github.skyblueheat.echoclaims.integration.vanilla.VanillaEntityProvider;
import io.github.skyblueheat.echoclaims.integration.vanilla.VanillaItemProvider;
import io.github.skyblueheat.echoclaims.persistence.SqliteRefundAuditRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteRefundItemRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteRefundRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteRefundStore;
import io.github.skyblueheat.echoclaims.paper.refund.PaperRefundDeliveryAdapter;
import io.github.skyblueheat.echoclaims.paper.command.EchoClaimsCommand;
import io.github.skyblueheat.echoclaims.paper.config.BukkitConfigurationSource;
import io.github.skyblueheat.echoclaims.paper.listener.DeathCaptureService;
import io.github.skyblueheat.echoclaims.paper.message.MessageService;
import io.github.skyblueheat.echoclaims.paper.message.PaperMessageService;
import io.github.skyblueheat.echoclaims.persistence.AuditRecordRepository;
import io.github.skyblueheat.echoclaims.persistence.AuditWriteQueue;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewCommentRepository;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewItemDecisionRepository;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewRepository;
import io.github.skyblueheat.echoclaims.persistence.ClaimReviewStore;
import io.github.skyblueheat.echoclaims.persistence.ClaimStore;
import io.github.skyblueheat.echoclaims.persistence.DatabaseManager;
import io.github.skyblueheat.echoclaims.persistence.EvidenceStore;
import io.github.skyblueheat.echoclaims.persistence.IncidentRepository;
import io.github.skyblueheat.echoclaims.persistence.InventorySnapshotRepository;
import io.github.skyblueheat.echoclaims.persistence.RefundAuditRepository;
import io.github.skyblueheat.echoclaims.persistence.RefundItemRepository;
import io.github.skyblueheat.echoclaims.persistence.RefundRepository;
import io.github.skyblueheat.echoclaims.persistence.RefundStore;
import io.github.skyblueheat.echoclaims.persistence.SqliteAuditRecordRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteClaimReviewCommentRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteClaimReviewItemDecisionRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteClaimReviewRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteClaimReviewStore;
import io.github.skyblueheat.echoclaims.persistence.SqliteClaimStore;
import io.github.skyblueheat.echoclaims.persistence.SqliteEvidenceStore;
import io.github.skyblueheat.echoclaims.persistence.SqliteIncidentRepository;
import io.github.skyblueheat.echoclaims.persistence.SqliteInventorySnapshotRepository;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Plugin entry point. Wires the persistence kernel and owns every background resource.
 */
public final class EchoClaimsPlugin extends JavaPlugin {

    private volatile boolean enabled = false;
    private volatile EchoClaimsSettings settings;
    private volatile PaperMessageService messages;
    private volatile ItemValueScorer itemValueScorer;
    private volatile StatusService statusService;
    private volatile EvidenceLookupService evidenceLookupService;
    private volatile ItemSerializer itemSerializer;
    private volatile ClaimLookupService claimLookupService;
    private volatile ClaimCreationService claimCreationService;
    private volatile ClaimTransitionService claimTransitionService;
    private volatile ClaimRateLimitService claimRateLimitService;
    private volatile IncidentSelectionSession incidentSelectionSession;
    private volatile ReviewService reviewService;
    private volatile ReviewEvidenceSelectionSession reviewEvidenceSession;
    private volatile ReviewMetrics reviewMetrics;
    private volatile RefundService refundService;
    private volatile RefundDeliveryAdapter refundDeliveryAdapter;

    private IntegrationRegistry integrations;
    private DatabaseManager databaseManager;
    private AuditRecordRepository auditRepository;
    private AuditWriteQueue writeQueue;
    private InventorySnapshotRepository snapshotRepository;
    private IncidentRepository incidentRepository;
    private EvidenceStore evidenceStore;
    private EvidenceMetrics evidenceMetrics;
    private EvidencePersistenceService evidenceService;
    private DeathCaptureService deathCaptureService;
    private ClaimStore claimStore;
    private ClaimMetrics claimMetrics;
    private ClaimReferenceGenerator claimReferenceGenerator;
    private ExecutorService queryExecutor;
    private long startedAt;

    @Override
    public void onEnable() {
        enabled = true;
        startedAt = System.currentTimeMillis();
        saveDefaultConfig();
        applyConfiguration().forEach(warning -> getLogger().warning("Configuration: " + warning));

        queryExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "echoclaims-sqlite-reader");
            thread.setDaemon(true);
            return thread;
        });

        databaseManager = new DatabaseManager(
                getDataFolder().toPath().resolve(settings.sqliteFile()));

        registerCommand();
        getLogger().info("EchoClaims enabling (v" + getPluginMeta().getVersion()
                + ") — storage initializing asynchronously");

        queryExecutor.submit(this::initializeStorageAsync);
    }

    @Override
    public void onDisable() {
        enabled = false;

        if (deathCaptureService != null) {
            deathCaptureService.disable();
        }

        if (incidentSelectionSession != null) {
            incidentSelectionSession.clearAll();
        }

        if (reviewEvidenceSession != null) {
            reviewEvidenceSession.clearAll();
        }

        if (evidenceService != null) {
            boolean drained = evidenceService.shutdown(settings.shutdownTimeout());
            if (!drained) {
                getLogger().warning("Evidence persistence service did not drain in time");
            }
        }

        if (writeQueue != null) {
            boolean drained = writeQueue.shutdown(settings.shutdownTimeout());
            AuditWriteQueue.QueueStatus status = writeQueue.status();
            if (drained) {
                getLogger().info("Audit write queue drained: " + status.written() + " record(s) stored");
            } else {
                getLogger().warning("Audit write queue did not drain in time; pending="
                        + status.pending() + " failed=" + status.failed()
                        + " dropped=" + status.dropped()
                        + " overflowDropped=" + status.overflowDropped()
                        + " abandoned=" + status.abandoned());
            }
        }

        if (queryExecutor != null) {
            queryExecutor.shutdownNow();
            try {
                if (!queryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    getLogger().warning("Query executor did not stop cleanly");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        getLogger().info("EchoClaims disabled");
    }

    /**
     * Re-reads config.yml and the locale files. Persistence and providers keep running
     * so a reload can never lose queued records.
     *
     * @return validation warnings that should be shown to the administrator
     */
    public List<String> reloadSettings() {
        reloadConfig();
        List<String> warnings = new ArrayList<>(applyConfiguration());
        return warnings;
    }

    /**
     * Runs migrations on the storage thread. Does not block the main server thread.
     * Post-init setup is scheduled back on the main thread via {@code runTask}.
     */
    private void initializeStorageAsync() {
        try {
            int applied = databaseManager.initialize();
            getServer().getScheduler().runTask(this, () -> onStorageReady(applied));
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE,
                    "EchoClaims could not initialize its database and will stay disabled",
                    exception);
            getServer().getScheduler().runTask(this,
                    () -> getServer().getPluginManager().disablePlugin(this));
        }
    }

    private void onStorageReady(int applied) {
        if (!enabled) {
            getLogger().warning("Storage initialized after plugin disable — discarding");
            return;
        }

        getLogger().info("Storage ready at " + databaseManager.databasePath()
                + " (" + applied + " migration(s) applied)");

        auditRepository = new SqliteAuditRecordRepository(databaseManager);
        writeQueue = new AuditWriteQueue(
                auditRepository,
                throwable -> getLogger().log(Level.SEVERE, "Audit write failed", throwable),
                settings.writeQueueCapacity(),
                settings.writeBatchSize()
        );
        writeQueue.start();

        snapshotRepository = new SqliteInventorySnapshotRepository(databaseManager);
        incidentRepository = new SqliteIncidentRepository(databaseManager);
        evidenceStore = new SqliteEvidenceStore(databaseManager);

        evidenceMetrics = new EvidenceMetrics();
        evidenceService = new EvidencePersistenceService(
                evidenceStore,
                evidenceMetrics,
                throwable -> getLogger().log(Level.SEVERE, "Evidence persistence failed", throwable),
                settings.evidenceQueueCapacity(),
                getLogger()
        );

        evidenceLookupService = new EvidenceLookupService(
                incidentRepository,
                snapshotRepository,
                settings.maxRecentResults()
        );

        integrations = new IntegrationRegistry((providerId, throwable) -> getLogger().log(
                Level.WARNING, "Content provider '" + providerId + "' failed", throwable));
        integrations.registerEntityProvider(new VanillaEntityProvider());
        integrations.registerItemProvider(new VanillaItemProvider());
        integrations.freeze();
        getLogger().info("Content providers: "
                + String.join(", ", integrations.describeProviders()));

        itemSerializer = new BukkitItemSerializer(settings.maxItemPayloadBytes());

        deathCaptureService = new DeathCaptureService(
                () -> settings,
                itemSerializer,
                itemValueScorer,
                integrations,
                evidenceService,
                getLogger()
        );
        getServer().getPluginManager().registerEvents(deathCaptureService, this);

        claimStore = new SqliteClaimStore(databaseManager);
        claimMetrics = new ClaimMetrics();
        claimReferenceGenerator = new ClaimReferenceGenerator();
        claimRateLimitService = new ClaimRateLimitService(settings.claimRateLimitCooldown());
        incidentSelectionSession = new IncidentSelectionSession(
                settings.claimSelectionSessionTtl(),
                settings.claimSelectionSessionMaxPlayers()
        );
        claimLookupService = new ClaimLookupService(claimStore, settings.maxRecentResults());
        claimCreationService = new ClaimCreationService(
                claimStore, claimReferenceGenerator, claimMetrics);
        claimTransitionService = new ClaimTransitionService(claimStore, claimMetrics);

        ClaimReviewRepository reviewRepository = new SqliteClaimReviewRepository(databaseManager);
        ClaimReviewCommentRepository commentRepository = new SqliteClaimReviewCommentRepository(databaseManager);
        ClaimReviewItemDecisionRepository decisionRepository = new SqliteClaimReviewItemDecisionRepository(databaseManager);
        ClaimReviewStore reviewStore = new SqliteClaimReviewStore(databaseManager);
        reviewMetrics = new ReviewMetrics();
        reviewService = new ReviewService(
                reviewRepository, commentRepository, decisionRepository, reviewStore, reviewMetrics,
                settings.reviewServiceConfig());
        reviewEvidenceSession = new ReviewEvidenceSelectionSession(
                settings.reviewEvidenceSessionTtl(),
                settings.reviewEvidenceSessionMaxStaff()
        );

        RefundRepository refundRepository = new SqliteRefundRepository(databaseManager);
        RefundItemRepository refundItemRepository = new SqliteRefundItemRepository(databaseManager);
        RefundAuditRepository refundAuditRepository = new SqliteRefundAuditRepository(databaseManager);
        RefundStore refundStore = new SqliteRefundStore(databaseManager);
        refundService = new RefundService(
                refundRepository, refundItemRepository, refundAuditRepository, refundStore,
                reviewRepository, decisionRepository, snapshotRepository,
                settings.refundServiceConfig()
        );
        refundDeliveryAdapter = new PaperRefundDeliveryAdapter(itemSerializer, getLogger());

        statusService = new StatusService(
                () -> settings,
                databaseManager,
                writeQueue,
                integrations,
                evidenceService,
                claimMetrics,
                claimLookupService,
                getPluginMeta().getVersion(),
                startedAt
        );

        getLogger().info("EchoClaims enabled (v" + getPluginMeta().getVersion() + ")");
    }

    private List<String> applyConfiguration() {
        SettingsLoadResult result =
                SettingsLoader.load(new BukkitConfigurationSource(getConfig()));
        settings = result.settings();
        itemValueScorer = new ItemValueScorer(settings.itemScoreWeights());
        messages = PaperMessageService.load(this, settings.locale());
        return result.warnings();
    }

    private void registerCommand() {
        PluginCommand command = getCommand("echoclaims");
        if (command == null) {
            getLogger().severe("Command 'echoclaims' is missing from plugin.yml");
            return;
        }

        EchoClaimsCommand executor = new EchoClaimsCommand(
                this,
                () -> (MessageService) messages,
                () -> statusService,
                () -> evidenceLookupService,
                () -> claimLookupService,
                () -> claimCreationService,
                () -> claimTransitionService,
                () -> claimRateLimitService,
                () -> incidentSelectionSession,
                () -> reviewService,
                () -> reviewEvidenceSession,
                () -> reviewMetrics,
                () -> refundService,
                () -> refundDeliveryAdapter,
                () -> settings,
                queryExecutor,
                runnable -> getServer().getScheduler().runTask(this, runnable),
                getLogger()
        );
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
