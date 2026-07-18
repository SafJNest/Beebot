# Fase 4 — write path e refactor

Ogni scrittura LoL mantiene MariaDB come writer compatibile e chiama MongoDB nello stesso metodo LeagueDB dopo il commit.

Sequenza:

1. validare l'input;
2. eseguire la scrittura MariaDB;
3. confermare MariaDB;
4. chiamare MongoDB con _id deterministico;
5. restituire il risultato MariaDB.

MongoDB cattura l'errore, logga operation, collection e id, e non falsifica il risultato MariaDB. Non esistono outbox, retry queue, proxy o doppio store.

## Write coperti

Account/summoner, detach, tracking, participant, match, rank ed eventi, profile statistics, mastery, rank, champion build/statistiche nel perimetro, leaderboard e tutte le altre scritture LoL effettivamente usate.

## Letture

I consumer LoL leggono da MongoDB: LeagueService, ProfileStatisticsService, LeaderboardService, BuildService, ChampionDataRefreshService, ChampionStatsService, ProfileBootstrapService, Tracker, LeagueMessage e LeagueHandler per le query migrate.

API, cache e modelli canonici restano invariati salvo aggiornamento esplicito del contratto. Custom builds e summoner.metrics non vengono copiati né ricreati da Mongo.
