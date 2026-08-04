# Changelog

All notable changes to EchoClaims will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/).

## [0.1.0-SNAPSHOT] - 2025-08-03

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
