# EchoClaims

**Resolve lost-item claims with server evidence instead of screenshots.**

[![CI](https://github.com/SkyBlueHeat/echoclaims/actions/workflows/ci.yml/badge.svg)](https://github.com/SkyBlueHeat/echoclaims/actions/workflows/ci.yml)

EchoClaims is an early-stage commercial Paper plugin foundation. It captures,
stores, and retrieves server-side evidence so administrators can resolve
lost-item disputes without relying on player-submitted screenshots.

> **Status:** Claim foundation (MVP-02) implemented.
> Player death evidence is captured, persisted, and queryable via admin commands.
> Players can create, submit, and cancel claims against incidents.
> Staff can view and list claims. Refund, approval, and GUI workflows are not yet implemented.
> See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the current state.

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
# Clean build with tests and shaded JAR
./gradlew clean test shadowJar --rerun-tasks

# Paper integration tests (disposable Paper server)
./gradlew paperIntegrationTest --rerun-tasks --no-configuration-cache

# Full runtime validation (disposable Paper server with headless bots)
./gradlew runtimeValidation --rerun-tasks --no-configuration-cache
```

The shaded JAR is produced at `build/libs/echoclaims-0.1.0-SNAPSHOT.jar`.

CI runs all three suites plus production JAR inspection on every pull request
and push to `main`. See [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

## Installation

1. Copy the shaded JAR into your server's `plugins/` directory.
2. Start the server. EchoClaims creates `plugins/EchoClaims/` with a default
   `config.yml` and the SQLite database `echoclaims.db`.
3. Use `/echoclaims status` (alias: `/ec status`) to verify the plugin is
   running.

## Commands

| Command | Alias | Permission | Description |
|---------|-------|------------|-------------|
| `/echoclaims status` | `/ec status` | `echoclaims.command.status` | Reports plugin version, database, queue, evidence metrics, providers, and uptime. |
| `/echoclaims incidents <player>` | `/ec incidents <player>` | `echoclaims.command.incidents` | Lists recent incidents for a player. |
| `/echoclaims incident <uuid>` | `/ec incident <uuid>` | `echoclaims.command.incident` | Shows details of a single incident by UUID. |
| `/echoclaims snapshot <uuid>` | `/ec snapshot <uuid>` | `echoclaims.command.snapshot` | Shows details of an inventory snapshot by UUID. |
| `/echoclaims claim list` | `/ec claim list` | `echoclaims.command.claim.list` | Lists your recent claims. |
| `/echoclaims claim view <ref>` | `/ec claim view <ref>` | `echoclaims.command.claim.view` | Shows details of a claim by public reference. |
| `/echoclaims claim claimable` | `/ec claim claimable` | `echoclaims.command.claim.create` | Lists your OPEN incidents that can be claimed. |
| `/echoclaims claim create <id-or-index> [desc]` | `/ec claim create <id-or-index> [desc]` | `echoclaims.command.claim.create` | Creates a DRAFT claim for an incident. |
| `/echoclaims claim submit <ref>` | `/ec claim submit <ref>` | `echoclaims.command.claim.submit` | Submits a DRAFT claim for review. |
| `/echoclaims claim cancel <ref>` | `/ec claim cancel <ref>` | `echoclaims.command.claim.cancel` | Cancels a DRAFT or SUBMITTED claim. |
| `/echoclaims claim staff-view <ref>` | `/ec claim staff-view <ref>` | `echoclaims.command.claim.staff.view` | Staff: view any claim by reference. |
| `/echoclaims claim staff-list <player>` | `/ec claim staff-list <player>` | `echoclaims.command.claim.staff.list` | Staff: list claims for a player. |

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `echoclaims.admin` | op | Full administration access. |
| `echoclaims.command.status` | op | Access to `/echoclaims status`. |
| `echoclaims.command.incidents` | op | Access to `/echoclaims incidents`. |
| `echoclaims.command.incident` | op | Access to `/echoclaims incident`. |
| `echoclaims.command.snapshot` | op | Access to `/echoclaims snapshot`. |
| `echoclaims.command.claim.list` | op | Access to `/echoclaims claim list`. |
| `echoclaims.command.claim.view` | op | Access to `/echoclaims claim view`. |
| `echoclaims.command.claim.create` | op | Access to `/echoclaims claim create` and `claimable`. |
| `echoclaims.command.claim.cancel` | op | Access to `/echoclaims claim cancel`. |
| `echoclaims.command.claim.submit` | op | Access to `/echoclaims claim submit`. |
| `echoclaims.command.claim.staff.view` | op | Staff: view any claim. |
| `echoclaims.command.claim.staff.list` | op | Staff: list claims for a player. |

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
