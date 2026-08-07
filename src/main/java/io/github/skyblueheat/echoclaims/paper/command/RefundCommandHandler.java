package io.github.skyblueheat.echoclaims.paper.command;

import io.github.skyblueheat.echoclaims.application.RefundDeliveryAdapter;
import io.github.skyblueheat.echoclaims.application.RefundService;
import io.github.skyblueheat.echoclaims.config.EchoClaimsSettings;
import io.github.skyblueheat.echoclaims.domain.refund.Refund;
import io.github.skyblueheat.echoclaims.domain.refund.RefundAuditEntry;
import io.github.skyblueheat.echoclaims.domain.refund.RefundItem;
import io.github.skyblueheat.echoclaims.paper.message.MessageService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles refund subcommands for the {@code /ec refund} command tree.
 *
 * <p>This class is a delegate of {@link EchoClaimsCommand} to avoid further
 * bloating that class. It follows the same patterns: permission checks,
 * async DB access via the query executor, and sync message sending via
 * the sync scheduler.</p>
 *
 * <h2>Commands</h2>
 *
 * <p>Staff commands:</p>
 * <ul>
 *   <li>{@code /ec refund status <claimRef>} — view refund status</li>
 *   <li>{@code /ec refund execute <claimRef>} — execute refund delivery</li>
 *   <li>{@code /ec refund history <claimRef>} — view refund audit history</li>
 *   <li>{@code /ec refund retry <claimRef>} — retry a failed refund</li>
 * </ul>
 *
 * <p>Player commands:</p>
 * <ul>
 *   <li>{@code /ec refund pending} — list pending refunds</li>
 *   <li>{@code /ec refund claim <claimRef>} — claim a pending refund</li>
 * </ul>
 */
public final class RefundCommandHandler {

    public static final String PERMISSION_REFUND_STATUS = "echoclaims.staff.refund.status";
    public static final String PERMISSION_REFUND_EXECUTE = "echoclaims.staff.refund.execute";
    public static final String PERMISSION_REFUND_HISTORY = "echoclaims.staff.refund.history";
    public static final String PERMISSION_REFUND_RETRY = "echoclaims.staff.refund.retry";
    public static final String PERMISSION_REFUND_PENDING = "echoclaims.refund.pending";
    public static final String PERMISSION_REFUND_CLAIM = "echoclaims.refund.claim";

    static final List<String> REFUND_SUBCOMMANDS =
            List.of("status", "execute", "history", "retry", "pending", "claim");

    private final Supplier<MessageService> messageSupplier;
    private final Supplier<RefundService> refundServiceSupplier;
    private final Supplier<RefundDeliveryAdapter> deliveryAdapterSupplier;
    private final Supplier<EchoClaimsSettings> settingsSupplier;
    private final ExecutorService queryExecutor;
    private final Consumer<Runnable> syncScheduler;
    private final Logger logger;

    public RefundCommandHandler(
            Supplier<MessageService> messageSupplier,
            Supplier<RefundService> refundServiceSupplier,
            Supplier<RefundDeliveryAdapter> deliveryAdapterSupplier,
            Supplier<EchoClaimsSettings> settingsSupplier,
            ExecutorService queryExecutor,
            Consumer<Runnable> syncScheduler,
            Logger logger
    ) {
        this.messageSupplier = Objects.requireNonNull(messageSupplier, "messageSupplier");
        this.refundServiceSupplier = Objects.requireNonNull(refundServiceSupplier, "refundServiceSupplier");
        this.deliveryAdapterSupplier = Objects.requireNonNull(deliveryAdapterSupplier, "deliveryAdapterSupplier");
        this.settingsSupplier = Objects.requireNonNull(settingsSupplier, "settingsSupplier");
        this.queryExecutor = Objects.requireNonNull(queryExecutor, "queryExecutor");
        this.syncScheduler = Objects.requireNonNull(syncScheduler, "syncScheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void handle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages().send(sender, "refund-usage");
            return;
        }

        EchoClaimsSettings settings = settingsSupplier.get();
        if (settings != null && !settings.refundsEnabled()) {
            messages().send(sender, "refund-disabled");
            return;
        }

        RefundService refundService = refundServiceSupplier.get();
        if (refundService == null) {
            messages().send(sender, "not-ready");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> refundStatus(sender, args);
            case "execute" -> refundExecute(sender, args);
            case "history" -> refundHistory(sender, args);
            case "retry" -> refundRetry(sender, args);
            case "pending" -> refundPending(sender);
            case "claim" -> refundClaim(sender, args);
            default -> messages().send(sender, "refund-unknown-subcommand");
        }
    }

    // ─── Staff: status ───

    private void refundStatus(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PERMISSION_REFUND_STATUS)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            messages().send(sender, "refund-usage");
            return;
        }
        String claimRef = args[2];
        queryExecutor.submit(() -> {
            try {
                var detail = refundServiceSupplier.get().findRefundByClaimId(resolveClaimId(claimRef));
                syncScheduler.accept(() -> {
                    if (detail.isEmpty()) {
                        messages().send(sender, "refund-not-found", Map.of("reference", claimRef));
                        return;
                    }
                    var d = detail.get();
                    messages().send(sender, "refund-status-header", Map.of("reference", claimRef));
                    messages().send(sender, "refund-status-info", Map.of(
                            "status", d.refund().status().name(),
                            "items", String.valueOf(d.items().size()),
                            "createdAt", String.valueOf(d.refund().createdAt())
                    ));
                    for (RefundItem item : d.items()) {
                        messages().send(sender, "refund-status-item", Map.of(
                                "material", item.materialKey(),
                                "refundable", String.valueOf(item.refundableQuantity()),
                                "delivered", String.valueOf(item.deliveredQuantity()),
                                "remaining", String.valueOf(item.remainingQuantity()),
                                "itemStatus", item.status().name()
                        ));
                    }
                });
            } catch (Exception e) {
                logger.log(Level.WARNING, "Refund status failed for " + claimRef, e);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    // ─── Staff: execute ───

    private void refundExecute(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PERMISSION_REFUND_EXECUTE)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            messages().send(sender, "refund-usage");
            return;
        }
        String claimRef = args[2];
        UUID actorUuid = sender instanceof Player p ? p.getUniqueId() : null;

        queryExecutor.submit(() -> {
            try {
                UUID claimId = resolveClaimId(claimRef);
                RefundService.ExecuteRefundResult result =
                        refundServiceSupplier.get().executeRefund(claimId, actorUuid, deliveryAdapterSupplier.get());
                syncScheduler.accept(() -> sendExecuteResult(sender, result, claimRef));
            } catch (Exception e) {
                logger.log(Level.WARNING, "Refund execute failed for " + claimRef, e);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    private void sendExecuteResult(CommandSender sender, RefundService.ExecuteRefundResult result, String claimRef) {
        if (result.isAlreadyCompleted()) {
            messages().send(sender, "refund-already-completed", Map.of("reference", claimRef));
        } else if (result.isConcurrencyConflict()) {
            messages().send(sender, "refund-concurrency-conflict");
        } else if (result.isFailed()) {
            messages().send(sender, "refund-failed", Map.of(
                    "reference", claimRef,
                    "reason", result.rejectionReason().orElse("unknown")
            ));
        } else if (result.isPartialDelivery()) {
            messages().send(sender, "refund-partial-delivery", Map.of(
                    "reference", claimRef,
                    "delivered", String.valueOf(result.totalDelivered()),
                    "remaining", String.valueOf(result.remainingCount())
            ));
        } else if (result.isSuccess()) {
            messages().send(sender, "refund-completed", Map.of("reference", claimRef));
        } else {
            messages().send(sender, "refund-rejected", Map.of(
                    "reference", claimRef,
                    "reason", result.rejectionReason().orElse("unknown")
            ));
        }
    }

    // ─── Staff: history ───

    private void refundHistory(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PERMISSION_REFUND_HISTORY)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            messages().send(sender, "refund-usage");
            return;
        }
        String claimRef = args[2];
        queryExecutor.submit(() -> {
            try {
                List<RefundAuditEntry> history =
                        refundServiceSupplier.get().getRefundHistory(resolveClaimId(claimRef));
                syncScheduler.accept(() -> {
                    if (history.isEmpty()) {
                        messages().send(sender, "refund-history-empty", Map.of("reference", claimRef));
                        return;
                    }
                    messages().send(sender, "refund-history-header", Map.of("reference", claimRef));
                    for (RefundAuditEntry entry : history) {
                        messages().send(sender, "refund-history-entry", Map.of(
                                "action", entry.action().name(),
                                "actor", entry.actorUuid() != null ? entry.actorUuid().toString() : "system",
                                "quantity", String.valueOf(entry.quantity()),
                                "reason", entry.reason(),
                                "time", String.valueOf(entry.recordedAt())
                        ));
                    }
                });
            } catch (Exception e) {
                logger.log(Level.WARNING, "Refund history failed for " + claimRef, e);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    // ─── Staff: retry ───

    private void refundRetry(CommandSender sender, String[] args) {
        if (!hasPermission(sender, PERMISSION_REFUND_RETRY)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            messages().send(sender, "refund-usage");
            return;
        }
        String claimRef = args[2];
        UUID actorUuid = sender instanceof Player p ? p.getUniqueId() : null;
        queryExecutor.submit(() -> {
            try {
                var result = refundServiceSupplier.get().retryRefund(resolveClaimId(claimRef), actorUuid);
                syncScheduler.accept(() -> {
                    if (result.isSuccess()) {
                        messages().send(sender, "refund-retry-success", Map.of("reference", claimRef));
                    } else if (result.isConcurrencyConflict()) {
                        messages().send(sender, "refund-concurrency-conflict");
                    } else {
                        messages().send(sender, "refund-retry-rejected", Map.of(
                                "reference", claimRef,
                                "reason", result.rejectionReason().orElse("unknown")
                        ));
                    }
                });
            } catch (Exception e) {
                logger.log(Level.WARNING, "Refund retry failed for " + claimRef, e);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    // ─── Player: pending ───

    private void refundPending(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "claim-player-only");
            return;
        }
        if (!hasPermission(sender, PERMISSION_REFUND_PENDING)) {
            messages().send(sender, "no-permission");
            return;
        }
        queryExecutor.submit(() -> {
            try {
                List<Refund> pending = refundServiceSupplier.get().findPendingRefundsForPlayer(player.getUniqueId());
                syncScheduler.accept(() -> {
                    if (pending.isEmpty()) {
                        messages().send(sender, "refund-no-pending");
                        return;
                    }
                    messages().send(sender, "refund-pending-header");
                    for (Refund refund : pending) {
                        messages().send(sender, "refund-pending-entry", Map.of(
                                "claimId", refund.claimId().toString(),
                                "status", refund.status().name(),
                                "createdAt", String.valueOf(refund.createdAt())
                        ));
                    }
                    messages().send(sender, "refund-pending-count",
                            Map.of("count", String.valueOf(pending.size())));
                });
            } catch (Exception e) {
                logger.log(Level.WARNING, "Refund pending failed for " + player.getUniqueId(), e);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    // ─── Player: claim ───

    private void refundClaim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "claim-player-only");
            return;
        }
        if (!hasPermission(sender, PERMISSION_REFUND_CLAIM)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            messages().send(sender, "refund-usage");
            return;
        }
        String claimRef = args[2];
        queryExecutor.submit(() -> {
            try {
                UUID claimId = resolveClaimId(claimRef);
                var detail = refundServiceSupplier.get().findRefundByClaimId(claimId);
                if (detail.isEmpty()) {
                    syncScheduler.accept(() -> messages().send(sender, "refund-not-found",
                            Map.of("reference", claimRef)));
                    return;
                }
                Refund refund = detail.get().refund();
                if (!refund.playerUuid().equals(player.getUniqueId())) {
                    syncScheduler.accept(() -> messages().send(sender, "refund-not-owned"));
                    return;
                }
                RefundService.ExecuteRefundResult result =
                        refundServiceSupplier.get().executeRefund(claimId, player.getUniqueId(),
                                deliveryAdapterSupplier.get());
                syncScheduler.accept(() -> sendExecuteResult(sender, result, claimRef));
            } catch (Exception e) {
                logger.log(Level.WARNING, "Refund claim failed for " + claimRef, e);
                syncScheduler.accept(() -> messages().send(sender, "db-error"));
            }
        });
    }

    // ─── Helpers ───

    private boolean hasPermission(CommandSender sender, String permission) {
        return sender.hasPermission(EchoClaimsCommand.PERMISSION_ADMIN) || sender.hasPermission(permission);
    }

    private UUID resolveClaimId(String reference) {
        return UUID.fromString(reference);
    }

    private MessageService messages() {
        return messageSupplier.get();
    }
}
