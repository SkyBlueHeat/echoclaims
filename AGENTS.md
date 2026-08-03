# EchoClaims Agent Instructions

These instructions apply to every task in this repository.

## Product intent

EchoClaims is a lost-item claim resolution engine for Paper servers.

It must integrate server-owned content rather than replace it:

- items from Oraxen, ItemsAdder, or vanilla
- mobs and bosses from MythicMobs or other providers
- NPCs from Citizens or other providers
- models from ModelEngine

The core must never assume a particular provider.

## Mandatory engineering rules

1. Target Paper 26.2 and Java 25 unless a task explicitly changes the compatibility target.
2. Use public Paper/Bukkit APIs. Do not use NMS or reflection into Minecraft internals.
3. External integrations must be optional and isolated behind bridge/provider interfaces.
4. The core plugin must start successfully when no external plugin is installed.
5. Never call Bukkit/Paper APIs from asynchronous threads unless the API is explicitly documented as thread-safe.
6. Never perform SQLite reads or writes on the main server thread.
7. Capture Bukkit data into immutable EchoClaims records on the main thread, then persist or process those records asynchronously.
8. No destructive world modification in early sprints.
9. Every generated consequence must be explainable from recorded causes.
10. Never silently delete audit history, item history, or claim records.
11. Prefer stable semantic roles and capabilities over provider-specific IDs in the claim engine.
12. Do not hardcode player-facing text in Java. Use message keys and locale files.
13. Admins must be able to disable systems and tune thresholds through configuration.
14. A bridge failure must disable that bridge, not the EchoClaims core.
15. Add tests for pure domain logic and migrations.
16. Every task must finish with a build, tests, and a concise changed-files report.

## Architecture boundaries

Allowed dependency direction:

```text
paper adapters / listeners
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

The domain package must not import Bukkit, Paper, MythicMobs, Oraxen, ItemsAdder, Citizens, or ModelEngine classes.

## Performance rules

- Do not persist every movement event.
- Aggregate high-frequency actions in memory.
- Use a bounded or controlled write path.
- Use one SQLite writer thread.
- Add indexes for every frequent lookup.
- Avoid scanning all loaded entities or chunks each tick.
- Avoid repeating expensive provider detection.
- Cache stable content identities carefully and invalidate on plugin reload.

## Security and reliability

- Treat YAML configuration as untrusted input.
- Validate IDs, roles, capabilities, and claim definitions.
- Use prepared SQL statements.
- Do not log secret configuration values.
- Fail closed for incompatible claim scenarios.
- Preserve a diagnostic reason when a claim is rejected.

## Pull request completion criteria

A task is complete only when:

- `./gradlew clean test shadowJar` passes
- no new warnings are introduced without explanation
- new behavior has tests where feasible
- configuration and docs are updated
- migrations are backwards-safe
- the PR description includes validation steps and known limitations
