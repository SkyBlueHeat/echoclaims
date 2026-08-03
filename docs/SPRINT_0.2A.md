# Sprint 0.2A — Content Bindings

## Goal

Extend the Sprint 1 memory kernel with a provider-neutral content binding layer that maps
provider-specific content IDs to WorldEcho semantic metadata (roles, capabilities, faction,
rank, superior, tags).

## Scope

- **Domain types**: `ContentKey.parse`, `BindingType`, `ContentBinding`, `BindingDiagnostic`,
  `BindingRegistry`, `EnrichedContent`
- **Config layer**: `BindingLoader` (validates `bindings.yml` from `ConfigurationSource`),
  `BindingLoadResult` (registry + diagnostics + fatal flag)
- **Application layer**: `BindingEnricher` (merges configured roles/capabilities with
  provider-supplied ones without mutation), `BindingReloadCoordinator` (pure-Java
  reload decision: fatal on reload keeps previous, fatal on startup falls back to empty)
- **Resources**: `bindings.yml` default file with example entity and item bindings
- **Paper layer**: `WorldEchoPlugin` saves/loads/reloads `bindings.yml`, exposes
  `BindingEnricher`; `WorldEchoCommand` shows binding info in `status` and `inspect`
- **Localization**: `messages_en.yml` and `messages_tr.yml` updated with binding-related keys
- **Tests**: `BindingLoaderTest` (24 cases), `BindingRegistryTest` (9 cases),
  `BindingEnricherTest` (9 cases), `BindingReloadCoordinatorTest` (8 cases),
  extended `BundledResourcesTest`

## Out of scope

- Scenario eligibility diagnostics (Sprint 0.3)
- Faction behavior, promotion, ownership transfer (Sprint 0.7)
- External content bridges (Sprint 0.4+)

## Validation

```bash
./gradlew clean test shadowJar
```

All 91 tests pass. The `shadowJar` smoke test confirms SQLite still works in the shaded JAR.

## Key fixes during implementation

- **Turkish locale bug**: `BindingLoader.parseEnumList` used `toUpperCase()` without a
  `Locale`, causing `can-fight` → `CAN_FİGHT` (dotted İ) on Turkish systems. Fixed with
  `toUpperCase(Locale.ROOT)`.
- **Plugin loadBindings**: catch block did not return early, causing a successful load to
  overwrite the error result. Fixed by moving the load inside the try block.
