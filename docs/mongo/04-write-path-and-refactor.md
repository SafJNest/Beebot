# Fase 4 — write path e refactor

Ogni scrittura LoL runtime passa direttamente da MongoDB. LeagueDB non è più un writer runtime: resta disponibile solo per la lettura SQL di MongoMigration.

Sequenza:

1. validare l'input;
2. chiamare MongoDB con `_id` deterministico;
3. invalidare le cache correlate;
4. restituire il risultato del modello canonico o dell'operazione Mongo.

MongoDB espone l'errore di connessione, schema o conversione. Non esistono query MariaDB runtime, outbox, retry queue, proxy o doppio write.

## Write coperti

Account/summoner, detach, tracking, participant, match, rank ed eventi, profile statistics, mastery, rank, champion build/statistiche nel perimetro, leaderboard e tutte le altre scritture LoL effettivamente usate passano da MongoDB.

## Letture

I consumer LoL leggono da MongoDB: LeagueService, ProfileStatisticsService, LeaderboardService, BuildService, ChampionDataRefreshService, ChampionStatsService, ProfileBootstrapService, Tracker, LeagueMessage e LeagueHandler per le query migrate.

API, cache e modelli canonici restano invariati salvo aggiornamento esplicito del contratto. Custom builds e summoner.metrics non vengono copiati né ricreati da Mongo.
