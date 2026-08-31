# Ottimizzazione spazio MongoDB

## Decisione

Con circa 6 milioni di documenti `summoner` e circa 1 GB di dati contro quasi 2 GB di indici, il primo intervento riguarda gli indici. La compressione applicativa dei documenti base viene rimandata: BSON/WiredTiger comprime già il documento, mentre un campo compresso perde query e proiezioni naturali.

I payload LoL non usano più Kryo. MariaDB usa JSON UTF-8 come testo (`longtext`) per build, champion statistics e profile statistics; Mongo usa BSON strutturato: `build` e champion statistics mantengono i propri campi strutturati, mentre `profile_statistics` espone gli aggregati direttamente a root senza `statistics` annidato e `profile_matchups` salva il payload matchup strutturato. Non vengono convertiti o cancellati automaticamente dati storici: l'operatore rimuove manualmente le righe/documenti Kryo prima della rigenerazione.

Gli eventi sono l'eccezione: non vengono filtrati direttamente e vengono letti come payload completo. Per questo vengono spostati da `match.events` alla collection `match_events`, creata con WiredTiger Zstandard nativo.

## Documento summoner

Forma target:

```json
{
  "_id": "puuid",
  "riotId": "Name#TAG",
  "region": "EUW1",
  "level": 500,
  "icon": 1234,
  "riotSearch": "nametag",
  "tracking": true,
  "userId": "discord-id"
}
```

`tracking=false`, `userId=null` e default vuoti non vengono persistiti. Gli identificativi numerici MariaDB e il campo duplicato `puuid` del summoner non vengono scritti. Il nuovo flusso non esegue cleanup o conversioni automatiche dei documenti già presenti.

I consumer ricevono il PUUID già presente nel modello Riot/Summoner. Non esistono collection di mapping e le letture Mongo non eseguono lookup MariaDB per ricostruire un id numerico.

## Indici

Gli indici sono gestiti dall'operatore del database, fuori dal runtime e dalla
migration. I key pattern devono seguire le query documentate in
[`01-db-structure.md`](01-db-structure.md). I vincoli unique su `{puuid,
filterKey}` richiedono un preflight operativo di identità mancanti o duplicate.

Gli `explain("executionStats")` devono verificare le query calde, incluse le letture per `profile_matchups`:

```javascript
db.summoner.find({region: "EUW1", riotSearch: /^name/}).explain("executionStats")
db.summoner.find({userId: "discord-id"}).explain("executionStats")
db.summoner.find({tracking: true}).explain("executionStats")
db.match.find({participants: {$elemMatch: {puuid: "puuid"}}, region: "EUW1", patchMajor: "14.2"}).sort({timeStart: -1}).limit(100).explain("executionStats")
db.profile_statistics.find({puuid: "puuid", filterKey: "filter"}).explain("executionStats")
db.profile_activity.find({puuid: "puuid", filterKey: "filter"}).explain("executionStats")
db.profile_matchups.find({puuid: "puuid", filterKey: "filter"}).explain("executionStats")
```

Registrare `executionTimeMillis`, `totalKeysExamined`, `totalDocsExamined`,
`nReturned`, `winningPlan`, `indexName` ed eventuali `COLLSCAN`, `SORT` e
`usedDisk`. Confrontare `db.runCommand({collStats: "summoner", scale: 1})`,
`indexSizes` e le collection coinvolte prima/dopo. Eventuali sort bloccanti
della leaderboard dopo `$unwind`/`$facet` sono costi applicativi residui, non
un errore da nascondere nel registry.

## Eventi compressi

`match` contiene base e participant. `match_events` contiene:

```json
{
  "_id": "EUW1_123",
  "encoding": "json",
  "uncompressedBytes": 18240,
  "data": "<JSON string>",
  "checksum": "<sha256>"
}
```

`MongoDB.upsertMatch()` scrive prima `match`, poi serializza gli eventi in JSON e li salva in `match_events`; la collection usa `block_compressor=zstd` e il server viene configurato con livello 9. `MongoDB.findMatch()` carica gli eventi con una seconda query. La history raccoglie gli id e carica gli eventi in una sola query `in`, evitando N+1. Non esiste conversione in-place del vecchio formato: l'operatore rimuove manualmente i dati obsoleti.

Configurazione server richiesta prima della migrazione:

```yaml
storage:
  wiredTiger:
    collectionConfig:
      blockCompressor: snappy
    engineConfig:
      zstdCompressionLevel: 9
```

`match_events` fa override per collection a `block_compressor=zstd`; le altre collection mantengono il compressor globale. Il livello 9 è un'impostazione server-wide per le collection che usano Zstandard.

La misura della compressione nativa va fatta a livello collection:

```text
storageSize / dataSize
```

Va misurata dopo la migrazione su un campione reale prima di stimare il risparmio totale. Il livello Zstandard è server-wide: non può essere impostato a 9 solo per `match_events` tramite `createCollection`; la collection può selezionare il compressor, mentre il server determina il livello.

## Masteries e audit

Le `masteries` restano BSON normale nella prima migrazione e non hanno indici embedded. `MongoDB.spaceAudit(sampleSize)` raccoglie statistiche della collection, `indexSizes`, BSON medio/massimo del campione, percentuale `userId`, percentuale `tracking=true` e regioni osservate.

La compressione inline delle masteries si valuta solo dopo un campione reale:

- sotto il 25% dello storage `summoner`: restano BSON;
- sopra il 25% oppure p95 del documento oltre 4 KB aggiuntivi: valutare lo stesso codec Zstandard;
- nessun indice sui campi embedded `masteries`.

La prima misura da conservare è la baseline del database con gli indici finali; la seconda è la misura dopo la rigenerazione delle statistiche derivate. I risultati `explain("executionStats")` delle query search, history e leaderboard fanno parte dell'audit insieme a `collStats` e `indexSizes`.
