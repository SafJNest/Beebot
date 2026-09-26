# Beebot: analisi per la separazione dei processi LoL

**Stato:** analisi read-only del codice applicativo; proposta, non ADR approvato
**Data:** 2026-09-26
**Perimetro:** repository Beebot corrente, con particolare attenzione a LoL, Spring MVC, MongoDB, Redis, R4J, tracking, code di lavoro, statistiche e API.

Questo documento descrive l'implementazione osservata e una migrazione incrementale verso processi separati. Non modifica gli ADR accettati: prima di implementare i confini proposti serve un nuovo ADR che risolva i punti di compatibilità indicati qui.

## 1. Sintesi della situazione

Oggi esiste un solo eseguibile. `com.safjnest.App.main()` avvia nello stesso processo Spring MVC/Tomcat, Redis e MongoDB, scheduler e code LoL, servizi Twitch e JDA. Il JAR assemblato usa `com.safjnest.App` come main class e include le dipendenze del monolite. Spring non è ancora Spring Boot: `SpringServer` crea Tomcat embedded e registra un `DispatcherServlet` configurato da `LolApiConfig`.

La separazione desiderata è coerente con gli owner di prodotto, ma non coincide con la struttura attuale dei package. Le classi LoL fanno ancora da ponte tra Riot/R4J, Redis, Mongo e HTTP/Discord. Spostarle semplicemente in moduli diversi lascerebbe dipendenze vietate o introdurrebbe chiamate HTTP molto granulari. La migrazione deve prima introdurre porte e contratti stabili, poi spostare un owner alla volta.

| Aspetto | Stato osservato | Conseguenza per la migrazione |
|---|---|---|
| Processo di avvio | `App` avvia Bot, API e servizi LoL insieme | Separare gli entrypoint solo dopo avere tolto ai consumer i riferimenti statici condivisi |
| HTTP | Spring MVC su Tomcat embedded, avviato da `SpringServer`; API pubbliche sotto `/api` | `lol-api` richiede un entrypoint autonomo; trasformarlo in Boot è una scelta di packaging/operatività, non una condizione per separare la logica |
| Riot | R4J è istanziato staticamente in `LeagueHandler`; numerosi service chiamano R4J | Introdurre `RiotGateway` e DTO neutrali prima di estrarre il processo |
| Persistence | `MongoDB.java` è un facade statico di circa 4.100 righe; servizi e tracker lo chiamano direttamente | Estrarre interfacce e repository per collection/access pattern, mantenendo query batch e semantiche attuali |
| Cache | `RedisClient` è statico, con pool diretto a `localhost:6379`; `RedisKey` unisce chiavi Riot, API/read model e status | Separare namespace e ownership, rendere host/porta/password configurabili, non trasformare Redis in database o coda durevole |
| Scheduler | `QueueHandler`/`Registry` gestiscono job in memoria per processo; `SyncScheduler` usa virtual thread per job senza limite | Le code attuali non diventano automaticamente code distribuite quando si avvia una seconda istanza |
| Tracking | `TrackerScheduler` lancia scansioni periodiche; `Tracker` cerca tutti i tracked e orchestra fetch, conversione e scritture | Spostare tutto l'orchestratore nel worker e introdurre claim/lease Mongo prima di scalare worker multipli |
| Osservabilità | `MongoCommandMonitor` e sampler di status esistono, ma non è configurato Actuator/Prometheus | Integrare metriche per servizio, separando metriche aggregate dai job/status diagnostici |

## 2. Struttura attuale rilevante

### Avvio e dipendenze principali

- `src/main/java/com/safjnest/App.java`: entrypoint unico; avvia `SpringServer`, `QueueHandler`, warmup leaderboard, `TwitchClient`, sampler e `Bot`; chiude gli scheduler e Mongo.
- `src/main/java/com/safjnest/core/Bot.java` e `src/main/java/com/safjnest/commands/**`: JDA, registrazione comandi, listener/eventi, audio e presentation Discord. I comandi LoL invocano direttamente `LeagueHandler`, servizi LoL, `MongoDB` e in alcuni casi Redis.
- `src/main/java/com/safjnest/spring/SpringServer.java`, `spring/config/LolApiConfig.java`, `spring/controller/**`: server HTTP Spring MVC attuale. Le route includono profili, match, champion, leaderboard, records, status e training.
- `src/main/java/com/safjnest/lol/LeagueHandler.java`: inizializza R4J e contiene anche metodi di presentazione/integrazione Discord, dati statici, rune/augment, conversioni e accessi a Mongo. È uno dei principali nodi da decomporre.
- `src/main/java/com/safjnest/lol/service/**`: servizi domain/cache/persistence/Riot. Le classi sono già separate per area, ma non per processo né per dipendenza.
- `src/main/java/com/safjnest/lol/queue/**`: `QueueHandler`, `Router`, `Registry`, `Job`, `RiotScheduler`, `ComputeScheduler` e `SyncScheduler`. Scheduler e registry sono process-local.
- `src/main/java/com/safjnest/lol/tracker/**`: tracker, trigger periodico, elaborazione e ingestion dei match.
- `src/main/java/com/safjnest/nosql/MongoDB.java`: singolo accesso Mongo runtime; `MongoMigration`/`LeagueDB` hanno il percorso storico di migrazione SQL.
- `src/main/java/com/safjnest/redis/RedisClient.java` e `RedisKey.java`: client e catalogo chiavi condivisi dall'app intera.
- `src/main/java/com/safjnest/lol/model/**`: modelli canonicali LoL usati da servizi, persistence, controller ed embed. Molti tipi e enum sono attualmente R4J.

Il POM radice è un singolo progetto Maven, compila per Java 25 e produce `jar-with-dependencies`. Tra le dipendenze nello stesso artefatto sono presenti JDA/audio, R4J, Mongo sync driver, Jedis, Spring MVC/Tomcat e il driver MariaDB usato dal percorso di migrazione. Non c'è ancora una distinzione di dipendenze tra API, worker e Discord.

### API e flussi read attuali

I controller sono in `src/main/java/com/safjnest/spring/controller/`. `LolController` gestisce ricerca, profilo, refresh profilo, match list, rank history, activity, matchups, records, profilo per Riot ID, live game e match detail. `ChampionController`, `LeaderboardController`, `RecordsController`, `ProfileIndexableController`, `AiTrainingController` e `StatusController` espongono gli altri read model.

Il read path non è sempre read-only:

1. `ProfileService.get()` prova la cache overview e poi legge `SummonerService`, rank, mastery e statistiche; le aggregazioni mancanti/stale vengono schedulate da `ComputeScheduler`.
2. La ricerca per nome può arrivare a `SummonerService.getPuuidByRiotIdAsync()` e quindi chiamare Riot quando Mongo non trova l'identità.
3. `SummonerService.getAsync()` può recuperare un summoner via Riot e salvarlo.
4. `RankService.getAsync()` e `MasteryService.getAsync()` possono avviare fetch Riot e persistenza.
5. `MatchService.getAsync()` su cache/Mongo miss accoda lavoro `SyncScheduler`, recupera da Riot, converte e persiste il match.
6. Le API di match, rank e altri read path possono quindi attendere I/O esterno o produrre scritture, oltre a restituire dati.

Questi comportamenti vanno classificati esplicitamente durante lo split: un API read side che apre Mongo/Redis ma non R4J non può mantenere silenziosamente gli attuali fallback Riot. Per i miss si dovrà scegliere tra una lettura stale/`202`, un comando asincrono al worker o una chiamata diretta al Riot Gateway motivata e limitata.

### Flusso Discord

I comandi League usano `LeagueHandler` e `com.safjnest.lol.service.*`; `LeagueMessage` compone modelli condivisi e rende embed JDA. La richiesta di isolare il bot richiede che la parte di rendering rimanga nel processo Discord mentre la ricerca e i dati LoL arrivino da client HTTP verso `lol-api`. Status, tracker, refresh e operazioni owner attualmente leggono anche code e dati interni: necessitano endpoint API appropriati o vanno trattati come funzioni amministrative specifiche, non come accesso al database dal bot.

## 3. Classi multi-responsabilità e flussi di scrittura

### `LeagueHandler`

`LeagueHandler.java` crea un `R4J` statico da settings, imposta il cache provider R4J a `EmptyCacheProvider`, carica rune/augment e fornisce metodi usati da comandi e servizi. Espone oggetti R4J e importa JDA, cache Discord, `MongoDB`, servizi e modelli LoL.

Va spezzata per ownership:

- `RiotClientConfiguration` e implementazione `RiotGateway` nel modulo/processo Riot: client R4J, autenticazione Riot, accesso endpoint e conversione da R4J ai DTO Riot.
- servizio dati statici: stabilire se i dati DDragon sono nel Riot Gateway o serviti come asset statici/versionati; non lasciare il fetch R4J nei consumer.
- mapping R4J→DTO e mapping DTO→modello canonicale nel worker quando il payload è usato per persistenza/compute.
- helper di dominio puri (per esempio mapping di spell o regole LoL) nel modulo domain se non dipendono da JDA, R4J, Mongo o Redis.
- rune/augment: spostare il caricamento e la cache secondo la sorgente effettiva; il rendering e gli oggetti specifici Discord restano in `beebot-discord`.

`LeagueHandler` come facade statico deve sparire dopo una finestra di compatibilità in cui i caller vengono migrati. Non deve diventare un client HTTP onnipresente statico.

### Servizi LoL

| Classe attuale | Responsabilità osservata / accoppiamento | Destinazione e taglio consigliati |
|---|---|---|
| `SummonerService` | Identità/search, Redis/Mongo, Account/Summoner API R4J, refresh, spectator/live-game, scrittura e invalidazione | Dividere in `SummonerQueryService` (API read), `SummonerRefreshUseCase` (worker orchestration), `RiotGateway` (R4J nel Riot); `SummonerRepository` e cache dedicate. Live game può essere richiesta API al gateway o dato aggiornato dal worker, ma nessun R4J nel servizio API |
| `RankService` | Legge Redis/Mongo, chiama League API, converte `LeagueEntry`, scrive rank e aggiorna `competitive`/cache | Query/proiezione rank in API; aggiornamento rank e competitive nel worker; fetch League entries solo tramite Riot Gateway DTO |
| `MasteryService` | Lettura/persistenza canonical `Mastery`, cache raw `ChampionMastery`, fetch R4J e creazione record | Read mastery API; fetch+conversione+write nel worker; R4J mastery DTO privato al Riot |
| `MatchService` | Read/caching detail, matchlist, fetch raw match/timeline R4J, conversione, scrittura match/partecipanti/events, cache invalidation | Query match API; worker per ingestion/mapping e scritture; gateway per match, list e timeline. Endpoint bulk quando più match sono richiesti insieme |
| `ProfileService` | Composizione risposta, cache overview, freshness, richiesta compute, query persistence | API mantiene composition/read model e richiesta asincrona al worker per refresh; worker possiede analizzatori e scritture statistiche. L'aggregazione batch per pagine/lista deve rimanere batch Mongo |
| `ChampionService` | Analisi e refresh champion stats/build/indexable, scansione match e scrittura aggregate | Compute e persistence nel worker; query/controller read in API. `ChampionAnalyzer`/`ChampionTierAnalyzer` possono restare nel worker come logica locale; non vanno convertiti in chiamate HTTP per match |
| `LeaderboardService` | Query page, redis/cache, competitive projection, aggregati e build/warmup | API owner delle query/read-cache; worker owner dei rebuild di `competitive` e snapshot. Mantenere query page/count paginata e batch PUUID→Summoner come in ADR-0009 |
| `CompetitiveService` | Deriva MMR/lane/OTP dai canonical ranks e statistiche e aggiorna la collection competitiva/cache | Worker owner per calcolo e write; API può avere modelli/query di lettura tramite repository senza eseguire rebuild |
| `Tracker` | Cerca tracked summoner, chiama R4J via service, compara match, elabora, calcola RankProgress, scrive match/summoner/rank e invalida cache | Intero caso d'uso nel worker. Separare orchestration da `MatchIngestionService` e da mapping/analyzer solo dove ciò chiarisce test e transazioni; lasciare analisi massiva in-process |
| `StaticDataService` | Redis + DDragon/R4J, restituisce oggetti R4J static data | Spostare la sorgente esterna al Riot Gateway oppure pubblicare asset versionati; introdurre DTO se attraversa HTTP. Non deve restare una dipendenza condivisa da API e Discord |

Non va spezzata una classe in più servizi remoti solo per avere un JAR per classe. `ProfileAnalyzer`, `ChampionAnalyzer`, `ProfileActivity` e i loop di match sono calcolo locale: restano nel worker con accesso batch Mongo.

### Flusso tracking osservato

`TrackerScheduler` avvia tramite `Chronos` una scansione a intervallo fisso di dieci minuti; pianifica anche retrieval rank entries. `Tracker.retrieveSummoners()` crea un root job `SyncScheduler`, carica l'intera lista con `MongoDB.findTrackedSummonerModels()`, raggruppa per shard e crea job figli. `retrieveSummoner()` recupera summoner e match list, attende 350 ms, controlla se il match è già tracked, fetch del precedente se necessario, fetch del match corrente, verifica ranked solo, poi `trackMatch()`:

1. legge se il match è già tracked;
2. `MatchService.insert()` converte il `LOLMatch`, calcola dati rank locali e inserisce match e seed summoner;
3. `loadMatch()` crea build/event data;
4. per ogni partecipante carica il precedente partecipante, aggiorna il rank tramite `RankService.refreshSync()` e calcola `RankProgress`;
5. aggiorna rank medio e salva match con tracked=true;
6. invalida cache e rilascia la cache timeline.

`RiotScheduler` governa le richieste R4J per shard. `SyncScheduler` invece è un dispatcher di orchestration, non il client Riot: ogni job viene eseguito su virtual thread, non possiede coda fisica per shard né limite/conservazione durevole. `ComputeScheduler` gestisce lavoro profile/champion con worker limitati, deduplica e priorità secondo ADR-0014. `QueueHandler` e `Registry` tracciano job/process tree solo in memoria del singolo processo.

Di conseguenza, `SyncScheduler` appartiene concettualmente al worker per i job di business, ma il suo contratto attuale non è pronto per multi-istanza: un carico senza limite può saturare Mongo/Riot; job persi a crash non vengono ripresi da una coda condivisa; deduplica e registry non sono globali. Nel primo worker si può mantenere un dispatcher locale con limiti distinti per tracking, match e compute; la distribuzione tra processi si ottiene col claim Mongo e con endpoint di comando idempotenti, non condividendo l'istanza statica.

## 4. Confini target e mappatura moduli

### Dipendenze proposte

Il target minimo ha i quattro runtime richiesti e due librerie richieste, ma c'è un conflitto di ownership: i modelli canonicali `lol.model` non sono DTO di trasporto e `lol-persistence` non dovrebbe possedere il dominio. Per mantenere entrambi i confini puliti raccomando una piccola terza libreria `lol-domain`. In alternativa si può lasciare il dominio nel modulo applicativo worker e ritardare lo split API condiviso; collocarlo dentro `lol-contracts` o `lol-persistence` confonderebbe i ruoli.

```text
beebot-discord ──HTTP + lol-contracts──> lol-api ──> lol-persistence ──> Mongo
                                             │              │
                                             │              └──────────────> Redis read cache
                                             └─HTTP + contracts─> lol-worker
                                                                      │
                                                                      ├─ lol-domain
                                                                      ├─ lol-persistence ──> Mongo
                                                                      ├─ Redis cache/invalidation
                                                                      └─HTTP + contracts─> lol-riot ──R4J──> Riot

                 lol-domain: canonical models and pure rules
                 lol-contracts: versioned process-boundary DTOs only
```

| Modulo / JAR | Contenuti che sposterei | Dipendenze vietate |
|---|---|---|
| `lol-contracts` | Request/response HTTP interne, PATCH, DTO Riot wire, error/status payload, enum semplici e versionabili | R4J, Mongo/BSON, Spring service, JDA, Redis, regole domain |
| `lol-domain` *(raccomandato)* | `lol.model` canonicali, filtri, regole e mapper puri; estrarre i tipi condivisi da package bot/infra | JDA, Spring, Mongo, Redis, R4J |
| `lol-persistence` | Mongo config/client/provider, `MongoCommandMonitor`, repository per access pattern, document mapper, executor di update entity e index/collection policy | JDA, R4J, HTTP, presentation |
| `lol-riot.jar` | R4J, chiave Riot, `RiotScheduler`/rate-limit e retry, deduplica chiamate in-flight, mapper R4J→wire DTO, cache raw e metriche Riot | Mongo, tracker, profilo, champion matrix, Redis read-model `los:*`, JDA |
| `lol-worker.jar` | Tracker/casi d'uso, scheduling, ingestion, mapping wire→domain, rank/mastery update, compute/profile/champion/competitive/leaderboard/records rebuild, sole scritture Mongo | JDA, R4J, accesso HTTP pubblico dell'API come proprietario dei dati, `LeagueHandler` |
| `lol-api.jar` | Spring Boot, controller pubblici e privati, validazione input, auth, response composition/read, cache read model, client HTTP verso worker, repository in sola lettura per i flussi normali | JDA, R4J, tracker/scheduler interno Riot, scritture compute |
| `beebot.jar` (`beebot-discord`) | JDA, comandi/eventi, audio, embed/rendering, client HTTP `lol-api`, configurazione URL e credenziale bot→API | Mongo, Redis LoL, R4J, repository, servizi LoL interni |

`LeagueDB` e `MongoMigration` restano in un tool/modulo di migrazione separato e non vanno trascinati in `lol-api` o `beebot`. Per ora non è necessario consegnare un settimo processo migration: può restare un profilo/tool non avviato nei runtime.

### Dipendenze Maven finali

Creare un parent Maven `packaging=pom`, conservando inizialmente lo stesso group/versione e Java 25. Produrre artefatti distinti, non un assembly con tutte le dipendenze:

```text
parent
├── lol-contracts
├── lol-domain
├── lol-persistence
├── lol-riot
├── lol-worker
├── lol-api
└── beebot-discord
```

- `lol-api` → contracts, domain, persistence, Spring Boot Web/Actuator/Micrometer, Redis client e HTTP client verso worker.
- `lol-worker` → contracts, domain, persistence, Spring Boot Web/Actuator/Micrometer, Redis client e HTTP client verso Riot.
- `lol-riot` → contracts, Spring Boot Web/Actuator/Micrometer, R4J e Redis raw cache solo se scelta la cache condivisa.
- `beebot-discord` → contracts, HTTP client leggero e dipendenze JDA/audio; niente persistence/driver/redis/R4J.
- `lol-persistence` → domain, Mongo sync driver e mapping JSON/BSON necessari.
- `lol-domain` → dipendenze di serializzazione solo se indispensabili; preferire niente Spring/Jackson service annotations.
- `lol-contracts` → Java DTO e Jackson annotations solo se necessarie. Nessuna dipendenza applicativa.

Non introdurre Spring Data come conseguenza automatica di Boot: i repository possono continuare a usare Mongo Java driver finché misure/necessità non giustificano un cambio.

## 5. Persistence e Redis

### Come decomporre `MongoDB.java`

`MongoDB.java` è una facade statica di circa 4.100 righe che oggi possiede bootstrap DB/collection, query, batch cursor, conversioni BSON↔domain, update atomici, rebuild e statistiche infrastrutturali. Suddividere per access pattern e responsabilità, non facendo un repository per ogni model embedded.

| Repository / supporto | API Mongo attuali da assorbire (esempi) |
|---|---|
| `MongoProvider` / `MongoConfiguration` | `initialize`, `database`, database name, client lifecycle e collection initialization |
| `SummonerRepository` | find/search/by PUUID/Riot ID, upsert/seed/batch, user link, patch fields, last-seen, rank/mastery embedded, claim/release tracking |
| `MatchRepository` | match detail, result/page/count, insert/upsert, match existence/tracked flag, match-list query, participant update, rank-history/previous participant |
| `MatchEventRepository` | eventi separati/compressi, read/write e batch timeline; niente payload evento nei consumer che non lo richiedono |
| `ProfileStatisticsRepository` | statistiche canonicali, query aggregate, refresh filters, upsert/delete e cursori match batch |
| `ProfileActivityRepository` / `ProfileMatchupRepository` | proiezioni per filterKey, query/upsert e prune canonicale |
| `ProfileRecordRepository` | profile/global record, ladder/segment/count, rebuild e record mastery |
| `ChampionRepository` | champion stats/builds/indexables, raw projections e batch sorgente per analyzer |
| `CompetitiveRepository` | competitive row query/upsert/delete/bulk rebuild e segmenti |
| `LeaderboardRepository` | query pagina/contatori/distribuzione/top-region e `leaderboard_aggregates`; pagina e totale restano percorsi distinti come da ADR |
| `TrainingReadRepository` | cursor/sample AI training e query proiettate solo per il trainer |
| `MongoEntityUpdateExecutor` + mapper | `applyEntityUpdate`, operatori `AbstractEntity`, conversioni `QueryRecord`/`Document` e mapping document/domain |
| `MongoMetrics`/monitor | contatori e osservazioni comandi; passare da `MongoCommandMonitor` a Micrometer senza etichette ad alta cardinalità |

Le query massive (`forEach*Batch`, cursori e proiezioni raw per analyzer) rimangono in `lol-persistence` ma vengono chiamate localmente dal worker. Non esporle come migliaia di endpoint. `AbstractEntity` e `NoSqlEntityExecutor` vanno controllati insieme: l'astrazione che registra update path oggi chiama un executor statico e influenza tutte le entità.

La collection `summoner` resta canonicale per ranks/masteries embedded; `competitive`, `leaderboard_aggregates`, `profile_statistics`, `profile_activity`, `profile_matchups`, `profile_records`, `champion*` restano proiezioni con gli ownership indicati negli ADR. Indici secondari risultano operator-managed: mantenere tale contratto e non reintrodurre bootstrap automatico. La policy di collection creation e zstd per `match_events` va verificata contro lo stato Mongo già deployato.

### Redis: proprietà delle chiavi

`RedisKey.java` mescola almeno tre famiglie; `RedisClient` usa un pool statico e host `localhost:6379`, con circuit-breaker di 30 secondi e fallback miss quando Redis non è disponibile.

| Famiglia esistente | Esempi/TTL attuali | Owner futuro |
|---|---|---|
| Raw Riot (`r4j:*`) | summoner/account/entries/masteries/match/list 1h; spectator 60s; timeline 2m; refresh cooldown 2m | `lol-riot`, se il gateway conserva cache raw. Nessun DTO R4J serializzato verso altri processi |
| DDragon (`r4j:static:*`) | item/champion/spell/rune/version 1 giorno | `lol-riot` se proxy dati esterni, altrimenti servizio/asset statici con namespace proprio |
| Read model (`los:*`) | profilo/rank 4h, mastery e statistiche 12h, match detail 6h, leaderboard snapshot 6h–1g, champion stats 12h | Letture `lol-api`; worker invalida le chiavi dipendenti quando scrive. Shared Redis non significa owner condiviso |
| Status (`status:*`) | contatori 10 giorni | Eliminare o assegnare a un owner di status esplicito; preferire gauge/counter Micrometer quando sono metriche operative |
| Queue/dedup/lease | oggi Registry e dedup job sono locali; `claim` Redis esiste per cooldown puntuale | Nessuna coda durevole nuova ora. Lease tracking in Mongo; dedup Redis non deve fingere garanzia multi-worker |

Separare cataloghi/config e prefissi (`lol:riot:*`, `lol:api:*` o equivalente), non per forza istanze Redis nel primo deploy. Configurare host, porta, TLS/password e pool per ambiente. Un cache miss Redis deve rimanere osservabile; oggi molte eccezioni diventano miss e rischiano di spostare carico su Mongo.

## 6. Modelli di dominio e wire DTO

I modelli canonicali (`Summoner`, `Rank`, `Mastery`, `Match`, `Participant`, `MatchResult`, statistiche e response read) devono rimanere modelli di dominio/read model e non essere duplicati con DTO Mongo o DTO pubblici paralleli. Gli oggetti che varcano una relazione HTTP sono invece tipi in `lol-contracts` con schema compatibile/versionabile.

Punto di compatibilità concreto: il codice corrente importa enum e POJO R4J anche fuori dal gateway. `MongoDB.java`, `lol.model` e servizi usano `LeagueShard`, `GameQueueType`, `LaneType`, `TierType`/`TierDivisionType`; `MatchService` e `Tracker` trattano `LOLMatch`, `LOLTimeline`, `LeagueEntry`, `ChampionMastery`, `RiotAccount`, R4J Summoner e spectator. Il confine “R4J non attraversa HTTP” è raggiungibile, ma il confine più forte “solo `lol-riot.jar` dipende da R4J” richiede di togliere R4J anche da domain e persistence. Soluzione proposta:

1. `lol-contracts` usa codici wire string stabili per region/queue/tier e DTO propri per account, summoner, rank entry, mastery, match, timeline ed errori.
2. `lol-riot` converte i POJO R4J in questi DTO; non li serializza direttamente.
3. `lol-worker` converte DTO Riot nei modelli canonicali e invoca mapper domain.
4. `lol-domain` migra gradualmente gli enum canonicali via enum owned dal progetto/utilità di mapping. Non iniziare rinominando payload pubblici: i JSON contract esistenti sono governati da ADR-0001/0005/0006 e API docs.
5. `lol-persistence` converte i codici documentali in enum domain senza importare classi R4J.

`lol-contracts` contiene solo contratti di processo e tipi semplici. Non deve diventare un “common” jar di modelli domain, mapper, framework o utility.

## 7. Operazioni HTTP interne

API pubblica e API interna devono essere separate per auth, logging, rate limiting e exposure. Prefix suggeriti: `/internal/worker/v1/**` e `/internal/riot/v1/**`; documentare OpenAPI interno separato da quello pubblico.

### API → worker

- `PATCH /internal/worker/v1/summoners/{region}/{puuid}`: comando idempotente di patch/aggiornamento; riceve solo campi business ammessi e non il tracking metadata interno.
- `POST /internal/worker/v1/summoners/{region}/{puuid}/refresh`: refresh profile/rank/mastery, con operation id o idempotency key.
- `POST /internal/worker/v1/compute/profile` e `/champion` (o endpoint command unificato): avvia/rebuild compute e restituisce accepted/job handle secondo il contratto `202` esistente.
- `GET /internal/worker/v1/jobs/{id}` e `GET /internal/worker/v1/status`: se API/bot devono esporre lo status dei job. Restituire una projection bounded, non l'intera struttura in memoria.
- Endpoint per operazioni amministrative (rank-entry rebuild, competitive/leaderboard rebuild) solo per operator/bot autorizzato; non esporre indiscriminatamente al client pubblico.

Le route pubbliche `GET` rimangono in API e leggono Mongo/Redis. Le GET che oggi fanno Riot fallback devono passare a `202`/dato stale e submit al worker, oppure usare una chiamata gateway solo per una necessità user-facing misurata. Non duplicare i job del worker con un fetch diretto dal controller.

### Worker → Riot Gateway

Endpoint distinti per account by Riot ID/PUUID, summoner, league entries, mastery, live game, match IDs, match e timeline. Aggiungere operazioni batch quando il worker deve recuperare più risorse; per match ingestion esporre `matchlist` e `matches batch` dove Riot API/R4J supporta effettivamente il pattern. Ogni risposta è wire DTO, mai POJO R4J.

Il gateway deve imporre i rate limit Riot per chiave/app e route/region in modo centralizzato. Un secondo worker non deve aggirare la quota avviando un secondo client/queue indipendente. Retry e backoff rispettano `Retry-After`/429 e non rimettono in coda loop illimitati. Le operazioni che restituiscono timeline/build grezzi devono essere paginabili o compresse/batch dove la dimensione lo richiede.

## 8. PATCH e semantica dei campi

Un `PATCH` deve distinguere: campo assente (non modificare), campo presente con valore, campo presente con `null` esplicito. Non usare `Optional<T>` per questo progetto. Un DTO Java può mantenere per ogni campo un flag `...Present` e il valore, con setter Jackson chiamato solo quando la property è presente:

```java
public final class SummonerPatchRequest {
    private boolean userIdPresent;
    private String userId;
    private boolean trackingPresent;
    private Boolean tracking;

    public void setUserId(String value) {
        userIdPresent = true;
        userId = value;
    }

    public void setTracking(Boolean value) {
        trackingPresent = true;
        tracking = value;
    }
}
```

La validazione applicativa decide il significato di null. Per esempio `userId: null` può voler dire unlink; `tracking: null` è meglio rifiutarlo come input non valido. Mancata chiamata al setter conserva `Present=false`. Evitare `Boolean` senza flag perché non distingue assente e null.

API valida region, PUUID, permessi e campi patchabili, poi inoltra al worker il comando DTO. Il worker applica la modifica atomica e gli effetti collaterali. `tracking=true` aggiorna il controllo business e metadata interni (`enabled`, `nextCheckAt`, `lastCheckAt`) nello stesso caso d'uso; non accetta metadata schedulazione dal client. Il response pubblico descrive lo stato accettato, non espone lease owner/token.

## 9. Sicurezza tra processi e configurazione

Usare un segreto distinto per ogni relazione effettiva:

| Chiamante → servizio | Credenziale |
|---|---|
| Beebot → API | `BOT_API_KEY` |
| API → worker | `API_WORKER_API_KEY` |
| worker → Riot | `WORKER_RIOT_API_KEY` |
| API → Riot, solo se introdotto dopo averlo giustificato | `API_RIOT_API_KEY` distinta; non è necessaria nel flusso preferito |

Servizio destinatario verifica `Authorization: Bearer ...` con un `OncePerRequestFilter` riutilizzabile che protegge esclusivamente `/internal/**`; health/metrics hanno policy di rete distinta. Client HTTP usa un `ClientHttpRequestInterceptor` o interceptor equivalente per aggiungere il token. Comparare byte con `MessageDigest.isEqual`, non confrontare con stringa e non fare log di header/body sensibili. Segreto mancante o vuoto in ambiente non locale deve impedire l'avvio del servizio, invece di lasciare un endpoint interno senza autenticazione.

Contare richieste rifiutate con counter e label finite (`service`, `operation`, `reason`); mai usare token, PUUID, Riot ID, gameId, userId, guildId, requestId o jobId come label. Se ci sono reverse proxy, assicurare che header Authorization non venga loggato. Il token condiviso per relazione è un primo confine applicativo: su host distinti, mantenere rete privata/VPN, firewall per peer e rendere configurabile TLS; aggiungere mTLS come autenticazione del peer a transport layer senza riscrivere DTO/controller/client.

Configurazione per servizio da environment/secrets: `SERVER_ADDRESS`, `SERVER_PORT`, `MANAGEMENT_ADDRESS`, `MANAGEMENT_PORT`, `MONGO_URI`, `MONGO_DATABASE`, `REDIS_HOST`, `REDIS_PORT`, credenziali Redis/TLS, `WORKER_BASE_URL`, `RIOT_BASE_URL`, `API_BASE_URL`, chiave Riot solo nel gateway e chiavi interne solo sui due lati della relazione. Il codice oggi legge settings JSON, `spring.properties` e Redis localhost; non spostare segreti in YAML versionato. Su una VPS usare bind loopback per porte interne e management. In container o su macchine differenti, `127.0.0.1` punta al container stesso: usare rete Docker privata o indirizzi WireGuard/firewall privati e non esporre i bind interni su Internet.

## 10. Tracking concorrente e scaling worker

La query attuale carica tutti i tracked summoner e non usa una scadenza/claim distribuita. Prima di avviare più istanze, introdurre un claim atomico in `TrackingRepository` basato su `tracking.enabled=true`, `tracking.nextCheckAt <= now` e `(leaseUntil assente o scaduto)`, con ordinamento e limite del batch. Il claim scrive `leaseOwner`, `leaseToken` (fencing token univoco per claim), `leaseUntil` e aggiorna atomicamente `nextCheckAt` o stato claim.

Ogni renew/complete/fail fa update condizionato su PUUID, owner e token correnti. Un worker che torna dopo la scadenza non può committare il risultato: il token evita scritture tardive anche se il nome istanza è stato riutilizzato. In caso di crash, lease scade e un altro worker riprende il summoner; i retry aggiornano backoff/nextCheckAt con limite e jitter. Claimare lotti moderati, rilasciare o completare ogni item separatamente, e non mantenere lease mentre si attende un batch Riot lungo senza rinnovo.

La scrittura match rimane idempotente sul full match ID. L'aggiornamento RankProgress però dipende dall'ordine temporale/precedente match e va protetto dal fencing e da update atomici; testare due worker sullo stesso PUUID e crash/timeout durante Riot. Deduplica in memoria resta ottimizzazione entro una istanza, non garanzia distribuita.

Per ora non aggiungere Kafka/Redis Streams. Se in seguito il volume o la necessità di replay richiedono una coda, introdurla tra API→worker per comandi o tra worker scheduler→worker executors con outbox/idempotency e consumer groups. Non metterla tra worker e Mongo e non spostare le scansioni Mongo/accumulatori su chiamate HTTP.

## 11. Prometheus e metriche

Integrare Actuator, Micrometer e registry Prometheus in ciascun runtime Spring (`lol-api`, `lol-worker`, `lol-riot`), esponendo health e scrape endpoint su management listener/rete privata. Separare `server.port` pubblico da `management.port/address`; non pubblicare `/actuator/prometheus` su Internet. Beebot non è Spring per default: si può esporre JMX/metriche dedicate solo se serve, senza convertirlo a Spring per uniformità.

Usare i timer/counter HTTP automatici di Spring/Micrometer per conteggio, latenza e status. Aggiungere metriche di dominio con unità esplicite e label a cardinalità finita:

- API: cache hit/miss, chiamate HTTP interne e durata per `service`/`operation`/`status`.
- Riot: richieste, errori e 429 per `platform`/`endpoint`/`operation`; retry, attesa queue, profondità e rate remaining per quota/route finite.
- Worker: cicli tracking, summoner dovuti/claimed/completati/falliti, durata, lease scaduti, match scoperti/ingestiti, durata delle famiglie compute e profondità queue locali.
- Tutti i runtime Spring: heap/nonheap, GC, thread, CPU, HTTP count/latency/error.

`api_requests_total` e `api_request_duration` possono essere i nomi concettuali richiesti; in Micrometer scegliere nomi base coerenti (per esempio `lol_api_requests` counter e `lol_api_request_duration` timer) e verificare la traduzione Prometheus, evitando di aggiungere manualmente `_total` se registry lo aggiunge. Mai usare PUUID, Riot ID, gameId, userId, guildId, requestId o jobId come label. Non inviare log a Prometheus: JSON strutturato nei log è un canale separato e l'eventuale Loki può arrivare dopo.

`MongoCommandMonitor` e status sampler attuali vanno integrati con Micrometer senza cancellare le viste operative utili. Lo snapshot dei job può restare un endpoint diagnostico bounded; non sostituisce metriche aggregate e non deve etichettare ogni job.

## 12. Dipendenze per processo: tabella di verifica

| Dipendenza | Discord | API | Worker | Riot |
|---|---:|---:|---:|---:|
| JDA/audio | sì | no | no | no |
| Mongo driver/repository | no | sì, read | sì, read/write | no |
| Redis LoL/read model | no | sì, read/cache | sì, invalidazione/cache compute dove serve | solo raw Riot cache se misurata |
| R4J/Riot key | no | no nel percorso standard | no | sì |
| Compute/Tracker/Scheduler | no | no | sì | solo Riot scheduler/rate limiter |
| Spring MVC/Boot | no | sì | sì | sì |
| `lol-contracts` | client API | sì | sì | sì |
| `lol-domain` | al massimo mapping per presentazione strettamente necessario, preferire wire DTO | sì | sì | no |

Discord non deve dipendere da `lol-domain` se i suoi comandi possono consumare i response DTO contrattuali. In ogni caso il requisito essenziale è che non importi servizi/persistence/R4J.

## 13. Costi e punti in cui il target può peggiorare il sistema

1. **Troppe chiamate interne:** fare una HTTP request per ogni summoner/match/participant trasformerebbe scansioni efficienti in migliaia di round-trip. Il worker deve conservare batch query/cursor e chiamare il gateway con batch quando possibile.
2. **Latenza di miss:** API→worker→Riot aggiunge rete e serializzazione rispetto a R4J nello stesso processo. Per endpoint interattivi restituire stale/`202` quando compatibile e misurare il percorso sincrono; non aspettare compute pesante nel controller.
3. **Payload grandi:** match/timeline/events possono essere grandi. DTO JSON duplicano parsing e memoria; limitare campi, comprimere il trasporto privato se misurato, evitare timeline nei flussi che non la richiedono.
4. **Limite Riot centralizzato:** un Riot service piccolo può diventare collo di bottiglia. È anche l'owner corretto di quota/rate-limit; scalarlo deve condividere coordinamento quota/idempotenza o rischia di superare i limiti Riot.
5. **Mongo conteso:** API e worker condividono Mongo come richiesto. Credenziali DB read-only dell'API aiutano a rendere reale il write owner; query/report pesanti API possono comunque competere con compute worker e vanno misurati.
6. **Redis shared-state accidentale:** prefissi condivisi o invalidazione incompleta causano dati stale. Documentare per chiave TTL, payload, owner, writer e invalidazione prima di spostare un processo.
7. **Job status non globale:** il Registry corrente è in-memory. Il bot non può interrogare una diversa istanza worker per vedere il job senza status/operation store esplicito; inizialmente API può restituire accepted senza polling oppure worker può salvare solo gli operation result necessari.
8. **Più deploy e failure mode:** health, secrets, restart, compatibilità contratti, rete e rollback diventano operativi. Separare i JAR senza pipeline/config per artefatto non realizza “deployare solo il servizio col fix”.
9. **Contratti e modelli duplicati:** serializzazione dei modelli canonicali direttamente su HTTP accoppia versione API e dominio. DTO specifici riducono il coupling ma aumentano mapper e manutenzione: mantenerli soltanto ai confini di processo.
10. **Enum R4J nel dominio/persistence:** la richiesta di isolare R4J richiede una migrazione ampia; mantenerli in `lol-contracts` trasferirebbe il lock-in ai client e contraddirebbe il suo perimetro.

Il target è peggiore dell'attuale se il carico e l'organizzazione continuano a richiedere deploy atomico, o se si estraggono analyzer e query massive via HTTP. I vantaggi concreti sono isolamento dipendenze/secret, scaling e deployment indipendenti; vanno validati contro misure di latenza e carico per processo.

## 14. Piano incrementale proposto

La sequenza sposta prima i confini che riducono coupling e rischio di dato. Ogni fase deve mantenere un artefatto deployabile e un rollback documentato; non avviare due tracker in parallelo.

| Fase | Modifiche e file/aree | Gate di verifica (da eseguire in fase d'implementazione) | Rischio / deployabilità |
|---|---|---|---|
| 0 — Baseline e ADR | Nuovo ADR che approva process ownership, domini/enum, route interne, compatibilità API e decisioni Redis/auth. Inventario dipendenze e baseline throughput/latency/API. | Compilazione e smoke endpoint attuali; contare dipendenze vietate e verificare payload attuali. | Nessun codice applicativo spostato; deploy corrente invariato. Gate necessario perché ADR-0011/0009/0014 documentano assunzioni attuali diverse. |
| 1 — Parent Maven e contracts/domain | Convertire root in parent; introdurre `lol-contracts` e `lol-domain`; spostare DTO e canonical model senza cambiare JSON, persistence o entrypoint. `pom.xml`, package `lol.model`, API docs e serializzazione. | Contract JSON golden tests; build del JAR legacy e confronto response/API serialization. | Alto rischio di build e JSON drift; produrre ancora un runtime monolitico aggregato di moduli. |
| 2 — `lol-persistence` in-process | Spostare client/config, executor, mapper e metodi Mongo in repository; adattare services mantenendo firma/facade compatibilità temporanea `MongoDB`. Nessun nuovo processo. | Test Mongo per tutte le query/write trasferite; confronto explain/index/paginazione e batch; migrazione/conversioni BSON. | Alto rischio query semantics/performance. Sempre un solo JAR e nessun cambio API. |
| 3 — Porte e DTO Riot in-process | Definire `RiotGateway`; sostituire dipendenze R4J dirette in servizi con interfaccia. Implementazione locale R4J e mapper DTO/domain; classificare account, rank, mastery, live game, match/list/timeline, DDragon. | Test contract e mapping fixture R4J; fake gateway test service; garantire stesso rate-limit/dedup/Riot error behavior. | Può restare deploy monolitico. Mantiene un solo client R4J, nessuna doppia quota. |
| 4 — `lol-riot.jar` | Avviare gateway Spring privato; spostare R4J/config/key, rate-limit, scheduler e raw cache. Implementare `HttpRiotGateway`; migrare prima il worker in-process/compat facade. | Contract integration con stub e Riot sandbox/live smoke controllato; test 429, timeout, retry, batch e dimensioni payload. | Due processi deployabili; mantenere fallback locale via feature flag solo durante rollout e non eseguire due schedulers sullo stesso keyspace. |
| 5 — `lol-worker.jar` | Estrarre Tracker/Scheduler, Sync/Compute e use case write/compute; collegare API a endpoint worker. Spostare `TrackerScheduler`, `Tracker`, `MatchService` write path, Rank/Mastery refresh, Profile/Champion/Competitive/Leaderboard/Record compute. | Test end-to-end command→worker→gateway→Mongo; idempotenza match/rank; backpressure; shutdown; nessuna scrittura API nel percorso patch/compute. | Rollout con tracker disabilitato in vecchio processo prima di abilitarlo nel worker; un solo writer schedulato. Legacy bot/API possono continuare a leggere. |
| 6 — Read-side API e PATCH | Separare API entrypoint; lasciare controllers/services read-only con repository Redis/Mongo; implementare PATCH request presence semantics + forwarding worker e submit/202 per refresh. Split config management/API/worker. | Test public API contract regression, missing/stale paths, auth, PATCH absent/null/value, timeout/failure del worker. Load test read vs write/compute. | Mantenere vecchie route e response; deploy API nuova dietro reverse proxy/traffic shift reversibile. |
| 7 — Isolare Beebot | Sostituire chiamate `LeagueHandler`/service nei comandi LoL con API client; tenere render in `LeagueMessage` e embed JDA; trasferire status/admin routes necessarie. Eliminare driver e package LoL infra dal bot. | Test command mapping/response, Discord interaction timeout, embed snapshot e error handling API indisponibile; dependency tree no R4J/Mongo/Redis. | Ultimo consumer migrato; deploy bot indipendente quando API è stabile. Conservare presentazione/comandi invariati. |
| 8 — Telemetria e scaling | Actuator/Micrometer/Prometheus su ogni processo, management network, dashboard; Mongo tracking due-query/lease/token, retry/backoff, worker instance configuration. | Scrape/health test, cardinality audit, due worker concorrenti, scadenza lease/fencing/crash, carico e alert. | Scale worker graduale; nessun cambio a Streams/Kafka. Rollback riducendo repliche e spegnendo nuovo scheduler. |

Prometheus può iniziare nella fase 4 o 5 per misurare i processi estratti, ma va completato per ogni Spring runtime nella fase 8. Non è necessario aspettare la fine per aggiungere metriche minime di health, HTTP e queue.

## 15. Compatibilità da mantenere e classi da rimuovere

**Quasi invariate all'inizio:** analyzer puri e modelli statistici; canonical Mongo identities; full match ID; Redis read cache schema e TTL finché sono condivisi; response pubbliche, `202`/`PARTIAL` e embed; `LeagueDB` limitato alla migrazione.

**Da spezzare:** `LeagueHandler`, `MongoDB`, `SummonerService`, `RankService`, `MasteryService`, `MatchService`, `ProfileService`, `ChampionService`, `LeaderboardService`, `CompetitiveService`, `Tracker` solo secondo la tabella owner (non necessariamente una classe nuova per ogni metodo).

**Da eliminare dopo compatibilità e dep-tree gate:** `LeagueHandler` static facade, `MongoDB` static facade, `RedisKey` catalogo globale non segmentato, singleton accessori `RedisClient` configurato hardcoded, e le dipendenze HTTP verso domain/service dall'app Discord. `App` resta entrypoint specifico di Discord, ma perde API, Redis/Mongo, queue e inizializzazione LoL.

`RiotScheduler` non scompare come concetto: la sua implementazione si trasferisce nel gateway e cambia i route type da enum R4J condivisi a route wire/interne. `SyncScheduler`/`ComputeScheduler` diventano implementazioni worker. Il job registry API non deve importare o controllare oggetti `Job` worker.

## 16. Riferimenti al codice e decisioni già registrate

- `src/main/java/com/safjnest/App.java`, `spring/SpringServer.java`, `spring/config/LolApiConfig.java`: processo unico, avvio manuale Tomcat/Spring MVC, CORS pubblico attuale.
- `src/main/java/com/safjnest/lol/LeagueHandler.java`: R4J statico e coupling con Discord, Mongo e dati LoL.
- `src/main/java/com/safjnest/lol/service/{SummonerService,RankService,MasteryService,MatchService,ProfileService,ChampionService,LeaderboardService,CompetitiveService}.java`: accessi incrociati oggi descritti.
- `src/main/java/com/safjnest/lol/tracker/{Tracker,TrackerScheduler}.java` e `lol/queue/{QueueHandler,Registry}.java`, `lol/queue/scheduler/{RiotScheduler,SyncScheduler,ComputeScheduler}.java`: tracking e code process-local.
- `src/main/java/com/safjnest/nosql/MongoDB.java`, `nosql/{AbstractEntity,NoSqlEntityExecutor,MongoCommandMonitor}.java`: persistence/mapping/monitor.
- `src/main/java/com/safjnest/redis/{RedisClient,RedisKey}.java`: pool hardcoded, fallback e catalogo TTL/namespace.
- `src/main/java/com/safjnest/spring/controller/**`: API pubbliche correnti e superficie da mantenere compatibile.
- `docs/architecture/adr/0001-canonical-lol-model-boundaries.md`, `0005-lol-api-json-contract.md`, `0007-unified-api-result-and-parameters.md`, `0008-endpoint-cache-and-async-lookups.md`, `0009-mongo-persistence-and-migration.md`, `0010-database-refresh-queue.md`, `0011-domain-services-and-r4j-queue.md`, `0012-profile-and-champion-analysis-facades.md`, `0014-global-job-scheduler.md`, `0015-contextual-ranking.md`: invarianti esistenti da rispettare o aggiornare esplicitamente.
- `docs/HANDBOOK.md`, `docs/api/**`, `docs/mongo/**`: istruzioni operative, payload e query/index contract da sincronizzare nelle fasi di implementazione.

### Decisioni da ratificare prima di scrivere il codice

1. Aggiungere `lol-domain` come libreria separata, necessario per non mettere modelli canonicali in `lol-contracts` o `lol-persistence`.
2. Standardizzare enum canonicali indipendenti da R4J senza cambiare JSON pubblico accidentalmente.
3. Definire se i fallback Riot delle GET diventano `202`/stale + worker oppure restano poche chiamate HTTP API→Riot giustificate.
4. Usare repository Mongo read-only nell'API tramite credenziali DB distinte e worker come writer applicativo.
5. Determinare DTO/batch supportati dal Riot Gateway, in particolare match/timeline, prima di scegliere endpoint sincroni.
6. Definire retention e visibilità dello status job dopo che API e worker hanno registry separati.
7. Aggiungere tracking schedule/lease/token e recovery con una migrazione schema compatibile.
