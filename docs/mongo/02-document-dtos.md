# Fase 2 — documenti e risultati

MongoDB.java serializza e legge i modelli canonici. MongoRecord.java espone projection e conversioni locali. MongoMigration.java usa gli stessi documenti per il backfill.

Non creare SummonerDocument, MatchDocument, ParticipantDocument, mapper o codec separati.

## Contratto MongoRecord

API: get, getAsString, getAsInt, getAsLong, getAsDouble, getAsBoolean, getAsInstant, getAsEnum, getAsRecord, getAsRecords, getAs(Class), getAs(field, Class) e toDocument.

toDocument restituisce una copia difensiva. Campo mancante o null restituisce null, zero, false o lista vuota secondo l'accessor. Un valore presente ma invalido genera un errore esplicito contenente collection, id e campo.

getAs(Class) usa il mapping interno di MongoDB verso Summoner, Rank, Mastery, Participant, Match, MatchResult e i modelli statistica già esistenti. Non esiste un DTO intermedio.

## Forma LoL

Un match usa _id con il Riot match ID completo, leagueShard come enum serializzato con name(), bans con le chiavi BLUE e RED e participants come lista di documenti flat. Un participant non ha un campo build nested. Eventi e liste sono strutturati solo quando rappresentano davvero una sequenza o una mappa.

QueryRecord e QueryResult restano invariati per MariaDB e non sono il contratto dei risultati Mongo.
