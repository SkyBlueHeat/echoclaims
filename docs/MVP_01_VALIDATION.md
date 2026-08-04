# MVP-01 Validation

## Scope

MVP-01 delivers the evidence capture and persistence foundation for EchoClaims:
player death capture, inventory snapshot serialization, atomic persistence, and
administrative query commands.

## Build Verification

```bash
./gradlew clean test shadowJar
```

**Expected result**: BUILD SUCCESSFUL with 246 passed, 14 skipped, 0 failed,
and a fat JAR produced at `build/libs/echoclaims-<version>.jar`.

The 14 skipped tests are `BukkitItemSerializer` round-trip tests gated by
`@EnabledIfSystemProperty(named = "echoclaims.paper.runtime", matches = "true")`.
They require a running Paper server and are executed by the Paper-backed
integration test task (see below).

> **Note**: A naive `grep -c SKIPPED` on Gradle output reports 16 matches
> because two PASSED tests have "Skipped" in their method names:
> `IncidentTest.blankMetadataKeysAreSkipped()` and
> `MapCodecTest.malformedIntValuesAreSkipped()`. The JUnit XML reports
> confirm the true skip count is 14.

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

## Fully Automated Runtime Validation

### Command

```bash
./gradlew runtimeValidation --rerun-tasks --no-configuration-cache
```

### How It Works

1. The `runtimeValidation` Gradle task builds the EchoClaims shadow JAR and
   a `RuntimeValidationPlugin` JAR from the `src/runtimeValidation` source set.
2. A disposable Paper 26.2 build 92 server is started at `build/runtimeServer/`
   with `online-mode=false`, flat world, and `max-tick-time=-1`.
3. Both `EchoClaims.jar` and `RuntimeValidationPlugin.jar` are placed in
   `plugins/`.
4. The Gradle task waits for the server to print "Done" (up to 120 seconds).
5. A **headless Minecraft client** (`HeadlessBotClient`) is launched as a
   separate Java process using **MCProtocolLib 26.2-SNAPSHOT** (protocol
   version 776, Minecraft 26.2).
6. The bot connects to `127.0.0.1:25565` in offline mode as `TestBot`
   (no Minecraft account required).
7. The `RuntimeValidationPlugin` detects the player join and runs the full
   validation scenario on a dedicated async thread, scheduling Bukkit API
   calls back on the main thread via `BukkitRunnable`.
8. The bot automatically sends respawn requests when it receives death
   screens (`ClientboundPlayerCombatKillPacket` →
   `ServerboundClientCommandPacket(PERFORM_RESPAWN)`).
9. After all checks complete, the validation plugin writes results to
   `validation-results.txt` and shuts down the server.
10. The Gradle task reads the results, prints them, and fails if any check
    failed.

### Headless Client Details

- **Library**: MCProtocolLib (`org.geysermc.mcprotocollib:protocol:26.2-20260709.110151-15`)
- **Protocol version**: 776 (Minecraft 26.2)
- **Repository**: OpenCollab Snapshots (`https://repo.opencollab.dev/maven-snapshots/`)
- **Authentication**: Offline mode (no Microsoft account, no credentials)
- **Bot username**: `TestBot`
- **Death handling**: Automatically sends `PERFORM_RESPAWN` on death screen

### Validation Checks (24 total)

| # | Check | Description |
|---|---|---|
| 1 | `EchoClaims_loaded` | EchoClaims plugin loaded and enabled |
| 2 | `EchoClaims_ready` | Database initialized with `incidents` and `inventory_snapshots` tables |
| 3 | `schema_migration_v1_v2` | `schema_version` table contains migrations 1 and 2 |
| 4 | `no_WorldEcho_branding` | No "WorldEcho" string in plugin name or config |
| 5 | `player_given_items` | Player given stone, bread, enchanted sword, armor, offhand shield |
| 6 | `first_death_triggered` | Player death and respawn events fired (keepInventory=false) |
| 7 | `first_incident_created` | Incident row in DB with `incident_type=PLAYER_DEATH`, `status=OPEN` |
| 8 | `first_snapshot_contents` | Pre-death snapshot has inventory, armor, offhand, and enchanted items |
| 9 | `second_death_triggered` | Second death at different location with different inventory |
| 10 | `second_incident_created` | Two distinct incidents in DB (no dedup for different deaths) |
| 11 | `command_status` | `/echoclaims status` executes without error |
| 12 | `command_incidents` | `/echoclaims incidents <uuid>` executes without error |
| 13 | `command_incident` | `/echoclaims incident <uuid>` executes without error |
| 14 | `command_snapshot` | `/echoclaims snapshot <uuid>` executes without error |
| 15 | `command_ec_alias` | `/ec status` alias executes without error |
| 16 | `keepInventory_false_incident_metadata` | First two incidents record `keepInventory=false` in metadata |
| 17 | `keepInventory_true_death` | Third death with `keepInventory=true` gamerule, death and respawn fired |
| 18 | `keepInventory_true_items_retained` | Player retains diamond and iron ingot after respawn with keepInventory=true |
| 19 | `keepInventory_true_incident_metadata` | Third incident records `keepInventory=true` in metadata |
| 20 | `restart_persistence` | Incidents and snapshots persisted in DB (>= 3 each) |
| 21 | `migration_idempotency` | `schema_version` still has exactly 2 migrations (no duplicates) |
| 22 | `no_exceptions` | No EchoClaims exceptions in server log |
| 23 | `shutdown_drain` | EchoClaims plugin still enabled at shutdown check |
| 24 | `no_remaining_threads` | No leftover EchoClaims threads |

### Results

```
PASS:EchoClaims_loaded
PASS:EchoClaims_ready
PASS:schema_migration_v1_v2
PASS:no_WorldEcho_branding
PASS:player_given_items
PASS:first_death_triggered
PASS:first_incident_created
PASS:first_snapshot_contents
PASS:second_death_triggered
PASS:second_incident_created
PASS:command_status
PASS:command_incidents
PASS:command_incident
PASS:command_snapshot
PASS:command_ec_alias
PASS:keepInventory_false_incident_metadata
PASS:keepInventory_true_death
PASS:keepInventory_true_items_retained
PASS:keepInventory_true_incident_metadata
PASS:restart_persistence
PASS:migration_idempotency
PASS:no_exceptions
PASS:shutdown_drain
PASS:no_remaining_threads
SUMMARY:executed=24,passed=24,failed=0,skipped=0
```

- **Executed**: 24
- **Passed**: 24
- **Failed**: 0
- **Skipped**: 0

### Bot Protocol Evidence

```
[HeadlessBot] Connecting to 127.0.0.1:25565 as TestBot
[HeadlessBot] Protocol: 776 (26.2)
[HeadlessBot] Received login packet - player joined the game
[HeadlessBot] Received death screen - sending respawn request
[HeadlessBot] Received respawn packet - player respawned
[HeadlessBot] Received death screen - sending respawn request
[HeadlessBot] Received respawn packet - player respawned
[HeadlessBot] Received death screen - sending respawn request
[HeadlessBot] Received respawn packet - player respawned
```

### keepInventory Validation Scenarios

**keepInventory=false** (deaths 1 and 2, default gamerule):

- PRE_DEATH snapshot contains the items (stone, bread, enchanted sword, armor, offhand shield)
- Player respawns without those items (items dropped on death)
- Incident metadata records `keepInventory=false`
- Verified by `keepInventory_false_incident_metadata` check

**keepInventory=true** (death 3, gamerule set via typed API):

- `world.setGameRule(GameRules.KEEP_INVENTORY, true)` applied before death
- PRE_DEATH snapshot contains the items (diamond, iron ingot)
- Player retains diamond and iron ingot after respawn
- Incident metadata records `keepInventory=true`
- Verified by `keepInventory_true_death`, `keepInventory_true_items_retained`,
  and `keepInventory_true_incident_metadata` checks

### Typed GameRule API

The `DeathCaptureService` uses the typed Paper 26.2 API instead of the
deprecated string-based lookup:

```java
Boolean keepInventory = world.getGameRuleValue(GameRules.KEEP_INVENTORY);
```

- **Typed constant**: `org.bukkit.GameRules.KEEP_INVENTORY` (`GameRule<Boolean>`)
- **Typed lookup**: `World.getGameRuleValue(GameRule<T>)` returns `@NotNull T`
- **Failure behavior**: If the typed lookup unexpectedly returns `null`, the
  metadata records `keepInventory=unknown` and a warning is logged. The value
  is never silently fabricated as `false`.

### MCProtocolLib Dependency Coordinate

The headless client dependency is scoped exclusively to the `runtimeValidation`
source set and is absent from the production JAR:

```
org.geysermc.mcprotocollib:protocol:26.2-20260709.110151-15
```

- **Repository**: `https://repo.opencollab.dev/maven-snapshots/`
- **Configuration**: `runtimeValidationImplementation` only
- **Pinned version**: `26.2-20260709.110151-15` (exact snapshot, no changing selector)
- **Protocol version**: 776 (Minecraft 26.2)

### Production JAR Inspection

The production shadow JAR (`echoclaims-0.1.0-SNAPSHOT.jar`, 296 entries) was
inspected to confirm no validation dependencies leaked into it:

```
MCProtocolLib classes (org.geysermc): 0
Netty classes (io.netty): 0
RuntimeValidationPlugin classes: 0
HeadlessBotClient classes: 0
validation plugin.yml: 0
Total entries: 296
```

The MCProtocolLib dependency, Netty transport, validation plugin, and headless
bot client are all confined to the `runtimeValidation` source set and do not
appear in the production JAR, normal runtime dependencies, or plugin
distribution artifacts.

### Bug Fix During Validation

The runtime validation discovered a bug in `DeathCaptureService.buildIncident()`:

- `world.getGameRuleValue("keepInventory")` (string-based, deprecated) threw
  `IllegalArgumentException: Unknown gamerule: keepInventory` on Paper 26.2.
- **Fix**: Replaced with the typed API `world.getGameRuleValue(GameRules.KEEP_INVENTORY)`
  which returns `@NotNull Boolean`. If the lookup unexpectedly returns `null`,
  the metadata records `keepInventory=unknown` with a logged warning — never
  silently fabricating `false`.
  (`@/src/main/java/io/github/skyblueheat/echoclaims/paper/listener/DeathCaptureService.java:256-270`)

## Known Limitations

- No claim resolution, approval, or refund features (MVP-02+)
- No GUI or webhook integration (MVP-02+)
- No external provider integration (MVP-02+)
- Single SQLite writer thread; high-frequency death events are bounded by queue capacity
