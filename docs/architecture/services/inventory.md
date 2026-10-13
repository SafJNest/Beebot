# Inventario e confini del monolite

Stato: analisi iniziale del 2026-10-10, baseline `cef023bc`.
Hive è sotto ADR-0017 e la tranche R4B è accettata sotto ADR-0018; il
[manifest R4B](r4b-inventory.md) documenta il nuovo confine. Tabelle numeriche e
accoppiamenti qui sotto documentano la baseline precedente, non un nuovo census.
Il target è descritto nel [documento principale](README.md).

## Metodo e copertura

CodeGraph risultava aggiornato: 518 file indicizzati, 12.769 nodi, 33.382 archi.
Sono stati inventariati tutti i 424 file Java principali, inclusi 10 file VoiceRSS,
e 69 file Java di test. Le analisi dei flussi e dei simboli usano CodeGraph;
l'analisi degli import è testuale e non rileva da sola riferimenti qualificati,
reflection o dipendenze comportamentali.

L'esplorazione approfondita riguarda bootstrap, API, modelli finali e dipendenze,
job/scheduler/worker, accesso Riot, Redis/JSON e strutture statistiche/build.
I domini non LoL sono classificati per area e import, senza un audit funzionale
completo di ogni comando. Il [manifest per file](source-inventory.md) rende
esplicita questa distinzione e copre anche le classi non destinate al comune.

| Area attuale sotto `com.safjnest` | File | Destinazione preliminare |
|---|---:|---|
| `App` | 1 | Separare i bootstrap dei tre servizi |
| `commands` | 131 | Bbot; comandi LoL/operatori diventano client del proprietario |
| `core` | 39 | Bbot; valutare separatamente `Chronos` |
| `lol` | 146 | Suddivisione dettagliata sotto |
| `model` | 35 | Bbot; configurazione globale da dividere per servizio |
| `nosql` | 5 | Spring/Mongo; il vecchio tooling di migrazione è stato rimosso |
| `redis` | 3 | Adapter Redis candidati al riuso, policy locali |
| `spring` | 13 | Spring; schema errore candidato al comune |
| `sql` | 7 | Bbot per domini propri; nessun reader SQL LoL dedicato |
| `status` | 6 | Raccolta locale nei servizi e composizione in Spring |
| `utils` | 26 | Bbot oppure parti pure da qualificare; non spostamento globale |
| `org.voicerss.tts` | 10 | Bbot/audio |

Dentro `lol`: 78 file model, 15 queue, 17 service, 23 utils, 6 champion,
4 message, 2 tracker e `LeagueHandler`.

## Principali accoppiamenti verificati

| Evidenza | Implicazione per l'estrazione |
|---|---|
| `App.main` avvia Spring, Twitch, metriche, code, warmup leaderboard e bot; shutdown chiude componenti di più domini | Tre entry point e lifecycle distinti; il restart del bot non deve controllare Spring/R4J |
| `QueueHandler.start` registra `SyncScheduler`, `RiotScheduler`, `ComputeScheduler` | Il comune deve ricevere gli scheduler dall'applicazione |
| `Router` usa `Registry.instance()` per il globale e `BotLogger` per promozioni | Runtime per istanza e callback/logger indipendente dal bot |
| `Registry.runType` interpreta `tracking`, `sample-games:*`, `rank-entries` | La proiezione dei run LoL appartiene al servizio, non al registry generico |
| `Registry` assegna PID/PPID con contatore e contesto `ThreadLocal` | L'albero attuale vale dentro una JVM; non attraversa HTTP automaticamente |
| `RiotScheduler` usa `LeagueShard` e `BotLogger` | Adapter di R4J sopra un core generico |
| `ComputeScheduler` contiene facade, key LoL, merge delle matrici e riserva champion | Adapter e orchestration di Spring/Mongo, esclusi dal comune |
| `SyncScheduler` usa `LeagueShard` e una virtual thread per job | Meccanismo di esecuzione riusabile; route e attivazione restano locali |
| `SummonerService.getRiotSummonerAsync` chiama Riot, scrive cache R4J e poi persiste via `saveAsync` | Separare acquisizione esterna e persistenza; non spostare l'intero servizio in R4J |
| `RankService`/`MasteryService` combinano fetch Riot con scritture Mongo e proiezioni | R4J restituisce dati; Spring mantiene il writer canonico |
| `Summoner` e `Match` dipendono da `AbstractEntity`; `Match` importa anche `Tracker` | Prima isolare dati e metodi operativi, poi estrarre il contratto canonico |
| `SummonerOverview.from` richiama statistiche e `ChampionUtils` | Il risultato è comune; composizione e lookup statici restano al produttore |
| `ProfileActivity` e `ProfileMatchups` contengono accumulatori | Record finali comuni, calcolo e strutture temporanee in Spring |
| `Build.RuneOption.configuration` è `RuneSignature` | La forma rune è comune anche se oggi vive in `lol.champion` |
| `JsonCodec` contiene JSON e conversione `org.bson.Document` | Separare codec di trasporto e conversione BSON |
| `LolApiConfig` nasconde `Build.filter` e `ChampionStatistics.filter` mediante mixin | Il JSON HTTP differisce dal JSON di storage; serve una prova di compatibilità specifica |
| `RedisKey` usa `App.isTesting()` e mescola namespace R4J/LoL/status; `RedisClient` usa pool statico localhost | Configurazione, lifecycle e cataloghi chiavi per servizio; il solo client tecnico è candidato al riuso |
| `LeagueHandler` concentra SDK, dati statici, Mongo, comandi JDA, cache e formattazione | Dividerlo per responsabilità, senza farne una dipendenza del comune |

Una scansione degli import trova 79 sorgenti principali con import R4J e 12
con import `App`: è una misura dell'accoppiamento visibile, non il conteggio
di tutti i chiamanti. Gli impact per nomi omonimi possono includere anche
`core.audio.QueueHandler`, che resta la coda audio del bot.

## Ownership LoL da mantenere in Spring

| Parte | Responsabilità mantenuta | Parte estratta verso R4J/comune |
|---|---|---|
| `SummonerService` | Identità persistita, search, link/tracking, composizione livegame, invalidazione dominio | Account/Summoner/Spectator fetch e cache esterna verso R4J; forme finali nel comune |
| `RankService` | Scrittura rank per queue o snapshot completo e cascata canonica | League API e payload esterni verso R4J; `Rank` comune |
| `MasteryService` | Scrittura masteries e aggiornamento record derivati | Mastery API verso R4J; `Mastery` comune |
| `MatchService` | Ingestione, letture Mongo, liste/history/detail e seed dei partecipanti | Match, match-list e timeline fetch verso R4J |
| `ProfileService` / `ProfileAnalyzer` | Composizione e generazione profile/statistics/activity/matchups | Solo risultati finali nel comune |
| `ChampionService` / `ChampionAnalyzer` / `ChampionBuildEngine` | Statistiche, build, refresh e scansioni Mongo | `ChampionView`, `ChampionStatistics`, `Build` e componenti di risposta nel comune |
| `ChampionTierAnalyzer` / `MatchupRankingAnalyzer` | Calcolo delle proiezioni e tier | Solo forme finali di risposta nel comune |
| `CompetitiveService` / `LeaderboardService` | Proiezione competitive, indici Redis e composizione pagine | Contratti di pagina/distribuzione nel comune |
| `ProfileRecordService` / `ProfileRecordAnalyzer` | Record e indici contestuali | Contratti record nel comune |
| `Tracker` / `TrackerScheduler` | Policy, scansioni Mongo, completamento match e scheduling periodico | Richieste Riot passano al client R4J; report tramite job comune |
| `CacheInvalidationService` | Invalidazione dei dati del dominio | Invalidazione della cache esterna mediante contratto R4J, non scritture Redis incrociate |
| `StaticDataService` | Consumo dei dati necessari a composizione/analisi | Acquisizione/cache Data Dragon in R4J, con contratti da completare |

Preservare la cascata ADR-0015: `RankService` persiste `summoner.ranks`,
`CompetitiveService` scrive `competitive`, poi `LeaderboardService` aggiorna
gli indici Redis. Un fallimento interrompe le fasi successive. Un refresh di
una sola queue non deve diventare uno snapshot completo. OTP assente non elimina
un giocatore normalmente ranked dalla proiezione.

`MatchService` mantiene la creazione `tracked=false`; `Tracker` possiede il
normale completamento `tracked=true`. La fetch di R4J non certifica l'avvenuta
persistenza e non prende possesso di queste transizioni.

## Cosa non diventa comune

- `ChampionBuildProvider`, `ChampionStatsProvider`, `ChampionBuildData`,
  `ChampionStatsData`, `BuildSignature` e accumulatori/record temporanei dei motori.
- `ChampionStatsDocument`, `ChampionStatsScope`, `ChampionNode`,
  `ChampionLeafStats`, `MatchupStats`, `TrendStats`, `WinLossStats`: modello
  della matrice persistita/calcolata, distinto dalla risposta `ChampionStatistics`.
- `CompetitiveEntry`, `ProfileStatisticsRow`, `ChampionTierSource`: proiezioni
  o intermedi interni, senza endpoint che ne restituisca il contratto direttamente.
- BSON, query/cursor, compression/checksum storage, driver e `AbstractEntity`.
- JDA, guild/user cache, custom emoji, embed, opzioni slash, audio e coda musica.
- SDK Riot, credenziali, rate limiter, nomenclature e policy di refresh.

`LeafStats` e `ProfileLeafStats` sono invece dati necessari al payload delle
statistiche profilo. Il riuso della base `LeafStats` da accumulatori champion
non impone di spostare anche i loro sottotipi nel comune.

## Utility e infrastruttura

Non esiste un criterio corretto “tutto utils è comune”. `ChampionUtils`,
`LeagueShardUtils`, `TierDivisionUtils`, `LaneTypeUtils`, `PatchUtils` e
`BuildUtils` mescolano semantica, accesso statico o presentazione. Estrarre
solo codifiche pure necessarie ai contratti, mantenendo un proprietario unico;
emoji, slash choices e formattazione restano nel bot, calcoli in Spring,
conversioni SDK in R4J. `TierMathUtils`, `OPTUtils`, parser timeline e
memory-release helpers restano sul lato del calcolo.

`RedisClient`/`RedisMemoryParser`, JSON puro, tempo e HTTP sono candidati
tecnici successivi. La presenza in più servizi non basta a condividere tutte
le loro policy. La prima estrazione obbligatoria riguarda DTO e job.

## Parti esterne al runtime Java

Il POM attuale produce un artifact e un assembly con entry point `App`, con
dipendenze bot, Riot, Mongo e Spring mescolate. Risorse comandi/audio appartengono
al bot; configurazione e logging vanno resi locali ai servizi. Le credenziali
non vanno copiate nell'inventario o nei contratti condivisi.

`database/berbit`, `database/spotify`, `database/website` restano nei rispettivi
domini/tooling; la loro ulteriore separazione è fuori dalla prima estrazione LoL.
I PHP/JS in `rsc/backup/old-memories` sono materiale storico; gli script in
`rsc/testing` sono tooling. Non rappresentano nuovi servizi da migrare.
`target*`, cache locali e output compilati sono esclusi dal piano dei sorgenti.

## Discrepanze registrate, senza correzioni implicite

1. ADR-0014, README architetturale e handbook descrivono compute con due worker.
   Il codice registra `PROFILE`, `PROFILE_2`, `CHAMPION`, `MONGO`; il placement
   profile usa le quattro load e, durante la riserva champion, sceglie tra i due
   profile. ADR-0017 conserva esplicitamente questa configurazione a quattro worker.
2. ADR-0014 descrive il progresso dei parent tramite figli diretti; in
   `Registry.progress` un progresso esplicito del job ha precedenza sui contatori
   dei figli. ADR-0017 conserva il progresso esplicito come precedenza e caratterizza il fallback.
3. Gli ADR accettati collocano i modelli in `lol.model` e il registry globale in
   `lol.queue`; il target introduce common e runtime locali. La sola tranche
   job è accettata in ADR-0017; i modelli del dominio restano in lol.model.
4. L'indice API contiene un link `summoner/rank-history.md` inesistente; il file
   presente è `docs/api/lol-api/summoner/rank-history.md`. Records è documentato
   in `docs/api/records.md`, ma non indicizzato nel sommario per scope. Questi
   disallineamenti sono preesistenti e saranno trattati nel gate API del port.

I conflitti pertinenti alle code sono risolti in ADR-0017. Le discrepanze
dell’indice API non coinvolte da Hive restano fuori perimetro; nessuna correzione
di endpoint o migrazione dei modelli viene implicata. Vedere job-queue.md per
la mappa attuale e hive-validation.md per prove e review.
