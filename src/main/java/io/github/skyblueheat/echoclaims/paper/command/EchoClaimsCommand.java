package io.github.skyblueheat.echoclaims.paper.command;

import io.github.skyblueheat.echoclaims.application.ClaimCreationService;
import io.github.skyblueheat.echoclaims.application.ClaimEligibilityService;
import io.github.skyblueheat.echoclaims.application.ClaimLookupService;
import io.github.skyblueheat.echoclaims.application.ClaimRateLimitService;
import io.github.skyblueheat.echoclaims.application.ClaimTransitionService;
import io.github.skyblueheat.echoclaims.application.EvidenceLookupService;
import io.github.skyblueheat.echoclaims.application.StatusService;
import io.github.skyblueheat.echoclaims.config.EchoClaimsSettings;
import io.github.skyblueheat.echoclaims.domain.claim.Claim;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimAuditEntry;
import io.github.skyblueheat.echoclaims.domain.claim.ClaimStatus;
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
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
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
    public static final String PERMISSION_CLAIM_LIST = "echoclaims.command.claim.list";
    public static final String PERMISSION_CLAIM_VIEW = "echoclaims.command.claim.view";
    public static final String PERMISSION_CLAIM_CREATE = "echoclaims.command.claim.create";
    public static final String PERMISSION_CLAIM_CANCEL = "echoclaims.command.claim.cancel";
    public static final String PERMISSION_CLAIM_SUBMIT = "echoclaims.command.claim.submit";
    public static final String PERMISSION_CLAIM_STAFF_VIEW = "echoclaims.command.claim.staff.view";
    public static final String PERMISSION_CLAIM_STAFF_LIST = "echoclaims.command.claim.staff.list";

    private static final List<String> SUBCOMMANDS =
            List.of("status", "incidents", "incident", "snapshot",
                    "claim");

    private static final List<String> CLAIM_SUBCOMMANDS =
            List.of("list", "view", "create", "cancel", "submit", "claimable");

    private static final List<String> CLAIM_STAFF_SUBCOMMANDS =
            List.of("staff-view", "staff-list");

    private final Plugin plugin;
    private final Supplier<MessageService> messageSupplier;
    private final Supplier<StatusService> statusServiceSupplier;
    private final Supplier<EvidenceLookupService> evidenceLookupSupplier;
    private final Supplier<ClaimLookupService> claimLookupSupplier;
    private final Supplier<ClaimCreationService> claimCreationSupplier;
    private final Supplier<ClaimTransitionService> claimTransitionSupplier;
    private final Supplier<ClaimRateLimitService> claimRateLimitSupplier;
    private final Supplier<EchoClaimsSettings> settingsSupplier;
    private final ExecutorService queryExecutor;
    private final Consumer<Runnable> syncScheduler;
    private final Logger logger;

    public EchoClaimsCommand(
            Plugin plugin,
            Supplier<MessageService> messageSupplier,
            Supplier<StatusService> statusServiceSupplier,
            Supplier<EvidenceLookupService> evidenceLookupSupplier,
            Supplier<ClaimLookupService> claimLookupSupplier,
            Supplier<ClaimCreationService> claimCreationSupplier,
            Supplier<ClaimTransitionService> claimTransitionSupplier,
            Supplier<ClaimRateLimitService> claimRateLimitSupplier,
            Supplier<EchoClaimsSettings> settingsSupplier,
            ExecutorService queryExecutor,
            Consumer<Runnable> syncScheduler,
            Logger logger
    ) {
        this.plugin = plugin;
        this.messageSupplier = Objects.requireNonNull(messageSupplier, "messageSupplier");
        this.statusServiceSupplier = Objects.requireNonNull(statusServiceSupplier, "statusServiceSupplier");
        this.evidenceLookupSupplier = Objects.requireNonNull(evidenceLookupSupplier, "evidenceLookupSupplier");
        this.claimLookupSupplier = Objects.requireNonNull(claimLookupSupplier, "claimLookupSupplier");
        this.claimCreationSupplier = Objects.requireNonNull(claimCreationSupplier, "claimCreationSupplier");
        this.claimTransitionSupplier = Objects.requireNonNull(claimTransitionSupplier, "claimTransitionSupplier");
        this.claimRateLimitSupplier = Objects.requireNonNull(claimRateLimitSupplier, "claimRateLimitSupplier");
        this.settingsSupplier = Objects.requireNonNull(settingsSupplier, "settingsSupplier");
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
            case "claim" -> handleClaim(sender, args);
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
        line(sender, "claims.enabled", Boolean.toString(report.claimsEnabled()));
        line(sender, "claims.total", Long.toString(report.totalClaims()));
        line(sender, "claims.draft", Long.toString(report.draftClaims()));
        line(sender, "claims.submitted", Long.toString(report.submittedClaims()));
        line(sender, "claims.cancelled", Long.toString(report.cancelledClaims()));
        line(sender, "claims.audit-entries", Long.toString(report.totalClaimAuditEntries()));
        line(sender, "claims.created", Long.toString(report.claimsCreated()));
        line(sender, "claims.submitted-metric", Long.toString(report.claimsSubmitted()));
        line(sender, "claims.cancelled-metric", Long.toString(report.claimsCancelled()));
        line(sender, "claims.rejected", Long.toString(report.claimsRejected()));
        line(sender, "claims.duplicate-open", Long.toString(report.duplicateOpenClaims()));
        line(sender, "claims.rate-limited", Long.toString(report.rateLimitedClaims()));
        line(sender, "claims.concurrency-conflicts", Long.toString(report.claimConcurrencyConflicts()));
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

    private void handleClaim(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages().send(sender, "claim-usage");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> claimList(sender);
            case "view" -> claimView(sender, args);
            case "create" -> claimCreate(sender, args);
            case "cancel" -> claimCancel(sender, args);
            case "submit" -> claimSubmit(sender, args);
            case "claimable" -> claimClaimable(sender);
            case "staff-view" -> claimStaffView(sender, args);
            case "staff-list" -> claimStaffList(sender, args);
            default -> messages().send(sender, "claim-unknown-subcommand");
        }
    }

    private void claimList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "claim-player-only");
            return;
        }
        if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_CLAIM_LIST)) {
            messages().send(sender, "no-permission");
            return;
        }

        ClaimLookupService lookup = claimLookupSupplier.get();
        if (lookup == null) {
            messages().send(sender, "not-ready");
            return;
        }

        UUID playerUuid = player.getUniqueId();
        queryExecutor.submit(() -> {
            try {
                List<Claim> claims = lookup.findClaimsByPlayer(playerUuid);
                syncScheduler.accept(() -> sendClaimList(sender, claims, lookup.maxRecentResults()));
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Claim list failed for " + playerUuid, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void sendClaimList(CommandSender sender, List<Claim> claims, int max) {
        if (claims.isEmpty()) {
            messages().send(sender, "claim-list-empty");
            return;
        }

        messages().send(sender, "claim-list-header");
        for (int i = 0; i < claims.size(); i++) {
            Claim claim = claims.get(i);
            messages().send(sender, "claim-list-entry", Map.of(
                    "index", Integer.toString(i + 1),
                    "reference", claim.publicReference(),
                    "status", claim.status().name(),
                    "time", formatTimestamp(claim.createdAt())
            ));
        }
        messages().send(sender, "claim-list-count", Map.of(
                "count", Integer.toString(claims.size()),
                "max", Integer.toString(max)
        ));
    }

    private void claimView(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "claim-player-only");
            return;
        }
        if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_CLAIM_VIEW)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            messages().send(sender, "claim-usage");
            return;
        }

        String reference = args[2];
        ClaimLookupService lookup = claimLookupSupplier.get();
        if (lookup == null) {
            messages().send(sender, "not-ready");
            return;
        }

        UUID playerUuid = player.getUniqueId();
        queryExecutor.submit(() -> {
            try {
                Optional<Claim> claimOpt = lookup.findClaimByPublicReference(reference);
                if (claimOpt.isPresent() && !claimOpt.get().playerUuid().equals(playerUuid)
                        && !sender.hasPermission(PERMISSION_ADMIN)) {
                    syncScheduler.accept(() -> messages().send(sender, "claim-not-found",
                            Map.of("reference", reference)));
                    return;
                }
                Optional<List<ClaimAuditEntry>> auditOpt = claimOpt.isPresent()
                        ? Optional.of(lookup.findAuditEntries(claimOpt.get().id()))
                        : Optional.empty();
                syncScheduler.accept(() -> sendClaimDetail(sender, claimOpt, auditOpt, reference));
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Claim view failed for " + reference, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void sendClaimDetail(CommandSender sender, Optional<Claim> claimOpt,
                                  Optional<List<ClaimAuditEntry>> auditOpt, String reference) {
        if (claimOpt.isEmpty()) {
            messages().send(sender, "claim-not-found", Map.of("reference", reference));
            return;
        }

        Claim claim = claimOpt.get();
        messages().send(sender, "claim-detail-header", Map.of("reference", claim.publicReference()));
        line(sender, "claim-id", claim.id().toString());
        line(sender, "claim-status", claim.status().name());
        line(sender, "claim-incident", claim.incidentId().toString());
        line(sender, "claim-created-at", formatTimestamp(claim.createdAt()));
        if (claim.submittedAt() > 0) {
            line(sender, "claim-submitted", formatTimestamp(claim.submittedAt()));
        }
        if (claim.cancelledAt() > 0) {
            line(sender, "claim-cancelled", formatTimestamp(claim.cancelledAt()));
        }
        if (claim.hasDescription()) {
            line(sender, "claim-description", claim.description());
        }
        line(sender, "claim-version", Integer.toString(claim.version()));

        List<ClaimAuditEntry> audit = auditOpt.orElse(List.of());
        if (!audit.isEmpty()) {
            messages().send(sender, "claim-audit-header", Map.of("count", Integer.toString(audit.size())));
            for (ClaimAuditEntry entry : audit) {
                messages().send(sender, "claim-audit-entry", Map.of(
                        "action", entry.action().name(),
                        "actor", entry.actorType().name(),
                        "time", formatTimestamp(entry.recordedAt()),
                        "version", Integer.toString(entry.claimVersion())
                ));
            }
        }
    }

    private void claimCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "claim-player-only");
            return;
        }
        if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_CLAIM_CREATE)) {
            messages().send(sender, "no-permission");
            return;
        }

        EchoClaimsSettings settings = settingsSupplier.get();
        if (!settings.claimsEnabled()) {
            messages().send(sender, "claim-disabled");
            return;
        }

        if (args.length < 3) {
            messages().send(sender, "claim-usage");
            return;
        }

        String incidentInput = args[2];
        String description = "";
        if (args.length > 3) {
            StringBuilder sb = new StringBuilder();
            for (int i = 3; i < args.length; i++) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(args[i]);
            }
            description = sb.toString();
        }
        if (description.length() > settings.claimMaxDescriptionLength()) {
            description = description.substring(0, settings.claimMaxDescriptionLength());
        }

        UUID playerUuid = player.getUniqueId();
        ClaimRateLimitService rateLimiter = claimRateLimitSupplier.get();
        if (rateLimiter != null) {
            ClaimRateLimitService.RateLimitResult rateResult = rateLimiter.check(playerUuid);
            if (!rateResult.isAllowed()) {
                messages().send(sender, "claim-rate-limited", Map.of(
                        "seconds", Long.toString(rateResult.remainingMillis() / 1000)
                ));
                return;
            }
        }

        EvidenceLookupService evidenceLookup = evidenceLookupSupplier.get();
        ClaimCreationService creationService = claimCreationSupplier.get();
        ClaimLookupService lookup = claimLookupSupplier.get();
        if (evidenceLookup == null || creationService == null || lookup == null) {
            messages().send(sender, "not-ready");
            return;
        }

        String finalDescription = description;
        queryExecutor.submit(() -> {
            try {
                Optional<Incident> incidentOpt = resolveIncident(evidenceLookup, incidentInput, playerUuid);
                if (incidentOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "claim-incident-not-found",
                            Map.of("input", incidentInput)));
                    return;
                }
                Incident incident = incidentOpt.get();
                UUID incidentId = incident.id();

                ClaimEligibilityService.EligibilityResult eligibility =
                        ClaimEligibilityService.evaluate(incident, playerUuid);
                if (!eligibility.isEligible()) {
                    String reason = eligibility.rejectionReason().orElse("Not eligible");
                    syncScheduler.accept(() -> messages().send(sender, "claim-not-eligible",
                            Map.of("reason", reason)));
                    return;
                }

                List<Claim> openClaims = lookup.findOpenClaimsByIncident(incidentId);
                if (!openClaims.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "claim-duplicate-open",
                            Map.of("reference", openClaims.get(0).publicReference())));
                    return;
                }

                Claim claim = creationService.createClaim(incident, playerUuid, finalDescription);
                if (rateLimiter != null) {
                    rateLimiter.recordCreation(playerUuid);
                }
                syncScheduler.accept(() -> messages().send(sender, "claim-created", Map.of(
                        "reference", claim.publicReference()
                )));
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Claim creation failed for input " + incidentInput, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private Optional<Incident> resolveIncident(
            EvidenceLookupService evidenceLookup,
            String input,
            UUID playerUuid
    ) throws java.sql.SQLException {
        Optional<UUID> uuidOpt = parseUuid(input);
        if (uuidOpt.isPresent()) {
            return evidenceLookup.findIncidentById(uuidOpt.get());
        }

        try {
            int index = Integer.parseInt(input);
            if (index < 1) {
                return Optional.empty();
            }
            List<Incident> incidents = evidenceLookup.findIncidentsByPlayer(playerUuid);
            if (index > incidents.size()) {
                return Optional.empty();
            }
            return Optional.of(incidents.get(index - 1));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private void claimClaimable(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "claim-player-only");
            return;
        }
        if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_CLAIM_CREATE)) {
            messages().send(sender, "no-permission");
            return;
        }

        EvidenceLookupService evidenceLookup = evidenceLookupSupplier.get();
        ClaimLookupService lookup = claimLookupSupplier.get();
        if (evidenceLookup == null || lookup == null) {
            messages().send(sender, "not-ready");
            return;
        }

        UUID playerUuid = player.getUniqueId();
        queryExecutor.submit(() -> {
            try {
                List<Incident> incidents = evidenceLookup.findIncidentsByPlayer(playerUuid);
                List<Incident> openIncidents = new ArrayList<>();
                for (Incident incident : incidents) {
                    if (incident.status() == io.github.skyblueheat.echoclaims.domain.incident.IncidentStatus.OPEN) {
                        List<Claim> openClaims = lookup.findOpenClaimsByIncident(incident.id());
                        if (openClaims.isEmpty()) {
                            openIncidents.add(incident);
                        }
                    }
                }
                syncScheduler.accept(() -> sendClaimableList(sender, openIncidents));
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Claimable list failed for " + playerUuid, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void sendClaimableList(CommandSender sender, List<Incident> incidents) {
        if (incidents.isEmpty()) {
            messages().send(sender, "claim-claimable-empty");
            return;
        }
        messages().send(sender, "claim-claimable-header");
        for (int i = 0; i < incidents.size(); i++) {
            Incident incident = incidents.get(i);
            messages().send(sender, "claim-claimable-entry", Map.of(
                    "index", Integer.toString(i + 1),
                    "type", incident.type().name(),
                    "time", formatTimestamp(incident.occurredAt()),
                    "cause", incident.cause()
            ));
        }
        messages().send(sender, "claim-claimable-footer");
    }

    private void claimCancel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "claim-player-only");
            return;
        }
        if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_CLAIM_CANCEL)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            messages().send(sender, "claim-usage");
            return;
        }

        String reference = args[2];
        transitionClaim(sender, player, reference, ClaimStatus.CANCELLED, "");
    }

    private void claimSubmit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "claim-player-only");
            return;
        }
        if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_CLAIM_SUBMIT)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            messages().send(sender, "claim-usage");
            return;
        }

        String reference = args[2];
        transitionClaim(sender, player, reference, ClaimStatus.SUBMITTED, "");
    }

    private void transitionClaim(CommandSender sender, Player player, String reference,
                                  ClaimStatus target, String reason) {
        ClaimLookupService lookup = claimLookupSupplier.get();
        ClaimTransitionService transitionService = claimTransitionSupplier.get();
        if (lookup == null || transitionService == null) {
            messages().send(sender, "not-ready");
            return;
        }

        UUID playerUuid = player.getUniqueId();
        queryExecutor.submit(() -> {
            try {
                Optional<Claim> claimOpt = lookup.findClaimByPublicReference(reference);
                if (claimOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "claim-not-found",
                            Map.of("reference", reference)));
                    return;
                }
                Claim claim = claimOpt.get();
                if (!claim.playerUuid().equals(playerUuid) && !sender.hasPermission(PERMISSION_ADMIN)) {
                    syncScheduler.accept(() -> messages().send(sender, "claim-not-found",
                            Map.of("reference", reference)));
                    return;
                }

                ClaimTransitionService.TransitionResult result =
                        transitionService.transition(claim, target, playerUuid, reason);
                if (result.isSuccess()) {
                    String key = target == ClaimStatus.SUBMITTED ? "claim-submitted-success" : "claim-cancelled-success";
                    syncScheduler.accept(() -> messages().send(sender, key,
                            Map.of("reference", claim.publicReference())));
                } else if (result.isConcurrencyConflict()) {
                    syncScheduler.accept(() -> messages().send(sender, "claim-concurrency-conflict",
                            Map.of("reference", claim.publicReference())));
                } else {
                    String reasonText = result.rejectionReason().orElse("Transition rejected");
                    syncScheduler.accept(() -> messages().send(sender, "claim-transition-rejected",
                            Map.of("reference", claim.publicReference(), "reason", reasonText)));
                }
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Claim transition failed for " + reference, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void claimStaffView(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_CLAIM_STAFF_VIEW)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            messages().send(sender, "claim-usage");
            return;
        }

        String reference = args[2];
        ClaimLookupService lookup = claimLookupSupplier.get();
        if (lookup == null) {
            messages().send(sender, "not-ready");
            return;
        }

        queryExecutor.submit(() -> {
            try {
                Optional<Claim> claimOpt = lookup.findClaimByPublicReference(reference);
                Optional<List<ClaimAuditEntry>> auditOpt = claimOpt.isPresent()
                        ? Optional.of(lookup.findAuditEntries(claimOpt.get().id()))
                        : Optional.empty();
                syncScheduler.accept(() -> sendClaimDetail(sender, claimOpt, auditOpt, reference));
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Staff claim view failed for " + reference, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void claimStaffList(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_CLAIM_STAFF_LIST)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            messages().send(sender, "claim-usage");
            return;
        }

        String input = args[2];
        Optional<UUID> playerUuid = resolvePlayerUuid(input);
        if (playerUuid.isEmpty()) {
            messages().send(sender, "invalid-player", Map.of("input", input));
            return;
        }

        ClaimLookupService lookup = claimLookupSupplier.get();
        if (lookup == null) {
            messages().send(sender, "not-ready");
            return;
        }

        UUID uuid = playerUuid.get();
        String playerName = resolvePlayerName(uuid);
        queryExecutor.submit(() -> {
            try {
                List<Claim> claims = lookup.findClaimsByPlayer(uuid);
                syncScheduler.accept(() -> {
                    if (claims.isEmpty()) {
                        messages().send(sender, "claim-staff-list-empty", Map.of("player", playerName));
                        return;
                    }
                    messages().send(sender, "claim-staff-list-header", Map.of("player", playerName));
                    for (int i = 0; i < claims.size(); i++) {
                        Claim claim = claims.get(i);
                        messages().send(sender, "claim-staff-list-entry", Map.of(
                                "index", Integer.toString(i + 1),
                                "reference", claim.publicReference(),
                                "status", claim.status().name(),
                                "time", formatTimestamp(claim.createdAt())
                        ));
                    }
                    messages().send(sender, "claim-list-count", Map.of(
                            "count", Integer.toString(claims.size()),
                            "max", Integer.toString(lookup.maxRecentResults())
                    ));
                });
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Staff claim list failed for " + uuid, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
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

        if (args.length == 2 && args[0].equalsIgnoreCase("claim")) {
            List<String> subs = new ArrayList<>(CLAIM_SUBCOMMANDS);
            if (sender.hasPermission(PERMISSION_ADMIN) || sender.hasPermission(PERMISSION_CLAIM_STAFF_VIEW)) {
                subs.add("staff-view");
            }
            if (sender.hasPermission(PERMISSION_ADMIN) || sender.hasPermission(PERMISSION_CLAIM_STAFF_LIST)) {
                subs.add("staff-list");
            }
            return filter(subs, args[1]);
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
