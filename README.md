# EchoClaims

**Resolve lost-item claims with server evidence instead of screenshots.**

[![CI](https://github.com/SkyBlueHeat/echoclaims/actions/workflows/ci.yml/badge.svg)](https://github.com/SkyBlueHeat/echoclaims/actions/workflows/ci.yml)

EchoClaims is an early-stage commercial Paper plugin foundation. It captures,
stores, and retrieves server-side evidence so administrators can resolve
lost-item disputes without relying on player-submitted screenshots.

> **Status:** Refund System (MVP-04) implemented.
> Player death evidence is captured, persisted, and queryable via admin commands.
> Players can create, submit, and cancel claims against incidents.
> Staff can review submitted claims, examine evidence, make per-item decisions,
> request information from players, and finalize reviews with outcomes (APPROVED,
> PARTIALLY_APPROVED, REJECTED). Approved reviews generate refunds with
> partial delivery, inventory capacity handling, and crash recovery support.
> GUI workflows are not yet implemented.
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
| `/echoclaims claim respond <ref> <message>` | `/ec claim respond <ref> <message>` | `echoclaims.claim.respond` | Player: respond to a staff information request. |
| `/echoclaims review queue` | `/ec review queue` | `echoclaims.staff.review.queue` | Staff: view the review queue of submitted claims. |
| `/echoclaims review view <ref>` | `/ec review view <ref>` | `echoclaims.staff.review.view` | Staff: view a claim review. |
| `/echoclaims review start <ref>` | `/ec review start <ref>` | `echoclaims.staff.review.start` | Staff: start a review and take ownership. |
| `/echoclaims review takeover <ref> <reason>` | `/ec review takeover <ref> <reason>` | `echoclaims.staff.review.takeover` | Staff: take over a review from another staff member. |
| `/echoclaims review evidence <ref> [index]` | `/ec review evidence <ref> [index]` | `echoclaims.staff.review.evidence` | Staff: view evidence for a review. |
| `/echoclaims review note <ref> <note>` | `/ec review note <ref> <note>` | `echoclaims.staff.review.note` | Staff: add an internal note to a review. |
| `/echoclaims review request-info <ref> <question>` | `/ec review request-info <ref> <question>` | `echoclaims.staff.review.request-info` | Staff: request information from the claimant. |
| `/echoclaims review item <ref> <index> <outcome> <reason> [note]` | `/ec review item <ref> ...` | `echoclaims.staff.review.item-decision` | Staff: create or update an item-level decision. |
| `/echoclaims review approve <ref> [summary]` | `/ec review approve <ref> [summary]` | `echoclaims.staff.review.approve` | Staff: finalize a review as APPROVED. |
| `/echoclaims review partial <ref> [summary]` | `/ec review partial <ref> [summary]` | `echoclaims.staff.review.partial` | Staff: finalize a review as PARTIALLY_APPROVED. |
| `/echoclaims review reject <ref> <summary>` | `/ec review reject <ref> <summary>` | `echoclaims.staff.review.reject` | Staff: finalize a review as REJECTED (summary required). |
| `/echoclaims review history <ref>` | `/ec review history <ref>` | `echoclaims.staff.review.history` | Staff: view review history and audit trail. |
| `/echoclaims refund status <ref>` | `/ec refund status <ref>` | `echoclaims.staff.refund.status` | Staff: view refund status for a claim. |
| `/echoclaims refund execute <ref>` | `/ec refund execute <ref>` | `echoclaims.staff.refund.execute` | Staff: execute a refund on behalf of a player. |
| `/echoclaims refund history <ref>` | `/ec refund history <ref>` | `echoclaims.staff.refund.history` | Staff: view refund audit history. |
| `/echoclaims refund retry <ref>` | `/ec refund retry <ref>` | `echoclaims.staff.refund.retry` | Staff: retry a failed refund. |
| `/echoclaims refund pending` | `/ec refund pending` | `echoclaims.refund.pending` | Player: view your pending refunds. |
| `/echoclaims refund claim <ref>` | `/ec refund claim <ref>` | `echoclaims.refund.claim` | Player: claim your refund (self-delivery). |

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
| `echoclaims.claim.respond` | true | Player: respond to staff information requests. |
| `echoclaims.staff.review.queue` | op | Staff: view the review queue. |
| `echoclaims.staff.review.view` | op | Staff: view a claim review. |
| `echoclaims.staff.review.start` | op | Staff: start a review. |
| `echoclaims.staff.review.takeover` | op | Staff: take over a review. |
| `echoclaims.staff.review.evidence` | op | Staff: view evidence for a review. |
| `echoclaims.staff.review.note` | op | Staff: add internal notes. |
| `echoclaims.staff.review.request-info` | op | Staff: request information from the claimant. |
| `echoclaims.staff.review.item-decision` | op | Staff: create/update item-level decisions. |
| `echoclaims.staff.review.approve` | op | Staff: finalize as APPROVED. |
| `echoclaims.staff.review.partial` | op | Staff: finalize as PARTIALLY_APPROVED. |
| `echoclaims.staff.review.reject` | op | Staff: finalize as REJECTED. |
| `echoclaims.staff.review.history` | op | Staff: view review history and audit trail. |
| `echoclaims.staff.refund.status` | op | Staff: view refund status. |
| `echoclaims.staff.refund.execute` | op | Staff: execute a refund. |
| `echoclaims.staff.refund.history` | op | Staff: view refund audit history. |
| `echoclaims.staff.refund.retry` | op | Staff: retry a failed refund. |
| `echoclaims.refund.pending` | true | Player: view own pending refunds. |
| `echoclaims.refund.claim` | true | Player: claim own refund (self-delivery). |

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
