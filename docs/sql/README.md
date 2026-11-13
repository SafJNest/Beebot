# SQL databases

SQL files under [`database/`](../../database/) and the remaining SQL adapters
serve the bot, website and Spotify domains. LoL persistence and maintenance
use MongoDB exclusively; the legacy LoL SQL adapter, projection and historical
import commands have been removed. See the [MongoDB documentation](../mongo/README.md).

Audio uses prepared queries and transactional writes in `SoundDB`, coordinated by `SoundCache`. See [the Sounds API](../api/beebot-sounds.md) and [history/backfill](../sound-history.md) for schema prerequisites and rollout.
