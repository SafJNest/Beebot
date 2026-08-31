# Documentation index

- [Developer Handbook](HANDBOOK.md) — **start here** for new command / endpoint / service / model / queue / mongo / cache
- [Repository rules and programming style](../AGENTS.md)
- [LoL architecture](architecture/README.md) — source of truth, ADR index, package layout
- [LoL HTTP API](api/lol-api.md) — index by scope and endpoint reference
- [LoL queues (walkthrough)](new-queue.md) — `QueueHandler` / `RiotScheduler` / `ComputeScheduler` / `SyncScheduler`
- [Profile statistics source of truth](architecture/profile-statistics-source-of-truth.md) — `PUUID + filterKey`, cache, Mongo, OTP
- [MongoDB LoL migration](mongo/README.md) — operational status, BSON rules, indexes, backfill
- [LoL/Mongo flow audits](audit/README.md) — verified flows (historical, see status in HANDBOOK)
- [Agent workflow](agents/README.md) — roles and macro-task order
- [SQL structure](sql/README.md) — only for `LeagueDB` backfill adapter
