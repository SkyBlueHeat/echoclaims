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
