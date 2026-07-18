# Fase 2: mapping Mongo e modelli esistenti

## Obiettivo

Definire il mapping BSON verso i modelli canonici LoL senza creare DTO Mongo duplicati.

La policy completa per scegliere il tipo di risultato è in [`06-result-policy.md`](06-result-policy.md).

## Regola principale

Mongo non impone un unico tipo di ritorno. La query sceglie il tipo minimo sufficiente:

| Query | Risultato |
|---|---|
| un solo valore | `String`, `int`, `long`, `boolean`, enum o altro scalar |
| più campi semplici usati localmente | `MongoRecord` |
| lista di valori semplici | `List<T>` |
| oggetto complesso già rappresentato | modello/DTO esistente |
| oggetto complesso senza modello | nuovo DTO solo se realmente necessario |

Non creare classi come `SummonerDocument`, `MatchDocument`, `ParticipantDocument` o `ProfileStatisticsDocument` quando il modello esistente rappresenta già lo stesso concetto.

Non creare DTO di successo sotto `com.safjnest.spring.dto`.

## Ownership del mapping

```text
MongoRecord <-> MongoRecordCodec <-> modello canonico LoL
```

Il codec è l'unico punto autorizzato a convertire:

- stringhe enum in enum R4J;
- BSON in liste/mappe Java;
- JSON legacy in strutture tipizzate;
- campi legacy numerici in ID canonici;
- ban legacy numerici in `BLUE` e `RED`.

Il codec non contiene logica di business, accesso Redis o logica HTTP.

## Infrastruttura prevista

```text
com.safjnest.mongo
  MongoRecord
  MongoRecordCodec
  MongoRecordMapper
  MongoConversionException
```

I codec specifici per `Match`, `Participant`, `SummonerOverview` e gli altri modelli possono essere registrati nel mapper, ma non introducono un DTO intermedio.

## Contratto `MongoRecord`

`MongoRecord` è un wrapper per documenti o projection Mongo quando non serve costruire un oggetto applicativo:

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

Regole:

- campi assenti/null: `null`, `0`, `false` o lista vuota secondo l'accessor;
- tipo presente ma invalido: errore con collection, `_id` e campo;
- enum R4J: `name()` esatto, mai ordinal;
- ban: `BLUE` e `RED`, mai `0` e `1`;
- nested BSON: disponibile solo con `getAsRecord(s)`;
- `toDocument()`: copia difensiva.

`getAs(Class<T>)` usa codec espliciti per modelli già esistenti. Un tipo complesso senza codec deve fallire, non essere trasformato in un DTO reflection-based.

## Modelli da riusare

Quando la query costruisce un oggetto complesso, usare i tipi già presenti:

- `Summoner` per l'identità summoner;
- `Rank` per un rank;
- `Mastery` per una mastery;
- `Match` per il dettaglio completo;
- `Participant` per il participant completo;
- `MatchResult` per la projection leggera delle liste;
- `SummonerOverview` per il riepilogo del comando/profile;
- `SummonerView` per la proiezione HTTP completa;
- `ProfileStatistics`, `ChampionStatistics`, `Build` e gli altri modelli canonici già presenti.

Una query che seleziona solo `puuid` ritorna `String`. Una query che seleziona `wins`, `losses` e `count` per un solo uso locale ritorna `MongoRecord`. Una query che alimenta il comando overview costruisce `SummonerOverview` tramite i dati già esistenti.

## Convenzioni BSON

- enum R4J come stringhe esatte: `EUW1`, `ARAM`, `MIDDLE`, `BLUE`;
- ban come `bans.BLUE` e `bans.RED`, mai `bans.0` e `bans.1`;
- `events` come documento BSON quando convertibile;
- `eventsRaw` e stato di conversione solo per compatibilità/errori;
- participant con campi flat per item, rune, spell, skill e augment;
- nessun mega-oggetto `build` nel document migrato;
- timestamp come epoch milliseconds, salvo accessor `Instant` a livello Java;
- PUUID e Riot match ID completo come identità canoniche.

## Ownership applicativa

- repository Mongo: filtri, projection, collection e wrapping `MongoRecord`;
- codec: conversione tra `MongoRecord` e modello esistente;
- `LeagueStore`: scelta del tipo di ritorno e mapping finale;
- service: composizione del comportamento applicativo;
- controller: modelli HTTP già pubblici;
- `QueryRecord` e `QueryResult`: esclusivamente MariaDB/transizione SQL.

## Test richiesti

- accessor scalar con tipi BSON nativi;
- campo assente/null e lista vuota;
- errore strict su tipo o enum invalido;
- nested document e lista di `MongoRecord`;
- codec modello esistente → `MongoRecord` → modello;
- nessuna classe `*Document` duplicata.
