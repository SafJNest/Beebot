# Documentation

- [Developer handbook](HANDBOOK.md) — repository conventions and change workflows.
- [Architecture](architecture/README.md) — ownership and accepted ADRs.
- [LoL HTTP caching analysis](architecture/lol-http-cache-analysis.md) — Cloudflare, profile refresh and cache policy by endpoint.
- [Agreed repository reorganization plan](architecture/repository-review.md) — final package layout, file moves, shared bot/API flow, exclusions and implementation sequence.
- [HTTP API](api/lol-api.md) — endpoint index and contracts.
- [MongoDB](mongo/README.md) — LoL persistence and migration.
- [Arena builds](arena-build/contracts.md) — CHERRY schema and processing.
- [Sounds API](api/beebot-sounds.md) — sounds, analytics, history and personal greet settings.

SQL files under [`database/`](../database/) serve the bot, website and Spotify
domains; LoL uses MongoDB exclusively. Audio uses prepared queries and
transactional writes in `SoundDB`, coordinated by `SoundCache`.
