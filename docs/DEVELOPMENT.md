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
- **Write queue tests** — Bounded queue, drain on shutdown, failure handling
- **Provider registry tests** — Priority ordering, failure suppression
- **Item scorer tests** — Deterministic scoring with configurable weights
- **Status service tests** — Status report aggregation
- **Bundled resources tests** — Config defaults, message parity, branding
- **Audit repository tests** — Insert, batch insert, and count operations

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
