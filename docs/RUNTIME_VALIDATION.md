# Runtime Validation Report

## Summary

EchoClaims 0.1.0-SNAPSHOT was built, tested, and validated on a clean Paper
server. All build tests passed, the plugin loaded and enabled successfully,
commands produced correct output, and the server shut down cleanly with no
exceptions.

## Environment

| Component | Value |
|-----------|-------|
| Operating system | Windows 10 amd64 |
| Java | Eclipse Adoptium Temurin 25.0.4+7 LTS |
| Paper | 26.2 build 87 |
| EchoClaims | 0.1.0-SNAPSHOT |
| Generated JAR | `echoclaims-0.1.0-SNAPSHOT.jar` |

## Build Results

**Command:**

```bash
./gradlew clean test shadowJar --rerun-tasks
```

**Results:**

- BUILD SUCCESSFUL
- 30/30 tests passed
- SQLite shaded-JAR smoke test passed
- Generated JAR loaded successfully on Paper

## Runtime Results

The plugin was tested on a clean Paper server containing only EchoClaims.

### Startup

- Paper detected and initialized EchoClaims.
- EchoClaims loaded and enabled successfully.
- SQLite storage was initialized asynchronously.
- Database created at `plugins/EchoClaims/echoclaims.db`.
- Schema migration version 1 completed successfully.
- Vanilla entity and item providers reported AVAILABLE.

### Commands Tested

#### `/echoclaims status`

Produced correct output reporting:

- version
- locale
- database availability (`ok`)
- schema version (`1`)
- pending writes (`0`)
- written writes (`0`)
- failed writes (`0`)
- dropped writes (`0`)
- providers (`entity:vanilla=AVAILABLE`, `item:vanilla=AVAILABLE`)
- uptime

#### `/ec status`

Alias produced the same output as `/echoclaims status`.

### Shutdown

- The server shut down cleanly.
- The audit write queue drained successfully.
- EchoClaims disabled without an exception.

### Log Search

A log search for:

```
WorldEcho|Exception|SEVERE|Could not|Failed
```

matched only the normal status field:

```
queue.failed: 0
```

This is not an error. There were no actual failure or exception log entries.

### Branding Verification

- No WorldEcho branding appeared in runtime logs, commands, or generated data.
- The plugin identified itself as EchoClaims throughout.
- The database file was `echoclaims.db` (not `worldecho.db`).
- The configuration directory was `plugins/EchoClaims/` (not `plugins/WorldEcho/`).

## Non-Blocking External Warnings

The server reported the following warnings. These are produced by the Paper
server or its bundled libraries and are not EchoClaims failures:

- Paper was five builds behind the latest available build.
- A JOML `sun.misc.Unsafe` deprecation warning.
- The async-profiler native engine was unavailable on Windows and used its
  Java fallback.

## Conclusion

EchoClaims 0.1.0-SNAPSHOT is verified as a clean, independent Paper plugin
foundation with no WorldEcho branding in runtime code, configuration, commands,
logs, or generated data. The plugin starts, initializes SQLite storage, runs
schema migrations, registers vanilla providers, responds to status commands,
and shuts down cleanly.

## MVP-04 Refund Runtime Validation

The `RuntimeValidationPlugin` was extended with refund-specific checks.

### Refund Validation Checks

| Check | Description |
|-------|-------------|
| `refund_golden_path_setup` | Creates a test refund with READY status and PENDING items in the DB |
| `refund_golden_path_execute` | Dispatches `/ec refund execute <ref>` with cleared inventory |
| `refund_golden_path_completed_in_db` | Verifies refund status is COMPLETED in DB |
| `refund_golden_path_items_delivered` | Verifies item status is DELIVERED and delivered == refundable |
| `refund_golden_path_audit_entries` | Verifies REFUND_CREATED and REFUND_COMPLETED audit entries exist |
| `refund_status_command_works` | `/ec refund status <ref>` runs without error |
| `refund_history_command_works` | `/ec refund history <ref>` runs without error |
| `refund_partial_delivery_setup` | Creates a refund with 64 diamonds, fills inventory to force partial |
| `refund_partial_delivery_execute` | Executes refund with nearly full inventory |
| `refund_partial_delivery_verify_status` | Verifies refund is not COMPLETED (partial delivery) |
| `refund_inventory_capacity_setup` | Creates a refund with 32 diamonds, fills entire inventory |
| `refund_inventory_capacity_execute` | Executes refund with completely full inventory |
| `refund_inventory_capacity_verify_undelivered` | Verifies 0 delivered, item remains PENDING, refund not COMPLETED |
| `refund_persists_after_restart` | Verifies refund row survives server restart |
| `refund_items_persist_after_restart` | Verifies refund items survive restart with correct quantities |
| `refund_audit_persists_after_restart` | Verifies audit entries survive restart |

### Validation Method

All refund validation uses:
- **Direct DB inspection** via JDBC to verify persisted state
- **Command dispatch** via `Server.dispatchCommand()` for player/staff commands
- **Main thread execution** via `runOnMain()` for all Bukkit API calls
- **Deterministic PASS/FAIL** output for each check

### Running Refund Validation

1. Build the runtime validation JAR: `./gradlew clean test shadowJar`
2. Deploy both `echoclaims-0.1.0-SNAPSHOT.jar` and the runtime validation plugin
3. Start the Paper server with `-Dechoclaims.paper.runtime=true`
4. The validation plugin runs all checks automatically and shuts down the server
5. Results are written to `runtime-validation-results.txt`
