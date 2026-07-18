# Policy risultati Mongo

Il tipo di risultato nasce dalla responsabilità della query:

| Caso | Risultato |
|---|---|
| un valore | scalar diretto |
| più campi locali | MongoRecord |
| lista di valori | List<T> |
| projection multipla | List<MongoRecord> |
| oggetto complesso già esistente | modello canonico esistente |

MongoRecord non è obbligatorio e non è un DTO HTTP. MongoDB mappa i record verso Summoner, Match, Participant, MatchResult, SummonerOverview e gli altri modelli già presenti.

API accessor: get, getAsString, getAsInt, getAsLong, getAsDouble, getAsBoolean, getAsInstant, getAsEnum, getAsRecord, getAsRecords, getAs(Class), getAs(field, Class) e toDocument.

Campo assente/null: default compatibile. Tipo presente non convertibile: errore esplicito con collection, id e campo. Enum R4J: name(). Ban: BLUE e RED. toDocument: copia difensiva.

Non creare DTO Mongo duplicati, MongoResult, mapper o codec separati. QueryRecord e QueryResult MariaDB rimangono invariati.
