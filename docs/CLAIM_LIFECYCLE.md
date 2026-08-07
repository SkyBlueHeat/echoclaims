# Claim Lifecycle

## State Diagram

```
                 ┌─────────┐
                 │  DRAFT  │
                 └────┬────┘
                      │
           ┌──────────┼──────────┐
           │                     │
           ▼                     ▼
    ┌─────────────┐       ┌───────────┐
    │  SUBMITTED  │       │ CANCELLED │
    └──────┬──────┘       └───────────┘
           │                     ▲
           │                     │
           └─────────────────────┘
```

## Transitions

| From | To | Trigger | Actor |
|------|----|---------|-------|
| DRAFT | SUBMITTED | `/ec claim submit <ref>` | Player |
| DRAFT | CANCELLED | `/ec claim cancel <ref>` | Player |
| SUBMITTED | CANCELLED | `/ec claim cancel <ref>` | Player |

**Terminal state**: `CANCELLED` — no transitions out.

**Rejected transitions**: Same-status transitions, transitions from
`CANCELLED`, and any transition not listed above are rejected with a
diagnostic reason.

## Transition Policy

`ClaimTransitionPolicy` is a pure static evaluator:

1. If current status equals target status → rejected ("Claim is already X")
2. If current status is `CANCELLED` → rejected ("Claim is cancelled and cannot be transitioned")
3. If transition is in the allowed table → allowed with corresponding `ClaimAction`
4. Otherwise → rejected with descriptive message

## Optimistic Concurrency

Each claim has a `version` field. When transitioning:

1. The caller reads the claim and obtains its current version
2. `ClaimStore.transitionClaim` executes:
   ```sql
   UPDATE claims SET status = ?, submitted_at = ?, cancelled_at = ?,
   version = version + 1 WHERE id = ? AND version = ?
   ```
3. If the stored version doesn't match `expectedVersion`, the update affects
   0 rows and the transition returns `false`
4. The service records a `concurrencyConflict` metric and returns a
   `concurrencyConflict()` result
5. The player is told to try again

## Atomicity

### Claim Creation

`SqliteClaimStore.createClaim` executes in a single transaction:
1. INSERT claim row
2. INSERT creation audit entry
3. COMMIT

If either step fails, the entire transaction is rolled back. No orphan claims
or audit entries can exist.

### Claim Transition

`SqliteClaimStore.transitionClaim` executes in a single transaction:
1. UPDATE claim status (with optimistic version check)
2. If update affected 0 rows → ROLLBACK, return false
3. INSERT transition audit entry
4. COMMIT, return true

If the audit insert fails after a successful update, the transaction is
rolled back — the status change is not persisted without its audit trail.

## Rate Limiting

`ClaimRateLimitService` provides in-memory rate limiting for claim creation:

- Configurable cooldown (default: 30 seconds)
- Zero or negative cooldown disables rate limiting
- Thread-safe via `ConcurrentHashMap`
- Expired entries are cleaned up opportunistically on each `check()` call
- State is lost on plugin restart (by design — rate limiting is a soft guard,
  not an authoritative barrier)
- Does **not** prevent duplicate active claims — that is enforced by the
  application check and database unique index

## Incident Selection

Players do not need to type raw UUIDs. Two mechanisms are provided:

1. **`/ec claim claimable`** — Lists the player's OPEN incidents that have no
   existing active claim, with a 1-based index
2. **`/ec claim create <index> [description]`** — Creates a claim for the
   incident at the given index from the player's recent incident list
3. **`/ec claim create <uuid> [description]`** — Still accepted for backward
   compatibility and advanced use

The index is resolved by querying the player's recent incidents (same order as
`/ec incidents <player>`) and selecting the incident at position `index - 1`.

## Audit Trail

Every claim action produces a `ClaimAuditEntry`:

- **CREATED** — Recorded when a claim is created
- **SUBMITTED** — Recorded when a claim transitions to SUBMITTED
- **CANCELLED** — Recorded when a claim transitions to CANCELLED

Audit entries are ordered by `recorded_at` ascending and include the claim
version at the time of the action, providing a complete history of the claim's
lifecycle.

Audit entries are never deleted (rule 10: never silently delete audit history).

## Refund Lifecycle

After a review is finalized as APPROVED or PARTIALLY_APPROVED, a refund is
created. The refund restores approved items to the player's inventory.

### Refund State Diagram

```
                 ┌─────────┐
                 │ PENDING │
                 └────┬────┘
                      │
                      ▼
                 ┌─────────┐
                 │  READY  │◄────────────┐
                 └────┬────┘             │
                      │                  │
                      ▼                  │
              ┌──────────────┐           │
              │ DELIVERING   │           │
              └──────┬───────┘           │
                     │                   │
           ┌────────┼────────┐           │
           │                 │           │
           ▼                 ▼           │
    ┌───────────┐    ┌───────────┐       │
    │ COMPLETED │    │  FAILED   │───────┘
    └───────────┘    └───────────┘
                     (retry if enabled)
```

### Refund Transitions

| From | To | Trigger | Actor |
|------|----|---------|-------|
| PENDING | READY | System marks refund ready | System |
| READY | DELIVERING | `/ec refund execute` or `/ec refund claim` | Staff/Player |
| DELIVERING | COMPLETED | All items delivered | System |
| DELIVERING | READY | Partial delivery — back to READY for retry | System |
| DELIVERING | FAILED | Delivery error | System |
| FAILED | READY | `/ec refund retry` (if retry enabled) | Staff |

**Terminal state**: `COMPLETED` — permanently terminal. SQL-level guard
prevents any transition from COMPLETED.

**FAILED**: Recoverable. Not terminal. Can transition to READY for retry
if `refunds.retry-failed-refunds` is enabled.

### RefundItem Lifecycle

Each refund item has its own delivery state:

| Status | Description |
|--------|-------------|
| `PENDING` | Not yet delivered |
| `PARTIALLY_DELIVERED` | Some quantity delivered, some remaining |
| `DELIVERED` | Fully delivered |
| `FAILED` | Delivery attempted but failed |

`findPendingByRefundId` returns items with status `PENDING` **or**
`PARTIALLY_DELIVERED`, ensuring partially delivered items are not lost.

### Partial Delivery

When inventory capacity is insufficient:

1. `RefundDeliveryAdapter.deliverItem()` returns a partial result
2. `deliveredQuantity` is updated to reflect what was inserted
3. Item status becomes `PARTIALLY_DELIVERED`
4. Refund transitions back to `READY`
5. Player can free inventory space and execute again
6. Only remaining quantity is delivered on next execution

### Completion Rule

A refund may only become `COMPLETED` when **all** refund items have
`deliveredQuantity == refundableQuantity`. The service checks persisted
quantities, not just item status, to prevent completion when quantities
disagree.

### Concurrency Guarantees

- **Optimistic concurrency**: All status transitions use version-based CAS
- **Atomic transactions**: Refund + items + audit in a single DB transaction
- **No concurrent execution**: `startDelivery` CAS ensures one owner
- **COMPLETED is permanently terminal**: SQL guard `AND status != 'COMPLETED'`

### Crash Recovery

See [docs/CRASH_RECOVERY.md](CRASH_RECOVERY.md) for full crash/recovery
semantics, including the crash window between DB commit and Bukkit inventory
mutation, duplicate delivery risk, and recovery procedures.

### Staff/Admin Workflow

1. **Review finalized** → Refund created automatically (if items are refundable)
2. **Check refund status**: `/ec refund status <ref>` (requires `echoclaims.staff.refund.status`)
3. **Execute refund**: `/ec refund execute <ref>` (requires `echoclaims.staff.refund.execute`)
4. **View history**: `/ec refund history <ref>` (requires `echoclaims.staff.refund.history`)
5. **Retry failed**: `/ec refund retry <ref>` (requires `echoclaims.staff.refund.retry`)
6. **Player self-claim**: `/ec refund claim <ref>` (requires `echoclaims.refund.claim`)
7. **View pending**: `/ec refund pending` (requires `echoclaims.refund.pending`)

### Permissions

| Permission | Description |
|------------|-------------|
| `echoclaims.staff.refund.status` | View refund status |
| `echoclaims.staff.refund.execute` | Execute refund on behalf of player |
| `echoclaims.staff.refund.history` | View refund audit history |
| `echoclaims.staff.refund.retry` | Retry failed refund |
| `echoclaims.refund.pending` | View own pending refunds |
| `echoclaims.refund.claim` | Claim own refund (self-delivery) |
| `echoclaims.admin` | All refund operations |
