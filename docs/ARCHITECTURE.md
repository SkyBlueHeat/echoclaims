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
│   └── StatusService     # Status report aggregation
├── config/               # Configuration loading and validation
│   ├── ConfigurationSource
│   ├── MapConfigurationSource
│   ├── EchoClaimsSettings
│   ├── SettingsLoader
│   ├── SettingsLoadResult
│   └── MessageCatalog
├── domain/               # Pure domain model (no Bukkit imports)
│   ├── audit/            # AuditRecord
│   ├── claim/            # (empty, reserved for MVP)
│   ├── content/          # ContentKey, IdentifiedContent, Capability, SemanticRole
│   ├── incident/         # (empty, reserved for MVP)
│   ├── item/             # ItemDescriptor, ItemScore, ItemScoreWeights
│   ├── refund/           # (empty, reserved for MVP)
│   └── snapshot/         # (empty, reserved for MVP)
├── integration/          # Provider-neutral integration layer
│   ├── ContentProvider
│   ├── EntityContentProvider
│   ├── ItemContentProvider
│   ├── IntegrationRegistry
│   ├── ProviderRegistry
│   ├── ProviderHealth
│   ├── ProviderStatus
│   ├── bukkit/           # Bukkit adapters
│   │   └── BukkitItems
│   └── vanilla/          # Vanilla fallback providers
│       ├── VanillaEntityProvider
│       └── VanillaItemProvider
├── paper/                # Paper plugin layer
│   ├── EchoClaimsPlugin  # Main plugin class
│   ├── command/
│   │   └── EchoClaimsCommand
│   ├── config/
│   │   └── BukkitConfigurationSource
│   └── message/
│       └── PaperMessageService
└── persistence/          # SQLite persistence
    ├── AuditRecordRepository
    ├── SqliteAuditRecordRepository
    ├── AuditWriteQueue
    ├── DatabaseManager
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

## Database Schema (v1)

- `schema_version` — migration tracking
- `audit_records` — immutable audit log with category, source, details, and timestamp

## Planned Domain Areas

The following packages are intentionally empty and will be populated in the
first MVP sprint:

- `domain/incident` — discrete events that may produce claims
- `domain/snapshot` — player inventory state snapshots
- `domain/claim` — player claim requests
- `domain/refund` — item restoration records
