# Changelog

All notable changes to EchoClaims will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/).

## [0.1.0-SNAPSHOT] - 2025-08-03

### MVP-04: Refund System

### Added
- Refund domain model: `Refund`, `RefundItem`, `RefundAuditEntry`,
  `RefundStatus` (PENDING, READY, DELIVERING, COMPLETED, FAILED),
  `RefundItemStatus` (PENDING, PARTIALLY_DELIVERED, DELIVERED, FAILED),
  `RefundAction`
- `RefundDeliveryAdapter` interface for provider-neutral inventory insertion
  with `ItemDeliveryResult` (success, partial, failure)
- `RefundService` — orchestrates refund creation, execution, retry, and
  completion with optimistic concurrency control
- `RefundServiceConfig` — immutable config record for refund settings
- Schema migration v6: `refunds`, `refund_items`, `refund_audit_entries`
  tables with indexes, foreign keys, and optimistic concurrency support
- Repository interfaces and SQLite implementations:
  - `RefundRepository` / `SqliteRefundRepository`
  - `RefundItemRepository` / `SqliteRefundItemRepository`
  - `RefundAuditRepository` / `SqliteRefundAuditRepository`
  - `RefundStore` / `SqliteRefundStore` with atomic multi-table transactions
- `RefundCommandHandler` — delegate for `/ec refund` subcommands:
  `status`, `execute`, `history`, `retry`, `pending`, `claim`
- Refund configuration: `refunds.enabled`, `refunds.player-self-claim`,
  `refunds.auto-deliver-on-login`, `refunds.max-items-per-execution`,
  `refunds.retry-failed-refunds`
- New permissions: `echoclaims.staff.refund.status`, `.execute`, `.history`,
  `.retry`, `echoclaims.refund.pending`, `.claim`
- Message keys for refund commands (en, tr)
- `RefundStatus.isTerminal()` — COMPLETED is terminal; FAILED is recoverable
- SQL-level guard preventing re-completion of COMPLETED refunds
- Partial delivery support: items can be partially delivered when inventory
  is full, with automatic retry on subsequent execution
- Documentation: [docs/CRASH_RECOVERY.md](docs/CRASH_RECOVERY.md)
- Tests:
  - Concurrency test matrix (8 scenarios) — `RefundStoreConcurrencyTest`
  - Atomicity failure injection tests (7 scenarios) — `RefundStoreAtomicityTest`
  - Partial delivery correctness tests — `RefundServiceTest`
  - Paper inventory delivery test matrix (7 scenarios) — `RefundServiceTest`
  - Command and authorization tests (23 scenarios) — `RefundCommandHandlerTest`
  - Domain/refund creation rule tests (7 scenarios) — `RefundCreationRuleTest`
  - Refund aggregate completion tests (5 combinations) — `RefundCreationRuleTest`
  - ItemStack round-trip tests (14 scenarios) — `BukkitItemSerializerTest`
  - Runtime validation: golden path, partial delivery, inventory capacity,
    restart persistence — `RuntimeValidationPlugin`

### Fixed
- `RefundStatus.isTerminal()` now returns `true` only for `COMPLETED`,
  not for `FAILED` (FAILED is recoverable via retry)
- `updateRefundStatus` SQL now includes `AND status != 'COMPLETED'` guard
  to prevent re-completion race conditions

### MVP-03: Staff Review System

### Added
- Review domain model: `ClaimReview`, `ClaimReviewComment`,
  `ClaimReviewItemDecision`, `ReviewState` (OPEN, FINALIZED),
  `ReviewOutcome` (APPROVED, PARTIALLY_APPROVED, REJECTED),
  `ReviewItemOutcome`, `ReviewReasonCode`, `CommentType`, `CommentVisibility`
- Extended `ClaimStatus` with UNDER_REVIEW, WAITING_FOR_PLAYER, APPROVED,
  PARTIALLY_APPROVED, REJECTED
- Extended `ClaimAction` with REVIEW_STARTED, REVIEW_TAKEN_OVER,
  INTERNAL_NOTE_ADDED, INFORMATION_REQUESTED, PLAYER_RESPONDED,
  ITEM_DECISION_CREATED, ITEM_DECISION_UPDATED, REVIEW_APPROVED,
  REVIEW_PARTIALLY_APPROVED, REVIEW_REJECTED
- Schema migration v5: `claim_reviews`, `claim_review_comments`,
  `claim_review_item_decisions` tables with indexes, foreign keys, and
  uniqueness constraints
- Repository interfaces and SQLite implementations:
  - `ClaimReviewRepository` / `SqliteClaimReviewRepository`
  - `ClaimReviewCommentRepository` / `SqliteClaimReviewCommentRepository`
  - `ClaimReviewItemDecisionRepository` / `SqliteClaimReviewItemDecisionRepository`
- `ClaimReviewStore` interface and `SqliteClaimReviewStore` with atomic
  multi-table transactions and optimistic concurrency control
- `ReviewTransitionPolicy` — actor-aware state machine for review transitions
- `ReviewOutcomePolicy` — derives and validates final outcomes from item decisions
- `ReviewService` — orchestrates review lifecycle with config-driven limits,
  assigned-reviewer enforcement, takeover reason validation, and audit trail
- `ReviewServiceConfig` — immutable config record for review limits and flags
- `ReviewEvidenceSelectionSession` — per-staff, per-claim evidence selection
  with TTL expiration and bounded staff count
- `ReviewMetrics` — thread-safe counters for review lifecycle events
- Review commands: `/ec review queue`, `view`, `start`, `takeover`, `evidence`,
  `note`, `request-info`, `item`, `approve`, `partial`, `reject`, `history`
- Player response command: `/ec claim respond`
- Review configuration: `reviews.enabled`, `reviews.evidence-session-ttl-seconds`,
  `reviews.evidence-session-max-staff`, `reviews.queue-page-size`,
  `reviews.max-internal-note-length`, `reviews.max-question-length`,
  `reviews.max-player-response-length`, `reviews.max-final-summary-length`,
  `reviews.max-item-note-length`, `reviews.max-item-decisions-per-claim`,
  `reviews.max-active-reviews-per-staff`, `reviews.player-response-cooldown-seconds`,
  `reviews.require-assignment-for-mutations`,
  `reviews.allow-player-cancel-after-review-start`
- New permissions: `echoclaims.staff.review.queue`, `.view`, `.start`,
  `.takeover`, `.evidence`, `.note`, `.request-info`, `.item-decision`,
  `.approve`, `.partial`, `.reject`, `.history`, `echoclaims.claim.respond`
- Message keys for review commands (en, tr)
- Tests for review domain models, policies, migration v5, persistence,
  atomicity, concurrency, authorization, commands, and configuration

### MVP-02: Claim Foundation

### Added
- Claim domain model: `Claim`, `ClaimStatus` (DRAFT, SUBMITTED, CANCELLED),
  `ClaimSource`, `ClaimAuditEntry`, `ClaimActorType`, `ClaimAction`
- Schema migration v3: `claims` and `claim_audit_entries` tables with foreign
  keys, indexes, and uniqueness constraints
- Schema migration v4: partial unique index `idx_claims_active_unique` on
  `(incident_id, player_uuid) WHERE status IN ('DRAFT', 'SUBMITTED')` for
  database-level duplicate active claim protection
- `ClaimStore` interface and `SqliteClaimStore` with atomic transactions for
  claim creation and transitions
- `ClaimTransitionPolicy` — pure state machine for allowed transitions
- `ClaimReferenceGenerator` — 8-character alphanumeric collision-safe references
- `ClaimMetrics` — thread-safe counters for claim lifecycle events
- `ClaimEligibilityService` — validates incident ownership, OPEN status, and
  snapshot existence
- `ClaimRateLimitService` — in-memory rate limiter with bounded memory and
  opportunistic cleanup of expired entries
- `ClaimCreationService` — creates DRAFT claims with eligibility and duplicate
  checks
- `ClaimTransitionService` — transitions claim status with optimistic
  concurrency control
- `ClaimLookupService` — read-only claim queries for player and staff commands
- Claim commands: `/ec claim list`, `view`, `create`, `submit`, `cancel`,
  `claimable`, `staff-view`, `staff-list`
- Incident selection by index — `/ec claim claimable` lists claimable incidents,
  `/ec claim create <index>` creates a claim without typing UUIDs
- Claim configuration: `claims.enabled`, `claims.rate-limit-cooldown-seconds`,
  `claims.max-description-length`
- New permissions: `echoclaims.command.claim.list`, `.view`, `.create`,
  `.cancel`, `.submit`, `.staff.view`, `.staff.list`
- Message keys for claim commands (en, tr)
- `StatusService` extended with claim metrics
- Tests for claim domain, transition policy, eligibility, reference generator,
  rate limiter (including bounded memory, cleanup, restart, concurrency),
  claim store (atomicity, duplicate protection, optimistic concurrency),
  migration v3 and v4, claim creation and transition services

### MVP-01: Evidence Capture Foundation

### Added
- Immutable domain records for evidence capture:
  - `InventorySnapshot` — full player inventory state with items, stats, location
  - `SnapshotItem` — single item in a snapshot slot with serialization and scoring
  - `Incident` — discrete event linking pre/post snapshots with metadata
  - `CaptureReason` enum (PRE_DEATH, POST_RESPAWN, MANUAL_ADMIN)
  - `IncidentType` enum (PLAYER_DEATH)
  - `IncidentStatus` enum (OPEN, RESOLVED, REJECTED)
  - `Coordinates` record for block-level positions
- Item serialization via `BukkitItemSerializer` using Paper byte serialization
  wrapped in Base64 with a 1-byte format version header
- Schema migration v2: `inventory_snapshots`, `snapshot_items`, `incidents` tables
  with foreign keys, indexes, and uniqueness constraints
- Repository interfaces and SQLite implementations:
  - `InventorySnapshotRepository` / `SqliteInventorySnapshotRepository`
  - `IncidentRepository` / `SqliteIncidentRepository`
- `MapCodec` utility for encoding/decoding maps to delimited strings for SQLite
- `EvidenceMetrics` thread-safe counters for captures, persistence, duplicates, rejections
- `DeduplicationKeyFactory` for deterministic semantic dedup keys
- `EvidencePersistenceService` — bounded single-thread async executor for evidence writes
- `EvidenceLookupService` — read-only queries for admin commands
- `DeathCaptureService` — `PlayerDeathEvent` and `PlayerRespawnEvent` listener
- Admin commands: `/echoclaims incidents <player>`, `/echoclaims incident <uuid>`,
  `/echoclaims snapshot <uuid>`
- Evidence configuration: `evidence.capture.player-death`, `evidence.capture.post-respawn`,
  `evidence.max-recent-results`, `evidence.max-item-payload-bytes`, `evidence.queue-capacity`
- New permissions: `echoclaims.command.incidents`, `echoclaims.command.incident`,
  `echoclaims.command.snapshot`
- Message keys for incident and snapshot display (en, tr)
- Tests for domain models, MapCodec, migration v2, repositories, deduplication,
  evidence persistence service, and configuration
- Updated `StatusService` to report evidence metrics

### Verified
- Paper runtime validation completed on Paper 26.2 build 87 with Java 25
  (Eclipse Adoptium Temurin 25.0.4+7 LTS) on Windows 10.
- Plugin loaded and enabled successfully on a clean Paper server.
- SQLite storage initialized asynchronously at `plugins/EchoClaims/echoclaims.db`.
- Schema migration version 1 completed successfully.
- `/echoclaims status` and `/ec status` produced correct output (version, locale,
  database availability, schema version, queue counts, providers, uptime).
- Database status reported `ok`; queue pending, failed, and dropped counts were zero.
- Server shut down cleanly; audit write queue drained without exceptions.
- No WorldEcho branding appeared in runtime logs, commands, or generated data.
- No startup, migration, command, database, or shutdown exceptions occurred.
- Full report: [docs/RUNTIME_VALIDATION.md](docs/RUNTIME_VALIDATION.md)

### Added
- EchoClaims plugin bootstrap from WorldEcho infrastructure.
- Plugin lifecycle (`EchoClaimsPlugin`) with startup and shutdown.
- SQLite initialization with idempotent schema migration (v1: audit_records table).
- Asynchronous bounded write queue (`AuditWriteQueue`) with batch writes and drop accounting.
- Clean queue shutdown with configurable timeout and drain behavior.
- Provider-neutral content identification (`IntegrationRegistry`, `ProviderRegistry`).
- Vanilla fallback providers for items and entities.
- Bukkit item mapping (`BukkitItems`) to immutable `ItemDescriptor` records.
- Deterministic item value scoring (`ItemValueScorer`) with configurable weights.
- `/echoclaims status` command (alias: `/ec status`) reporting version, database,
  queue state, providers, and uptime.
- Configuration validation with safe defaults and warning logging.
- Locale-aware message catalog with MiniMessage rendering (en, tr).
- Audit record domain model and SQLite repository.
- Empty domain areas prepared for incidents, inventory snapshots, claims, and refunds.
- Tests for settings loader, schema migrator, write queue, provider registry,
  item scorer, status service, audit repository, and bundled resources.
- Documentation: README, ARCHITECTURE, BOOTSTRAP_FROM_WORLDECHO, DEVELOPMENT.

### Removed
- All WorldEcho-specific story, memory, narrative, eligibility, binding, and
  scenario logic.
- WorldEcho-specific database tables (story_events, tracked_items, ownership
  ledgers, lot tables, lineage).
- WorldEcho-specific commands (recent, inspect, reload, eligibility, item).
- WorldEcho-specific permissions and configuration paths.
- bindings.yml resource file.
