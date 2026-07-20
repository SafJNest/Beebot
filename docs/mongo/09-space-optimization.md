# Ottimizzazione spazio MongoDB

## Decisione

Con circa 6 milioni di documenti `summoner` e circa 1 GB di dati contro quasi 2 GB di indici, il primo intervento riguarda gli indici. La compressione applicativa dei documenti base viene rimandata: BSON/WiredTiger comprime già il documento, mentre un campo compresso perde query e proiezioni naturali.

I payload LoL non usano più Kryo. MariaDB usa JSON UTF-8 come testo (`longtext`) per build, champion statistics e profile statistics; Mongo usa BSON strutturato nei campi `build` e `statistics`, così projection e aggregation restano disponibili. Non vengono convertiti o cancellati automaticamente dati storici: l'operatore rimuove manualmente le righe/documenti Kryo prima della rigenerazione.

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

Indice target su `summoner`:

- `_id`, implicito e non eliminabile;
- `summoners_user_id`, sparse;
- `summoners_region_riot_search`, `{ region: 1, riotSearch: 1 }`;
- `summoners_tracking_region_active`, `{ tracking: 1, region: 1 }`, partial filter `{ tracking: true }`;
- `summoners_rank_lp`, `{ ranks.rank: 1, ranks.lp: -1 }`;
- `summoners_mastery_level_points`, `{ masteries.level: -1, masteries.points: -1 }`.

Per le letture principali sono inoltre dichiarati gli indici `participants.puuid + leagueShard + queue + timeStart`, i quattro indici leaderboard con `mmr + puuid` come ordinamento deterministico e gli indici champion già filtrati per `filterKey`, queue e champion. Gli indici nuovi vengono aggiunti con nomi distinti; non viene eseguito alcun drop automatico.

`MongoDB.ensureIndexes()` crea gli indici mancanti e non esegue drop automatici. Durante il backfill gli indici secondari sono posticipati; vengono creati dopo il completamento di summoner e match. Gli indici obsoleti, se presenti, vengono verificati e rimossi manualmente dopo l'audit. Dopo la migrazione si eseguono gli `explain("executionStats")` delle query principali:

```javascript
db.summoner.find({region: "EUW1", riotSearch: /^name/}).explain("executionStats")
db.summoner.find({userId: "discord-id"}).explain("executionStats")
db.summoner.find({tracking: true}).explain("executionStats")
```

L'accettazione richiede `IXSCAN` e assenza di `COLLSCAN` sulle ricerche attive. Confrontare `db.summoner.stats().indexSizes` e `db.runCommand({collStats: "summoner", scale: 1})`; il recupero fisico del file può richiedere manutenzione Mongo separata.

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

La prima misura da conservare è la baseline del database con gli indici finali; la seconda è la misura dopo la rigenerazione degli aggregati e delle statistiche derivate. I risultati `explain("executionStats")` delle query search, history e leaderboard fanno parte dell'audit insieme a `collStats` e `indexSizes`.
