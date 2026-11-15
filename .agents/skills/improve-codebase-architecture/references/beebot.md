# Beebot: orientamento dell'analisi

Leggere i documenti proprietari attuali. Queste indicazioni sono punti di partenza, non una fotografia permanente dell'implementazione.

## Perimetro concordato il 15 novembre 2026

Per la revisione corrente dare precedenza a coerenza bot/API per guild, alert, suoni e user, cache locali con bulk, errori condivisi, Spring/autenticazione e riuso di parsing e trasporto HTTP con LoL. Il piano completo concordato, incluso l'albero finale e ogni destinazione, è `docs/architecture/repository-review.md`. Leggerlo prima di riprendere il lavoro; questa skill ne richiama i vincoli senza mantenerne una copia alternativa.

- Struttura scelta: `com.safjnest.beebot` per il bot e i suoi oggetti/cache, `com.safjnest.lol` per il dominio LoL e `com.safjnest.spring` per HTTP e autenticazione API. L'ultima decisione mette `ApiKey`, `AuthFilter`, `DiscordAuthService` e `DiscordGuildCache` in `spring.auth`; le precedenti destinazioni `auth` alla radice, `beebot.auth` e `spring.security` sono superate.
- Le cache operative vanno in `beebot.cache`, la base `CacheAdapter` e `GenericCache` in `cache` alla radice. Client Discord condiviso in `discord.DiscordApi`, errore applicativo in `exception.BeebotException`; `AuthDB` resta in SQL.
- Il proprietario esegue i movimenti dei file esistenti; l'assistente crea quelli nuovi nei package finali e prosegue con gli assorbimenti. Ricontrollare il working tree prima di procedere e preservare gli spostamenti dell'utente. I file da assorbire restano temporaneamente disponibili fino alla migrazione delle funzionalità e dei chiamanti.
- La collocazione concordata di auth in Spring estende il suo perimetro documentato. Durante l'implementazione sincronizzare le regole e la documentazione proprietaria pertinenti; non trattare il piano come prova di una modifica al codice o di un ADR già emendato.

- `MongoDB` resta il repository LoL esistente: nessuna scomposizione, ridenominazione, ottimizzazione delle sue query o spostamento dei suoi tipi è parte di questa revisione.
- La struttura SQL e `QueryRecord`/`QueryRecordParser` restano al loro posto. Sono ammesse proposte circoscritte per allineare le query nuove di auth/sessioni/API e assorbire le operazioni necessarie a eliminare i percorsi paralleli guild/sound. Non riaprire un refactoring generale di `AbstractDB`/`BotDB`.
- Conservare `App.java`, nomi e metodi storici come `distruzione_demoniaca`. Ignorare BotStatus, metriche e loro collocazione. Comandi e handler cambiano package secondo il piano mantenendo struttura e presentazione; gli adeguamenti di chiamata eliminano solo gli accessi che aggirano il proprietario, senza un refactoring autonomo della loro architettura.
- `Chronos` segue il cambio di package pianificato, conservando nome e comportamento. La sua rimozione è secondaria. `Future`/`CompletableFuture` non sostituiscono da soli la pianificazione periodica.
- Preservare gli oggetti operativi: `GuildData`, `AlertData` e `Sound` mantengono operazioni che salvano sul DB. La cache possiede caricamento/identità; gli oggetti possiedono modifiche e salvataggi. Evitare la catena cache → nuovo service → nuovo repository → DB.
- Eliminare come obiettivo i percorsi indipendenti di `GuildService`/`GuildSettingsDB` e delle mutazioni API sound. Trasferire le funzionalità ai proprietari esistenti preservando transazioni, controlli e formato delle risposte; non limitarsi a spostare centinaia di righe nei controller.
- Usare la stessa infrastruttura di cache locale per i diversi tipi, con istanze e namespace distinti. La cache di permessi/sessioni conserva le sue regole di revoca, disponibilità e fencing; non diventa una semplice cache di oggetti del bot.
- Bulk significa riuso degli hit, recupero dei soli ID mancanti e coordinamento con i caricamenti singoli. Se 10 di 20 user sono presenti, i fetch riguardano solo gli altri 10. Controllare anche i costruttori: una query aggregata seguita da caricamenti per oggetto non elimina il problema.
- L'XP è di `MemberData`, con identità guild+user; alias/greet sono di `UserData` globale. Le associazioni LoL continuano a usare i modelli e il percorso Mongo esistenti, senza promettere bulk che richiedano modifiche al repository escluso.
- Nei numeri distinguere parsing rigoroso, fallback, null, stringhe, soli `Number`, overflow e valori frazionari. Nell'HTTP condividere costruzione/invio; mantenere timeout e classificazione degli errori specifici di Discord e Data Dragon. Non accorpare per somiglianza di sintassi.

## Fonti e flussi da confrontare

I nomi e le posizioni sotto possono riferirsi alla baseline precedente ai movimenti: risolvere i simboli nel checkout corrente con CodeGraph e usare la tabella del piano per le destinazioni. Per esempio `core.Bot` diventa `beebot.core.Bot`, mentre le credenziali oggi in `auth` vanno in `spring.auth`.

| Concetto | Fonti e ingressi iniziali |
|---|---|
| Regole e decisioni | `AGENTS.md`, `docs/architecture/README.md`, ADR pertinenti; `docs/HANDBOOK.md` §§5–7 |
| Stato delle revisioni | `docs/architecture/repository-review.md`, se esiste; gli altri documenti di analisi possono descrivere proposte non implementate |
| Avvio e arresto | `App`, `core.Bot`, `spring.SpringServer`, timer, client ed executor effettivamente registrati |
| Identità e autorizzazione | `auth`, `guild.DiscordGuildService`, `sound.SoundAccess`, `utils.PermissionHandler`, filtro HTTP e comandi |
| Impostazioni e alert | `GuildCache`, `GuildData`, `AlertData` e sottotipi, `GuildService`, `GuildSettingsDB`, comandi settings ed eventi; `docs/architecture/guild-sound-core-analysis.md`, `docs/api/beebot-guilds.md` |
| Audio | `model.sound.Sound`, `SoundCache`, `core.audio`, package `sound`, `BotDB`, `SoundDB`, upload/download/greet/soundboard ed eventi; `docs/api/beebot-sounds.md`, `docs/sound-history.md` |
| LoL | `LeagueHandler`, `lol.message`, `lol.service`, modelli canonici, `nosql.MongoDB`, `redis`, tracker e scheduler; ADR correnti, `docs/mongo`, `docs/arena-build`, API |
| Meccanismi condivisi | `QueryRecord`, `QueryRecordParser`, `JsonCodec`, `HttpUtils`, `CryptoUtils`, cache, metriche e scheduler con i loro consumatori |
| Altre integrazioni | Spotify, Twitch, TTS, chat, configurazioni e schemi SQL presenti; censire il resto prima di escluderlo |

## Distinzioni da mantenere

- Comando Discord e controller HTTP sono ingressi diversi allo stesso comportamento quando il caso d'uso è lo stesso. Verificare il servizio o oggetto operativo comune prima di creare un percorso parallelo per la dashboard.
- La lista delle guild amministrabili non dimostra la membership ordinaria necessaria ai suoni. Il lookup JDA e la gestione degli errori possono essere condivisibili; permessi, visibilità, revoca, TTL e identità del chiamante devono restare espliciti.
- Un tipo usato da Mongo ma collocato in `sql` può essere una rappresentazione già condivisa. Esaminare il codice prima di dedurre accessi MariaDB o introdurre un altro modello. Nella revisione corrente la sua collocazione è esplicitamente esclusa dagli interventi.
- JSON HTTP e JSON di persistenza possono richiedere visibilità dei campi e mixin diversi. Riutilizzare configurazione compatibile senza cancellare queste differenze.
- Coda audio, timer periodico e job con deduplicazione/progresso hanno semantiche differenti. Non unificarli per il nome. Per cambiare il job scheduler LoL leggere ADR-0014 e segnalare eventuali conflitti.
- I consumatori trasversali restano rilevanti anche nelle aree escluse, ma non riaprire per questo BotStatus o i modelli di metriche nel piano corrente.
- File audio permanenti, metadati Sound, copie temporanee sulla VPS e tracce in riproduzione hanno cicli di vita diversi. Per R2 ricostruire upload, risoluzione locale, download concorrenti, playback, cancellazione e scadenza prima di proporre il confine dello storage.
- Una classe per comando è un'organizzazione valida. Esaminare deleghe e logica duplicata senza introdurre una gerarchia astratta o ridisegnare i comandi per uniformità.

## Vincoli di implementazione

Conservare nomi canonici, modelli, responsabilità, API, ordini dei campi e presentazione secondo `AGENTS.md` e gli ADR. Riusare le utility esistenti; non introdurre framework DI o rappresentazioni parallele. LoL conserva Mongo come persistenza e Redis come dato ricostruibile.

Un problema di responsabilità può essere documentato senza cambiare un proprietario approvato. Una proposta che richiede un nuovo ADR resta una proposta fino alla decisione pertinente.

Nelle fasi di sola indagine registrare che API, presentazione e comportamento operativo non sono cambiati. Nelle fasi di implementazione aggiornare la documentazione proprietaria, senza trasformare un rapporto di audit in un ADR accettato.
