package io.github.skyblueheat.echoclaims.paper.command;

import io.github.skyblueheat.echoclaims.application.StatusService;
import io.github.skyblueheat.echoclaims.paper.message.PaperMessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Administration command for EchoClaims.
 *
 * <p>Database access always runs on the query executor and results are sent back on the
 * server thread.</p>
 */
public final class EchoClaimsCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION_ADMIN = "echoclaims.admin";
    public static final String PERMISSION_STATUS = "echoclaims.command.status";

    private static final List<String> SUBCOMMANDS = List.of("status");

    private final Plugin plugin;
    private final Supplier<PaperMessageService> messageSupplier;
    private final Supplier<StatusService> statusServiceSupplier;
    private final ExecutorService queryExecutor;
    private final Consumer<Runnable> syncScheduler;

    public EchoClaimsCommand(
            Plugin plugin,
            Supplier<PaperMessageService> messageSupplier,
            Supplier<StatusService> statusServiceSupplier,
            ExecutorService queryExecutor,
            Consumer<Runnable> syncScheduler
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.messageSupplier = Objects.requireNonNull(messageSupplier, "messageSupplier");
        this.statusServiceSupplier = Objects.requireNonNull(statusServiceSupplier, "statusServiceSupplier");
        this.queryExecutor = Objects.requireNonNull(queryExecutor, "queryExecutor");
        this.syncScheduler = Objects.requireNonNull(syncScheduler, "syncScheduler");
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!sender.hasPermission(PERMISSION_ADMIN) && !sender.hasPermission(PERMISSION_STATUS)) {
            messages().send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            messages().send(sender, "usage");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender);
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

    private PaperMessageService messages() {
        return messageSupplier.get();
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (!sender.hasPermission(PERMISSION_ADMIN) && !sender.hasPermission(PERMISSION_STATUS)) {
            return List.of();
        }

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
