# EchoClaims Architecture

## Layered Design

```
paper adapters / listeners / commands
        ↓
application services
        ↓
domain model
        ↓
repository interfaces

integration bridges
        ↓
provider-neutral integration interfaces
```

The domain package must not import Bukkit, Paper, or any external plugin classes.

## Package Structure

```
io.github.skyblueheat.echoclaims
├── application/          # Application services
│   ├── ItemValueScorer   # Pure item scoring
│   ├── StatusService     # Status report aggregation
│   ├── EvidenceMetrics   # Thread-safe evidence capture/persistence counters
│   ├── DeduplicationKeyFactory  # Deterministic semantic dedup keys
│   ├── EvidencePersistenceService  # Bounded async evidence writer
│   ├── EvidenceLookupService  # Read-only evidence queries for admin commands
│   ├── ClaimEligibilityService  # Claim eligibility validation
│   ├── ClaimCreationService  # Claim creation with duplicate protection
│   ├── ClaimTransitionService  # Claim status transitions with optimistic concurrency
│   ├── ClaimTransitionPolicy  # Pure state machine for allowed transitions
│   ├── ClaimLookupService  # Read-only claim queries
│   ├── ClaimRateLimitService  # In-memory rate limiter with bounded memory
│   ├── ClaimReferenceGenerator  # 8-char alphanumeric reference generator
│   ├── ClaimMetrics  # Thread-safe claim lifecycle counters
│   ├── ReviewService  # Review lifecycle orchestration with atomic persistence
│   ├── ReviewServiceConfig  # Immutable review limits and flags
│   ├── ReviewTransitionPolicy  # Actor-aware review state machine
│   ├── ReviewOutcomePolicy  # Derives/validates final outcomes from item decisions
│   ├── ReviewEvidenceSelectionSession  # Per-staff, per-claim evidence selection
│   └── ReviewMetrics  # Thread-safe review lifecycle counters
├── config/               # Configuration loading and validation
│   ├── ConfigurationSource
│   ├── MapConfigurationSource
│   ├── EchoClaimsSettings
│   ├── SettingsLoader
│   ├── SettingsLoadResult
│   └── MessageCatalog
├── domain/               # Pure domain model (no Bukkit imports)
│   ├── audit/            # AuditRecord
│   ├── claim/            # Claim, ClaimStatus, ClaimSource, ClaimAuditEntry, ClaimActorType, ClaimAction
│   ├── review/           # ClaimReview, ClaimReviewComment, ClaimReviewItemDecision, ReviewState, ReviewOutcome, ReviewItemOutcome, ReviewReasonCode, CommentType, CommentVisibility, ReviewTransitionPolicy, ReviewOutcomePolicy
│   ├── content/          # ContentKey, IdentifiedContent, Capability, SemanticRole
│   ├── incident/         # Incident, IncidentType, IncidentStatus
│   ├── item/             # ItemDescriptor, ItemScore, ItemScoreWeights
│   ├── refund/           # (empty, reserved for future)
│   └── snapshot/         # InventorySnapshot, SnapshotItem, CaptureReason, Coordinates
├── integration/          # Provider-neutral integration layer
│   ├── ContentProvider
│   ├── EntityContentProvider
│   ├── ItemContentProvider
│   ├── IntegrationRegistry
│   ├── ProviderRegistry
│   ├── ProviderHealth
│   ├── ProviderStatus
│   ├── ItemSerializer    # Interface for item byte serialization
│   ├── ItemSerializationException
│   ├── bukkit/           # Bukkit adapters
│   │   ├── BukkitItems
│   │   └── BukkitItemSerializer  # Paper byte serialization + Base64 + versioning
│   └── vanilla/          # Vanilla fallback providers
│       ├── VanillaEntityProvider
│       └── VanillaItemProvider
├── paper/                # Paper plugin layer
│   ├── EchoClaimsPlugin  # Main plugin class
│   ├── command/
│   │   └── EchoClaimsCommand  # status, incidents, incident, snapshot, claim, review subcommands
│   ├── config/
│   │   └── BukkitConfigurationSource
│   ├── listener/
│   │   └── DeathCaptureService  # PlayerDeathEvent + PlayerRespawnEvent listener
│   └── message/
│       └── PaperMessageService
└── persistence/          # SQLite persistence
    ├── AuditRecordRepository
    ├── SqliteAuditRecordRepository
    ├── AuditWriteQueue
    ├── DatabaseManager
    ├── InventorySnapshotRepository
    ├── SqliteInventorySnapshotRepository
    ├── IncidentRepository
    ├── SqliteIncidentRepository
    ├── ClaimRepository
    ├── SqliteClaimRepository
    ├── ClaimAuditRepository
    ├── SqliteClaimAuditRepository
    ├── ClaimStore
    ├── SqliteClaimStore
    ├── ClaimReviewRepository
    ├── SqliteClaimReviewRepository
    ├── ClaimReviewCommentRepository
    ├── SqliteClaimReviewCommentRepository
    ├── ClaimReviewItemDecisionRepository
    ├── SqliteClaimReviewItemDecisionRepository
    ├── ClaimReviewStore
    ├── SqliteClaimReviewStore
    ├── MapCodec           # Map ↔ delimited string codec for SQLite text columns
    └── migration/
        ├── Migration
        └── SchemaMigrator
```

## Key Design Decisions

### No database I/O on the main thread
All SQLite operations run on dedicated worker threads. The write queue uses a
bounded `ArrayBlockingQueue` so the server thread only performs a non-blocking
hand-off. The query executor is a single-thread `ExecutorService` for read
operations.

### Bounded write queue with drop accounting
When the queue is full, new records are dropped and counted rather than
applying back-pressure to the server. The `QueueStatus` record exposes
submitted, written, failed, dropped, and pending counts.

### Provider isolation
Each content provider runs in a `ProviderRegistry` with failure isolation.
A provider that throws repeatedly is suppressed after a configurable failure
limit, preventing a broken bridge from affecting the core.

### Configuration validation
All configuration values are validated on load. Invalid values are replaced
with documented defaults and logged as warnings. Startup never aborts due to
a configuration error.

### Forward-only migrations
Schema migrations are versioned, forward-only, and applied in a transaction.
The `schema_version` table records what was applied and when.

## Database Schema (v5)

- `schema_version` — migration tracking
- `audit_records` — immutable audit log with category, source, details, and timestamp
- `inventory_snapshots` — immutable player inventory state at capture time
- `snapshot_items` — individual items within a snapshot (FK to `inventory_snapshots`)
- `incidents` — discrete events that may produce lost-item claims, linked to snapshots
- `claims` — player claim requests against incidents, with status lifecycle and audit
- `claim_audit_entries` — immutable audit trail for every claim action
- `idx_claims_active_unique` — partial unique index preventing duplicate active claims per (incident, player)
- `claim_reviews` — staff reviews linked to claims, with state, outcome, reviewer, and version
- `claim_review_comments` — append-only comments (internal notes, info requests, player responses, final summaries)
- `claim_review_item_decisions` — per-evidence-item staff decisions with outcome, reason, and approved quantity

### Evidence capture flow

1. `DeathCaptureService` listens for `PlayerDeathEvent` on the server thread.
2. Bukkit objects (inventory, location, health, etc.) are converted to immutable
   domain records (`InventorySnapshot`, `Incident`) on the main thread.
3. Items are serialized via `BukkitItemSerializer` using Paper's byte serialization
   wrapped in Base64 with a version header.
4. `EvidencePersistenceService` accepts the snapshot+incident pair on a bounded
   single-thread executor. If the queue is full, the capture is rejected and counted.
5. The persistence worker inserts the snapshot and its items atomically in a
   transaction, then inserts the incident. Deduplication keys prevent duplicate
   incidents from being stored.
6. On `PlayerRespawnEvent`, a post-respawn snapshot is captured and linked to the
   incident via `updatePostEventSnapshot`.

### Deduplication

`DeduplicationKeyFactory` generates deterministic keys from semantic event data:
player UUID, time bucket (1-second granularity), world, and coordinates.
The `incidents` table enforces uniqueness on `deduplication_key`, so duplicate
submissions are rejected by the database and counted as duplicates in metrics.

### Item serialization

`BukkitItemSerializer` wraps Paper's `ItemStack.serializeAsBytes` in Base64 with
a 1-byte format version header. The maximum payload size is configurable
(default 65536 bytes). Items exceeding the limit are skipped with a warning.

### Claim lifecycle

Claims are created in DRAFT status by players. Players can submit (DRAFT →
SUBMITTED) or cancel (DRAFT/SUBMITTED → CANCELLED). CANCELLED is terminal.
Staff can start a review (SUBMITTED → UNDER_REVIEW), request information
(UNDER_REVIEW → WAITING_FOR_PLAYER), and finalize with an outcome
(APPROVED, PARTIALLY_APPROVED, REJECTED). See
[CLAIM_LIFECYCLE.md](CLAIM_LIFECYCLE.md) for the full state diagram.

### Review system

The review system allows staff to review submitted claims, examine evidence,
make per-item decisions, and finalize with an outcome. Key design points:

- **Assigned-reviewer enforcement**: when `require-assignment-for-mutations`
  is true, only the assigned reviewer can add notes, request information,
  create/update item decisions, and finalize the review.
- **Takeover requires a reason**: staff must provide a non-blank reason when
  taking over a review from another staff member.
- **REJECTED requires a summary**: finalizing with REJECTED outcome requires
  a non-blank final summary for auditability.
- **OTHER reason requires a note**: item decisions with `ReviewReasonCode.OTHER`
  must include a non-blank staff note.
- **Evidence selection sessions are claim-bound**: each staff member's evidence
  selection session is keyed by (staffUuid, claimId) to prevent index confusion.
- **Atomic multi-table persistence**: `SqliteClaimReviewStore` executes all
  SQL statements for a review operation in a single transaction with
  optimistic concurrency control on both the review and claim versions.
- **Comment visibility**: internal notes are only visible to staff; information
  requests, player responses, and final summaries are visible to claim participants.

### Claim atomicity and concurrency

`SqliteClaimStore` creates claims and audit entries in a single transaction.
Transitions use optimistic concurrency: the UPDATE includes a version check,
and only succeeds if the stored version matches the expected version. See
[CLAIM_MODEL.md](CLAIM_MODEL.md) for schema details.

## Populated Domain Areas

The following packages are now populated:

- `domain/incident` — `Incident`, `IncidentType`, `IncidentStatus`
- `domain/snapshot` — `InventorySnapshot`, `SnapshotItem`, `CaptureReason`, `Coordinates`
- `domain/claim` — `Claim`, `ClaimStatus`, `ClaimSource`, `ClaimAuditEntry`, `ClaimActorType`, `ClaimAction`
- `domain/review` — `ClaimReview`, `ClaimReviewComment`, `ClaimReviewItemDecision`, `ReviewState`, `ReviewOutcome`, `ReviewItemOutcome`, `ReviewReasonCode`, `CommentType`, `CommentVisibility`, `ReviewTransitionPolicy`, `ReviewOutcomePolicy`

The following remain reserved for future sprints:

- `domain/refund` — item restoration records
