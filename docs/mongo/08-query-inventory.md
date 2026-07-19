# Inventario query LoL

La controparte runtime vive in MongoDB.java; non esistono store o repository intermedi.

| Area | Query/operazione MariaDB | Controparte Mongo | Consumer |
|---|---|---|---|
| identity | summoner by PUUID, Riot ID | `_id = puuid` in `summoner` | LeagueService |
| account | account per user, user per PUUID | summoner e MongoRecord | LeagueService |
| search | focused/search | regex riotSearch + region | autocomplete |
| rank | rank per queue, entries | ranks[] e name() | profile/leaderboard |
| mastery | mastery list | masteries[] | profile |
| profile | statistics read/write | profile_statistics | ProfileStatisticsService, non migrata |
| match | detail/existence/id | match e full Riot ID | LeagueService |
| history | all games, history, count | participant/time/queue filter | LeagueMessage |
| participant | participant update | participant flat in match | Tracker |
| events | match events | `match_events` con Binary `zstd-json` | Tracker, match detail/history |
| champion | match ids/source/projections | participant/match filters | Champion services |
| leaderboard | rows/count/distribution | leaderboard collections | LeaderboardService |
| migration | backfill/checkpoint | MongoMigration e migration_runs | owner job |

Fuori scope: custom builds e summoner.metrics.

## Invarianti

puuid è l'identità summoner e `_id` del documento; il Riot match ID completo è l'identità match; enum R4J usa name(); bans usa BLUE e RED; participant resta flat; upsert/update/delete sono idempotenti; letture applicative Mongo-only; errori di lettura Mongo espliciti; mirror fallito significa log senza falsificare MariaDB.

Verifica: nessun riferimento a LeagueStore, MongoLeagueStore, MongoInfrastructure, MongoRecordMapper, MongoRecordCodec, LeagueWriteOutbox, LeagueDbMongoHooks o classi *Document. Sotto com.safjnest.mongo devono restare tre file Java principali.
