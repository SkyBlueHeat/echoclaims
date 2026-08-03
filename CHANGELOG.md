# Changelog

All notable changes to EchoClaims will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/).

## [0.1.0-SNAPSHOT] - 2025-08-03

### Added
- EchoClaims plugin bootstrap from WorldEcho infrastructure.
- Plugin lifecycle (`EchoClaimsPlugin`) with startup and shutdown.
- SQLite initialization with idempotent schema migration (v1: audit_records table).
- Asynchronous bounded write queue (`AuditWriteQueue`) with batch writes and drop accounting.
- Clean queue shutdown with configurable timeout and drain behavior.
- Provider-neutral content identification (`IntegrationRegistry`, `ProviderRegistry`).
- Vanilla fallback providers for items and entities.
- Bukkit item mapping (`BukkitItems`) to immutable `ItemDescriptor` records.
- Deterministic item value scoring (`ItemValueScorer`) with configurable weights.
- `/echoclaims status` command (alias: `/ec status`) reporting version, database,
  queue state, providers, and uptime.
- Configuration validation with safe defaults and warning logging.
- Locale-aware message catalog with MiniMessage rendering (en, tr).
- Audit record domain model and SQLite repository.
- Empty domain areas prepared for incidents, inventory snapshots, claims, and refunds.
- Tests for settings loader, schema migrator, write queue, provider registry,
  item scorer, status service, audit repository, and bundled resources.
- Documentation: README, ARCHITECTURE, BOOTSTRAP_FROM_WORLDECHO, DEVELOPMENT.

### Removed
- All WorldEcho-specific story, memory, narrative, eligibility, binding, and
  scenario logic.
- WorldEcho-specific database tables (story_events, tracked_items, ownership
  ledgers, lot tables, lineage).
- WorldEcho-specific commands (recent, inspect, reload, eligibility, item).
- WorldEcho-specific permissions and configuration paths.
- bindings.yml resource file.
