# LoL HTTP API

Documentazione della superficie HTTP attualmente esposta da Beebot. Il contratto è ricavato dai controller Spring, da `LolApiParameters` e dai modelli canonici in `lol.model`.

Controller di riferimento:

- [`LolController`](../../src/main/java/com/safjnest/spring/controller/LolController.java): search, profile e match;
- [`ChampionController`](../../src/main/java/com/safjnest/spring/controller/ChampionController.java): dati champion;
- [`LeaderboardController`](../../src/main/java/com/safjnest/spring/controller/LeaderboardController.java): leaderboard e distribuzioni.

## Regole comuni

Tutti gli endpoint sono `GET`. I parametri enum sono case-insensitive e vengono sottoposti a `trim()`.

Gli errori HTTP usano sempre questo envelope:

```json
{
  "status": 400,
  "code": "invalid_request",
  "message": "Invalid queue: must be one of: ..."
}
```

| Status | Significato |
|---|---|
| `200` | Risposta pronta; vale anche per una risposta `PARTIAL`. |
| `202` | Il dato manca, la generazione è stata avviata in background e va richiesto di nuovo. |
| `400` | Path/query parameter mancante, enum non valido o combinazione non supportata. |
| `404` | Risorsa o endpoint inesistente. |
| `405` | Metodo HTTP non supportato. |
| `500` | Errore inatteso del server. |

`202` usa `LolApiError`, per esempio:

```json
{
  "status": 202,
  "code": "champion_data_pending",
  "message": "Champion data is being prepared"
}
```

Il flusso dei dati LoL resta centralizzato nei service: Redis, database e lavoro asincrono tramite `ProfileBootstrapService` o `Tracker`. Le request HTTP non eseguono calcoli pesanti né fetch Riot sincroni per i profili mancanti. La sola coda applicativa mantenuta è quella del flusso match.

## Superficie disponibile

| Metodo | Endpoint | Controller | Risposta principale |
|---|---|---|---|
| `GET` | `/api/lol/{shard}/search` | `LolController` | `List<SummonerView>` |
| `GET` | `/api/lol/{shard}/profile/{puuid}` | `LolController` | `SummonerView` oppure `202` |
| `GET` | `/api/lol/{shard}/profile-by-name/{gameName}/{tagLine}` | `LolController` | `SummonerView` oppure `202` |
| `GET` | `/api/lol/{shard}/match/{gameId}` | `LolController` | `Match` oppure `202` |
| `GET` | `/api/lol/champion/{champion}` | `ChampionController` | `ChampionView` oppure `202` |
| `GET` | `/api/lol/leaderboard` | `LeaderboardController` | `LeaderboardPage` |
| `GET` | `/api/lol/leaderboard/rank-distribution` | `LeaderboardController` | `LeaderboardDistribution` |
| `GET` | `/api/lol/leaderboard/top-regions` | `LeaderboardController` | `LeaderboardDistribution` |

## Shard e regioni

### `{shard}` nel path

Gli endpoint search, profile e match richiedono uno shard nel path. Il valore deve essere il nome esatto dell'enum R4J, senza alias come `EUW` o `EUNE`:

| Valore | Area |
|---|---|
| `BR1` | Brazil |
| `EUN1` | Europe Nordic & East |
| `EUW1` | Europe West |
| `JP1` | Japan |
| `KR` | Korea |
| `LA1` | Latin America North |
| `LA2` | Latin America South |
| `NA1` | North America |
| `OC1` | Oceania |
| `TR1` | Turkey |
| `RU` | Russia |
| `PBE1` | Public Beta Environment |
| `SG2` | Singapore |
| `PH2` | Philippines |
| `ID1` | Indonesia |
| `VN2` | Vietnam |
| `TH2` | Thailand |
| `TW2` | Taiwan |
| `ME1` | Middle East |

`UNKNOWN` è rifiutato. La lista precedente è quella accettata dal parser R4J presente nel progetto; la lista UI di `LeagueShardUtils.getActives()` è più restrittiva e non include `PBE1`, `PH2`, `ID1` e `TH2`.

### `region` in query

Champion e leaderboard non hanno lo shard nel path: usano il parametro opzionale `region`.

- se omesso, il servizio usa l'aggregato interno `GLOBAL`;
- `GLOBAL` non è un valore valido da inviare come parametro;
- se presente, accetta gli stessi 19 shard sopra elencati;
- `region=unknown` e `region=GLOBAL` producono `400`.

## Queue

Il parametro `queue` è opzionale negli endpoint champion e leaderboard. Il default è:

```text
TEAM_BUILDER_RANKED_SOLO
```

Nel leaderboard `TEAM_BUILDER_RANKED_SOLO` viene normalizzato al valore canonico `RANKED_SOLO_5X5`. Negli endpoint champion il valore passato viene invece usato direttamente nel filtro.

Il parser non applica una whitelist locale: accetta ogni costante di `GameQueueType` dell'attuale versione R4J. Questo non significa che ogni queue abbia dati persistiti o sia semanticamente utile per tutti gli endpoint. Le queue più rilevanti per i dati attuali sono:

| Queue | Uso |
|---|---|
| `RANKED_SOLO_5X5` | Solo/Duo ranked; queue canonica della leaderboard |
| `TEAM_BUILDER_RANKED_SOLO` | Solo/Duo ranked; default API e dataset match |
| `RANKED_FLEX_SR` | Ranked Flex |
| `TEAM_BUILDER_DRAFT_UNRANKED_5X5` | Draft Pick |
| `TEAM_BUILDER_DRAFT_RANKED_5X5` | Draft ranked legacy |
| `NORMAL_5V5_BLIND_PICK` | Blind Pick |
| `QUICKPLAY_NORMAL` | Quickplay |
| `SWIFTPLAY` | Swiftplay |
| `ARAM` | ARAM |
| `ARAM_CLASH` | ARAM Clash |
| `CHERRY` | Arena |
| `STRAWBERRY` | Swarm |
| `NEXUS_BLITZ` | Nexus Blitz |
| `ULTBOOK` | Ultimate Spellbook |
| `URF` | URF |
| `ALL_RANDOM_URF` | ARURF |
| `ONEFORALL_5X5` | One for All |
| `DOOMBOTS_V2` | Doom Bots |
| `CLASH` | Clash |

Valori accettati dal parser R4J, comprese queue storiche, speciali, TFT e custom:

```text
CUSTOM, NORMAL_3X3_BLIND_PICK_OLD, NORMAL_5X5_BLIND_PICK_OLD,
NORMAL_5X5_DRAFT, RANKED_SOLO_5X5, RANKED_PREMADE_5X5,
RANKED_PREMADE_3X3, RANKED_TEAM_3X3, RANKED_TEAM_5X5,
ODIN_5X5_BLIND, ODIN_5X5_DRAFT, BOT_5X5, BOT_ODIN_5X5,
BOT_5X5_INTRO_OLD, BOT_5X5_BEGINNER_OLD, BOT_5X5_INTERMEDIATE_OLD,
BOT_3X3_BEGINNER_OLD, GROUP_FINDER_5X5, ARAM_5X5_OLD, ONEFORALL_5X5,
FIRSTBLOOD_1X1, FIRSTBLOOD_2X2, HEXAKILL_6X6_SR, URF_5X5,
ONE_FOR_ALL_MIRROR, BOT_URF_5X5, NIGHTMARE_BOT_5X5_RANK1,
NIGHTMARE_BOT_5X5_RANK2, NIGHTMARE_BOT_5X5_RANK5, ASCENSION_5X5,
HEXAKILL, BILGEWATER_ARAM_5X5, KING_PORO_5X5, COUNTER_PICK,
BILGEWATER_5X5, NEXUS_SIEGE_OLD, DEFINITELY_NOT_DOMINION_5X5,
ALL_RANDOM_URF, SNOW_BATTLE_ARURF, OVERCHARGE, ARAM_5X5,
TEAM_BUILDER_DRAFT_UNRANKED_5X5, TEAM_BUILDER_DRAFT_RANKED_5X5,
TEAM_BUILDER_RANKED_SOLO, NORMAL_5V5_BLIND_PICK, RANKED_FLEX_SR,
ARAM, NORMAL_3X3_BLIND_PICK, RANKED_FLEX_TT, ASSASSINATE_5X5,
DARKSTAR_3X3, CLASH, ARURF_CLASH, BOT_3X3_INTERMEDIATE,
BOT_3X3_INTRO, BOT_3X3_BEGINNER, BOT_5X5_INTRO, BOT_5X5_BEGINNER,
BOT_5X5_INTERMEDIATE, NEXUS_SIEGE, NIGHTMARE_BOT_5X5_VOTE,
NIGHTMARE_BOT_5X5, INVASION_NORMAL, INVASION_ONSLAUGHT, NEXUS_BLITZ,
ODYSSEY_INTRO, ODYSSEY_CADET, ODYSSEY_CREWMEMBER, ODYSSEY_CAPTAIN,
ODYSSEY_ONSLAUGHT, STRAWBERRY, TUTORIAL_MODULE_1, TUTORIAL_MODULE_2,
TUTORIAL_MODULE_3, TEAMFIGHT_TACTICS, TEAMFIGHT_TACTICS_RANKED,
TEAMFIGHT_TACTICS_TUTORIAL, TEAMFIGHT_TACTICS_SIMULATION,
TEAMFIGHT_TACTICS_HYPER_ROLL, TEAMFIGHT_TACTICS_HYPER_ROLL_1V0,
TEAMFIGHT_TACTICS_DOUBLE_UP_2V0, TEAMFIGHT_TACTICS_DOUBLE_UP_4V0,
TEAMFIGHT_TACTICS_DOUBLE_UP, TEAMFIGHT_TACTICS_DOUBLE_UP_1V7_BOTS,
TEAMFIGHT_TACTICS_DOUBLE_UP_WORKSHOP, TEAMFIGHT_TACTICS_FORTUNE_FAVOR,
TEAMFIGHT_TACTICS_SOUL_BRAWL, TEAMFIGHT_TACTICS_CHONCC_TREASURE,
TEAMFIGHT_TACTICS_TOCKER_S_TRIALS, TEAMFIGHT_TACTICS_PENGU_S_PARTY,
TEAMFIGHT_TACTICS_AO_SHINS_ASCENT,
TEAMFIGHT_TACTICS_SET3_5_REVIVAL_GALAXIES,
TEAMFIGHT_TACTICS_REVIVAL_DAWN_OF_HEROES,
TEAMFIGHT_TACTICS_REVIVAL_FESTIVAL_OF_BEASTS,
TEAMFIGHT_TACTICS_SET_QUEUE_1, TEAMFIGHT_TACTICS_SET_QUEUE_2,
TEAMFIGHT_TACTICS_SET_QUEUE_3, CHERRY, BRAWL, URF_1V1, URF_2V2,
URF_3V3, URF_4V4, URF, ULTBOOK, KIWI, ONE_VS_ONE, TWO_VS_TWO,
THREE_VS_THREE, FOUR_VS_FOUR, TFT_CUSTOM, TFT_HYPER_ROLL_CUSTOM,
ARAM_CLASH, ARAM_BOTS, QUICKPLAY_NORMAL, SWIFTPLAY, DOOMBOTS_V2,
PRACTICE_TOOL, CUSTOM_SUMMONERS_RIFT, CUSTOM_ARAM
```

## Rank e role

### Rank di filtro

I parametri `rank` accettano solo il tier, non una divisione:

```text
CHALLENGER, GRANDMASTER, MASTER, DIAMOND, EMERALD,
PLATINUM, GOLD, SILVER, BRONZE, IRON, UNRANKED
```

Il significato dipende dall'endpoint:

- champion: il rank fornito è il minimo tier del filtro, quindi `EMERALD` include Emerald e i tier superiori secondo il filtro `GREATER_OR_EQUAL`;
- champion: il codice applica inoltre un caso speciale a `CHALLENGER`, che interroga `CHALLENGER` e `GRANDMASTER`;
- leaderboard: il rank seleziona quel tier e tutte le sue divisioni, quindi `EMERALD` seleziona Emerald I-IV;
- rank distribution: restituisce sempre i tier competitivi `CHALLENGER` fino a `IRON`, non `UNRANKED`;
- top-regions: `rank` è il tier esatto aggregato per regione.

Nel payload dei profili e della leaderboard `Rank.tier` è invece una divisione R4J, ad esempio `DIAMOND_II`, con `lp`, `wins` e `losses`. Sono possibili anche divisioni storiche `_V` e `UNRANKED` perché fanno parte del modello R4J.

### Role

Il parametro `role` è accettato solo da `/champion/{champion}` e solo con questi valori:

```text
TOP, JUNGLE, MID, BOT, UTILITY
```

`role` è rifiutato con `400` se la queue selezionata non supporta una lane. Le queue senza lane definite dal codice sono:

```text
CHERRY, ULTBOOK, URF, ALL_RANDOM_URF, DOOMBOTS_V2,
ONEFORALL_5X5, ARAM, ARAM_CLASH, NEXUS_BLITZ, STRAWBERRY
```

## Endpoint

### 1. Search summoner

```http
GET /api/lol/{shard}/search?q={query}
```

| Parametro | Obbligatorio | Descrizione |
|---|---:|---|
| `shard` | sì | Shard League nel path. |
| `q` | sì | Query Riot ID; viene normalizzata in lowercase rimuovendo spazi, `-` e `#`. |

La ricerca è autocomplete/prefix search, limitata a 25 risultati nello shard richiesto. `q=Player#EUW` e `q=PlayerEUW` producono la stessa normalizzazione; in una URL il carattere `#` va codificato come `%23`.

Risposta `200`: lista di `SummonerView`. Per la search il campo `overview` è una struttura vuota; viene restituito il rank Solo/Duo quando disponibile, altrimenti `UNRANKED`.

### 2. Profilo per PUUID

```http
GET /api/lol/{shard}/profile/{puuid}
```

| Parametro | Obbligatorio | Descrizione |
|---|---:|---|
| `shard` | sì | Shard League del profilo. |
| `puuid` | sì | PUUID Riot canonico del summoner. |

Risposta `200`: `SummonerView` completo:

```text
summoner   -> summonerId, puuid, riotId, region, level, icon
ranks      -> queue, tier, lp, wins, losses
overview   -> statistics, masteries, champions, form, mostPlayed, recentMatches
```

`recentMatches` contiene i `MatchResult` leggeri, mentre `Match` completo è riservato al dettaglio match. Se il summoner esiste nel DB ma le statistiche aggregate non sono ancora disponibili, il profilo resta `200` con i dati disponibili e il refresh viene avviato immediatamente in background.

Risposta `202`: `LolApiError` con codice `profile_pending` quando il summoner non è ancora presente nella tabella `summoner`. Il bootstrap Riot → DB viene avviato in background.

Risposta `404`: profilo non trovato.

### 3. Profilo per Riot ID

```http
GET /api/lol/{shard}/profile-by-name/{gameName}/{tagLine}
```

| Parametro | Obbligatorio | Descrizione |
|---|---:|---|
| `shard` | sì | Shard League su cui risolvere l'account. |
| `gameName` | sì | Parte prima di `#` del Riot ID. |
| `tagLine` | sì | Parte dopo `#` del Riot ID. |

Il servizio risolve prima il Riot ID in PUUID e poi usa lo stesso flusso di `/profile/{puuid}`. I valori sono segmenti di path: caratteri riservati devono essere URL-encoded.

Risposta `200`: stesso `SummonerView` del profilo per PUUID.

Risposta `202`: stesso `profile_pending` del profilo per PUUID quando il summoner non è ancora presente nella tabella `summoner`.

Risposta `404`: Riot ID non risolto o profilo non trovato.

### 4. Dettaglio match

```http
GET /api/lol/{shard}/match/{gameId}
```

| Parametro | Obbligatorio | Descrizione |
|---|---:|---|
| `shard` | sì | Shard associato al match. |
| `gameId` | sì | ID match Riot, normalmente `EUW1_134131`; è accettato anche il solo identificativo numerico. |

Il prefisso prima di `_` viene rimosso per la lookup SQL, mentre lo shard del path resta la fonte usata per la regione. Il flusso è:

```text
Redis detail -> database -> enqueue Tracker -> analisi asincrona Riot
```

Risposta `200`: `Match` completo con:

```text
id, gameId, leagueShard, queue, rank, lastUpdate,
bans, events, timeStart, timeEnd, patch, participants
```

Ogni `Participant` può includere identità (`puuid`, `riotId`, `riotTag`), esito, champion, lane, team, rank/LP, statistiche, item, spell, rune, skill order, augments e build path.

Risposta `202`:

```json
{
  "status": 202,
  "code": "match_pending",
  "message": "Match analysis is pending"
}
```

Risposta `404`: il match è stato cercato e marcato definitivamente come non trovato.

### 5. Dati champion

```http
GET /api/lol/champion/{champion}?rank={rank}&region={region}&queue={queue}&role={role}
```

| Parametro | Obbligatorio | Default | Descrizione |
|---|---:|---|---|
| `champion` | sì | — | Nome champion esatto, case-insensitive; la normalizzazione rimuove spazi, apostrofi e alcuni caratteri speciali. |
| `rank` | no | nessun filtro | Tier minimo del dataset. |
| `region` | no | `GLOBAL` interno | Shard di aggregazione; non inviare `GLOBAL`. |
| `queue` | no | `TEAM_BUILDER_RANKED_SOLO` | Queue del dataset. |
| `role` | no | nessun filtro | Una delle cinque role giocabili. |

Risposta `200`: `ChampionView`:

```text
champion       -> id, name, image
stats          -> overview, laneStats, powerCurve, trend,
                  matchups (tutti), laneSynergies (tutte)
stats.overview -> games, picks, bans, wins, winrate, pickrate,
                  banrate, kda, csPerMinute, goldPerMinute,
                  damageProfile
build          -> coreBuilds, coreItems, starters, boots,
                  supportItems, slots, runes, summonerSpells,
                  skillOrders, prismatics, augments
```

Ogni opzione build espone il proprio `id`/configurazione, `matches`, `wins`, `winrate` e `pickrate`. Le categorie build hanno massimo tre opzioni; con pochi dati ne possono avere una e senza dati sono liste vuote. Gli starter sono aggregati per configurazione completa, quindi la stessa pozione ripetuta più volte mantiene la propria cardinalità nella chiave. `augments` è indicizzato per slot (`augment 1` ... `augment 4`) e non è una lista piatta. Il primo elemento non ha significato implicito.

`matchups` contiene tutti i matchup validi con champion avversario, lane, matches, wins, winrate, deltaWinrate, goldDiffAt15, csDiffAt15, soloKillRate, killParticipation, opponentBanRate e metricGames. `laneSynergies` contiene tutte le synergy valide con champion/lane alleato, matches, wins, winrate e pickrate. Metriche non disponibili restano `null`.

Il campo interno `filter` di `ChampionStatistics` e `Build` non viene serializzato nell'HTTP JSON. Il valore di `champion` deve corrispondere al nome statico; un champion sconosciuto produce `404`.

Risposte aggiuntive:

- `202 champion_data_pending` se statistiche o build non sono ancora persistite; il refresh viene avviato immediatamente e non calcolato nella request;
- `400` per rank, region, queue o role non validi, o role incompatibile con la queue;
- `404` per champion sconosciuto.

### 6. Leaderboard paginata

```http
GET /api/lol/leaderboard?rank={rank}&queue={queue}&page={page}&limit={limit}
```

| Parametro | Obbligatorio | Default | Descrizione |
|---|---:|---|---|
| `rank` | no | tutti gli utenti | Tier richiesto, con tutte le divisioni del tier. Se omesso, include tutti gli utenti della leaderboard. |
| `region` | no | tutti gli shard | Shard da filtrare; per tutti gli shard il parametro viene omesso. |
| `queue` | no | `TEAM_BUILDER_RANKED_SOLO` | Queue da filtrare. |
| `page` | no | `1` | Pagina 1-based, deve essere `>= 1`. |
| `limit` | no | `50` | Righe per pagina, intero tra `1` e `50`. |

La dimensione pagina è controllata da `limit`. Risposta `200`:

```text
page, pageSize, total, pages,
summoners[] -> position, summoner
```

Ogni `summoner` è lo stesso `SummonerView` usato dal profilo, con `ranks`, `overview.statistics`, `overview.masteries` e i match recenti disponibili nello stesso contratto. Se mancano statistiche per una o più righe, il refresh viene avviato immediatamente e l'endpoint restituisce `202 leaderboard_pending`; la pagina completa viene restituita con `200` al retry successivo.

Se `rank` e `region` sono omessi, la leaderboard restituisce tutti gli utenti in ordine decrescente di `mmr`, con paginazione da 50 righe oppure dal valore di `limit`. Per filtrare uno shard si aggiunge `&region={region}`. Il totale e le righe vengono calcolati lato database, quindi il dataset può contenere anche milioni di utenti senza costruire una risposta unica.

### 7. Distribuzione dei rank

```http
GET /api/lol/leaderboard/rank-distribution?queue={queue}
```

| Parametro | Obbligatorio | Default | Descrizione |
|---|---:|---|---|
| `region` | no | tutti gli shard | Shard da aggregare; per tutti gli shard il parametro viene omesso. |
| `queue` | no | `TEAM_BUILDER_RANKED_SOLO` | Queue da aggregare. |

Risposta `200`: `LeaderboardDistribution` con `entries[]`, dove ogni entry ha:

```text
key     -> CHALLENGER, GRANDMASTER, MASTER, DIAMOND, EMERALD,
           PLATINUM, GOLD, SILVER, BRONZE o IRON
players -> numero di player
```

La risposta non è paginata e contiene anche entry a `0` quando il rebuild ha seminato la combinazione senza player. `UNRANKED` non fa parte della distribuzione competitiva.

### 8. Top region per rank

```http
GET /api/lol/leaderboard/top-regions?rank={rank}&queue={queue}
```

| Parametro | Obbligatorio | Default | Descrizione |
|---|---:|---|---|
| `rank` | sì | — | Tier esatto da aggregare. |
| `queue` | no | `TEAM_BUILDER_RANKED_SOLO` | Queue da aggregare. |

Risposta `200`: `LeaderboardDistribution` con entry `{key, players}`, dove `key` è lo shard (`EUW1`, `NA1`, ecc.). Le regioni sono ordinate per numero di player decrescente e poi per nome.

## Modelli JSON canonici

| Modello | Uso |
|---|---|
| `Summoner` | Identità base: `summonerId`, `puuid`, `riotId`, `region`, `level`, `icon`. |
| `Rank` | Ranked queue: `queue`, `tier`, `lp`, `wins`, `losses`. |
| `Mastery` | `championId`, `level`, `points`. |
| `SummonerView` | Profilo completo, condiviso con la leaderboard. |
| `MatchResult` | Match leggero per liste e overview. |
| `Match` | Match completo con eventi, ban e partecipanti. |
| `ChampionView` | Champion, statistiche aggregate e unico aggregato build/options. |
| `LeaderboardPage` | Metadati pagina e righe leaderboard. |
| `LeaderboardDistribution` | Conteggi per rank o regione. |

I success payload usano i modelli canonici sotto `com.safjnest.lol.model`; Spring mantiene solo controller, parsing HTTP e il modello errore `LolApiError`. Le regole derivano da [ADR-0001](../architecture/adr/0001-canonical-lol-model-boundaries.md), [ADR-0003](../architecture/adr/0003-match-and-match-result-models.md), [ADR-0005](../architecture/adr/0005-lol-api-json-contract.md), [ADR-0006](../architecture/adr/0006-champion-api-contract.md), [ADR-0007](../architecture/adr/0007-unified-api-result-and-parameters.md) e [ADR-0008](../architecture/adr/0008-endpoint-cache-and-async-lookups.md).
