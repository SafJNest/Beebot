# Audit 02 — comando `summoner profile`

Per il contratto completo e il runbook di recupero contesto vedere [`profile-statistics-source-of-truth.md`](../architecture/profile-statistics-source-of-truth.md).

## Percorso runtime attuale

```text
/summoner profile
  -> risoluzione account/cache
  -> Riot API solo quando il profilo Mongo è incompleto
  -> LeagueService.upsertSummoner
  -> ProfileStatisticsService.get / DatabaseTracker refresh
  -> LeagueMessage.send
```

L’account e il profilo vengono persistiti direttamente in MongoDB. `LeagueDB` non è più coinvolto nel comando e non esiste un mirror MariaDB→Mongo nel runtime.

## Contratto Mongo

`ProfileStatisticsService` è il proprietario del calcolo e del refresh. Legge da Mongo i match proiettati usando lo stesso `Filter` completo del comando e persiste un documento flat indicizzato da `puuid + filterKey`, che contiene:

- `total`;
- `queueStats`, `laneStats`, `championStats` con il contesto queue/lane per ogni champion;
- `matchups`, `duoStats` e `pings`;
- `lastUpdate` e gli estremi temporali dell'aggregato.

Il documento match resta la sorgente dei participant; `recentMatches` viene caricato separatamente come `MatchResult` leggero dallo stesso filtro e non viene serializzato dentro `ProfileStatistics`.

La chiave Mongo non è la stagione: è l'uguaglianza esatta `{ puuid, filterKey }`, dove `filterKey` è `Filter.toSummonerKey()` e include anche il periodo. L'applicazione usa la coppia come identità logica e l'indice unique `profile_statistics_identity` protegge la cardinalità uno-a-uno.

Il contesto champion è calcolato nello stesso passaggio degli aggregati
generici. Non modifica embed, comandi o layout esistenti.

## Account e cache

`UserData` legge gli account collegati con `MongoDB.findAccountsByUserId`. L’aggiunta usa `LeagueService.upsertSummoner`; la rimozione usa `MongoDB.detachSummonerUser` con filtro su PUUID e `userId`. Dopo add/unlink vengono invalidati i riferimenti Redis e la cache locale viene aggiornata.

Il PUUID è il valore stabile usato dall’autocomplete e dai lookup Mongo. La Riot API resta una sorgente di refresh, non una persistenza intermedia.

## Verifica

I test devono eseguire il flusso con una chiave Redis nuova o invalidata e confrontare overview, profile e comando generico sullo stesso PUUID, `Filter` e `lastUpdate`. La guardia runtime deve continuare a fallire se il codice del profilo reintroduce `LeagueDB`.
