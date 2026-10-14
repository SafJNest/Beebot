# ADR-0016: Confini dei servizi e nucleo condiviso

- Status: Proposed
- Owner: Main agent
- Date: 2026-10-10
- Scope: analisi iniziale sul branch `beebot-servicies`
- Implementation: Hive accettato separatamente in ADR-0017; contratti R4B accettati separatamente in ADR-0018. Processi, client HTTP e moduli applicativi restano proposti.

## Decisione di ownership e package 2026-10-13

L'ownership è confermata dall'utente: Bbot conserva i domini Discord; Spring
possiede API LoL, dominio, tracker, Mongo e proiezioni; S4B possiede solo
acquisizione Riot, SDK, routing, rate protection, queue Riot e cache raw Riot.
Nel monolite i package applicativi Spring vivono sotto
`com.safjnest.spring`; S4B resta sotto `com.safjnest.s4b`. Questa decisione
non anticipa client HTTP, bootstrap o processi separati, che restano proposti.

## Proposta di proxy Riot 2026-10-14

La specifica condivisa per il proxy Riot e il controllo tecnico iniziale sono
stati completati in
[`riot-endpoint-proxy-spec.md`](../services/riot-endpoint-proxy-spec.md).
Propone che S4B sia l'unico consumer di R4J e ricavi le route dagli
`URLEndpoint` della versione R4J bloccata. `lol-service` espone le stesse
route tramite un proxy catch-all, senza un secondo catalogo URL, e comunica
con S4B tramite HTTP usando i DTO esistenti in R4B. Spring passa la priorità
in `X-S4B-Priority`; una richiesta resta aperta durante l'attesa imposta da
un rate limit Riot. La queue usa lo scope Riot per region; S4B possiede il
cooldown/requeue `429`, mentre i retry `5xx` restano limitati. R4J 2.8.1 non
espone oggi gli header necessari, quindi S4B usa un trasporto JDK posseduto
dal servizio, mantenendo `URLEndpoint` come unica fonte dei path e binding
tipizzati per gli altri dettagli dell'operazione. Il client di `lol-service`
viene generato dallo stesso schema senza copiare le route né dipendere da R4J.
Il contratto include anche i payload statici Data Dragon di rune e versioni.

Il contratto è pronto per l'implementazione parallela. Il trasporto locale
S4B è una decisione della specifica proposta, non cambia lo stato degli ADR
accettati. Le specifiche di S4B e lol-service restano riallineate.

## Contesto

Il monolite avvia Discord, Spring, Riot scheduling, Mongo, Redis e calcoli LoL
con un singolo bootstrap e artifact. Il target richiesto è preparare Bbot,
R4J e Spring come servizi separati, inizialmente nella stessa repository e
successivamente in repository con versioning indipendente.

Le due estrazioni iniziali sono i contratti finali condivisi e il sistema
generico di job/code. I record/provider/accumulatori usati solo per calcolare
build/statistiche restano nel proprietario Spring/Mongo.

## Decisione proposta

1. Bbot possiede Discord e i domini del bot; consuma LoL mediante il client Spring.
2. R4J possiede SDK/credenziali Riot, acquisizione esterna/dati statici, routing,
   rate protection e Redis del proprio dominio; risponde a Spring senza scrivere Mongo.
3. Spring ospita API e dominio LoL, possiede Mongo, calcoli, tracking, job interni
   e cache/proiezioni Redis del dominio; richiede i dati esterni a R4J.
4. Il nucleo comune contiene i contratti canonici necessari a richieste/risposte
   e il core generico job/code. Un futuro package HTTP contiene soltanto trasporto.
5. Le radici accettate dalle tranche autonome sono `com.hive` (ADR-0017) e
   `com.R4B.api.dto` / `com.R4B.lol` (ADR-0018). Client HTTP e moduli
   applicativi restano proposti; non introdurre `shared` come nuovo wrapper.
6. Le strutture finali restano uniche e conservano i nomi canonici e il JSON.
   Separare i metodi persistenti/calcolo/SDK prima di trasferire i tipi.
7. Job runtime, registry e dedup appartengono alla singola istanza del servizio.
   Scheduler, route, naming, placement, logging e protezioni sono configurati
   localmente; il core non conosce shard Riot né queue/operation Mongo.
8. Le code iniziali restano volatili. Correlazione remota è esplicita; non si
   serializzano future, funzioni, classi scheduler o thread-local context.
9. Redis può essere disponibile a entrambi i servizi; non abilita writer incrociati
   né trasferisce la proprietà della persistenza dal servizio Spring a R4J.

## Regole da preservare

- Nomi canonici, PUUID/full match ID e `riotId:String`.
- Cascate di writer ADR-0015 e confini ingestione/completamento del match.
- Contratto JSON/status pubblico e presentazione Discord invariati.
- SDK/driver/framework esclusi dai contratti comuni target.
- Accumulatori, storage model e business policy esclusi dal core job generico.
- Nessun refactor di queue audio, guild cache o domini bot durante l'estrazione LoL.

## Relazione con le decisioni vigenti

Questo ADR **non è accettato** e non sostituisce alcun ADR attuale.
[ADR-0017](0017-hive-job-runtime.md) accetta esclusivamente Hive e il wiring
del monolite, con core `com.hive`. ADR-0018 accetta soltanto i DTO comuni,
i valori/helper LoL e la migrazione dei consumatori locali. Nessuna delle due
tranche accetta processi separati, moduli applicativi o confini HTTP remoti.
Prima di implementarlo devono essere aggiornate esplicitamente le regole
pertinenti in `AGENTS.md` e le decisioni sulla collocazione dei modelli
(ADR-0001/0005/0007), sulla ownership dei servizi/persistenza
(ADR-0009/0011/0012) e sul registry globale (ADR-0014).
ADR-0015 conserva i writer canonici nel servizio Spring.

Lo schema di errore HTTP può essere condiviso senza spostare mapping,
validazione, exception handling o dipendenze Spring nel comune. Anche questa
eccezione alla collocazione attuale va formalizzata nella tranche interessata.

Le discrepanze compute/progresso registrate nell'inventario devono essere
risolte prima del port dei rispettivi componenti; non vengono normalizzate
silenziosamente in questa decisione proposta.

## Alternative escluse dalla proposta

- Trasferire tutto `lol.model` o `utils` nel comune: include persistence,
  accumulatori, presentazione e dipendenze SDK.
- Condividere DTO finali duplicati per Spring e Bbot: divergenza di ownership.
- Usare un unico registry/coda tra processi perché la libreria è comune:
  memoria e contesto non sono condivisi attraverso HTTP.
- Far scrivere rank/match/proiezioni a R4J: introduce un secondo writer Mongo.
- Trasferire subito repository o introdurre un broker: fuori dalla prima fase richiesta.

## Criteri di accettazione successiva

Contratti compilabili indipendentemente dai servizi; job configurabili con route
estranee a LoL; bootstrap/build autonome; nessuna dipendenza concreta del comune
dai servizi; trasporto e writer con ownership chiara; fixture JSON, lifecycle e
integrazione pertinenti superate; documentazione/ADR sincronizzati.

La specifica completa e l'ordine delle tranche sono in
[services/README.md](../services/README.md).
