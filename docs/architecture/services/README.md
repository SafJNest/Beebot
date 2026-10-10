# Separazione iniziale dei servizi

Stato: proposta tecnica, solo analisi e documentazione. Data: 2026-10-10.
Baseline: branch `beebot-servicies`, commit `cef023bc`, working tree inizialmente pulito.

L'obiettivo è preparare tre servizi nella stessa repository e un nucleo condiviso,
estraibili successivamente in repository e artifact versionati separati. In questa
fase non vengono spostati package, modificate dipendenze o introdotti processi.

## Documenti

1. [Inventario e accoppiamenti](inventory.md): copertura, responsabilità attuali,
   confini proposti e problemi da risolvere.
2. [Inventario per file](source-inventory.md): destinazione preliminare di tutti
   i 424 file Java sotto `src/main/java`; non equivale all'approvazione di uno spostamento.
3. [Contratti condivisi](shared-contracts.md): risposte canoniche, dipendenze
   transitive, esclusioni, serializzazione e futuro client HTTP.
4. [Job e code generici](job-queue.md): core riutilizzabile, configurazione locale,
   invarianti e limiti tra processi.
5. [Piano di estrazione](migration-plan.md): ordine, criteri di completamento,
   test e decisioni ancora aperte.
6. [ADR-0016 proposto](../adr/0016-service-boundaries-and-shared-core.md): registra
   il target senza sostituire gli ADR accettati.

## Responsabilità target

| Parte | Responsabilità | Dati e dipendenze proprie |
|---|---|---|
| Bbot | Discord, comandi, eventi, audio, embed, configurazione guild/utenti e integrazioni del bot; consuma le risposte LoL | JDA, audio, Twitch/Spotify e persistenza relazionale dei domini del bot |
| R4J | Chiamate Riot e dati statici, routing platform/region, rate limit, coda delle richieste esterne, risposte a Spring | SDK R4J, credenziali Riot, Redis per cache esterna e protezioni proprie |
| Spring | API, servizi del dominio LoL, Mongo, calcolo di profili/champion/build/record/classifiche, tracking, refresh e code interne | Spring/Tomcat, Mongo, Redis per cache/proiezioni del dominio, client verso R4J |
| Comune | Strutture scambiate, core generico job/code, in seguito trasporto HTTP riutilizzabile | Nessun accesso a Mongo, Discord o Riot; nessuna configurazione dei servizi |

“Mongo” in questi documenti indica la parte dati/calcolo del servizio Spring,
non un quarto servizio. I servizi di dominio restano indipendenti dal framework:
il processo Spring li ospita, mentre controller/configurazione sono il suo adapter HTTP.

```text
Bbot ── richieste / DTO finali ──> Spring ── richieste / dati esterni ──> R4J ──> Riot
                                  │                                  │
                                  ├── Mongo                          └── Redis R4J
                                  └── Redis dominio

Bbot, Spring, R4J ── dipendono da ──> comune
comune ── non dipende da ──> servizi
```

Non è previsto accesso LoL diretto del bot a Mongo o Riot nel target. Il bot
continua a possedere i suoi dati guild/audio/utenti: “solo bot” non elimina
la persistenza necessaria a quei domini. `LeagueDB` resta uno strumento di
backfill e non diventa un servizio runtime.

## Package e build proposti

Mantenere il namespace aziendale `com.safjnest`, anziché creare radici ambigue
come `com.dto` o `com.job`. Sotto questa radice:

```text
com.safjnest.shared
  dto.lol.{summoner,match,champion,statistics,leaderboard,record}
  dto.status                 # aggregati operativi esposti
  dto.http                   # schema errore HTTP, senza Spring
  dto.riot                   # soli dati del confine Spring ↔ R4J, da specificare
  job.{scheduler,worker,status}
  http                       # riservato al futuro HttpBuilder/client

com.safjnest.bbot             # adapter e funzionalità del bot
com.safjnest.r4j              # adapter Riot e policy del servizio
com.safjnest.spring           # composizione del servizio, API e dominio ospitato
```

I nomi dei package dei servizi e degli artifact sono proposti, non definitivi.
La migrazione del dominio nel processo Spring non autorizza a creare success
DTO sotto `spring.dto`: i contratti saranno unici in `shared.dto`.

La prima build separata proposta è Maven multi-module: `shared`, `bbot`, `r4j`,
`spring`, sotto un parent comune. Un solo modulo `shared` è sufficiente per
l'inizio; i suoi sottopackage devono comunque rispettare confini verificabili.
In seguito DTO, job e HTTP possono diventare artifact distinti senza riscriverne
le responsabilità. Non serve separarli subito in ulteriori repository.

Una riorganizzazione di package in un unico JAR è una fase intermedia: la reale
separazione richiede build e bootstrap indipendenti. Il comune non deve caricare
credenziali, aprire connessioni o avviare thread durante il caricamento delle classi.

## Stato delle decisioni

Il perimetro richiesto è acquisito: tre servizi, stessa repository, contratti
finali comuni, job/code generici e calcoli specifici dentro Spring/Mongo.
Layout preciso, API interna R4J, rappresentazione degli enum e gestione dei
job remoti richiedono una decisione prima della rispettiva implementazione.

Le regole attuali in `AGENTS.md`, ADR-0001/0005/0007/0009/0011/0012/0014/0015
restano vigenti. Spostare modelli fuori da `lol.model`, separare il registry
globale o condividere lo schema di `LolApiError` richiede la loro sincronizzazione
esplicita; nessun ADR accettato viene modificato da questa proposta.

## Verifica di questa fase

Analisi su indice CodeGraph aggiornato, inventario completo dei sorgenti Java
principali, esplorazione dei confini e impact di `QueueHandler`, `SummonerView`,
`Build` e `LeagueHandler`; lettura di handbook, ADR e documentazione API/Mongo.
L'inventario per file distingue evidenza strutturale, import testuali e destinazione
proposta; non è una verifica runtime di ogni metodo.

API pubbliche, payload, presentazione Discord, storage, Redis e comportamento
delle code restano invariati. La revisione della documentazione è soddisfatta
con questi documenti e i rimandi negli indici; non occorre riscrivere gli endpoint
o la guida Mongo per un target non implementato. Nessun test Maven, Mongo,
Redis o Riot viene dichiarato eseguito in questa fase documentale.
