# Configuration

Every value in `config.yml` is validated on load. An invalid value never prevents the
plugin from starting: it is replaced by the default documented here and reported as a
warning in the server log and in `/worldecho reload` output.

Files live in `plugins/WorldEcho/`:

| File | Purpose |
| --- | --- |
| `config.yml` | Capture rules, item scoring, persistence, command limits |
| `bindings.yml` | Content bindings: semantic roles, capabilities, faction, rank, tags |
| `messages_en.yml` | English messages (also the fallback for missing keys) |
| `messages_tr.yml` | Turkish messages |
| `worldecho.db` | SQLite database (created automatically) |

## Top level

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `locale` | `en` or `tr` | `en` | Which message file is used. Unknown values fall back to `en`. |

## capture.player-deaths

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `enabled` | boolean | `true` | Record "a living entity killed a player" memories. |
| `record-without-valuable-item` | boolean | `true` | Store the memory even when no drop reaches `minimum-item-score`. |
| `minimum-item-score` | 0–10000 | `25` | Lowest score a drop needs before it can become the memory's item. |
| `redact-custom-item-names` | boolean | `false` | Replace custom item names with an empty value in stored snapshots. |
| `ignored-worlds` | list of strings | `[]` | Worlds that never produce memories. Matched case-insensitively. |

A death is ignored when the damage source has no living causing entity, for example a fall
or lava death. Projectiles are attributed to their shooter.

## scoring

Item scoring is deterministic: the same item always produces the same score and the same
explanation, which `/worldecho inspect item` prints factor by factor.

```text
score = material
      + (enchantment-base + enchantment-per-level * level) per enchantment
      + custom-name-bonus       (only when the item has a custom name)
      + unbreakable-bonus       (only when the item is unbreakable)
      + provider-identified-bonus (only when a non-vanilla provider claimed the item)
      + min(amount-bonus-cap, amount - 1)
      - round(wear * wear-penalty-cap)
```

The result is clamped to a minimum of `0`.

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `material-scores` | map | see `config.yml` | Exact material paths, for example `elytra: 55`. Wins over `material-tiers`. |
| `material-tiers` | map | see `config.yml` | Material path prefixes, for example `netherite_: 60`. Longest match wins. |
| `default-material-score` | 0–10000 | `1` | Used when no exact score and no tier matches. |
| `enchantment-base` | 0–10000 | `4` | Points for each enchantment. |
| `enchantment-per-level` | 0–10000 | `2` | Extra points per enchantment level. |
| `custom-name-bonus` | 0–10000 | `8` | Applied once for a renamed item. |
| `unbreakable-bonus` | 0–10000 | `12` | Applied once for an unbreakable item. |
| `provider-identified-bonus` | 0–10000 | `10` | Applied when a bridge such as Oraxen claimed the item. |
| `amount-bonus-cap` | 0–10000 | `5` | Maximum bonus from stack size. |
| `wear-penalty-cap` | 0–10000 | `10` | Maximum penalty for a fully damaged item. |

Custom provider items are **not** automatically valuable: they receive one bonus and are
still scored on their material, enchantments, and condition.

## persistence

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `sqlite-file` | string | `worldecho.db` | Database file inside the plugin data folder. |
| `shutdown-timeout-seconds` | 1–300 | `10` | How long `onDisable` waits for the writer thread to flush. |
| `write-queue-capacity` | 16–100000 | `2000` | Queued events before new ones are dropped and logged. |
| `write-batch-size` | 1–1000 | `64` | Events written per transaction. |

The queue is bounded on purpose: when the disk cannot keep up, WorldEcho drops the newest
events and reports the count in `/worldecho status` instead of stalling the server thread.

## commands

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `recent-default-count` | 1–1000 | `10` | Rows returned by `/worldecho recent` without an argument. |
| `recent-maximum-count` | 1–1000 | `50` | Hard cap for `/worldecho recent <count>`. |

## Messages

Message files use [MiniMessage](https://docs.advntr.dev/minimessage/format.html) tags and
`{placeholder}` tokens. Placeholder values are sanitized before substitution, so a custom
item name cannot inject formatting into an admin message.

`messages_en.yml` is always loaded as the fallback catalog, so a partially translated
`messages_tr.yml` still produces readable output; missing keys are shown as
`Missing message: <key>` instead of failing silently.

## Reloading

`/worldecho reload` re-reads `config.yml`, `bindings.yml`, and the message files. The
database connection, the writer thread, and the provider registry keep running, so a
reload can never lose queued memories. Changing `persistence.*` therefore requires a
server restart.

## items

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `identity.enabled` | boolean | `true` | Whether the item identity system is active. When `false`, `/worldecho item track` refuses to assign new IDs. |
| `history.default-limit` | 1–1000 | `10` | Default number of ownership history entries shown by `/worldecho item history` when no count is given. |
| `history.maximum-limit` | 1–1000 | `50` | Maximum number of ownership history entries that can be requested in a single command. |

## items.automatic-tracking

WorldEcho automatically identifies and records items entering active player inventories.
Normal players and server administrators are not expected to manually track items.

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `enabled` | boolean | `true` | Master switch for automatic item tracking. When `false`, no automatic identity assignment or ownership reconciliation occurs. |
| `player-inventories` | boolean | `true` | Track items in player inventories (main, hotbar, armor, offhand). |
| `reconcile-on-join` | boolean | `true` | Schedule inventory reconciliation when a player joins. |
| `reconcile-on-respawn` | boolean | `true` | Schedule inventory reconciliation when a player respawns. |
| `reconcile-after-inventory-events` | boolean | `true` | Schedule reconciliation after inventory click, drag, pickup, drop, crafting, furnace extract, offhand swap, and fishing events. |
| `transform-identity-continuity` | boolean | `true` | Preserve WorldEcho UUID across anvil, smithing table, and grindstone transformations. |
| `debug-messages` | boolean | `false` | Show debug messages for automatic tracking (admin only, console only). |

When automatic tracking is disabled, manual commands (`/worldecho item track`, `/worldecho item assign-owner`) remain available as diagnostic and repair tools.

## Content bindings (`bindings.yml`)

Bindings map provider-specific content IDs to WorldEcho semantic metadata. A binding
**enriches** — never replaces — the roles and capabilities a provider supplies.

### Schema

| Key | Type | Default | Meaning |
| --- | --- | --- | --- |
| `version` | integer | required | Schema version. Currently `1`. |
| `bindings.entities` | section | `{}` | Entity bindings keyed by `provider:contentId`. |
| `bindings.items` | section | `{}` | Item bindings keyed by `provider:contentId`. |

### Binding fields

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `roles` | list of strings | `[]` | Semantic roles to add. Accepts kebab-case or SCREAMING_SNAKE_CASE. |
| `capabilities` | list of strings | `[]` | Capabilities to add. Same format as roles. |
| `faction` | string | absent | Faction identifier (metadata only in Sprint 0.2A). |
| `rank` | string | absent | Rank identifier (metadata only in Sprint 0.2A). |
| `superior` | string | absent | Content key of a superior entity (metadata only). |
| `tags` | list of strings | `[]` | Free-form string tags. |

Valid roles: `PLAYER`, `SOLDIER`, `CAPTAIN`, `COMMANDER`, `MONARCH`, `MERCHANT`,
`BLACKSMITH`, `HEALER`, `BANDIT`, `ANIMAL`, `MONSTER`, `CIVILIAN`, `WITNESS`, `HEIR`,
`RIVAL`, `WEAPON`, `ARMOR`, `RELIC`, `LEGENDARY_CANDIDATE`, `FACTION_SYMBOL`,
`QUEST_OBJECT`, `HEIRLOOM`, `FIRE`, `ICE`.

Valid capabilities: `CAN_FIGHT`, `CAN_SPEAK`, `CAN_HOLD_ITEMS`, `CAN_OWN_ITEMS`,
`CAN_BE_PROMOTED`, `CAN_COMMAND_UNITS`, `CAN_LEAD_FACTION`, `CAN_RAID_SETTLEMENTS`,
`CAN_TRADE`, `CAN_CREATE_RIVALRY`, `CAN_BE_CORRUPTED`, `CAN_HAVE_HISTORY`,
`CAN_CHANGE_OWNER`, `CAN_BE_STOLEN`, `CAN_BE_LOST`, `CAN_BECOME_HEIRLOOM`.

Unknown tokens produce a warning and are excluded but do not prevent loading. A binding
for an unavailable provider is valid; it becomes useful when the provider later identifies
that key. Entity and item bindings use separate namespaces, so the same content key may
appear in both sections.

`faction`, `rank`, `superior`, and `tags` do **not** activate faction behavior,
promotion, ownership transfer, or story execution. They are stored for future sprints.
