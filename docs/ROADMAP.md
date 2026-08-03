# WorldEcho Roadmap

## 0.1 — First Memory

- reliable memory kernel
- provider-neutral content identity
- SQLite persistence
- player death and valuable-loot capture
- admin inspection tools
- vanilla fallback

## 0.2 — Content Bindings ✅

- ✅ validated `bindings.yml`
- ✅ semantic roles and capabilities
- ✅ factions, ranks, and superior mappings
- ✅ content scan and validation commands
- ✅ scenario eligibility diagnostics (Sprint 0.2B)

## 0.3A — Persistent Item Identity & Ownership Ledger ✅

- ✅ stable item UUID and ownership history
- ✅ PDC-based persistent item identity
- ✅ append-only ownership ledger

## 0.3B — Automatic Player Inventory Identity & Ownership Synchronization ✅

- ✅ automatic UNIQUE/LOT identity classification
- ✅ automatic PDC assignment for player inventories
- ✅ lot fingerprinting and persistence
- ✅ coalescing inventory reconciliation scheduler
- ✅ event-driven reconciliation (join, respawn, click, drag, pickup, death, drop, world change, crafting, furnace, fishing, offhand swap)
- ✅ duplicate observation detection
- ✅ transformation identity continuity: anvil, smithing, grindstone

## 0.3C — World Drops, Containers & Entity Ownership

- persistent dropped Item entity registry
- WORLD_DROP ownership tracking
- ENTITY ownership for mob equipment
- container ownership (chests, hoppers, barrels)
- shulker box and bundle nested content tracking
- hopper tracking
- container crawling

## 0.3D — First Story — Stolen Item Carrier

- `stolen_item_carrier`
- `stolen_relic_command_chain`
- generated named rival
- delayed rumor
- recovery after restart

## 0.4 — MythicMobs Bridge

- MythicMob identity
- spawn and lookup
- faction/rank binding
- boss and mob event adaptation
- bridge health diagnostics

## 0.5 — Custom Items

- Oraxen bridge
- ItemsAdder bridge
- custom-item identity and recreation
- item lineage and ownership transitions
- lost relic recovery stories

## 0.6 — NPC Memory

- Citizens bridge
- NPC traits and relationships
- rescue, betrayal, succession, and profession stories
- NPC-driven rumors

## 0.7 — Factions and Command Chains

- promotion and succession
- captains, commanders, monarchs
- faction resources and hostility
- raids and internal conflict
- player reputation per faction

## 0.8 — Living Regions

- aggregated region metrics
- roads and routes
- settlements
- regional events
- protection-plugin integration

## 0.9 — Chronicle

- player chronicle
- world history
- return summaries
- PlaceholderAPI
- localized messages
- configurable inventory/book interfaces

## 1.0 — Integration Story Engine

- stable public WorldEcho API
- scenario YAML validation
- hundreds of compatible combinations
- integration SDK and testkit
- migration tooling
- performance profiling
- CurseForge, Modrinth, and Hangar release pipeline
