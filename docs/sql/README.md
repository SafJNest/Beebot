# SQL structure

Gli script SQL del repository sono organizzati per database in [`database/`](../../database/). Ogni file nella cartella principale di un database descrive una tabella; le modifiche a database già esistenti sono raccolte nelle cartelle `migrations/`.

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
| `summoner` | [`summoner.sql`](../../database/league_of_legends/summoner.sql) | `id` | Identità LoL e regione; `riot_search` è generata |
| `summoner_metric` | [`summoner_metric.sql`](../../database/league_of_legends/summoner_metric.sql) | `id` | `summoner_id` → `summoner.id`; unique per campione |

### Relazioni principali

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

Le colonne che rappresentano un campione o una regione restano valori applicativi e non hanno foreign key verso `champion` o una tabella regioni. Questo mantiene compatibilità con gli import Riot esistenti.

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

`leaderboard_distribution` è una tabella derivata. La definizione contiene solo schema, chiavi e indice temporale. Il contenuto viene ricostruito da `rank` e `summoner` tramite `LeaderboardService.rebuildDistribution()` e aggiornato dal job giornaliero di `TrackerScheduler`. Il rebuild inserisce anche le combinazioni senza player con conteggio `0` e sostituisce lo snapshot in una transazione.

Non è presente una migration dedicata a `leaderboard_distribution` nel repository: per installazioni esistenti si usa la definizione base [`leaderboard_distribution.sql`](../../database/league_of_legends/leaderboard_distribution.sql), mentre le migration successive riguardano gli indici e la normalizzazione di `rank`.

Le query paginated della leaderboard usano `rank.mmr` per l'ordinamento, senza tie-breaker aggiuntivi. La migration [`0002-rank-leaderboard-filter.sql`](../../database/league_of_legends/migrations/0002-rank-leaderboard-filter.sql) contiene l'indice precedente su rank e LP; [`0003-rank-mmr-filter.sql`](../../database/league_of_legends/migrations/0003-rank-mmr-filter.sql) lo sostituisce con l'indice basato su MMR. La migration [`0001-rank-mmr.sql`](../../database/league_of_legends/migrations/0001-rank-mmr.sql) aggiunge la colonna e gli indici globali/regionali. La migration [`0004-rank-canonical-mmr.sql`](../../database/league_of_legends/migrations/0004-rank-canonical-mmr.sql) normalizza la queue solo a `RANKED_SOLO_5X5`, rimuove i duplicati legacy e crea gli indici minimi per ogni combinazione di filtro.

## Regole operative

- Le definizioni base sono allineate al dump `SHOW CREATE TABLE` di riferimento.
- Non aggiungere DML o rebuild nelle definizioni delle tabelle.
- Le migration devono essere versionate e separate dagli script base.
- Dopo modifiche a una tabella, aggiornare questo indice e verificare PK, unique key, indici e foreign key.
- `rank` e `match` sono identificatori SQL da quotare nelle query applicative.
