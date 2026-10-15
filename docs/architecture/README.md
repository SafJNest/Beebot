# LoL architecture

This directory contains accepted architecture decisions for the LoL domain.
Repository-wide implementation rules live in [`AGENTS.md`](../../AGENTS.md);
operational steps live in the [developer handbook](../HANDBOOK.md).

## Ownership

- `lol.model` owns canonical LoL success models.
- `lol.service` owns domain reads, writes, cache coordination and composition.
- `lol.queue` owns job registration and scheduling; `lol.tracker` owns periodic
  tracking.
- `nosql.MongoDB` owns Mongo persistence. MariaDB is used only by
  `MongoMigration`.
- Spring owns HTTP controllers, configuration and error representations.
- Redis stores disposable caches and derived indexes.

The current scheduler contract is [ADR-0014](adr/0014-global-job-scheduler.md).
The profile aggregate identity and ownership are summarized in
[`profile-statistics-source-of-truth.md`](profile-statistics-source-of-truth.md).
Mongo persistence details are in [`docs/mongo`](../mongo/README.md); CHERRY
build ownership is in [`docs/arena-build`](../arena-build/README.md).

## Accepted ADRs

- [0001 — Canonical LoL model boundaries](adr/0001-canonical-lol-model-boundaries.md)
- [0002 — Summoner view and leaderboard contract](adr/0002-summoner-view-and-leaderboard-contract.md)
- [0003 — Match and match-result models](adr/0003-match-and-match-result-models.md)
- [0005 — LoL API JSON contract](adr/0005-lol-api-json-contract.md)
- [0006 — Champion API contract](adr/0006-champion-api-contract.md)
- [0007 — Unified API result and parameters](adr/0007-unified-api-result-and-parameters.md)
- [0008 — Endpoint cache and asynchronous lookups](adr/0008-endpoint-cache-and-async-lookups.md)
- [0009 — Mongo persistence and migration](adr/0009-mongo-persistence-and-migration.md)
- [0010 — Database refresh queue](adr/0010-database-refresh-queue.md), superseded queue terminology
- [0011 — Domain services and R4J queue](adr/0011-domain-services-and-r4j-queue.md), superseded ownership clauses
- [0012 — Profile and champion analysis facades](adr/0012-profile-and-champion-analysis-facades.md)
- [0013 — Champion tier-list projection](adr/0013-champion-tier-list.md)
- [0014 — Global job scheduler](adr/0014-global-job-scheduler.md), current queue contract
- [0015 — Contextual ranking projections](adr/0015-contextual-ranking.md)
