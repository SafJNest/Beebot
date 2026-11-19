# API Beebot per il frontend

Contratto per il sito dei suoni e il dashboard Beebot. Include le rotte già
esposte dal backend e le chiamate che verranno sostituite quando il
dashboard passerà al nuovo contratto. Tutti gli esempi usano ID e credenziali fittizi.

- [URL e token](#base-url-accesso-e-stato)
- [Nuove rotte di catalogo, ricerca, commenti, voti e play](#nuovo-catalogo-e-interazioni)
- [Upload, librerie e rotte suoni attive](#suoni-già-esposti)
- [Analytics](#analytics)
- [Soundboard](#soundboard-di-gilda)
- [Login, profilo e greets](#login-profilo-e-greets)
- [Impostazioni e alert](#impostazioni-e-alert-di-gilda)

## Base URL, accesso e stato

I percorsi di questo documento sono relativi a
`https://api.safjnest.com/api/beebot`. Nel dashboard il server Qwik prende
questa base da `BEEBOT_API_URL` e la chiave applicativa da `BEEBOT_API_KEY`.

| Accesso | Chi può usare la pagina | Header verso Beebot |
| --- | --- | --- |
| **Pubblico** | Ospiti e utenti connessi | `X-API-Key: <applicationKey>`. Il server aggiunge `Authorization: Bearer <sessionToken>` quando c'è una sessione. |
| **Sessione** | Utente connesso, con i permessi richiesti dalla rotta | `X-API-Key: <applicationKey>` e `Authorization: Bearer <sessionToken>`. |
| **Login** | Utente prima della sessione | `X-API-Key: <applicationKey>`. |
| **CDN** | Chi ha l'URL dell'oggetto | Il browser usa l'URL così com'è. |

**Pubblico** indica che il login è facoltativo. Sulle rotte `/public`
il server Qwik custodisce e invia la chiave applicativa. Il
browser usa loader, azioni o route del frontend sul proprio dominio. Il
server legge il cookie HttpOnly `beebot_session` e inoltra il bearer quando
serve. Per `/public` usa una chiamata ad accesso facoltativo che può
raggiungere Beebot anche senza cookie. Le rotte con **Sessione** continuano
a usare il flusso autenticato esistente. Un bearer inviato ma scaduto o
invalido dà `401`, anche su `/public`.

I client di servizio possono usare una chiave personale con il solo
header `X-API-Key` sia sulle rotte pubbliche sia su quelle protette. La
chiave personale identifica l'utente anche sulle pubbliche. Il sito usa
la chiave applicativa.
Una chiave personale con bearer viene rifiutata. Le rotte **Login** accettano
la chiave applicativa.

- **Attivo** significa che la rotta esiste nel checkout backend. Il deploy
  effettivo dipende dall'ambiente indicato in `BEEBOT_API_URL`.
- **Da sostituire** significa che la rotta è attiva oggi e uscirà al passaggio
  del frontend alle nuove chiamate.

Gli ID Discord, dei suoni, delle soundboard, dei commenti e dei tag sono
stringhe decimali positive. Mantienili come stringhe anche in JSON. Fanno
eccezione gli ID interni numerici di alert e warning e i campi `level`.
Nelle mappe JSON anche le chiavi numeriche sono stringhe. Il greet globale
usa la chiave speciale `"0"`. Le date sono ISO 8601 UTC, salvo `expiresAt`
(epoch in millisecondi) e le date locali delle serie analytics (`YYYY-MM-DD`).

I parametri ammessi sono quelli elencati per ogni rotta. Le GET e DELETE di
questo contratto hanno body vuoto. Per JSON usa `Content-Type: application/json`.
Per `FormData` lascia al client la generazione del boundary. Le risposte
`204` sono vuote.

### Errori

`{ "code": "sound_not_found", "message": "Sound not found" }` è un esempio
del formato comune. In caso di rate limit di Discord può arrivare anche
`retryAfter` in secondi e il relativo header `Retry-After`.

| HTTP | Cosa gestire nel frontend |
| --- | --- |
| `400` | Input, body, cursore, ID o filtro non valido. Mostrare l'errore del campo o della richiesta. |
| `401` | Chiave o sessione mancante, scaduta o revocata. Richiedere il login sulle pagine protette. |
| `403` | Sessione valida, permessi insufficienti per l'operazione. |
| `404` | Risorsa assente o invisibile. Nel catalogo pubblico i privati altrui rientrano in questo caso. |
| `405`, `415` | Metodo o content type errato. |
| `409` | Conflitto, per esempio nome soundboard già usato o soundboard piena. |
| `413` | Upload oltre il limite del server o del proxy. |
| `500`, `502`, `503` | Errore interno, risposta upstream invalida o dipendenza indisponibile. |

Usa `code` per la logica. Esempi attivi sono `invalid_session`,
`guild_forbidden`, `guild_not_found`, `sound_not_found`,
`sound_forbidden`, `duplicate_sound_name` (`400`),
`duplicate_soundboard_name`, `soundboard_full`, `sound_storage_unavailable`
e `discord_rate_limited` (`503`). Commenti e immagini riusano il formato.
Oggi tutte le risposte Beebot, incluse quelle ospite, usano
`Cache-Control: no-store`. Una futura cache condivisa delle letture
`/public` riguarderà soltanto le richieste ospite.

## Nuovo catalogo e interazioni

Tutte le rotte di questa sezione sono **attive**.

### Letture pubbliche

| Metodo e percorso | Query | Risposta |
| --- | --- | --- |
| `GET /public/sounds/explore` | Vuota | `200 Explore` |
| `GET /public/sounds` | Filtri del catalogo sotto | `200 CatalogPage` |
| `GET /public/sounds/suggestions` | `q` obbligatorio | `200 Suggestions` |
| `GET /public/sounds/{soundId}` | Vuota | `200 SoundCard` |
| `GET /public/sounds/{soundId}/comments` | `parentId`, `sort`, `limit`, `cursor` | `200 CommentPage` |
| `GET /public/users/{userId}` | Vuota | `200 Creator` |

`Explore` dà fino a 10 elementi per sezione. `popularTags` usa il numero
di suoni pubblici per tag, `trending` i play negli ultimi 7 giorni e
`latest` gli upload più recenti. La lista del creator usa il catalogo con
`uploaderId={userId}`. I totali del creator riguardano i suoi suoni pubblici
e `uploadingSince` è la data del primo upload pubblico oppure `null`.
Un creator senza upload pubblici ha contatori a zero. Se il profilo
Discord manca in cache, il nome visualizzato può essere `Unknown`.

#### Filtri del catalogo

`GET /public/sounds` senza query è la prima pagina. Tutti i filtri sono
facoltativi e si combinano tra loro.

| Parametro | Tipo e valori | Default e comportamento |
| --- | --- | --- |
| `q` | Stringa, max 255 caratteri | Assente o vuota equivale a nessuna ricerca per nome. Match parziale, maiuscole ignorate. |
| `tag` | Nome esatto del tag, stringa non vuota, max 255 caratteri | Omettere per tutti i tag. Maiuscole ignorate. |
| `uploaderId` | ID Discord come stringa | Omettere per tutti i creator. |
| `minDurationMs` | Intero `>= 0` | Limite inferiore incluso. |
| `maxDurationMs` | Intero `>= 0` e `>= minDurationMs` se entrambi presenti | Limite superiore incluso. Con un filtro di durata, i suoni di durata ignota sono esclusi. |
| `sort` | `relevance`, `popular`, `newest`, `mostLiked`, `mostDisliked` | `popular` senza `q`, `relevance` con `q`. Ordine decrescente per metrica. |
| `limit` | Intero da 1 a 100 | 20. Omettere il valore di default. |
| `cursor` | Stringa restituita da `nextCursor` | Omettere per la prima pagina. Ripetere gli stessi filtri e sort quando si avanza. |

`relevance` ordina nome esatto, prefisso, sottostringa, poi riproduzioni.
`popular` ordina per play totali. Con sessione, solo la prima pagina
può contenere fino a 5 risultati in `mine`, filtrati con gli stessi criteri
e comprensivi dei propri privati. `items` contiene esclusivamente risultati
pubblici. Lo stesso suono pubblico può comparire in entrambe le liste.
Il cursore riguarda `items` e `null` indica l'ultima pagina. Per un nuovo
filtro riparti dalla prima pagina.

Esempi:

```text
GET /public/sounds
GET /public/sounds?tag=arcade
GET /public/sounds?q=air&minDurationMs=1000&maxDurationMs=5000
GET /public/sounds?uploaderId=456&sort=newest
GET /public/sounds?tag=arcade&cursor=<nextCursor>
```

Per `GET /public/sounds/suggestions?q=air`, `q` deve essere non vuota
dopo il trim e lunga al massimo 255 caratteri. La risposta ha fino a 5 suoni
(ordina prima i match per prefisso, poi per play) e fino a 3 tag che iniziano
con `q`. I suggerimenti con sessione includono anche i propri privati.
La chiamata ha un solo parametro e restituisce direttamente le due liste.

#### Commenti e thread

`GET /public/sounds/42/comments` dà 20 commenti principali. Se è presente
un pinned, usa il primo posto della prima pagina, una sola volta anche
con un ordinamento diverso. La risposta contiene `replyCount` per
aprire ogni thread quando serve.

| Parametro | Tipo e valori | Default e comportamento |
| --- | --- | --- |
| `parentId` | ID di un commento dello stesso suono | Assente per i commenti principali. Se presente, dà solo le risposte dirette a quel padre. Può essere l'ID di una risposta già annidata. |
| `sort` | `top`, `newest`, `oldest`, `mostDisliked` | `top` per like decrescenti. `mostDisliked` per dislike decrescenti. A parità si usa data e ID. |
| `limit` | Intero da 1 a 100 | 20 per singolo livello. |
| `cursor` | `nextCursor` della stessa lista | Assente per la prima pagina. Ripetere suono, `parentId` e sort. |

```text
GET /public/sounds/42/comments
GET /public/sounds/42/comments?sort=newest
GET /public/sounds/42/comments?parentId=7
GET /public/sounds/42/comments?parentId=7&cursor=<nextCursor>
```

Il dettaglio del suono e la pagina commenti si caricano separatamente.
`commentCount` include commenti e risposte attivi. Un commento eliminato
con risposte può apparire come segnaposto senza autore o testo. La sua
riga sparisce quando l'ultimo ramo di risposte viene rimosso. Con ordinamenti
a contatori, i risultati possono riordinarsi tra richieste.

#### Tipi del catalogo

```ts
type Id = string
type DateTime = string
type Cursor = string | null
type Vote = 'LIKE' | 'DISLIKE' | null
type PublicUser = { id: Id; name: string; avatarUrl: string | null }
type Tag = { id: Id; name: string }
type TagCount = Tag & { soundCount: number }
type Viewer = { ownedByMe: boolean; vote: Vote }

type SoundCard = {
  id: Id
  name: string
  public: boolean
  createdAt: DateTime | null
  durationMs: number | null
  audioUrl: string | null
  imageUrl: string | null
  uploader: PublicUser
  tags: Tag[]
  plays: number
  listeners: number
  likes: number
  dislikes: number
  commentCount: number
  viewer?: Viewer
}
type CatalogPage = { items: SoundCard[]; mine: SoundCard[]; nextCursor: Cursor }
type Explore = { popularTags: TagCount[]; trending: SoundCard[]; latest: SoundCard[] }
type Suggestions = { sounds: SoundCard[]; tags: TagCount[] }
type Creator = {
  user: PublicUser
  uploadingSince: DateTime | null
  soundCount: number
  plays: number
  likes: number
  dislikes: number
  commentCount: number
}
type SoundComment = {
  id: Id
  soundId: Id
  parentId: Id | null
  author: PublicUser | null
  text: string | null
  createdAt: DateTime
  editedAt: DateTime | null
  deleted: boolean
  pinned: boolean
  hearted: boolean
  likes: number
  replyCount: number
  uploader: boolean
  viewer?: Viewer
}
type CommentPage = { items: SoundComment[]; nextCursor: Cursor }
```

Gli ospiti ricevono il campo `viewer` omesso. Nel catalogo pubblico il
proprietario può leggere il proprio suono privato con sessione, mentre
gli altri ricevono `404`. `uploader` nei commenti indica che l'autore
del commento è l'uploader del suono. I dislike del commento si possono
usare come ordinamento e lo stato personale può essere `DISLIKE`.

`audioUrl` e `imageUrl` sono URL CDN completi, ad esempio
`https://cdn.example.com/sounds/42/42.mp3` e
`https://cdn.example.com/sounds/42/thumbnail-cover.png`. Usa il valore
restituito, così funzionano anche le chiavi precedenti. Se il link è già
noto, il CDN può servirlo direttamente a prescindere dalla visibilità della
scheda. Una copertina assente è `imageUrl: null`.

#### Esempio di scheda pubblica senza sessione

```json
{
  "id": "42",
  "name": "Airhorn",
  "public": true,
  "createdAt": "2026-11-01T10:00:00Z",
  "durationMs": 2088,
  "audioUrl": "https://cdn.example.com/sounds/42/42.mp3",
  "imageUrl": null,
  "uploader": {"id":"456","name":"Luna","avatarUrl":null},
  "tags": [{"id":"1","name":"meme"}],
  "plays": 18,
  "listeners": 7,
  "likes": 4,
  "dislikes": 1,
  "commentCount": 2
}
```

Con sessione la stessa scheda aggiunge per esempio
`{"viewer":{"ownedByMe":false,"vote":"LIKE"}}`.

### Scritture e play

| Metodo e percorso | Accesso | Body | Risposta |
| --- | --- | --- | --- |
| `POST /public/sounds/{soundId}/plays` | Pubblico | `{ "playbackId": "<UUID>" }` | `204` |
| `POST /sounds/{soundId}/comments` | Sessione | `{"text":"Ciao"}` oppure `{"text":"Risposta","parentId":"7"}` | `201 CommentChange` |
| `PATCH /sounds/{soundId}/comments/{commentId}` | Sessione | Oggetto parziale con `text`, `pinned` o `hearted` | `200 CommentChange` |
| `DELETE /sounds/{soundId}/comments/{commentId}` | Sessione | Vuoto | `200 CommentRemoval` |
| `PUT /sounds/{soundId}/vote` | Sessione | `{"vote":"LIKE"}`, `{"vote":"DISLIKE"}` o `{"vote":null}` | `200 SoundVoteResult` |
| `PUT /sounds/{soundId}/comments/{commentId}/vote` | Sessione | Stesso body | `200 CommentVoteResult` |
| `PUT /sounds/{soundId}/image` | Sessione | Multipart con `file` | `200 { "imageUrl": "…" }` |
| `DELETE /sounds/{soundId}/image` | Sessione | Vuoto | `204` |

- **Play**. Fai la chiamata quando l'audio comincia a riprodursi. Genera un
  UUID per ogni nuova riproduzione e riusa lo stesso UUID nei retry.
  Pause, riprese e seek rientrano nella stessa riproduzione. Il backend
  ricava l'utente dalla sessione quando c'è. Gli ospiti contribuiscono
  al numero di play, mentre utenti unici e history personale usano eventi
  con identità.
- **Crea commento**. `text` è obbligatorio, trim, da 1 a 2.000 caratteri
  Unicode. Per un commento principale ometti `parentId`. Per una risposta
  usa l'ID del padre, attivo e dello stesso suono. La sessione fornisce
  l'autore.
- **Modifica/elimina commento**. L'autore può modificare il testo ed
  eliminare il commento. L'uploader del suono può impostare o rimuovere il
  cuore e il pin con booleani, anche sui commenti altrui. Solo una radice
  può essere pinned. La PATCH richiede almeno un campo e, se contiene
  più campi, il chiamante deve avere il permesso per tutti. Ad esempio
  `{"text":"Corretto"}`, `{"pinned":true}` o `{"hearted":false}`.
- **Voto**. `vote` è l'unico campo, obbligatorio. `null` rimuove il
  voto. LIKE e DISLIKE si sostituiscono. Ripetere lo stesso PUT è sicuro.
  Per il commento la risposta ha il totale dei like e il voto personale.
- **Immagine del suono**. Solo l'uploader può sostituirla o rimuoverla.
  Formato PNG, JPEG o GIF, max 1 MiB. Usa una chiamata separata dopo aver
  ottenuto l'ID dell'upload audio. `PUT` sostituisce l'immagine precedente.
  La DELETE restituisce `204` anche quando il suono è già senza copertina.

```ts
type CommentChange = { comment: SoundComment; commentCount: number; previousPinnedId: Id | null }
type CommentRemoval = { comment: SoundComment | null; commentCount: number }
type SoundVoteResult = { soundId: Id; commentId: null; likes: number; dislikes: number; vote: Vote }
type CommentVoteResult = { soundId: Id; commentId: Id; likes: number; vote: Vote }
```

Il frontend può aggiornare i contatori dalla risposta della scrittura. Se
`previousPinnedId` è valorizzato, il pin su quell'altro commento va rimosso.
I body JSON di queste operazioni rifiutano i campi estranei.

## Suoni già esposti

Tutte queste rotte usano **Sessione**. I valori riferiti alla prossima
versione del backend sono indicati anche se il controller esiste già.

| Metodo e percorso | Query o body | Risposta | Stato |
| --- | --- | --- | --- |
| `GET /guilds/{guildId}/sounds` | `period`, `timezone` | `200 GuildSoundLibrary` | Attivo |
| `POST /guilds/{guildId}/sounds` | Multipart `file` e `metadata` | `201 LegacySound`, header `Location` | Attivo |
| `GET /sounds/me` | `period`, `timezone` | `200 MySoundLibrary` | Attivo |
| `GET /sounds/{soundId}` | Vuoto | `200 LegacySound` | Da sostituire con la scheda pubblica |
| `GET /sounds` | `q`, `tag`, `sort`, `order`, `limit` | `200 { sounds: LegacySound[], limit: number }` | Da sostituire con il catalogo pubblico |
| `GET /tags` | `q` obbligatorio | `200 { tags: Tag[] }` | Da sostituire con i suggerimenti |
| `PATCH /sounds/{soundId}` | Oggetto parziale con `name`, `public`, `tags` | `200 LegacySound` | Attivo |
| `DELETE /sounds/{soundId}` | Vuoto | `204` | Attivo |
| `PATCH /sounds/{soundId}/like` | `{ "enabled": true }` o `{ "enabled": false }` | `200 LegacyVote` | Da sostituire con il PUT |
| `PATCH /sounds/{soundId}/dislike` | Stesso body | `200 LegacyVote` | Da sostituire con il PUT |

### Upload e modifica del suono

Upload e raccolta di gilda richiedono che il chiamante e il bot condividano
`guildId`. Per l'upload invia `file` binario MP3 o Opus e una parte
`metadata` con tipo `application/json`. I soli campi sono `name`
obbligatorio, `public` facoltativo con default `true`, `tags`
facoltativo con default `[]`.

```ts
const form = new FormData()
form.append('file', audioFile, audioFile.name)
form.append(
  'metadata',
  new Blob([JSON.stringify({ name: 'Airhorn', public: true, tags: ['meme'] })], {
    type: 'application/json',
  }),
  'metadata.json',
)
```

L'API diretta accetta fino a 25 MiB. Il form del dashboard attuale limita il
file a 3.9 MB per lasciare margine al multipart sul proxy. Il nome viene
trimmato, deve essere non vuoto e diverso da un ID tutto numerico. Usa
**32 caratteri Unicode** nel nuovo frontend. Il backend della fase precedente
accettava 255 caratteri e i vecchi nomi lunghi rimangono leggibili. I tag
sono al massimo cinque stringhe non vuote, ognuna di max 255 caratteri,
normalizzate in minuscolo e deduplicate. Per esempio
`{"name":"Airhorn v2","public":false,"tags":[]}` è una PATCH valida e
svuota i tag. I campi omessi restano invariati e ogni campo nella PATCH
richiede un tipo valido. `Location` per l'upload è
`/api/beebot/sounds/{soundId}` nella rotta attuale.

Solo l'uploader modifica. L'uploader o l'Administrator/owner della gilda
d'origine può eliminare. Manage Server da solo dà accesso alle impostazioni
di gilda, mentre la cancellazione del suono richiede i permessi sopra.
L'eliminazione rimuove il suono dalla libreria e dalle associazioni SQL.
I vecchi audio CDN possono continuare a esistere.

### Letture e voto da sostituire

- `GET /sounds` richiede almeno uno tra `q` (sottostringa del nome) e
  `tag` (nome esatto), ciascuno fino a 255 caratteri. Sono case-insensitive
  e combinabili. `sort` ammette `plays` (default), `likes` e
  `dislikes`. `order` ammette `desc` (default) e `asc`. `limit`
  va da 1 a 100, default 100. Questa lista arriva in una risposta unica.
- `GET /tags?q=me` richiede un prefisso non vuoto fino a 255 caratteri.
  Restituisce fino a 100 tag in ordine alfabetico. `Tag` ha `id` e `name`.
- Le letture autenticate esistenti possono includere privati di una
  gilda condivisa. La scheda `/public` segue la regola del solo uploader
  per i privati. I voti PATCH accettano un solo campo booleano
  `enabled`. `true` imposta quel voto. `false` lo rimuove se attivo.
  Usa le chiamate da sostituire solo per supportare ambienti che
  eseguono la versione precedente.

```ts
type LegacySound = {
  id: Id
  name: string
  guildId: Id
  userId: Id
  uploader: PublicUser
  public: boolean
  createdAt: DateTime | null
  url: string | null
  durationMs: number | null
  tags: Tag[]
  plays: number
  listeners: number
  likes: number
  dislikes: number
  myPlays: number
  likedByMe: boolean
  dislikedByMe: boolean
  ownedByMe: boolean
  canEdit: boolean
  canDelete: boolean
  soundboardCount: number
}
type LegacyVote = { soundId: Id; likes: number; dislikes: number; likedByMe: boolean; dislikedByMe: boolean }
type GuildSoundLibrary = {
  guildId: Id
  counts: { all: number; mine: number; others: number }
  tags: TagCount[]
  summary: PeriodSummary
  sounds: LegacySound[]
}
type MySoundLibrary = {
  counts: { all: number }
  tags: TagCount[]
  summary: PeriodSummary
  guilds: Record<Id, { name: string | null; sounds: LegacySound[] }>
}
```

`LegacySound.url` è l'URL audio nelle risposte esistenti. Nel nuovo
`SoundCard` la proprietà si chiama `audioUrl`. Le librerie esistenti
restituiscono la raccolta completa. I contatori sul singolo suono sono
globali, mentre le sintesi seguono il periodo richiesto.

### Analytics

| Metodo e percorso attivo | Query | Risposta `200` |
| --- | --- | --- |
| `GET /guilds/{guildId}/sounds/analytics` | `period`, `timezone` | `GuildAnalytics` |
| `GET /sounds/me/analytics` | `period`, `timezone` | `MySoundAnalytics` |
| `GET /sounds/{soundId}/analytics` | `period`, `timezone`, `guildId`, `cursor` | `SoundAnalytics` |

`period` vale `7d`, `30d` (default), `90d` o `all`. `timezone`
è un ID IANA, per esempio `Europe/Rome` (default) o `UTC`. Gli stessi
parametri valgono per le librerie. Sul dettaglio `guildId` è facoltativo
e richiede membership e bot nella gilda. Omettilo per statistiche globali.
La rotta di gilda considera i suoni caricati e i play registrati nella
gilda. La rotta personale considera gli upload dell'utente e i loro play
globali. Le risposte non espongono lo storico individuale di altri utenti.

`cursor` è solo per le riproduzioni personali del singolo suono.
`my.history` dà fino a 100 eventi, dal più recente. Per caricare altri
eventi, chiama il dettaglio analytics con `my.nextCursor` e lo stesso
`soundId`, utente, periodo, timezone e `guildId` della richiesta di
partenza. Aggiungi gli eventi alla lista e conserva i totali del periodo.
Per esempio `GET /sounds/42/analytics?period=7d&guildId=123&cursor=<cursor>`.

```ts
type Period = '7d' | '30d' | '90d' | 'all'
type TimeRange = { period: Period; timezone: string; from: DateTime | null; to: DateTime }
type PlaySummary = { plays: number; listeners: number; averagePlaysPerListener: number }
type CollectionSummary = PlaySummary & { myPlays: number; likes: number; dislikes: number; soundCount: number }
type Comparison = { previousPlays: number | null; playsChangePercent: number | null }
type PeriodSummary = CollectionSummary & Comparison & { period: Period }
type Source = 'AUDIO' | 'SOUND' | 'GREET' | 'SOUNDBOARD' | 'WEB' | 'UNKNOWN'
type DailyPlays = { date: string; plays: number }
type Listener = { user: PublicUser; plays: number; soundCount: number }
type GroupAnalytics = PlaySummary & {
  playsPerDay: DailyPlays[]
  heatmap: { weekday: number; hour: number; plays: number }[]
  sources: { source: Source; plays: number }[]
  replayDistribution: { min: number; max: number | null; users: number }[]
}
type PersonalHistory = {
  plays: number
  history: { id: Id; playedAt: DateTime; guildId: Id | null; source: Source }[]
  nextCursor: Cursor
}
type SoundTotals = GroupAnalytics & Comparison & { likes: number; dislikes: number }
type SoundAnalytics = TimeRange & {
  soundId: Id
  totals: SoundTotals
  guilds: Record<Id, GroupAnalytics & { users: Listener[] }>
  otherGuilds: GroupAnalytics
  my: PersonalHistory
}
type MySoundAnalytics = TimeRange & { summary: PeriodSummary; sounds: SoundAnalytics[] }
type GuildAnalytics = TimeRange & {
  guildId: Id
  summary: CollectionSummary
  playsPerDay: DailyPlays[]
  topSounds: { soundId: Id; plays: number }[]
  mostLiked: { soundId: Id; likes: number }[]
  users: Listener[]
  sounds: (SoundTotals & { soundId: Id; users: Listener[]; my: PersonalHistory })[]
}
```

L'intervallo è `[from, to)`. `all` ha `from: null` e confronto
precedente nullo. La variazione percentuale è nulla anche se il periodo
precedente ha zero play. Like e dislike sono totali correnti. La heatmap
ha 168 celle (giorni 1–7, lunedì = 1, ore 0–23), anche a zero.
`topSounds` e `mostLiked` restituiscono fino a 5 risultati.
`otherGuilds` aggrega gilde non visibili e omette identità e ID di quelle
gilde. `guildId: null` nella cronologia può indicare un play web, una
gilda sconosciuta o una gilda a cui il chiamante non ha accesso. Le
riproduzioni web anonime entrano nei totali e nella sorgente `WEB`.

## Soundboard di gilda

Rotte **attive**, tutte con **Sessione**. La lettura richiede normale
membership e bot presente. Qualunque membro autorizzato può creare una
soundboard. Le scritture su una soundboard esistente richiedono il suo
creatore o un Administrator/owner della gilda. Nessuna di queste rotte ha
parametri di query.

| Metodo e percorso | Body | Risposta |
| --- | --- | --- |
| `GET /guilds/{guildId}/soundboards` | Vuoto | `200 Soundboard[]` |
| `GET /guilds/{guildId}/soundboards/{boardId}` | Vuoto | `200 SoundboardDetail` |
| `POST /guilds/{guildId}/soundboards` | `{ "name": "Arcade", "soundIds": ["42", "73"] }` | `201 SoundboardDetail` e `Location` |
| `PATCH /guilds/{guildId}/soundboards/{boardId}` | `{ "name": "Arcade remix" }` | `200 SoundboardDetail` |
| `PUT /guilds/{guildId}/soundboards/{boardId}/sounds/{soundId}` | Vuoto | `204` |
| `DELETE /guilds/{guildId}/soundboards/{boardId}/sounds/{soundId}` | Vuoto | `204` |
| `PUT /guilds/{guildId}/soundboards/{boardId}/thumbnail` | Multipart con `file` PNG, JPEG o GIF, max 1 MiB | `200 { thumbnailUrl: string }` |
| `DELETE /guilds/{guildId}/soundboards/{boardId}` | Vuoto | `204` |

```ts
type Soundboard = {
  id: Id
  guildId: Id
  userId: Id
  name: string
  thumbnailUrl: string | null
  soundCount: number
  canEdit: boolean
}
type SoundboardDetail = { board: Soundboard; sounds: LegacySound[] }
```

Il nome è trimmato, max 255 caratteri, non vuoto e non interamente
numerico. Scegli da 1 a **25** ID alla creazione per il contratto obiettivo.
Il limite si applica alla lunghezza inviata e gli ID ripetuti sono
deduplicati. La revisione precedente ha il limite 20. `Location` punta
a `/api/beebot/guilds/{guildId}/soundboards/{boardId}`. Si possono aggiungere
suoni pubblici o privati originari della gilda. Un'aggiunta già presente
o la rimozione di un'associazione già assente dà `204`. Rimuovendo
l'ultimo suono, la board resta. Cancellare la board rimuove le associazioni
e conserva i singoli suoni. La copertina della soundboard e quella del
singolo suono sono due campi e due operazioni distinte.

## Login, profilo e greets

Tutte le seguenti rotte sono **attive** e hanno query vuota.

| Metodo e percorso | Accesso | Body | Risposta |
| --- | --- | --- | --- |
| `POST /auth/discord/authorize` | Login | `{ "state": "…", "redirectUri": "…" }` | `200 { authorizationUrl: string, expiresIn: number }` |
| `POST /auth/session` | Login | `{ "code": "…", "state": "…", "redirectUri": "…" }` | `200 { sessionToken: string, expiresAt: number, user: AuthUser }` |
| `POST /auth/logout` | Sessione | Vuoto | `204` |
| `GET /me` | Sessione | Vuoto | `200 { user: AuthUser, servers: Server[], greets: Record<Id, LegacySound> }` |
| `GET /guilds` | Sessione | Vuoto | `200 Server[]` |
| `GET /servers` | Sessione | Vuoto | `200 Server[]`, alias di `/guilds` |
| `POST /me/settings` | Sessione | `{ "greets": { "0": "42", "123": "73", "789": null } }` | `200 { greets: Record<Id, GreetResult> }` |

```ts
type AuthUser = { id: Id; username: string; globalName: string | null; avatarUrl: string | null }
type Server = { id: Id; name: string; iconUrl: string | null; owner: boolean }
type FieldError = { field: string | null; code: string; message: string }
type GreetResult = { status: 'ok' | 'error'; errors: FieldError[] }
```

Login Qwik. `/auth/login/` genera uno state URL-safe (32–256 caratteri
`[A-Za-z0-9_-]`), lo conserva in un cookie HttpOnly breve e chiama
`/auth/discord/authorize`. `expiresIn` è in secondi e vale 600.
Il browser apre `authorizationUrl`. Discord torna alla pagina Qwik
`/auth/callback/?code=…&state=…`. Qwik verifica lo state e invia lo stesso
`redirectUri` a `POST /auth/session`. Deve corrispondere alla callback
configurata con `BEEBOT_AUTH_CALLBACK_URI`, associata alla chiave e
registrata su Discord. Il `code` deve essere non vuoto e lungo al massimo
512 caratteri. Conserva `sessionToken` in `beebot_session` HttpOnly,
Secure in HTTPS e SameSite=Lax, fino a `expiresAt`. Al logout revoca e
svuota il cookie. La sessione dura al massimo otto ore, entro la scadenza
del token Discord.

`/me.servers`, `/guilds` e `/servers` usano la directory amministrativa
di owner, Administrator e Manage Server. Per la normale membership dei
suoni fa fede la singola rotta. `greets` contiene solo i greet
effettivamente configurati e visibili. Il globale (`"0"`) ammette suoni
pubblici o privati propri. Un override di gilda ammette suoni pubblici o
privati originari di quella gilda con bot e utente presenti. `null`
cancella il greet di quello scope. Le chiavi omesse restano come sono.
Il POST contiene unicamente `greets` e ogni chiave ha un risultato
proprio: controlla `status` anche con HTTP `200`.

## Impostazioni e alert di gilda

Rotte **attive** con **Sessione**, bot presente e permesso owner,
Administrator o Manage Server.

| Metodo e percorso | Query / body | Risposta |
| --- | --- | --- |
| `GET /guilds/{guildId}` | Vuoti | `200 GuildSettingsView` |
| `POST /guilds/{guildId}/settings` | JSON parziale descritto sotto | `200 SettingsResults` |
| `DELETE /guilds/{guildId}/alerts/{type}` | `type = welcome, leave, boost, levelUp`; body vuoto | `204` |
| `DELETE /guilds/{guildId}/alerts/reward/{level}` | `level = 1…100`; body vuoto | `204` |
| `DELETE /guilds/{guildId}/alerts/twitch/{streamerId}` | ID Twitch numerico; body vuoto | `204` |
| `DELETE /guilds/{guildId}/settings/channels/{channelId}` | ID del canale; body vuoto | `204` |
| `GET /guilds/{guildId}/alerts/twitch/lookup` | `login` obbligatorio, nome Twitch da 1 a 25 caratteri `[A-Za-z0-9_]` | `200 { id: Id, login: string, displayName: string }` |

Per la ricerca streamer passa solo lo username, es. `?login=arcade`.
Il risultato fornisce l'ID per `twitch.{streamerId}`. Le DELETE sono
idempotenti per una voce già assente. Rimuovere un override di canale
ripristina i default. La rimozione Twitch può tornare `503` con
`twitch_unsubscribe_failed` dopo aver rimosso la configurazione locale.

### Tutti i campi scrivibili

Il body del POST è un oggetto non vuoto. Blocchi e campi interni sono
facoltativi. I valori omessi restano invariati.

| Blocco | Campi ammessi e vincoli |
| --- | --- |
| `general` | `prefix` stringa da 1 a 32, `experienceEnabled` booleano, `voice` voce TTS conosciuta (es. `John`), `leagueShard` nome enum (es. `EUW1`). `language` è derivato e compare solo in lettura. |
| `blacklist` | `enabled` booleano, `threshold` intero 0–127, `channelId` ID di canale testuale oppure `null`. |
| `channels.{channelId}` | `experienceEnabled`, `statisticsEnabled` booleani, `experienceModifier` numero finito non negativo, `leagueShard` nome enum o `null` per ereditare. |
| `welcome`, `boost` | `message`, `privateMessage`, `channelId`, `enabled`, `sendType`, `roleIds`. |
| `leave` | `message`, `privateMessage`, `channelId`, `enabled`, `sendType`. |
| `levelUp` | `message`, `privateMessage`, `enabled`, `sendType`, `roleIds`. |
| `reward.{level}` | `message`, `privateMessage`, `enabled`, `sendType`, `roleIds`, `temporary` booleano. Chiave livello canonica da `"1"` a `"100"`. |
| `twitch.{streamerId}` | `message`, `privateMessage`, `channelId`, `enabled`, `sendType`, `roleId` singolo. Chiave streamer decimale positiva, fino a 32 cifre. |

Messaggi di max 2.000 caratteri, anche vuoti. `null` svuota
`message`, `privateMessage`, `channelId` o `roleId`. Gli altri
campi richiedono i tipi indicati. `sendType` vale `CHANNEL`, `PRIVATE`
o `BOTH`. `roleIds` è un array di massimo 25 ID di ruoli della gilda
che sostituisce la selezione, oppure `[]` per svuotarla. Il canale fisso
dell'alert deve essere testuale e di quella gilda. I blocchi a chiavi
(`channels`, `twitch`, `reward`) si mandano come oggetti.

Per `leagueShard`, i valori della versione corrente sono `BR1`, `EUN1`,
`EUW1`, `JP1`, `KR`, `LA1`, `LA2`, `NA1`, `OC1`, `TR1`, `RU`, `PBE1`,
`SG2`, `PH2`, `ID1`, `VN2`, `TH2`, `TW2`, `ME1` e `UNKNOWN`. Per i
canali usa `null` al posto di `UNKNOWN` se vuoi ereditare. Per la voce
invia il nome così come registrato da Beebot. Il dashboard attuale
mostra e modifica quel nome come testo.

<details>
<summary>Voci TTS accettate</summary>

Invia il nome della voce. Il codice lingua è informativo.

| Lingua | Valori di `voice` |
| --- | --- |
| `ar-eg` | `Oda` |
| `de-de` | `Hanna`, `Lina`, `Jonas` |
| `el-gr` | `Neo` |
| `en-gb` | `Alice`, `Nancy`, `Lily`, `Harry` |
| `en-in` | `Eka`, `Jai`, `Ajit` |
| `en-us` | `Linda`, `Amy`, `Mary`, `John`, `Mike` |
| `es-es` | `Camilla`, `Sofía`, `Luna`, `Diego` |
| `fr-fr` | `Axel`, `Zola`, `Iva`, `Bette` |
| `it-it` | `Mia`, `Pietro`, `Bria` |
| `ja-jp` | `Fumi`, `Akira`, `Airi` |
| `ko-kr` | `Nari` |
| `nl-nl` | `Bram`, `Lotte` |
| `pl-pl` | `Jan`, `Julia` |
| `pt-pt` | `Leonor` |
| `ro-ro` | `Doru` |
| `ru-ru` | `Olga`, `Marina`, `Peter` |
| `sv-se` | `Hugo`, `Molly` |
| `zh-cn` | `Shu`, `Chow`, `Wang`, `Luli` |

</details>

Esempio con più impostazioni nello stesso body.

```json
{
  "general": { "prefix": "!", "experienceEnabled": true, "voice": "John", "leagueShard": "EUW1" },
  "channels": { "1234": { "experienceModifier": 1.5, "leagueShard": null } },
  "leave": { "message": "Ciao #user", "channelId": "1234", "sendType": "CHANNEL" },
  "reward": { "5": { "roleIds": ["4321"], "temporary": true } },
  "twitch": { "456": {} }
}
```

Un oggetto vuoto per un singolo alert, reward o Twitch crea la voce con
i default se assente. Una mappa vuota lascia le voci esistenti come sono.
Per cancellare usa la DELETE dedicata. Il blocco warning/actions presente
in lettura è fuori dallo sviluppo frontend di questa fase.

### Risposte delle impostazioni

```ts
type SendType = 'CHANNEL' | 'PRIVATE' | 'BOTH'
type AlertState = {
  id: number | null
  exists: boolean
  enabled: boolean
  valid: boolean
  message: string | null
  privateMessage: string | null
  channelId: Id | null
  sendType: SendType | null
  roleIds: Id[]
  roleId: Id | null
  streamerId: Id | null
  level: number | null
  temporary: boolean | null
  problems: FieldError[]
}
type SettingsItem = { status: 'ok' | 'error' | 'saved_with_errors'; errors: FieldError[]; alert: AlertState | null }
type SettingsResults = Partial<
  Record<'general' | 'blacklist' | 'welcome' | 'leave' | 'boost' | 'levelUp', SettingsItem> &
  Record<'channels' | 'twitch' | 'reward', Record<string, SettingsItem> | SettingsItem>
>
type GuildSettingsView = {
  id: Id
  name: string
  iconUrl: string | null
  channels: { id: Id; name: string; type: string; parentId: Id | null; position: number | null }[]
  roles: { id: Id; name: string; color: number; position: number; managed: boolean; assignable: boolean }[]
  settings: {
    general: { prefix: string; experienceEnabled: boolean; voice: string; language: string; leagueShard: string }
    blacklist: { enabled: boolean; threshold: number; channelId: Id | null }
    channels: Record<Id, { experienceEnabled: boolean; experienceModifier: number; statisticsEnabled: boolean; leagueShard: string | null }>
    welcome: AlertState
    leave: AlertState
    boost: AlertState
    levelUp: AlertState
    twitch: Record<Id, AlertState>
    reward: Record<string, AlertState>
    warning: { actions: { id: number; action: number; roleId: Id | null; actionTime: number; infractions: number; infractionsTime: number; actionMessage: string }[] }
  }
}
```

La GET fornisce le categorie in `channels` con tipo `CATEGORY`.
I canali senza override hanno XP e statistiche attive, modificatore 1
e shard ereditato. Per le scritture controlla i risultati di ciascun
blocco o elemento anche con HTTP `200`. Per una mappa valida, leggi
`results.channels[channelId]`, `results.twitch[streamerId]` o
`results.reward[level]`. Un blocco inviato con il tipo sbagliato restituisce
direttamente `SettingsItem` per quel blocco. `status: "ok"` e
`alert.valid: false` significano che la configurazione è stata salvata
ma ha problemi operativi. `saved_with_errors` per Twitch significa
che il salvataggio è riuscito e la registrazione esterna è fallita.
`errors` appartiene alla richiesta. `problems` appartiene alla
configurazione, con codici quali `missing_channel`, `missing_message`,
`disabled`, `experience_disabled`.

## Passaggio al nuovo contratto

- Il sito pubblico e i commenti usano le rotte di questo contratto quando
  l'ambiente Beebot esegue la nuova versione. Un `404` di un suono
  privato resta un esito normale della rotta pubblica.
- Per il dashboard riusa upload, board, analytics, sessione e impostazioni.
  La nuova scheda usa `SoundCard`; le raccolte e le mutazioni già attive
  mantengono `LegacySound`. Le letture e i voti da sostituire cambiano
  insieme ai loro chiamanti.
- Per il supporto esistente vedi [OpenAPI suoni e soundboard](beebot-sounds.openapi.json),
  [autenticazione](beebot-auth.md) e [impostazioni di gilda](beebot-guilds.md).
