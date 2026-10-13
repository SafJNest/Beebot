# Hive: job e code generici

Stato: infrastruttura implementata nel monolite, decisione mirata
[ADR-0017](../adr/0017-hive-job-runtime.md). La separazione dei processi,
i contratti DTO e le API inter-servizio di ADR-0016 restano proposte.

## Mappa di estrazione e responsabilità

| Componente precedente | Responsabilità | Destinazione | Chiamanti / modifica | Verifica |
|---|---|---|---|---|
| `lol.queue.job.Job` e stati | Dati e progresso | `com.hive.job` | Servizi, tracker, scheduler e comandi: import migrati | Outcome item, priorità, progresso esplicito |
| `lol.queue.Registry` | Lifecycle/context/dedup/follower | `com.hive.Registry` | Runtime e scheduler: istanza esplicita, senza globale | Isolamento, figli/callback, cleanup, errore/cancellazione |
| `lol.queue.Router` | Registrazione/dispatch/promozione | `com.hive.Router` | Hive e test: scheduler/route tipizzati, token risultato | Duplicati, route invalide, follower/promozione |
| `spring.lol.queue.QueueHandler` | Composizione applicativa Spring e submission | `com.safjnest.spring.lol.queue.QueueHandler`; meccanismo in `com.hive.Hive` | App, servizi/tracker/comandi: bootstrap esplicito e un solo runtime | Nessun avvio implicito, admission/shutdown |
| `AbstractScheduler` | Route validate, placement hook, queued/direct lifecycle | `com.hive.scheduler` | Concrete locali: registry/config espliciti | Mode diretto senza queue fisica, enqueue failure |
| `JobQueue`/`JobWorker` | Lane e drain del body | `com.hive.worker` | Scheduler generico | FIFO, promozione, no preemption, worker release |
| Snapshot job/queue/worker/scheduler/run | Dati generici | `com.hive.status` | BotStatus, StatusService, comandi: import migrati | Compatibilità JSON e limiti |
| `Registry.runType` | Classificazione tracking/sample/rank | hook locale `spring.lol.scheduler.SyncScheduler` | Snapshot scheduler e StatusService | Nessuna key LoL interpretata dal core |
| `RiotScheduler` | Serializzazione per shard e log | `com.safjnest.s4b.scheduler` | Fetch servizi/comandi: import migrati | Route/naming e protezioni conservati |
| `ComputeScheduler`/`DatabaseWorkerType`/`ChampionMatrixRequest` | Policy dominio/Mongo | `spring.lol.scheduler` | Facade/champion/profile/tracker: import migrati | Quattro worker, riserva, placement, merge |
| `SyncScheduler` | Policy route shard/global | `spring.lol.scheduler` | Tracker/rank/match/orchestratori | Virtual thread diretto e albero comune |
| Continuation dei servizi | Persistenza e contesto | servizi locali + executor Hive posseduto | Rank/Mastery/Summoner async | Context retain/resume e shutdown |

Il core dipende solo dal JDK. Nessun `lol.*`, R4J, Mongo/BSON, Redis/Jedis,
Spring, JDA, App o BotLogger entra in Hive. Il risultato riutilizzabile non
richiede un processo Beebot: i sorgenti core compilano indipendentemente e
un POM autonomo `hive/pom.xml` produce il solo artifact Hive dagli stessi
sorgenti canonici, senza copiare il core o trasformare i tre servizi in moduli.
L'estrazione dei moduli dei tre servizi resta una fase successiva.

## Composizione e configurazione

Ogni `Hive` possiede Registry, Router, scheduler registrati ed executor.
`HiveConfig` definisce identità, Clock, limiti snapshot, timeout di shutdown
ed osservazione; costruttori e hook scheduler definiscono validazione route,
nomi, placement, worker e mode queued/direct. Non esiste un framework DI.
Nessun core singleton e nessun thread/connessione viene avviato caricando classi.

Il monolite inizializza la facade da App prima di esporre API e tracker.
Riot, compute e Sync condividono un Registry e conservano l'albero cross-scheduler.
Due Hive indipendenti hanno PID, contesto, registry e dedup indipendenti.
Submission prima del bootstrap o dopo shutdown fallisce esplicitamente.

La submission lega lo stesso tipo R allo scheduler e alla route e un contratto
`ResultType<T>` al risultato (`Class<T>` è la scorciatoia invariabile per scalari). Lo stesso scheduler/route/key attivo con un token
incompatibile viene rifiutato; non si crea un follower con un cast pericoloso.
Le chiavi sono identità strutturate, non concatenazioni ambigue di stringhe.
`ResultType.list(Element.class)` identifica e verifica anche gli elementi
delle liste; richieste List con elementi incompatibili non condividono un
source. I contratti non sono wildcard che consentono un tipo risultato più
stretto del token dichiarato.

Route logica e placement fisico restano distinti. La dedup usa la route logica;
spostare la destinazione fisica all'inserimento non cambia l'identità del job.
La proiezione HTTP mostra la route logica nel job e la queue fisica nel dispatcher.

## Implementazioni locali

**Riot:** una queue seriale per LeagueShard valida; UNKNOWN è rifiutato. I nomi
`r4j-<shard>-` e i log/toggle rimangono applicativi. La route platform shard
non viene trasformata in routing region; SDK e servizi continuano a scegliere
l'endpoint/region appropriati. Nessun cambiamento alla policy dei rate limit,
a cache/cooldown Redis, ai namespace o ai TTL.

**Compute:** quattro queue/worker seriali PROFILE, PROFILE_2, CHAMPION, MONGO.
La route PROFILE usa le load al momento dell'inserimento. Senza riserva può
scegliere tra i quattro; una matrix/build/full refresh champion queued o running
restringe la scelta a PROFILE/PROFILE_2. Nessun job già inserito migra.
Matrix, build, key, filtri, merge e chiamate di calcolo/persistenza restano locali.
Una matrix accetta richieste build finché non inizia; dopo `start()` lo snapshot
è immutabile e una build arrivata dopo segue il proprio lavoro deduplicato.

**Sync:** route shard o null globale, esecuzione immediata su virtual thread
posseduto dal runtime. Nessuna queue equivalente a compute/Riot, nessun worker
seriale per shard e nessun backlog Redis. Gli orchestratori che attendono figli
compute usano Sync per evitare join sul worker seriale che stanno occupando.

## Lifecycle e compatibilità

- Registrazione e validazione precedono dispatch. Duplicati e route sconosciute
  falliscono; nessuno switch centrale enumera i servizi concreti.
- Dedup solo dei lavori attivi per scheduler/route/key nello stesso runtime.
  Il riuso crea un PID follower senza seconda esecuzione fisica; risultato ed
  errore provengono dal source. Il risultato terminale non diventa una cache.
- IMMEDIATE > NORMAL > BACKGROUND, FIFO ordinario. Promozione del source queued
  davanti alla lane urgente; nessuna preemption/duplicazione del body running.
- Priorità child = più urgente fra richiesta e parent. Parent/child descrive
  ownership, non un limite massimo di urgenza.
- Il worker viene rilasciato quando il body termina; future/lifecycle attendono
  figli e callback registrati. I figli già completati contano nel totale parent.
- Parent riuscito con figli falliti: COMPLETED_WITH_ERRORS e risultato del body
  riuscito. Errori del body/enqueue/callback sono eccezionali.
- Retain/resume conserva e ripristina il contesto e rilascia anche in errore.
  I servizi usano continuation possedute dal runtime invece del common pool.
- I job di un altro registry non sono accettati anche quando hanno lo stesso PID.
  Cleanup rimuove entry e key secondo lifecycle, senza storico persistente.
- Progresso esplicito ha precedenza, anche sul parent. Contatori dei figli diretti
  sono fallback. Fase/item/outcome done/missing/failed restano disponibili.
- Status applicativo conserva tre livelli completi + massimo 100 job al quarto, e massimo
  20 queued job per worker. Il cap generico totale è configurabile; il monolite
  usa Integer.MAX_VALUE per preservare i primi tre livelli completi. Nessuna
  mappa item senza limite nel payload pubblico.

## Cancellazione e arresto

`Hive.termination(future)` offre uno stage di sola lettura della terminazione
logica effettiva. Il cleanup delle matrix locali si lega a quello stage, non
alla cancellazione della view pubblica; cancellare una view non perde il merge
con una matrix ancora attiva.

La cancellazione della future del chiamante è distinta dalla cancellazione
logica del job e dall'interruzione fisica del body. Non promette che un body
running si sia fermato. La creazione di un follower che genererebbe un ciclo di dipendenze viene
rifiutata; la dedup non deve trasformare due alberi attivi in un’attesa reciproca.
Lo shutdown chiude admission e gli executor posseduti,
cancella i lifecycle attivi e non riapre implicitamente il runtime. Callback
late e follower non devono riattivare lavoro cancellato.

I timeout sono configurati, non garanzie di terminazione per codice che ignora
l'interruzione. Un crash JVM non esegue cleanup. Queue in memoria senza backlog
durevole, retry persistito, broker, distributed dedup o exactly-once.

## Confini futuri e verifiche

Una libreria comune non condivide memoria tra processi. Lambda, future e contesto
ThreadLocal non attraversano HTTP. Correlazione remota, idempotenza writer,
contratti di job remoto e retention dei risultati richiedono task/ADR successivi.

API `/api/status`, domain payloads, schema Mongo, writer ordering, Redis e
presentazione rimangono invariati. [hive-validation.md](hive-validation.md)
registra test offline, indipendenza core, review e limiti delle prove live.
