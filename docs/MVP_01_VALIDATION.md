# MVP-01 Validation

## Scope

MVP-01 delivers the evidence capture and persistence foundation for EchoClaims:
player death capture, inventory snapshot serialization, atomic persistence, and
administrative query commands.

## Build Verification

```bash
./gradlew clean test shadowJar
```

**Expected result**: BUILD SUCCESSFUL with 246 passed, 16 skipped, 0 failed,
and a fat JAR produced at `build/libs/echoclaims-<version>.jar`.

The 16 skipped tests are `BukkitItemSerializer` round-trip tests gated by
`@EnabledIfSystemProperty(named = "echoclaims.paper.runtime", matches = "true")`.
They require a running Paper server and are executed by the Paper-backed
integration test task (see below).

## Paper-Backed Integration Test

### Command

```bash
./gradlew paperIntegrationTest --rerun-tasks --no-configuration-cache
```

### How It Works

1. The `paperIntegrationTest` Gradle task downloads Paper 26.2 build 92
   (stable) to `build/paperServer/paper.jar` (cached on subsequent runs).
2. A disposable server directory is created at `build/paperServer/` with
   `eula.txt` and the test plugin JAR in `plugins/`.
3. The test plugin (`SerializationTestPlugin`) is compiled from the
   `src/paperIntegrationTest` source set and bundled with EchoClaims main
   classes into `SerializationTestPlugin.jar`.
4. Paper starts with `java -Xmx1G -jar paper.jar nogui`.
5. During `onEnable()`, the plugin runs 14 round-trip serialization tests
   on the main thread (where Bukkit's `RegistryAccess` is available).
6. Results are written to `build/paperServer/results.txt`.
7. The plugin calls `getServer().shutdown()` to stop the server cleanly.
8. The Gradle task reads the results file, prints each test outcome, and
   fails the build if any test failed.

### Environment

- **Paper version**: 26.2-92 (stable, 2026-08-02T20:33:44Z)
- **Java version**: Eclipse Adoptium Temurin 25.0.4+7 (LTS)
- **Minecraft version**: 26.2
- **Server directory**: `build/paperServer/` (disposable, gitignored)

### Results

```
PASS:ordinaryVanillaItem
PASS:enchantments
PASS:customDisplayName
PASS:lore
PASS:damage
PASS:unbreakable
PASS:potionMetadata
PASS:armorItem
PASS:offhandItem
PASS:amountPreservation
PASS:oversizedPayload
PASS:airItem
PASS:serializationUniqueness
PASS:formatVersionInspection
SUMMARY:executed=14,passed=14,failed=0,skipped=0
```

- **Executed**: 14
- **Passed**: 14
- **Failed**: 0
- **Skipped**: 0

### Server Startup and Shutdown

- **Startup**: Paper initialized successfully, loaded 1 plugin
  (`SerializationTestPlugin`), prepared spawn area in ~20 seconds.
- **Shutdown**: Plugin called `getServer().shutdown()` after all tests
  passed. Server process exited cleanly. Gradle task detected results
  file and reported BUILD SUCCESSFUL.

### Proof of Initialized Paper Runtime

The server log confirms a fully initialized Paper environment:

```
[Server thread/INFO]: This server is running Paper version 26.2-92-main@0a99345
  (Implementing API version 26.2.build.92-stable)
[Server thread/INFO]: [SerializationTestPlugin] Enabling SerializationTestPlugin v0.1.0
[Server thread/INFO]: [SerializationTestPlugin] SerializationTestPlugin: starting round-trip tests
[Server thread/INFO]: [SerializationTestPlugin] SerializationTestPlugin: 14 passed, 0 failed, shutting down server
```

`ItemStack.serializeAsBytes()` and `ItemStack.deserializeBytes()` require
`RegistryAccess`, which is only available when the server is fully running.
All 14 tests passed, proving the serialization works correctly in a real
Paper environment.

## Test Coverage Summary

| Test Class | Tests | Coverage |
|---|---|---|
| `BukkitItemSerializerTest` (CI) | 16 | Null/empty handling, corrupted payload, unsupported version, format introspection, config validation |
| `BukkitItemSerializerTest` (Paper) | 14 | Round-trip: vanilla items, enchantments, display name, lore, damage, unbreakable, potion, armor, offhand, amount, oversized, air, uniqueness, format version |
| `EvidenceTransactionAtomicityTest` | 7 | Atomic insert of snapshot+items+incident, rollback on duplicate key, rollback on incident failure, retry after rollback, post-respawn atomicity, nonexistent incident rollback |
| `DeduplicationKeyFactoryTest` | 10+ | Key determinism, collision resistance with different causes, different inventories, snapshot fingerprint consistency |
| `EvidencePersistenceServiceTest` | 7 | Single insert, duplicate detection, post-respawn link, queue rejection, shutdown drain, repeated disable safety |
| `EchoClaimsCommandTest` | 18 | Permission checks, subcommand routing, async query handling, not-ready states, UUID parsing, tab completion, message sending |
| `DeathCaptureServiceTest` | 4 | Disable flag, concurrent duplicate key consistency, different-cause collision resistance, different-inventory collision resistance |
| `EvidenceRepositoryTest` | 8 | Snapshot insert/query, incident insert/query, atomic snapshot+items, duplicate key, count, recent by player |
| `DatabaseManagerTest` | 9 | Schema version, foreign keys, path validation, idempotent init, pragmas |
| `SchemaMigratorTest` | 4 | Migration to latest, idempotent re-run, audit records, applied versions |
| `SchemaMigratorFailureTest` | 12 | Rollback on failure, version ordering, gap detection, retry after failure |
| `MigrationV2Test` | 4 | Evidence tables, indexes, unique constraints |
| `BundledResourcesTest` | 5 | Message keys exist, TR translations, config matches defaults, plugin identity |
| `AuditWriteQueueTest` | 4 | Drain on shutdown, failure counting, post-shutdown rejection, queue full drop |
| `AuditWriteQueueConcurrencyTest` | 11 | Concurrent submit/shutdown, metric invariants, double shutdown, partial failure |
| `MapCodecTest` | 7 | String/int map round-trip, special characters, empty/null handling |

## Runtime Validation Steps

### 1. Server Setup

- Paper 26.2 (Java 25)
- Place `EchoClaims-<version>.jar` in `plugins/`
- Start server, confirm no errors in console

### 2. Plugin Startup

- Verify console shows EchoClaims initialization
- Run `/echoclaims status` — should display version, locale, database OK, schema version, queue stats

### 3. Death Capture

- Spawn a player with inventory items
- Kill the player (e.g. fall damage, mob)
- Verify console logs evidence capture
- Run `/echoclaims incidents <player-uuid>` — should list the death incident
- Run `/echoclaims incident <incident-uuid>` — should show incident details
- Run `/echoclaims snapshot <snapshot-uuid>` — should show inventory contents

### 4. Post-Respawn Capture

- After death, respawn the player
- Verify post-respawn snapshot is captured and linked to the incident
- Run `/echoclaims incident <incident-uuid>` — should show post-event snapshot UUID

### 5. Deduplication

- Trigger a death event that fires twice (e.g. via plugin conflict)
- Verify only one incident is persisted (duplicate counted in metrics)
- Check `/echoclaims status` — `evidence.duplicates` should be > 0

### 6. Atomicity

- Kill a player, then immediately stop the server
- Restart and verify no orphan snapshots exist in the database
- All snapshots should have corresponding incidents

### 7. Configuration

- Set `evidence.capture.player-death: false` in `config.yml`
- Reload plugin
- Kill a player — no evidence should be captured
- Re-enable and verify capture resumes

### 8. Permissions

- Test with a player lacking `echoclaims.command.incidents` — should get "no permission"
- Test with admin/console — should work

## Known Limitations

- No claim resolution, approval, or refund features (MVP-02+)
- No GUI or webhook integration (MVP-02+)
- No external provider integration (MVP-02+)
- Single SQLite writer thread; high-frequency death events are bounded by queue capacity
