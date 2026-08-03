# Devin Agent Prompt — WorldEcho Sprint 1

You are implementing the first production-quality slice of **WorldEcho**, a Paper gameplay plugin.

## Product context

WorldEcho is not a conventional quest plugin and not a content-asset plugin.

Server owners already create custom mobs, bosses, weapons, NPCs, models, and factions with tools such as MythicMobs, Oraxen, ItemsAdder, Citizens, and ModelEngine.

WorldEcho must connect those objects into persistent stories.

Core promise:

> We do not create the server owner's content. We make its content remember one another.

Example future scenario:

- A custom goblin soldier kills a player.
- The player drops a custom Flame Sword.
- The soldier belongs to a configured goblin faction and has a captain.
- The sword reaches the captain.
- The captain becomes stronger, gains status, becomes the player's rival, and may later deliver the weapon to a king, overthrow the king, raid the player, sell the weapon, or lose it to another player.
- Every ownership transition and important event becomes server history.

Do not implement this entire future scenario in this session. Build the reliable memory kernel it requires.

Read these files before changing code:

- `AGENTS.md`
- `docs/PRODUCT_SPEC.md`
- `docs/ARCHITECTURE.md`
- `docs/SPRINT_1.md`
- `docs/ROADMAP.md`
- `README.md`

## Current repository state

A starter scaffold exists. Inspect it fully before editing.

Target:

- Paper 26.2
- Java 25
- Gradle Kotlin DSL
- SQLite
- one shaded plugin JAR
- no hard dependencies on external content plugins

Paper's current official setup uses `io.papermc.paper:paper-api:26.2.build.+` with a Java 25 toolchain.

## Session objective

Complete and harden **Sprint 1 — Memory Kernel**.

The vertical slice is:

> When a player is killed by a living entity, WorldEcho identifies the killer through the provider registry, selects the most valuable eligible dropped item, creates an immutable provider-neutral memory event, persists it asynchronously, and exposes it through admin commands.

## Required work

### 1. Inspect and correct the scaffold

- Review every existing source and configuration file.
- Correct compile errors, API incompatibilities, package problems, unsafe threading, and incomplete error handling.
- Keep the architecture boundaries from `AGENTS.md`.

### 2. Gradle and reproducible build

- Generate and commit a Gradle 9.6.1 wrapper.
- Ensure `./gradlew clean test shadowJar` succeeds on Java 25.
- Use the Shadow plugin to package SQLite.
- Verify the final JAR contains `plugin.yml`.
- Do not package Paper API.
- Keep dependency versions explicit except the Paper API build selector already chosen for this early snapshot.

### 3. Plugin lifecycle

Implement robust `onEnable` and `onDisable` behavior:

- create plugin data directory
- load and validate config
- initialize SQLite and migrations
- start the single-writer persistence queue
- initialize integration registry
- register listeners and commands
- log enabled providers
- shut down the queue cleanly
- reject startup cleanly if persistence cannot initialize

Do not put logic in the plugin constructor.

### 4. Persistence

Use prepared statements and versioned migrations.

Minimum table for this slice:

```text
story_events
- id TEXT PRIMARY KEY
- event_type TEXT NOT NULL
- occurred_at INTEGER NOT NULL
- world_id TEXT
- x INTEGER
- y INTEGER
- z INTEGER
- player_id TEXT
- actor_provider TEXT
- actor_content_id TEXT
- actor_runtime_id TEXT
- item_provider TEXT
- item_content_id TEXT
- item_snapshot TEXT
- details TEXT NOT NULL
```

Also include a schema version/migration table.

Requirements:

- no DB operations on the main server thread
- one controlled writer thread
- safe shutdown and queue drain
- clear failure logging
- index `occurred_at`, `player_id`, and `event_type`
- repository query for recent events
- migration tests using a temporary SQLite database

### 5. Provider-neutral integration API

Keep domain types free from Bukkit imports.

Required concepts:

- stable `ContentKey(providerId, contentId)`
- identified entity content
- identified item content
- semantic roles
- capabilities
- provider health/availability
- registry priority
- vanilla fallback providers

External bridge implementation is deferred, but the API must be designed so MythicMobs/Oraxen/ItemsAdder/Citizens bridges can be added without modifying the story engine.

Do not add compile-time dependencies on those plugins in Sprint 1.

### 6. Player death capture

Create a Paper listener that:

- resolves an entity killer, including projectile shooters where feasible
- ignores unsupported causes safely
- reads event data only on the main thread
- identifies the killer through the integration registry
- scores drops without mutating them
- chooses at most one valuable candidate
- creates an immutable memory event
- queues persistence asynchronously
- never stores mutable Bukkit objects in the async work item

Configuration must allow:

- enabling/disabling player death capture
- recording deaths without a qualifying item
- minimum item score
- ignoring specific worlds
- redacting custom names if desired

### 7. Item candidate scoring

Create a deterministic, configurable scorer.

Initial factors may include:

- material tier
- enchantment count/levels
- custom name
- unbreakable state
- damage/durability
- amount
- provider identification

The scorer must not assume every custom item is valuable. It should allow future provider metadata and admin overrides.

Add pure unit tests.

### 8. Commands

Implement `/worldecho` with permission `worldecho.admin`.

Subcommands:

- `status`
  - version
  - database health
  - write queue status
  - loaded provider list
  - captured event count

- `recent [count]`
  - query asynchronously
  - return to server thread before sending Bukkit messages
  - cap count safely

- `inspect item`
  - identify the main-hand item
  - show provider, content ID, roles, capabilities, and score

- `inspect entity`
  - ray trace a nearby entity
  - show provider, content ID, roles, and capabilities

- `reload`
  - reload safe configuration only
  - do not reload the plugin or recreate the database unsafely

Provide tab completion and useful error messages.

### 9. Localization

No player/admin-facing message should be hardcoded in command/listener classes.

Use:

- `messages_en.yml`
- `messages_tr.yml`

Implement a small message service with placeholders.

English is the default locale. Turkish is included.

### 10. Tests

At minimum:

- scenario capability matching
- item score ordering
- migration from an empty DB
- repository insert/read
- configuration validation
- provider registry priority and vanilla fallback
- immutable event mapping without retaining Bukkit objects where possible

Use pure tests for domain/application code. Avoid fragile server mocks unless clearly useful.

### 11. CI

Create GitHub Actions workflow:

- checkout
- set up Java 25
- validate Gradle wrapper
- run `./gradlew clean test shadowJar`
- upload test reports on failure
- upload the plugin JAR on success

### 12. Documentation

Update:

- `README.md`
- `docs/SPRINT_1.md`

Add:

- `docs/TESTING.md`
- `docs/CONFIGURATION.md`
- `CHANGELOG.md`

Document known limitations honestly.

## Non-negotiable constraints

- No NMS.
- No reflection into Minecraft internals.
- No database access on the main thread.
- No Bukkit/Paper object retained for asynchronous processing.
- No hard dependency on MythicMobs, Oraxen, ItemsAdder, Citizens, or ModelEngine.
- No destructive world changes.
- No custom resource pack.
- No client mod.
- No invented integration behavior that cannot be verified.
- No broad rewrite that ignores the existing scaffold.
- Do not start Sprint 2.

## Completion criteria

The session is complete only when:

1. `./gradlew clean test shadowJar` passes.
2. The produced JAR is identified and its path is reported.
3. Tests cover the required domain and persistence behavior.
4. All database operations are demonstrably off the main thread.
5. The plugin can start with no optional integrations.
6. The recent-events command is asynchronous.
7. Documentation explains how to run a manual Paper test.
8. You provide:
   - concise implementation summary
   - changed file list
   - commands run
   - test results
   - known limitations
   - recommended Sprint 2 tasks

## Working style

- First inspect and write a short implementation plan.
- Then implement in small, reviewable commits.
- Run tests after each meaningful slice.
- If a current Paper API differs from the scaffold, consult official Paper documentation and adapt.
- Prefer explicit and boring reliability over clever abstractions.
- Do not claim manual in-game validation unless you actually ran a Paper server and reproduced the event.
