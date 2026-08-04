# MVP-02 Validation Report

## Build

- **Command**: `./gradlew clean test shadowJar --rerun-tasks`
- **Result**: BUILD SUCCESSFUL
- **Tests**: 337 passed, 0 failed, 14 skipped (Paper-dependent tests)
- **Total**: 351 test cases

## Behavioral Policy

### Claim Creation Workflow

- `/ec claim create <incident-id-or-index> [description]` creates a claim in
  **DRAFT** status with a creation audit entry.
- `/ec claim submit <ref>` transitions a **DRAFT** claim to **SUBMITTED**.
- Description updates are not supported after creation in MVP-02 (description
  is set at creation time only).
- **Verified by**: `ClaimCreationServiceTest`, `ClaimTransitionServiceTest`,
  `ClaimTransitionPolicyTest`, `ClaimStoreTest`

### Cancellation Policy

- Players can cancel claims in **DRAFT** or **SUBMITTED** status.
- **CANCELLED** is a terminal state — no transitions out.
- Cancellation is performed via `/ec claim cancel <ref>`.
- **Verified by**: `ClaimTransitionPolicyTest`, `ClaimTransitionServiceTest`

### Repeat-Claim Policy

- A cancelled claim permits a new claim for the same incident by the same
  player.
- Enforced at:
  - **Application level**: `ClaimCreationService` checks
    `findOpenClaimsByIncident` before creating
  - **Database level**: Partial unique index `idx_claims_active_unique` on
    `(incident_id, player_uuid) WHERE status IN ('DRAFT', 'SUBMITTED')`
  - **Tests**: `ClaimStoreTest.databaseRejectsDuplicateActiveClaimForSameIncidentAndPlayer`,
    `ClaimStoreTest.cancelledClaimAllowsNewActiveClaimForSameIncident`,
    `MigrationV4Test.partialUniqueIndexPreventsDuplicateActiveClaims`,
    `MigrationV4Test.cancelledClaimsAllowNewActiveClaimForSameIncident`

### Incident Selection

- Players use `/ec claim claimable` to list their OPEN incidents with no
  existing active claim, displayed with a 1-based index.
- Players use `/ec claim create <index> [description]` to create a claim by
  index — no raw UUID typing required.
- Raw UUID input is still accepted for backward compatibility.
- **Verified by**: `EchoClaimsCommandTest` (command routing)

### Active Claim Definition

- **Active statuses**: `DRAFT`, `SUBMITTED`
- **Terminal status**: `CANCELLED`
- Only one active claim per (incident, player) pair is allowed.
- Different players can each have an active claim for the same incident.

## Database and Concurrency Audit

### Atomicity

- `SqliteClaimStore.createClaim`: claim + audit entry in single transaction.
  Failure rolls back both. **Tested**: `ClaimStoreTest.auditInsertionFailureRollsBackClaimCreation`
- `SqliteClaimStore.transitionClaim`: status update + audit entry in single
  transaction. Version mismatch returns false without inserting audit.
  **Tested**: `ClaimStoreTest.transitionClaimFailsOnVersionMismatch`,
  `ClaimStoreTest.transitionClaimUpdatesStatusAndVersion`

### Duplicate Active Claim Protection

- Application check via `findOpenClaimsByIncident` before creation.
- Database partial unique index on `(incident_id, player_uuid) WHERE status
  IN ('DRAFT', 'SUBMITTED')`.
- **Tested**: `ClaimStoreTest.databaseRejectsDuplicateActiveClaimForSameIncidentAndPlayer`,
  `MigrationV4Test.partialUniqueIndexPreventsDuplicateActiveClaims`

### Public Claim Reference Uniqueness

- `UNIQUE` constraint on `public_reference` column.
- `ClaimReferenceGenerator` generates 8-character alphanumeric references
  with collision retry.
- **Tested**: `ClaimStoreTest.findByPublicReferenceWorks`,
  `ClaimReferenceGeneratorTest`

### Bounded and Ordered List Queries

- `findRecentClaimsByPlayer`: `LIMIT ?` with `Math.max(1, Math.min(limit, 100))`
  clamping, ordered by `created_at DESC`.
- `findOpenClaimsByIncident`: ordered by `created_at DESC`, no limit (bounded
  by unique index — at most one active claim per player per incident).
- `findAuditEntries`: ordered by `recorded_at ASC`.
- `ClaimLookupService.maxRecentResults` clamped to [1, 100].
- **Tested**: `ClaimStoreTest.findRecentClaimsByPlayerReturnsOrderedResults`,
  `ClaimStoreTest.findOpenClaimsByIncidentReturnsOnlyOpen`

### Foreign Key Enforcement

- `PRAGMA foreign_keys = ON` set in `DatabaseManager`.
- `claims.incident_id` → `incidents.id` (ON DELETE RESTRICT)
- `claim_audit_entries.claim_id` → `claims.id` (ON DELETE RESTRICT)
- **Tested**: `DatabaseManagerTest.foreignKeyEnforcementIsActive`,
  `MigrationV3Test.claimAuditEntriesHasForeignKeyToClaims`,
  `ClaimStoreTest.auditInsertionFailureRollsBackClaimCreation`

### Immutability of Incidents and Snapshots

- Incidents and snapshots are never modified by claim operations.
- Claims only reference incident IDs; no UPDATE statements on incidents or
  snapshots tables originate from claim services.
- No `ON DELETE CASCADE` on claim FKs — `ON DELETE RESTRICT` prevents
  silent evidence deletion.

## Rate Limit Audit

### Bounded Memory

- `ConcurrentHashMap` with opportunistic cleanup of expired entries on each
  `check()` call.
- **Tested**: `ClaimRateLimitServiceTest.cleanupExpiredEntriesPreventsUnboundedGrowth`

### Expired Entry Cleanup

- `cleanupExpired(now)` removes entries where `now - entry.value > cooldownMillis`.
- Called on every `check()` when cooldown is positive.
- **Tested**: `ClaimRateLimitServiceTest.cleanupExpiredEntriesPreventsUnboundedGrowth`

### Non-Growth with Unique Player UUIDs

- After 100 unique players with 50ms cooldown, sleeping 100ms then checking
  reduces tracked count below 100.
- **Tested**: `ClaimRateLimitServiceTest.cleanupExpiredEntriesPreventsUnboundedGrowth`

### Non-Authoritative Role

- Rate limiter is a soft guard, not an authoritative duplicate/active claim
  barrier. Duplicate protection is enforced by application check + DB unique
  index.
- Rate limiter state is in-memory only, lost on restart.

### Restart Behavior

- New `ClaimRateLimitService` instance starts with empty state.
- **Tested**: `ClaimRateLimitServiceTest.restartResetsRateLimitState`

### Concurrency

- `ConcurrentHashMap` ensures thread-safe access.
- 10-thread concurrent test verifies no corruption.
- **Tested**: `ClaimRateLimitServiceTest.concurrentChecksAreSafe`

### No Log Spam

- Rate limiter does not log anything. Rejections are communicated via
  `RateLimitResult` to the caller.

## Async and Paper Safety Audit

### No Main-Thread SQLite

- All claim DB operations go through `queryExecutor.submit()`.
- `queryExecutor` is a single-thread daemon `ExecutorService`.
- **Verified**: Code review of `EchoClaimsCommand` — all DB calls are inside
  `queryExecutor.submit()` lambdas.

### No Retained Mutable Bukkit Objects

- Commands capture `player.getUniqueId()` (immutable UUID) before async work.
- `sender` reference is used in `syncScheduler.accept()` callbacks that run
  on the main thread via `runTask`.
- No `Player`, `Inventory`, or `Location` objects are passed to async work.

### Command Output on Main Thread

- All `syncScheduler.accept()` calls use `getServer().getScheduler().runTask()`.
- Messages are sent to the player on the main thread.

### Safe Handling of Disconnected Players

- `MessageService.send` handles sending to potentially offline senders
  gracefully (Bukkit handles this at the API level).

### Console Staff Commands

- `claimStaffView` and `claimStaffList` do not require `sender instanceof Player`.
- Permission checks work for console senders.

### Safe Rejection During Shutdown

- `onDisable` sets `enabled = false`, preventing `onStorageReady` from
  initializing services after shutdown.
- `queryExecutor.shutdownNow()` cancels pending tasks.
- `awaitTermination` with 5-second timeout ensures clean shutdown.

### No Silent Loss Due to Executor Saturation

- `queryExecutor` is unbounded (single-thread executor with unbounded queue).
- If the executor is shut down, `submit` throws `RejectedExecutionException`,
  which is caught by the command handler's try-catch.

## Scope Exclusions

The following are explicitly **not** implemented in MVP-02:

- Refund or item restoration
- Staff approval or rejection of submitted claims
- GUI-based claim management
- Mailbox or notification systems
- Discord integration
- MySQL or external database support
- Automated claim resolution
- Description updates after creation

## Paper Integration Test

- **Command**: `./gradlew paperIntegrationTest`
- **Result**: 14 passed, 0 failed, 0 skipped
- **Coverage**: Serialization round-trip tests on disposable Paper server

## Runtime Validation

- **Command**: `./gradlew runtimeValidation`
- **Result**: 58 passed, 0 failed, 0 skipped
- **Coverage**: Full claim flow on disposable Paper server with two headless bots
  - Plugin load and ready checks
  - Schema migration v1-v4 verification (claims tables, partial unique index)
  - Evidence capture (3 deaths, snapshots, incidents, metadata)
  - Claim flow: claimable list, create by index, list, view, submit, cancel
  - Claim DB verification: DRAFT -> SUBMITTED -> CANCELLED states
  - Duplicate active claim rejection after cancellation
  - Audit trail verification (CREATED, SUBMITTED, CANCELLED actions)
  - **Security flow checks (22 new):**
    - Incident row captured before claim operations
    - Inventory snapshot and snapshot-item evidence captured before claim operations
    - Evidence state captured for immutability comparison
    - Second claim created for security flow testing
    - Second headless player connected via MCProtocolLib
    - Cross-player claim creation attempt on first player's incident
    - Cross-player claim creation rejected (eligibility check)
    - Rejected attempt creates no claim row for second player
    - Rejected attempt creates no incorrect audit row
    - Cross-player claim view attempt on first player's claim
    - Cross-player view rejected (ownership check)
    - Console executes staff claim list
    - Console executes staff claim view
    - Console executes staff audit history (staff-view with audit entries)
    - Staff commands return expected claim and audit data
    - Cancelled claim rejects submit (no new audit entry)
    - Cancelled claim rejects second cancellation (no new audit entry)
    - Terminal transition creates no extra mutation or audit row
    - Incident evidence remains field-for-field unchanged after all operations
    - Snapshot evidence remains field-for-field unchanged after all operations
    - Claim public reference resolves after restart (DB persistence)
    - Complete audit history resolves after restart (DB persistence)
  - No exceptions in server log
  - Clean shutdown drain and no remaining threads

## Production JAR Inspection

- **No test/validation classes**: JAR contains only `io/github/skyblueheat/echoclaims` production classes and `org/sqlite` (sqlite-jdbc)
- **No external dependencies**: Only sqlite-jdbc is shaded; no other third-party libraries
- **No local paths**: No `C:\` or user-specific paths embedded in JAR contents
- **No WorldEcho identifiers**: Zero matches for `WorldEcho` or `world_echo`
- **No unwanted classes**: Zero matches for `HeadlessBotClient`, `RuntimeValidationPlugin`, `org/geysermc`, `io/netty`, test classes
- **No generated artifacts**: Zero matches for databases (`.db`), logs (`.log`), worlds, validation result files
- **Single plugin.yml**: Exactly 1 `plugin.yml` entry
- **Smoke test**: `shadowJarSmokeTest` passes — SQLite database opens successfully using only the shaded JAR
- **CI JAR inspection**: GitHub Actions workflow fails on any forbidden pattern match

## GitHub Actions CI

- **Workflow**: `.github/workflows/ci.yml`
- **Triggers**: Pull requests and pushes to `main`
- **Runner**: `ubuntu-latest` with JDK 25 (Temurin)
- **Jobs**: Single job with 30-minute timeout
- **Steps**:
  1. `./gradlew clean test shadowJar --rerun-tasks`
  2. `./gradlew paperIntegrationTest --rerun-tasks --no-configuration-cache`
  3. `./gradlew runtimeValidation --rerun-tasks --no-configuration-cache`
  4. Production JAR inspection (fails on forbidden patterns)
- **Artifacts**: Test reports and runtime validation evidence uploaded on failure
- **Permissions**: `contents: read` only
- **No `continue-on-error`**: All steps must pass
