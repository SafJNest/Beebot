# Audit degli endpoint Beebot e LoL

Inventario del checkout Beebot con base `9c6e74b0` al 21 novembre 2026, aggiornato dopo il ritiro di `/servers`. Le annotazioni Spring espongono **90 combinazioni di metodo e percorso**. Sono 64 sotto `/api/beebot`, 25 sotto `/api/lol` e una per lo stato. Il conteggio include gli alias espliciti e conta separatamente i metodi sullo stesso percorso.

Il confronto usa il codice corrente di the-dashboard (base `a17b36d`, con modifiche locali) e il client Iodid `a7858e1`. La presenza di un consumatore nel sorgente è evidenza statica. Le stime sulle operazioni SQL derivano dal codice e la latenza resta da misurare.

## Priorità del riordino

La fase corrente riguarda Beebot. L'inventario LoL rimane nella fotografia delle rotte.

| Priorità | Operazione | Effetto e verifica |
| --- | --- | --- |
| 1 | Allineare `GET /me/soundboards` alla pagina My soundboards | Implementato. Restituisce le board dell’autore, comprese quelle legate a una gilda. La creazione da `POST /me/soundboards` continua a creare board senza gilda. |
| 2 | Completare la migrazione dei voti al `PUT /sounds/{id}/vote` | I due PATCH storici sono candidati al ritiro. Verificare i client e la differenza di visibilità dei suoni privati di gilda prima del passaggio. Il contratto nuovo imposta il voto desiderato o lo azzera e la dashboard lo usa già. |
| 3 | Unire ricerca e autocomplete | Proposta sotto `GET /public/search`, con proiezione e limiti più piccoli per l’autocomplete. La dashboard oggi lancia due richieste Beebot in parallelo per suggerimento. |
| 4 | Ridurre le letture delle librerie | Implementati il caricamento su apertura del selettore, il riepilogo di gilda in una chiamata, le statistiche del singolo suono su dettaglio e la proiezione dei tag popolari. |
| 5 | Consolidare i percorsi di gestione delle soundboard | Le cinque mutazioni con prefisso gilda hanno un equivalente sotto `/soundboards/{id}`. Le due forme passano oggi per controlli diversi in `editAccess`. Prima del ritiro verificare owner, amministratore, membro, gilda errata e accesso dopo uscita dalla gilda. Conservare la lettura di gilda che serve ai membri. |

### Percorsi sostituibili e convenzioni

- L'alias `GET /servers` è stato rimosso. `GET /guilds` continua a servire i client che chiedono solo la directory. La dashboard legge la directory dentro `GET /me`.
- `GET /sounds`, `GET /tags` e `GET /sounds/{soundId}` sono già segnati come sostituibili nella [guida del catalogo](../api/beebot-sound-community.md). I sostituti sono `/public/sounds`, `/public/sounds/suggestions` e `/public/sounds/{soundId}`. Il vecchio percorso usa la membership di gilda e modelli operativi diversi. La visibilità del catalogo pubblico dei suoni usa pubblico oppure autore. Confrontare anche ordinamento crescente, limite/cursore, campi di gestione e tag prima del ritiro.
- I due PATCH dei voti e i tre GET audio precedenti sono **cinque candidati**. La rimozione porterebbe Beebot da 64 a 59 operazioni. Ritirare anche le cinque mutazioni duplicate delle board porterebbe a 54, dopo migrazione dei consumatori e verifica dei permessi.
- Uniformare in seguito `/sounds/me` e `/sounds/me/analytics` a `/me/sounds` e `/me/sounds/analytics`, come `/me/soundboards`. È una migrazione di contratto. Vale la pena farla insieme al passaggio di client e documentazione.
- Il catalogo restituisce ancora `mine: []` a ogni ricerca. `items` contiene già i match privati dell’autore nello stesso ordinamento. Segnare `mine` come campo da ritirare dopo aver verificato i parser.
- Mantenere le rotte di commenti e voti sotto suono e board. I controller delegano già tramite `SoundTarget`. Radici, risposte con `parentId` e paginazione condividono un GET per tipo di risorsa. Il dettaglio deve poter essere aperto senza caricare i commenti. Un play dalla board usa già `POST /public/sounds/{id}/plays` con `soundboardId`.
- Mantenere i DELETE dei settings per risorsa e le transazioni indipendenti nel POST dei settings. Twitch lookup è una lettura esterna distinta. Foto e thumbnail sono operazioni multipart su risorse proprie. Warning e action restano fuori dal riordino.
- Consolidare la documentazione in una specifica Beebot con tag per area. L’[OpenAPI dei suoni](../api/beebot-sounds.openapi.json) coincide con 53 operazioni del controller, comprese `/me` e `/me/settings`. Le altre 11 sono le otto operazioni di gilda e le tre di login/sessione. Sono descritte nelle guide [guild](../api/beebot-guilds.md) e [auth](../api/beebot-auth.md). Usare le raccolte client come consumatori della specifica.

## Lavoro evitabile

La dashboard ora chiede `overview=true` per la libreria di gilda. Riepilogo e classifica usano la stessa lettura delle riproduzioni. My sounds chiede le statistiche solo per il suono aperto. Il selettore New soundboard carica la libreria audio quando viene aperto. L’header legge solo i tag con `tagsOnly=true` sul percorso Explore esistente.

| Flusso | Lavoro osservato | Intervento |
| --- | --- | --- |
| Autocomplete | La dashboard esegue suggestions e una pagina di board. Il backend dei suggerimenti costruisce card complete. Con risultati presenti esegue selezione suoni, tag associati, arricchimento e tag suggeriti. | Un payload leggero per suoni, board e tag. Se la UI richiede play e cover, includere URL e durata. Evitare calcoli di listener, voto e tag per singolo risultato quando la UI non li usa. Misurare separatamente risparmio HTTP e risparmio SQL. |
| Explore | In un caso con risultati e un solo batch, cinque letture SQL. Trending, ultimi caricamenti, tag delle card, arricchimento e tag popolari. L’header chiama lo stesso Explore per ricavarne solo i tag popolari. | Riutilizzare la componente dei tag sul server, con cache breve e indipendente dalla sessione per i tag pubblici. Misurare il costo del raggruppamento di `sound_play` e decidere se mantenere trending. Togliere trending elimina una delle cinque letture standard. |
| Ricerca All | Il sito apre due liste con due richieste in parallelo e cursori indipendenti. I filtri dei suoni e delle board hanno semantiche diverse. | Conservare le liste e i due cursori. Se il costo HTTP della prima pagina è rilevante, valutare una risposta con due sezioni opzionali e lo stesso codice di ricerca sotto. `q=…` contiene una ricerca per sottostringa. Misurare righe lette e ordinamento su volumi realistici prima di cambiare la strategia SQL. |
| Libreria suoni | I GET di libreria caricano tutti i risultati visibili, con tag, arricchimento e riepilogo. La dashboard avvia anche i loader di analytics di gilda e profilo, che selezionano gli stessi suoni e caricano le riproduzioni in modalità dettagliata. | Impiegare la stessa selezione per dati aggregati equivalenti. Per la prima vista usare un riepilogo e i top limitati. Per volumi grandi paginare la libreria con filtri server e tenere a parte il dettaglio. |
| Apertura My soundboards | Il layout avvia `useMySounds`. Nel layout la lista serve al selettore di suoni nel modulo New soundboard. | Caricare i candidati quando si apre il modulo. Misurare senza far pesare l’intera libreria audio sulla visita della lista board. |
| Dettaglio board gestionale | Il dettaglio può leggere la board due volte, per permessi e proiezione, e recupera gli ID ordinati separatamente dai suoni filtrati. | Riutilizzare la riga già verificata nello stesso contesto di lettura. Gli ID completi servono all’editor anche quando alcuni suoni sono filtrati. Qualsiasi accorpamento delle query deve conservare entrambe le informazioni. |
| Cache HTTP | `AuthFilter` imposta `no-store` su Beebot. Sotto `/public` la sessione può cambiare visibilità, voti e ownership. L’applicazione usa comunque la propria API key. | Valutare una cache di risposte o proiezioni anonime solo dopo aver verificato come il proxy protegge la chiave e distingue la sessione, comprese le personal key. Le risposte personalizzate e le mutazioni restano private. Per LoL misurare prima le hit delle partite complete e tenere separate le risposte in preparazione. |

Fonti principali per le prestazioni e i contratti interni:
[SoundService](../../src/main/java/com/safjnest/sound/SoundService.java),
[SoundDB](../../src/main/java/com/safjnest/database/sql/database/SoundDB.java),
[SoundCache](../../src/main/java/com/safjnest/bot/cache/managers/SoundCache.java),
[SoundAnalytics](../../src/main/java/com/safjnest/sound/SoundAnalytics.java),
[SoundboardController](../../src/main/java/com/safjnest/spring/controller/beebot/SoundboardController.java),
[AuthFilter](../../src/main/java/com/safjnest/spring/config/AuthFilter.java).

## Proposta di ricerca unica

`GET /api/beebot/public/search` accetta `q` facoltativa. Omettere `types` richiede tutte le sezioni. Per una selezione esplicita, usare un elenco come `types=sounds,tags`, `types=sounds,boards` o `types=boards,tags`. Il server esegue solo il lavoro necessario alle sezioni richieste, compresi i filtri sui suoni che servono a trovare le board.

| Esempio | Risposta |
| --- | --- |
| `GET /public/search` | Top suoni e board per ascolti, tag più usati sui suoni visibili. |
| `GET /public/search?q=brizz` | Suoni per nome o tag, board per nome o contenuto, tag per nome. |
| `GET /public/search?types=boards&q=brizz` | Solo la sezione board, con match per nome o per contenuto. |
| `GET /public/search?types=boards&sort=mostLiked` | Top board per like. |
| `GET /public/search?types=boards&q=brizz&cursor=…` | Pagina successiva delle sole board con gli stessi filtri. |
| `GET /public/search?q=bri&mode=suggest` | Anteprima con proiezioni leggere e limiti bassi per suoni, board e tag. |

Ogni sezione richiesta ha `items` e `nextCursor`. La proposta usa 20 suoni, 10 board e 10 tag come limiti di ricerca di default. Omettendo `limit` si ottengono questi valori. In autocomplete si usano limiti più bassi e un prefisso favorito. Le sezioni escluse si omettono dalla risposta. La continuazione usa un solo `types` con `cursor`, così la pagina delle board si può scorrere senza rieseguire le altre due. Ordinare per rilevanza con `q` e per popolarità in sua assenza. Usare l’ID come ultimo criterio stabile. Limitare l’ordinamento per dislike ai suoni.

### Board trovate tramite i suoni

Per ogni board visibile, cercare un match diretto sul nome oppure una corrispondenza tramite `soundboard_sounds`. Per la seconda, un `EXISTS` collega la board a un suono che soddisfa la stessa condizione di ricerca per nome o tag. Il ramo valuta i suoni prima di qualsiasi paginazione. Una board con il ventunesimo suono corrispondente può quindi comparire già nella prima pagina delle board, e una board con più corrispondenze compare una volta. La relazione funziona anche con `types=boards`.

In rilevanza, ordinare prima i nomi esatti, poi prefissi e sottostringhe del nome, infine i risultati dovuti ai suoni contenuti. Un ordinamento esplicito per popolarità o like resta prioritario. Un eventuale filtro esatto per tag si applica alle board tramite i suoni con quel tag. Con `q` e filtri vuoti, la query delle board salta il controllo della relazione.

Applicare la visibilità sia alle board sia ai suoni usati come prova della corrispondenza. Per le board pubbliche usare solo i suoni che si vedono nel loro dettaglio pubblico. Per una board privata cercabile dall’autore, usare il medesimo perimetro del dettaglio accessibile al chiamante. Le corrispondenze basate su oggetti invisibili possono rivelarne l’esistenza anche se la risposta contiene solo la board.

### Passaggio dei client

Riutilizzare le letture attuali da `SoundService`, `SoundDB` e `BotDB`. Confrontare su MariaDB i piani board-first tramite `EXISTS` e sound-first tramite gli indici sulle due chiavi di `soundboard_sounds`, con query comuni e rare. Misurare l’effetto del match per sottostringa e del lavoro evitato quando `types` è parziale. L’endpoint unico riduce le chiamate HTTP, mentre il numero di SELECT dipende dalle sezioni richieste e dalla proiezione.

Migrare la dashboard su ricerca e autocomplete prima di ritirare `GET /public/sounds`, `GET /public/soundboards` e `GET /public/sounds/suggestions`. Prima del passaggio verificare anche i flussi di selezione dei tag, le schede creator, i client esterni e la compatibilità delle vecchie ricerche autenticate. Explore può restare la composizione della home con trending e ultimi caricamenti. Il caricamento dei tag dell’header passerà a `/public/search?types=tags`.

## LoL

La [revisione LoL](lol-refactor-review.md) contiene i flussi e i vincoli delle singole elaborazioni.

- Iodid usa attivamente la lookup del profilo sia per PUUID sia per Riot ID. Le vie convergono sullo stesso dominio, con ingressi diversi per identità. Mantenerle. La variante live per Riot ID è una candidata a verifica d’uso. Iodid apre il live per PUUID, mentre la variante per nome è documentata.
- `matches` restituisce già le facet che usa Iodid. `facets` ha una capacità ulteriore: filtri ripetibili per conteggi disgiuntivi. Entrambe usano `FacetsService`. Verificare il riuso delle elaborazioni equivalenti e l’uso separato prima di ritirare la rotta. Mantenere il contratto di [match e facet](../api/summoner/match-facets.md).
- `refresh` aggiorna il profilo e accoda la sincronizzazione ordinaria dello storico. `history` accoda la variante forzata e risponde 202. Il lavoro va deduplicato dove girano i job, mantenendo la [semantica del refresh](../api/summoner/profile-refresh.md) e gli stati dell’[ADR-0007](adr/0007-unified-api-result-and-parameters.md).
- `/records/HIGHEST_MASTERY/{champion}` può diventare un filtro opzionale `champion` di `/records/{metric}`, validato soltanto per la metrica compatibile. Iodid ha già un path dedicato, da migrare esplicitamente. Conservare la panoramica globale e i record del singolo profilo, che hanno scopo diverso. [Contratto record](../api/records.md)
- Activity, matchups, rank history e match detail hanno costi, filtri e tempi di disponibilità distinti. La leaderboard, la distribuzione dei rank e le regioni principali servono viste diverse ed esistono nel client attivo. Tenere separati anche i feed indexables e l’export di training. Individuare i consumer dell’export prima di valutarne lo spostamento.
- Il dettaglio match è un buon candidato per cache lunga sul 200 completo. Una risposta pending o un arricchimento successivo richiedono una politica distinta. I profili cambiano dopo refresh e richiedono revalidazione più frequente. Questo audit lascia Mongo e la struttura dei job nei rispettivi confini.

## Inventario completo

I percorsi sono relativi al prefisso del gruppo. Per parametri, codici di risposta ed esempi valgono i contratti collegati.

### Beebot, `/api/beebot` (64)

**Autenticazione e profilo (5)** · [Auth](../api/beebot-auth.md)

```text
POST /auth/discord/authorize
POST /auth/session
POST /auth/logout
GET /me
POST /me/settings
```

**Gilde, settings e alert (8)** · [Guild](../api/beebot-guilds.md)

```text
GET /guilds
GET /guilds/{guildId}
POST /guilds/{guildId}/settings
DELETE /guilds/{guildId}/alerts/{type}
DELETE /guilds/{guildId}/alerts/reward/{level}
DELETE /guilds/{guildId}/alerts/twitch/{streamerId}
DELETE /guilds/{guildId}/settings/channels/{channelId}
GET /guilds/{guildId}/alerts/twitch/lookup
```

**Catalogo, consultazione e riproduzione (10)** · [Community](../api/beebot-sound-community.md) · [Soundboard](../api/beebot-soundboards.md)

```text
GET /public/sounds/explore
GET /public/sounds
GET /public/soundboards
GET /public/sounds/suggestions
GET /public/sounds/{soundId}
GET /public/soundboards/{id}
GET /public/sounds/{soundId}/comments
GET /public/soundboards/{boardId}/comments
GET /public/users/{userId}
POST /public/sounds/{soundId}/plays
```

**Libreria, upload, modifica e analytics suoni (13)** · [Suoni](../api/beebot-sounds.md)

```text
GET /guilds/{guildId}/sounds
POST /guilds/{guildId}/sounds
GET /sounds/me
GET /sounds/{soundId}
GET /sounds
GET /tags
GET /guilds/{guildId}/sounds/analytics
GET /sounds/me/analytics
GET /sounds/{soundId}/analytics
PATCH /sounds/{soundId}
DELETE /sounds/{soundId}
PUT /sounds/{soundId}/image
DELETE /sounds/{soundId}/image
```

**Commenti e voti (12)** · [Community](../api/beebot-sound-community.md)

```text
POST /sounds/{soundId}/comments
POST /soundboards/{boardId}/comments
PATCH /sounds/{soundId}/comments/{commentId}
PATCH /soundboards/{boardId}/comments/{commentId}
DELETE /sounds/{soundId}/comments/{commentId}
DELETE /soundboards/{boardId}/comments/{commentId}
PUT /sounds/{soundId}/vote
PUT /soundboards/{boardId}/vote
PUT /sounds/{soundId}/comments/{commentId}/vote
PUT /soundboards/{boardId}/comments/{commentId}/vote
PATCH /sounds/{soundId}/like
PATCH /sounds/{soundId}/dislike
```

**Gestione soundboard (16)** · [Soundboard](../api/beebot-soundboards.md)

```text
GET /guilds/{guildId}/soundboards
GET /me/soundboards
GET /guilds/{guildId}/soundboards/{id}
GET /soundboards/{id}
POST /guilds/{guildId}/soundboards
POST /me/soundboards
PATCH /guilds/{guildId}/soundboards/{id}
PATCH /soundboards/{id}
PUT /guilds/{guildId}/soundboards/{id}/sounds/{soundId}
PUT /soundboards/{id}/sounds/{soundId}
DELETE /guilds/{guildId}/soundboards/{id}/sounds/{soundId}
DELETE /soundboards/{id}/sounds/{soundId}
PUT /guilds/{guildId}/soundboards/{id}/thumbnail
PUT /soundboards/{id}/thumbnail
DELETE /guilds/{guildId}/soundboards/{id}
DELETE /soundboards/{id}
```

### LoL, `/api/lol` (25)

[Contratto LoL](../api/lol-api.md)

```text
GET /search
GET /profile/indexables
GET /{shard}/profile/{puuid}
GET /{shard}/profile-by-name/{gameName}/{tagLine}
POST /{shard}/profile/{puuid}/refresh
POST /{shard}/profile/{puuid}/history
GET /{shard}/profile/{puuid}/matches
GET /{shard}/profile/{puuid}/facets
GET /{shard}/profile/{puuid}/rank-history
GET /{shard}/profile/{puuid}/activity
GET /{shard}/profile/{puuid}/matchups
GET /{shard}/profile/{puuid}/records
GET /{shard}/livegame/{puuid}
GET /{shard}/livegame-by-name/{gameName}/{tagLine}
GET /{shard}/match/{gameId}
GET /champion/indexables
GET /champions/tier-list
GET /champion/{champion}
GET /leaderboard
GET /leaderboard/rank-distribution
GET /leaderboard/top-regions
GET /records
GET /records/{metric}
GET /records/HIGHEST_MASTERY/{champion}
GET /ai/training
```

### Stato (1)

[Contratto status](../api/status.md)

```text
GET /api/status
```
