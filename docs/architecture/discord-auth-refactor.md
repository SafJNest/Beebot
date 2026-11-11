# Refactor autenticazione Discord — appunti di lavoro

Stato: implementato. Questo documento conserva le decisioni del refactor; i contratti HTTP correnti sono descritti in [autenticazione](../api/beebot-auth.md) e [impostazioni guild](../api/beebot-guilds.md).

## Obiettivo

`DiscordAuthService` deve contenere soltanto l'orchestrazione dell'autenticazione: avvio OAuth, scambio del codice, creazione e verifica della sessione, logout. Spostare accesso HTTP a Discord, parsing delle risposte, permessi, URL CDN, chiavi, hash e cifratura nei componenti che ne sono proprietari. Evitare classi di servizio che aggiungono solo un passaggio senza una responsabilità distinta.

Tutte le API key devono seguire lo stesso flusso esterno, indipendentemente dal tipo: **fetch → controllo API key/sessione → metodo richiesto**. L'autenticazione può risolvere identità e credenziali con meccanismi diversi, ma deve produrre un contesto canonico; il metodo non deve cambiare percorso o semantica in base a `personal`, `generic` o sessione. Lavorare a layer con responsabilità nette: trasporto/fetch, autenticazione e autorizzazione, metodo applicativo.

## Interventi richiesti

1. **Modello `ApiKey`.** Creare una classe con `type`, `value` e operazioni appropriate come `toHash`, costruzione/ricerca a partire da un hash e `toUserId` per le chiavi personali. Definire chiaramente il confine tra chiave in chiaro e record persistito. SHA-256 è unidirezionale: `fromHash` non può ricostruire `value`; deve significare lookup o costruzione di un oggetto che contiene solo l'hash, senza fingere di recuperare il segreto. Valutare enum per `type`, evitando stringhe sparse (`generic`, `personal`).
2. **`DiscordApi`.** Creare una classe responsabile delle chiamate HTTP a Discord, per esempio `DiscordApi.me(values)`, scambio del codice OAuth e elenco dei server. Deve contenere richiesta, risposta, parsing, paginazione, gestione degli errori Discord e URL di avatar/icona. L'auth service usa risultati tipizzati.
3. **Permessi.** Eliminare `DiscordAuthService.hasAdministrator(String)`. Riutilizzare `PermissionHandler`, aggiungendo lì il supporto ai bit dei permessi restituiti dall'API Discord. Verificare il tipo e il valore del bit con JDA prima di sostituire la logica attuale. Il metodo esistente `PermissionHandler.hasPermission(Member, Permission)` opera su un `Member`, quindi non copre direttamente la stringa numerica dell'API.
4. **Cifratura.** Spostare `encrypt`, `decrypt` e `parseEncryptionKey` fuori da `DiscordAuthService`. Prima controllare le utility esistenti; se manca una soluzione adatta, creare una classe di cifratura in `utils`, con algoritmo AES-GCM e formato persistito compatibili con le sessioni già salvate. Gli errori HTTP restano al confine auth, non nella utility crittografica.
5. **Hash e token.** Togliere `sha256` e la generazione di token casuali dall'auth service. Riutilizzare una utility compatibile, se esiste, o crearne una dedicata. Conservare la codifica SHA-256 Base64 URL senza padding usata da API key, state, sessioni e cache: un hash esadecimale non è intercambiabile.
6. **Servizi superflui.** Riesaminare `PersonalGuildService` e `AuthKeyService`; eliminare o assorbire quelli che non giustificano una responsabilità autonoma. Conservare però i comportamenti necessari: verifica aggiornata dell'appartenenza e dei permessi per le chiavi personali; generazione e revoca delle chiavi generiche e personali. Evitare di spostare semplicemente la complessità in `DiscordAuthService`.
7. **Flusso API key uniforme.** Rimuovere le diramazioni in cui una richiesta fa un percorso diverso perché la chiave è personale o perché arriva una sessione OAuth. Ogni richiesta passa dagli stessi layer e raggiunge lo stesso metodo applicativo con un'identità/contesto autenticato canonico. Le differenze nella risoluzione o nella validità delle credenziali si chiudono nel layer auth. L'autorizzazione alla singola guild resta un controllo mirato sull'ID richiesto per tutti i tipi di credenziale.
8. **`GET /beebot/me`.** Aggiungere il nuovo endpoint che restituisce `{ "user": ..., "servers": [...] }`, autenticato con `X-API-Key` e `Authorization: Bearer <session token>`. `user` mantiene i campi profilo correnti (`id`, `username`, `globalName`, `avatarUrl`); `servers` mantiene criteri e forma correnti (owner oppure permessi Administrator/Manage Server).
9. **Lista guild.** La funzionalità dell'attuale `/servers` resta: esporla come `GET /beebot/guilds` generico. `/me` restituisce profilo e lista; `/guilds` restituisce la lista da sola. Entrambi devono leggere la stessa cache per utente. La dashboard passa a `/me` per il profilo e usa `/guilds` quando serve solo l'elenco. Non eliminare il route generico `/guilds` dopo l'aggiornamento dashboard.
10. **Accesso alla singola guild.** Il contratto di `GET /beebot/guilds/{id}` e `POST /beebot/guilds/{id}/settings` resta invariato. `requireAdministrator` controlla l'ID richiesto contro la lista in cache, senza nuova chiamata Discord né scansione di tutti i server ad ogni request. La lista cache può essere popolata da `/me` o `/guilds` e condivisa solo per lo stesso utente.
11. **Cache guild Discord per utente.** Cache della lista ricevuta da `GET /users/@me/guilds`, filtrata/arricchita con `id`, `name`, `iconUrl`, `owner` e permessi. Chiave per Discord user ID o sessione, mai condivisa tra utenti; TTL 60 secondi. Redis se disponibile, fallback in memoria, secondo l'approccio della cache sessioni. Single-flight per utente al cache miss: richieste concorrenti (per esempio `/me` e `/guilds/{id}`) attendono una sola chiamata Discord. Non cacheare errori. Invalidare al logout e quando il token Discord viene rinnovato o cambia.
12. **Errori Discord distinti.** Body `{ "code": "...", "message": "..." }`. Discord 429 → HTTP 503 `discord_rate_limited`, `retryAfter` in secondi nel body e header `Retry-After`; Discord 5xx/rete → HTTP 503 `discord_unavailable`; Discord 401/403 → HTTP 401 `discord_token_expired`; storage sessione indisponibile → HTTP 503 `auth_unavailable`; impostazioni non caricabili su `/guilds/{id}` → HTTP 503 `settings_unavailable`. Per risposte Discord non 2xx loggare a livello warn status, `X-RateLimit-Bucket`, `X-RateLimit-Remaining` e `retry_after`; non registrare token.

Test richiesti: `/me` restituisce user e servers con una chiamata Discord; `/me` e `/guilds/{id}` concorrenti a cache vuota causano una sola chiamata; entro il TTL `/me` e `/guilds/{id}` non chiamano Discord; 429 dà `discord_rate_limited` con `Retry-After` e non viene cacheato; 401 dà `discord_token_expired`; `/guilds` e `/me` restituiscono la stessa lista.

Fuori scope: nessun campo oltre `user` e `servers` in `/me`; nessun cambio ai contratti di `/guilds/{id}` e `POST /guilds/{id}/settings`.

## Implementazione e responsabilità

| Responsabilità | Proprietario |
| --- | --- |
| Identità, tipo, valore/hash e creazione del modello chiave | `auth.ApiKey` |
| Persistenza, emissione e revoca delle chiavi | `sql.database.AuthDB` |
| OAuth, sessioni e risoluzione delle credenziali nel contesto canonico | `auth.DiscordAuthService` |
| Record Discord | `DiscordApi.AuthUser`, `Server`, `DiscordToken` |
| Record auth e richieste/risposte HTTP | `DiscordAuthService` e `DiscordAuthController`, annidati nei proprietari |
| HTTP Discord, parsing, URL CDN e accesso JDA alle guild personali | `auth.DiscordApi` |
| Lista autorizzata e controllo della guild richiesta | `guild.DiscordGuildService` |
| Cache Redis/memoria, single-flight e invalidazione | `guild.DiscordGuildCache` |
| Interpretazione permessi numerici | `utils.PermissionHandler` |
| Token casuali, hash e AES-GCM | `utils.CryptoUtils` |
| Errori HTTP Beebot | `AuthExceptionHandler.AuthError` e handler/filter esistenti |

`AuthKeyService`, `PersonalGuildService` e `GuildPermissionService` sono stati assorbiti nei proprietari sopra e rimossi. Eliminata la rappresentazione duplicata `AuthDB.Client`. I record di sola rappresentazione sono annidati nelle classi proprietarie: nessun file dedicato al solo record. Il wrapper `Servers` è eliminato: client, service, cache e controller usano direttamente `List<DiscordApi.Server>`. `AuthDB.Session` resta la rappresentazione interna dello storage SQL, distinta dal DTO che restituisce il token di sessione.

## Comportamento realizzato

- Tutte le richieste seguono autenticazione → contesto → autorizzazione → metodo. Il tipo di credenziale cambia soltanto la risoluzione auth e la sorgente del caricamento della lista; i controller e i controlli guild restano condivisi.
- `/me` restituisce solo `{user, servers}`; `/guilds` restituisce direttamente un array JSON di server; `/servers` è alias con la stessa forma. Owner, Administrator e Manage Server permettono GET e POST sulla guild.
- Cache isolata per utente/applicazione/sessione e versione del token, con TTL di 60 secondi. Redis coordina istanze diverse con lease e generazioni; la memoria coordina il singolo processo quando Redis non è disponibile. Errori e risultati invalidati non vengono pubblicati. I lease scaduti non possono pubblicare dati.
- Logout invalida la cache e impedisce la pubblicazione dei caricamenti già in corso. Sessioni nuove e token cambiati usano identità cache diverse. Non esiste un endpoint di rinnovo del token: una futura sostituzione in-place deve invalidare il vecchio contesto prima di salvarlo.
- OAuth carica fino a 200 guild in una richiesta. Le chiavi personali leggono prima i membri JDA già disponibili; una cache membri completa dimostra anche l'assenza. Solo una membership sconosciuta richiede una fetch. Mai scambiare cache incompleta per mancata appartenenza.
- Il profilo arriva dalla sessione/chiave personale. Nome, icona, canali e ruoli della singola guild arrivano dalla cache JDA. Le impostazioni welcome/leave e gli altri blocchi continuano a leggere/scrivere SQL; i salvataggi invalidano `GuildCache` come prima. Nessuna nuova richiesta Discord è introdotta da quei percorsi. La registrazione Twitch necessaria dopo il salvataggio resta invariata.
- Errori 429, token scaduto, rete/Discord, auth storage e settings storage rimangono distinti come documentato nelle API; niente credenziali nei log.

## Compatibilità e verifica

I formati persistiti SHA-256 Base64 URL e AES-256-GCM restano compatibili; nessuna migrazione aggiuntiva. Test con un vettore AES-GCM fisso verificano la lettura dei token precedenti. Revoca delle chiavi e delle sessioni verificata in SQL su ogni richiesta. I permessi guild possono riflettere uno stato vecchio al massimo di 60 secondi.

La chiave Redis delle guild usa la versione `v2` per separare il payload lista dal vecchio wrapper durante il cambio di versione.

Copertura: contratti `/me`/`/guilds`/`/servers`, richieste parallele, TTL, isolamento utenti/sessioni/token, Redis disponibile/indisponibile simulato, coordinamento di istanze, invalidazione locale/distribuita durante il caricamento, logout, membership JDA completa/incompleta, permessi, errori Discord e compatibilità crittografica. Il test Lua su Redis reale è abilitabile con `-Dbeebot.test.redis.port=<porta>`; nessuna connessione Discord reale nei test.

Questo lavoro riguarda soltanto le API Beebot e i servizi richiamati. API LoL, Mongo, shard, command/embeds e presentazione Discord non richiedono cambiamenti. Le modifiche di lavoro preesistenti sono preservate. Restano necessari il riavvio/deploy del backend e l'aggiornamento della dashboard al nuovo body di `/me`.

Verifica finale: 55 test superati, nessun errore; un test di integrazione Redis saltato perché il server locale non è disponibile. Comando: `mvn -o -Dtest="com.safjnest.auth.*Test,com.safjnest.guild.*Test,BeebotAuthFilterTest,GuildControllerTest,SpringPropertiesTest,TestAccessTest,AuthDBTest,CryptoUtilsTest" test`. Compilazione e `git diff --check` superati. Discord reale e Redis reale non sono stati chiamati per la validazione.
