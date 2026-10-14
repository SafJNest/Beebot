# Piano iniziale di estrazione

Stato: tranche package e prima applicazione standalone di `lol-service`
implementate e pubblicate come alpha, dopo le tranche Hive e R4B. Il contratto
del proxy Riot e il controllo tecnico iniziale sono completati; gli agenti S4B
e `lol-service` lavorano in parallelo. S4B possiede il trasporto HTTP che
legge status/header Riot e il client tipizzato viene generato dallo stesso
schema binding; il passaggio HTTP da Beebot e la rimozione delle copie locali
restano aperti.

## Ordine delle fasi

| Fase | Lavoro | Gate di completamento |
|---|---|---|
| 0 — Analisi | Inventario, DTO, job, ownership e dipendenze | Documenti presenti, conflitti registrati, nessuna modifica runtime |
| 1 — Decisione e baseline | Definire layout, accettare/sincronizzare la decisione architetturale e caratterizzare comportamento vigente | Regole/ADR allineati; risolte discrepanze compute/progresso; fixture JSON e test lifecycle |
| 2 — Contratti comuni | Separare metodi operativi dai modelli canonici e trasferire DTO/dipendenze di dati | Contratti compilano senza servizi/SDK/driver; JSON compatibile; nessun DTO duplicato |
| 3 — Job/code comuni | Hive per istanza, core generico e adapter locali implementati nel monolite | Evidenze e review in hive-validation.md; processi ancora condivisi |
| 4 — Confini package | S4B sotto `com.safjnest.s4b`; API LoL, dominio, tracker, Mongo e runtime sotto `com.safjnest.spring` nello stesso root POM | Ownership isolata; nessun cambiamento al JSON o al bootstrap |
| 5 — Proxy Riot S4B | Route Riot-shaped derivate da `URLEndpoint`, proxy catch-all in lol-service, DTO R4B, priorità e attesa durante rate limit | Nessuna route URL duplicata; lol-service senza SDK/S4B implementation; S4B senza Mongo/JDA; controllo indipendente di scope regionali, retry `429`, retry `5xx` finiti e durata HTTP |
| 6 — Confine Bbot | Client verso Spring e conversione dei comandi/maintenance alle API del proprietario | Bot senza accesso LoL diretto a Mongo/Riot; embed e comandi compatibili |
| 7 — Repository separate | Pubblicazione dei comuni, versioni e release indipendenti | Contratti e build già isolati; smoke test tra versioni supportate |

L'estrazione di DTO e job può iniziare mantenendo un processo unico per ridurre
il numero di variabili. Le loro dipendenze vanno comunque isolate fin da subito.
Il passaggio a processi distinti cambia il contesto parent/child e richiede
il gate di correlazione/error handling, non un semplice cambio di import.

## Prima tranche dei contratti

1. Stabilizzare serializzazione e rappresentazione dei valori/enum necessari.
2. Partire da `ResponseMetadata`, valori semplici e DTO senza side effect
   (`Mastery`, indexables e wrapper/pagine compatibili con la loro chiusura di tipi).
3. Preparare `SummonerView`/`SummonerOverview`: rimuovere dipendenze da persistenza,
   lookup statici e accumulatori dalla chiusura `Summoner`/statistiche.
4. Preparare `Build` e `ChampionView`/`ChampionStatistics`: shape rune comune,
   calcolo privato Spring, distinzione tra codec HTTP e codec storage.
5. Estrarre match/livegame/history, activity/matchups, records, leaderboard e
   status rispettando lo stesso proprietario canonico.

Un wrapper con pochi campi non è estraibile prima dei suoi tipi annidati.
Non lasciare classi duplicate nei vecchi package “temporaneamente” senza
un gate esplicito; i consumatori cambiano import nella stessa tranche.
Gli adapter privati possono convertire SDK/BSON verso il modello canonico.

## Prima tranche dei job

1. Caratterizzare `Registry`, `Router`, `JobQueue`, worker e callback, compresi
   fallimento di enqueue, parent con progresso proprio, follower e shutdown.
2. Rendere esplicita l'istanza runtime mantenendo tutti gli scheduler del primo
   port sullo stesso registry, finché il confine di processo non viene introdotto.
3. Estrarre core e snapshot generici; spostare startup, logging e `runType`
   nei proprietari applicativi.
4. Applicare un adapter R4J e un adapter compute/Sync con le policy correnti
   concordate; nessuna riscrittura opportunistica del routing.
5. Separare i runtime con la build autonoma e la correlazione dei confini HTTP.

Non includere nel primo task job un broker durevole, distributed dedup,
retry persistiti, nuovo autoscaling o cambiamento del numero di worker.

## Operazioni interne da coprire prima del taglio del bot

Le API GET pubbliche non coprono tutte le operazioni del bot. Link/unlink,
abilitazione tracking, import match/history, OP.GG, comandi owner di rigenerazione,
repair, toggle/logging e letture operative devono essere inventariati per chiamante
e trasformati in richieste al servizio proprietario, con controllo delle autorizzazioni.

La specifica interna dovrà elencare per ciascuna operazione input, output,
idempotenza, priorità ammessa, completamento e progresso. I comandi Discord
mantengono permessi, help, cooldown e presentazione; non diventano un accesso
remoto arbitrario a query Mongo o codice eseguibile.

## Checklist per ogni tranche

- CodeGraph aggiornato; explore/impact mirati prima di modificare codice.
- Un solo writer per dato/proiezione e una sola classe canonica per contratto.
- Compile del modulo interessato e test mirati al comportamento modificato.
- Fixture HTTP prodotte con il mapper di trasporto e consumate dai client:
  profile ready/partial/pending, livegame assente/presente, match, build/statistics,
  records, leaderboard, status, errori e liste.
- Validare null/assenze e map keys, enum/alias, campi nascosti e named properties.
- Test job: due runtime senza interferenze, route custom, dedup/follower,
  priorità/promozione, parent/child, retain/resume, errori, cleanup e shutdown.
- Test adapter: placement compute concordato, riserva/merge champion e routing
  R4J platform/region con rate protection.
- Review dei join e delle continuation; nessun calcolo costoso sul thread HTTP.
- Smoke test di ciascun servizio con gli altri spenti, poi integrazione HTTP con
  timeout, indisponibilità, `202`, risposte vuote e failure di persistenza.
- Mongo/Redis live solo nelle tranche che cambiano accesso/ownership, riportando
  separatamente test offline, fixture e prova runtime.
- API e documentazione sincronizzate; nessuna modifica della presentazione non richiesta.

Non introdurre nuove librerie se JDK, Jackson e infrastruttura già presenti
soddisfano il requisito. I controlli devono seguire il rischio della tranche,
senza far passare una compilazione o test statici per validazione dei servizi reali.

## Decisioni da chiudere al momento opportuno

| Decisione | Proposta iniziale | Necessaria prima di |
|---|---|---|
| Namespace/artifact | Hive `com.hive`; R4B `com.R4B.api.dto` / `com.R4B.lol`; artifact autonomi dai sorgenti canonici | Tranche accettate 0017/0018; moduli applicativi rinviati |
| Enum LoL sul wire | Tipi del contratto con valori JSON compatibili; mapping SDK in R4J | DTO indipendenti da R4J |
| Entity operative | Metodi persistenti privati del writer, stesso modello canonico di dati | Estrazione `Summoner`/`Match` |
| Compute/progresso | Caratterizzare e risolvere le discrepanze registrate | Port scheduler/registry |
| API S4B | Route derivate dagli `URLEndpoint` R4J usati, binding tipizzati ai DTO R4B, risorse Data Dragon senza envelope, header di priorità interno | Bootstrap del proxy S4B e client/proxy lol-service |
| Completamento remoto | HTTP sincrono; richiesta aperta durante l'attesa di rate limit; retry `429` posseduto da S4B e retry `5xx` finiti | Confine di processo; niente `202`/polling per acquisizioni Riot |
| Redis | Client/pool per servizio, cataloghi chiavi posseduti separatamente | Bootstrap indipendenti |
| Invalidazione cache raw dopo insert | Chiamata autenticata e tipizzata al proprietario S4B, se ancora richiesta | Rimozione delle scritture Redis incrociate |
| Status | Metriche locali; aggregazione/correlazione esplicita | Status di tre processi |
| Repliche/durabilità | Nessuna garanzia nuova nella prima estrazione | Scaling multi-istanza o backlog durevole |

Con Redis condiviso fisicamente si può mantenere il namespace attuale nella
fase transitoria; i writer delle chiavi devono comunque essere distinti.
Un cambio di namespace/TTL richiede un piano di invalidazione/rebuild, non
una modifica implicita nella separazione dei servizi.

## Versioning successivo

S4B è pubblicato su JitPack al commit `e4424d6`; Beebot lo pinna a quel commit.
`lol-service` ha l'alpha `0.1.0-alpha-1` pubblicata dopo una build Maven
standalone riuscita. Le modifiche additive devono restare compatibili; quelle breaking
vanno coordinate con i consumatori e verificate con fixture tra le versioni
supportate. Una rename Java può essere breaking per i binari anche a JSON invariato.

## Consegna della definizione proxy

Questa fase ha aggiornato la specifica e i rimandi architetturali, senza
modificare endpoint, JSON, command/embed, documenti Mongo, chiavi Redis,
dipendenze Maven o package Java. Il controllo read-only ha confermato la
derivazione dei path dagli `URLEndpoint` e ha rilevato che R4J 2.8.1 non espone
status/header e applica la stessa policy di retry a 429 e 5xx. Il contratto
stabilisce quindi un trasporto JDK posseduto da S4B, con binding tipizzati che
usano `URLEndpoint` come fonte dei path; S4B possiede il cooldown/requeue 429,
e i retry 5xx restano limitati per numero e durata. Lo schema binding genera
anche il contratto client di `lol-service`, senza route scritte una seconda
volta. La copertura include anche
`runesReforged.json` e `versions.json` come risorse JSON statiche senza
envelope. Ora partono in parallelo gli agenti S4B e `lol-service`; il
controllore verifica l'allineamento durante il lavoro e al handoff.

## Decisione della tranche job/code

ADR-0017 conserva il codice verificato: quattro worker compute e progresso
esplicito prima dei contatori figli. Il core vive in `com.hive`, i concreti
in `s4b.scheduler` e `spring.lol.scheduler`; Spring compone un solo runtime condiviso.
DTO dominio, moduli dei servizi e trasporto restano fasi indipendenti.

## Decisione della tranche R4B

ADR-0018 autorizza contratti, componenti serializzati, enum/helper LoL e la
migrazione completa dei consumatori locali. Non introduce client HTTP, builder
richieste, processi distinti o una riscrittura Hive. I servizi continuano a
comporsi localmente, con SDK dietro adapter `r4j` e firme indipendenti.
La chiusura deve compilare senza SDK fuori dagli adapter, oltre alla build
autonoma R4B. [Inventario](r4b-inventory.md) e [verifiche](r4b-validation.md)
descrivono la tranche effettiva; le future fasi 4–7 restano proposte.

## Confine S4B

La specifica [s4b-service-spec.md](s4b-service-spec.md) registra il target
concordato e la tranche attuale: gateway e scheduler sotto il package S4B nel
root POM. Le API interne, il client lol-service, il taglio del processo, il
runtime Hive e la pool Redis indipendenti restano tranche successive.
