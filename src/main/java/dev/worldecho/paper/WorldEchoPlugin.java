package dev.worldecho.paper;

import dev.worldecho.application.BindingEnricher;
import dev.worldecho.application.BindingReloadCoordinator;
import dev.worldecho.application.ItemValueScorer;
import dev.worldecho.application.MemoryRecorder;
import dev.worldecho.config.BindingLoadResult;
import dev.worldecho.config.BindingLoader;
import dev.worldecho.config.SettingsLoadResult;
import dev.worldecho.config.SettingsLoader;
import dev.worldecho.config.WorldEchoSettings;
import dev.worldecho.domain.binding.BindingDiagnostic;
import dev.worldecho.domain.binding.BindingRegistry;
import dev.worldecho.integration.IntegrationRegistry;
import dev.worldecho.integration.vanilla.VanillaEntityProvider;
import dev.worldecho.integration.vanilla.VanillaItemProvider;
import dev.worldecho.paper.command.WorldEchoCommand;
import dev.worldecho.paper.config.BukkitConfigurationSource;
import dev.worldecho.paper.inventory.PlayerInventoryReconciler;
import dev.worldecho.paper.inventory.PlayerInventoryReconciliationScheduler;
import dev.worldecho.paper.item.ItemIdentityAdapter;
import dev.worldecho.paper.listener.PlayerDeathMemoryListener;
import dev.worldecho.paper.listener.PlayerInventoryObservationListener;
import dev.worldecho.paper.listener.ItemTransformationListener;
import dev.worldecho.paper.message.PaperMessageService;
import dev.worldecho.domain.item.AutomaticItemIdentityService;
import dev.worldecho.domain.item.LotOwnershipTransitionService;
import dev.worldecho.domain.item.OwnershipTransitionService;
import dev.worldecho.domain.item.ReconciliationMetrics;
import dev.worldecho.persistence.DatabaseManager;
import dev.worldecho.persistence.LotOwnershipLedgerRepository;
import dev.worldecho.persistence.OwnershipLedgerRepository;
import dev.worldecho.persistence.SqliteLotOwnershipLedgerRepository;
import dev.worldecho.persistence.SqliteStoryEventRepository;
import dev.worldecho.persistence.SqliteTrackedItemLotRepository;
import dev.worldecho.persistence.SqliteTrackedItemRepository;
import dev.worldecho.persistence.SqliteOwnershipLedgerRepository;
import dev.worldecho.persistence.StoryEventRepository;
import dev.worldecho.persistence.StoryWriteQueue;
import dev.worldecho.persistence.TrackedItemLotRepository;
import dev.worldecho.persistence.TrackedItemRepository;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * Plugin entry point. Wires the memory kernel and owns every background resource.
 */
public final class WorldEchoPlugin extends JavaPlugin {

    private static final Duration STORAGE_INIT_TIMEOUT = Duration.ofSeconds(30);

    private static final String BINDINGS_FILE = "bindings.yml";

    private volatile WorldEchoSettings settings;
    private volatile PaperMessageService messages;
    private volatile ItemValueScorer itemValueScorer;
    private volatile BindingEnricher bindingEnricher;

    private IntegrationRegistry integrations;
    private DatabaseManager databaseManager;
    private StoryEventRepository repository;
    private StoryWriteQueue writeQueue;
    private ExecutorService queryExecutor;
    private ItemIdentityAdapter itemIdentityAdapter;
    private TrackedItemRepository trackedItemRepository;
    private OwnershipLedgerRepository ledgerRepository;
    private TrackedItemLotRepository lotRepository;
    private LotOwnershipLedgerRepository lotLedgerRepository;
    private OwnershipTransitionService ownershipTransitionService;
    private LotOwnershipTransitionService lotOwnershipTransitionService;
    private AutomaticItemIdentityService automaticIdentityService;
    private ReconciliationMetrics reconciliationMetrics;
    private dev.worldecho.domain.item.DuplicateObservationRegistry duplicateObservationRegistry;
    private PlayerInventoryReconciler inventoryReconciler;
    private PlayerInventoryReconciliationScheduler reconciliationScheduler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultBindings();
        applyConfiguration().forEach(warning -> getLogger().warning("Configuration: " + warning));
        loadBindings();

        queryExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "worldecho-sqlite-reader");
            thread.setDaemon(true);
            return thread;
        });

        databaseManager = new DatabaseManager(
                getDataFolder().toPath().resolve(settings.sqliteFile()));

        if (!initializeStorage()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        repository = new SqliteStoryEventRepository(databaseManager);
        trackedItemRepository = new SqliteTrackedItemRepository(databaseManager);
        ledgerRepository = new SqliteOwnershipLedgerRepository(databaseManager);
        itemIdentityAdapter = new ItemIdentityAdapter(this);
        writeQueue = new StoryWriteQueue(
                repository,
                throwable -> getLogger().log(Level.SEVERE, "Story write failed", throwable),
                settings.writeQueueCapacity(),
                settings.writeBatchSize()
        );
        writeQueue.start();

        integrations = new IntegrationRegistry((providerId, throwable) -> getLogger().log(
                Level.WARNING, "Content provider '" + providerId + "' failed", throwable));
        integrations.registerEntityProvider(new VanillaEntityProvider());
        integrations.registerItemProvider(new VanillaItemProvider());
        getLogger().info("Content providers: "
                + String.join(", ", integrations.describeProviders()));

        MemoryRecorder memoryRecorder = new MemoryRecorder(
                writeQueue,
                event -> getLogger().warning(
                        "Write queue is saturated; dropped memory event " + event.id())
        );

        getServer().getPluginManager().registerEvents(
                new PlayerDeathMemoryListener(
                        () -> settings,
                        integrations,
                        () -> itemValueScorer,
                        memoryRecorder,
                        itemIdentityAdapter
                ),
                this
        );

        lotRepository = new SqliteTrackedItemLotRepository(databaseManager);
        lotLedgerRepository = new SqliteLotOwnershipLedgerRepository(databaseManager);
        ownershipTransitionService = new OwnershipTransitionService(
                trackedItemRepository, ledgerRepository, java.time.Clock.systemUTC());
        lotOwnershipTransitionService = new LotOwnershipTransitionService(
                lotRepository, lotLedgerRepository, java.time.Clock.systemUTC());
        automaticIdentityService = new AutomaticItemIdentityService(
                trackedItemRepository, lotRepository,
                ownershipTransitionService, lotOwnershipTransitionService,
                java.time.Clock.systemUTC());
        reconciliationMetrics = new ReconciliationMetrics();
        duplicateObservationRegistry = new dev.worldecho.domain.item.DuplicateObservationRegistry(300_000L);

        String serverSessionId = java.util.UUID.randomUUID().toString();
        inventoryReconciler = new PlayerInventoryReconciler(
                () -> settings,
                integrations,
                () -> bindingEnricher,
                itemIdentityAdapter,
                automaticIdentityService,
                reconciliationMetrics,
                duplicateObservationRegistry,
                serverSessionId
        );
        reconciliationScheduler = new PlayerInventoryReconciliationScheduler(
                this,
                () -> settings,
                inventoryReconciler,
                queryExecutor,
                reconciliationMetrics
        );

        getServer().getPluginManager().registerEvents(
                new PlayerInventoryObservationListener(
                        () -> settings,
                        reconciliationScheduler
                ),
                this
        );

        getServer().getPluginManager().registerEvents(
                new ItemTransformationListener(
                        () -> settings,
                        itemIdentityAdapter
                ),
                this
        );

        if (settings.automaticTrackingEnabled()) {
            reconciliationScheduler.scheduleForAllOnline("plugin-enable");
        }

        registerCommand();
        getLogger().info("WorldEcho memory kernel enabled");
    }

    @Override
    public void onDisable() {
        if (reconciliationScheduler != null) {
            reconciliationScheduler.shutdown();
        }
        if (reconciliationMetrics != null) {
            getLogger().info("Automatic tracking metrics: reconciliations="
                    + reconciliationMetrics.inventoryReconciliations()
                    + " identities-assigned=" + reconciliationMetrics.automaticIdentitiesAssigned()
                    + " lots-assigned=" + reconciliationMetrics.automaticLotsAssigned()
                    + " ownership-transitions=" + reconciliationMetrics.ownershipTransitionsRecorded()
                    + " warnings=" + reconciliationMetrics.identityWarnings()
                    + " duplicates=" + reconciliationMetrics.duplicateIdentityObservations());
        }
        if (writeQueue != null) {
            boolean drained = writeQueue.shutdown(settings.shutdownTimeout());
            StoryWriteQueue.QueueStatus status = writeQueue.status();
            if (drained) {
                getLogger().info("Story write queue drained: " + status.written() + " event(s) stored");
            } else {
                getLogger().warning("Story write queue did not drain in time; pending="
                        + status.pending() + " failed=" + status.failed()
                        + " dropped=" + status.dropped());
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

        getLogger().info("WorldEcho memory kernel disabled");
    }

    /**
     * Re-reads config.yml, bindings.yml, and the locale files. Persistence and providers
     * keep running so a reload can never lose queued memories.
     *
     * @return validation warnings that should be shown to the administrator
     */
    public List<String> reloadSettings() {
        boolean wasTrackingEnabled = settings != null && settings.automaticTrackingEnabled();
        reloadConfig();
        List<String> warnings = new ArrayList<>(applyConfiguration());
        warnings.addAll(loadBindings());
        if (!wasTrackingEnabled && settings.automaticTrackingEnabled() && reconciliationScheduler != null) {
            reconciliationScheduler.scheduleForAllOnline("reload-enable");
        }
        return warnings;
    }

    /**
     * Runs migrations on the storage thread and waits for the result.
     *
     * <p>Enable is not a tick, so waiting here is safe, and keeping the JDBC work off the
     * server thread preserves the rule that SQLite is only ever touched by WorldEcho
     * worker threads.</p>
     */
    private boolean initializeStorage() {
        try {
            int applied = queryExecutor
                    .submit(() -> databaseManager.initialize())
                    .get(STORAGE_INIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            getLogger().info("Storage ready at " + databaseManager.databasePath()
                    + " (" + applied + " migration(s) applied)");
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            getLogger().severe("Interrupted while preparing WorldEcho storage");
            return false;
        } catch (ExecutionException | TimeoutException exception) {
            getLogger().log(Level.SEVERE,
                    "WorldEcho could not initialize its database and will stay disabled",
                    exception);
            return false;
        }
    }

    private List<String> applyConfiguration() {
        SettingsLoadResult result =
                SettingsLoader.load(new BukkitConfigurationSource(getConfig()));
        settings = result.settings();
        itemValueScorer = new ItemValueScorer(settings.itemScoreWeights());
        messages = PaperMessageService.load(this, settings.locale());
        return result.warnings();
    }

    private void saveDefaultBindings() {
        Path destination = getDataFolder().toPath().resolve(BINDINGS_FILE);
        if (!Files.exists(destination)) {
            try {
                if (!Files.exists(getDataFolder().toPath())) {
                    Files.createDirectories(getDataFolder().toPath());
                }
                try (var stream = getResource(BINDINGS_FILE)) {
                    if (stream != null) {
                        Files.copy(stream, destination);
                    }
                }
            } catch (IOException exception) {
                getLogger().log(Level.WARNING, "Could not create default " + BINDINGS_FILE, exception);
            }
        }
    }

    /**
     * Loads (or reloads) bindings.yml. On startup, a fatal error falls back to an empty
     * registry. On reload, a fatal error keeps the previous registry.
     *
     * @return diagnostic messages for the administrator
     */
    private List<String> loadBindings() {
        Path bindingsPath = getDataFolder().toPath().resolve(BINDINGS_FILE);
        BindingLoadResult result;

        if (!Files.exists(bindingsPath)) {
            result = BindingLoader.load(new BukkitConfigurationSource(new YamlConfiguration()));
        } else {
            YamlConfiguration yaml = new YamlConfiguration();
            try (Reader reader = Files.newBufferedReader(bindingsPath, StandardCharsets.UTF_8)) {
                yaml.load(reader);
                result = BindingLoader.load(new BukkitConfigurationSource(yaml));
            } catch (IOException | org.bukkit.configuration.InvalidConfigurationException exception) {
                getLogger().log(Level.WARNING, "Could not read " + BINDINGS_FILE, exception);
                result = new BindingLoadResult(BindingRegistry.empty(), List.of(
                        new BindingDiagnostic(BindingDiagnostic.Severity.ERROR, BINDINGS_FILE,
                                "File could not be parsed: " + exception.getMessage())
                ), true);
            }
        }

        BindingRegistry previousRegistry =
                bindingEnricher != null ? bindingEnricher.registry() : null;
        BindingReloadCoordinator.ReloadDecision decision =
                BindingReloadCoordinator.decide(previousRegistry, result);

        List<String> messages = new ArrayList<>();
        if (decision.keptPrevious()) {
            getLogger().warning("Bindings reload failed; previous registry is kept");
            messages.add("Bindings reload failed; previous registry is kept");
            return messages;
        }

        BindingRegistry registry = decision.registry();
        bindingEnricher = new BindingEnricher(registry);

        for (BindingDiagnostic diagnostic : result.diagnostics()) {
            String entry = diagnostic.toString();
            messages.add(entry);
            if (diagnostic.isError()) {
                getLogger().warning("Bindings: " + entry);
            } else {
                getLogger().info("Bindings: " + entry);
            }
        }

        String summary = "Bindings: " + registry.entityBindingCount() + " entity, "
                + registry.itemBindingCount() + " item, "
                + registry.warningCount() + " warning(s), " + registry.errorCount() + " error(s)";
        getLogger().info(summary);
        messages.add(summary);

        return messages;
    }

    private void registerCommand() {
        PluginCommand command = getCommand("worldecho");
        if (command == null) {
            getLogger().severe("Command 'worldecho' is missing from plugin.yml");
            return;
        }

        WorldEchoCommand executor = new WorldEchoCommand(
                this,
                () -> settings,
                () -> messages,
                () -> itemValueScorer,
                () -> bindingEnricher,
                integrations,
                databaseManager,
                repository,
                writeQueue,
                queryExecutor,
                this::reloadSettings,
                itemIdentityAdapter,
                trackedItemRepository,
                ledgerRepository,
                reconciliationScheduler,
                reconciliationMetrics
        );
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
