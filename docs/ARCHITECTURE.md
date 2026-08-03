# WorldEcho Target Architecture

## Modules

The long-term repository may become a multi-module Gradle project:

```text
worldecho-api
worldecho-core
worldecho-paper
worldecho-bridge-mythicmobs
worldecho-bridge-oraxen
worldecho-bridge-itemsadder
worldecho-bridge-citizens
worldecho-bridge-modelengine
worldecho-testkit
```

Sprint 1 remains a single project with package boundaries that can later be extracted.

## Package responsibilities

```text
dev.worldecho.domain
  Pure Java domain types and rules. No Bukkit imports.

dev.worldecho.domain.binding
  Content binding domain types: BindingType, ContentBinding, BindingDiagnostic,
  BindingRegistry, EnrichedContent. No Bukkit imports.

dev.worldecho.domain.scenario
  Scenario compatibility and eligibility diagnostics: EligibilityProfile,
  EligibilityCatalog, EligibilityEvaluator, EligibilityResult, EligibilityDiagnostic,
  EligibilityFormatter. No Bukkit imports.

dev.worldecho.domain.item
  Item identity and ownership domain: TrackedItemId, OwnershipSubject,
  OwnershipSubjectType, OwnershipTransitionReason, TrackedItemRecord,
  OwnershipLedgerEntry, OwnershipState, OwnershipResult, OwnershipResultStatus,
  OwnershipTransitionService, IdentityMode, ObservedItemDescriptor,
  IdentityClassificationResult, ItemIdentityPolicy, LotCompatibilityFingerprint,
  TrackedItemLot, TrackedItemLotId, LotLineageEntry, LotRelationType,
  LotOwnershipLedgerEntry, LotOwnershipState, LotOwnershipTransitionService,
  AutomaticItemIdentityService, ReconciliationCycle, ObservedInventorySlot,
  ObservedInventorySnapshot, SlotProcessResult, ReconciliationMetrics,
  DuplicateObservationRegistry. No Bukkit imports.

dev.worldecho.application
  Use cases: record memory, generate candidate, create story, schedule consequence,
  enrich content with bindings.

dev.worldecho.persistence
  Repository interfaces, SQLite implementations, schema migrations, write queue.
  Includes TrackedItemRepository, OwnershipLedgerRepository,
  TrackedItemLotRepository, LotOwnershipLedgerRepository and their SQLite impls.

dev.worldecho.integration
  Provider-neutral contracts and registry.

dev.worldecho.integration.vanilla
  Vanilla fallback providers.

dev.worldecho.paper
  Plugin lifecycle, commands, listeners, Bukkit-to-domain mapping.

dev.worldecho.paper.inventory
  PlayerInventoryReconciler and PlayerInventoryReconciliationScheduler:
  main-thread inventory snapshot capture and asynchronous processing with
  per-player coalescing.

dev.worldecho.paper.listener
  PlayerDeathMemoryListener, PlayerInventoryObservationListener,
  ItemTransformationListener: event-driven reconciliation triggers and
  transformation identity continuity.

dev.worldecho.paper.item
  ItemIdentityAdapter: reads and writes WorldEcho tracked-item IDs on ItemStacks
  using the Persistent Data Container. Includes writeIdentity for transformation
  identity continuity.

dev.worldecho.paper.command
  WorldEchoCommand and ItemCommandHandler: command dispatch, tab completion,
  async database queries, and message formatting. Includes reconcile and policy
  subcommands.

dev.worldecho.config
  Configuration validation and message access.
```

## Provider API sketch

```java
public interface EntityContentProvider {
    String providerId();
    boolean isAvailable();
    boolean supports(Entity entity);
    Optional<ExternalEntityContent> identify(Entity entity);
    Optional<Entity> spawn(String contentId, Location location);
}
```

The final public API should avoid leaking third-party provider classes.

## Persistence strategy

### PDC

Use PDC for lightweight, stable references attached to live Minecraft objects:

- WorldEcho item UUID
- WorldEcho story entity UUID
- active story instance ID
- faction ID
- integration content key

### SQLite

Use SQLite for durable and queryable state:

- story events
- story entities
- relationships
- item history
- ownership transitions
- story instances
- scheduled consequences
- faction state
- region state
- world history
- player summaries

### Threading

- Bukkit event data is captured on the server thread.
- Immutable domain records are handed to an asynchronous single-writer queue.
- Database access never occurs on the main thread.
- Results that need Bukkit changes return to the server thread through the scheduler.

## Planned schema

```text
schema_version
story_events
story_entities
story_relationships
story_items
item_ownership
factions
faction_memberships
story_instances
story_decisions
scheduled_consequences
regions
region_metrics
world_history
player_story_state
content_bindings (in-memory registry loaded from bindings.yml)
```

## Story scenario format

Eventually scenarios should be data-driven YAML with code-backed actions.

```yaml
id: stolen_relic_command_chain
version: 1

trigger:
  event: player_killed_by_entity
  requires_drop: true

requirements:
  killer:
    roles: [soldier]
    capabilities: [can-hold-items, can-be-promoted]
  item:
    roles: [weapon, legendary-candidate]
    capabilities: [can-change-owner, can-have-history]
  faction:
    command-chain: true

actions:
  - claim-item
  - find-superior
  - transfer-item
  - create-rivalry
  - schedule-rumor

fallback:
  scenario: stolen_item_carrier
```

## Scenario safety

Every scenario must have:

- explicit eligibility rules
- a fallback or rejection reason
- idempotency protection
- maximum active instance limits
- cancellation behavior
- recovery behavior after restart
- audit events
