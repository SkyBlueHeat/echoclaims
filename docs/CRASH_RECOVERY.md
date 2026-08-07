# Crash/Recovery Semantics

## Overview

This document audits the crash/recovery semantics of the EchoClaims refund system.
The refund system has a critical crash window between database commit and Bukkit
inventory mutation that must be understood by operators and developers.

## Crash Window

### The Problem

When `RefundService.executeRefund` processes items, the following sequence occurs:

1. **DB transaction**: `startDelivery` transitions refund to `DELIVERING` (atomic with audit)
2. **Item delivery loop**: For each pending item:
   - `adapter.deliverItem(item)` is called (Bukkit inventory mutation on main thread)
   - `updateItemDelivery` persists the result (atomic with audit)
3. **Completion check**: If all items delivered, `completeRefund` transitions to `COMPLETED`

The crash window exists between step 2a (inventory mutation) and step 2b (DB persist).

### What Happens on Crash

| Crash Point | DB State | Inventory State | Recovery |
|---|---|---|---|
| Before step 1 | No refund row | No items delivered | Re-execute from scratch |
| After step 1, before step 2 | Refund = `DELIVERING` | No items delivered | Re-execute: `findPendingByRefundId` returns all items |
| After 2a, before 2b (item 1) | Refund = `DELIVERING`, item 1 = `PENDING` | Item 1 delivered in inventory | **Duplicate delivery risk** — see below |
| After 2b (item 1), before 2a (item 2) | Item 1 = `DELIVERED` | Item 1 delivered, item 2 not | Re-execute: only item 2 is pending |
| After all items, before step 3 | All items = `DELIVERED` | All items delivered | Re-execute: `findPendingByRefundId` returns empty, completes refund |

### Duplicate Delivery Risk

If the server crashes after Bukkit's `addItem()` succeeds but before the DB
`updateItemDelivery` commits, the item is in the player's inventory but the DB
still shows it as `PENDING`. On recovery, re-executing the refund would deliver
the item again.

**Mitigation**: This is an inherent limitation of cross-system atomicity. The
system does NOT claim exactly-once delivery across the crash window. The
following design choices minimize the risk:

1. **Small window**: The DB write happens immediately after the inventory mutation,
   minimizing the crash window to milliseconds.
2. **Idempotent re-execution**: The refund can be re-executed safely; only items
   still in `PENDING` or `PARTIALLY_DELIVERED` state are re-delivered.
3. **Audit trail**: Every delivery attempt is recorded in the audit log, allowing
   administrators to detect and investigate potential duplicates.
4. **`DELIVERING` state**: The refund is marked `DELIVERING` before any items are
   processed, preventing concurrent execution.

### Recovery Procedure

1. On server restart, `DELIVERING` refunds are NOT automatically resumed.
2. Staff or player can re-execute the refund via `/ec refund execute <ref>` or
   `/ec refund claim <ref>`.
3. The `executeRefund` method:
   - Finds the refund by claim ID
   - If status is `DELIVERING`, transitions back to `READY` first
   - Then processes all `PENDING` and `PARTIALLY_DELIVERED` items
4. Items already `DELIVERED` in the DB are skipped.

## State Machine Recovery

### Refund Status Recovery

| Status at Crash | Recovery Action |
|---|---|
| `PENDING` | No action needed — refund has not been executed yet |
| `READY` | Can be executed normally |
| `DELIVERING` | Re-execute: service transitions back to `READY`, then processes pending items |
| `COMPLETED` | No action needed — refund is terminal |
| `FAILED` | Can be retried if `retry-failed-refunds` is enabled; otherwise requires admin intervention |

### Refund Item Status Recovery

| Status at Crash | Recovery Action |
|---|---|
| `PENDING` | Will be delivered on next execution |
| `PARTIALLY_DELIVERED` | Only remaining quantity will be delivered |
| `DELIVERED` | Skipped — already fully delivered |
| `FAILED` | Not re-delivered automatically; refund must be retried |

## Concurrency Guarantees

- **Optimistic concurrency**: All status transitions use version-based optimistic locking
- **Atomic transactions**: Refund + items + audit entries are persisted in a single transaction
- **No concurrent execution**: `startDelivery` uses CAS to ensure only one thread enters `DELIVERING`
- **COMPLETED is permanently terminal**: SQL-level guard prevents any transition from `COMPLETED`
- **FAILED is recoverable**: Can transition to `READY` or `PENDING` for retry

## Known Limitations

1. **Not exactly-once across crash window**: Items may be duplicated if crash occurs
   between inventory mutation and DB persist.
2. **No automatic resume**: `DELIVERING` refunds must be manually re-executed after restart.
3. **No compensation**: If an item is duplicated, there is no automatic rollback mechanism.
   Administrators must investigate using the audit trail.
4. **Offline players**: If the player is offline, delivery cannot occur. The refund remains
   in `READY` state until the player is online and the refund is executed.

## Configuration

The following configuration options affect crash recovery:

- `refund.retry-failed-refunds`: Enables retry of `FAILED` refunds (default: true)
- `refund.auto-deliver-on-login`: Automatically deliver pending refunds when player joins
  (default: false — not yet implemented in MVP-04)
- `refund.max-items-per-execution`: Limits items processed per execution, reducing the
  crash window size (default: 100)
