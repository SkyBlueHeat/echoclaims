# Product Direction

## Vision

EchoClaims resolves lost-item disputes on Paper servers using server-side
evidence instead of player-submitted screenshots. The server captures
immutable evidence (inventory snapshots, incidents) and players file claims
against that evidence. Staff review claims with full audit trails.

## Incident vs. Claim

An **incident** is an immutable server-captured event (e.g. player death).
Incidents are created automatically by event listeners and cannot be modified
by players or staff.

A **claim** is a player-initiated request for resolution against an incident.
Claims have a lifecycle (DRAFT → SUBMITTED → CANCELLED) and are fully audited.
Claims reference incidents but never modify them.

This separation ensures that evidence remains tamper-proof while giving players
a structured way to seek resolution.

## MVP-02 Scope

MVP-02 implements the claim foundation:

- Claim domain model (Claim, ClaimStatus, ClaimSource, ClaimAuditEntry,
  ClaimActorType, ClaimAction)
- Claim creation with eligibility checks and duplicate active claim protection
- Claim lifecycle: DRAFT → SUBMITTED, DRAFT/SUBMITTED → CANCELLED
- Optimistic concurrency control with version numbers
- Atomic persistence of claims and audit entries
- Rate limiting with bounded in-memory state
- Incident selection by index (no raw UUID typing required)
- Staff view and list commands
- Full audit trail for every claim action

## Out of Scope (Future Sprints)

The following are explicitly **not** in MVP-02:

- Refund or item restoration workflows
- Staff approval or rejection of submitted claims
- GUI-based claim management
- Mailbox or notification systems
- Discord integration
- MySQL or other external database support
- Automated claim resolution

## Design Principles

1. **Evidence is immutable.** Incidents and snapshots are never modified after
   creation. Claims reference them but cannot alter them.
2. **Every action is audited.** Each claim creation, transition, and rejection
   produces an audit entry with actor, action, reason, and timestamp.
3. **Fail closed.** Incompatible claim scenarios are rejected with a diagnostic
   reason, never silently accepted.
4. **No main-thread database access.** All SQLite operations run on worker
   threads. Bukkit data is captured into immutable records on the main thread
   before async processing.
5. **Bounded resources.** Rate limiter cleans expired entries. Query results
   are capped. Write queues are bounded with drop accounting.
6. **Provider-neutral.** The core never assumes a specific content provider.
   Integrations with Oraxen, ItemsAdder, MythicMobs, Citizens, and ModelEngine
   are optional bridges.
