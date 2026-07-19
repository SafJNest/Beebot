# Audit 02 — comando `summoner profile`

## Percorso

```text
/summoner profile
  -> SummonerProfile.execute
  -> LeagueHandler.getSummonerByArgs
  -> Riot API
  -> LeagueDB.addLOLAccount(summoner)
  -> commit MariaDB + MongoDB.mirrorSummoner
  -> LeagueMessage.send
  -> LeagueMessage.getSummonerEmbed
  -> LeagueService.getAdvancedLOLData
  -> MongoDB.findAdvancedProfileProjections(puuid, shard, ...)
  -> MongoDB.findAdvancedProfileProjections
  -> LeagueService.toQueryResult
  -> consumer lanes_played / champion aggregates
```

Il comando inserisce l’account prima di costruire il messaggio: [SummonerProfile.java](../../src/main/java/com/safjnest/commands/lol/summoner/SummonerProfile.java:42) e [LeagueDB.java](../../src/main/java/com/safjnest/sql/database/LeagueDB.java:200).

## Contratto MariaDB storico

`LeagueDB.getAdvancedLOLData` non restituisce match raw. Restituisce una riga aggregata per champion con:

- `champion`;
- `games`, `wins`, `losses`;
- `avg_kills`, `avg_deaths`, `avg_assists`;
- `total_lp_gain`;
- `lanes_played` nel formato `LANE-wins-losses, ...`.

Evidenza: [LeagueDB.java](../../src/main/java/com/safjnest/sql/database/LeagueDB.java:115).

## Contratto Mongo attuale

`LeagueService.getAdvancedLOLData` invoca `MongoDB.findAdvancedProfileProjections`, che ora raggruppa i participant Mongo per champion e restituisce le colonne aggregate richieste dal consumer.

Evidenza: [LeagueService.java](../../src/main/java/com/safjnest/lol/service/LeagueService.java:391), [MongoDB.java](../../src/main/java/com/safjnest/mongo/MongoDB.java:565) e [LeagueService.java](../../src/main/java/com/safjnest/lol/service/LeagueService.java:575).

Il documento match resta la sorgente dei participant, ma non viene più consegnato direttamente alla sezione advanced. Il consumer riceve `lanes_played` nel formato SQL compatibile e può continuare a eseguire `arrayColumn("lanes_played")`.

Evidenza: [LeagueMessage.java](../../src/main/java/com/safjnest/lol/message/LeagueMessage.java:431).

## Esito dopo il fix

### Fix applicato — aggregato profile

L’aggregazione ora produce `champion`, `games`, `wins`, `losses`, `avg_kills`, `avg_deaths`, `avg_assists`, `total_lp_gain` e `lanes_played`. Manca solo la prova runtime su dati reali e la pulizia delle eventuali chiavi Redis già calcolate con il vecchio mapping.

## Insert account

Il mirror dell’account è più lineare:

1. `LeagueDB.addLOLAccount` esegue SQL e commit;
2. passa il PUUID già presente nel modello Riot;
3. `MongoDB.mirrorSummoner` rilegge solo i campi compatibili della riga MariaDB;
4. `MongoDB.upsertSummoner` usa `puuid` come `_id` e non scrive identificativi numerici MariaDB.

Il replace Mongo è ora verificato tramite `UpdateResult`; una riga MariaDB non riletta o un errore di conversione vengono registrati dal mirror.

## Cache

Il risultato incompatibile viene memorizzato in `RedisKey.ADVANCED_LOL_DATA` per 24 ore dopo la conversione. Dopo una correzione del mapping, il test deve eliminare questa chiave o usare un `puuid`/intervallo nuovo.

Evidenza: [LeagueService.java](../../src/main/java/com/safjnest/lol/service/LeagueService.java:391).

## Verifica residua

Serve un test runtime con cache `ADVANCED_LOL_DATA` invalidata e confronto tra aggregato MariaDB e Mongo sullo stesso `puuid`, intervallo e queue.
