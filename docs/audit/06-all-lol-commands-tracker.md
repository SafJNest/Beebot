# Audit 06 — comandi LoL e Tracker

- Data: 2026-07-19
- Tipo: audit statico end-to-end
- Scope: tutti i comandi sotto `commands/lol`, `LeagueMessage`, `LeagueService`, `LeagueDB`, `MongoDB`, `Tracker` e `TrackerScheduler`
- Fix applicati nello stesso pass: schema match Mongo, query profile/OP.GG, ack delle write Mongo e bans JSON

## Mappa dei comandi

| Comando | Entry point | Percorso dati | Esito statico |
|---|---|---|---|
| `/summoner profile` e `/summoner` prefix | `SummonerProfile`, `Summoner` | Riot summoner → `LeagueDB.addLOLAccount` → mirror Mongo → profile/advanced query Mongo → Redis | coerente dopo la separazione aggregate/participant |
| `/summoner overview` | `SummonerOverview` | Riot identity → id Mongo → `LeagueMessage` overview → Mongo profile/ranks/masteries/statistics | rischio su summoner non trovato: manca guardia prima di usare `summoner.getPUUID()` |
| `/summoner champion` | `SummonerChampion` | Riot identity → id Mongo → match history Mongo → statistiche champion | stesso rischio di overview su id `0` o summoner nullo |
| `/summoner link` | `SummonerLink` → `UserData.addRiotAccount` | MariaDB account/user → mirror summoner Mongo | coerente; verifica il risultato SQL prima di confermare il link |
| `/summoner unlink` | `SummonerUnlink` → `UserData.deleteRiotAccount` | MariaDB detach → `MongoDB.detachSummoner` | coerente; dati match non vengono cancellati, come previsto |
| `/summoner track` | `SummonerTrack` | MariaDB tracking update → mirror `tracking` Mongo → Tracker | **P1**: il comando non controlla il booleano restituito da `trackSummoner` |
| `/opgg` | `Opgg` → `LeagueMessage.getOpggEmbed` | Riot match list/detail → query participant Mongo per rank/lp/gain → Tracker queue | coerente dopo `findSummonerData`; persistenza match è asincrona |
| `/livegame` | `Livegame` → spectator flow | Redis spectator → Riot spectator API; account mirror iniziale | coerente, non è un flusso match persistito |
| `/champion` | `Champion` | `ChampionStatsService` + `BuildService` → Redis/Mongo aggregate → embed | coerente staticamente; non usa MariaDB match direttamente |
| `/champions` | `Champions` → `LeagueMessage` | champion aggregate Mongo/Redis → ranking embed | coerente staticamente; dipende da refresh Tracker |
| `/augment` | `Augment` | catalogo augment in memoria/Riot | fuori dal persistence match |
| `/item` | `Item` | Data Dragon/cache R4J | fuori dal persistence match |
| `/region` | `Region` | `GuildData`/`ChannelData` | fuori da LeagueDB/Mongo |
| `/ultimatebravery` | `UltimateBravery` | Riot/Data Dragon + payload generato | fuori dal persistence match |

## Flussi verificati

### Profile e overview

`SummonerProfile` aggiunge l’account in MariaDB. Dopo il commit, `LeagueDB.addLOLAccount` chiama il mirror Mongo. `LeagueMessage.getSummonerEmbed` legge:

1. identity e rank tramite `LeagueService`/Mongo;
2. statistiche aggregate tramite `findAdvancedProfileProjections`;
3. storico participant tramite `findMatchHistory` quando viene aperta la vista match;
4. cache Redis dopo una lettura completa.

Il profile aggregate ora raggruppa i participant Mongo per `champion` e produce `games`, `wins`, `losses`, medie KDA, `total_lp_gain` e `lanes_played`, cioè le chiavi richieste dall’embed legacy.

`SummonerOverview` e `SummonerChampion` non eseguono `LeagueDB.addLOLAccount`. Se l’identità Riot è valida ma non esiste ancora in Mongo, `getSummonerIdByPuuid` può restituire `0`; il comportamento deve essere verificato come risposta vuota/errore esplicito invece di proseguire con un id inesistente.

### OP.GG

Il comando carica le partite da Riot per mostrare subito l’embed. In parallelo:

1. l’account viene scritto/mirrorato;
2. `LeagueService.getSummonerData` legge le partite Mongo ordinate cronologicamente;
3. `findSummonerData` estrae dal participant del summoner `game_id`, `rank`, `lp`, `gain`, `win`, `time_start`, `time_end` e `patch`;
4. `Tracker.queueMatch` inserisce il full Riot match id nella coda Redis;
5. il worker Tracker persiste match, participant, rank medio ed eventi.

L’embed può quindi essere renderizzato prima che il worker abbia terminato la persistenza. Questo è un comportamento asincrono previsto, ma non deve essere interpretato come match non trovato.

### Tracker e scrittura match

Il percorso principale è:

```text
Riot match
  → Tracker.analyzeMatchHistory
  → LeagueDB.saveMatch + commit MariaDB
  → MongoDB.mirrorMatch
  → LeagueDB.setSummonerData per participant
  → MongoDB.mirrorParticipant
  → LeagueDB.setMatchRank / setMatchEvent
  → MongoDB.updateMatchRank / updateMatchEvents
```

Il documento `lol_matches` ora contiene:

```json
{
  "_id": "EUW1_23",
  "region": "EUW1",
  "game_id": "23",
  "fullGameId": "EUW1_23",
  "participants": [
    {
      "rank": "GOLD_II",
      "lp": 73,
      "gain": 21
    }
  ]
}
```

`rank`, `lp` e `gain` sono dati del participant, non del match. Gli enum vengono serializzati con `name()`, i bans sono `BLUE`/`RED` e i participant restano flat.

## Rischi aperti del Tracker

### P0 — salvare dopo un `saveMatch` fallito

`Tracker.analyzeMatchHistory(LOLMatch, Summoner, QueryRecord)` continua il flusso anche se `LeagueDB.saveMatch` restituisce `0`. In quel caso può tentare di inserire participant con `match_id = 0` e aggiornare rank/eventi con un id non valido. Il task deve fermarsi e loggare il match completo quando il commit MariaDB non produce un id.

### P1 — queue richiesta ignorata

`analyzeMatchHistory(GameQueueType queue, Summoner summoner)` riceve una queue ma costruisce sempre la match list `TEAM_BUILDER_RANKED_SOLO`. `toTrack` include anche `CHERRY`; il ramo non è quindi realmente supportato dal parametro ricevuto.

### P1 — participant Riot non risolto

`checkSummoner` usa `summoner.getPUUID()` prima di verificare `summoner != null`. Un participant sconosciuto può interrompere l’intero batch invece di essere saltato con errore associato al puuid.

### P1 — queue Redis letta con struttura diversa

`queueMatch` usa `RedisClient.sadd` e `popQueue` usa `smembers`, mentre `copyQueue` usa `lrangeAll` sulla stessa chiave. `copyQueue` non è compatibile con il writer set e va rimosso o corretto nel prossimo pass.

### P1 — match Riot assente nella coda

`popQueue` trasforma ogni id Redis in `LeagueService.getMatch` senza filtrare i `null`. Un match scaduto o non più disponibile può arrivare a `analyzeMatchHistory` e causare errore non contestualizzato.

### P2 — eventi corrotti normalizzati a vuoto

`createJSONEvents` sostituisce payload non decodificabili con array vuoti. Il flusso non fallisce, ma perde informazione e rende indistinguibile “nessun evento” da “conversione fallita”.

### P2 — testo LP precedente calcolato dalla riga corrente

Nel ramo OP.GG che legge `QueryResult`, `previousRow` viene individuata correttamente ma `prevRank` viene letto da `row` invece che da `previousRow`. Il dato `rank/lp/gain` arriva quindi dal nuovo contratto, ma il testo di promozione può mostrare il rank corrente anche come valore precedente.

### P2 — sincronizzazione cache

Il mirror aggiorna Mongo, mentre alcune cache Redis del profile e dell’OP.GG hanno TTL propri. Una riconciliazione deve invalidare `SUMMONER_DATA`, `ADVANCED_LOL_DATA` e profile page dopo un batch Tracker completato.

## Verdetto

I comandi statici e Data Dragon non presentano un problema Mongo diretto. I flussi profile, OP.GG e match ora hanno contratti Mongo distinti e coerenti. Il Tracker non è ancora dichiarabile completamente affidabile: il blocco su `saveMatch == 0`, la queue ignorata e la gestione dei participant mancanti restano fix separati e documentati, non modificati in questo pass.
