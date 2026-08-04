# Development Guide

## Prerequisites

- **Java 25** (JDK)
- **Gradle 9.6.1+** (wrapper included)

## Build Commands

```bash
# Clean build with tests and shaded JAR
./gradlew clean test shadowJar --rerun-tasks

# Run tests only
./gradlew test

# Run the SQLite smoke test against the shaded JAR
./gradlew shadowJarSmokeTest
```

## Project Structure

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full package layout.

## Coding Conventions

1. **No Bukkit/Paper APIs in the domain layer.** The `domain` package must
   not import any Bukkit, Paper, or external plugin classes.
2. **No SQLite I/O on the main thread.** All database operations go through
   the write queue or the query executor.
3. **Immutable records across thread boundaries.** Bukkit data is captured
   into immutable records on the server thread, then persisted or processed
   asynchronously.
4. **Configuration is untrusted.** Validate all config values on load;
   replace invalid values with defaults and log warnings.
5. **Prepared SQL statements.** Never concatenate user input into SQL.
6. **Message keys, not hardcoded text.** Player-facing text uses message
   keys and locale files.
7. **Bridge failures are isolated.** A failing provider is suppressed, not
   allowed to crash the core.
8. **Tests for pure domain logic.** Add tests for all new domain logic and
   migrations.

## Testing

Tests are in `src/test/java/` under the same package structure as the main
source. JUnit 6 is used via the `junitVersion` property in `gradle.properties`.

### Test Categories

- **Settings loader tests** — Configuration validation and defaults
- **Schema migrator tests** — Idempotent migrations and schema correctness
- **Migration v2 tests** — Evidence tables, indexes, uniqueness constraints
- **Migration v3 tests** — Claim tables, indexes, foreign keys, unique public reference
- **Migration v4 tests** — Partial unique index for duplicate active claim protection
- **Write queue tests** — Bounded queue, drain on shutdown, failure handling
- **Provider registry tests** — Priority ordering, failure suppression
- **Item scorer tests** — Deterministic scoring with configurable weights
- **Status service tests** — Status report aggregation with evidence and claim metrics
- **Bundled resources tests** — Config defaults, message parity, branding
- **Audit repository tests** — Insert, batch insert, and count operations
- **Domain model tests** — InventorySnapshot, SnapshotItem, Incident, Claim validation
- **MapCodec tests** — Round-trip encoding/decoding with special characters
- **Deduplication key tests** — Deterministic key generation and time bucketing
- **Evidence repository tests** — Snapshot and incident CRUD with transactional atomicity
- **Evidence persistence service tests** — Async persistence, dedup enforcement, shutdown
- **Claim transition policy tests** — Allowed and rejected state transitions
- **Claim eligibility tests** — Ownership, status, and snapshot validation
- **Claim reference generator tests** — Uniqueness, format, collision handling
- **Claim rate limit tests** — Bounded memory, cleanup, restart, concurrency
- **Claim store tests** — Atomicity, duplicate protection, optimistic concurrency, bounded queries
- **Claim creation service tests** — End-to-end creation with eligibility and duplicate checks
- **Claim transition service tests** — Transitions with optimistic concurrency and audit

## Adding a New Migration

1. Add a new `Migration` entry to `SchemaMigrator.MIGRATIONS` with the next
   version number.
2. Write `CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` statements
   so the migration is safe to re-run.
3. Add a test in `SchemaMigratorTest` verifying the new table/index exists.
4. Never modify an existing migration — only add new ones.

## Adding a New Configuration Option

1. Add the field to `EchoClaimsSettings` with validation in the compact
   constructor.
2. Add loading logic in `SettingsLoader` with type checking and clamping.
3. Add the default value to `config.yml`.
4. Add a test in `SettingsLoaderTest`.

## Local Runtime Validation

After a successful build, validate the plugin on a clean Paper server before
pushing.

### Prerequisites

- Paper 26.2 (latest build recommended)
- Java 25 JDK (Eclipse Adoptium Temurin 25.0.4+7 LTS or equivalent)
- The shaded JAR from `build/libs/echoclaims-0.1.0-SNAPSHOT.jar`

### Procedure

1. **Build the plugin:**

   ```bash
   ./gradlew clean test shadowJar --rerun-tasks
   ```

   Confirm: BUILD SUCCESSFUL, all tests pass, smoke test passes.

2. **Prepare a clean Paper server:**

   - Download Paper 26.2 from [papermc.io](https://papermc.io).
   - Create an empty server directory (e.g. `paper-server/`).
   - Place the Paper JAR in the server directory.
   - Accept the EULA (`eula.txt`).

3. **Install EchoClaims:**

   - Copy `build/libs/echoclaims-0.1.0-SNAPSHOT.jar` into `plugins/`.
   - Do not install any other plugins.

4. **Start the server:**

   ```bash
   java -jar paper.jar nogui
   ```

5. **Verify startup:**

   - Check the console for `EchoClaims enabled (v0.1.0-SNAPSHOT)`.
   - Confirm `plugins/EchoClaims/echoclaims.db` was created.
   - Confirm `plugins/EchoClaims/config.yml` was created with EchoClaims defaults.

6. **Test commands:**

   - Run `/echoclaims status` and verify output includes:
     - version, locale, database availability (`ok`), schema version (`4`),
     - queue pending/written/failed/dropped counts,
     - evidence accepted/snapshots/incidents/duplicates/failed/rejected/pending counts,
     - claims enabled/total/draft/submitted/cancelled/audit-entries/created/submitted/cancelled/rejected/duplicate-open/rate-limited/concurrency-conflicts counts,
     - provider list (`entity:vanilla=AVAILABLE`, `item:vanilla=AVAILABLE`),
     - uptime.
   - Run `/ec status` and confirm the alias produces the same output.
   - Run `/echoclaims incidents <player>` to list recent incidents.
   - Run `/echoclaims incident <uuid>` to view a single incident.
   - Run `/echoclaims snapshot <uuid>` to view a snapshot.
   - Run `/echoclaims claim claimable` to list claimable incidents.
   - Run `/echoclaims claim create <index> [description]` to create a claim.
   - Run `/echoclaims claim list` to list your claims.
   - Run `/echoclaims claim view <ref>` to view a claim.
   - Run `/echoclaims claim submit <ref>` to submit a claim.
   - Run `/echoclaims claim cancel <ref>` to cancel a claim.
   - Run `/echoclaims claim staff-view <ref>` to view any claim as staff.
   - Run `/echoclaims claim staff-list <player>` to list a player's claims as staff.

7. **Test evidence capture:**

   - Have a player die (e.g. fall damage, mob kill).
   - Run `/echoclaims incidents <player>` and verify an incident appears.
   - Copy the incident UUID and run `/echoclaims incident <uuid>` to see details.
   - Copy the pre-event snapshot UUID and run `/echoclaims snapshot <uuid>` to see items.
   - Run `/echoclaims status` and verify evidence metrics are non-zero.

8. **Test shutdown:**

   - Stop the server with `stop`.
   - Confirm the console shows the audit write queue drained.
   - Confirm EchoClaims disabled without an exception.

9. **Search logs for errors:**

   Search the server log for:

   ```
   WorldEcho|Exception|SEVERE|Could not|Failed
   ```

   The only expected match is the status field `queue.failed: 0`, which is
   not an error. No actual exception or failure entries should appear.

10. **Clean up:**

   - Do not commit the test server, database, logs, or JAR.
   - These are excluded by `.gitignore` (`*.db`, `plugins/`, `logs/`, `*.log`).

### Non-Blocking External Warnings

The following warnings may appear in the server log and are produced by Paper
or its bundled libraries, not by EchoClaims:

- Paper is a few builds behind the latest available build.
- JOML `sun.misc.Unsafe` deprecation warning.
- async-profiler native engine unavailable on Windows (Java fallback used).

These do not indicate EchoClaims failures.
