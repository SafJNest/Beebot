# Audit 02 — comando `summoner profile`

## Percorso runtime attuale

```text
/summoner profile
  -> risoluzione account/cache
  -> Riot API solo quando il profilo Mongo è incompleto
  -> LeagueService.upsertSummoner
  -> MongoDB.findAdvancedProfileProjections
  -> LeagueMessage.send
```

L’account e il profilo vengono persistiti direttamente in MongoDB. `LeagueDB` non è più coinvolto nel comando e non esiste un mirror MariaDB→Mongo nel runtime.

## Contratto Mongo

`LeagueService.getAdvancedLOLData` usa `MongoDB.findAdvancedProfileProjections`, che raggruppa i participant Mongo per champion e restituisce:

- `champion`;
- `games`, `wins`, `losses`;
- `avg_kills`, `avg_deaths`, `avg_assists`;
- `total_lp_gain`;
- `lanes_played` nel formato consumato dal messaggio.

Il documento match resta la sorgente dei participant; la projection consegna al consumer l’aggregato già compatibile con `LeagueMessage`.

## Account e cache

`UserData` legge gli account collegati con `MongoDB.findAccountsByUserId`. L’aggiunta usa `LeagueService.upsertSummoner`; la rimozione usa `MongoDB.detachSummonerUser` con filtro su PUUID e `userId`. Dopo add/unlink vengono invalidati i riferimenti Redis e la cache locale viene aggiornata.

Il PUUID è il valore stabile usato dall’autocomplete e dai lookup Mongo. La Riot API resta una sorgente di refresh, non una persistenza intermedia.

## Verifica

I test devono eseguire il flusso con una chiave Redis nuova o invalidata e confrontare la projection Mongo sullo stesso PUUID, intervallo e queue. La guardia runtime deve continuare a fallire se il codice del profilo reintroduce `LeagueDB`.
