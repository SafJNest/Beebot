# Fase 2 — documenti e risultati

MongoDB.java serializza e legge i modelli canonici. QueryRecordParser converte le projection Mongo in QueryRecord detached. MongoMigration.java usa lo stesso parser per le righe MariaDB del backfill.

Non creare SummonerDocument, MatchDocument, ParticipantDocument, mapper o codec separati.

## Contratto QueryRecord

API: get, getValue, getAsString, getAsInt, getAsLong, getAsDouble, getAsBoolean, getAsInstant, getAsEnum, getAsRecord, getAsRecords e getAsList.

QueryRecord conserva i valori scalari compatibili con il contratto MariaDB e supporta QueryRecord e liste di QueryRecord annidate. Il parser copia ricorsivamente Document, mappe, liste, byte array e valori BSON; nessun oggetto BSON resta referenziato dopo il parsing.

Il mapping verso Summoner, Rank, Mastery, Participant, Match, MatchResult e i modelli statistica viene eseguito da MongoDB.read(QueryRecord, Class). Non esiste un wrapper Mongo intermedio.

## Forma LoL

Un match usa `_id` con il Riot match ID completo, `region` come enum serializzato con `name()`, `patch` completo e `patchMajor` per i filtri, bans con le chiavi BLUE e RED e participants come lista di documenti flat. Un participant non ha un campo build nested. Eventi e liste sono strutturati solo quando rappresentano davvero una sequenza o una mappa.

QueryRecord e List<QueryRecord> sono il contratto comune: MariaDB li popola in forma piatta, MongoDB può popolare le liste con strutture annidate.
