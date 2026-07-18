# MongoDB LoL migration

## Purpose

Questa directory è la fonte operativa per la migrazione della persistenza LoL da MariaDB a MongoDB.

La documentazione viene implementata prima del codice. Nessun agente deve inventare collection, tipi di risultato, chiavi, enum o regole di scrittura non descritte qui o nell'ADR collegato.

## Stato

- Stato: documentazione proposta, non ancora implementata.
- Perimetro: prima `league_of_legends`; Berbit, Spotify e Website sono fasi successive.
- Fonte primaria durante la transizione: MariaDB.
- Strategia: dual-write, shadow-read, cutover progressivo e rollback configurabile.
- Redis: cache e coda effimera, non storage durevole.

## Ordine di lettura

1. questo file;
2. [`01-db-structure.md`](01-db-structure.md);
3. [`02-document-dtos.md`](02-document-dtos.md);
4. [`03-query-migration.md`](03-query-migration.md);
5. [`04-write-path-and-refactor.md`](04-write-path-and-refactor.md);
6. [`05-data-migration-and-cutover.md`](05-data-migration-and-cutover.md);
7. [`06-result-policy.md`](06-result-policy.md);
8. [`07-agent-strategy.md`](07-agent-strategy.md);
9. [`ADR-0009`](../architecture/adr/0009-mongo-persistence-and-migration.md).

Un agente che implementa una sola fase deve leggere questo file e il file assegnato. Le decisioni di formato sono ripetute nel file della fase quando servono per lavorare senza contesto aggiuntivo.

## Fasi

| Fase | Documento | Risultato |
|---|---|---|
| 1 | [`01-db-structure.md`](01-db-structure.md) | collection, chiavi, embedding e indici definiti |
| 2 | [`02-document-dtos.md`](02-document-dtos.md) | mapping Mongo e codec verso modelli esistenti |
| 3 | [`03-query-migration.md`](03-query-migration.md) | query MariaDB mappate in operazioni Mongo tipizzate |
| 4 | [`04-write-path-and-refactor.md`](04-write-path-and-refactor.md) | write path unico e consumer migrati |
| 5 | [`05-data-migration-and-cutover.md`](05-data-migration-and-cutover.md) | backfill, riconciliazione, cutover e rollback |
| 6 | [`06-result-policy.md`](06-result-policy.md) | scelta pragmatica del tipo di risultato |
| 7 | [`07-agent-strategy.md`](07-agent-strategy.md) | ownership, agenti, gate e handoff |

## Identità canoniche

| Concetto | Identità Mongo | Campo legacy temporaneo |
|---|---|---|
| Summoner | `puuid` | `legacySummonerId` |
| Match | Riot match ID completo, per esempio `EUW1_134131` | `legacyMatchId` |
| Profile statistics | `puuid + seasonStart` | chiave SQL precedente |
| Rank embedded | `queue + region` dentro il summoner | `summoner_id` |
| Mastery embedded | `championId` dentro il summoner | `summoner_id` + `champion_id` |

Gli ID numerici legacy servono solo per backfill, riconciliazione e compatibilità transitoria. Non sono chiavi per nuove query o nuovi riferimenti applicativi.

## Regole JSON comuni

### Enum

Ogni enum R4J viene serializzato con `name()` esatto e in maiuscolo:

- `LeagueShard`;
- `GameQueueType`;
- `TierType`;
- `TierDivisionType`;
- `LaneType`;
- `TeamType`;
- enum degli eventi R4J.

Non usare ordinali numerici, label di presentazione, alias regionali o valori tradotti.

### Team e ban

I team sono nominati. Non usare chiavi ordinali come `0` e `1`.

```json
{
  "bans": {
    "BLUE": [266, 157, 238, 517, 777],
    "RED": [64, 119, 238, 141, 875]
  }
}
```

Se il dataset non ha ban, mantenere la forma stabile:

```json
{
  "bans": {
    "BLUE": [],
    "RED": []
  }
}
```

### Null, liste e date

- `null` indica un valore non disponibile o non riconosciuto;
- `[]` indica una collezione conosciuta ma vuota;
- le mappe contatore usano chiavi semantiche, mai ordinali;
- gli istanti persistiti usano epoch milliseconds per mantenere compatibilità con i modelli LoL esistenti;
- JSON legacy e BSON strutturato sono equivalenti solo dopo validazione del codec.

### Participant e build

Il participant non contiene un mega-oggetto `build` annidato. I dati sono campi espliciti e liste solo dove rappresentano una sequenza:

```json
{
  "item0": 1055,
  "item1": 6672,
  "item2": 3006,
  "item3": 3031,
  "item4": 3121,
  "item5": 3089,
  "item6": 0,
  "starterItems": [1055],
  "buildPath": [6672, 3006],
  "boots": 3006,
  "supportItem": 0,
  "primaryRunes": [8005, 9111, 9104],
  "secondaryRunes": [8210, 8237],
  "statsRunes": [5005, 5008, 5001],
  "skillOrder": [1, 2, 3, 1],
  "augments": []
}
```

La migrazione può migliorare leggermente la struttura del JSON, ma deve mantenere una trasformazione esplicita e un payload raw per i valori non convertibili.

## Configurazione Mongo

La configurazione runtime viene letta da `rsc/settings.json`, già escluso dal controllo versione.

La forma documentata è una singola stringa URI di connessione, senza database applicativo:

```json
{
  "settings": {
    "mongo": "mongodb://<user>:<password>@safjnest.com:27017/"
  }
}
```

Il database logico è `beebot` in production e `beebot_test` quando `App.isTesting()` è attivo. Le collection LoL usano il prefisso `lol_` in entrambi i database. Password e URI reali non devono comparire in documentazione, log, test o commit.

Il bootstrap dello schema applicativo deve creare collection e indici mancanti in modo idempotente. Non sono ammessi setup manuali come prerequisito runtime.

## Regole per gli agenti

- usare `Summoner`, `Rank`, `Mastery`, `Match`, `Participant` e `MatchResult` come modelli canonici;
- usare `MongoRecord` solo per projection e risultati locali;
- riusare i modelli/DTO esistenti per gli oggetti complessi;
- non creare document DTO duplicati, mai sotto `spring.dto` o nella persistenza;
- considerare `summoner.metrics` e custom builds fuori dal modello Mongo LoL corrente;
- catalogare eventuali query SQL legacy per metrics/custom builds come fuori scope, senza migrarle;
- non esporre `QueryResult` o `QueryRecord` oltre il boundary MariaDB transitorio;
- non aggiungere una seconda funzione proprietaria per lo stesso dato;
- ogni scrittura LoL MariaDB deve avere una funzione di mirror Mongo nello stesso write path;
- aggiornare l'API e la documentazione se il payload pubblico cambia;
- fermarsi e segnalare ogni conflitto con un ADR o con questa documentazione.
- seguire [`07-agent-strategy.md`](07-agent-strategy.md) per ownership, sequenza e handoff.

## Gate globale

La documentazione è implementabile quando un agente può determinare senza interpretazione autonoma:

- quale collection usare;
- quale `_id` assegnare;
- quali campi serializzare;
- quale codec chiamare;
- quale indice richiedere;
- quale funzione usare per una scrittura;
- come verificare e riprendere una migrazione;
- come eseguire rollback.
