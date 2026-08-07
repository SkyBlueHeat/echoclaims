package io.github.skyblueheat.echoclaims.application;

/**
 * Immutable configuration for {@link RefundService}.
 *
 * <p>Carries refund-related limits and flags extracted from {@code EchoClaimsSettings}
 * so the service remains testable without a full settings record.</p>
 *
 * @param enabled               whether the refund system is enabled
 * @param playerSelfClaim       whether players can claim their own refunds
 * @param autoDeliverOnLogin    whether refunds are automatically delivered on login
 * @param maxItemsPerExecution  maximum number of items to process in a single delivery
 * @param retryFailedRefunds    whether failed refunds can be retried
 */
public record RefundServiceConfig(
        boolean enabled,
        boolean playerSelfClaim,
        boolean autoDeliverOnLogin,
        int maxItemsPerExecution,
        boolean retryFailedRefunds
) {

    public RefundServiceConfig {
        maxItemsPerExecution = Math.max(1, Math.min(maxItemsPerExecution, 1_000));
    }

    public static RefundServiceConfig defaults() {
        return new RefundServiceConfig(
                true,
                true,
                false,
                100,
                true
        );
    }
}
