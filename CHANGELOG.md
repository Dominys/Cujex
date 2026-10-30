# Changelog

All notable changes to Cujex are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Cujex versions its **public API**: the published modules `cujex-core` and
`cujex-kafka` — their public types, step definitions, JEXL functions, and
configuration properties. The `examples/` and `cujex-coverage` modules are not
published and are exempt from compatibility guarantees.

## [Unreleased]

## [1.0.0] - 2026-10-30

### Added

- **`cujex-core`** — domain-agnostic Cucumber + JEXL test infrastructure:
  - Scenario context storage for sharing state between steps.
  - Per-cell JEXL preprocessing of data tables and templates.
  - Typed data-table mapping onto arbitrary data models (records, POJOs,
    builders).
  - JSON template engine.
  - Generic creation and validation step definitions.
- **`cujex-kafka`** — opt-in Kafka extension (activated via explicit `@Import`):
  - One long-lived consumer per topic with a fan-out bus.
  - Per-scenario filtering inboxes for parallel-safe assertions.
  - Consumer-group-readiness wait and a producer. Payload-agnostic (`byte[]`).
- Maven Central publishing via tag-driven releases: pushing a `vX.Y.Z` tag
  signs and publishes `cujex-core` + `cujex-kafka` to the Central Portal.

[Unreleased]: https://github.com/Dominys/Cujex/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Dominys/Cujex/releases/tag/v1.0.0
