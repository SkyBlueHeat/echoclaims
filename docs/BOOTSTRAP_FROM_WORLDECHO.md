# Bootstrap from WorldEcho

This document records what was reused, removed, renamed, and what remains as
technical debt after extracting EchoClaims from the WorldEcho codebase.

## Context

EchoClaims was bootstrapped from the WorldEcho repository because WorldEcho
contains reusable Paper plugin lifecycle, SQLite persistence, asynchronous
write queue, provider-neutral item identification, and automated test
infrastructure. WorldEcho is a separate project and was not modified during
this extraction.

## Reused Infrastructure

| Component | WorldEcho name | EchoClaims name |
|-----------|---------------|-----------------|
| Gradle build + wrapper | — | — (kept as-is) |
| Paper plugin lifecycle | `WorldEchoPlugin` | `EchoClaimsPlugin` |
| SQLite connection manager | `DatabaseManager` | `DatabaseManager` |
| Schema migrator | `SchemaMigrator` | `SchemaMigrator` |
| Migration record | `Migration` | `Migration` |
| Bounded write queue | `StoryWriteQueue` | `AuditWriteQueue` |
| Provider registry | `ProviderRegistry` | `ProviderRegistry` |
| Integration registry | `IntegrationRegistry` | `IntegrationRegistry` |
| Content provider interfaces | `ContentProvider`, `EntityContentProvider`, `ItemContentProvider` | (same names) |
| Vanilla item provider | `VanillaItemProvider` | `VanillaItemProvider` |
| Vanilla entity provider | `VanillaEntityProvider` | `VanillaEntityProvider` |
| Bukkit item adapter | `BukkitItems` | `BukkitItems` |
| Item descriptor | `ItemDescriptor` | `ItemDescriptor` |
| Item score | `ItemScore` | `ItemScore` |
| Item score weights | `ItemScoreWeights` | `ItemScoreWeights` |
| Item value scorer | `ItemValueScorer` | `ItemValueScorer` |
| Content key | `ContentKey` | `ContentKey` |
| Identified content | `IdentifiedContent` | `IdentifiedContent` |
| Capability enum | `Capability` | `Capability` (trimmed) |
| Semantic role enum | `SemanticRole` | `SemanticRole` (trimmed) |
| Configuration source | `ConfigurationSource` | `ConfigurationSource` |
| Map configuration source | `MapConfigurationSource` | `MapConfigurationSource` |
| Settings loader | `SettingsLoader` | `SettingsLoader` |
| Settings record | `WorldEchoSettings` | `EchoClaimsSettings` |
| Message catalog | `MessageCatalog` | `MessageCatalog` |
| Paper message service | `PaperMessageService` | `PaperMessageService` |
| Bukkit config adapter | `BukkitConfigurationSource` | `BukkitConfigurationSource` |
| Shadow JAR smoke test | `SqliteSmoke` | `SqliteSmoke` (unchanged) |

## Removed WorldEcho-Specific Components

- Story memory event model (`StoryMemoryEvent`, `MemoryEventType`, `Attributes`)
- Story event repository (`StoryEventRepository`, `SqliteStoryEventRepository`)
- Story write queue (replaced by `AuditWriteQueue`)
- Memory recorder (`MemoryRecorder`)
- Death capture (`DeathCapture`, `DeathMemoryFactory`, `LootCandidate`)
- Player death listener (`PlayerDeathMemoryListener`)
- Binding system (`BindingRegistry`, `BindingLoader`, `BindingEnricher`,
  `BindingReloadCoordinator`, `ContentBinding`, `BindingDiagnostic`,
  `BindingType`, `EnrichedContent`, `BindingLoadResult`)
- Eligibility system (`EligibilityCatalog`, `EligibilityEvaluator`,
  `EligibilityFormatter`, `EligibilityProfile`, `EligibilityResult`,
  `EligibilityStatus`, `EligibilityDiagnostic`, `EligibilityDiagnosticCode`,
  `CompatibilityResult`, `ContentRequirements`, `ScenarioCompatibilityService`)
- Tracked item identity (`AutomaticItemIdentityService`, `TrackedItemId`,
  `TrackedItemRecord`, `TrackedItemLot`, `TrackedItemLotId`,
  `LotOwnershipTransitionService`, `OwnershipTransitionService`,
  `OwnershipLedgerRepository`, `LotOwnershipLedgerRepository`,
  `SqliteOwnershipLedgerRepository`, `SqliteLotOwnershipLedgerRepository`,
  `SqliteTrackedItemRepository`, `SqliteTrackedItemLotRepository`,
  `TrackedItemRepository`, `TrackedItemLotRepository`)
- Lot lineage and fingerprinting (`LotLineageEntry`, `LotRelationType`,
  `LotCompatibilityFingerprint`, `LotOwnershipState`, `LotOwnershipLedgerEntry`)
- Reconciliation system (`ReconciliationCycle`, `ReconciliationMetrics`,
  `ReconciliationPlanGenerator`, `ReconciliationSchedulerState`,
  `SlotProcessResult`, `SlotSnapshotComparator`, `PlayerInventoryReconciler`,
  `PlayerInventoryReconciliationScheduler`)
- Inventory observation listener (`PlayerInventoryObservationListener`)
- Item transformation listener (`ItemTransformationListener`)
- Item identity adapter (`ItemIdentityAdapter`)
- Duplicate observation registry
- Observed inventory slots and snapshots
- Ownership subject and transition models
- Transformation decision model
- Identity classification and policy models
- Item command handler (`ItemCommandHandler`)
- WorldEcho command (`WorldEchoCommand`) — replaced by `EchoClaimsCommand`
- bindings.yml resource
- All WorldEcho-specific database migrations (story_events, tracked_items,
  ownership ledgers, lot tables, lineage)
- All WorldEcho-specific documentation (sprint plans, product spec, roadmap,
  concept document, configuration guide, testing guide)

## Renamed Components

| WorldEcho | EchoClaims | Notes |
|-----------|-----------|-------|
| `dev.worldecho` | `io.github.skyblueheat.echoclaims` | Java base package |
| `WorldEchoPlugin` | `EchoClaimsPlugin` | Main plugin class |
| `WorldEchoSettings` | `EchoClaimsSettings` | Settings record |
| `WorldEchoCommand` | `EchoClaimsCommand` | Admin command |
| `StoryWriteQueue` | `AuditWriteQueue` | Write queue |
| `StoryEventRepository` | `AuditRecordRepository` | Repository interface |
| `SqliteStoryEventRepository` | `SqliteAuditRecordRepository` | SQLite repository |
| `StoryMemoryEvent` | `AuditRecord` | Domain record |
| `worldecho.db` | `echoclaims.db` | Database filename |
| `worldecho` command | `echoclaims` (alias `ec`) | Root command |
| `worldecho.admin` | `echoclaims.admin` | Admin permission |

## Database Migration Decision

EchoClaims uses a fresh initial migration (v1: `audit_records` table) and does
not preserve WorldEcho's story-related tables. This is a new product repository
and does not need backward compatibility with WorldEcho databases. The
`schema_version` table structure is reused.

## Runtime Branding Verification

EchoClaims was tested on a clean Paper 26.2 server (build 87) with Java 25
(Eclipse Adoptium Temurin 25.0.4+7 LTS) on Windows 10. The following was
confirmed during runtime validation:

- **No WorldEcho branding** appeared in server logs, command output, or
  generated data files.
- A log search for `WorldEcho|Exception|SEVERE|Could not|Failed` matched only
  the normal status field `queue.failed: 0`, which is not an error.
- The plugin identified itself as `EchoClaims` in all log messages, command
  output, and the `plugin.yml` registration.
- The database file was created as `echoclaims.db` (not `worldecho.db`).
- The configuration file was created as `plugins/EchoClaims/config.yml`.
- No startup, migration, command, database, or shutdown exceptions occurred.

See [RUNTIME_VALIDATION.md](RUNTIME_VALIDATION.md) for the full report.

## Remaining Technical Debt

- The `SemanticRole` and `Capability` enums were trimmed but may need further
  adjustment as the EchoClaims domain model evolves.
- The `ItemValueScorer` and `ItemScoreWeights` are carried over as-is; the
  scoring model may need to be revisited for claim-specific use cases.
- The `VanillaEntityProvider` is included but entity identification is not
  used by any current EchoClaims feature. It is kept as reusable infrastructure.
- The retention configuration (`retention.days`) is a placeholder; pruning is
  not yet implemented.
- No external provider bridges (MythicMobs, Oraxen, ItemsAdder, Citizens,
  ModelEngine) are implemented yet.

## Components Planned for the First EchoClaims MVP

1. **Incident capture** — Record player deaths, inventory snapshots, and
   related events as incidents.
2. **Inventory snapshots** — Capture and store player inventory state at
   configurable trigger points.
3. **Claim workflow** — Allow players to file lost-item claims; allow admins
   to review, approve, and deny claims based on recorded evidence.
4. **Refund execution** — Restore items or record compensation when a claim
   is approved.
5. **Audit trail** — Every claim action (file, review, approve, deny, refund)
   produces an immutable audit record.
6. **Evidence browser** — Admin command to query incidents and snapshots by
   player, time range, and item.
