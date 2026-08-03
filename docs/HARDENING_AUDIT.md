# EchoClaims Hardening Audit Report

**Branch:** `devin/echoclaims-hardening-01`
**Date:** 2026-08-03
**Base:** `origin/main`
**Build:** `./gradlew clean test shadowJar --rerun-tasks` — **BUILD SUCCESSFUL**
**Tests:** 125 passed, 0 failed

## Audit Document Path

`docs/HARDENING_AUDIT.md` — confirmed at this exact path.

## SQLite Filename Validation Rules

**Implementation:** `EchoClaimsSettings.validateSqliteFile()` at `src/main/java/.../config/EchoClaimsSettings.java`

**Strategy:** Whitelist approach — verify the value is a plain filename with no parent path component using `Path.of(value).getFileName()`.

**Accepted:**
- `echoclaims.db`
- `echo-claims.db`
- `echo_claims.db`
- `claims.v1.db` (multiple dots supported)
- `a.db`
- Any filename <= 255 characters with no path separators

**Rejected (falls back to `echoclaims.db`):**
- `.` (current directory)
- `..` (parent directory)
- `../claims.db` (path traversal)
- `folder/claims.db` (forward slash)
- `folder\claims.db` (backslash)
- `/claims.db` (absolute Unix path)
- `/var/claims.db` (absolute Unix path)
- `C:\claims.db` (absolute Windows path)
- `  ` (blank)
- `` (empty)
- `null` (null)
- Names exceeding 255 characters

**Tests:** `SettingsLoaderHardeningTest` — parameterized tests for 5 accepted and 10 rejected values, plus boundary tests for 255-char and 256-char names.

## Paper Main-Thread Startup Behavior

**Finding (BLOCKER — fixed):** `EchoClaimsPlugin.onEnable()` previously called `initializeStorage()` which called `future.get(30, TimeUnit.SECONDS)`. This **blocked the Paper main thread for up to 30 seconds** while waiting for SQLite initialization.

**Fix:** Redesigned `onEnable()` to be fully non-blocking:
1. `onEnable()` submits `initializeStorageAsync()` to `queryExecutor` and returns immediately
2. `initializeStorageAsync()` runs `databaseManager.initialize()` on the executor thread
3. On success, schedules `onStorageReady()` back on the main thread via `getServer().getScheduler().runTask()`
4. On failure, schedules `disablePlugin()` back on the main thread
5. Command is registered immediately in `onEnable()` — `/echoclaims status` returns a "not-ready" message until `statusService` is set

**Call path (after fix):**
```
onEnable() [main thread]
  -> queryExecutor.submit(initializeStorageAsync) [returns immediately]
  -> registerCommand() [main thread, non-blocking]
  -> return

initializeStorageAsync() [executor thread]
  -> databaseManager.initialize() [executor thread, SQLite calls]
  -> runTask(onStorageReady or disablePlugin) [schedules on main thread]

onStorageReady() [main thread, via runTask]
  -> create writeQueue, integrations, statusService
```

**No blocking calls in `onEnable()`:** No `Future.get()`, no `join()`, no `awaitTermination()`, no `Thread.sleep()`.

## Initialization Timeout Cleanup

**Previous behavior:** `future.cancel(true)` on timeout — insufficient because the executor thread could continue running.

**Current behavior (after async redesign):**
- **Executor shutdown:** `onDisable()` calls `queryExecutor.shutdownNow()` followed by `awaitTermination(5, SECONDS)`
- **Executor termination:** Verified by `awaitTermination` with 5-second timeout; warning logged if not terminated
- **Interrupted status preservation:** `onDisable()` catches `InterruptedException` and calls `Thread.currentThread().interrupt()`
- **Database connection cleanup:** `DatabaseManager` uses try-with-resources for all connections; no persistent connections held
- **Prevention of late "storage ready" state:** `onStorageReady()` checks `enabled` flag — if plugin is disabled, logs warning and returns without creating writeQueue/integrations/statusService
- **Prevention of commands becoming ready after disable:** `status()` checks if `statusServiceSupplier.get()` returns null — sends "not-ready" message
- **No initialization thread remaining after failure:** On failure, `disablePlugin()` is scheduled on main thread; `onDisable()` shuts down the executor

## Provider Registry Freeze Atomicity

**Synchronization strategy:** Both `register()` and `freeze()` use `synchronized(this)` on the same monitor. The `frozen` `AtomicBoolean` is checked inside the synchronized block in `register()`, and `freeze()` is synchronized, so no registration can race with freezing.

**Lookup behavior:**
- **Lookups allowed before freeze:** Yes — `identify()` does not check `frozen`. This is intentional: lookups are read-only on a `CopyOnWriteArrayList` and are safe at any time.
- **Registration after first lookup:** Allowed — lookup does not freeze the registry. Only explicit `freeze()` call prevents further registrations.

**Concurrency test:** `ProviderRegistryHardeningTest.concurrentRegisterAndFreezeNoRace` — 16 threads, one calls `freeze()` while 15 attempt `register()`. All 15 either succeed (registered before freeze) or throw `IllegalStateException` (frozen before registration). No race condition; `isFrozen()` is always true after the test.

## Audit Write Queue Metric Semantics

**Counter definitions:**

| Counter | When incremented | `submitted` incremented? |
|---------|------------------|------------------------|
| `submitted` | Record passes `running` check | — |
| `written` | Batch successfully persisted by repository | Yes (prior) |
| `failed` | Repository throws on batch write | Yes (prior) |
| `dropped` | Record rejected before acceptance (`running=false`) | No |
| `overflowDropped` | Record accepted but queue is full (`offer` fails) | Yes (prior) |
| `abandoned` | Record accepted but not persisted before shutdown timeout | Yes (prior) |
| `pending` | Current queue size (not a counter) | — |

**Invariant:**

```
submitted = written + failed + abandoned + overflowDropped + pending
```

`dropped` is a separate counter for pre-acceptance rejections and is NOT part of the invariant because `submitted` is not incremented for dropped records.

**Tests proving the invariant:**
- `metricsAreConsistentAfterFullCycle` — 10 records submitted, 10 written, invariant holds
- `metricInvariantHoldsWithAbandonedRecords` — 3 records submitted, some abandoned after shutdown timeout, invariant holds
- `metricInvariantHoldsWithQueueFullDrop` — 100 records submitted, 84 overflow-dropped, 16 pending, invariant holds
- `dropsInsteadOfBlockingWhenTheQueueIsFull` (in `AuditWriteQueueTest`) — 40 submitted, 24 overflow-dropped, 16 pending, invariant holds

## Migration Validation

**Implementation:** `SchemaMigrator` has a static initializer block that validates the `MIGRATIONS` list (the actual immutable list used by `migrate()`).

**Checks applied to the actual migration collection:**
- **Duplicate versions:** Static initializer throws `IllegalStateException` if any two migrations share the same version
- **Ordering:** `migrate()` iterates `MIGRATIONS` in list order; tests verify ascending order
- **No gaps:** Tests verify versions are contiguous (1, 2, 3, ...)
- **Starts at 1:** First migration version must be 1
- **`latestVersion()` matches last migration:** Verified by test
- **`migrations()` returns the same immutable instance:** Verified by `assertSame` — validation applies to the actual collection used by `migrate()`
- **Future extension:** Re-running `initialize()` on a fully-migrated database applies 0 migrations (idempotent)
- **Retry after rollback:** Failed migration rolls back; subsequent `initialize()` succeeds

**Tests:** `SchemaMigratorFailureTest` — 11 tests covering all above scenarios.

## Findings Table

| # | Severity | Component | Finding | Fix | Regression Test |
|---|----------|-----------|---------|-----|-----------------|
| 1 | BLOCKER | Config | `retention.days` config field exposed unimplemented pruning | Removed from `EchoClaimsSettings`, `SettingsLoader`, `config.yml` | `SettingsLoaderHardeningTest.retentionDaysIsNotPresentInSettings` |
| 2 | BLOCKER | Config | `sqlite-file` accepted path traversal, slashes, backslashes | Rewrote `validateSqliteFile()` using `Path.of(value).getFileName()` whitelist approach | `SettingsLoaderHardeningTest` — 5 accepted, 10 rejected, 2 boundary |
| 3 | BLOCKER | Command | `/echoclaims status` blocked main thread with SQLite calls | Submit `collect()` to `queryExecutor`, send results via `runTask` | `BundledResourcesTest.messagesUsedByTheCommandExist` (not-ready key) |
| 4 | BLOCKER | Plugin | `onEnable()` blocked main thread up to 30s via `Future.get(30s)` | Redesigned to async: submit init to executor, schedule post-init via `runTask` | Architectural evidence: no blocking calls in `onEnable()` |
| 5 | BLOCKER | ProviderRegistry | `register()` race condition with `clear()` + `addAll()` | Wrapped in `synchronized(this)` | `ProviderRegistryHardeningTest.concurrentLookupIsSafe` |
| 6 | HIGH | AuditWriteQueue | Double-start could spawn two writer threads | `AtomicBoolean.compareAndSet` guard | `AuditWriteQueueConcurrencyTest.doubleStartThrows` |
| 7 | HIGH | AuditWriteQueue | Shutdown before start threw on unstarted thread | `started.get()` check | `AuditWriteQueueConcurrencyTest.shutdownBeforeStartReturnsImmediately` |
| 8 | HIGH | AuditWriteQueue | Late records after shutdown not accounted | Post-`stopped.await()` drain counts as `abandoned` | `AuditWriteQueueConcurrencyTest.simultaneousSubmitAndShutdown` |
| 9 | HIGH | Plugin | `future.cancel(true)` insufficient for timeout cleanup | Replaced with async design; `onDisable()` shuts down executor with `awaitTermination` | Architectural evidence in `onDisable()` |
| 10 | HIGH | Plugin | Late "storage ready" state after plugin disable | `onStorageReady()` checks `enabled` flag | Architectural evidence in `onStorageReady()` |
| 11 | HIGH | ProviderRegistry | No immutability after startup | `freeze()`/`isFrozen()` with synchronized `register()` | `ProviderRegistryHardeningTest.frozenRegistryRejectsRegistration`, `concurrentRegisterAndFreezeNoRace` |
| 12 | HIGH | AuditWriteQueue | `dropped` counter conflated pre-acceptance rejections and queue-full drops | Split into `dropped` (pre-acceptance) and `overflowDropped` (queue full) | `AuditWriteQueueTest.dropsInsteadOfBlockingWhenTheQueueIsFull`, `metricInvariantHoldsWithQueueFullDrop` |
| 13 | HIGH | SchemaMigrator | No duplicate migration version validation | Static initializer checks `MIGRATIONS` list | `SchemaMigratorFailureTest.migrationVersionsAreUnique` |
| 14 | HIGH | ProviderRegistry | `freeze()` not synchronized with `register()` | Both methods use `synchronized(this)` on same monitor | `ProviderRegistryHardeningTest.concurrentRegisterAndFreezeNoRace` |
| 15 | MEDIUM | Config | Invalid locale, extreme values, unsafe paths not tested | 29 parameterized + individual tests | `SettingsLoaderHardeningTest` |
| 16 | MEDIUM | MessageCatalog | MiniMessage injection not tested | 11 tests | `MessageCatalogSafetyTest` |
| 17 | MEDIUM | DatabaseManager | PRAGMAs, FK enforcement, edge cases not tested | 9 tests | `DatabaseManagerTest` |
| 18 | MEDIUM | AuditWriteQueue | Concurrency, shutdown, metric invariants not tested | 12 tests | `AuditWriteQueueConcurrencyTest` |
| 19 | MEDIUM | SqliteAuditRecordRepository | Failure injection not tested | 7 tests | `SqliteAuditRecordRepositoryFailureTest` |
| 20 | MEDIUM | SchemaMigrator | Rollback, retry, ordering, gaps not tested | 11 tests | `SchemaMigratorFailureTest` |
| 21 | MEDIUM | ProviderRegistry | Null handling, health, ordering not tested | 11 tests | `ProviderRegistryHardeningTest` |
| 22 | MEDIUM | StatusService | Not-ready state, providers, uptime not tested | 3 tests | `StatusServiceHardeningTest` |

## Shadow JAR Inspection

- **File:** `build/libs/echoclaims-0.1.0-SNAPSHOT.jar`
- **WorldEcho references:** None
- **Test classes:** None
- **`.db` / `.log` files:** None
- **`plugin.yml`:** Exactly one
- **EchoClaims classes:** Main source classes only

## Changed Files

### Source (10 files modified):
1. `src/main/java/.../config/EchoClaimsSettings.java` — rewrote `validateSqliteFile()` with `Path.of` whitelist
2. `src/main/java/.../config/SettingsLoader.java` — removed `retention.days` loading
3. `src/main/java/.../integration/IntegrationRegistry.java` — added `freeze()`
4. `src/main/java/.../integration/ProviderRegistry.java` — synchronized `register()` and `freeze()`, added `freeze()`/`isFrozen()`
5. `src/main/java/.../paper/EchoClaimsPlugin.java` — async startup, `enabled` flag, executor cleanup
6. `src/main/java/.../paper/command/EchoClaimsCommand.java` — async status, not-ready check
7. `src/main/java/.../persistence/AuditWriteQueue.java` — double-start guard, `abandoned`/`overflowDropped` counters
8. `src/main/java/.../persistence/migration/SchemaMigrator.java` — duplicate version validation
9. `src/main/resources/config.yml` — removed `retention` section
10. `src/main/resources/messages_en.yml` + `messages_tr.yml` — added `not-ready` message key

### Tests (8 files added, 3 modified):
1. `src/test/.../application/StatusServiceHardeningTest.java` — 3 tests (new)
2. `src/test/.../application/StatusServiceTest.java` — 2 tests (new, created during hardening)
3. `src/test/.../config/MessageCatalogSafetyTest.java` — 11 tests (new)
4. `src/test/.../config/SettingsLoaderHardeningTest.java` — 29 tests (new, parameterized)
5. `src/test/.../integration/ProviderRegistryHardeningTest.java` — 11 tests (new)
6. `src/test/.../persistence/AuditWriteQueueConcurrencyTest.java` — 12 tests (new)
7. `src/test/.../persistence/DatabaseManagerTest.java` — 9 tests (new)
8. `src/test/.../persistence/SqliteAuditRecordRepositoryFailureTest.java` — 7 tests (new)
9. `src/test/.../persistence/migration/SchemaMigratorFailureTest.java` — 11 tests (new)
10. `src/test/.../config/SettingsLoaderTest.java` — modified (removed `retentionDays` assertion)
11. `src/test/.../persistence/AuditWriteQueueTest.java` — modified (updated for `overflowDropped`)
12. `src/test/.../resources/BundledResourcesTest.java` — modified (added `not-ready` key check)

## Test Count Breakdown

| Test Class | Count | Classification |
|-----------|-------|---------------|
| `ItemValueScorerTest` | 4 | Pre-existing |
| `SettingsLoaderTest` | 6 | Pre-existing (was 7, 1 removed for `retentionDays`) |
| `ProviderRegistryTest` | 4 | Pre-existing |
| `AuditWriteQueueTest` | 4 | Pre-existing (modified for `overflowDropped`) |
| `SqliteAuditRecordRepositoryTest` | 3 | Pre-existing |
| `SchemaMigratorTest` | 4 | Pre-existing |
| `BundledResourcesTest` | 5 | Pre-existing (modified for `not-ready` key) |
| **Pre-existing subtotal** | **30** | |
| `StatusServiceTest` | 2 | New (created during hardening) |
| `StatusServiceHardeningTest` | 3 | New |
| `MessageCatalogSafetyTest` | 11 | New |
| `SettingsLoaderHardeningTest` | 29 | New (parameterized) |
| `ProviderRegistryHardeningTest` | 11 | New |
| `AuditWriteQueueConcurrencyTest` | 12 | New |
| `DatabaseManagerTest` | 9 | New |
| `SqliteAuditRecordRepositoryFailureTest` | 7 | New |
| `SchemaMigratorFailureTest` | 11 | New |
| **New subtotal** | **95** | |
| **Total** | **125** | |

**Previous report correction:** The prior report claimed 31 pre-existing tests and 70 new tests (101 total). The actual pre-existing count was 30 (not 31 — `StatusServiceTest` was created during the hardening session, not pre-existing). The new test count is 95 (not 70), increased by the review fixes: parameterized SQLite filename tests, freeze atomicity tests, metric invariant tests, and migration validation tests.

## Validation Steps

1. `./gradlew clean test shadowJar --rerun-tasks` — BUILD SUCCESSFUL
2. Shadow JAR inspected: no WorldEcho, no test classes, no DB/log files, single plugin.yml
3. All 125 tests pass including 95 new hardening tests

## Known Limitations

- Paper runtime validation not yet performed (requires Paper server environment)
- CI verification pending (will be verified separately after this review)
- SQLite native library cleanup warning on Windows is a known SQLite JDBC issue, not an EchoClaims bug
