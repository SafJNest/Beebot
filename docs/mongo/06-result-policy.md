# Policy risultati Mongo

Il tipo di risultato nasce dalla responsabilità della query:

| Caso | Risultato |
|---|---|
| un valore | scalar diretto |
| più campi locali | QueryRecord |
| lista di valori | List<T> |
| projection multipla | List<QueryRecord> |
| oggetto complesso già esistente | modello canonico esistente |

QueryRecord non è un DTO HTTP. MongoDB mappa i record verso Summoner, Match, Participant, MatchResult, SummonerOverview e gli altri modelli già presenti quando la projection rappresenta un oggetto canonico.

API accessor: get, getValue, getAsString, getAsInt, getAsLong, getAsDouble, getAsBoolean, getAsInstant, getAsEnum, getAsRecord, getAsRecords e getAsList.

Campo assente/null: default compatibile. Enum R4J: name(). Ban: BLUE e RED. Il parser produce sempre una copia detached e non conserva riferimenti BSON.

Non creare DTO Mongo duplicati, contenitori risultati Mongo, mapper o codec separati. QueryRecordParser è il parser comune MariaDB/Mongo.
