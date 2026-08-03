# Sprint 1 — Memory Kernel

## Goal

Prove that WorldEcho can capture a meaningful player action, identify the participating content without provider-specific coupling, persist it safely, and expose the result to an administrator.

## Primary vertical slice

> A player is killed by a living entity while carrying at least one valuable dropped item. WorldEcho records a provider-neutral death event with the killer identity and the best loot candidate.

This sprint does **not** transfer the item to a captain. It creates the reliable memory foundation required for that later scenario.

## Required deliverables

1. Paper 26.2 / Java 25 project builds.
2. Plugin starts with no optional integrations installed.
3. SQLite initializes with versioned migrations.
4. Database work is off the main thread.
5. Vanilla entity and item identification works.
6. Player death event captures:
   - event ID
   - timestamp
   - player UUID
   - world and block location
   - killer provider and content ID
   - selected loot provider and content ID
   - selected loot display snapshot
7. `/worldecho status` reports plugin, DB, queue, and providers.
8. `/worldecho recent` shows recent captured memories.
9. `/worldecho inspect` identifies the held item or targeted entity.
10. Pure domain scenario-capability matching has tests.
11. Build workflow runs tests and uploads the JAR.
12. README documents local setup.

## Acceptance tests

- Server starts without MythicMobs, Oraxen, ItemsAdder, Citizens, or ModelEngine.
- Killing a player with a zombie and dropping a diamond sword creates one event.
- A death without an entity killer is ignored or recorded according to config.
- A death with no qualifying item records the event with no loot candidate.
- Reload/restart does not destroy existing records.
- Rapid repeated events do not write directly on the main thread.
- Invalid configuration produces a clear warning and safe defaults.
- `/worldecho recent` does not block the main thread.
- `./gradlew clean test shadowJar` passes.

## Implementation status

| Deliverable | Status | Where |
| --- | --- | --- |
| Paper 26.2 / Java 25 build | done | `build.gradle.kts`, committed Gradle 9.6.1 wrapper |
| Starts without optional integrations | done | `WorldEchoPlugin`, vanilla fallback providers |
| Versioned SQLite migrations | done | `persistence/migration/SchemaMigrator` |
| No database work on the server thread | done | `StoryWriteQueue` writer thread, reader executor, migrations run on the storage thread during enable |
| Vanilla entity and item identification | done | `integration/vanilla/*` |
| Death capture with the fields listed above | done | `PlayerDeathMemoryListener` → `DeathCapture` → `DeathMemoryFactory` |
| `/worldecho status` | done | plugin version, locale, queue counters, providers, database health, schema version, event count |
| `/worldecho recent` | done | async query, results delivered on the server thread, capped by config |
| `/worldecho inspect` | done | held item with score breakdown, ray-traced entity |
| `/worldecho reload` | done | configuration and messages only; storage keeps running |
| Scenario capability tests | done | `ScenarioCompatibilityServiceTest` |
| CI workflow | done | `.github/workflows/build.yml` |
| README setup docs | done | `README.md`, `docs/CONFIGURATION.md`, `docs/TESTING.md` |

Every acceptance test above except the in-game `/worldecho inspect` output and the
`record-without-valuable-item: false` case was executed on a real Paper 26.2 server; the
results and the remaining gaps are recorded in `docs/TESTING.md`.

## Deferred

- MythicMobs bridge
- actual item ownership transfer
- captain/superior lookup
- factions
- rumors
- player-facing journal
- story instance state machine
- Oraxen and ItemsAdder item creation
- Citizens NPC relationships
