# Separazione iniziale dei servizi

Stato: Hive sotto ADR-0017 e R4B sotto ADR-0018; S4B e `lol-service` hanno un artifact alpha nei rispettivi repository. `lol-service` compila come applicazione standalone. Contratto del proxy Riot e controllo tecnico iniziale completati; gli agenti di implementazione lavorano in parallelo. Il trasporto S4B deve gestire localmente status/header, cooldown 429 e retry 5xx finiti; il taglio HTTP da Beebot è ancora da completare. Data: 2026-10-14.
Baseline: branch `beebot-servicies`, commit `cef023bc`, working tree inizialmente pulito.

Il target è Bbot in Beebot, `lol-service` e S4B in repository separate, con
Hive/R4B come artifact condivisi. S4B è pubblicato come package alpha, ma la
chiamata diretta dal servizio è ancora in uso. Il codice Spring è stato copiato
nel repository `lol-service`, ora compilabile e avviabile; l'estrazione non è completa.
I processi e i client HTTP restano da separare. R4B possiede i contratti
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
10. [Specifiche S4B](s4b-service-spec.md): package interno che possiede acquisizione Riot, queue fisica e cache.
11. [Specifiche lol-service](lol-service-spec.md): API LoL, dominio, Mongo, Compute/Sync e client S4B sotto `com.safjnest.spring`.
12. [Proxy degli endpoint Riot](riot-endpoint-proxy-spec.md): contratto comune host/path, DTO R4B, priorità, rate limit e revisione indipendente per S4B e lol-service.

## Responsabilità target

| Parte | Responsabilità | Dati e dipendenze proprie |
|---|---|---|
| Bbot | Discord, comandi, eventi, audio, embed, configurazione guild/utenti e integrazioni del bot; consuma le risposte LoL | JDA, audio, Twitch/Spotify e persistenza relazionale dei domini del bot |
| S4B | Chiamate Riot e dati statici, routing platform/region, rate limit, coda delle richieste esterne, risposte a Spring | SDK R4J, credenziali Riot, Redis per cache esterna e protezioni proprie |
| lol-service | API e proxy LoL, dominio, Mongo, calcolo di profili/champion/build/record/classifiche, tracking, refresh e code interne | Spring/Tomcat, Mongo, Redis per cache/proiezioni del dominio, client/proxy verso S4B; nessuna dipendenza da R4J |
| Comune | Strutture scambiate, core generico job/code, in seguito trasporto HTTP riutilizzabile | Nessun accesso a Mongo, Discord o Riot; nessuna configurazione dei servizi |

“Mongo” in questi documenti indica la parte dati/calcolo del servizio Spring,
non un quarto servizio. I servizi di dominio restano indipendenti dal framework:
il processo Spring li ospita, mentre controller/configurazione sono il suo adapter HTTP.

```text
Bbot ── richieste / DTO finali ──> lol-service ── Riot routes / R4B DTO ──> S4B ──> R4J ──> Riot
                                        │                                  ├── queue Riot
                                        ├── Mongo                          └── Redis R4J
                                        └── Redis dominio

Bbot, lol-service, S4B ── dipendono da ──> comune
comune ── non dipende da ──> servizi
```

Non è previsto accesso LoL diretto del bot a Mongo o Riot nel target. Il bot
continua a possedere i suoi dati guild/audio/utenti: “solo bot” non elimina
la persistenza necessaria a quei domini. Il reader LoL MariaDB e il comando
di migrazione sono stati rimossi; non fanno parte dei runtime dei servizi.

## Package e build

I package dei contratti e del core job sono definiti dagli ADR accettati.
Non introdurre una radice `shared` o copie dei contratti in Spring/Bbot:

```text
com.R4B.api.dto              # contratti LoL/acquisizione condivisi
com.R4B.lol                  # enum e helper LoL puri
com.hive                     # infrastruttura autonoma di job/code
  job, scheduler, worker, status

com.safjnest.bbot             # adapter e funzionalità del bot
com.safjnest.s4b              # acquisizione Riot, SDK e scheduler S4B
com.safjnest.spring           # composizione lol-service, API, dominio e persistence
```

S4B e lol-service sono processi distinti e dipendono dagli artifact R4B/Hive;
nessuna istanza runtime, registry o coda è condivisa tramite libreria.

La build usa repository Maven separati, senza un parent multi-module comune.
S4B e `lol-service` sono pubblicati come alpha. La build standalone di
`lol-service` è verificata; l'integrazione runtime tra i tre processi è ancora
aperta.

Una riorganizzazione di package in un unico JAR è una fase intermedia: la reale
separazione richiede build e bootstrap indipendenti. Il comune non deve caricare
credenziali, aprire connessioni o avviare thread durante il caricamento delle classi.

## Stato delle decisioni

Il perimetro richiesto è acquisito: Bbot, `lol-service` e S4B in repository
separate; Spring possiede API pubblica, tracker, Mongo e logica LoL, mentre S4B
possiede solo acquisizione Riot. Il client interno HTTP S4B, il bootstrap e le
configurazioni Redis/Hive indipendenti restano da implementare.

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
