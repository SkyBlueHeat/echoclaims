# Changelog

## 0.2.0-SNAPSHOT

### Added

- Initial Paper 26.2 / Java 25 project scaffold
- Committed Gradle 9.6.1 wrapper and a GitHub Actions build that runs
  `./gradlew clean test shadowJar` on Java 25
- Provider-neutral content identity model
- Generic `ProviderRegistry` with priority ordering, vanilla fallback, and automatic
  suppression of a bridge that throws
- Vanilla entity and item providers
- SQLite event persistence with versioned, idempotent migrations and indexes on
  `occurred_at`, `player_id`, and `event_type`
- Bounded single-writer queue with batched transactions, drop accounting, and a clean
  shutdown drain, plus a separate reader thread for command queries
- Player death capture that reads Bukkit state only on the server thread and hands an
  immutable event to the write path
- Deterministic, configuration-driven item value scoring with an explainable breakdown
- Validating configuration loader that replaces invalid values with documented defaults
- `/worldecho status`, `recent`, `inspect item|entity`, and `reload` with tab completion
- English and Turkish message files with locale fallback and placeholder sanitization
- `docs/CONFIGURATION.md` and `docs/TESTING.md`
- Tests for scoring, event mapping, configuration validation, messages, provider priority
  and failure isolation, migrations, repository round-trips, the write queue, and the
  shipped resources

### Fixed

- The shaded JAR relocated `org.sqlite`, which broke JNI binding for the bundled native
  library and disabled the plugin during `onEnable` with `UnsatisfiedLinkError`. The
  relocation is gone and `shadowJar` is now finalized by a smoke test that opens a real
  SQLite database using only the shaded JAR.

### Changed

- `ItemValueScorer` operates on the immutable `ItemDescriptor` instead of a Bukkit
  `ItemStack`, so scoring is pure and testable
- Bukkit-to-domain mapping is isolated in `integration/bukkit/BukkitItems`
- `/worldecho reload` only reloads configuration and messages; persistence and providers
  keep running

### Added (0.2.0-SNAPSHOT)

- Content bindings: server owners can map provider-specific content IDs to WorldEcho
  semantic roles, capabilities, faction, rank, superior, and tags via `bindings.yml`
- `BindingLoader` validates untrusted YAML, producing diagnostics for invalid entries while
  loading valid ones; a fatal error (missing/unsupported schema version) falls back to an
  empty registry
- `BindingEnricher` merges configured roles and capabilities with provider-supplied ones
  without mutating the original `IdentifiedContent`
- `BindingReloadCoordinator` extracts reload decision logic (fatal on reload keeps
  previous registry; fatal on startup falls back to empty) into testable pure-Java code
- `ContentKey.parse(String)` splits `provider:contentId` strings with validation
- `/worldecho status` now shows entity/item binding counts, warnings, errors, and schema
  version
- `/worldecho inspect item|entity` now displays binding metadata (faction, rank, superior,
  tags) when a binding exists
- `/worldecho reload` now reloads `bindings.yml` in addition to `config.yml` and messages
- `bindings.yml` shipped as a default resource with example entity and item bindings
- Tests: `BindingLoaderTest` (24 cases), `BindingRegistryTest` (9 cases),
  `BindingEnricherTest` (9 cases), `BindingReloadCoordinatorTest` (8 cases),
  and extended `BundledResourcesTest`

### Known limitations

- Validated on Paper 26.2 build 87 with Temurin 25 (enable/disable, commands, a real
  zombie kill captured from a headless bot, persistence across restart, Turkish locale and
  invalid-config fallback). Still unverified: in-game `/worldecho inspect` output,
  `record-without-valuable-item: false` with a high minimum score, and a Spark profile.
  See `docs/TESTING.md`.
- External content bridges (MythicMobs, Oraxen, ItemsAdder, Citizens, ModelEngine) are not
  implemented; only the vanilla fallback providers exist.
- The player-death listener only records a memory; it does not transfer ownership.
- Scenario definitions are not yet loaded from YAML.
- Item snapshots are intentionally minimal and must not be treated as a complete item
  serialization format.
- At most 64 drops per death are inspected, so an unusually large inventory may not have
  every item considered.
- Changing `persistence.*` requires a restart; `/worldecho reload` does not rebuild storage.

### Added (0.2.1-SNAPSHOT — Sprint 0.2B)

- Scenario eligibility diagnostics: evaluates bound or enriched content against named
  requirement profiles and produces structured, deterministic diagnostics
- Built-in profiles: `item-carrier`, `promotion-candidate`, `combat-story-actor`
  (entity), `transferable-story-item` (item)
- `EligibilityCatalog`: immutable profile catalog with case-insensitive lookup
  (`Locale.ROOT`), deterministic ordering, and duplicate ID rejection
- `EligibilityEvaluator`: pure-Java evaluator supporting both `ContentBinding` and
  `EnrichedContent`; provider-supplied roles and capabilities count when evaluating
  enriched content
- `EligibilityResult`: immutable result with matched/missing roles, capabilities, metadata,
  tags, and structured diagnostics with stable codes
- `EligibilityFormatter`: pure-Java formatter for console-friendly command output
- Diagnostic codes: `ELIGIBLE`, `NO_BINDING`, `PROFILE_NOT_FOUND`, `WRONG_BINDING_TYPE`,
  `MISSING_ROLE`, `MISSING_CAPABILITY`, `MISSING_FACTION`, `MISSING_RANK`,
  `MISSING_SUPERIOR`, `MISSING_TAG`
- `/worldecho eligibility profiles|check|all` console-compatible commands with tab
  completion for profile IDs, targets, and bound content keys
- `/worldecho status` now shows eligibility profile counts
- `/worldecho inspect` now shows concise eligibility summary per profile
- English and Turkish messages for all eligibility commands
- Tests: `EligibilityCatalogTest` (9 cases), `EligibilityEvaluatorTest` (22 cases),
  `EligibilityFormatterTest` (8 cases), extended `BundledResourcesTest`
- Turkish locale regression tests for profile ID and capability normalization

### Added (0.3.0-SNAPSHOT — Sprint 0.3A)

- Persistent item identity via Paper Persistent Data Container (`worldecho:item_id` key)
- Append-only ownership ledger with immutable entries, sequence numbers, and transition
  reasons
- Ownership subjects: PLAYER, ENTITY, CONTAINER, WORLD_DROP, SYSTEM, UNKNOWN with stable
  identifiers and validation
- Idempotency keys to prevent duplicate ledger entries on retries
- `OwnershipTransitionService`: pure-Java service with Clock injection, validation,
  idempotency, and no-change short-circuit
- SQLite schema migration v2: `tracked_items` and `item_ownership_ledger` tables with
  indexes and foreign key constraints
- `TrackedItemRepository` and `OwnershipLedgerRepository` interfaces with SQLite
  implementations
- `ItemIdentityAdapter`: PDC read/write/assign for ItemStack identity
- `/worldecho item track|inspect|owner|history|assign-owner` commands with permissions
  and tab completion
- `/worldecho status` now shows tracked-items and ledger-entries counts
- Reconciliation: if a PDC identity exists but the persistence record is missing,
  `/worldecho item track` creates the record without generating a new ID
- Configuration: `items.identity.enabled`, `items.history.default-limit`,
  `items.history.maximum-limit`
- Permissions: `worldecho.item.inspect`, `worldecho.item.track`,
  `worldecho.item.history`, `worldecho.item.assign`
- English and Turkish messages for all item/ownership commands
- Tests: `TrackedItemIdTest` (9), `OwnershipSubjectTest` (16),
  `OwnershipTransitionServiceTest` (11), `SqliteTrackedItemRepositoryTest` (6),
  `SqliteOwnershipLedgerRepositoryTest` (14), extended `SchemaMigratorTest`,
  extended `BundledResourcesTest`

### Added (0.3.1-SNAPSHOT — Sprint 0.3B)

- Automatic item identity classification: `UNIQUE` (one-of-a-kind) vs `LOT` (stackable)
  via pure-Java `ItemIdentityPolicy` with confidence scores and explainable reasons
- `LotCompatibilityFingerprint`: deterministic lot grouping by material, damage,
  enchantments, and provider — normalized and case-insensitive
- Automatic PDC identity assignment for UNIQUE items on first inventory observation
- Lot persistence: `TrackedItemLot`, `LotLineageEntry` (splits/merges),
  `LotOwnershipLedgerEntry`, `LotOwnershipState` with full SQLite repositories
- `AutomaticItemIdentityService`: coordinates identity assignment and ownership
  synchronization for both UNIQUE and LOT items
- `PlayerInventoryReconciler`: captures immutable inventory snapshots on the main
  thread and processes them asynchronously
- `PlayerInventoryReconciliationScheduler`: coalesces multiple inventory events per
  player into a single next-tick reconciliation — no every-tick scanner
- Event listeners: join, respawn, inventory click, inventory drag, pickup, death,
  drop, world change, crafting, furnace extract, offhand swap, fishing. Creative
  inventory actions and merchant trades are covered by InventoryClickEvent. Hotbar
  number-key swaps are covered by InventoryClickEvent with HOTBAR_SWAP action.
- Transformation identity continuity: anvil, smithing table, grindstone preserve
  source item's WorldEcho UUID on the result item via `ItemTransformationListener`
- `ItemIdentityAdapter.writeIdentity` for copying existing IDs to result items
- `ReconciliationCycle`: deterministic cycle IDs with idempotency keys per item/lot
- `ReconciliationMetrics`: thread-safe counters for status reporting
- `DuplicateObservationRegistry`: bounded in-memory duplicate UNIQUE detection
- SQLite schema migration v3: `tracked_item_lots`, `lot_lineage`,
  `item_lot_ownership_ledger` tables with indexes
- `/worldecho item reconcile [player]` command with `worldecho.item.reconcile` permission
- `/worldecho item policy` command with `worldecho.item.policy` permission
- `/worldecho status` now shows automatic tracking metrics
- Configuration: `items.automatic-tracking.*` section with enable/disable and
  per-event-type reconcile toggles
- English and Turkish messages for all new commands
- Tests: `ItemIdentityPolicyTest` (14), `LotCompatibilityFingerprintTest` (7),
  `ReconciliationCycleTest` (4), `ReconciliationMetricsTest` (4),
  `DuplicateObservationRegistryTest` (5), `TrackedItemLotRepositoryTest` (8),
  `AutomaticItemIdentityServiceTest` (10), `LotSplitMergeTest` (9),
  extended `SchemaMigratorTest` (+1 v3 migration test),
  extended `BundledResourcesTest` (+18 new message key checks)

- `/worldecho item track` redefined as diagnostic/repair tool with diagnostic message
- `/worldecho item policy` now shows lot fingerprint and uses new `item-policy-*` message keys
- `DuplicateObservationRegistry` wired into `PlayerInventoryReconciler` for live duplicate detection
- Reload schedules reconciliation for online players when automatic tracking changes from disabled to enabled
- `PaperMessageService.raw()` for unformatted message lookup
- 18 new localization keys (en + tr): policy output, classification, malformed identity,
  duplicate identity, persistence failure, automatic tracking disabled, console usage,
  manual track diagnostic wording
- `duplicate-identity-observations` metric in `/worldecho status`

### Known limitations (0.3.1-SNAPSHOT)

- Transformation identity continuity covers anvil, smithing table, and grindstone;
  crafting table and stonecutter produce items with new identities
- Duplicate observation detection is in-memory only and per-session
- LOT items do not receive PDC metadata; lot identity is owner-scoped (stable owner ID + fingerprint).
  Physical split/merge lineage is not tracked by automatic tracking.
- Lot amount is the sum of all observed stacks with the same fingerprint for that owner in a
  single reconciliation cycle. Existing lots whose fingerprint is absent from the snapshot are
  zeroed. Concurrent inventory modifications may produce stale amounts until the next cycle.
- Headless Paper 26.2 verification (schema migration, status, reload, shutdown) not performed

### Gap fix 2 (0.3.1-SNAPSHOT)

- LOT identity model clarified to owner-scoped aggregate commodity (Model A):
  `findByFingerprintAndOwner` replaces global `findByFingerprint` in automatic tracking.
  Different players with the same fingerprint get separate lots. No PDC on LOT items.
- `ReconciliationSchedulerState` extracted as pure-Java component: coalescing, shutdown
  rejection, pending-state cleanup now unit-testable without Bukkit
- `TransformationDecision` extracted as pure-Java component: inventory classification,
  capture decisions, write decisions, feature gate now unit-testable without Bukkit
- `ReconciliationPlanGenerator` extracted as pure-Java component: plan generation
  from snapshots now unit-testable without Bukkit
- `SlotSnapshotComparator` extracted as pure-Java component: snapshot diff/comparison
  now unit-testable without Bukkit
- `ItemTransformationListener` refactored to delegate decisions to `TransformationDecision`
- `PlayerInventoryReconciliationScheduler` refactored to delegate state to `ReconciliationSchedulerState`
- Duplicate ID detection moved BEFORE ownership transition to prevent ping-pong
- New composite index `idx_tracked_lots_fingerprint_owner` for owner-scoped lot lookup
- New tests: `ReconciliationSchedulerStateTest` (9), `TransformationDecisionTest` (22),
  `ReconciliationPlanGeneratorTest` (9), `SlotSnapshotComparatorTest` (10),
  `AutomaticItemIdentityServiceTest` +11 (owner-scoped, restart-safe, no ping-pong, join plan, quantity handling),
  `LotSplitMergeTest` +4 (owner-scoped lookup tests)

### Gap fix 3 (0.3.1-SNAPSHOT)

- **Stable owner-scoped lot aggregation**: LOT amounts are now aggregated per owner+fingerprint
  in a single reconciliation cycle. Multiple stacks of the same fingerprint are summed into one
  update, replacing the previous per-slot latest-stack-size behavior.
- Schema migration v4: adds `owner_type`, `owner_stable_id`, `owner_display_snapshot` columns
  to `tracked_item_lots`. Backfills from `created_by_subject` for existing rows. Adds indexes
  `idx_tracked_lots_owner_fp` and `idx_tracked_lots_owner_fp_unique` on
  `(owner_type, owner_stable_id, fingerprint)`.
- `TrackedItemLot` record extended with `ownerType`, `ownerStableId`, `ownerDisplaySnapshot`
  fields (normalized: type/stableId lowercased, display snapshot stripped).
- `TrackedItemLotRepository` interface: new `findByOwnerAndFingerprint`, `findAllByOwner`,
  `updateOwnerDisplaySnapshot` methods for stable owner-scoped lookup.
- `SqliteTrackedItemLotRepository`: INSERT includes new columns; `mapRow` reads them with
  backward-compatible fallback; new query methods implemented.
- `AutomaticItemIdentityService.processLotSlots(ObservedInventorySnapshot)`: replaces
  per-slot `processLotSlot` with snapshot-based aggregation. Groups LOT slots by fingerprint,
  sums amounts, performs one update per owner+fingerprint per cycle.
- **Zeroing logic**: existing lots whose fingerprint is absent from the current snapshot are
  set to `amount=0`, reflecting that the player no longer holds those items.
- `PlayerInventoryReconciler`: UNIQUE items processed individually in the plan loop; LOT items
  processed as a single aggregated batch via `processLotSlots(snapshot)`.
- `AutomaticItemIdentityServiceTest` rewritten: 25 tests covering aggregation, zeroing,
  idempotency, split/merge preservation, acquisition/consumption, display name independence,
  restart safety, malformed isolation, negative amount guard.
- `SchemaMigratorTest`: updated for v4 migration assertions (new indexes, schema version 4).

### Gap fix 4 (0.3.1-SNAPSHOT)

- **UNIQUE database invariant restored**: `idx_tracked_lots_owner_fp_unique` is now a
  `UNIQUE INDEX` on `(owner_type, owner_stable_id, fingerprint)`, enforced at the database
  level. `findByOwnerAndFingerprint` never returns ambiguous duplicates.
- **Migration v4 backfill**: correctly parses `player:UUID` from `created_by_subject` using
  `substr(created_by_subject, 8)`. Non-`player:` subjects fall back to empty owner fields.
  Duplicate legacy rows for the same `(owner_type, owner_stable_id, fingerprint)` are
  deterministically resolved before UNIQUE index creation: the row with the latest
  `last_seen_at` (then `created_at`) is kept; duplicates are deleted.
- **Atomic `reconcileOwnerAggregates`**: new repository operation that performs all LOT
  snapshot updates for a single owner in one transaction — upserts observed fingerprints,
  zeros absent ones, updates display snapshot. Rolls back on any SQL failure.
- **`AutomaticItemIdentityService` refactored**: `processLotSlots` now calls
  `reconcileOwnerAggregates` instead of individual find/create/update/zero calls. Zeroing
  failures are no longer silently swallowed — they return structured `PERSISTENCE_FAILURE`
  results with error messages.
- **`LotSplitMergeTest` updated**: split/merge fixtures use different owners for parent and
  child lots to comply with the UNIQUE constraint.
- New tests: `MigrationV4BackfillTest` (11 tests), `ReconcileOwnerAggregatesTest` (9 tests).
- Total: 383 tests, 0 failures, 0 skipped.
