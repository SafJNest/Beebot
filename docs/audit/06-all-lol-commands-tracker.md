# Audit 06 — comandi LoL e Tracker

- Data: 2026-07-22
- Tipo: audit statico end-to-end
- Scope: tutti i comandi sotto `commands/lol`, `LeagueMessage`, `LeagueService`, `LeagueDB`, `MongoDB`, `Tracker` e `TrackerScheduler`
- Fix applicati nello stesso pass: runtime LoL Mongo-only, schema match Mongo, query profile/OP.GG, account ownership e bans/eventi JSON

## Mappa dei comandi

| Comando | Entry point | Percorso dati | Esito statico |
|---|---|---|---|
| `/summoner profile` e `/summoner` prefix | `SummonerProfile`, `Summoner` | account/cache → Riot fallback → `LeagueService.upsertSummoner` → `ProfileStatisticsService` per PUUID+Filter → Redis | coerente, senza MariaDB runtime |
| `/summoner overview` | `SummonerOverview` | Riot identity → PUUID → `LeagueMessage` overview → Mongo profile/ranks/masteries/statistics | lettura senza lookup id numerico |
| `/summoner champion` | `SummonerChampion` | Riot identity → PUUID → match history Mongo → statistiche champion | lettura senza lookup id numerico |
| `/summoner link` | `SummonerLink` → `UserData.addRiotAccount` | `MongoDB.upsertSummoner` con ownership `userId` | coerente; aggiorna cache locale e Redis |
| `/summoner unlink` | `SummonerUnlink` → `UserData.deleteRiotAccount` | `MongoDB.detachSummonerUser` con ownership `userId` | coerente; dati match non vengono cancellati |
| `/summoner track` | `SummonerTrack` | `MongoDB.setSummonerTracking` → Tracker | coerente; il comando controlla l’esito Mongo |
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

`SummonerProfile` risolve e aggiorna l’account direttamente in Mongo. `LeagueMessage.getSummonerEmbed` legge:

1. identity e rank tramite `LeagueService`/Mongo;
2. statistiche aggregate flat tramite `ProfileStatisticsService` usando il `Filter` completo;
3. l'embed storico del profilo, alimentato dal nuovo aggregato senza modificare la presentazione;
4. `lastUpdate` del documento, quando l'aggregato è disponibile.

L’aggregato ora raggruppa i participant Mongo per totale, queue, lane, champion, matchup, duo e ping. Overview, profile e `!summoner` leggono lo stesso oggetto, ma mantengono le rispettive viste e il rispettivo formato; i matchup e la lista completa dei champion restano nelle pagine dedicate. `lastUpdate` viene scritto dopo il completamento del calcolo e mostrato nell'overview base e dal comando generico `!summoner`.

Il profilo HTTP carica invece `recentMatches` con una query `MatchResult` separata sullo stesso filtro. I match raw restano riservati a timeline e dettagli.

`SummonerOverview` e `SummonerChampion` propagano direttamente il PUUID al flusso Mongo. Se l’identità Riot è valida ma il documento non esiste ancora, la query Mongo restituisce un risultato vuoto/esplicito senza tentare un mapping MariaDB.

### OP.GG

Il comando carica le partite da Riot per mostrare subito l’embed. In parallelo:

1. l’account viene aggiornato direttamente in Mongo;
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
  → MongoDB.upsertMatchDocument
  → MongoDB.upsertParticipant per participant
  → MongoDB.updateMatchRank / match_events
```

Il documento `match` ora contiene:

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

### P0 — verificare il salvataggio Mongo

Il flusso runtime non usa più un id MariaDB intermedio. Il controllo da mantenere è che `MongoDB.upsertMatchDocument` confermi il match prima degli upsert dei participant e che gli update rank/eventi usino il full Riot match id.

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

Nel ramo OP.GG che legge `List<QueryRecord>`, `previousRow` viene individuata correttamente ma `prevRank` viene letto da `row` invece che da `previousRow`. Il dato `rank/lp/gain` arriva quindi dal nuovo contratto, ma il testo di promozione può mostrare il rank corrente anche come valore precedente.

### P2 — sincronizzazione cache

Le cache Redis del profile e dell’OP.GG hanno TTL propri. Una riconciliazione deve invalidare `PROFILE_STATISTICS`, `PROFILE_RECENT_MATCHES`, `SUMMONER_DATA` e la profile page dopo un batch Tracker completato.

## Verdetto

I comandi statici e Data Dragon non presentano un problema Mongo diretto. I flussi account, profile, OP.GG e match ora hanno contratti Mongo distinti e coerenti. Restano da verificare separatamente i dettagli di queue e la gestione dei participant Riot mancanti; non sono fallback MariaDB.
