# MVP-01 Validation

## Scope

MVP-01 delivers the evidence capture and persistence foundation for EchoClaims:
player death capture, inventory snapshot serialization, atomic persistence, and
administrative query commands.

## Build Verification

```bash
./gradlew clean test shadowJar
```

**Expected result**: BUILD SUCCESSFUL with 245+ tests passing and a fat JAR produced
at `build/libs/EchoClaims-<version>-all.jar`.

## Test Coverage Summary

| Test Class | Tests | Coverage |
|---|---|---|
| `BukkitItemSerializerTest` | 15 | Null/empty handling, corrupted payload, unsupported version, format introspection, config validation |
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
- Place `EchoClaims-<version>-all.jar` in `plugins/`
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

- Item round-trip serialization tests require CraftBukkit runtime; covered by runtime validation
- No claim resolution, approval, or refund features (MVP-02+)
- No GUI or webhook integration (MVP-02+)
- No external provider integration (MVP-02+)
- Single SQLite writer thread; high-frequency death events are bounded by queue capacity
