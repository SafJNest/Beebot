Analizza il progetto tramite CodeGraph, concentrandoti inizialmente su `LeagueService`.

`LeagueService` è diventato troppo grande, frammentato e difficile da comprendere. Il suo comportamento deve essere suddiviso in service di dominio più piccoli, coerenti con lo stile architetturale già presente nel repository.

Prima di modificare il codice:

1. Analizza `LeagueService` e tutti gli altri service collegati.
2. Ricostruisci tramite CodeGraph:

   * dipendenze;
   * chiamanti;
   * flussi sincroni e asincroni;
   * accessi a MongoDB;
   * accessi a Redis;
   * chiamate Riot tramite R4J;
   * logica di composizione delle response.
3. Individua:

   * comportamenti duplicati;
   * metodi che fanno più cose;
   * nomi ambigui;
   * responsabilità appartenenti al dominio sbagliato;
   * varianti sync e async implementate in modo incoerente;
   * logica ripetuta in service differenti.
4. Produci prima un piano di refactor completo, indicando:

   * nuovi service;
   * responsabilità di ciascun service;
   * metodi da spostare;
   * metodi da unire;
   * metodi da eliminare;
   * metodi da rinominare;
   * dipendenze da aggiornare;
   * ordine consigliato delle modifiche.

Non iniziare il refactor finché il piano non è chiaro e non sono stati identificati tutti i riferimenti a `LeagueService`.

## Principi architetturali

Ogni comportamento deve esistere in un solo punto.

Non devono esserci due metodi differenti che implementano sostanzialmente la stessa operazione.

Ogni metodo deve avere una responsabilità precisa. Evita metodi che combinano:

* lettura cache;
* query Mongo;
* chiamata Riot;
* trasformazione;
* composizione della response.

I nomi devono essere semplici e contestuali. Quando il dominio è già espresso dal nome del service, non ripeterlo inutilmente nei metodi.

Esempio:

```java
summonerService.get(...)
summonerService.getAsync(...)
```

e non:

```java
summonerService.getSummoner(...)
summonerService.getSummonerAsync(...)
```

Le varianti sync e async devono condividere lo stesso core. La versione sincrona deve limitarsi, quando necessario, ad attendere il risultato della versione asincrona o del relativo metodo core, senza duplicare la logica.

Mantieni lo stile degli altri service del progetto, migliorandolo dove esistono pattern duplicati o poco chiari.

## Service di dominio

Estrarre progressivamente le responsabilità di `LeagueService` nei seguenti service:

* `SummonerService`
* `MatchService`
* `RankService`
* `MasteryService`

Valuta nomi differenti solo quando risultano più coerenti con il dominio reale del repository.

Al termine del refactor non devono rimanere riferimenti funzionali a `LeagueService`. Se la classe diventa completamente inutile, eliminarla.

## Primo step: SummonerService

Partire dal dominio summoner.

All’interno di `SummonerService`, il core pubblico principale deve essere semplice:

```java
get(...)
getAsync(...)
```

### Core interno

Il flusso di integrazione deve essere suddiviso chiaramente nei seguenti metodi privati:

```java
fetch(...)
query(...)
cache(...)
```

Responsabilità:

* `fetch`: esegue la chiamata esterna tramite R4J;
* `query`: legge da MongoDB in modo sincrono;
* `cache`: legge o scrive Redis in modo sincrono.

La chiamata Riot deve essere sempre modellata in modo asincrono tramite `CompletableFuture`.

Sarà il chiamante a decidere se:

* attendere il risultato con `join`;
* propagare il future;
* restituire una response `202 Accepted` mentre il dato viene recuperato o aggiornato.

Non duplicare il flusso cache → database → Riot tra più metodi.

Deve esserci un unico percorso decisionale per il recupero del summoner.

## Oggetti Riot collegati al summoner

Gestire separatamente i due oggetti R4J:

```java
getRiotSummoner(...)
getRiotSummonerAsync(...)

getRiotAccount(...)
getRiotAccountAsync(...)
```

### getRiotSummoner

Deve restituire il `Summoner` di R4J.

Il relativo flusso deve includere, in un unico punto:

* eventuale lettura da Redis;
* chiamata Riot quando necessaria;
* aggiornamento della cache;
* upsert su MongoDB quando previsto.

La versione async restituisce il `CompletableFuture`.

La versione sync usa lo stesso flusso e attende il future solo quando richiesto dal contesto.

### getRiotAccount

Deve restituire l’`Account` di R4J.

Anche in questo caso devono esistere una variante sync e una async basate sullo stesso core, senza duplicazioni.

Verifica se cache e persistenza dell’account seguono realmente lo stesso comportamento del summoner. Non forzare un’astrazione comune se i due domini hanno lifecycle o chiavi differenti.

## Search e query aggiuntive

Dopo aver stabilizzato il core, classificare tutti gli altri metodi attualmente collegati ai summoner, ad esempio:

* ricerca per Riot ID;
* ricerca per PUUID;
* ricerca per summoner ID;
* ricerca per nome e tag;
* query Mongo specifiche;
* autocomplete;
* lookup per region;
* aggiornamento;
* refresh;
* upsert.

Ogni metodo deve avere:

* un nome che descriva esattamente il criterio di ricerca;
* una sola responsabilità;
* un unico core condiviso tra sync e async;
* nessuna duplicazione del fallback cache/database/Riot.

Metodi equivalenti con firme leggermente diverse devono essere unificati quando possibile.

## MatchService, RankService e MasteryService

Dopo `SummonerService`, applicare lo stesso criterio agli altri domini.

Per ciascun service:

1. identificare il metodo pubblico principale;
2. separare accesso cache, database e Riot;
3. centralizzare il flusso di fallback;
4. mantenere un unico core async;
5. creare wrapper sync solo dove realmente necessari;
6. spostare search, query e aggregazioni nel dominio corretto;
7. eliminare duplicazioni provenienti da `LeagueService`;
8. aggiornare tutti i chiamanti.

I nomi devono essere contestuali:

```java
matchService.get(...)
rankService.get(...)
masteryService.get(...)
```

Utilizzare nomi più specifici soltanto quando esistono operazioni realmente differenti, ad esempio:

```java
matchService.getById(...)
matchService.getRecent(...)
rankService.getSoloQueue(...)
masteryService.getByChampion(...)
```

Non creare metodi generici che nascondono comportamenti differenti attraverso molti flag o parametri booleani.

## ProfilePageService

Creare o rifattorizzare `ProfilePageService` come orchestratore della pagina profilo.

`ProfilePageService` non deve conoscere i dettagli di:

* Redis;
* MongoDB;
* R4J;
* upsert;
* fallback;
* mapping interni dei singoli domini.

Deve esclusivamente:

1. invocare i service di dominio necessari;
2. avviare in parallelo le operazioni indipendenti;
3. attendere i risultati già disponibili;
4. riconoscere le operazioni ancora in caricamento;
5. decidere quando restituire `202 Accepted`;
6. costruire la response finale della pagina profilo.

Esempio concettuale:

```java
SummonerService
RankService
MasteryService
MatchService
        ↓
ProfilePageService
        ↓
ProfilePageResponse
```

Le chiamate indipendenti devono essere eseguite in parallelo tramite future, evitando sequenze di `join` che rendano il flusso inutilmente seriale.

Il `join` deve avvenire solamente nel punto di orchestrazione corretto.

La logica relativa al `202` deve essere centralizzata e non replicata nei singoli controller o nei service di dominio.

Valuta la creazione di un risultato esplicito che distingua:

* dato disponibile;
* caricamento avviato;
* dato non trovato;
* errore esterno;
* errore interno.

Evita però di introdurre astrazioni generiche se non semplificano concretamente il codice esistente.

## Controller

I controller devono diventare sottili.

Devono limitarsi a:

* validare l’input HTTP;
* chiamare il service appropriato;
* trasformare il risultato in response HTTP.

Non devono contenere:

* accessi Redis;
* query Mongo;
* chiamate R4J;
* `join` distribuiti;
* logica di upsert;
* decisioni complesse sullo stato dei dati.

## Risultato atteso

Al termine del lavoro:

* `LeagueService` è eliminato oppure ridotto fino a non avere più responsabilità;
* ogni dominio possiede il proprio service;
* ogni comportamento è implementato una sola volta;
* sync e async condividono lo stesso core;
* cache, query e fetch sono chiaramente separati;
* i nomi dei metodi sono brevi, contestuali e non ambigui;
* `ProfilePageService` orchestra i service senza conoscere i dettagli infrastrutturali;
* le operazioni indipendenti vengono eseguite in parallelo;
* la gestione del `202 Accepted` è centralizzata;
* tutti i riferimenti e i chiamanti vengono aggiornati;
* non rimangono metodi obsoleti, alias temporanei o wrapper duplicati.

Procedi per step piccoli e verificabili:

1. analisi CodeGraph;
2. piano completo;
3. `SummonerService`;
4. migrazione dei chiamanti summoner;
5. eliminazione della relativa logica da `LeagueService`;
6. `MatchService`;
7. `RankService`;
8. `MasteryService`;
9. `ProfilePageService`;
10. aggiornamento controller;
11. rimozione definitiva di `LeagueService`;
12. verifica finale delle duplicazioni e delle dipendenze residue.

Prima di implementare, mostra il piano con la mappatura:

```text
Metodo attuale
→ Nuovo service
→ Nuovo metodo
→ Azione: sposta / unisci / rinomina / elimina
→ Chiamanti da aggiornare
```
