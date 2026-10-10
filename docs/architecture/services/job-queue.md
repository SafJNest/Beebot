# Specifica iniziale di job e code generici

Stato: proposta, basata su ADR-0014 e codice `cef023bc`.
La libreria comune gestisce il meccanismo; ogni servizio configura la propria
istanza e possiede routing, policy e funzioni da eseguire.

## Base esistente e destinazioni

| Componente attuale | Parte comune | Parte locale da isolare |
|---|---|---|
| `Job<T>` | Identità locale, priorità, nome/key/route validata, fase, item e progresso | Nessuna nomenclatura o shard di dominio; la funzione resta in memoria |
| `JobPriority`, `JobState`, `WorkerState` | Priorità e lifecycle/worker state | Nessun enum Riot o Mongo |
| `Registry` | Lifecycle, context, dedup, follower, parent/child, callback, future | Singleton obbligatorio e classificazione `runType` LoL |
| `Router` | Registrazione, validazione, dispatch e promozione | `GLOBAL` obbligatorio e `BotLogger` |
| `QueueHandler` | Unica superficie di submission, snapshot, retain/resume e shutdown | Registrazione automatica dei tre scheduler concreti |
| `AbstractScheduler<R>` | Infrastruttura fisica e hook, registry per istanza | Scelta route, nomi, placement, policy e bootstrap |
| `JobQueue` | Tre lane con ordinamento e promozione | Nessuna dipendenza di dominio |
| `JobWorker<R>` | Drain, lifecycle e rilascio del worker | Configurazione thread/limiti locale |
| `JobStatus`, `JobProgress`, `WorkerStatus`, `QueueStatus`, `SchedulerStatus` | Snapshot generici in `shared.job.status` | Aggregazione HTTP/processo resta locale |
| `RunStatus` | Eventuale forma generica dei dati | Interpretazione tracking/sample/rank nelle proiezioni Spring |
| `RiotScheduler` | Usa il core | R4J: shard, region, nomi, rate protection e logging |
| `ComputeScheduler`, `DatabaseWorkerType`, `ChampionMatrixRequest` | Usano il core | Spring: domini, riserva champion, merge matrix/build, facade e filtri |
| `SyncScheduler` | Meccanismo esecuzione diretta eventualmente estratto | Spring: route shard/global e attivazione di tracking/rank/match |

Il job core non importa `shared.dto.lol`, `lol.*`, SDK R4J, Mongo, JDA,
Spring, Redis o `App`. Gli snapshot generici possono essere consumati dai DTO
status applicativi; la dipendenza inversa non è ammessa. Il core usa il JDK,
senza framework DI, connessioni o thread avviati prima del bootstrap esplicito.

## Composizione per istanza

Ogni processo crea registry/router/scheduler e li chiude nel proprio lifecycle.
In una fase monolite possono già esistere runtime distinti; la separazione
deve però essere introdotta dopo i test sul cambiamento di parent/child.
Un runtime possiede un solo registry: schedulers, handler e worker di quel
runtime devono usare esattamente la stessa istanza.

La configurazione necessaria comprende:

- Clock e identità dell'istanza per test e correlazione.
- Scheduler registrati e validazione dei loro identificatori/route.
- Strategia di esecuzione: queue con worker, oppure esecuzione diretta.
- Route logica e placement fisico, con limiti di concorrenza/policy del servizio.
- Nomi di scheduler, queue e thread; label/report di stato e logging attraverso hook.
- Limiti snapshot, timeout di arresto ed eventuale admission policy.

Non è necessario aggiungere un'interfaccia per ogni parametro. Costruttori,
factory e hook esistenti possono bastare; il punto è eliminare le dipendenze
obbligatorie verso i tre scheduler concreti e verso un singleton globale.

## Submission e routing

La superficie resta una sola: `immediate`, `normal`, `background` oppure
una submission con priorità esplicita. La firma target deve associare lo stesso
tipo `R` a scheduler e route; `Class<?> + Object` attuale non fornisce quella
garanzia in compilazione, anche se la validazione runtime già rifiuta route errate.

Schema indicativo, non API Java già approvata:

```text
submit<T,R>(scheduler<R>, route:R, key, name, priority, work(Job<T>)) -> Future<T>
```

L'identificatore scheduler appartiene alla configurazione del runtime, non alla
classe Java serializzata. Una migrazione iniziale può conservare la facade e
la validazione esistenti; il target non deve richiedere `RiotScheduler.class`
nel common né uno switch centrale tra scheduler noti.

Separare:

1. Route logica: contesto del job, parte dell'identità di dedup.
2. Placement fisico: queue/worker scelto dallo scheduler all'inserimento.
3. Priorità: lane del lavoro dentro quella queue.

Due job con lo stesso testo key su route diverse non si deduplicano. Il
placement non cambia l'identità logica, non migra task già inseriti e non
interpreta automaticamente platform/region o domini Mongo.

## Configurazioni dei servizi

| Policy | R4J | Spring/Mongo |
|---|---|---|
| Route | Platform shard o routing region secondo endpoint/limiter | Profile, champion, Mongo e altri tipi di operazione |
| Placement | Serializzazione per route configurata; scope account/region da definire | Placement e riserva per carico/operazione |
| Key | Operazione + identità + parametri canonici Riot | PUUID/filterKey, champion/filter, matrix patch/queue ecc. |
| Nome | Label e nomi thread R4J | Label profili/champion/maintenance |
| Protezioni | Rate limit SDK/Riot, cooldown/cache esterna | Memoria, batch, costi compute e writer Mongo |
| Esito | Dati acquisiti o errore esterno | Persistenza, proiezioni e invalidazioni completate |

Il core non contiene `PROFILE`, `CHAMPION`, `EUW1`, `EUROPE`, patch, collection,
prefix di key champion o convenzioni di nome. `ChampionMatrixRequest` resta
Spring: aggrega richieste di build/matrice del dominio e non è un job generico.

La serializzazione per shard del `RiotScheduler` attuale non è una prova che
ogni rate limit Riot abbia scope shard. La futura configurazione R4J deve
esplicitare endpoint platform/region e scope del limiter, senza codificarli nel core.

## Invarianti da portare

1. Registrazione prima del dispatch; scheduler sconosciuto o route invalida
   falliscono esplicitamente.
2. Dedup soltanto tra lavori attivi dello stesso runtime, scheduler, route e key;
   il risultato completato non diventa una cache del registry.
3. Un riuso crea un follower logico con collegamento al source; non esegue
   una seconda funzione fisica e riceve il risultato/fallimento del source.
4. `IMMEDIATE > NORMAL > BACKGROUND`; FIFO ordinario nella lane. La promozione
   di un source queued lo mette davanti alla nuova lane. Il lavoro running
   non viene interrotto o duplicato.
5. Il contesto locale determina PPID. Il codice corrente eredita la priorità
   più urgente tra parent e richiesta; un child può essere più urgente del parent.
6. Terminare il body libera il worker fisico; la future può restare in
   `WAITING_CHILDREN` finché figli e callback registrati terminano.
7. Errori dei figli non cancellano i fratelli; un parent con body riuscito può
   terminare `COMPLETED_WITH_ERRORS`. Questo stato non implica necessariamente
   una future eccezionale: preservare/testare la distinzione.
8. `retain` prima della conclusione del body protegge il callback; `resume`
   ripristina il parent e rilascia il riferimento anche in errore.
9. Le entry terminali vengono rimosse quando il lifecycle e i riferimenti
   attivi sono esauriti. Il registry non è uno storico persistente.
10. Il progresso mantiene fase, item corrente, totale e item terminali
    done/missing/failed. Le snapshot pubbliche restano limitate e non espongono
    mappe item senza limite.

Il progresso dei parent presenta una discrepanza da risolvere: l'ADR descrive
i figli diretti, mentre il codice preferisce il progresso esplicito se presente.
Anche eventuali doppi aggiornamenti di un item devono essere caratterizzati:
nessun miglioramento di semantica nascosto dentro uno spostamento di package.

Gli snapshot attuali HTTP mostrano tre livelli completi e al massimo 100 job
del quarto; la vista del worker limita i queued job a 20. Il nuovo core può
rendere questi limiti configurabili, mentre Spring mantiene gli stessi default
e payload per non cambiare `/api/status` durante l'estrazione.

## Lifecycle, carico e arresto

Il bootstrap deve registrare gli scheduler prima di accettare lavoro; tipi
duplicati falliscono. L'arresto deve interrompere nuove submission, gestire
queued/running/follower/callback e chiudere gli executor del solo runtime.
L'attuale queue è in memoria e senza capacità massima esplicita: non dichiararla
già bounded. La compatibilità iniziale conserva questa scelta; una capacity
opzionale richiede una policy esplicita di rifiuto, non scarto silenzioso.

Non attendere su una richiesta figlia destinata allo stesso worker seriale
che il parent sta occupando: il port deve cercare questi join e mantenere il
rilascio del worker o la composizione asincrona dove necessari. Non spostare
continuation di persistenza su executor non posseduti dal servizio incidentalmente.

Cancellazione di una future, cancellazione del job e interruzione del body
non sono sinonimi. Deadline, watchdog e propagazione delle cancellazioni vanno
specificati/testati prima di promettere che ogni task termina sempre. Un crash
JVM o un body bloccato non esegue automaticamente il cleanup applicativo.

## Confine tra processi

Condividere la libreria non condivide la memoria. Ogni processo avrà PID/PPID,
dedup, future, priorità e queue locali. `ThreadLocal`, lambda e `Class<?>`
non attraversano HTTP. Il core non invia codice eseguibile né `Job<T>` sul wire.

Per il primo confine HTTP, una richiesta Spring → R4J può essere rappresentata
da un child locale il cui body resta aperto fino al risultato HTTP; R4J crea
il proprio job locale. Correlation ID collega le due esecuzioni, senza fingere
che il registry Spring possieda i figli interni del registry R4J.

Se il servizio remoto risponderà prima del completamento, servirà un contratto
di accettazione/stato/risultato e retention dei risultati; non è fornito
dall'attuale registry che rimuove i terminali. Per identificare job remoti, una
reference deve comprendere servizio, istanza/avvio e PID, oppure un ID globale
equivalente, mai il solo PID numerico. La scelta è una decisione successiva.

La priorità inviata è una richiesta: il servizio remoto la valida secondo
la propria policy. Timeout HTTP non prova che il lavoro remoto sia stato
cancellato; retry non garantisce esecuzione unica o scrittura unica. Dedup
locale non basta tra repliche o dopo restart: idempotenza dei writer e, se
richiesto, coordinamento multi-istanza costituiscono task separati.

## Redis e persistenza delle code

Redis resta cache/proiezione o protezione del servizio secondo ownership.
Il job core iniziale non introduce backlog, pub/sub, Redis Streams, distributed
lock, retry persistiti, serializzazione di lambda o garanzie exactly-once.
La necessità di una coda durevole richiederebbe un contratto di comandi
serializzabili e un ADR distinto. Il comportamento volatile di ADR-0014 viene
preservato, salvo la separazione dei runtime formalmente accettata.

## Verifica richiesta per l'implementazione

I test devono dimostrare isolamento tra runtime, configurazione con route
non LoL, dedup/follower, promozione, parent/child, errore e cancellazione,
retain/resume, cleanup e arresto. Servono anche fixture status e test delle
policy locali: platform/region R4J, placement/riserva/merge di Spring.
`RouterTest` e `ChampionMatrixRequestTest` sono la base individuata nel progetto;
non attestano da soli tutti questi casi. Nessun test è eseguito per questa proposta.
