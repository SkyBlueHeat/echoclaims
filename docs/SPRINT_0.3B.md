# Sprint 0.3B — Automatic Item Tracking

## Objective

Automatically assign `UNIQUE` or `LOT` identities to items entering active player inventories, reconcile ownership, and preserve Minecraft stacking behavior — without requiring players to manually track items.

## Summary

This sprint introduces a complete automatic item identity and ownership synchronization system:

- **Identity classification**: Items are classified as `UNIQUE` (one-of-a-kind, tracked individually) or `LOT` (stackable, tracked as a lot) using a pure-Java policy engine.
- **Automatic PDC assignment**: UNIQUE items get a WorldEcho UUID written to their Persistent Data Container on first observation.
- **Owner-scoped lot aggregation (Model A)**: LOT items are grouped per-player by a deterministic `LotCompatibilityFingerprint` (material, damage, enchantments, provider). Each player gets their own lot aggregate for the same fingerprint. No PDC metadata is written to LOT items. Lot amount is the **sum of all observed stacks** with the same fingerprint for that owner in a single reconciliation cycle — one update per owner+fingerprint per cycle. Existing lots whose fingerprint is absent from the snapshot are zeroed.
- **Ownership reconciliation**: Every reconciliation cycle records ownership transitions idempotently, using deterministic cycle-scoped keys.
- **Coalescing scheduler**: Multiple inventory events in the same tick coalesce into a single next-tick reconciliation — no every-tick scanner, no unbounded task creation.
- **Event-driven**: Join, respawn, inventory click/drag, pickup, death, drop, world change, crafting, furnace extract, offhand swap, and fishing events trigger reconciliation. Creative inventory actions and merchant trades are covered by InventoryClickEvent. Hotbar number-key swaps are covered by InventoryClickEvent with HOTBAR_SWAP action.
- **Transformation identity continuity**: Anvil, smithing table, and grindstone transformations preserve the source item's WorldEcho UUID on the result item.
- **Silent operation**: No chat spam for normal players. Metrics are visible via `/worldecho status`.
- **Admin commands**: `/worldecho item reconcile [player]` and `/worldecho item policy` for manual control and diagnostics.

## New domain types

| Type | Package | Purpose |
|------|---------|---------|
| `IdentityMode` | `domain.item` | Enum: `UNIQUE`, `LOT` |
| `ObservedItemDescriptor` | `domain.item` | Immutable snapshot for classification |
| `IdentityClassificationResult` | `domain.item` | Classification result with confidence and reasons |
| `ItemIdentityPolicy` | `domain.item` | Pure-Java classification logic |
| `LotCompatibilityFingerprint` | `domain.item` | Deterministic lot compatibility key |
| `TrackedItemLotId` | `domain.item` | UUID-based lot identifier |
| `TrackedItemLot` | `domain.item` | Persistent lot record |
| `LotRelationType` | `domain.item` | Enum: `SPLIT_FROM`, `MERGED_INTO` (persistence schema only; not populated by automatic tracking) |
| `LotLineageEntry` | `domain.item` | Lineage record for lot splits/merges (persistence schema only; not populated by automatic tracking) |
| `LotOwnershipLedgerEntry` | `domain.item` | Append-only lot ownership ledger entry |
| `LotOwnershipState` | `domain.item` | Current ownership projection for a lot |
| `LotOwnershipTransitionService` | `domain.item` | Validates and records lot ownership transitions |
| `AutomaticItemIdentityService` | `domain.item` | Coordinates identity assignment + persistence |
| `ReconciliationCycle` | `domain.item` | Stable identifier for a single reconciliation cycle |
| `ObservedInventorySlot` | `domain.item` | Immutable inventory slot snapshot |
| `ObservedInventorySnapshot` | `domain.item` | Immutable full inventory snapshot |
| `SlotProcessResult` | `domain.item` | Result of processing a single slot |
| `ReconciliationMetrics` | `domain.item` | Thread-safe metrics counters |
| `DuplicateObservationRegistry` | `domain.item` | Bounded in-memory duplicate detection |
| `ReconciliationSchedulerState` | `domain.item` | Pure-Java pending-player coalescing state |
| `TransformationDecision` | `domain.item` | Pure-Java transformation identity continuity decision logic |
| `ReconciliationPlanGenerator` | `domain.item` | Pure-Java reconciliation plan generation from snapshots |
| `SlotSnapshotComparator` | `domain.item` | Pure-Java snapshot diff/comparison logic |

## New persistence types

| Type | Purpose |
|------|---------|
| `TrackedItemLotRepository` | Interface for lot CRUD + lineage |
| `SqliteTrackedItemLotRepository` | SQLite implementation |
| `LotOwnershipLedgerRepository` | Interface for lot ownership ledger |
| `SqliteLotOwnershipLedgerRepository` | SQLite implementation |

## Schema migration v3

Adds three new tables with indexes:

- `tracked_item_lots` — lot records with fingerprint, amounts, tracking metadata
- `lot_lineage` — split/merge lineage with idempotency keys
- `item_lot_ownership_ledger` — append-only lot ownership transitions with sequence numbers

Indexes: `idx_tracked_lots_fingerprint`, `idx_tracked_lots_fingerprint_owner`, `idx_tracked_lots_content_key`, `idx_lot_lineage_lot_id`, `idx_lot_lineage_related_lot`, `idx_lot_ledger_lot_seq_desc`, `idx_lot_ledger_new_subject`

## Schema migration v4

Adds stable owner scope columns to `tracked_item_lots`:

- `owner_type` — normalized owner type token (e.g. `player`)
- `owner_stable_id` — stable owner identifier (UUID for players)
- `owner_display_snapshot` — display name at last observation

Backfills owner data from existing `created_by_subject` column using `substr(created_by_subject, 8)` to extract the UUID from the `player:UUID` format. Non-`player:` subjects are left with empty owner fields (documented fallback). Before creating the UNIQUE index, duplicate legacy rows for the same `(owner_type, owner_stable_id, fingerprint)` are deterministically resolved: the row with the latest `last_seen_at` (then `created_at`) is kept as canonical; duplicates are deleted.

**Database invariant**: `UNIQUE(owner_type, owner_stable_id, fingerprint)` — enforced at the database level via `idx_tracked_lots_owner_fp_unique`. This guarantees that `findByOwnerAndFingerprint` never returns ambiguous duplicates. The aggregation service relies on this invariant; no two rows with the same owner+fingerprint can coexist.

Adds indexes `idx_tracked_lots_owner_fp` (non-unique, for range scans) and `idx_tracked_lots_owner_fp_unique` (UNIQUE, for invariant enforcement) on `(owner_type, owner_stable_id, fingerprint)`.

## New Paper-layer types

| Type | Purpose |
|------|---------|
| `PlayerInventoryReconciler` | Captures immutable snapshots on main thread, processes asynchronously |
| `PlayerInventoryReconciliationScheduler` | Coalesces events per-player into next-tick tasks |
| `PlayerInventoryObservationListener` | Listens for join, respawn, click, drag, pickup, death, drop, world change, crafting, furnace extract, offhand swap, fishing |
| `ItemTransformationListener` | Preserves UNIQUE identity across anvil, smithing, grindstone transformations (delegates decisions to `TransformationDecision`) |

## Configuration

New `items.automatic-tracking` section in `config.yml`:

```yaml
items:
  automatic-tracking:
    enabled: true
    player-inventories: true
    reconcile-on-join: true
    reconcile-on-respawn: true
    reconcile-after-inventory-events: true
    transform-identity-continuity: true
    debug-messages: false
```

## Product statement

WorldEcho automatically identifies and records items entering active player inventories. Normal players and server administrators are not expected to manually track items.

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/worldecho item reconcile [player]` | `worldecho.item.reconcile` | Triggers manual inventory reconciliation |
| `/worldecho item policy` | `worldecho.item.policy` | Shows identity classification for held item (read-only) |

### Redefined commands

`/worldecho item track` is redefined as a **diagnostic/repair tool**. It forces immediate held-item reconciliation, preserves existing identity, and clearly identifies itself as manual repair/diagnostic functionality. It is not required during normal gameplay.

`/worldecho item assign-owner` remains a ledger-only administrative repair tool.

## Status metrics

`/worldecho status` now shows:
- `auto-tracking`: enabled/disabled
- `recon.count`: total reconciliations
- `recon.identities`: UNIQUE identities assigned
- `recon.lots`: LOT identities assigned
- `recon.ownership`: ownership transitions recorded
- `recon.warnings`: identity warnings
- `recon.duplicates`: duplicate identity observations
- `recon.pending`: pending reconciliations

## Threading rules

- Bukkit API calls (inventory reads, PDC writes) happen on the main thread only
- Database persistence happens on the single-threaded query executor
- Immutable snapshots are captured on the main thread and processed asynchronously
- No SQLite reads or writes on the main server thread

## Reload behavior

`/worldecho reload` reloads automatic tracking settings. When automatic tracking changes from disabled to enabled, it schedules reconciliation for all online players. It does not synchronously scan inventories, replace identities, or clear ownership history.

## Shutdown behavior

On shutdown, the scheduler rejects new reconciliation requests, cancels pending tasks, and reports concise reconciliation metrics. PDC identities already written are preserved.

## Tests

- Test count: 383 tests, 0 failures, 0 skipped
- New tests: `ItemIdentityPolicyTest` (14), `LotCompatibilityFingerprintTest` (7), `ReconciliationCycleTest` (4), `ReconciliationMetricsTest` (4), `DuplicateObservationRegistryTest` (5), `ReconciliationSchedulerStateTest` (9), `TransformationDecisionTest` (22), `ReconciliationPlanGeneratorTest` (9), `SlotSnapshotComparatorTest` (10), `TrackedItemLotRepositoryTest` (8), `AutomaticItemIdentityServiceTest` (25), `LotSplitMergeTest` (13), `SchemaMigratorTest` (6), `MigrationV4BackfillTest` (11), `ReconcileOwnerAggregatesTest` (9), `BundledResourcesTest` +18 new message key checks

## Known limitations

- Duplicate observation detection is in-memory only and per-session; it does not persist across restarts
- LOT items do not receive PDC metadata; lot identity is owner-scoped and resolved by stable owner ID (UUID) + fingerprint at reconciliation time. The database enforces `UNIQUE(owner_type, owner_stable_id, fingerprint)` — one lot per owner per fingerprint. Physical split/merge lineage (`lot_lineage` table) is a schema-only feature; it is not populated by automatic tracking.
- Lot amount is the sum of all observed stacks with the same fingerprint for that owner in a single reconciliation cycle. Reconciliation is atomic: upsert, zeroing, and display snapshot update happen in a single transaction via `reconcileOwnerAggregates`. If any SQL operation fails, the entire transaction rolls back and the service returns structured `PERSISTENCE_FAILURE` results (no silent exception swallowing). Concurrent inventory modifications may produce stale amounts until the next reconciliation cycle.
- Transformation identity continuity covers anvil, smithing table, and grindstone; other crafting mechanics (crafting table, stonecutter) produce items with new identities
- Headless Paper 26.2 verification (schema migration, status, reload, shutdown) not performed — requires a running Paper server
