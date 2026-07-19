# Ottimizzazione spazio MongoDB

## Decisione

Con circa 6 milioni di documenti `summoner` e circa 1 GB di dati contro quasi 2 GB di indici, il primo intervento riguarda gli indici. La compressione applicativa dei documenti base viene rimandata: BSON/WiredTiger comprime già il documento, mentre un campo compresso perde query e proiezioni naturali.

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

`tracking=false`, `userId=null` e default vuoti non vengono persistiti. `legacySummonerId` e il campo duplicato `puuid` non vengono scritti. Il database target viene cancellato prima della migrazione, quindi non sono previsti cleanup o conversioni manuali dei documenti già presenti.

I consumer ricevono il PUUID già presente nel modello Riot/Summoner. Non esistono collection di mapping e le letture Mongo non eseguono lookup MariaDB per ricostruire un id numerico.

## Indici

Indice target su `summoner`:

- `_id`, implicito e non eliminabile;
- `summoners_user_id`, sparse;
- `summoners_region_riot_search`, `{ region: 1, riotSearch: 1 }`;
- `summoners_tracking_region_active`, `{ tracking: 1, region: 1 }`, partial filter `{ tracking: true }`.

`MongoDB.ensureSchema()` crea gli indici mancanti e non esegue drop automatici. Il database target viene ricreato vuoto prima del run, quindi non esistono indici legacy da rimuovere. Dopo la migrazione si eseguono gli `explain("executionStats")` delle tre query principali:

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

`MongoDB.upsertMatch()` scrive prima `match`, poi serializza gli eventi in JSON e li salva in `match_events`; la collection usa `block_compressor=zstd` e il server viene configurato con livello 9. `MongoDB.findMatch()` carica gli eventi con una seconda query. La history raccoglie gli id e carica gli eventi in una sola query `in`, evitando N+1. Non esiste conversione in-place del vecchio formato: il target viene migrato pulito.

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

La prima misura da conservare è la baseline del database vuoto con gli indici finali; la seconda è la misura dopo la migrazione raw e dopo la costruzione eventuale delle statistiche derivate.
