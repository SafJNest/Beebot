# Separazione iniziale dei servizi

Stato: separazione servizi proposta; Hive sotto ADR-0017, tranche R4B accettata sotto ADR-0018. Data: 2026-10-10.
Baseline: branch `beebot-servicies`, commit `cef023bc`, working tree inizialmente pulito.

L'obiettivo è preparare tre servizi nella stessa repository e un nucleo condiviso,
estraibili successivamente in repository e artifact versionati separati. In questa
fase processi e client HTTP restano proposti. Hive e la tranche R4B hanno
decisioni autonome; il wiring rimane nel monolite. R4B possiede i contratti
`com.R4B.api.dto` e i valori/helper `com.R4B.lol`, conservando nomi canonici,
JSON, persistenza e presentazione.

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

7. [ADR-0018 R4B](../adr/0018-r4b-shared-contracts.md): decisione mirata dei contratti.
8. [Inventario R4B](r4b-inventory.md): endpoint, tipi, helper, conversioni e consumatori.
9. [Verifiche R4B](r4b-validation.md): build separate, compatibilità e review effettive.

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

Il core implementato usa la radice indipendente **`com.hive`**, come richiesto
dall’utente. Non è un package `shared` e non dipende da `com.safjnest`.
Gli altri namespace sotto descrivono solo la precedente proposta dei contratti
e dei servizi, da decidere nelle rispettive tranche:

```text
com.safjnest.shared
  dto.lol.{summoner,match,champion,statistics,leaderboard,record}
  dto.status                 # aggregati operativi esposti
  dto.http                   # schema errore HTTP, senza Spring
  dto.riot                   # soli dati del confine Spring ↔ R4J, da specificare
  http                       # riservato al futuro HttpBuilder/client

com.hive                     # implementato: infrastruttura autonoma
  job, scheduler, worker, status

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

ADR-0017 accetta esclusivamente il core `com.hive` e i suoi adapter,
preservando quattro worker compute e progresso esplicito prima dei figli.
Le regole attuali in `AGENTS.md`, ADR-0001/0005/0007/0009/0011/0012/0014/0015
restano vigenti. Spostare modelli fuori da `lol.model`, separare il registry
globale o condividere lo schema di `LolApiError` richiede la loro sincronizzazione
esplicita; nessun ADR accettato viene modificato da questa proposta.

## Verifica della fase documentale originale

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

## Tranche Hive

La specifica [job-queue.md](job-queue.md) descrive il risultato attuale.
Il report [hive-validation.md](hive-validation.md) separa le prove offline
e le review dalle integrazioni Mongo/Redis/Riot. Nessuna separazione dei processi
o migrazione dei DTO di dominio viene dichiarata completata.
