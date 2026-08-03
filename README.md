# EchoClaims

**Resolve lost-item claims with server evidence instead of screenshots.**

EchoClaims is an early-stage commercial Paper plugin foundation. It captures,
stores, and retrieves server-side evidence so administrators can resolve
lost-item disputes without relying on player-submitted screenshots.

> **Status:** This repository contains the bootstrap foundation only.
> The complete lost-item claim workflow is not yet implemented.
> See [docs/BOOTSTRAP_FROM_WORLDECHO.md](docs/BOOTSTRAP_FROM_WORLDECHO.md)
> for the current state and planned MVP scope.

## Requirements

- **Paper 26.2** (or later 26.x)
- **Java 25**
- **SQLite** (bundled via sqlite-jdbc, no external database required)

## Validated Environment

EchoClaims has been built and tested on:

- **OS:** Windows 10 amd64
- **Java:** Eclipse Adoptium Temurin 25.0.4+7 LTS
- **Paper:** 26.2 build 87
- **EchoClaims:** 0.1.0-SNAPSHOT
- **JAR:** `echoclaims-0.1.0-SNAPSHOT.jar`

See [docs/RUNTIME_VALIDATION.md](docs/RUNTIME_VALIDATION.md) for the full
runtime validation report.

## Building

```bash
./gradlew clean test shadowJar --rerun-tasks
```

The shaded JAR is produced at `build/libs/echoclaims-0.1.0-SNAPSHOT.jar`.

## Installation

1. Copy the shaded JAR into your server's `plugins/` directory.
2. Start the server. EchoClaims creates `plugins/EchoClaims/` with a default
   `config.yml` and the SQLite database `echoclaims.db`.
3. Use `/echoclaims status` (alias: `/ec status`) to verify the plugin is
   running.

## Commands

| Command | Alias | Permission | Description |
|---------|-------|------------|-------------|
| `/echoclaims status` | `/ec status` | `echoclaims.command.status` | Reports plugin version, database availability, write queue state, provider list, and uptime. |

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `echoclaims.admin` | op | Full administration access. |
| `echoclaims.command.status` | op | Access to `/echoclaims status`. |

## Configuration

See `config.yml` inside `plugins/EchoClaims/` after first run. Every value is
validated on load; invalid values are replaced with defaults and logged.

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the layered design and
package structure.

## Development

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for build instructions, testing,
and coding conventions.

## License

Proprietary. © SkyBlueHeat. All rights reserved.
