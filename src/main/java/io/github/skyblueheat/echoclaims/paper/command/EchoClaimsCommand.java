package io.github.skyblueheat.echoclaims.paper.command;

import io.github.skyblueheat.echoclaims.application.ClaimCreationService;
import io.github.skyblueheat.echoclaims.application.ClaimEligibilityService;
import io.github.skyblueheat.echoclaims.application.ClaimLookupService;
import io.github.skyblueheat.echoclaims.application.ClaimRateLimitService;
import io.github.skyblueheat.echoclaims.application.ClaimTransitionService;
import io.github.skyblueheat.echoclaims.application.EvidenceLookupService;
import io.github.skyblueheat.echoclaims.application.IncidentSelectionSession;
import io.github.skyblueheat.echoclaims.application.RefundDeliveryAdapter;
import io.github.skyblueheat.echoclaims.application.RefundService;
import io.github.skyblueheat.echoclaims.application.ReviewEvidenceSelectionSession;
import io.github.skyblueheat.echoclaims.application.ReviewMetrics;
import io.github.skyblueheat.echoclaims.application.ReviewService;
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
    public static final String PERMISSION_REVIEW_QUEUE = "echoclaims.staff.review.queue";
    public static final String PERMISSION_REVIEW_VIEW = "echoclaims.staff.review.view";
    public static final String PERMISSION_REVIEW_START = "echoclaims.staff.review.start";
    public static final String PERMISSION_REVIEW_TAKEOVER = "echoclaims.staff.review.takeover";
    public static final String PERMISSION_REVIEW_EVIDENCE = "echoclaims.staff.review.evidence";
    public static final String PERMISSION_REVIEW_NOTE = "echoclaims.staff.review.note";
    public static final String PERMISSION_REVIEW_REQUEST_INFO = "echoclaims.staff.review.request-info";
    public static final String PERMISSION_REVIEW_ITEM_DECISION = "echoclaims.staff.review.item-decision";
    public static final String PERMISSION_REVIEW_APPROVE = "echoclaims.staff.review.approve";
    public static final String PERMISSION_REVIEW_PARTIAL = "echoclaims.staff.review.partial";
    public static final String PERMISSION_REVIEW_REJECT = "echoclaims.staff.review.reject";
    public static final String PERMISSION_REVIEW_HISTORY = "echoclaims.staff.review.history";
    public static final String PERMISSION_CLAIM_RESPOND = "echoclaims.claim.respond";

    private static final List<String> SUBCOMMANDS =
            List.of("status", "incidents", "incident", "snapshot",
                    "claim", "review", "refund");

    private static final List<String> CLAIM_SUBCOMMANDS =
            List.of("list", "view", "create", "cancel", "submit", "claimable", "respond");

    private static final List<String> CLAIM_STAFF_SUBCOMMANDS =
            List.of("staff-view", "staff-list");

    private static final List<String> REVIEW_SUBCOMMANDS =
            List.of("queue", "view", "start", "takeover", "evidence",
                    "note", "request-info", "item", "approve", "partial",
                    "reject", "history");

    private final Plugin plugin;
    private final Supplier<MessageService> messageSupplier;
    private final Supplier<StatusService> statusServiceSupplier;
    private final Supplier<EvidenceLookupService> evidenceLookupSupplier;
    private final Supplier<ClaimLookupService> claimLookupSupplier;
    private final Supplier<ClaimCreationService> claimCreationSupplier;
    private final Supplier<ClaimTransitionService> claimTransitionSupplier;
    private final Supplier<ClaimRateLimitService> claimRateLimitSupplier;
    private final Supplier<IncidentSelectionSession> incidentSelectionSessionSupplier;
    private final Supplier<ReviewService> reviewServiceSupplier;
    private final Supplier<ReviewEvidenceSelectionSession> reviewEvidenceSessionSupplier;
    private final Supplier<ReviewMetrics> reviewMetricsSupplier;
    private final Supplier<RefundService> refundServiceSupplier;
    private final Supplier<RefundDeliveryAdapter> refundDeliveryAdapterSupplier;
    private final Supplier<EchoClaimsSettings> settingsSupplier;
    private final ExecutorService queryExecutor;
    private final Consumer<Runnable> syncScheduler;
    private final Logger logger;
    private RefundCommandHandler refundCommandHandler;

    public EchoClaimsCommand(
            Plugin plugin,
            Supplier<MessageService> messageSupplier,
            Supplier<StatusService> statusServiceSupplier,
            Supplier<EvidenceLookupService> evidenceLookupSupplier,
            Supplier<ClaimLookupService> claimLookupSupplier,
            Supplier<ClaimCreationService> claimCreationSupplier,
            Supplier<ClaimTransitionService> claimTransitionSupplier,
            Supplier<ClaimRateLimitService> claimRateLimitSupplier,
            Supplier<IncidentSelectionSession> incidentSelectionSessionSupplier,
            Supplier<ReviewService> reviewServiceSupplier,
            Supplier<ReviewEvidenceSelectionSession> reviewEvidenceSessionSupplier,
            Supplier<ReviewMetrics> reviewMetricsSupplier,
            Supplier<RefundService> refundServiceSupplier,
            Supplier<RefundDeliveryAdapter> refundDeliveryAdapterSupplier,
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
        this.incidentSelectionSessionSupplier = Objects.requireNonNull(incidentSelectionSessionSupplier, "incidentSelectionSessionSupplier");
        this.reviewServiceSupplier = Objects.requireNonNull(reviewServiceSupplier, "reviewServiceSupplier");
        this.reviewEvidenceSessionSupplier = Objects.requireNonNull(reviewEvidenceSessionSupplier, "reviewEvidenceSessionSupplier");
        this.reviewMetricsSupplier = Objects.requireNonNull(reviewMetricsSupplier, "reviewMetricsSupplier");
        this.refundServiceSupplier = Objects.requireNonNull(refundServiceSupplier, "refundServiceSupplier");
        this.refundDeliveryAdapterSupplier = Objects.requireNonNull(refundDeliveryAdapterSupplier, "refundDeliveryAdapterSupplier");
        this.settingsSupplier = Objects.requireNonNull(settingsSupplier, "settingsSupplier");
        this.queryExecutor = Objects.requireNonNull(queryExecutor, "queryExecutor");
        this.syncScheduler = Objects.requireNonNull(syncScheduler, "syncScheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.refundCommandHandler = new RefundCommandHandler(
                messageSupplier, refundServiceSupplier, refundDeliveryAdapterSupplier,
                settingsSupplier, queryExecutor, syncScheduler, logger
        );
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
            case "review" -> handleReview(sender, args);
            case "refund" -> refundCommandHandler.handle(sender, args);
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
            case "respond" -> claimRespond(sender, args);
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
                Optional<UUID> uuidOpt = parseUuid(incidentInput);
                Optional<Incident> incidentOpt;
                boolean sessionExpired = false;

                if (uuidOpt.isPresent()) {
                    incidentOpt = evidenceLookup.findIncidentById(uuidOpt.get());
                } else {
                    try {
                        int index = Integer.parseInt(incidentInput);
                        if (index < 1) {
                            incidentOpt = Optional.empty();
                        } else {
                            IncidentSelectionSession session = incidentSelectionSessionSupplier.get();
                            if (session == null) {
                                incidentOpt = Optional.empty();
                                sessionExpired = true;
                            } else {
                                Optional<UUID> incidentIdOpt = session.resolve(playerUuid, index);
                                if (incidentIdOpt.isEmpty()) {
                                    incidentOpt = Optional.empty();
                                    sessionExpired = true;
                                } else {
                                    incidentOpt = evidenceLookup.findIncidentById(incidentIdOpt.get());
                                }
                            }
                        }
                    } catch (NumberFormatException ignored) {
                        incidentOpt = Optional.empty();
                    }
                }

                if (incidentOpt.isEmpty()) {
                    if (sessionExpired) {
                        syncScheduler.accept(() -> messages().send(sender, "claim-selection-expired"));
                    } else {
                        syncScheduler.accept(() -> messages().send(sender, "claim-incident-not-found",
                                Map.of("input", incidentInput)));
                    }
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
            IncidentSelectionSession session = incidentSelectionSessionSupplier.get();
            if (session == null) {
                return Optional.empty();
            }
            Optional<UUID> incidentIdOpt = session.resolve(playerUuid, index);
            if (incidentIdOpt.isEmpty()) {
                return Optional.empty();
            }
            return evidenceLookup.findIncidentById(incidentIdOpt.get());
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
                List<UUID> incidentIds = new ArrayList<>();
                for (Incident inc : openIncidents) {
                    incidentIds.add(inc.id());
                }
                IncidentSelectionSession session = incidentSelectionSessionSupplier.get();
                if (session != null) {
                    session.store(playerUuid, incidentIds);
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
        EchoClaimsSettings settings = settingsSupplier.get();
        long ttlSeconds = settings != null ? settings.claimSelectionSessionTtl().toSeconds() : 120;
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
        messages().send(sender, "claim-claimable-footer", Map.of(
                "ttl", Long.toString(ttlSeconds)
        ));
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

    private void claimRespond(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "claim-player-only");
            return;
        }
        if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_CLAIM_RESPOND)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 4) {
            messages().send(sender, "claim-respond-usage");
            return;
        }

        String reference = args[2];
        String response = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));

        ClaimLookupService lookup = claimLookupSupplier.get();
        ReviewService reviewService = reviewServiceSupplier.get();
        if (lookup == null || reviewService == null) {
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
                if (claim.status() != ClaimStatus.WAITING_FOR_PLAYER) {
                    syncScheduler.accept(() -> messages().send(sender, "claim-respond-not-waiting",
                            Map.of("reference", reference)));
                    return;
                }
                Optional<io.github.skyblueheat.echoclaims.domain.review.ClaimReview> reviewOpt =
                        reviewService.findReviewByClaimId(claim.id());
                if (reviewOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "review-not-found",
                            Map.of("reference", reference)));
                    return;
                }
                ReviewService.PlayerResponseResult result =
                        reviewService.recordPlayerResponse(claim, reviewOpt.get(), playerUuid, response);
                syncScheduler.accept(() -> {
                    if (result.isSuccess()) {
                        messages().send(sender, "claim-respond-success",
                                Map.of("reference", reference));
                    } else if (result.isConcurrencyConflict()) {
                        messages().send(sender, "review-concurrency-conflict",
                                Map.of("reference", reference));
                    } else {
                        messages().send(sender, "claim-respond-failed",
                                Map.of("reference", reference, "reason",
                                        result.rejectionReason().orElse("Unknown error")));
                    }
                });
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Claim respond failed for " + playerUuid, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void handleReview(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages().send(sender, "review-usage");
            return;
        }

        EchoClaimsSettings settings = settingsSupplier.get();
        if (settings != null && !settings.reviewsEnabled()) {
            messages().send(sender, "review-disabled");
            return;
        }

        ReviewService reviewService = reviewServiceSupplier.get();
        if (reviewService == null) {
            messages().send(sender, "not-ready");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "queue" -> reviewQueue(sender);
            case "view" -> reviewView(sender, args);
            case "start" -> reviewStart(sender, args);
            case "takeover" -> reviewTakeover(sender, args);
            case "evidence" -> reviewEvidence(sender, args);
            case "note" -> reviewNote(sender, args);
            case "request-info" -> reviewRequestInfo(sender, args);
            case "item" -> reviewItem(sender, args);
            case "approve" -> reviewFinalize(sender, args, io.github.skyblueheat.echoclaims.domain.review.ReviewOutcome.APPROVED,
                    PERMISSION_REVIEW_APPROVE);
            case "partial" -> reviewFinalize(sender, args, io.github.skyblueheat.echoclaims.domain.review.ReviewOutcome.PARTIALLY_APPROVED,
                    PERMISSION_REVIEW_PARTIAL);
            case "reject" -> reviewFinalize(sender, args, io.github.skyblueheat.echoclaims.domain.review.ReviewOutcome.REJECTED,
                    PERMISSION_REVIEW_REJECT);
            case "history" -> reviewHistory(sender, args);
            default -> messages().send(sender, "review-unknown-subcommand");
        }
    }

    private void reviewQueue(CommandSender sender) {
        if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_REVIEW_QUEUE)) {
            messages().send(sender, "no-permission");
            return;
        }

        ClaimLookupService lookup = claimLookupSupplier.get();
        if (lookup == null) {
            messages().send(sender, "not-ready");
            return;
        }

        int pageSize = settingsSupplier.get() != null ? settingsSupplier.get().reviewQueuePageSize() : 20;
        queryExecutor.submit(() -> {
            try {
                List<Claim> queue = lookup.findClaimsByStatus(ClaimStatus.SUBMITTED);
                syncScheduler.accept(() -> {
                    if (queue.isEmpty()) {
                        messages().send(sender, "review-queue-empty");
                        return;
                    }
                    messages().send(sender, "review-queue-header");
                    int index = 1;
                    for (Claim claim : queue) {
                        messages().send(sender, "review-queue-entry", Map.of(
                                "index", String.valueOf(index++),
                                "reference", claim.publicReference(),
                                "status", claim.status().name(),
                                "time", formatTimestamp(claim.createdAt())
                        ));
                    }
                    messages().send(sender, "review-queue-count",
                            Map.of("count", String.valueOf(queue.size())));
                });
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Review queue failed", exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void reviewView(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_REVIEW_VIEW)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            messages().send(sender, "review-usage");
            return;
        }

        String reference = args[2];
        ClaimLookupService lookup = claimLookupSupplier.get();
        ReviewService reviewService = reviewServiceSupplier.get();
        queryExecutor.submit(() -> {
            try {
                Optional<Claim> claimOpt = lookup.findClaimByPublicReference(reference);
                if (claimOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "claim-not-found",
                            Map.of("reference", reference)));
                    return;
                }
                Claim claim = claimOpt.get();
                Optional<io.github.skyblueheat.echoclaims.domain.review.ClaimReview> reviewOpt =
                        reviewService.findReviewByClaimId(claim.id());
                if (reviewOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "review-not-found",
                            Map.of("reference", reference)));
                    return;
                }
                io.github.skyblueheat.echoclaims.domain.review.ClaimReview review = reviewOpt.get();
                List<io.github.skyblueheat.echoclaims.domain.review.ClaimReviewComment> comments =
                        reviewService.findCommentsByClaimId(claim.id());
                List<io.github.skyblueheat.echoclaims.domain.review.ClaimReviewItemDecision> decisions =
                        reviewService.findDecisionsByReviewId(review.id());

                syncScheduler.accept(() -> {
                    messages().send(sender, "review-detail-header",
                            Map.of("reference", reference));
                    messages().send(sender, "review-detail-reviewer",
                            Map.of("reviewer", review.assignedReviewerUuid().toString()));
                    messages().send(sender, "review-detail-state",
                            Map.of("state", review.reviewState().name()));
                    if (review.finalOutcome() != null) {
                        messages().send(sender, "review-detail-outcome",
                                Map.of("outcome", review.finalOutcome().name()));
                    }
                    messages().send(sender, "review-detail-started",
                            Map.of("time", formatTimestamp(review.startedAt())));
                    if (review.finalizedAt() > 0) {
                        messages().send(sender, "review-detail-finalized",
                                Map.of("time", formatTimestamp(review.finalizedAt())));
                    }
                    if (review.hasFinalSummary()) {
                        messages().send(sender, "review-detail-summary",
                                Map.of("summary", review.finalSummary()));
                    }
                    if (!comments.isEmpty()) {
                        messages().send(sender, "review-comments-header",
                                Map.of("count", String.valueOf(comments.size())));
                        for (var comment : comments) {
                            messages().send(sender, "review-comment-entry", Map.of(
                                    "type", comment.commentType().name(),
                                    "visibility", comment.visibility().name(),
                                    "actor", comment.actorUuid().toString(),
                                    "time", formatTimestamp(comment.createdAt()),
                                    "body", comment.body()
                            ));
                        }
                    }
                    if (!decisions.isEmpty()) {
                        messages().send(sender, "review-decisions-header",
                                Map.of("count", String.valueOf(decisions.size())));
                        for (var decision : decisions) {
                            messages().send(sender, "review-decision-entry", Map.of(
                                    "outcome", decision.outcome().name(),
                                    "reference", decision.evidenceItemReference(),
                                    "approved", String.valueOf(decision.approvedQuantity()),
                                    "original", String.valueOf(decision.originalQuantity()),
                                    "reason", decision.reasonCode().name()
                            ));
                        }
                    }
                });
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Review view failed for " + reference, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void reviewStart(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_REVIEW_START)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            messages().send(sender, "claim-player-only");
            return;
        }
        if (args.length < 3) {
            messages().send(sender, "review-usage");
            return;
        }

        String reference = args[2];
        UUID staffUuid = player.getUniqueId();
        ClaimLookupService lookup = claimLookupSupplier.get();
        ReviewService reviewService = reviewServiceSupplier.get();

        queryExecutor.submit(() -> {
            try {
                Optional<Claim> claimOpt = lookup.findClaimByPublicReference(reference);
                if (claimOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "claim-not-found",
                            Map.of("reference", reference)));
                    return;
                }
                ReviewService.StartReviewResult result =
                        reviewService.startReview(claimOpt.get(), staffUuid);
                syncScheduler.accept(() -> {
                    if (result.isSuccess()) {
                        messages().send(sender, "review-started",
                                Map.of("reference", reference));
                    } else if (result.isConcurrencyConflict()) {
                        messages().send(sender, "review-concurrency-conflict",
                                Map.of("reference", reference));
                    } else {
                        messages().send(sender, "review-start-failed",
                                Map.of("reference", reference, "reason",
                                        result.rejectionReason().orElse("Unknown error")));
                    }
                });
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Review start failed for " + reference, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void reviewTakeover(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_REVIEW_TAKEOVER)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            messages().send(sender, "claim-player-only");
            return;
        }
        if (args.length < 4) {
            messages().send(sender, "review-usage");
            return;
        }

        String reference = args[2];
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
        UUID staffUuid = player.getUniqueId();
        ClaimLookupService lookup = claimLookupSupplier.get();
        ReviewService reviewService = reviewServiceSupplier.get();

        queryExecutor.submit(() -> {
            try {
                Optional<Claim> claimOpt = lookup.findClaimByPublicReference(reference);
                if (claimOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "claim-not-found",
                            Map.of("reference", reference)));
                    return;
                }
                Optional<io.github.skyblueheat.echoclaims.domain.review.ClaimReview> reviewOpt =
                        reviewService.findReviewByClaimId(claimOpt.get().id());
                if (reviewOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "review-not-found",
                            Map.of("reference", reference)));
                    return;
                }
                ReviewService.TakeoverResult result =
                        reviewService.takeoverReview(reviewOpt.get(), staffUuid, reason);
                syncScheduler.accept(() -> {
                    if (result.isSuccess()) {
                        messages().send(sender, "review-takeover-success",
                                Map.of("reference", reference));
                    } else if (result.isConcurrencyConflict()) {
                        messages().send(sender, "review-concurrency-conflict",
                                Map.of("reference", reference));
                    } else {
                        messages().send(sender, "review-takeover-failed",
                                Map.of("reference", reference, "reason",
                                        result.rejectionReason().orElse("Unknown error")));
                    }
                });
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Review takeover failed for " + reference, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void reviewEvidence(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_REVIEW_EVIDENCE)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            messages().send(sender, "claim-player-only");
            return;
        }
        if (args.length < 3) {
            messages().send(sender, "review-usage");
            return;
        }

        String reference = args[2];
        UUID staffUuid = player.getUniqueId();
        ClaimLookupService lookup = claimLookupSupplier.get();
        EvidenceLookupService evidenceLookup = evidenceLookupSupplier.get();
        ReviewEvidenceSelectionSession session = reviewEvidenceSessionSupplier.get();

        queryExecutor.submit(() -> {
            try {
                Optional<Claim> claimOpt = lookup.findClaimByPublicReference(reference);
                if (claimOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "claim-not-found",
                            Map.of("reference", reference)));
                    return;
                }
                Claim claim = claimOpt.get();
                Optional<Incident> incidentOpt = evidenceLookup.findIncidentById(claim.incidentId());
                if (incidentOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "db-error"));
                    return;
                }
                Incident incident = incidentOpt.get();
                Optional<InventorySnapshot> preSnapshot = incident.preEventSnapshotUuid() != null
                        ? evidenceLookup.findSnapshotById(incident.preEventSnapshotUuid())
                        : Optional.empty();
                Optional<InventorySnapshot> postSnapshot = incident.postEventSnapshotUuid() != null
                        ? evidenceLookup.findSnapshotById(incident.postEventSnapshotUuid())
                        : Optional.empty();

                if (preSnapshot.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "review-evidence-empty"));
                    return;
                }

                List<io.github.skyblueheat.echoclaims.domain.review.EvidenceComparisonResult> results =
                        io.github.skyblueheat.echoclaims.domain.review.EvidenceComparisonService.compare(
                                preSnapshot.get(), postSnapshot.orElse(null));

                List<String> references = results.stream()
                        .map(io.github.skyblueheat.echoclaims.domain.review.EvidenceComparisonResult::evidenceItemReference)
                        .toList();
                session.store(staffUuid, claim.id(), references);

                long ttlSeconds = settingsSupplier.get() != null
                        ? settingsSupplier.get().reviewEvidenceSessionTtl().toSeconds() : 300;

                syncScheduler.accept(() -> {
                    if (results.isEmpty()) {
                        messages().send(sender, "review-evidence-empty");
                        return;
                    }
                    messages().send(sender, "review-evidence-header",
                            Map.of("reference", reference));
                    int index = 1;
                    for (var result : results) {
                        String material = result.hasBeforeItem()
                                ? result.beforeItem().materialKey()
                                : result.hasAfterItem()
                                        ? result.afterItem().materialKey()
                                        : "unknown";
                        String slot = io.github.skyblueheat.echoclaims.domain.review.EvidenceItemKey
                                .slot(result.evidenceItemReference());
                        int before = result.hasBeforeItem() ? result.beforeItem().amount() : 0;
                        int after = result.hasAfterItem() ? result.afterItem().amount() : 0;
                        messages().send(sender, "review-evidence-entry", Map.of(
                                "index", String.valueOf(index++),
                                "state", result.state().name(),
                                "slot", slot,
                                "material", material,
                                "before", String.valueOf(before),
                                "after", String.valueOf(after)
                        ));
                    }
                    messages().send(sender, "review-evidence-footer",
                            Map.of("ttl", String.valueOf(ttlSeconds)));
                });
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Review evidence failed for " + reference, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void reviewNote(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_REVIEW_NOTE)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            messages().send(sender, "claim-player-only");
            return;
        }
        if (args.length < 4) {
            messages().send(sender, "review-usage");
            return;
        }

        String reference = args[2];
        String note = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
        UUID staffUuid = player.getUniqueId();
        ClaimLookupService lookup = claimLookupSupplier.get();
        ReviewService reviewService = reviewServiceSupplier.get();

        queryExecutor.submit(() -> {
            try {
                Optional<Claim> claimOpt = lookup.findClaimByPublicReference(reference);
                if (claimOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "claim-not-found",
                            Map.of("reference", reference)));
                    return;
                }
                Optional<io.github.skyblueheat.echoclaims.domain.review.ClaimReview> reviewOpt =
                        reviewService.findReviewByClaimId(claimOpt.get().id());
                if (reviewOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "review-not-found",
                            Map.of("reference", reference)));
                    return;
                }
                ReviewService.AddNoteResult result =
                        reviewService.addInternalNote(reviewOpt.get(), staffUuid, note);
                syncScheduler.accept(() -> {
                    if (result.isSuccess()) {
                        messages().send(sender, "review-note-added",
                                Map.of("reference", reference));
                    } else {
                        messages().send(sender, "review-note-failed",
                                Map.of("reference", reference, "reason",
                                        result.rejectionReason().orElse("Unknown error")));
                    }
                });
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Review note failed for " + reference, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void reviewRequestInfo(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_REVIEW_REQUEST_INFO)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            messages().send(sender, "claim-player-only");
            return;
        }
        if (args.length < 4) {
            messages().send(sender, "review-usage");
            return;
        }

        String reference = args[2];
        String request = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
        UUID staffUuid = player.getUniqueId();
        ClaimLookupService lookup = claimLookupSupplier.get();
        ReviewService reviewService = reviewServiceSupplier.get();

        queryExecutor.submit(() -> {
            try {
                Optional<Claim> claimOpt = lookup.findClaimByPublicReference(reference);
                if (claimOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "claim-not-found",
                            Map.of("reference", reference)));
                    return;
                }
                Claim claim = claimOpt.get();
                Optional<io.github.skyblueheat.echoclaims.domain.review.ClaimReview> reviewOpt =
                        reviewService.findReviewByClaimId(claim.id());
                if (reviewOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "review-not-found",
                            Map.of("reference", reference)));
                    return;
                }
                ReviewService.RequestInfoResult result =
                        reviewService.requestInformation(claim, reviewOpt.get(), staffUuid, request);
                syncScheduler.accept(() -> {
                    if (result.isSuccess()) {
                        messages().send(sender, "review-info-requested",
                                Map.of("reference", reference));
                    } else if (result.isConcurrencyConflict()) {
                        messages().send(sender, "review-concurrency-conflict",
                                Map.of("reference", reference));
                    } else {
                        messages().send(sender, "review-info-failed",
                                Map.of("reference", reference, "reason",
                                        result.rejectionReason().orElse("Unknown error")));
                    }
                });
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Review request-info failed for " + reference, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void reviewItem(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_REVIEW_ITEM_DECISION)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            messages().send(sender, "claim-player-only");
            return;
        }
        if (args.length < 7) {
            messages().send(sender, "review-usage");
            return;
        }

        String reference = args[2];
        int index;
        try {
            index = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            messages().send(sender, "review-evidence-session-expired");
            return;
        }

        io.github.skyblueheat.echoclaims.domain.review.ReviewItemOutcome outcome;
        try {
            outcome = io.github.skyblueheat.echoclaims.domain.review.ReviewItemOutcome
                    .valueOf(args[4].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            messages().send(sender, "review-invalid-outcome");
            return;
        }

        int approvedQuantity;
        try {
            approvedQuantity = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            messages().send(sender, "review-invalid-quantity",
                    Map.of("max", "0"));
            return;
        }

        io.github.skyblueheat.echoclaims.domain.review.ReviewReasonCode reasonCode;
        try {
            reasonCode = io.github.skyblueheat.echoclaims.domain.review.ReviewReasonCode
                    .valueOf(args[6].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            messages().send(sender, "review-invalid-reason",
                    Map.of("codes", java.util.Arrays.stream(
                                    io.github.skyblueheat.echoclaims.domain.review.ReviewReasonCode.values())
                            .map(Enum::name).reduce((a, b) -> a + ", " + b).orElse("")));
            return;
        }

        String staffNote = args.length > 7
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 7, args.length)) : "";

        UUID staffUuid = player.getUniqueId();
        ClaimLookupService lookup = claimLookupSupplier.get();
        ReviewService reviewService = reviewServiceSupplier.get();
        ReviewEvidenceSelectionSession session = reviewEvidenceSessionSupplier.get();

        queryExecutor.submit(() -> {
            try {
                Optional<Claim> claimOpt = lookup.findClaimByPublicReference(reference);
                if (claimOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "claim-not-found",
                            Map.of("reference", reference)));
                    return;
                }
                Claim claim = claimOpt.get();

                Optional<String> refOpt = session.resolve(staffUuid, claim.id(), index);
                if (refOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "review-evidence-session-expired"));
                    return;
                }
                String evidenceItemReference = refOpt.get();

                Optional<io.github.skyblueheat.echoclaims.domain.review.ClaimReview> reviewOpt =
                        reviewService.findReviewByClaimId(claim.id());
                if (reviewOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "review-not-found",
                            Map.of("reference", reference)));
                    return;
                }
                io.github.skyblueheat.echoclaims.domain.review.ClaimReview review = reviewOpt.get();

                UUID sourceSnapshotId = io.github.skyblueheat.echoclaims.domain.review.EvidenceItemKey
                        .snapshotUuid(evidenceItemReference);
                EvidenceLookupService evidenceLookup = evidenceLookupSupplier.get();
                Optional<InventorySnapshot> snapshotOpt = evidenceLookup.findSnapshotById(sourceSnapshotId);
                if (snapshotOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "db-error"));
                    return;
                }
                String slot = io.github.skyblueheat.echoclaims.domain.review.EvidenceItemKey
                        .slot(evidenceItemReference);
                int originalQuantity = 0;
                for (SnapshotItem item : snapshotOpt.get().allItems()) {
                    if (item.slot().equals(slot) && !item.isAir()) {
                        originalQuantity = item.amount();
                        break;
                    }
                }

                var existingDecision = reviewService.findDecisionsByReviewId(review.id()).stream()
                        .filter(d -> d.evidenceItemReference().equals(evidenceItemReference))
                        .findFirst();

                ReviewService.ItemDecisionResult result;
                if (existingDecision.isPresent()) {
                    result = reviewService.updateItemDecision(
                            existingDecision.get(), staffUuid, approvedQuantity,
                            outcome, reasonCode, staffNote);
                } else {
                    result = reviewService.createItemDecision(
                            review, staffUuid, evidenceItemReference, sourceSnapshotId,
                            originalQuantity, approvedQuantity, outcome, reasonCode, staffNote);
                }

                syncScheduler.accept(() -> {
                    if (result.isSuccess()) {
                        if (existingDecision.isPresent()) {
                            messages().send(sender, "review-item-updated",
                                    Map.of("reference", evidenceItemReference));
                        } else {
                            messages().send(sender, "review-item-created",
                                    Map.of("reference", evidenceItemReference));
                        }
                    } else if (result.isConcurrencyConflict()) {
                        messages().send(sender, "review-concurrency-conflict",
                                Map.of("reference", reference));
                    } else {
                        messages().send(sender, "review-item-failed",
                                Map.of("reference", reference, "reason",
                                        result.rejectionReason().orElse("Unknown error")));
                    }
                });
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Review item decision failed for " + reference, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void reviewFinalize(
            CommandSender sender, String[] args,
            io.github.skyblueheat.echoclaims.domain.review.ReviewOutcome outcome,
            String permission
    ) {
        if (!hasPermission(sender, PERMISSION_ADMIN, permission)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            messages().send(sender, "claim-player-only");
            return;
        }
        if (args.length < 3) {
            messages().send(sender, "review-usage");
            return;
        }

        String reference = args[2];
        String summary = args.length > 3
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "";
        UUID staffUuid = player.getUniqueId();
        ClaimLookupService lookup = claimLookupSupplier.get();
        ReviewService reviewService = reviewServiceSupplier.get();

        queryExecutor.submit(() -> {
            try {
                Optional<Claim> claimOpt = lookup.findClaimByPublicReference(reference);
                if (claimOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "claim-not-found",
                            Map.of("reference", reference)));
                    return;
                }
                Claim claim = claimOpt.get();
                Optional<io.github.skyblueheat.echoclaims.domain.review.ClaimReview> reviewOpt =
                        reviewService.findReviewByClaimId(claim.id());
                if (reviewOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "review-not-found",
                            Map.of("reference", reference)));
                    return;
                }
                ReviewService.FinalizeReviewResult result =
                        reviewService.finalizeReview(claim, reviewOpt.get(), staffUuid, outcome, summary);
                syncScheduler.accept(() -> {
                    if (result.isSuccess()) {
                        messages().send(sender, "review-finalized",
                                Map.of("reference", reference, "outcome", outcome.name()));
                    } else if (result.isConcurrencyConflict()) {
                        messages().send(sender, "review-concurrency-conflict",
                                Map.of("reference", reference));
                    } else {
                        messages().send(sender, "review-finalize-failed",
                                Map.of("reference", reference, "reason",
                                        result.rejectionReason().orElse("Unknown error")));
                    }
                });
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Review finalize failed for " + reference, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void reviewHistory(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PERMISSION_ADMIN, PERMISSION_REVIEW_HISTORY)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            messages().send(sender, "review-usage");
            return;
        }

        String reference = args[2];
        ClaimLookupService lookup = claimLookupSupplier.get();

        queryExecutor.submit(() -> {
            try {
                Optional<Claim> claimOpt = lookup.findClaimByPublicReference(reference);
                if (claimOpt.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "claim-not-found",
                            Map.of("reference", reference)));
                    return;
                }
                List<ClaimAuditEntry> audit = lookup.findAuditEntries(claimOpt.get().id());
                syncScheduler.accept(() -> {
                    if (audit.isEmpty()) {
                        messages().send(sender, "review-history-empty",
                                Map.of("reference", reference));
                        return;
                    }
                    messages().send(sender, "review-history-header",
                            Map.of("reference", reference));
                    for (ClaimAuditEntry entry : audit) {
                        messages().send(sender, "review-history-entry", Map.of(
                                "action", entry.action().name(),
                                "actor", entry.actorUuid() != null ? entry.actorUuid().toString() : "system",
                                "time", formatTimestamp(entry.recordedAt()),
                                "version", String.valueOf(entry.claimVersion())
                        ));
                    }
                });
            } catch (Exception exception) {
                logger.log(Level.WARNING, "Review history failed for " + reference, exception);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
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

        if (args.length == 2 && args[0].equalsIgnoreCase("review")) {
            return filter(REVIEW_SUBCOMMANDS, args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("refund")) {
            return filter(RefundCommandHandler.REFUND_SUBCOMMANDS, args[1]);
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
