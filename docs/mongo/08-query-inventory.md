# Inventario query LoL

La controparte runtime vive in MongoDB.java; non esistono store o repository intermedi.

| Area | Query/operazione MariaDB | Controparte Mongo | Consumer |
|---|---|---|---|
| identity | summoner by PUUID, legacy id, Riot ID | lol_summoners | LeagueService |
| account | account per user, user per PUUID | lol_summoners e MongoRecord | LeagueService |
| search | focused/search | regex riotSearch + region | autocomplete |
| rank | rank per queue, entries | ranks[] e name() | profile/leaderboard |
| mastery | mastery list | masteries[] | profile |
| profile | statistics read/write | lol_profile_statistics | ProfileStatisticsService |
| match | detail/existence/id | lol_matches e full Riot ID | LeagueService |
| history | all games, history, count | participant/time/queue filter | LeagueMessage |
| participant | participant update | participant flat in lol_matches | Tracker |
| events | match events | events BSON | Tracker |
| champion | match ids/source/projections | participant/match filters | Champion services |
| leaderboard | rows/count/distribution | leaderboard collections | LeaderboardService |
| migration | backfill/checkpoint | MongoMigration e lol_migration_runs | owner job |

Fuori scope: custom builds e summoner.metrics.

## Invarianti

puuid è l'identità summoner; il Riot match ID completo è l'identità match; enum R4J usa name(); bans usa BLUE e RED; participant resta flat; upsert/update/delete sono idempotenti; letture applicative Mongo-only; errori di lettura Mongo espliciti; mirror fallito significa log senza falsificare MariaDB.

Verifica: nessun riferimento a LeagueStore, MongoLeagueStore, MongoInfrastructure, MongoRecordMapper, MongoRecordCodec, LeagueWriteOutbox, LeagueDbMongoHooks o classi *Document. Sotto com.safjnest.mongo devono restare tre file Java principali.

