# Policy risultati Mongo

## Obiettivo

Scegliere il tipo di ritorno corretto per ogni query senza imporre `MongoRecord` o un DTO come wrapper universale.

## Matrice decisionale

| Situazione | Tipo corretto |
|---|---|
| Un solo dato | tipo diretto: `String`, `int`, `long`, `boolean`, enum |
| Più campi semplici usati in un solo punto | `MongoRecord` |
| Lista di valori semplici | `List<T>` |
| Oggetto complesso con modello esistente | modello/DTO esistente |
| Oggetto complesso senza modello | nuovo DTO solo dopo verifica architetturale |

Esempi:

```java
String findPuuid(...);
long countMatches(...);
MongoRecord findSummaryProjection(...);
SummonerOverview findSummonerOverview(...);
List<MatchResult> findMatchHistory(...);
```

Una query che ritorna solo `puuid` non deve creare un `MongoRecord`. Una query overview non deve restituire un `MongoRecord` al controller se esiste già `SummonerOverview`.

## `MongoRecord`

È un wrapper read-oriented per un documento o una projection Mongo. Non è un DTO HTTP, non è un modello LoL e non sostituisce un tipo canonico.

```java
String getAsString(String field);
int getAsInt(String field);
long getAsLong(String field);
double getAsDouble(String field);
boolean getAsBoolean(String field);
Instant getAsInstant(String field);

<E extends Enum<E>> E getAsEnum(String field, Class<E> type);
MongoRecord getAsRecord(String field);
List<MongoRecord> getAsRecords(String field);
<T> List<T> getAsList(String field, Class<T> type);
<T> T getAs(Class<T> type);
<T> T getAs(String field, Class<T> type);
```

Le query multiple possono restituire `List<MongoRecord>`. Per migrazioni o scansioni grandi usare un cursore/iteratore lazy, non una lista completa.

## Conversioni

- campo mancante/null: `null`, `0`, `false` o lista vuota secondo il metodo;
- valore presente con tipo errato: `MongoConversionException`;
- enum: `Enum.valueOf` sul nome R4J esatto;
- numeri: conversione da `Number` o stringa numerica validata;
- date: `Date`, `Instant`, epoch milliseconds o stringa ISO valida;
- nested: `getAsRecord` e `getAsRecords`;
- `toDocument()`: copia difensiva.

L'errore deve indicare almeno collection, `_id` e campo quando disponibili.

## Codec per modelli esistenti

`getAs(Class<T>)` usa codec espliciti:

```java
public interface MongoRecordCodec<T> {
    T read(MongoRecord record);
    MongoRecord write(T value);
}
```

I codec convertono direttamente verso classi già presenti, per esempio `Match`, `Participant`, `MatchResult`, `SummonerOverview` o `SummonerView`.

Non creare `SummonerDocument`, `MatchDocument`, `ParticipantDocument` o `SummonerOverviewDTO` se esiste già il modello canonico equivalente.

Il registry fallisce se un tipo complesso non ha codec. Non è ammesso un mapping reflection-based che inventi campi o nesting.

## BSON LoL

- enum R4J sempre `name()`;
- `bans.BLUE` e `bans.RED`, mai chiavi ordinali;
- Riot match ID completo come identità match;
- PUUID come identità summoner;
- participant con campi flat;
- nessun mega-oggetto `build`;
- `events` come BSON strutturato quando convertibile.

## Boundary

```text
Mongo repository -> scalar/MongoRecord/List<MongoRecord>
                -> codec o mapping LeagueStore
                -> modello LoL esistente
                -> service/API
```

`MongoRecord` non deve arrivare al controller quando esiste un modello HTTP canonico.

`QueryRecord` e `QueryResult` restano wrapper MariaDB e non vengono sostituiti globalmente.

## Database e schema runtime

La URI Mongo identifica il server. Il database viene scelto dal codice:

```text
App.isTesting() == false -> beebot
App.isTesting() == true  -> beebot_test
```

Le collection mantengono lo stesso nome nei due database. Un initializer applicativo deve assicurare collection e indici dichiarati nel registry dello schema prima che partano i repository.

Il bootstrap è idempotente:

- crea collection e indici mancanti;
- usa nomi stabili per gli indici;
- accetta un indice già presente solo se la specifica coincide;
- segnala conflitti di specifica;
- non elimina o modifica automaticamente indici esistenti.

## Test minimi

- scalar diretto;
- projection `MongoRecord`;
- nested document/lista;
- null e campi assenti;
- conversione strict di tipo ed enum;
- codec per un modello esistente;
- round-trip modello → BSON → modello;
- nessun DTO Mongo duplicato.
