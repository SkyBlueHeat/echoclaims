# Sprint 0.2B — Scenario Eligibility Diagnostics

## Summary

Sprint 0.2B adds a pure-Java scenario eligibility diagnostic system that evaluates
bound or enriched content against named requirement profiles. It answers:

> Is this configured entity or item semantically ready to participate in a future
> WorldEcho story role?

> If it is not ready, exactly which requirements are missing?

These results are **diagnostics only**. No story is started, scheduled, persisted,
or executed.

## Scope

- **Domain types**: `EligibilityDiagnosticCode`, `EligibilityDiagnostic`,
  `EligibilityStatus`, `EligibilityResult`, `EligibilityProfile`, `EligibilityCatalog`
- **Evaluator**: `EligibilityEvaluator` evaluates `ContentBinding` or `EnrichedContent`
  against profiles from the catalog
- **Formatter**: `EligibilityFormatter` produces console-friendly output lines
- **Paper layer**: `/worldecho eligibility profiles|check|all` commands with tab
  completion; `/worldecho status` shows profile counts; `/worldecho inspect` shows
  concise eligibility summary
- **Localization**: English and Turkish messages for all eligibility commands
- **Tests**: `EligibilityCatalogTest` (9 cases), `EligibilityEvaluatorTest` (22 cases),
  `EligibilityFormatterTest` (8 cases), extended `BundledResourcesTest`

## Built-in profiles

| Profile ID | Type | Required capabilities | Required metadata |
|---|---|---|---|
| `item-carrier` | entity | `CAN_HOLD_ITEMS` | — |
| `promotion-candidate` | entity | `CAN_BE_PROMOTED` | faction, rank |
| `combat-story-actor` | entity | `CAN_FIGHT` | — |
| `transferable-story-item` | item | `CAN_CHANGE_OWNER`, `CAN_HAVE_HISTORY` | — |

Profiles are **diagnostics, not executable scenarios**. They describe what semantic
metadata a content binding must have to be considered ready for a future story role.

## Diagnostic codes

| Code | Meaning |
|---|---|
| `ELIGIBLE` | All requirements satisfied |
| `NO_BINDING` | No binding found for the content key |
| `PROFILE_NOT_FOUND` | Unknown profile ID |
| `WRONG_BINDING_TYPE` | Entity binding evaluated against item profile or vice versa |
| `MISSING_ROLE` | A required semantic role is absent |
| `MISSING_CAPABILITY` | A required capability is absent |
| `MISSING_FACTION` | Faction metadata is required but absent or blank |
| `MISSING_RANK` | Rank metadata is required but absent or blank |
| `MISSING_SUPERIOR` | Superior metadata is required but absent |
| `MISSING_TAG` | A required tag is absent |

## Command syntax

```
/worldecho eligibility profiles
/worldecho eligibility check <entity|item> <content-key> <profile>
/worldecho eligibility all <entity|item> <content-key>
```

Examples:

```
worldecho eligibility profiles
worldecho eligibility check entity minecraft:zombie combat-story-actor
worldecho eligibility all entity minecraft:zombie
```

All commands work from the Paper console without a Minecraft client.

## Reload integration

Eligibility commands read from the active immutable `BindingRegistry` published by
the binding reload coordinator. After `/worldecho reload`:

- eligibility commands immediately use the new active registry
- no plugin restart is required
- a failed binding reload preserves the previous registry and eligibility results

## Turkish locale safety

All profile ID and enum normalization uses `Locale.ROOT` to avoid Turkish locale
issues (e.g. `i` → `İ` instead of `I`). Regression tests temporarily set the default
locale to Turkish and verify deterministic behavior.

## Out of scope

- No story starts in Sprint 0.2B
- No scenario instance is persisted
- No faction behavior exists
- No promotion occurs
- No item ownership changes
- No external bridge exists
- Profiles are currently built into the application
- YAML scenario definitions are deferred to Sprint 0.3

## Validation

```bash
./gradlew clean test shadowJar
```

All 139 tests pass. `shadowJarSmokeTest` passes.
