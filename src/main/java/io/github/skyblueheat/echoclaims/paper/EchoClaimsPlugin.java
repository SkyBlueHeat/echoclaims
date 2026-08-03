package io.github.skyblueheat.echoclaims.paper;

import io.github.skyblueheat.echoclaims.application.ItemValueScorer;
import io.github.skyblueheat.echoclaims.application.StatusService;
import io.github.skyblueheat.echoclaims.config.EchoClaimsSettings;
import io.github.skyblueheat.echoclaims.config.SettingsLoadResult;
import io.github.skyblueheat.echoclaims.config.SettingsLoader;
import io.github.skyblueheat.echoclaims.integration.IntegrationRegistry;
import io.github.skyblueheat.echoclaims.integration.vanilla.VanillaEntityProvider;
import io.github.skyblueheat.echoclaims.integration.vanilla.VanillaItemProvider;
import io.github.skyblueheat.echoclaims.paper.command.EchoClaimsCommand;
import io.github.skyblueheat.echoclaims.paper.config.BukkitConfigurationSource;
import io.github.skyblueheat.echoclaims.paper.message.PaperMessageService;
import io.github.skyblueheat.echoclaims.persistence.AuditRecordRepository;
import io.github.skyblueheat.echoclaims.persistence.AuditWriteQueue;
import io.github.skyblueheat.echoclaims.persistence.DatabaseManager;
import io.github.skyblueheat.echoclaims.persistence.SqliteAuditRecordRepository;
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

    private IntegrationRegistry integrations;
    private DatabaseManager databaseManager;
    private AuditRecordRepository auditRepository;
    private AuditWriteQueue writeQueue;
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

        integrations = new IntegrationRegistry((providerId, throwable) -> getLogger().log(
                Level.WARNING, "Content provider '" + providerId + "' failed", throwable));
        integrations.registerEntityProvider(new VanillaEntityProvider());
        integrations.registerItemProvider(new VanillaItemProvider());
        integrations.freeze();
        getLogger().info("Content providers: "
                + String.join(", ", integrations.describeProviders()));

        statusService = new StatusService(
                () -> settings,
                databaseManager,
                writeQueue,
                integrations,
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
                () -> messages,
                () -> statusService,
                queryExecutor,
                runnable -> getServer().getScheduler().runTask(this, runnable)
        );
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
