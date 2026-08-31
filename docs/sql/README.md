# SQL structure

Repository SQL scripts are organized by database in [`database/`](../../database/). Each file in a database's top-level folder describes one table; changes to existing databases are collected in `migrations/` folders.

## Index

### League of Legends

| Table | Definition | Primary key | Main relationships |
|---|---|---|---|
| `champion` | [`champion.sql`](../../database/league_of_legends/champion.sql) | `id` | Catalogo campioni |
| `champion_builds` | [`champion_builds.sql`](../../database/league_of_legends/champion_builds.sql) | `id` | Build aggregate filtrate |
| `champion_stats` | [`champion_stats.sql`](../../database/league_of_legends/champion_stats.sql) | `id` | Statistiche aggregate per campione e filtro |
| `custom_build` | [`custom_build.sql`](../../database/league_of_legends/custom_build.sql) | `id` | Build create dagli utenti; `user_id` identifica l'utente applicativo |
| `leaderboard_distribution` | [`leaderboard_distribution.sql`](../../database/league_of_legends/leaderboard_distribution.sql) | `queue`, `rank`, `region` | Aggregati della leaderboard ricostruiti dal job bulk |
| `masteries` | [`masteries.sql`](../../database/league_of_legends/masteries.sql) | `id` | `summoner_id` → `summoner.id` |
| `match` | [`match.sql`](../../database/league_of_legends/match.sql) | `id` | Match persistiti; unico su `game_id`, `region` |
| `metrics` | [`metrics.sql`](../../database/league_of_legends/metrics.sql) | `id` | Metriche aggregate per shard e tipo |
| `participant` | [`participant.sql`](../../database/league_of_legends/participant.sql) | `id` | `match_id` → `match.id`; `summoner_id` → `summoner.id` |
| `profile_statistics` | [`profile_statistics.sql`](../../database/league_of_legends/profile_statistics.sql) | `key` | `summoner_id` → `summoner.id` |
| `rank` | [`rank.sql`](../../database/league_of_legends/rank.sql) | `id` | `summoner_id` → `summoner.id`; unique per `summoner_id`, `queue` |
| `summoner` | [`summoner.sql`](../../database/league_of_legends/summoner.sql) | `id` | LoL identity and region; `riot_search` is generated |
| `summoner_metric` | [`summoner_metric.sql`](../../database/league_of_legends/summoner_metric.sql) | `id` | `summoner_id` → `summoner.id`; unique per campione |

### Main relationships

```text
summoner
├── rank
├── masteries
├── participant ─── match
├── profile_statistics
└── summoner_metric

champion
├── masteries.champion_id
├── participant.champion
└── summoner_metric.champion
```

Columns representing a champion or a region remain application values and have no foreign key to `champion` or a region table. This keeps compatibility with existing Riot imports.

### Berbit

| Table | Definition |
|---|---|
| `blacklist` | [`blacklist.sql`](../../database/berbit/blacklist.sql) |
| `command` | [`command.sql`](../../database/berbit/command.sql) |
| `experience` | [`experience.sql`](../../database/berbit/experience.sql) |
| `twitch_subscription` | [`twitch_subscription.sql`](../../database/berbit/twitch_subscription.sql) |
| `setting/Channel` | [`Channel.sql`](../../database/berbit/setting/Channel.sql) |
| `setting/guild` | [`guild.sql`](../../database/berbit/setting/guild.sql) |
| `setting/user` | [`user.sql`](../../database/berbit/setting/user.sql) |
| `sound/greeting` | [`greeting.sql`](../../database/berbit/sound/greeting.sql) |
| `sound/sound` | [`sound.sql`](../../database/berbit/sound/sound.sql) |
| `sound/sound_history` | [`sound_history.sql`](../../database/berbit/sound/sound_history.sql) |
| `sound/sound_interactions` | [`sound_interactions.sql`](../../database/berbit/sound/sound_interactions.sql) |
| `sound/tag` | [`tag.sql`](../../database/berbit/sound/tag.sql) |
| `sound/tag_sounds` | [`tag_sounds.sql`](../../database/berbit/sound/tag_sounds.sql) |
| `warning/automated_action` | [`automated_action.sql`](../../database/berbit/warning/automated_action.sql) |
| `warning/automated_action_expiration` | [`automated_action_expiration.sql`](../../database/berbit/warning/automated_action_expiration.sql) |
| `warning/warning` | [`warning.sql`](../../database/berbit/warning/warning.sql) |

### Spotify

| Table | Definition |
|---|---|
| `album_artists` | [`album_artists.sql`](../../database/spotify/album_artists.sql) |
| `albums` | [`albums.sql`](../../database/spotify/albums.sql) |
| `artists` | [`artists.sql`](../../database/spotify/artists.sql) |
| `spotifyDB` | [`spotifyDB.sql`](../../database/spotify/spotifyDB.sql) |
| `tracks` | [`tracks.sql`](../../database/spotify/tracks.sql) |
| `tracks_streamings` | [`tracks_streamings.sql`](../../database/spotify/tracks_streamings.sql) |
| `users` | [`users.sql`](../../database/spotify/users.sql) |

### Website

| Table | Definition |
|---|---|
| `ApiKey` | [`ApiKey.sql`](../../database/website/ApiKey.sql) |
| `DiscordToken` | [`DiscordToken.sql`](../../database/website/DiscordToken.sql) |
| `User` | [`User.sql`](../../database/website/User.sql) |
| `UserSession` | [`UserSession.sql`](../../database/website/UserSession.sql) |

## Leaderboard

`leaderboard_distribution` is a historical derived table in the SQL database. The LoL Mongo runtime neither reads nor updates it: leaderboard and distributions are computed directly from `summoner.ranks{}` in Mongo. The definition is kept for SQL installations and historical migration context.

There is no dedicated migration for `leaderboard_distribution` in the repository: for existing SQL installations use the base definition [`leaderboard_distribution.sql`](../../database/league_of_legends/leaderboard_distribution.sql), while later migrations concern `rank` indexes and normalization.

Paginated leaderboard queries order by `rank.mmr` without additional tie-breakers. Migration [`0002-rank-leaderboard-filter.sql`](../../database/league_of_legends/migrations/0002-rank-leaderboard-filter.sql) contains the previous rank/LP index; [`0003-rank-mmr-filter.sql`](../../database/league_of_legends/migrations/0003-rank-mmr-filter.sql) replaces it with the MMR-based index. Migration [`0001-rank-mmr.sql`](../../database/league_of_legends/migrations/0001-rank-mmr.sql) adds the column and global/regional indexes. Migration [`0004-rank-canonical-mmr.sql`](../../database/league_of_legends/migrations/0004-rank-canonical-mmr.sql) normalizes queue to `RANKED_SOLO_5X5` only, removes legacy duplicates and creates minimal indexes for each filter combination.

## Operational rules

- Base definitions are aligned with the reference `SHOW CREATE TABLE` dump.
- Do not add DML or rebuilds in table definitions.
- Migrations must be versioned and separate from base scripts.
- After changing a table, update this index and verify PK, unique key, indexes and foreign keys.
- `rank` and `match` are SQL identifiers to quote in application queries.
