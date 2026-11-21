# Soundboard e ricerca — contratto per la dashboard

Prefisso di tutte le rotte `/api/beebot`. Il server della dashboard invia `X-API-Key`
e aggiunge `Authorization: Bearer <sessione>` per l'utente autenticato. Le rotte
`/public` accettano anche la richiesta senza bearer. Le altre richiedono
un'identità. Mantieni le credenziali API sul server. Le risposte usano
`Cache-Control: no-store`.

Tutti gli ID viaggiano come stringhe. I cursori sono opachi e `nextCursor: null`
indica l'ultima pagina.

## Ricerca

| Chiamata | Query | Risposta |
| --- | --- | --- |
| `GET /public/sounds` | `q`, `tag`, `uploaderId`, `minDurationMs`, `maxDurationMs`, `sort`, `limit`, `cursor` | `200 {items: SoundCard[], mine: [], nextCursor}` |
| `GET /public/soundboards` | `q`, `limit`, `cursor` | `200 {items: Soundboard[], nextCursor}` |
| `GET /public/soundboards/{id}` | Nessuna | `200 {board: Soundboard, sounds: SoundCard[]}` |

Usa due tab con cursori indipendenti. Entrambe le liste hanno 20 risultati
di default e accettano 1–100. Ometti il limite di default. Riparti dalla
prima pagina quando cambia la ricerca, un filtro o l'identità.

Nei suoni tutti i risultati sono in `items`. Usa la lista così come arriva
dal backend, compresi i propri privati. Il campo `mine` è sempre vuoto e
resta presente per il parser precedente. `q` cerca nel nome o nei tag.
`sort` accetta `relevance`, `popular`, `newest`, `mostLiked`, `mostDisliked`.
Con `q` si usa `relevance` di default, altrimenti `popular`. I filtri e la
struttura di `SoundCard` sono nel [contratto dei suoni](beebot-sound-community.md#filtri-del-catalogo).

Le soundboard vengono cercate per nome, ignorando le maiuscole. Prima
viene il nome esatto, poi il prefisso, poi la sottostringa. A parità
viene prima l'ID maggiore. Senza `q` si usa direttamente l'ID decrescente.
La ricerca globale mostra board pubbliche e, con sessione, le proprie
private. Si può aprire una board privata di gilda se il server riconosce
la membership dell'utente. Il dettaglio pubblico di una board pubblicata
include solo suoni attualmente pubblici. La ricerca restituisce solo le card.

Esempio di risposta della ricerca soundboard.

```json
{
  "items": [
    {
      "id": "7",
      "guildId": null,
      "userId": "456",
      "name": "Arcade",
      "thumbnailUrl": null,
      "canEdit": false,
      "public": true,
      "sortMode": "MANUAL",
      "likes": 12,
      "plays": 42,
      "commentCount": 3
    }
  ],
  "nextCursor": null
}
```

`guildId: null` indica una board senza gilda. Mostra l'autore in quel caso.
Nel dettaglio pubblico autenticato `board.viewer` contiene `{vote: "LIKE" | null}`.
Nei risultati di ricerca il voto personale è omesso. Nelle letture pubbliche
`canEdit` segnala l'autore. Per autorizzare gli amministratori usa
il dettaglio gestionale. `soundCount` è presente sulle rotte gestionali.

## Creazione e modifica

| Chiamata | Body | Risposta |
| --- | --- | --- |
| `GET /me/soundboards` | Nessuno | `200 Soundboard[]` delle board di cui l'utente è autore, incluse quelle collegate a una gilda |
| `POST /me/soundboards` | `name`, `soundIds`, opzionali `public` e `sortMode` | `201 {board,sounds,soundIds}` con `Location` |
| `GET /guilds/{guildId}/soundboards` | Nessuno | `200 Soundboard[]` |
| `POST /guilds/{guildId}/soundboards` | Stesso body di creazione | `201 {board,sounds,soundIds}` con `Location` |
| `GET /guilds/{guildId}/soundboards/{id}` | Nessuno | `200 {board,sounds,soundIds?}` |
| `GET /soundboards/{id}` | Nessuno | `200 {board,sounds,soundIds}` per gli editor |
| `PATCH /soundboards/{id}` | Qualunque sottoinsieme non vuoto di `name`, `public`, `sortMode`, `soundIds` | `200 {board,sounds,soundIds}` |
| `PUT /soundboards/{id}/sounds/{soundId}` | Nessuno | `204` |
| `DELETE /soundboards/{id}/sounds/{soundId}` | Nessuno | `204` |
| `PUT /soundboards/{id}/thumbnail` | Multipart `file`, PNG/JPEG/GIF fino a 1 MiB | `200 {thumbnailUrl}` |
| `DELETE /soundboards/{id}` | Nessuno | `204` |

Le scritture per ID si possono anche fare sotto
`/guilds/{guildId}/soundboards/{id}` con gli stessi metodi. Le rotte con
gilda richiedono membership e presenza del bot. Le modifiche richiedono
l'autore o un amministratore della gilda associata. Solo l'autore può
modificare la propria board senza gilda. La visibilità pubblica permette
la lettura e la riproduzione.

```json
{
  "name": "Arcade",
  "public": true,
  "sortMode": "MANUAL",
  "soundIds": ["1", "2", "3"]
}
```

`name` è da 1 a 255 caratteri dopo il trim e non può essere interamente
numerico. Il default per `public` è `false`. `sortMode` accetta
`NAME_ASC`, `NAME_DESC`, `MANUAL` e ha default `NAME_ASC`. Il formato
di creazione di gilda precedente con i soli `name` e `soundIds` continua
a deduplicare gli ID. Negli altri body gli ID devono essere distinti.

Invia da 1 a 25 suoni in creazione. In modifica `soundIds` è la lista
finale e può essere vuota. Omettilo per conservare i collegamenti e le
posizioni. Il backend salva le posizioni nell'ordine dell'array anche
quando la visualizzazione è alfabetica. `MANUAL` le usa direttamente.
L'aggiunta singola accoda un nuovo suono. Il dettaglio restituisce
`sounds` già ordinati. In gestione, `sounds` usa la forma `Sound` già
usata dalla dashboard di gilda e `soundIds` contiene le posizioni
manuali salvate. Se una card manca, mostra un elemento non disponibile
in base all'ID. Invia la lista solo quando l'utente ha modificato i
collegamenti o il loro ordine.

La pubblicazione e l'aggiunta a una board pubblica richiedono suoni
pubblici. Una board di gilda privata accetta anche i privati di quella
gilda. Una board personale privata accetta anche i privati dell'autore.
Per ripubblicare o risalvare una lista pubblica con un riferimento
diventato privato, l'editor deve rimuoverlo oppure rendere di nuovo
pubblico il suono.

## Commenti e voti

| Chiamata | Body o filtri | Risposta |
| --- | --- | --- |
| `GET /public/soundboards/{id}/comments` | `parentId`, `sort`, `limit`, `cursor` facoltativi | `200 {items,nextCursor}` |
| `POST /soundboards/{id}/comments` | `{"text":"Bella board"}` o `{"text":"Grazie","parentId":"81"}` | `201 {comment,commentCount,previousPinnedId}` |
| `PATCH /soundboards/{id}/comments/{commentId}` | `text`, `pinned` e/o `hearted` | `200 {comment,commentCount,previousPinnedId}` |
| `DELETE /soundboards/{id}/comments/{commentId}` | Nessuno | `200 {comment,commentCount}` |
| `PUT /soundboards/{id}/vote` | `{"vote":"LIKE"}` o `{"vote":null}` | `200 {soundboardId,commentId:null,likes,vote}` |
| `PUT /soundboards/{id}/comments/{commentId}/vote` | `LIKE`, `DISLIKE` o `null` nel campo `vote` | `200 {soundboardId,commentId,likes,vote}` |

Testo da 1 a 2.000 caratteri dopo il trim. Solo l'autore di un commento
ne modifica il testo o lo elimina. Il proprietario della board gestisce
pin e cuore. Un solo commento radice può essere fissato. `previousPinnedId`
permette di aggiornare la radice precedente dopo un nuovo pin. Quando
elimini un commento con risposte, la risposta contiene il segnaposto
con `deleted: true`; altrimenti `comment` è `null`. Ripetere un PUT di voto
mantiene lo stesso risultato.

Le pagine contengono 20 radici o 20 risposte dirette di default.
`sort` accetta `top` (default), `newest`, `oldest`, `mostDisliked`.
Il pinned compare per primo solo nella prima pagina delle radici.
La forma di un commento è quella del [contratto esistente](beebot-sound-community.md#commenti-e-thread),
con `soundboardId` al posto di `soundId`. I commenti della board
si caricano separatamente dai suoi suoni.

## Riproduzioni

`POST /public/sounds/{soundId}/plays` accetta `soundboardId` facoltativo.

```json
{
  "playbackId": "00f1704e-970b-4fae-8144-5e4af3db24e5",
  "soundboardId": "7"
}
```

Invia la chiamata quando parte l'audio. Riusa l'UUID in retry, pause,
riprese e seek. Ottieni `204`. Con `soundboardId` il backend verifica
la visibilità della board e l'associazione al suono e incrementa
entrambi i contatori una volta. Ometti l'ID per le riproduzioni
sul dettaglio del singolo suono. Gli ospiti contribuiscono ai play
delle board pubbliche.

## Errori e rilascio

Il formato è `{code,message,retryAfter}`. Gestisci `400 invalid_request`,
`401 invalid_session` o `invalid_api_key`, `403 soundboard_forbidden` o
`guild_forbidden`, `404 soundboard_not_found`, `sound_not_found` o
`comment_not_found`, `409 duplicate_soundboard_name`,
`private_sound_in_public_board` o `soundboard_full`, e
`503 sound_storage_unavailable`. Per i dettagli di autenticazione
e dei multipart vedi il [contratto Beebot](beebot-sound-community.md).

Il backend richiede la [migrazione 006](../../database/berbit/sound/006-soundboards.sql)
dopo la 005, con bot e API fermi. Le board esistenti partono private
in `NAME_ASC`. I vecchi play conservano l'attribuzione esistente e
partono con `soundboard_id = NULL`. La cancellazione di una board
rimuove commenti, voti e collegamenti e conserva i play dei singoli suoni.
Un upload di suono richiede ancora una gilda. La creazione di board
personali usa suoni già esistenti.
