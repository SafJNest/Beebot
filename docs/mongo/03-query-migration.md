# Fase 3 — query MariaDB → MongoDB

Ogni query LoL usata dal runtime deve avere una controparte in MongoDB.java. Le query MariaDB rimaste in LeagueDB sono esclusivamente sorgenti del backfill MongoMigration.

## Ownership

MongoDB.java è l'unico proprietario di filtri, projection, collection, conversione Document/MongoRecord, mapping verso i modelli canonici e operazioni di scrittura Mongo.

I service chiamano MongoDB direttamente. Non esiste LeagueStore e non c'è fallback MariaDB in caso di errore di lettura.

## Matrice

- PUUID, nome, user id e count: scalar diretto.
- Più campi locali: MongoRecord.
- Lista di projection: List<MongoRecord>.
- Summoner, Rank e Mastery: modello esistente.
- Match detail e history: Match o MatchResult esistente.
- Leaderboard, profile e champion aggregate: projection Mongo mappata in MongoDB.

Una query a più campi viene prima rappresentata come MongoRecord e solo dopo, se serve, convertita in un modello canonico. Un singolo valore non viene avvolto inutilmente.

## Copertura

La controparte copre summoner, ricerca, account, rank, mastery, profile statistics, match, history, participant, eventi, rank match, champion data, build aggregate, leaderboard, distribuzioni e statistiche aggregate.

Custom builds e summoner.metrics restano esplicitamente fuori inventario.

## Errori

Un errore di connessione, schema o conversione Mongo è esplicito. Non viene trasformato in lista vuota e non attiva fallback MariaDB. I default si applicano solo a campo mancante o null, non a un documento corrotto.
