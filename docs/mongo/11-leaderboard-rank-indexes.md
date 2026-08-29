# Competitive leaderboard indexes

`summoner.ranks` conserva soltanto il rank Riot (`rank`, `lp`, `wins`,
`losses`). L'ordinamento MMR e il ruolo primario vivono nella collection
derivata `competitive`, una riga per `{ puuid, queue }`:

```javascript
{
  _id: "<puuid>:RANKED_SOLO_5X5",
  puuid: "<puuid>",
  region: "EUW1",
  queue: "RANKED_SOLO_5X5",
  mmr: 2470,
  primary: "UTILITY",
  lastUpdate: 1720000000000
}
```

Una riga esiste se è presente il rank della queue. `primary` è nullo finché non
esistono statistiche canoniche della stessa queue e poi deriva dalle foglie
`profile_statistics.champions.<championId>.<CanonicalQueue>.<position>`; non
esiste `filterKey` nella collection. La leaderboard ottiene i PUUID ordinati e
paginati da `competitive`, poi carica i soli summoner della pagina con `_id:
{$in: [...]}`.

`!test competitive` svuota e ricostruisce la proiezione, rigenera gli snapshot
leaderboard e invalida la loro versione Redis. `!test competitive stats` fa lo
stesso e, per ogni summoner ranked senza statistiche canoniche, esegue un task
background `profile-statistics` alla volta: non crea una coda con l'intera base
utenti in memoria.

Gli indici sono gestiti manualmente dall'operatore: il runtime non crea, cambia
o rimuove indici.

## Create

```javascript
db.competitive.createIndex(
  { queue: 1, mmr: -1 },
  { name: "competitive_queue_mmr" }
);

db.competitive.createIndex(
  { queue: 1, region: 1, mmr: -1 },
  { name: "competitive_queue_region_mmr" }
);

db.competitive.createIndex(
  { queue: 1, primary: 1, mmr: -1 },
  { name: "competitive_queue_primary_mmr" }
);

db.competitive.createIndex(
  { queue: 1, region: 1, primary: 1, mmr: -1 },
  { name: "competitive_queue_region_primary_mmr" }
);
```

## Cleanup di `summoner.ranks`

Dopo che `!test competitive` ha popolato l'indice, rimuovere i vecchi campi
MMR embedded e gli indici `summoner_leaderboard_*` / `ranks.*.mmr`. La query di
cleanup è in [`10-ranks-object-migration.md`](10-ranks-object-migration.md).

## Validate

```javascript
db.competitive.find(
  {
    queue: "RANKED_SOLO_5X5",
    region: "EUW1",
    primary: "UTILITY",
    mmr: { $gte: 30000 }
  },
  { _id: 0, puuid: 1 }
)
.sort({ mmr: -1 })
.skip(50)
.limit(50)
.explain("executionStats");

db.summoner.find(
  { _id: { $in: ["<puuid-1>", "<puuid-2>"] } },
  { _id: 1, riotId: 1, region: 1, level: 1, icon: 1, ranks: 1, masteries: 1 }
).explain("executionStats");
```

Il primo explain deve usare l'indice che corrisponde allo scope (con `primary`
se il ruolo è richiesto), senza `COLLSCAN` o `SORT` bloccante. Il secondo deve
risolvere l'`$in` sul primary key `_id` di `summoner`.
