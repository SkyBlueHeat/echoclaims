# Inventory Snapshots

## Overview

An **InventorySnapshot** is an immutable domain record capturing a player's full
inventory state at a specific moment. Each snapshot contains all inventory items,
armor, offhand item, and cursor item, along with player vitals (health, food, XP)
and contextual data (world, coordinates, game mode).

## Domain Record

```java
public record InventorySnapshot(
    UUID id,
    UUID playerUuid,
    CaptureReason captureReason,   // PRE_DEATH or POST_RESPAWN
    long capturedAt,               // epoch millis
    String worldId,                // lowercase world name
    Coordinates coordinates,       // nullable
    String gameMode,               // e.g. "SURVIVAL"
    double health,
    int foodLevel,
    int experienceLevel,
    int totalExperience,
    List<SnapshotItem> inventoryItems,
    List<SnapshotItem> armorItems,
    SnapshotItem offhandItem,      // nullable
    SnapshotItem cursorItem,       // nullable
    int schemaVersion
) {}
```

## SnapshotItem

Each item in a snapshot is recorded as:

```java
public record SnapshotItem(
    String slot,              // slot index as string (e.g. "0", "36", "100")
    String materialKey,       // e.g. "minecraft:diamond_sword"
    int amount,
    String serializedData,    // Base64-encoded byte serialization (format versioned)
    String contentIdentity,   // provider-neutral identity (e.g. "oraxen:my_item")
    int score,                // computed item value score
    String displayName,       // custom name, empty if none
    int damage,               // current damage value
    int maxDurability,        // max durability
    Map<String, Integer> enchantments  // enchantment name -> level
) {}
```

## Capture Flow

1. **Pre-death capture** (`DeathCaptureService.captureDeath`):
   - Triggered by `PlayerDeathEvent` on the main thread.
   - Reads `PlayerInventory` contents, armor, offhand, and cursor.
   - Serializes each item via `ItemSerializer.serialize(ItemStack)`.
   - Scores each item via `ItemValueScorer`.
   - Identifies provider content via `IntegrationRegistry`.
   - Builds an immutable `InventorySnapshot` with `CaptureReason.PRE_DEATH`.
   - Submits to `EvidencePersistenceService` for async atomic persistence.

2. **Post-respawn capture** (`DeathCaptureService.capturePostRespawn`):
   - Triggered by `PlayerRespawnEvent` on the main thread.
   - Captures the player's inventory after respawn.
   - Builds an `InventorySnapshot` with `CaptureReason.POST_RESPAWN`.
   - Links to the existing incident via `EvidenceStore.insertPostRespawnSnapshot()`.

## Item Serialization

Items are serialized using `BukkitItemSerializer`, which uses Paper's
`ItemStack.serializeAsBytes()` and `ItemStack.deserializeBytes()` APIs.

**Format**: `[4-byte version header][N-byte ItemStack byte data]`, Base64-encoded.

- Format version: `1` (owned by EchoClaims, not Paper)
- Default max payload: `65,536` bytes (configurable via `evidence.max-item-payload-bytes`)
- Null and air items serialize to empty string
- Unsupported format versions are rejected on deserialization

## Snapshot Fingerprint

The snapshot fingerprint is used in deduplication keys to distinguish deaths with
identical metadata (same player, time, world, coordinates, cause) but different
inventory contents. It is computed by `DeduplicationKeyFactory.snapshotFingerprint()`:

- Concatenates each item's material key, amount, slot, and score
- Hashes the concatenation with SHA-256
- Returns the hex-encoded hash

## Database Schema

```sql
CREATE TABLE inventory_snapshots (
    id               TEXT PRIMARY KEY,
    player_uuid      TEXT NOT NULL,
    capture_reason   TEXT NOT NULL,
    captured_at      INTEGER NOT NULL,
    world_id         TEXT NOT NULL,
    x                INTEGER,
    y                INTEGER,
    z                INTEGER,
    game_mode        TEXT NOT NULL,
    health           REAL NOT NULL,
    food_level       INTEGER NOT NULL,
    experience_level INTEGER NOT NULL,
    total_experience INTEGER NOT NULL,
    schema_version   INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE snapshot_items (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    snapshot_uuid   TEXT NOT NULL,
    slot            TEXT NOT NULL,
    slot_type       TEXT NOT NULL,   -- INVENTORY, ARMOR, OFFHAND, CURSOR
    material_key    TEXT NOT NULL,
    amount          INTEGER NOT NULL,
    serialized_data TEXT NOT NULL DEFAULT '',
    content_identity TEXT NOT NULL DEFAULT '',
    score           INTEGER NOT NULL,
    display_name    TEXT NOT NULL DEFAULT '',
    damage          INTEGER NOT NULL DEFAULT 0,
    max_durability  INTEGER NOT NULL DEFAULT 0,
    enchantments    TEXT NOT NULL DEFAULT '',
    FOREIGN KEY (snapshot_uuid) REFERENCES inventory_snapshots(id),
    UNIQUE (snapshot_uuid, slot)
);
```

## Indexes

- `idx_snapshots_player_time` on `(player_uuid, captured_at DESC)`
- `idx_snapshot_items_snapshot` on `(snapshot_uuid)`

## Atomicity Guarantee

Snapshot and snapshot items are always inserted in a single SQLite transaction.
When combined with an incident (via `EvidenceStore.insertDeathEvidence`), the
snapshot, all items, and the incident are inserted atomically. If any statement
fails, the entire transaction rolls back — no orphan snapshots or incidents.
