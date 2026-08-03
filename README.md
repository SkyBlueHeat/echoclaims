# WorldEcho

> Working title: **WorldEcho — The World Remembers**

WorldEcho is a Paper gameplay plugin and integration framework that turns a server's existing mobs, items, NPCs, factions, and player actions into persistent stories.

The server owner supplies content through plugins such as MythicMobs, Oraxen, ItemsAdder, Citizens, and ModelEngine. WorldEcho supplies the memory graph, semantic content model, scenario compatibility rules, delayed consequences, rumors, item history, faction command chains, and world chronicle.

## Status

This repository implements **Sprint 0.3B — Automatic Player Inventory Identity & Ownership
Synchronization**, building on Sprint 0.3A's persistent item identity and ownership ledger
and Sprint 0.2B's eligibility diagnostics.

WorldEcho automatically identifies and records items entering active player inventories.
Normal players and server administrators are not expected to manually track items.

The implemented vertical slice is:

> When a living entity kills a player, WorldEcho identifies the killer through the provider
> registry, selects the most valuable eligible dropped item, creates an immutable
> provider-neutral memory event, persists it asynchronously, and exposes it through admin
> commands.

Current foundation:

- Paper 26.2 / Java 25 Gradle project with a committed Gradle 9.6.1 wrapper
- SQLite storage with versioned, idempotent migrations and indexed lookups
- bounded single-writer persistence queue and a separate reader thread
- provider-neutral content identity API with priority ordering and vanilla fallback
- a failing bridge suppresses itself instead of disabling the core
- player-death capture that reads Bukkit state only on the server thread
- deterministic, configuration-driven item value scoring with an explainable breakdown
- content bindings: `bindings.yml` maps provider content IDs to semantic roles,
  capabilities, faction, rank, superior, and tags; bindings enrich but never replace
  provider-supplied metadata
- scenario eligibility diagnostics: built-in profiles evaluate whether bound content is
  semantically ready for future story roles; structured diagnostics with stable codes
- persistent item identity via Paper Persistent Data Container (`worldecho:item_id`)
- append-only ownership ledger with immutable entries, sequence numbers, idempotency
  keys, and transition reasons
- `/worldecho status`, `recent`, `inspect item|entity`, `reload`,
  `eligibility profiles|check|all`, and `item track|inspect|owner|history|assign-owner`
- English and Turkish message files with sanitized placeholders
- unit tests for scoring, mapping, configuration, messages, providers, migrations,
  persistence, content bindings, eligibility, item identity, ownership ledger, and the
  shipped resources

Not implemented yet (Sprint 0.3C and later): world-drop tracking, container ownership,
mob equipment, story generation, rival generation, factions, and external content bridges.

## Core product rule

**WorldEcho does not create the server owner's art assets. It makes their content remember and affect one another.**

## Build

Requires JDK 25. The Gradle wrapper is committed, so no local Gradle installation is
needed.

```bash
./gradlew clean test shadowJar
```

The distributable JAR is produced under:

```text
build/libs/worldecho-0.1.0-SNAPSHOT.jar
```

SQLite is shaded into the JAR (deliberately **not** relocated, so its native library keeps
binding) and the Paper API is not packaged. `shadowJar` is finalized by a smoke test that
opens a SQLite database using only the shaded JAR.

## Runtime

Place the JAR in a Paper 26.2 server's `plugins` directory.

WorldEcho has no hard dependency on any external content plugin and starts with none
installed.

### Commands

All subcommands require the `worldecho.admin` permission (default: op). Item subcommands
also require their respective permissions: `worldecho.item.inspect`, `worldecho.item.track`,
`worldecho.item.history`, `worldecho.item.assign`, `worldecho.item.reconcile`,
`worldecho.item.policy`.

| Command | Description |
| --- | --- |
| `/worldecho status` | Version, locale, queue counters, providers, database health, schema version, event count, tracked-items, ledger-entries, automatic tracking metrics |
| `/worldecho recent [count]` | Most recent memories, read off the server thread |
| `/worldecho inspect item` | Provider, content ID, roles, capabilities, binding metadata, score breakdown, and ownership data of the held item |
| `/worldecho inspect entity` | Provider, content ID, roles, capabilities, and binding metadata of the entity you are looking at |
| `/worldecho reload` | Re-reads `config.yml`, `bindings.yml`, and the message files |
| `/worldecho item track` | **Diagnostic/repair tool.** Assign a WorldEcho ID to the held item and create a persistence record. Automatic tracking handles items without commands. |
| `/worldecho item inspect` | Show tracked status, item ID, content key, material, current owner, and history count |
| `/worldecho item owner <item-id>` | Show current ownership state for a tracked item |
| `/worldecho item history <item-id> [count]` | Show ownership history entries in descending sequence order |
| `/worldecho item assign-owner <item-id> player\|entity\|system <id>` | **Administrative repair tool.** Assign ownership without moving the physical item. Future physical inventory reconciliation may correct ledger ownership back to observed reality. |
| `/worldecho item reconcile [player]` | Trigger manual inventory reconciliation for diagnostics and recovery |
| `/worldecho item policy` | Show identity classification mode, reasons, confidence, and lot compatibility for the held item |

## Documentation

- `AGENTS.md` — engineering rules for Devin and other coding agents
- `docs/PRODUCT_SPEC.md` — complete product direction
- `docs/ARCHITECTURE.md` — target architecture
- `docs/SPRINT_0.3A.md` — Sprint 0.3A item identity and ownership ledger details
- `docs/SPRINT_0.3B.md` — Sprint 0.3B automatic player inventory tracking details
- `docs/SPRINT_1.md` — first implementation slice
- `docs/ROADMAP.md` — staged delivery plan
- `docs/CONFIGURATION.md` — every configuration key and the scoring formula
- `docs/TESTING.md` — automated coverage, what was verified on a real Paper 26.2 server,
  and the manual checklist
- `DEVIN_PROMPT.md` — ready-to-paste Devin Agent prompt

## Important

`WorldEcho` is a working name. Perform a complete name, platform, domain, and trademark check before public release.
