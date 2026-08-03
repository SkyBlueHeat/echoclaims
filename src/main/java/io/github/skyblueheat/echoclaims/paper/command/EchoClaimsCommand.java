package io.github.skyblueheat.echoclaims.paper.command;

import io.github.skyblueheat.echoclaims.application.EvidenceLookupService;
import io.github.skyblueheat.echoclaims.application.StatusService;
import io.github.skyblueheat.echoclaims.domain.incident.Incident;
import io.github.skyblueheat.echoclaims.domain.snapshot.InventorySnapshot;
import io.github.skyblueheat.echoclaims.domain.snapshot.SnapshotItem;
import io.github.skyblueheat.echoclaims.paper.message.MessageService;
import io.github.skyblueheat.echoclaims.paper.message.PaperMessageService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Administration command for EchoClaims.
 *
 * <p>Database access always runs on the query executor and results are sent back on the
 * server thread.</p>
 */
public final class EchoClaimsCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION_ADMIN = "echoclaims.admin";
    public static final String PERMISSION_STATUS = "echoclaims.command.status";
    public static final String PERMISSION_INCIDENTS = "echoclaims.command.incidents";
    public static final String PERMISSION_INCIDENT = "echoclaims.command.incident";
    public static final String PERMISSION_SNAPSHOT = "echoclaims.command.snapshot";

    private static final List<String> SUBCOMMANDS =
            List.of("status", "incidents", "incident", "snapshot");

    private final Plugin plugin;
    private final Supplier<MessageService> messageSupplier;
    private final Supplier<StatusService> statusServiceSupplier;
    private final Supplier<EvidenceLookupService> evidenceLookupSupplier;
    private final ExecutorService queryExecutor;
    private final Consumer<Runnable> syncScheduler;
    private final Logger logger;

    public EchoClaimsCommand(
            Plugin plugin,
            Supplier<MessageService> messageSupplier,
            Supplier<StatusService> statusServiceSupplier,
            Supplier<EvidenceLookupService> evidenceLookupSupplier,
            ExecutorService queryExecutor,
            Consumer<Runnable> syncScheduler,
            Logger logger
    ) {
        this.plugin = plugin;
        this.messageSupplier = Objects.requireNonNull(messageSupplier, "messageSupplier");
        this.statusServiceSupplier = Objects.requireNonNull(statusServiceSupplier, "statusServiceSupplier");
        this.evidenceLookupSupplier = Objects.requireNonNull(evidenceLookupSupplier, "evidenceLookupSupplier");
        this.queryExecutor = Objects.requireNonNull(queryExecutor, "queryExecutor");
        this.syncScheduler = Objects.requireNonNull(syncScheduler, "syncScheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (args.length == 0) {
            messages().send(sender, "usage");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_STATUS)) {
                    messages().send(sender, "no-permission");
                    return true;
                }
                status(sender);
            }
            case "incidents" -> {
                if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_INCIDENTS)) {
                    messages().send(sender, "no-permission");
                    return true;
                }
                incidents(sender, args);
            }
            case "incident" -> {
                if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_INCIDENT)) {
                    messages().send(sender, "no-permission");
                    return true;
                }
                incident(sender, args);
            }
            case "snapshot" -> {
                if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_SNAPSHOT)) {
                    messages().send(sender, "no-permission");
                    return true;
                }
                snapshot(sender, args);
            }
            default -> messages().send(sender, "unknown-subcommand");
        }

        return true;
    }

    private void status(CommandSender sender) {
        StatusService service = statusServiceSupplier.get();
        if (service == null) {
            messages().send(sender, "not-ready");
            return;
        }
        queryExecutor.submit(() -> {
            StatusService.StatusReport report = service.collect();
            syncScheduler.accept(() -> sendStatusReport(sender, report));
        });
    }

    private void sendStatusReport(CommandSender sender, StatusService.StatusReport report) {
        messages().send(sender, "status-header");
        line(sender, "version", report.version());
        line(sender, "locale", report.locale());
        line(sender, "database", report.databaseAvailable() ? "ok" : "unavailable");
        line(sender, "schema-version", Integer.toString(report.schemaVersion()));
        line(sender, "queue.pending", Integer.toString(report.pendingWrites()));
        line(sender, "queue.written", Long.toString(report.writtenCount()));
        line(sender, "queue.failed", Long.toString(report.failedCount()));
        line(sender, "queue.dropped", Long.toString(report.droppedCount()));
        line(sender, "queue.overflow-dropped", Long.toString(report.overflowDroppedCount()));
        line(sender, "queue.abandoned", Long.toString(report.abandonedCount()));
        line(sender, "providers", String.join(", ", report.providers()));
        line(sender, "uptime", formatUptime(report.uptimeMillis()));
        line(sender, "evidence.accepted", Long.toString(report.evidenceAccepted()));
        line(sender, "evidence.snapshots", Long.toString(report.evidenceSnapshots()));
        line(sender, "evidence.incidents", Long.toString(report.evidenceIncidents()));
        line(sender, "evidence.duplicates", Long.toString(report.evidenceDuplicates()));
        line(sender, "evidence.failed", Long.toString(report.evidenceFailed()));
        line(sender, "evidence.rejected", Long.toString(report.evidenceRejected()));
        line(sender, "evidence.pending", Integer.toString(report.evidencePending()));
    }

    private void incidents(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages().send(sender, "usage");
            return;
        }

        String input = args[1];
        Optional<UUID> playerUuid = resolvePlayerUuid(input);
        if (playerUuid.isEmpty()) {
            messages().send(sender, "invalid-player", Map.of("input", input));
            return;
        }

        EvidenceLookupService lookup = evidenceLookupSupplier.get();
        if (lookup == null) {
            messages().send(sender, "not-ready");
            return;
        }

        UUID uuid = playerUuid.get();
        String playerName = resolvePlayerName(uuid);

        queryExecutor.submit(() -> {
            try {
                List<Incident> incidents = lookup.findIncidentsByPlayer(uuid);
                syncScheduler.accept(() -> sendIncidentsList(sender, incidents, playerName, lookup.maxRecentResults()));
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Evidence lookup failed for incidents " + uuid, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void sendIncidentsList(CommandSender sender, List<Incident> incidents, String playerName, int max) {
        if (incidents.isEmpty()) {
            messages().send(sender, "incidents-empty", Map.of("player", playerName));
            return;
        }

        messages().send(sender, "incidents-header", Map.of("player", playerName));
        for (int i = 0; i < incidents.size(); i++) {
            Incident incident = incidents.get(i);
            messages().send(sender, "incidents-entry", Map.of(
                    "index", Integer.toString(i + 1),
                    "type", incident.type().name(),
                    "time", formatTimestamp(incident.occurredAt()),
                    "id", incident.id().toString()
            ));
        }
        messages().send(sender, "incidents-count", Map.of(
                "count", Integer.toString(incidents.size()),
                "max", Integer.toString(max)
        ));
    }

    private void incident(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages().send(sender, "usage");
            return;
        }

        String input = args[1];
        Optional<UUID> incidentUuid = parseUuid(input);
        if (incidentUuid.isEmpty()) {
            messages().send(sender, "invalid-uuid", Map.of("input", input));
            return;
        }

        EvidenceLookupService lookup = evidenceLookupSupplier.get();
        if (lookup == null) {
            messages().send(sender, "not-ready");
            return;
        }

        UUID uuid = incidentUuid.get();

        queryExecutor.submit(() -> {
            try {
                Optional<Incident> result = lookup.findIncidentById(uuid);
                syncScheduler.accept(() -> sendIncidentDetail(sender, result, uuid));
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Evidence lookup failed for incident " + uuid, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void sendIncidentDetail(CommandSender sender, Optional<Incident> result, UUID uuid) {
        if (result.isEmpty()) {
            messages().send(sender, "incident-not-found", Map.of("id", uuid.toString()));
            return;
        }

        Incident incident = result.get();
        messages().send(sender, "incident-header", Map.of("id", incident.id().toString()));
        line(sender, "incident-type", incident.type().name());
        line(sender, "incident-player", incident.playerUuid().toString());
        line(sender, "incident-occurred", formatTimestamp(incident.occurredAt()));
        line(sender, "incident-world", incident.worldId());
        if (incident.hasCoordinates()) {
            line(sender, "incident-coords",
                    incident.coordinates().x() + ", " + incident.coordinates().y() + ", " + incident.coordinates().z());
        }
        line(sender, "incident-cause", incident.cause());
        if (incident.hasKillerPlayer()) {
            line(sender, "incident-killer", "player:" + incident.killerPlayerUuid());
        } else if (incident.hasKillerEntity()) {
            line(sender, "incident-killer", incident.killerEntityKey());
        }
        line(sender, "incident-status", incident.status().name());
        line(sender, "incident-pre-snapshot", incident.preEventSnapshotUuid().toString());
        if (incident.hasPostEventSnapshot()) {
            line(sender, "incident-post-snapshot", incident.postEventSnapshotUuid().toString());
        } else {
            messages().send(sender, "incident-no-post-snapshot");
        }
        if (incident.hasMetadata()) {
            line(sender, "incident-metadata", formatMetadata(incident.metadata()));
        }
    }

    private void snapshot(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages().send(sender, "usage");
            return;
        }

        String input = args[1];
        Optional<UUID> snapshotUuid = parseUuid(input);
        if (snapshotUuid.isEmpty()) {
            messages().send(sender, "invalid-uuid", Map.of("input", input));
            return;
        }

        EvidenceLookupService lookup = evidenceLookupSupplier.get();
        if (lookup == null) {
            messages().send(sender, "not-ready");
            return;
        }

        UUID uuid = snapshotUuid.get();

        queryExecutor.submit(() -> {
            try {
                Optional<InventorySnapshot> result = lookup.findSnapshotById(uuid);
                syncScheduler.accept(() -> sendSnapshotDetail(sender, result, uuid));
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Evidence lookup failed for snapshot " + uuid, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void sendSnapshotDetail(CommandSender sender, Optional<InventorySnapshot> result, UUID uuid) {
        if (result.isEmpty()) {
            messages().send(sender, "snapshot-not-found", Map.of("id", uuid.toString()));
            return;
        }

        InventorySnapshot snapshot = result.get();
        messages().send(sender, "snapshot-header", Map.of("id", snapshot.id().toString()));
        line(sender, "snapshot-player", snapshot.playerUuid().toString());
        line(sender, "snapshot-reason", snapshot.captureReason().name());
        line(sender, "snapshot-captured", formatTimestamp(snapshot.capturedAt()));
        line(sender, "snapshot-world", snapshot.worldId());
        if (snapshot.hasCoordinates()) {
            line(sender, "snapshot-coords",
                    snapshot.coordinates().x() + ", " + snapshot.coordinates().y() + ", " + snapshot.coordinates().z());
        }
        line(sender, "snapshot-gamemode", snapshot.gameMode());
        line(sender, "snapshot-health", String.format(java.util.Locale.ROOT, "%.1f", snapshot.health()));
        line(sender, "snapshot-food", Integer.toString(snapshot.foodLevel()));
        line(sender, "snapshot-xp",
                snapshot.experienceLevel() + " (" + snapshot.totalExperience() + ")");

        List<SnapshotItem> items = snapshot.allItems();
        if (items.isEmpty()) {
            messages().send(sender, "snapshot-no-items");
            return;
        }

        messages().send(sender, "snapshot-items-header", Map.of("count", Integer.toString(items.size())));
        for (SnapshotItem item : items) {
            messages().send(sender, "snapshot-item-entry", Map.of(
                    "slot", item.slot(),
                    "material", item.materialKey(),
                    "amount", Integer.toString(item.amount()),
                    "score", Integer.toString(item.score())
            ));
        }
    }

    private static String formatTimestamp(long epochMillis) {
        return java.time.Instant.ofEpochMilli(epochMillis).toString();
    }

    private static String formatMetadata(Map<String, String> metadata) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static boolean hasPermission(CommandSender sender, String admin, String specific) {
        return sender.hasPermission(admin) || sender.hasPermission(specific);
    }

    private Optional<UUID> resolvePlayerUuid(String input) {
        Optional<UUID> parsed = parseUuid(input);
        if (parsed.isPresent()) {
            return parsed;
        }

        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(input);
            if (player != null && player.getUniqueId() != null) {
                return Optional.of(player.getUniqueId());
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private static String resolvePlayerName(UUID uuid) {
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            String name = player != null ? player.getName() : null;
            return name != null ? name : uuid.toString();
        } catch (Exception exception) {
            return uuid.toString();
        }
    }

    private static Optional<UUID> parseUuid(String input) {
        try {
            return Optional.of(UUID.fromString(input));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static String formatUptime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m " + (seconds % 60) + "s";
        }
        if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        }
        return seconds + "s";
    }

    private void line(CommandSender sender, String key, String value) {
        messages().send(sender, "status-line", Map.of("key", key, "value", value));
    }

    private MessageService messages() {
        return messageSupplier.get();
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        return List.of();
    }

    static List<String> filter(List<String> candidates, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new java.util.ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.startsWith(normalized)) {
                matches.add(candidate);
            }
        }
        return List.copyOf(matches);
    }
}
