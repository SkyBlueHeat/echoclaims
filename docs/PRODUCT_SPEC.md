# WorldEcho Product Specification

## One-sentence definition

WorldEcho is a Paper gameplay plugin that connects a server's existing custom mobs, items, NPCs, factions, and player behavior into persistent, branching stories.

## Product promise

**We do not create the server's content. We make its content remember one another.**

A custom boss should not only respawn at coordinates. It can become:

- the successor of a defeated commander
- the owner of a weapon lost by a player
- the enemy of a particular player
- the leader of a faction that grew because players ignored it
- an entry in the permanent history of the server

## Player return motivation

WorldEcho must create curiosity, not fear of missing out:

> “What did yesterday's actions become today?”

There are no mandatory login streaks. Returning players receive a comprehensible summary of developments that occurred while they were away.

## Core gameplay loop

1. The player performs meaningful actions.
2. WorldEcho records causes, actors, items, regions, and relationships.
3. Compatible scenario candidates are generated.
4. A scenario becomes an active story instance.
5. The player receives a rumor, journal entry, request, threat, or discovery.
6. The player chooses or acts.
7. Consequences happen immediately, after Minecraft time, or on a later login.
8. The result becomes part of item history, region memory, faction state, and world history.

## Representative scenario: stolen weapon enters a command chain

Initial server content:

- MythicMob `GoblinSoldier`
- MythicMob `GoblinCaptain`
- MythicMob `GoblinKing`
- Oraxen item `flame_sword`
- faction `goblin_clan`

Cause:

- A Goblin Soldier kills a player.
- The player's Flame Sword drops.

Possible WorldEcho story:

1. The soldier is identified as a low-rank goblin capable of carrying items and being promoted.
2. The Flame Sword is identified as a transferable legendary-candidate weapon.
3. The soldier claims the weapon or reports the kill.
4. The weapon is delivered to the nearest or configured Goblin Captain.
5. The captain becomes the weapon's owner.
6. The captain gains status, generates a name, and becomes the player's rival.
7. The player receives a rumor about where the captain was seen.
8. If ignored, the captain may unite camps, deliver the sword to the king, overthrow the king, sell it, become corrupted, or raid the player's region.
9. If another player kills the captain, that player's action becomes part of the same story.
10. The weapon preserves its ownership and event history.

## Content sources

WorldEcho should eventually support:

- Vanilla Bukkit/Paper entities and items
- MythicMobs
- Oraxen
- ItemsAdder
- Citizens
- ModelEngine
- PlaceholderAPI
- Vault
- protection plugins
- third-party integrations through a public WorldEcho API

All integrations are optional.

## Provider-neutral semantic model

External content is mapped into stable WorldEcho concepts.

### Entity roles

Examples:

- soldier
- captain
- commander
- monarch
- merchant
- blacksmith
- healer
- bandit
- animal
- monster
- civilian
- witness
- heir
- rival

### Item roles

Examples:

- weapon
- armor
- relic
- legendary-candidate
- faction-symbol
- royal-seal
- quest-object
- heirloom
- corrupting
- fire
- ice

### Capabilities

Examples:

- can-fight
- can-speak
- can-hold-items
- can-own-items
- can-be-promoted
- can-command-units
- can-lead-faction
- can-raid-settlements
- can-trade
- can-create-rivalry
- can-be-corrupted
- can-have-history
- can-change-owner
- can-be-stolen
- can-be-lost
- can-become-heirloom

Scenarios declare required roles and capabilities. Incompatible combinations are rejected before story creation.

## Major systems

### 1. Memory recorder

Captures meaningful player, item, entity, NPC, faction, and region events.

It must avoid recording meaningless high-frequency noise.

### 2. Content bridge layer

Identifies provider-specific objects and converts them into WorldEcho content references.

### 3. Content registry and binding system

Allows admins to map custom content IDs to semantic roles, capabilities, factions, ranks, and hierarchy.

### 4. Relationship graph

Stores typed relationships such as:

- killed
- defeated-by
- owns
- stole
- delivered-to
- commands
- belongs-to
- rivals
- trusts
- fears
- rescued
- betrayed
- inherited-from

### 5. Item chronicle

Tracks stable item identity, ownership, kills, significant battles, losses, reforges, corruption, and inheritance.

### 6. Faction and command-chain engine

Tracks ranks, superiors, successors, internal rivalries, resources, territory influence, and promotions.

### 7. Scenario engine

Builds stories from:

```text
cause
+ region
+ actors
+ content
+ relationships
+ threat or opportunity
+ player decision
+ consequence
+ future hook
```

### 8. Delayed consequence scheduler

Processes consequences based on:

- elapsed real time
- elapsed Minecraft time
- next player login
- faction or region conditions
- a related event occurring

### 9. Rumor and chronicle system

Communicates stories through vanilla UI:

- chat
- books
- inventory menus
- titles
- action bars
- boss bars
- NPC dialogue integrations
- PlaceholderAPI output

### 10. World history

Stores important events that become the shared history of the server.

## Non-goals for the core plugin

WorldEcho does not need to provide:

- custom models
- custom textures
- new client sounds
- a client mod
- a custom launcher
- a replacement for MythicMobs
- a replacement for ItemsAdder/Oraxen
- a replacement for Citizens
- forced world resets
- daily login punishments

## Experience principles

1. Causes and consequences must be understandable.
2. Stories should emerge from player behavior.
3. Existing server content must become more valuable.
4. Players should be able to influence stories, not only observe them.
5. Ignoring an event is a valid decision with consequences.
6. No single story should permanently destroy player builds by default.
7. The system should work in vanilla mode and become richer with integrations.
8. Admins must retain control over frequency, severity, and eligible content.
