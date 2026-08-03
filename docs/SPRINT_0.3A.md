# Sprint 0.3A — Item Identity and Ownership Ledger

> **Prerequisite for Sprint 0.3B.** Sprint 0.3B (Automatic Player Inventory Identity &
> Ownership Synchronization) builds on this sprint's PDC identity, ownership ledger, and
> manual tracking commands. See `docs/SPRINT_0.3B.md`.

## Summary

Sprint 0.3A introduces a persistent, stable identity for physical Minecraft items using
Paper's Persistent Data Container (PDC) and an append-only ownership ledger that records
every ownership transition with an immutable, explainable history.

## What was implemented

### Domain model (`dev.worldecho.domain.item`)

- **`TrackedItemId`** — immutable UUID-based identifier, stored as lowercase canonical string.
- **`OwnershipSubjectType`** — enum: `PLAYER`, `ENTITY`, `CONTAINER`, `WORLD_DROP`, `SYSTEM`, `UNKNOWN`.
- **`OwnershipSubject`** — immutable record combining type, stable ID, and optional display name.
  Factory methods for `player`, `entity`, `container`, `worldDrop`, `system`, `unknown`.
  `isValid()` validates the stable ID format per type.
- **`OwnershipTransitionReason`** — stable vocabulary enum: `TRACKED`, `ADMIN_ASSIGNMENT`,
  `PLAYER_HELD`, `ENTITY_HELD`, `DROPPED`, `PICKED_UP`, `STORED`, `RETRIEVED`,
  `TRANSFERRED`, `RECOVERED`, `UNKNOWN`.
- **`TrackedItemRecord`** — immutable persistent record with initial snapshot fields
  (created_at, first_seen_at, content_key, provider_id, initial_material,
  initial_custom_name, initial_value_score, tracking_reason, created_by_subject).
  Only `last_seen_at` is updatable via `withLastSeenAt()`.
- **`OwnershipLedgerEntry`** — immutable append-only entry with entry_id, sequence_number,
  previous/new subject, transition_reason, occurred_at, recorded_at, source,
  story_event_id, idempotency_key, notes.
- **`OwnershipState`** — immutable projection of current ownership derived from the latest
  ledger entry.
- **`OwnershipResult`** / **`OwnershipResultStatus`** — result of transition attempts with
  status: `RECORDED`, `IDEMPOTENT_REPLAY`, `NO_CHANGE`, `ITEM_NOT_TRACKED`,
  `INVALID_SUBJECT`, `CONFLICT`, `PERSISTENCE_FAILURE`.
- **`OwnershipTransitionService`** — pure-Java service with `Clock` injection, idempotency
  key handling, subject validation, and no-change short-circuit. Persistence errors are
  caught and converted to `PERSISTENCE_FAILURE` results.

### Persistence

- **Schema migration v2** — creates `tracked_items` and `item_ownership_ledger` tables with
  indexes (`idx_tracked_items_content_key`, `idx_ledger_item_seq_desc`,
  `idx_ledger_new_subject`, `idx_ledger_occurred_at`) and constraints
  (`UNIQUE(item_id, sequence_number)`, `UNIQUE(item_id, idempotency_key)`,
  `FOREIGN KEY(item_id) REFERENCES tracked_items(item_id)`).
- **`TrackedItemRepository`** / **`SqliteTrackedItemRepository`** — create, findById, exists,
  observe (update last_seen_at), count.
- **`OwnershipLedgerRepository`** / **`SqliteOwnershipLedgerRepository`** — append (with
  idempotency replay and conflict detection), findCurrentOwnership, findHistory,
  countHistory, findByIdempotencyKey, count.

### Paper integration

- **`ItemIdentityAdapter`** — reads and writes `worldecho:item_id` PDC key on ItemStacks.
  Methods: `readIdentity`, `ensureIdentity`, `assignNewIdentity`. Returns `IdentityResult`
  with status: `EXISTING`, `ASSIGNED`, `MISSING`, `MALFORMED`, `UNSUPPORTED_ITEM`.
- **`ItemCommandHandler`** — handles `/worldecho item` subcommands:
  - `track` — assigns a WorldEcho ID to the held item and creates a persistence record +
    initial ownership entry. Reconciles if PDC identity exists but persistence record is
    missing.
  - `inspect` — shows tracked status, item ID, content key, material, current owner,
    ownership sequence, and history count.
  - `owner <item-id>` — shows current ownership state for a tracked item.
  - `history <item-id> [count]` — shows ownership history entries in descending sequence
    order, respecting configured default and maximum limits.
  - `assign-owner <item-id> player|entity|system <id>` — administratively assigns
    ownership without moving the physical item.
- **Status command** — now includes `tracked-items` and `ledger-entries` counts.
- **Tab completion** — supports `item` subcommands and `assign-owner` subject types.

### Existing inspect integration

`/worldecho inspect item` now shows:
- `worldecho.item-id` — the tracked-item ID or `untracked`
- `worldecho.current-owner` — current ownership subject description
- `worldecho.history-count` — number of ledger entries
- A `persistence-missing` warning when PDC identity exists but the database record is absent

Read-only inspection never assigns an ID.

### Eligibility integration

`/worldecho item inspect` displays `transferable-story-item: eligible/not eligible` when
the held item has a content binding. Tracking and eligibility remain separate concepts.

### Death-memory integration

The death-memory system reads an existing tracked item ID from the selected loot drop
when present. The ID is stored in the death snapshot as `trackedItemId`. No new IDs are
assigned during death capture. Existing death-event semantics are unchanged.

### Configuration

New keys in `config.yml`:

```yaml
items:
  identity:
    enabled: true
  history:
    default-limit: 10
    maximum-limit: 50
```

### Permissions

- `worldecho.item.inspect` — inspect tracked-item identity and ownership
- `worldecho.item.track` — track a new item with a WorldEcho identity
- `worldecho.item.history` — view ownership history for a tracked item
- `worldecho.item.assign` — administratively assign ownership of a tracked item

### Localization

All new message keys added to both `messages_en.yml` and `messages_tr.yml` with
MiniMessage formatting and sanitized placeholders.

### Tests (all passing)

- `TrackedItemIdTest` — parse, tryParse, equality, lowercase normalization
- `OwnershipSubjectTest` — factory methods, validation, describe, enum round-trips
- `OwnershipTransitionServiceTest` — recording, idempotency, conflict, no-change,
  not-tracked, invalid subject, current ownership query
- `SqliteTrackedItemRepositoryTest` — create, findById, exists, observe, count, idempotency
- `SqliteOwnershipLedgerRepositoryTest` — append, find current, find history, idempotency
  replay, conflict, count, previous subject persistence
- `SchemaMigratorTest` — v2 tables and indexes verified
- `BundledResourcesTest` — all item message keys present in both locales
- `ItemIdentityAdapterTest` — pure-Java decision logic: untracked, valid, malformed,
  uppercase, blank, partial, stable reads
- `ReconciliationTest` — PDC exists but DB missing, repeated track reconciles,
  preserves original ID, no duplicate ledger entries, snapshot conflict diagnostics
- `ItemOwnershipIntegrationTest` — track→create→sequence 1, restart→owner present,
  append→count increases, descending history, story events survive, different items
  both use sequence 1
- `DeathMemoryFactoryTest` — tracked item ID included in snapshot when present,
  empty when not tracked

## Validation steps

1. `./gradlew clean test shadowJar` — all tests pass, jar builds successfully
2. SQLite smoke test passes
3. Config defaults match shipped config.yml
4. Message key parity between English and Turkish locales

## Known limitations

- No automatic item tracking on pickup/drop/transfer — only manual `/worldecho item track`
  in Sprint 0.3A
- No GUI for ownership history
- No external bridge integration for item identity (MythicMobs, Oraxen, etc.)
- `assign-owner` updates the ledger only; it does not move the physical Minecraft item
- Container and world-drop subject types are defined but not yet populated by listeners
